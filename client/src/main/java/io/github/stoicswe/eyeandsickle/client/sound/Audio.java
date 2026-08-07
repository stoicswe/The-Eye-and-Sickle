package io.github.stoicswe.eyeandsickle.client.sound;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * The client's sound, and the only class in this package the rest of the client may touch.
 *
 * <p>Everything else here — {@link SoftMixer}, {@link Voice}, {@link Sample}, {@link Gain},
 * {@link Tone} — is package-private. A view that reached the mixer directly could open a device, hold
 * a voice, or apply a gain outside the player's sliders, and none of those would be visible from
 * Settings. One door means one place where the player's decisions are enforced.
 *
 * <h2>⚠ NOTHING HERE BLOCKS THE CALLER, AND THAT IS A HARD RULE</h2>
 *
 * {@link #play} is called from the FX thread, sometimes during a repaint. It never touches a file,
 * never opens a device and never waits on the audio thread. The first play of a sound that has not
 * been decoded yet is handed to {@link #loader} and arrives a few milliseconds late; every play after
 * that takes the resident path. {@link #warmUp()} at startup makes even the first one resident.
 *
 * <h2>⚠ THE WHOLE FACILITY FAILS SILENT, AND IT FAILS ONCE</h2>
 *
 * A headless build box, a machine with no mixer, a device held exclusively by something else, an
 * asset that will not decode: none of those is worth a dialog, a stack trace, or a retry per sound.
 * The mixer latches off and every call here becomes a cheap no-op. Sound is decoration — the message
 * still arrives, the notice is still on screen, the log still has it.
 *
 * <h2>⚠ REDUCE MOTION DOES NOT SILENCE ANYTHING, AND MUST NOT</h2>
 *
 * {@code Pulse.reducedMotion} suppresses decorative movement under WCAG 2.2.2, and this package
 * deliberately does not consult it. Sound is not motion; a player who cannot tolerate moving elements
 * has said nothing whatever about audio, and treating one setting as the other would take away the
 * one channel that reaches somebody who is not looking at the screen. The controls that <i>do</i>
 * govern audio are the sliders, which is what WCAG 1.4.2 asks for — see {@link Bus}.
 */
public final class Audio implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Audio.class.getName());

    private static final Audio SHARED = new Audio();

    /**
     * ⚠ A singleton for the reason {@code RichPresence} is one: there is one pair of speakers, and
     * threading an instance through every view that might one day make a noise would be an invasive
     * change to reach a fact about the machine rather than about the game.
     */
    public static Audio shared() {
        return SHARED;
    }

    private final SoftMixer mixer = new SoftMixer();

    /**
     * Decodes off the calling thread.
     *
     * <h2>⚠ ONE THREAD, AND SEPARATE FROM THE MIXER'S</h2>
     *
     * Decoding on the <b>mixer</b> thread would put a multi-megabyte music decode behind a hard
     * deadline, and the buffer it delayed would be heard as a gap. Decoding on the <b>FX</b> thread
     * would drop frames. So a third, and only one of it: loads are rare, ordering them costs nothing,
     * and a pool would let a burst of first-plays start several decodes at once for no benefit.
     *
     * <p>⚠ Daemon, so a client that is closing is never held open by a sound that was about to load.
     */
    private final ExecutorService loader = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "eas-audio-loader");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * ⚠ Per-play pitch variation only. Nothing derived from this reaches a rule — see {@link Tone}'s
     * note on why that is what makes an unseeded generator acceptable in a codebase that otherwise
     * commits its randomness back to the save.
     */
    private final Random variation = new Random();

    private volatile MusicCue current = MusicCue.NONE;

    private volatile boolean muted;

    private volatile int masterPercent = 60;

    private volatile int crossfadeMs = 1500;

    private Audio() {}

    // ── volume and routing ───────────────────────────────────────────────────────────────────────

    /**
     * Sets the master level, 0–100.
     *
     * <p>⚠ Zero is genuinely silent and stops the device being opened at all, rather than playing at
     * zero gain — {@link Gain#amplitude} keeps that exact. A muted client should not be holding a
     * mixer line, and on some drivers a zero-gain write is still an audible click.
     */
    public void setMasterVolume(int percent) {
        masterPercent = percent;
        pushMaster();
    }

    public void setBusVolume(Bus bus, int percent) {
        mixer.setBus(bus, percent);
    }

    /**
     * Mutes without disturbing the player's levels — for "silence when the window is not focused".
     *
     * <p>⚠ Kept separate from the master slider rather than setting it to zero. Writing zero into the
     * setting would destroy whatever the player had chosen the first time they alt-tabbed, and they
     * would come back to a game that had forgotten its own volume.
     */
    public void setMuted(boolean silent) {
        muted = silent;
        pushMaster();
        if (silent) {
            // ⚠ Music is stopped rather than left running silently. A bed that kept advancing behind a
            // mute would come back seconds or minutes further along, which sounds like the track
            // skipped; and there is no reason to hold the device for audio nobody can hear.
            mixer.stopMusic(fadeFrames(250));
        } else if (current != MusicCue.NONE) {
            startMusic(current, 250);
        }
    }

    private void pushMaster() {
        mixer.setMaster(muted ? 0 : masterPercent);
    }

    /** How much music is pulled down while an effect sounds, 0–100 (100 = not at all). */
    public void setDuckDepth(int percent) {
        mixer.setDuckDepth(percent);
    }

    public void setDuckingEnabled(boolean enabled) {
        mixer.duck(enabled);
    }

    public void setCrossfadeMs(int ms) {
        crossfadeMs = Math.max(0, ms);
    }

    /** Chooses an output device by its mixer name; blank means the system default. */
    public void setDevice(String name) {
        mixer.setDevice(name);
    }

    /**
     * The output devices this machine offers, by name, for the Settings picker.
     *
     * <p>⚠ Filtered to mixers that can actually take the engine's format. Listing every mixer would
     * offer the player their microphone and their MIDI ports as places to send the soundtrack.
     */
    public static List<String> outputDevices() {
        List<String> names = new ArrayList<>();
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, Sample.CANONICAL);
            for (Mixer.Info candidate : AudioSystem.getMixerInfo()) {
                if (AudioSystem.getMixer(candidate).isLineSupported(info)) {
                    names.add(candidate.getName());
                }
            }
        } catch (Exception | UnsatisfiedLinkError unavailable) {
            LOG.log(Level.FINE, "could not enumerate audio devices", unavailable);
        }
        return names;
    }

    /** What Settings reports. Never a guess — every field is what actually happened. */
    public Status status() {
        return mixer.status();
    }

    /**
     * The engine's real state, for the Settings readout.
     *
     * @param running the device is open and audio is flowing
     * @param failed no usable device; sound is off for the rest of the session
     * @param device what it is actually playing through, which may not be what was asked for
     * @param voices how many sounds are live right now
     */
    public record Status(boolean running, boolean failed, String device, int voices) {}

    // ── effects ──────────────────────────────────────────────────────────────────────────────────

    /** Plays an effect at its declared gain, centred. */
    public void play(Sfx effect) {
        play(effect, 1.0f, 0.0f);
    }

    /**
     * Plays an effect with a relative gain and a stereo position.
     *
     * @param relativeGain multiplied by the effect's own {@link Sfx#gain()}; 1.0 leaves it alone
     * @param pan −1 hard left, 0 centre, +1 hard right
     */
    public void play(Sfx effect, float relativeGain, float pan) {
        if (effect == null || muted || masterPercent <= 0) {
            return;
        }
        // ⚠ Claimed BEFORE anything is queued or loaded. Claiming afterwards would let a burst all
        // pass the check and then all play, which is the exact thing the guard exists to prevent.
        if (!effect.claim()) {
            return;
        }
        if (effect.resident()) {
            queue(effect, relativeGain, pan);
            return;
        }
        // First play of this sound: decode off this thread. A few milliseconds late, never blocking.
        loader.execute(() -> queue(effect, relativeGain, pan));
    }

    private void queue(Sfx effect, float relativeGain, float pan) {
        Sample sample = effect.sample();
        if (sample == null) {
            return;
        }
        double spread = effect.pitchSpread();
        // A rate of 1.0 is the sample's own pitch; ±spread either side of it.
        double rate = spread <= 0 ? 1.0 : 1.0 + (variation.nextDouble() * 2 - 1) * spread;
        mixer.play(new Voice.Sampled(sample, Bus.EFFECTS, effect.gain() * relativeGain, pan, rate, false));
    }

    /** Decodes every effect ahead of time, so no first play is ever late. */
    public void warmUp() {
        loader.execute(() -> {
            for (Sfx effect : Sfx.values()) {
                effect.sample();
            }
        });
    }

    // ── music ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Crossfades to a cue, or to silence.
     *
     * <h2>⚠ IDEMPOTENT, AND IT HAS TO BE</h2>
     *
     * Call sites for music are screen changes, and a screen is re-entered constantly — a window
     * closes, a dialog dismisses, a tab is reselected. Asking for the cue that is already playing
     * must therefore be free, or the bed restarts from the top every time the player closes a window.
     * That is the single most likely way for this to go wrong and it is checked first.
     */
    public void music(MusicCue cue) {
        MusicCue wanted = cue == null ? MusicCue.NONE : cue;
        if (wanted == current && (wanted == MusicCue.NONE || mixer.musicPlaying())) {
            return;
        }
        current = wanted;
        if (muted) {
            return;
        }
        mixer.stopMusic(fadeFrames(crossfadeMs));
        if (wanted != MusicCue.NONE) {
            startMusic(wanted, crossfadeMs);
        }
    }

    /** What is playing, or what would be if the client were not muted. */
    public MusicCue currentMusic() {
        return current;
    }

    private void startMusic(MusicCue cue, int fadeMs) {
        String resource = cue.resource();
        if (resource == null) {
            return;
        }
        loader.execute(() -> {
            byte[] file = read(resource);
            if (file == null) {
                // ⚠ FINE, not WARNING. No track is shipped, so on today's build this is the normal
                // path for every cue — a warning per screen change would bury the client log within
                // a minute and would be reporting a decision rather than a fault.
                LOG.fine(() -> "no music for " + cue + "; silence");
                return;
            }
            // ⚠ Re-checked after the load. The player can change screens while a several-megabyte
            // bed is being read, and without this the cue they have left starts playing over the one
            // they have arrived at.
            if (current != cue || muted) {
                return;
            }
            mixer.play(new Voice.Streamed(cue.name(), file, true, 1.0f, fadeFrames(fadeMs)));
        });
    }

    private static byte[] read(String resource) {
        try (InputStream in = Audio.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception unreadable) {
            LOG.log(Level.FINE, "could not read " + resource, unreadable);
            return null;
        }
    }

    private static int fadeFrames(int ms) {
        return Math.max(1, (int) (Sample.CANONICAL.getSampleRate() * ms / 1000.0));
    }

    // ── lifetime ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Stops everything and releases the device.
     *
     * <p>⚠ Called from {@code EyeAndSickleClient.shutdown()}, beside {@code RichPresence.close()}, and
     * for a related reason: a process that is going away should stop holding things that belong to the
     * machine. Both threads here are daemons, so the cost of not calling it is a device held until the
     * JVM exits rather than a leak — but on macOS an audio device held by a closing application is
     * visible to the player.
     */
    @Override
    public void close() {
        mixer.close();
        loader.shutdownNow();
    }

    /** Test seam — drops decoded samples and the retrigger clocks. */
    void reset() {
        for (Sfx effect : Sfx.values()) {
            effect.reset();
        }
        current = MusicCue.NONE;
    }
}
