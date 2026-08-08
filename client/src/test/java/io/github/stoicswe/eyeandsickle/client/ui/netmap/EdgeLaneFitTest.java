package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every routing lane has to fit inside the corridor it routes in, and leave the lateral bracket alone.
 *
 * <h2>⚠ THE BUG THIS PINS SHIPPED, AND IT IS INVISIBLE TO EVERY OTHER CHECK</h2>
 *
 * {@code NetCanvas.forward} turned an edge at column {@code 1 + lane * 2} with three lanes, which is
 * where a seven-column gap put them. {@code UiTokens.NET_GAP_COLS} was later narrowed to <b>3</b> and
 * nothing revisited the formula, so lanes 1 and 2 turned at columns 3 and 5 in a run that ended at 2 —
 * outside it, on the next layer's node box, where every write was refused by {@code occupied}. That is
 * the safety net doing its job: nothing was corrupted, and nothing failed. What reached the screen was
 * an edge with a source stub, no vertical, no destination run and <b>no arrowhead</b> — two thirds of a
 * fan-out rendered as loose ticks against the node boxes, and a reader cannot tell which machine
 * connects to which.
 *
 * <p>⚠ It is invisible to the rest of the suite because it is not a crash, not a layout overflow and
 * not a glyph problem — the map draws, the nodes are right, the numbers are right. It is only wrong to
 * look at. Hence an arithmetic invariant.
 *
 * <p>⚠ The corridor grew from three columns to thirteen on 2026-08-08, when forward edges stopped
 * stopping at the end of the gap and ran the whole way to the next box. That is exactly the kind of
 * move that left the lane arithmetic behind last time, which is why the properties below are
 * re-derived from the tokens here rather than read back from {@code NetCanvas}: a test that asks the
 * code for its own answer can only ever agree with it.
 */
@DisplayName("edge routing lanes")
class EdgeLaneFitTest {

    /** How far a forward edge runs: one layer's box to the next layer's box. */
    private static final int CORRIDOR = UiTokens.NET_GAP_COLS + UiTokens.NET_LATERAL_COLS;

    /** The last column of that run, where the arrowhead goes. */
    private static final int ARROW = CORRIDOR - 1;

    /** The next layer's lateral channel, counted from the start of the corridor. */
    private static final int CHANNEL = CORRIDOR - UiTokens.NET_LATERAL_BUS_COLS;

    @Test
    @DisplayName("every lane turns inside the run, with room for a destination run and an arrowhead")
    void everyLaneFits() {
        int[] turns = NetCanvas.turnColumns();
        assertThat(turns).as("a corridor always offers at least one lane").isNotEmpty();
        for (int turn : turns) {
            assertThat(turn)
                    .as(
                            "a lane turning at column %d in a %d-column corridor would route over the "
                                    + "next layer's node box, be refused by `occupied`, and render as a "
                                    + "stub with no arrowhead",
                            turn, CORRIDOR)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThan(ARROW);
        }
    }

    @Test
    @DisplayName("the arrowhead column is never a turn column")
    void theArrowheadIsNotOverwritten() {
        // `forward` writes the arrowhead at ARROW with put/close rather than merge, so a turn landing
        // there would be silently replaced by a junction and the edge would lose the one mark that
        // says which way it runs.
        assertThat(NetCanvas.turnColumns()).doesNotContain(ARROW);
    }

    @Test
    @DisplayName("no lane turns in the lateral channel")
    void lanesLeaveTheLateralBracketAlone() {
        // ⚠ A forward VERTICAL in the column same-layer edges use would be indistinguishable from a
        // lateral edge — the one distinction this map cannot afford to blur, because it is carried by
        // shape alone so that it survives greyscale. A forward horizontal crossing that column is a
        // different matter and is handled by yielding; see NetCanvas.merge.
        assertThat(NetCanvas.turnColumns()).doesNotContain(CHANNEL);
    }

    @Test
    @DisplayName("no two lanes share a vertical")
    void lanesAreDistinct() {
        int[] turns = NetCanvas.turnColumns();
        for (int i = 1; i < turns.length; i++) {
            assertThat(turns[i])
                    .as("lane %d and lane %d would draw their verticals in the same column", i - 1, i)
                    .isNotEqualTo(turns[i - 1]);
        }
    }

    @Test
    @DisplayName("the lateral bracket fits between the corridor and the node box")
    void theBracketFits() {
        // The channel and its stub have to sit inside the strip, and the stub has to be the column
        // immediately left of the box — otherwise a same-layer edge stops short of the machine it
        // joins, which is the defect this bracket was moved to fix.
        assertThat(UiTokens.NET_LATERAL_BUS_COLS)
                .as("the bracket needs a channel and a stub")
                .isGreaterThanOrEqualTo(2)
                .isLessThanOrEqualTo(UiTokens.NET_LATERAL_COLS);
        assertThat(CHANNEL).as("the channel is inside the corridor").isGreaterThan(0).isLessThan(ARROW);
    }
}
