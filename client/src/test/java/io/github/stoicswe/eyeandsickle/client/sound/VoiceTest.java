package io.github.stoicswe.eyeandsickle.client.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The mixing itself, on buffers rather than through a device.
 *
 * <p>A voice's whole contract is "add yourself to this array and say whether you are done", which is
 * checkable by handing it an array and looking at it. That is the payoff of the accumulator design:
 * the part of the engine most likely to be subtly wrong is the part that needs no hardware to test.
 */
@DisplayName("voices")
class VoiceTest {

    /** A one-second ramp from 0 to 1, so a position in the sample is readable from its value. */
    private static Sample ramp(int frames) {
        float[] data = new float[frames * 2];
        for (int f = 0; f < frames; f++) {
            float value = (float) f / frames;
            data[f * 2] = value;
            data[f * 2 + 1] = value;
        }
        return Sample.of("ramp", data);
    }

    private static Sample flat(int frames, float value) {
        float[] data = new float[frames * 2];
        java.util.Arrays.fill(data, value);
        return Sample.of("flat", data);
    }

    @Nested
    @DisplayName("a sampled voice")
    class Sampled {

        @Test
        @DisplayName("adds to the buffer rather than overwriting it, which is what makes polyphony work")
        void accumulates() {
            // ⚠ THE SINGLE MOST IMPORTANT PROPERTY IN THE ENGINE. `=` instead of `+=` in the mix loop
            // compiles, plays one sound perfectly, and silently drops every other voice — so the
            // symptom is "only one sound plays at a time", which reads as a Clip-style limitation
            // rather than as a one-character bug.
            float[] out = new float[8];
            new Voice.Sampled(flat(4, 0.25f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false).mix(out, 4, 1.0f);
            float afterOne = out[0];
            new Voice.Sampled(flat(4, 0.25f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false).mix(out, 4, 1.0f);
            assertThat(out[0]).as("a second voice must add to the first").isEqualTo(afterOne * 2, within(0.0001f));
        }

        @Test
        @DisplayName("finishes when it runs out, so it is dropped rather than looping forever")
        void terminates() {
            Voice voice = new Voice.Sampled(flat(100, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false);
            assertThat(voice.mix(new float[400], 200, 1.0f))
                    .as("asked for more frames than it has")
                    .isFalse();
        }

        @Test
        @DisplayName("a looping voice never finishes")
        void loops() {
            Voice voice = new Voice.Sampled(flat(100, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, true);
            for (int pass = 0; pass < 10; pass++) {
                assertThat(voice.mix(new float[400], 200, 1.0f)).isTrue();
            }
        }

        @Test
        @DisplayName("the bus gain scales it, and is applied per buffer rather than baked in")
        void busGainApplies() {
            // ⚠ This is what makes dragging the volume slider audible on music that is ALREADY
            // playing. Fold the gain into the voice at construction and a volume change applies only
            // to sounds started afterwards — which is the one case nobody tests, because you test a
            // volume slider by moving it while something is playing.
            float[] loud = new float[8];
            float[] quiet = new float[8];
            new Voice.Sampled(flat(4, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false).mix(loud, 4, 1.0f);
            new Voice.Sampled(flat(4, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false).mix(quiet, 4, 0.5f);
            assertThat(quiet[0]).isEqualTo(loud[0] / 2, within(0.0001f));
        }

        @Test
        @DisplayName("a faster rate consumes the sample sooner, which is what pitch variation is")
        void rateShiftsPitch() {
            // At double rate a 200-frame sample is exhausted in ~100 output frames.
            Voice fast = new Voice.Sampled(flat(200, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 2.0, false);
            assertThat(fast.mix(new float[240], 120, 1.0f))
                    .as("double rate should run out inside 120 frames")
                    .isFalse();
            Voice normal = new Voice.Sampled(flat(200, 0.5f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false);
            assertThat(normal.mix(new float[240], 120, 1.0f))
                    .as("unit rate should still have material left")
                    .isTrue();
        }

        @Test
        @DisplayName("panning is constant power, so a centred sound is not quieter than a hard-panned one")
        void constantPowerPan() {
            // ⚠ A linear pan law loses about 3 dB in the middle, so every centred sound — which is
            // most of them — sits quieter than the same sound panned hard over. The symptom is that
            // panning anything appears to make it louder.
            float[] centre = new float[8];
            float[] left = new float[8];
            new Voice.Sampled(flat(4, 1.0f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, false).mix(centre, 4, 1.0f);
            new Voice.Sampled(flat(4, 1.0f), Bus.EFFECTS, 1.0f, -1.0f, 1.0, false).mix(left, 4, 1.0f);

            double centrePower = centre[0] * centre[0] + centre[1] * centre[1];
            double leftPower = left[0] * left[0] + left[1] * left[1];
            assertThat(centrePower).isEqualTo(leftPower, within(0.0001));
        }

        @Test
        @DisplayName("hard left puts nothing in the right channel")
        void panSeparates() {
            float[] out = new float[8];
            new Voice.Sampled(flat(4, 1.0f), Bus.EFFECTS, 1.0f, -1.0f, 1.0, false).mix(out, 4, 1.0f);
            assertThat(out[0]).as("left").isGreaterThan(0.9f);
            assertThat(out[1]).as("right").isEqualTo(0.0f, within(0.0001f));
        }

        @Test
        @DisplayName("release fades it out and then ends it, rather than cutting")
        void releaseFades() {
            Voice voice = new Voice.Sampled(flat(10_000, 1.0f), Bus.EFFECTS, 1.0f, 0.0f, 1.0, true);
            voice.release(100);
            float[] out = new float[200];
            voice.mix(out, 100, 1.0f);
            // Falling across the buffer rather than stopping at a step, which would be a click.
            assertThat(out[0]).isGreaterThan(out[100]);
            assertThat(voice.mix(new float[200], 100, 1.0f))
                    .as("finished once the envelope reaches zero")
                    .isFalse();
        }

        @Test
        @DisplayName("interpolates between frames instead of stepping to the nearest")
        void interpolates() {
            // A ramp read at half rate should land BETWEEN the source values, not repeat them.
            // Nearest-neighbour resampling adds a broadband hiss that is most audible exactly where
            // the ear is most sensitive to it — on a sound's quiet tail.
            //
            // ⚠ Asserted as a MIDPOINT rather than against a literal, and the first version of this
            // test got that wrong: it expected 0.005 and measured 0.00354, because a centred voice is
            // scaled by cos(π/4) under the constant-power pan law. The code was right and the
            // expectation had quietly encoded a second rule. A ratio between three output samples
            // depends on neither the pan law nor the gain, so it can only fail if the interpolation
            // itself changes.
            float[] out = new float[8];
            new Voice.Sampled(ramp(100), Bus.EFFECTS, 1.0f, 0.0f, 0.5, false).mix(out, 4, 1.0f);
            assertThat(out[2])
                    .as("stepping to the nearest frame would repeat frame 0")
                    .isNotEqualTo(out[0]);
            assertThat(out[2])
                    .as("output frame 1 sits half way between frames 0 and 2")
                    .isEqualTo((out[0] + out[4]) / 2, within(0.00001f));
        }
    }

    @Nested
    @DisplayName("a streamed voice")
    class Streamed {

        @Test
        @DisplayName("decodes a real WAV and mixes it")
        void decodesAWav() {
            byte[] wav = Wavs.tone(0.25f, 4410);
            Voice.Streamed voice = new Voice.Streamed("probe", wav, false, 1.0f, 1);
            float[] out = new float[2000];
            assertThat(voice.mix(out, 1000, 1.0f)).isTrue();

            float peak = 0;
            for (float value : out) {
                peak = Math.max(peak, Math.abs(value));
            }
            assertThat(peak).as("decoded to silence").isGreaterThan(0.05f);
            voice.close();
        }

        @Test
        @DisplayName("a non-looping stream ends when the file does")
        void endsAtTheEnd() {
            Voice.Streamed voice = new Voice.Streamed("probe", Wavs.tone(0.25f, 441), false, 1.0f, 1);
            boolean alive = true;
            for (int pass = 0; pass < 200 && alive; pass++) {
                alive = voice.mix(new float[2000], 1000, 1.0f);
            }
            assertThat(alive).as("should have run out well before 200 buffers").isFalse();
            voice.close();
        }

        @Test
        @DisplayName("a looping stream re-opens instead of ending")
        void loopsForever() {
            Voice.Streamed voice = new Voice.Streamed("probe", Wavs.tone(0.25f, 441), true, 1.0f, 1);
            for (int pass = 0; pass < 200; pass++) {
                assertThat(voice.mix(new float[2000], 1000, 1.0f))
                        .as("pass %d", pass)
                        .isTrue();
            }
            voice.close();
        }

        @Test
        @DisplayName("fades in from silence rather than starting at full level")
        void fadesIn() {
            // A music bed that appeared at full volume would be a step into continuous material,
            // which is the click the crossfade exists to avoid.
            int fade = 4410;
            Voice.Streamed voice = new Voice.Streamed("probe", Wavs.tone(0.5f, 44100), true, 1.0f, fade);
            float[] first = new float[2000];
            voice.mix(first, 1000, 1.0f);

            float early = peak(first);
            float[] later = new float[2000];
            for (int pass = 0; pass < 8; pass++) {
                java.util.Arrays.fill(later, 0f);
                voice.mix(later, 1000, 1.0f);
            }
            assertThat(peak(later)).as("should be louder once the fade has run").isGreaterThan(early);
            voice.close();
        }

        @Test
        @DisplayName("release ends it, so a crossfade finishes rather than leaving a voice resident")
        void releaseEnds() {
            Voice.Streamed voice = new Voice.Streamed("probe", Wavs.tone(0.5f, 44100), true, 1.0f, 1);
            voice.mix(new float[2000], 1000, 1.0f);
            voice.release(500);
            boolean alive = true;
            for (int pass = 0; pass < 10 && alive; pass++) {
                alive = voice.mix(new float[2000], 1000, 1.0f);
            }
            assertThat(alive)
                    .as("a released stream must not stay resident forever")
                    .isFalse();
            voice.close();
        }

        @Test
        @DisplayName("undecodable bytes are silence, not an exception")
        void rubbishIsSilent() {
            // The failure mode for a corrupt or truncated asset has to be "no music", never a throw
            // on the loader thread — which would take the rest of the catalogue's loading with it.
            Voice.Streamed voice = new Voice.Streamed("junk", new byte[] {1, 2, 3, 4, 5}, true, 1.0f, 1);
            assertThat(voice.mix(new float[2000], 1000, 1.0f)).isFalse();
            voice.close();
        }

        private float peak(float[] buffer) {
            float peak = 0;
            for (float value : buffer) {
                peak = Math.max(peak, Math.abs(value));
            }
            return peak;
        }
    }
}
