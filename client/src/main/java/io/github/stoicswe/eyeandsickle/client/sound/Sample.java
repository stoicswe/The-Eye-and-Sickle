package io.github.stoicswe.eyeandsickle.client.sound;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * A decoded sound, in the engine's one internal format, ready to be mixed.
 *
 * <h2>⚠ ONE CANONICAL FORMAT, CONVERTED AT LOAD, NEVER AT PLAY</h2>
 *
 * Every sample in the game is stored here as 44.1 kHz interleaved stereo float, whatever the file on
 * disk was. Doing the conversion once, when the sound is loaded, is what lets {@link SoftMixer}'s
 * inner loop be a multiply and an add: a mixer that had to ask each voice for its sample rate and
 * channel count would be doing format negotiation per buffer, on the one thread that must never miss
 * a deadline.
 *
 * <p>Floats rather than shorts because that is what mixing wants — summing many voices needs
 * headroom above full scale, which an integer format does not have, and the conversion back down
 * happens once per buffer at the very end with {@link Gain#limit} standing between it and a wrap.
 *
 * <h2>⚠ THE JDK RESAMPLES, AND THIS WAS MEASURED RATHER THAN ASSUMED</h2>
 *
 * {@code AudioSystem.getAudioInputStream(target, source)} performs sample-rate, channel-count and
 * bit-depth conversion. That is not obvious from the API — it reads like a format <i>assertion</i>
 * — and the shipped chime is 11 kHz mono, so this path is exercised by the only asset in the game.
 * Verified on both JDKs on the development machine (OpenJDK 26 / Homebrew and OpenJ9 26 / Semeru):
 * 9,924 frames at 11,025 Hz mono became 39,704 frames at 44,100 Hz stereo, preserving the 0.900 s
 * duration exactly. ⚠ Unlike secp256k1 — which {@code protocol/crypto} records as behaving
 * <i>differently</i> on those same two runtimes — the two agreed here, and that agreement is why
 * this is a one-line conversion rather than a probe with a fallback.
 *
 * <h2>⚠ WHAT CAN BE DECODED IS A CLASSPATH QUESTION, NOT A CODE QUESTION</h2>
 *
 * {@code javax.sound.sampled} is an <b>SPI</b>: {@code AudioSystem} asks every
 * {@code AudioFileReader} on the classpath whether it recognises a stream. The JDK ships three —
 * measured, on this machine, with {@code AudioSystem.getAudioFileTypes()}: <b>WAVE, AU, AIFF</b>.
 * Nothing here names any of them, so dropping a Vorbis or MP3 service provider onto the classpath
 * would make those formats load through this same method with <b>no change to this file</b>.
 *
 * <p>⚠ That matters because of size, and the arithmetic is worth having in front of you before
 * anybody commits a soundtrack. Uncompressed 16-bit PCM costs, per minute of music:
 *
 * <pre>
 *   44.1 kHz stereo   10.6 MB     22.05 kHz stereo   5.3 MB
 *   44.1 kHz mono      5.3 MB     22.05 kHz mono     2.6 MB
 * </pre>
 *
 * and this client ships <b>five platform uber jars plus a jpackage image</b>, so a figure here is
 * multiplied by six before it reaches a release. A five-track soundtrack at two minutes each is
 * ~26 MB as 22 kHz mono WAV and ~1.5 MB as Vorbis. The engine is deliberately indifferent; the
 * decision is a dependency one and belongs to whoever has the actual music.
 */
final class Sample {

    private static final Logger LOG = Logger.getLogger(Sample.class.getName());

    /**
     * The engine's internal format: 44.1 kHz, 16-bit, stereo, little-endian, signed.
     *
     * <p>⚠ 44.1 rather than 48 kHz because the material is a game's effects rather than video
     * post-production, every consumer device handles it, and it is the rate assets are most likely to
     * arrive in — a conversion that turns out to be the identity costs nothing.
     *
     * <p>⚠ Stereo even though every sound the game has today is mono. The channel count is fixed at
     * the <i>device</i>, so making it mono would mean re-opening the line the first time anything
     * needed to be panned, and panning is how the interface will eventually say <i>where</i>
     * something happened. Doubling a mono sample costs one copy at load.
     */
    static final AudioFormat CANONICAL =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false);

    /** Interleaved stereo, −1..+1. Left at index 0, right at 1, left at 2, and so on. */
    private final float[] frames;

    private final String name;

    private Sample(String name, float[] frames) {
        this.name = name;
        this.frames = frames;
    }

    /** How many stereo frames — half the length of the backing array. */
    int frameCount() {
        return frames.length / 2;
    }

    float[] data() {
        return frames;
    }

    String name() {
        return name;
    }

    /** Built from generated samples rather than a file. See {@link Tone}. */
    static Sample of(String name, float[] interleavedStereo) {
        return new Sample(name, interleavedStereo);
    }

    /**
     * Decodes a classpath resource, or returns null if it cannot be.
     *
     * <h2>⚠ NULL RATHER THAN AN EXCEPTION, AND THE CALLER MUST NOT RETRY</h2>
     *
     * A missing or undecodable sound is not worth a dialog, a stack trace on the path that was about
     * to tell the player something, or a second attempt on every play. {@link Sfx} caches the null
     * alongside the successes for exactly that reason. Sound is decoration; the message still
     * arrives.
     */
    static Sample load(String name, String resourcePath) {
        try (InputStream raw = Sample.class.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                LOG.fine(() -> "no such sound on the classpath: " + resourcePath);
                return null;
            }
            // ⚠ Read fully into memory before handing it to AudioSystem. Format sniffing needs a
            // mark-supporting stream, and a resource stream from inside a jar does not reliably
            // provide one — the failure is an UnsupportedAudioFileException on a file that is
            // perfectly valid, which sends you looking at the asset instead of at the stream.
            byte[] bytes = raw.readAllBytes();
            try (AudioInputStream source =
                            AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
                    AudioInputStream canonical = AudioSystem.getAudioInputStream(CANONICAL, source)) {
                byte[] pcm = canonical.readAllBytes();
                Sample sample = new Sample(name, toFloat(pcm, pcm.length));
                LOG.fine(() -> "loaded " + name + ": " + sample.frameCount() + " frames");
                return sample;
            }
        } catch (Exception | UnsatisfiedLinkError unavailable) {
            // ⚠ Catches Error as well as Exception. A machine with no audio stack fails in the native
            // layer, and a notification path that threw would take the notification down with it.
            LOG.log(Level.FINE, unavailable, () -> "could not decode " + name);
            return null;
        }
    }

    /**
     * 16-bit little-endian signed PCM to −1..+1 float.
     *
     * <p>⚠ Divided by 32768 rather than 32767. The two-lengths of a signed 16-bit range are not
     * symmetric — it runs −32768..+32767 — so 32768 is the value that maps the most negative sample
     * to exactly −1.0 and cannot produce anything outside the range. Dividing by 32767 puts full-scale
     * negative material a hair past −1.0, which is the one condition {@link Gain#limit} exists to
     * catch and would mean the limiter engaging on audio that never actually clipped.
     */
    static float[] toFloat(byte[] pcm, int length) {
        int usable = length - (length % 2);
        float[] out = new float[usable / 2];
        for (int i = 0, s = 0; i < usable; i += 2, s++) {
            int lo = pcm[i] & 0xFF;
            int hi = pcm[i + 1]; // signed: this is the high byte and carries the sign
            out[s] = ((hi << 8) | lo) / 32768.0f;
        }
        return out;
    }
}
