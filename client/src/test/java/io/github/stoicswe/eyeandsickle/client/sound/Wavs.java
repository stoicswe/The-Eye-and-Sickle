package io.github.stoicswe.eyeandsickle.client.sound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Builds real WAV files in memory, for the tests that need one.
 *
 * <h2>⚠ A REAL WAV, NOT A HAND-WRITTEN HEADER, AND NOT A FIXTURE FILE</h2>
 *
 * The streamed voice's whole job is to drive {@code AudioSystem}'s decoder, so a test that fed it a
 * hand-assembled byte array would be testing this helper's understanding of the RIFF format rather
 * than the engine's. {@link AudioSystem#write} produces exactly what the decoder expects, by
 * definition.
 *
 * <p>Generated rather than committed because a binary fixture is a file nobody can review in a diff,
 * and because generating one lets a test ask for the specific length or rate it needs.
 *
 * <h2>⚠ DELIBERATELY NOT AT THE ENGINE'S CANONICAL FORMAT</h2>
 *
 * These come out as <b>22.05 kHz mono</b> while the engine mixes at 44.1 kHz stereo, so every test
 * using one exercises the conversion path rather than an accidental identity. That matters: the
 * shipped chime is 11 kHz mono, so resampling is on the only real asset in the game, and a fixture
 * that happened to match the canonical format would leave it untested.
 *
 * <p>This opens no audio device — writing and decoding are file operations, and run on a build box
 * with no sound hardware at all.
 */
final class Wavs {

    private Wavs() {}

    private static final float RATE = 22050f;

    /** A 440 Hz sine at the requested peak, {@code frames} long, as a mono 16-bit WAV. */
    static byte[] tone(float peak, int frames) {
        byte[] pcm = new byte[frames * 2];
        for (int f = 0; f < frames; f++) {
            double value = Math.sin(2 * Math.PI * 440 * f / RATE) * peak;
            int sample = (int) Math.round(value * 32767);
            pcm[f * 2] = (byte) (sample & 0xFF);
            pcm[f * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RATE, 16, 1, 2, RATE, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
        } catch (Exception impossible) {
            throw new IllegalStateException("could not build a WAV in memory", impossible);
        }
        return out.toByteArray();
    }
}
