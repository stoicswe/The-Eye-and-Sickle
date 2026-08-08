package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.EyeMark.Look;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The heat mark's eye: where it looks, and when it blinks.
 *
 * <h2>Why this is a test rather than a look at the screen</h2>
 *
 * A twelve-second sweep and a once-a-minute blink are exactly the two things a screenshot cannot
 * catch and staring at the deck cannot confirm. "Does the gaze reach both ends", "does it rest there
 * or bounce off them", "does a blink last as long as its table", "can a blink retrigger itself and
 * hold the eye shut" are answerable here in milliseconds and nowhere else in under a minute of
 * watching. {@code DiskLamp.Flicker} is the same seam for the same reason — and, like the lamp, the
 * whole state machine is a value, so no toolkit is needed.
 *
 * <p>⚠ The <b>decision</b> to blink arrives as a parameter rather than being drawn inside
 * {@link Look}, which is what makes the blink testable at all: the widget owns the seeded
 * {@code Random} and this file never has to know about it.
 */
class EyeMarkTest {

    /** One full sweep, so a test can say "a whole period" without restating the number. */
    private static final int PERIOD = EyeMark.GAZE_PERIOD_TICKS;

    private static Look after(int ticks) {
        Look look = Look.REST;
        for (int i = 0; i < ticks; i++) {
            look = look.next(false);
        }
        return look;
    }

    @Nested
    @DisplayName("at rest")
    class Rest {

        @Test
        @DisplayName("the eye is open and looking straight ahead")
        void restIsTheReadablePose() {
            // ⚠ This is the pose Reduce motion holds and the pose every render captures, so it is
            // the one that has to read as an eye. A rest pose caught mid-blink or parked at an
            // extreme would make the accessibility path — and every screenshot — the worst-looking
            // state of the widget.
            assertThat(Look.REST.gaze()).isZero();
            assertThat(Look.REST.open()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("the gaze")
    class Gaze {

        @Test
        @DisplayName("reaches both ends and returns to where it started")
        void sweeps() {
            double min = 1;
            double max = -1;
            for (int i = 0; i < PERIOD; i++) {
                double gaze = after(i).gaze();
                min = Math.min(min, gaze);
                max = Math.max(max, gaze);
            }
            assertThat(min).isEqualTo(-1);
            assertThat(max).isEqualTo(1);
            assertThat(after(PERIOD)).isEqualTo(Look.REST);
        }

        @Test
        @DisplayName("rests at each end rather than bouncing off it")
        void dwells() {
            // ⚠ THE PROPERTY THE OVERSHOOT EXISTS FOR. A pure triangle reverses the instant it
            // arrives, which reads as a pupil batted between two walls; §5 forbids easing the ends,
            // so the dwell comes from overshooting a linear sweep and clamping. The expected share
            // is derived from the constant rather than typed, so re-tuning one re-tunes the other.
            int parked = 0;
            for (int i = 0; i < PERIOD; i++) {
                if (Math.abs(after(i).gaze()) == 1) {
                    parked++;
                }
            }
            double share = parked / (double) PERIOD;
            double expected = 1 - 1 / EyeMark.GAZE_OVERSHOOT;
            assertThat(share).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.05));
            // And the bounds either side of it: no dwell at all is the bounce this exists to stop,
            // and an eye parked most of the time is one that has stopped looking.
            assertThat(share).isGreaterThan(0.2).isLessThan(0.6);
        }

        @Test
        @DisplayName("never reverses part way through a travel")
        void isMonotonicBetweenTheEnds() {
            // Guards the shape rather than the numbers: anything oscillating, eased or wobbling
            // would break this while still passing "it reaches both ends".
            int reversals = 0;
            double previous = after(0).gaze();
            double direction = 0;
            for (int i = 1; i <= PERIOD; i++) {
                double gaze = after(i).gaze();
                double step = gaze - previous;
                if (step != 0) {
                    if (direction != 0 && Math.signum(step) != direction) {
                        reversals++;
                    }
                    direction = Math.signum(step);
                }
                previous = gaze;
            }
            // ⚠ EXACTLY TWO, and the first draft asserted one. A period walked from REST starts at
            // centre, so it turns at the right end and again at the left before coming back — two
            // turns per full look, not one. Two is what a back-and-forth is; anything more is a
            // twitch, and this is the assertion that would catch one.
            assertThat(reversals).isEqualTo(2);
        }

        @Test
        @DisplayName("moves in steps small enough that the stepping is not the animation")
        void stepsAreSmall() {
            // §5 permits no interpolation, so smoothness can only come from the steps being smaller
            // than the thing being moved. The gaze spans 2.0 over its travel and the pupil's real
            // travel is a couple of pixels, so a per-tick step of a few percent is well under a
            // pixel. This is the assertion that would fire if somebody sped the sweep up.
            double worst = 0;
            for (int i = 1; i <= PERIOD; i++) {
                worst = Math.max(worst, Math.abs(after(i).gaze() - after(i - 1).gaze()));
            }
            assertThat(worst).isLessThan(0.1);
        }
    }

    @Nested
    @DisplayName("the blink")
    class Blink {

        @Test
        @DisplayName("runs its table once and reopens")
        void lastsExactlyItsTable() {
            Look look = Look.REST.next(true);
            for (double expected : EyeMark.BLINK) {
                assertThat(look.open()).isEqualTo(expected);
                look = look.next(false);
            }
            assertThat(look.open()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not happen unless something asks for one")
        void neverSpontaneous() {
            for (int i = 0; i < PERIOD * 4; i++) {
                assertThat(after(i).open()).as("open at tick %d", i).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("always finishes, however hard something asks for another")
        void cannotRetrigger() {
            // ⚠ THE OPPOSITE OF DiskLamp, DELIBERATELY. There, a write mid-burst restarts the
            // flicker, because the lamp's job is to report that something just happened. Here the
            // draw runs on every tick, so a blink that could restart itself would be re-armed before
            // it finished — and the failure is not the obvious one.
            //
            // ⚠ MEASURED AGAINST THE BROKEN VERSION: re-arming does not hold the eye SHUT, it jams
            // it HALF-CLOSED at the first entry of the table, forever. The first draft of this test
            // asserted "never shut on two consecutive ticks" and passed against exactly that build —
            // a regression test green on the bug it was written for. The property that actually
            // holds the line is that a blink COMPLETES: once one starts, the eye is fully open again
            // within the table's own length, whatever is asked of it in between.
            Look look = Look.REST;
            int sinceOpen = 0;
            for (int i = 0; i < 500; i++) {
                look = look.next(true);
                sinceOpen = look.open() == 1 ? 0 : sinceOpen + 1;
                assertThat(sinceOpen)
                        .as("ticks since the eye was last fully open, at %d", i)
                        .isLessThanOrEqualTo(EyeMark.BLINK.length);
            }
        }

        @Test
        @DisplayName("is rare — not more often than once in half a minute, on average")
        void isRare() {
            // ⚠ Asserted as an INTERVAL rather than as the probability, because "rare" is the
            // property somebody cares about and the probability is only how it is spelled. A person
            // blinks every few seconds; an eye on the status strip that did would be a spinner.
            // Pulse's driver is 100ms, so 300 ticks is thirty seconds — Pulse itself is not touched
            // here because constructing it needs a live toolkit.
            double meanIntervalTicks = 1 / EyeMark.BLINK_CHANCE_PER_TICK;
            assertThat(meanIntervalTicks).isGreaterThan(300);
        }
    }
}
