package io.github.stoicswe.eyeandsickle.client.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic of loudness, checked without a sound card.
 *
 * <p>Everything in {@link Gain} is a pure function, which is the whole reason it is a separate class:
 * the rules that decide what a player hears are checkable on a headless build box, where no test that
 * opened an audio device could run at all.
 */
@DisplayName("gain")
class GainTest {

    @Nested
    @DisplayName("the volume taper")
    class Taper {

        @Test
        @DisplayName("zero is exactly silent, and not merely very quiet")
        void zeroIsSilent() {
            // ⚠ Load-bearing rather than pedantic. SoftMixer uses a zero master to skip opening the
            // device at all, so a taper that returned 1e-4 for "off" would leave a muted client
            // holding a mixer line — and on some drivers a zero-gain write is still an audible click.
            assertThat(Gain.amplitude(0)).isEqualTo(0.0f);
        }

        @Test
        @DisplayName("a hundred is full scale, and not a hair under")
        void fullIsFull() {
            // Anything below 1.0 here would mean the game could never reach the volume the player's
            // own system volume is set to, which reads as the game being quiet on every machine.
            assertThat(Gain.amplitude(100)).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("it is a square law, so the middle of the slider is a quarter of the amplitude")
        void squareLaw() {
            assertThat(Gain.amplitude(50)).isEqualTo(0.25f, within(0.001f));
            assertThat(Gain.amplitude(25)).isEqualTo(0.0625f, within(0.001f));
            // The shipped default. Recorded here because it is the number a player hears first.
            assertThat(Gain.amplitude(60)).isEqualTo(0.36f, within(0.001f));
        }

        @Test
        @DisplayName("it never falls as the slider rises")
        void monotonic() {
            float previous = -1;
            for (int percent = 0; percent <= 100; percent++) {
                float now = Gain.amplitude(percent);
                assertThat(now).as("at %d%%", percent).isGreaterThanOrEqualTo(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("out-of-range input is clamped rather than producing a negative or explosive gain")
        void clamped() {
            // These come from a settings file the player can edit by hand, so they are not
            // hypothetical. A negative amplitude would invert the waveform; a large one would clip
            // everything.
            assertThat(Gain.amplitude(-40)).isEqualTo(0.0f);
            assertThat(Gain.amplitude(1000)).isEqualTo(1.0f);
        }
    }

    @Nested
    @DisplayName("the crossfade")
    class Crossfade {

        @Test
        @DisplayName("power is constant across the whole fade, which is what stops the audible dip")
        void equalPower() {
            // ⚠ THIS IS THE TEST THAT DISTINGUISHES EQUAL-POWER FROM LINEAR, and it is the whole
            // reason the trigonometry is there. Two uncorrelated signals sum in power, so what must
            // stay at 1.0 is out² + in², not out + in. A linear crossfade would put this at 0.5 in
            // the middle — a 3 dB hole heard as the music dropping out and coming back.
            for (double progress = 0.0; progress <= 1.0; progress += 0.05) {
                float out = Gain.fadeOut(progress);
                float in = Gain.fadeIn(progress);
                assertThat(out * out + in * in).as("power at %.2f", progress).isEqualTo(1.0f, within(0.0001f));
            }
        }

        @Test
        @DisplayName("it starts and ends where it should")
        void endpoints() {
            assertThat(Gain.fadeOut(0.0)).isEqualTo(1.0f, within(0.0001f));
            assertThat(Gain.fadeOut(1.0)).isEqualTo(0.0f, within(0.0001f));
            assertThat(Gain.fadeIn(0.0)).isEqualTo(0.0f, within(0.0001f));
            assertThat(Gain.fadeIn(1.0)).isEqualTo(1.0f, within(0.0001f));
        }

        @Test
        @DisplayName("progress past the end does not go negative")
        void doesNotInvertPastTheEnd() {
            // ⚠ Cosine past π/2 is NEGATIVE, so an unclamped ramp that kept being evaluated would
            // invert the track rather than hold it silent — which is not silence, it is the same
            // music with its phase flipped, at rising volume.
            assertThat(Gain.fadeOut(1.5)).isEqualTo(0.0f, within(0.0001f));
            assertThat(Gain.fadeIn(-0.5)).isEqualTo(0.0f, within(0.0001f));
        }
    }

    @Nested
    @DisplayName("the limiter")
    class Limiter {

        @Test
        @DisplayName("nothing escapes the range, which is what stops the sum wrapping into a crack")
        void bounded() {
            // ⚠ The failure this exists to prevent is not distortion, it is WRAPPING: a float just
            // over +1 converted to 16-bit without bounding comes out as a large NEGATIVE number, and
            // that is a full-scale discontinuity — a loud crack, arriving exactly when the game is
            // busiest and several voices are summing.
            for (float value = -8.0f; value <= 8.0f; value += 0.01f) {
                assertThat(Gain.limit(value)).as("limit(%f)", value).isBetween(-1.0f, 1.0f);
            }
        }

        @Test
        @DisplayName("quiet material passes through with its level essentially untouched")
        void transparentWhereItMatters() {
            // A limiter that coloured ordinary content would be a compressor nobody asked for. Unit
            // slope at the origin is what makes it inaudible until something actually needs limiting.
            assertThat(Gain.limit(0.0f)).isEqualTo(0.0f);
            assertThat(Gain.limit(0.1f)).isEqualTo(0.1f, within(0.002f));
            assertThat(Gain.limit(-0.1f)).isEqualTo(-0.1f, within(0.002f));
        }

        @Test
        @DisplayName("it is symmetric, so it adds no DC offset")
        void symmetric() {
            // An asymmetric limiter shifts the waveform's centre away from zero. That is inaudible in
            // itself and it eats headroom on one side, so the loud side starts wrapping first.
            for (float value = 0.0f; value <= 2.0f; value += 0.05f) {
                assertThat(Gain.limit(value)).isEqualTo(-Gain.limit(-value), within(0.00001f));
            }
        }

        @Test
        @DisplayName("it never folds back on itself as the input grows")
        void monotonic() {
            // ⚠ The specific trap in a cubic soft knee. y = 1.5x − 0.5x³ turns over above x = 1 and
            // starts DESCENDING, so without the hard bound above ±1 a very loud sum would come out
            // quieter than a moderately loud one — distortion that gets worse and then inverts.
            float previous = -2;
            for (float value = -4.0f; value <= 4.0f; value += 0.01f) {
                float now = Gain.limit(value);
                assertThat(now).as("limit(%f)", value).isGreaterThanOrEqualTo(previous - 0.00001f);
                previous = now;
            }
        }
    }
}
