package io.github.stoicswe.eyeandsickle.client.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * The whole audio engine: one output line, one thread, and every voice summed in software.
 *
 * <h2>⚠ ONE LINE FOR THE ENTIRE GAME, NOT ONE {@code Clip} PER SOUND</h2>
 *
 * The obvious implementation gives each sound its own {@link javax.sound.sampled.Clip}, and the
 * one-chime version of this package did exactly that. It does not survive contact with a second
 * sound, for three separate reasons, and only the first is the one people expect:
 *
 * <ol>
 *   <li><b>A {@code Clip} has one playback cursor.</b> Triggering the same effect twice in quick
 *       succession restarts it rather than layering it, so two messages arriving together make one
 *       noise. Fixing that means a <i>pool</i> of clips per effect — N effects × M voices worth of
 *       mixer lines, for a catalogue that is expected to grow.
 *   <li><b>Every open {@code Clip} holds a line.</b> Measured on this machine every device reported
 *       {@code maxLines=unlimited}, which is a fact about macOS on Apple Silicon and not about
 *       Windows or ALSA; the failure when a platform is less generous is that playback silently stops
 *       working after a while, which is close to unattributable.
 *   <li><b>A {@code Clip} holds the whole sound decoded.</b> Music through one means a multi-megabyte
 *       track fully resident, and there is no way to fade, duck or crossfade between two of them
 *       except through per-line gain controls that not every platform provides.
 * </ol>
 *
 * Summing the voices here instead answers all three at once, and gives per-bus volume, ducking and
 * crossfade as arithmetic rather than as three more mechanisms.
 *
 * <h2>⚠ THE LINE IS THE CLOCK. THERE IS NO TIMER ANYWHERE IN THIS PACKAGE.</h2>
 *
 * {@link SourceDataLine#write} blocks until the device has room. Measured: writing 200 ms of frames
 * into an 80 ms buffer took 162 ms, i.e. the call returns immediately until the buffer fills and then
 * paces the caller exactly. So the mix loop is a plain {@code while} loop with no sleep, no
 * {@code Pulse} subscription, no {@code Timeline} and no {@code AnimationTimer} — the device's own
 * consumption rate is the schedule, and it is more accurate than any clock this client could ask for.
 *
 * <p>⚠ That is also what keeps this package outside {@code UiContractTest}'s reach. That test scans
 * <b>all of {@code src/main/java}</b> for {@code AnimationTimer} and rations it to two files by name;
 * an audio engine built on a timer would have had to argue for a third. It does not need to, because
 * it does not have one.
 *
 * <h2>⚠ A PLATFORM THREAD, DELIBERATELY NOT A VIRTUAL ONE</h2>
 *
 * {@code presence/RichPresence} runs its worker on {@code Thread.ofVirtual()} and is right to — it
 * blocks on socket I/O, which unmounts the carrier. This one must not. {@code write} blocks in a
 * <b>native</b> call, and a virtual thread blocked in native code <i>pins</i> its carrier thread for
 * the duration, so an audio engine on a virtual thread quietly occupies a scheduler thread forever
 * and hurts everything else that expected one.
 *
 * <h2>⚠ IT PARKS WHEN THERE IS NOTHING TO PLAY, AND RELEASES THE DEVICE</h2>
 *
 * A loop that kept writing silence would hold the audio device open for the whole session, which on
 * macOS and Windows is visible to the player (and on some setups prevents another application from
 * changing the sample rate). With no voices the thread stops the line and waits on the lock until
 * something is queued.
 */
final class SoftMixer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SoftMixer.class.getName());

    /**
     * Frames per mix buffer — about 20 ms at 44.1 kHz.
     *
     * <p>⚠ This is the latency floor: a sound triggered just after a buffer was filled is heard up to
     * one buffer plus the device's own backlog later. 20 ms is inaudible as delay for interface
     * feedback and gives the thread a comfortable margin; going much smaller trades that margin for a
     * precision nothing here needs, and the failure mode of losing the margin is a click.
     */
    private static final int BUFFER_FRAMES = 882;

    /** How much the device buffers ahead of us. Four buffers ≈ 80 ms of slack against scheduling. */
    private static final int DEVICE_BUFFERS = 4;

    /**
     * The most voices that may sound at once.
     *
     * <p>⚠ A cap rather than an unbounded list, because the cost of a voice is paid every buffer and
     * a runaway caller — a loop that plays a click per row of a table — would starve the audio thread
     * rather than merely being loud. Thirty-two is far above anything the interface should ever
     * produce; reaching it is a bug somewhere else, and {@link #steal} makes that bug quiet rather
     * than fatal.
     */
    private static final int MAX_VOICES = 32;

    /** How much of the remaining duck distance is covered per buffer. See {@link #musicBusGain}. */
    private static final float DUCK_STEP = 0.15f;

    private final Object lock = new Object();

    private final List<Voice> voices = new ArrayList<>();

    private volatile float master = 1.0f;
    private volatile float musicGain = 1.0f;
    private volatile float effectsGain = 1.0f;

    /** 0 = music silenced under effects, 1 = no ducking at all. See {@link #duckTarget}. */
    private volatile float duckDepth = 1.0f;

    private volatile boolean ducking;

    /** Follows {@link #duckTarget} one buffer at a time; a step change would be an audible thump. */
    private float duckCurrent = 1.0f;

    private volatile String preferredDevice = "";

    private volatile boolean running;
    private volatile boolean broken;
    private volatile String deviceName = "";

    private Thread worker;
    private SourceDataLine line;

    // ── what the rest of the client calls ────────────────────────────────────────────────────────

    void setMaster(int percent) {
        master = Gain.amplitude(percent);
    }

    void setBus(Bus bus, int percent) {
        float amplitude = Gain.amplitude(percent);
        if (bus == Bus.MUSIC) {
            musicGain = amplitude;
        } else {
            effectsGain = amplitude;
        }
    }

    /** 0–100, where 100 means music is untouched by effects and 0 means it disappears under them. */
    void setDuckDepth(int percent) {
        duckDepth = Math.max(0, Math.min(100, percent)) / 100.0f;
    }

    /**
     * Chooses an output device by name, or restores the system default when blank.
     *
     * <p>⚠ Takes effect on the next start, and the current line is dropped so that "next" is
     * immediate. Re-opening under a playing voice is the only way a device change can be heard at the
     * moment it is made, and a setting that appears to do nothing until a restart is one players
     * conclude is broken.
     */
    void setDevice(String name) {
        String wanted = name == null ? "" : name;
        if (wanted.equals(preferredDevice)) {
            return;
        }
        preferredDevice = wanted;
        synchronized (lock) {
            // ⚠ `broken` is cleared as well. A device that failed to open is not evidence that the
            // NEXT one will, and leaving the engine latched off would make the picker unable to
            // recover from a single bad choice.
            broken = false;
            closeLine();
            lock.notifyAll();
        }
    }

    /**
     * Queues a voice.
     *
     * <p>⚠ Safe from any thread and never blocks — it is called from the FX thread, where a wait on
     * the audio device would be a frozen interface. Nothing is decoded here; the voice arrives ready.
     */
    void play(Voice voice) {
        if (voice == null || master <= 0.0f) {
            return;
        }
        synchronized (lock) {
            if (broken) {
                return;
            }
            if (voices.size() >= MAX_VOICES && !steal()) {
                return;
            }
            voices.add(voice);
            start();
            lock.notifyAll();
        }
    }

    /** Fades out every music voice. Effects are left alone — they are already momentary. */
    void stopMusic(int fadeFrames) {
        synchronized (lock) {
            for (Voice voice : voices) {
                if (voice.bus() == Bus.MUSIC) {
                    voice.release(fadeFrames);
                }
            }
        }
    }

    /** Whether any music voice is still alive, so the facade need not track it separately. */
    boolean musicPlaying() {
        synchronized (lock) {
            for (Voice voice : voices) {
                if (voice.bus() == Bus.MUSIC) {
                    return true;
                }
            }
            return false;
        }
    }

    void duck(boolean on) {
        ducking = on;
    }

    /**
     * What the Settings page reports. Never guesses — every field is what actually happened.
     *
     * <p>⚠ The record itself lives on {@link Audio}, not here. This class is package-private so that
     * nothing outside the package can open a device or place a voice outside the player's sliders,
     * and a public nested type on a package-private class is unreachable anyway — the compiler says
     * so, which is how this was found. Putting it on the facade keeps the one-public-door rule intact
     * instead of widening this class to satisfy a readout.
     */
    Audio.Status status() {
        synchronized (lock) {
            return new Audio.Status(running && !broken, broken, deviceName, voices.size());
        }
    }

    // ── the engine ───────────────────────────────────────────────────────────────────────────────

    /** Must hold {@link #lock}. */
    private void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::run, "eas-audio");
        // ⚠ Daemon, so a client that is closing is never held open by the sound thread. The mix loop
        // has no natural end and would otherwise keep the JVM alive after the last window closed.
        worker.setDaemon(true);
        // ⚠ Above normal, because the deadline here is real and the consequence of missing it is an
        // audible click. Deliberately not MAX_PRIORITY: this must never outrank the FX thread, since
        // a smooth soundtrack over a stuttering interface is the wrong trade.
        worker.setPriority(Thread.NORM_PRIORITY + 1);
        worker.start();
    }

    /**
     * Drops the quietest voice to make room. Must hold {@link #lock}.
     *
     * <p>⚠ Music is never stolen. A music bed is long, continuous and the most conspicuous thing in
     * the mix — losing it to a burst of interface clicks would be the loudest possible symptom of a
     * problem in the quietest possible part of the code.
     */
    private boolean steal() {
        int victim = -1;
        float quietest = Float.MAX_VALUE;
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            if (voice.bus() == Bus.MUSIC) {
                continue;
            }
            float level = voice.level();
            if (level < quietest) {
                quietest = level;
                victim = i;
            }
        }
        if (victim < 0) {
            return false;
        }
        voices.remove(victim);
        return true;
    }

    private void run() {
        float[] accumulator = new float[BUFFER_FRAMES * 2];
        byte[] bytes = new byte[BUFFER_FRAMES * 4];
        List<Voice> playing = new ArrayList<>();
        List<Voice> dead = new ArrayList<>();

        while (running) {
            synchronized (lock) {
                while (running && voices.isEmpty()) {
                    closeLine();
                    try {
                        lock.wait();
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
                if (!running) {
                    break;
                }
                playing.clear();
                playing.addAll(voices);
            }

            if (!openLine()) {
                // The device is unavailable. Drop everything queued rather than spinning on it —
                // `broken` latches, so nothing will be queued again this session.
                synchronized (lock) {
                    voices.clear();
                }
                continue;
            }

            java.util.Arrays.fill(accumulator, 0.0f);
            dead.clear();

            // ⚠ Whether effects are sounding is decided from the voice LIST, before mixing, not from
            // the samples afterwards. A voice on the effects bus is by definition about to make a
            // noise, and asking the buffer instead would mean knowing the duck target only after the
            // buffer it applies to had already been mixed.
            boolean effectsSounding = false;
            for (Voice voice : playing) {
                if (voice.bus() == Bus.EFFECTS) {
                    effectsSounding = true;
                    break;
                }
            }
            float musicBus = musicBusGain(effectsSounding);
            float effectsBus = effectsGain;

            for (Voice voice : playing) {
                // ⚠ Each voice mixes into a SHARED accumulator, so this is where polyphony actually
                // happens: two of the same effect at once are two entries in this list, summed. Each
                // is handed its own bus gain, which is the only arrangement that is correct when both
                // buses are sounding — see Voice#mix.
                float bus = voice.bus() == Bus.MUSIC ? musicBus : effectsBus;
                if (!voice.mix(accumulator, BUFFER_FRAMES, bus)) {
                    dead.add(voice);
                }
            }

            if (!dead.isEmpty()) {
                synchronized (lock) {
                    voices.removeAll(dead);
                }
                for (Voice voice : dead) {
                    if (voice instanceof Voice.Streamed streamed) {
                        streamed.close();
                    }
                }
            }

            for (int i = 0; i < accumulator.length; i++) {
                int value = Math.round(Gain.limit(accumulator[i] * master) * 32767.0f);
                bytes[i * 2] = (byte) (value & 0xFF);
                bytes[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
            }

            try {
                // The blocking write that paces this entire loop. See the class note.
                line.write(bytes, 0, bytes.length);
            } catch (Exception | UnsatisfiedLinkError lost) {
                LOG.log(Level.FINE, "audio device went away", lost);
                synchronized (lock) {
                    closeLine();
                    voices.clear();
                }
            }
        }
        synchronized (lock) {
            closeLine();
        }
    }

    /**
     * The music bus multiplier for this buffer, stepping the duck one buffer closer to its target.
     *
     * <h2>⚠ THE DUCK RAMPS; IT NEVER STEPS</h2>
     *
     * Setting the music gain straight to its ducked value the moment an effect starts is a
     * discontinuity in a continuous signal, which is heard as a click — so an anti-distraction
     * feature would announce itself with the single most distracting artefact available. Moving 15%
     * of the remaining distance per buffer settles in about 150 ms, which reads as the music
     * stepping politely aside.
     *
     * <p>⚠ Called exactly once per buffer, because it has a side effect. Calling it twice would
     * advance the ramp at double rate, and calling it not at all would leave a duck permanently
     * engaged after the effect that caused it had finished.
     */
    private float musicBusGain(boolean effectsSounding) {
        float target = ducking && effectsSounding ? duckDepth : 1.0f;
        duckCurrent += (target - duckCurrent) * DUCK_STEP;
        return musicGain * duckCurrent;
    }

    /** Must hold {@link #lock} for the failure path. Returns false when the device is unusable. */
    private boolean openLine() {
        if (line != null) {
            return true;
        }
        if (broken) {
            return false;
        }
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, Sample.CANONICAL);
            SourceDataLine opened = null;
            String wanted = preferredDevice;
            if (!wanted.isBlank()) {
                for (Mixer.Info candidate : AudioSystem.getMixerInfo()) {
                    if (candidate.getName().equals(wanted)) {
                        Mixer mixer = AudioSystem.getMixer(candidate);
                        if (mixer.isLineSupported(info)) {
                            opened = (SourceDataLine) mixer.getLine(info);
                            deviceName = candidate.getName();
                        }
                        break;
                    }
                }
            }
            if (opened == null) {
                // ⚠ Falls back to the default rather than failing. A device named in settings may
                // simply have been unplugged since, and silence is a much worse answer than "the
                // headphones you chose are gone, so this is coming out of the speakers".
                opened = AudioSystem.getSourceDataLine(Sample.CANONICAL);
                deviceName = wanted.isBlank() ? "system default" : "system default (" + wanted + " unavailable)";
            }
            opened.open(Sample.CANONICAL, BUFFER_FRAMES * 4 * DEVICE_BUFFERS);
            opened.start();
            line = opened;
            LOG.fine(() -> "audio out: " + deviceName + ", buffer " + line.getBufferSize() + " bytes");
            return true;
        } catch (Exception | UnsatisfiedLinkError unavailable) {
            // ⚠ Latches. A headless build box, a machine with no mixer, a device held exclusively by
            // something else: none of those resolve by trying again on the next sound, and retrying
            // per effect would put a device probe on the path of every notification.
            LOG.log(Level.FINE, "no audio device; sound is off for this session", unavailable);
            broken = true;
            deviceName = "";
            return false;
        }
    }

    private void closeLine() {
        if (line == null) {
            return;
        }
        try {
            line.stop();
            line.flush();
            line.close();
        } catch (Exception | UnsatisfiedLinkError ignored) {
            // Closing a line that has already gone away is not a failure worth reporting.
        }
        line = null;
    }

    @Override
    public void close() {
        Thread stopping;
        synchronized (lock) {
            running = false;
            voices.clear();
            stopping = worker;
            worker = null;
            lock.notifyAll();
        }
        if (stopping != null) {
            try {
                // ⚠ Bounded. A blocked native write must never hold up the client's shutdown, and the
                // thread is a daemon, so the worst case of not joining is that it is killed with the
                // JVM rather than leaking.
                stopping.join(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
