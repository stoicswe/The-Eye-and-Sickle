package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where a drop-down overlay's left edge goes.
 *
 * <h2>Why this is a unit test and not a render</h2>
 *
 * It shipped wrong, and it shipped wrong <em>because</em> the only way to check it was to render the
 * deck and look at it. The rule lived inside {@code Anchoring.place}, which needs live scene bounds
 * and therefore a toolkit, so there was nothing a test could reach — the same reason
 * {@code SecurityCenterView.latestOf} and {@code markStateFor} were pulled out into pure
 * package-private functions after the same class of bug.
 *
 * <p>The numbers below are the deck's real ones at 1400px, measured off a snapshot: the rail is
 * 34px, the operator cell runs roughly 97–290, the balance cell sits near the right-hand end, and
 * the panels are 420 and 590 wide.
 */
@DisplayName("anchoring a drop-down to a strip cell")
class AnchoringTest {

    /** The desk starts after the rail. */
    private static final double DESK_LEFT = 34;

    private static final double DESK_RIGHT = 1400;

    @Nested
    @DisplayName("a cell near the right-hand end")
    class RightHandCells {

        @Test
        @DisplayName("right-aligns to the cell, which is what a wide report wants")
        void rightAligns() {
            // The chain-sync report hangs off the balance cell and is far wider than it, so it grows
            // leftward into the strip. Its right edge lines up with the cell's.
            double x = Anchoring.horizontal(1180, 1290, 590, DESK_LEFT, DESK_RIGHT);

            assertThat(x).isEqualTo(1290 - 590);
        }

        @Test
        @DisplayName("never runs off the right-hand edge")
        void staysInsideOnTheRight() {
            double x = Anchoring.horizontal(1380, 1400, 590, DESK_LEFT, DESK_RIGHT);

            assertThat(x + 590).isLessThanOrEqualTo(DESK_RIGHT);
        }
    }

    @Nested
    @DisplayName("⚠ a cell near the LEFT-hand end — the operator panel")
    class LeftHandCells {

        @Test
        @DisplayName("left-aligns to its cell instead of jamming against the edge")
        void leftAligns() {
            // THE REGRESSION. The operator cell is the FIRST cell in the strip, so right-aligning a
            // 420px panel to a cell whose right edge is at 290 asks for -130. The old rule clamped
            // that to the field's left edge, and what landed was a panel touching neither its cell
            // nor anything else — reported as looking half off screen.
            double x = Anchoring.horizontal(97, 290, 420, DESK_LEFT, DESK_RIGHT);

            assertThat(x).as("should hang under its own cell").isEqualTo(97);
            assertThat(x).as("and clear the rail").isGreaterThanOrEqualTo(DESK_LEFT);
        }

        @Test
        @DisplayName("⚠ never covers the rail, even when the cell starts before the desk does")
        void neverCoversTheRail() {
            // A cell whose left edge is left of the desk's — the window controls' end of the strip.
            // The overlay still may not be placed on top of the rail.
            double x = Anchoring.horizontal(4, 80, 420, DESK_LEFT, DESK_RIGHT);

            assertThat(x).isGreaterThanOrEqualTo(DESK_LEFT);
        }
    }

    @Nested
    @DisplayName("when it cannot fit at all")
    class Overflow {

        @Test
        @DisplayName("⚠ overflows RIGHT, so it is readable from its first character")
        void overflowsRightNotLeft() {
            // Wider than the whole desk — a very narrow window, or a 200% UI scale. Neither bound can
            // be honoured, and the order the clamps are applied in decides which way it spills. Off
            // the right is recoverable by reading; off the left is under the rail and lost.
            double x = Anchoring.horizontal(97, 290, 2000, DESK_LEFT, DESK_RIGHT);

            assertThat(x).isEqualTo(DESK_LEFT);
        }
    }
}
