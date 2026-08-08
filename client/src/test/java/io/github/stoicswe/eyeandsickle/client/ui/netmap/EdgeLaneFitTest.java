package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every routing lane has to fit inside the gap it routes in.
 *
 * <h2>⚠ THE BUG THIS PINS SHIPPED, AND IT IS INVISIBLE TO EVERY OTHER CHECK</h2>
 *
 * {@code NetCanvas.forward} turns an edge at column {@code 1 + lane * 2} — so lanes 0, 1 and 2 turn
 * at columns <b>1, 3 and 5</b>. Its own class note says these are "drawn in the seven-column gap",
 * which is where those numbers come from. {@code UiTokens.NET_GAP_COLS} is <b>3</b>.
 *
 * <p>So lanes 1 and 2 turned outside the gap, on cells belonging to the next layer's node box. Every
 * write there is refused by {@code occupied}, which is the safety net doing its job — nothing was
 * corrupted, and nothing failed. What reached the screen was an edge with a source stub, no vertical,
 * no destination run and <b>no arrowhead</b>: two thirds of a fan-out rendered as loose ticks against
 * the node boxes, and a reader cannot tell which machine connects to which.
 *
 * <p>⚠ It is invisible to the rest of the suite because it is not a crash, not a layout overflow and
 * not a glyph problem — the map draws, the nodes are right, the numbers are right. It is only wrong
 * to look at, and this project has no automated way to look. Hence an arithmetic invariant.
 *
 * <p>⚠ The gap was narrowed at some point and the lane arithmetic was never revisited. That is the
 * shape of failure worth remembering: a constant moved, and the code that had been written against
 * its old value kept compiling.
 */
@DisplayName("edge routing lanes")
class EdgeLaneFitTest {

    /**
     * The column {@code NetCanvas.forward} turns an edge of this lane at.
     *
     * <p>⚠ Duplicated deliberately rather than exposed from {@code NetCanvas}: the point is to pin
     * the RELATIONSHIP between two tokens and one formula, and a test that asked the code for its own
     * answer could only ever agree with it.
     */
    private static int turnColumn(int lane) {
        return 1 + lane * 2;
    }

    @Test
    @DisplayName("every lane turns inside the gap, with room for a run and an arrowhead after it")
    void everyLaneFits() {
        int last = UiTokens.NET_GAP_COLS - 1;
        for (int lane = 0; lane < UiTokens.NET_LANES; lane++) {
            int turn = turnColumn(lane);
            assertThat(turn)
                    .as(
                            "lane %d turns at column %d, but the gap is %d columns (0..%d). "
                                    + "It would route over the next layer's node box, be refused by "
                                    + "`occupied`, and render as a stub with no arrowhead.",
                            lane, turn, UiTokens.NET_GAP_COLS, last)
                    .isLessThan(last);
        }
    }

    @Test
    @DisplayName("the arrowhead column is never a turn column")
    void theArrowheadIsNotOverwritten() {
        // `forward` writes the arrowhead at `last` with put/close rather than merge, so a turn landing
        // there would be silently replaced by a junction and the edge would lose the one mark that
        // says which way it runs.
        int last = UiTokens.NET_GAP_COLS - 1;
        for (int lane = 0; lane < UiTokens.NET_LANES; lane++) {
            assertThat(turnColumn(lane)).as("lane %d", lane).isNotEqualTo(last);
        }
    }
}
