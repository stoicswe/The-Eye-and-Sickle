package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Bluesky mark's wind-up-and-release spin.
 *
 * <h2>⚠ THIS WIDGET IS A NARROW AMENDMENT TO §5 AND THESE TESTS ARE ITS FENCE</h2>
 *
 * {@code docs/design/ui-design-language.md} §5 reads "step timing only. No easing curve anywhere in
 * the product", and §9's build-blocking rejection list names <b>spring</b> by name. This is a spring,
 * on explicit direction (2026-08-06). What keeps it defensible is that it introduces <b>no animation
 * machinery</b> — the motion is a hand-authored table of angles walked one entry per {@code Pulse}
 * tick — and that it runs only while a real network sync is in flight.
 *
 * <p>So what is asserted here is the shape of the concession, not the prettiness of the curve.
 */
class SyncSpinTest {

    /**
     * ⚠ The motion is DATA, not a function.
     *
     * <p>A formula would be an easing function sitting in the source, and the next person would
     * import it — at which point §5 has been abandoned rather than amended. A table has nothing to
     * reuse.
     */
    @Test
    @DisplayName("the motion is a finite table, short enough to read")
    void itIsATable() {
        double[] angles = SyncSpin.angles();
        assertThat(angles).hasSizeBetween(8, 40);
    }

    /**
     * ⚠ It starts and ends at rest, or the mark is left crooked when a sync finishes.
     *
     * <p>The pane also snaps it home on stop, so this is belt and braces — but a table that did not
     * come back to zero would leave a permanently tilted logo for anybody whose sync ended on the
     * last frame.
     */
    @Test
    @DisplayName("it returns to rest")
    void itComesHome() {
        double[] angles = SyncSpin.angles();
        assertThat(angles[angles.length - 1] % 360).isEqualTo(SyncSpin.REST);
    }

    /** ⚠ The wind-up leans LEFT — negative — which is the tension the release comes out of. */
    @Test
    @DisplayName("it winds up to the left before it releases")
    void itWindsUpLeft() {
        double[] angles = SyncSpin.angles();
        assertThat(angles[0]).isNegative();
        assertThat(angles[3])
                .as("and further left than it started — the tension is still loading")
                .isLessThan(angles[0]);
    }

    /**
     * ⚠ The shape that was asked for: slow, then fast, then slow.
     *
     * <p>Asserted on the <b>deltas</b> rather than the angles, because that is what "fast" means —
     * and it is the property that would silently vanish if somebody flattened the table into an even
     * sweep while keeping the endpoints.
     */
    @Test
    @DisplayName("it accelerates through the middle and decelerates into rest")
    void itAcceleratesThenSlows() {
        double[] angles = SyncSpin.angles();
        double windUp = Math.abs(angles[3] - angles[2]);
        double fastest = 0;
        for (int i = 1; i < angles.length; i++) {
            fastest = Math.max(fastest, Math.abs(angles[i] - angles[i - 1]));
        }
        double lastStep = Math.abs(angles[angles.length - 1] - angles[angles.length - 2]);

        assertThat(fastest)
                .as("the release has to be visibly faster than the wind-up")
                .isGreaterThan(windUp * 5);
        assertThat(lastStep)
                .as("and it has to arrive at rest slowly, or it stops dead rather than settling")
                .isLessThan(fastest / 5);
    }

    /**
     * ⚠ It goes all the way round exactly once.
     *
     * <p>A table that stopped short would swing back rather than turn, and one that went round twice
     * would take twice as long to say the same thing.
     */
    @Test
    @DisplayName("it makes one full turn")
    void oneTurn() {
        double[] angles = SyncSpin.angles();
        double peak = 0;
        for (double angle : angles) {
            peak = Math.max(peak, angle);
        }
        assertThat(peak).isBetween(360.0, 375.0);
    }

    /** ⚠ And the overshoot, which is what makes it read as tension rather than as a stepper motor. */
    @Test
    @DisplayName("it overshoots once and comes back")
    void itOvershoots() {
        double[] angles = SyncSpin.angles();
        double peak = 0;
        for (double angle : angles) {
            peak = Math.max(peak, angle);
        }
        assertThat(peak)
                .as("past the full turn, then settling back onto it")
                .isGreaterThan(360.0);
    }
}
