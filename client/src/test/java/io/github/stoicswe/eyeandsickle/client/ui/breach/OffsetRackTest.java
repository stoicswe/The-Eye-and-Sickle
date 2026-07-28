package io.github.stoicswe.eyeandsickle.client.ui.breach;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cipher's three rows line up down the column.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * The control this replaces padded one side of a caret and let the layout centre what was left, so
 * every arrow landed <b>one cell right of the box it controlled</b>. Reported exactly as it looked:
 * <em>"clicking the second arrow changes the left-most column"</em>. The handler was right the whole
 * time and the picture was lying about which control was which — a player cannot debug that, they can
 * only conclude the game is mis-wired.
 *
 * <p>The cipher is more exposed to it than the tumbler was, because reading down a column and
 * subtracting <em>is</em> the puzzle. A column that is one cell out does not look broken; it looks
 * like the player got the arithmetic wrong.
 *
 * <p>No toolkit is started here, and none can be: every {@code Label} in that class throws at static
 * init with no display. {@code pad} is a pure function and is the part that was wrong before.
 */
class OffsetRackTest {

    /** {@code CELL}, restated — a test that read the constant could not catch it changing. */
    private static final int WIDTH = 5;

    @Test
    @DisplayName("every cell is the full width, whatever it holds")
    void fullWidth() {
        for (String text : new String[] {"", "0", "-9", "255", "-255", "FF"}) {
            assertThat(OffsetRack.pad(text)).as("cell %s", text).hasSize(WIDTH);
        }
    }

    @Test
    @DisplayName("values are right-aligned, so the digits of one column sit under each other")
    void rightAligned() {
        // Left-aligning would put the units digit of "-9" under the tens digit of "-255", which is
        // the one thing a column of subtractions must not do.
        assertThat(OffsetRack.pad("-9")).isEqualTo("  -9 ");
        assertThat(OffsetRack.pad("-255")).isEqualTo("-255 ");
    }

    @Test
    @DisplayName("an over-wide value is truncated rather than allowed to shear the column")
    void neverWidens() {
        // Widening would shift every cell to its right, which is the failure every other
        // character-cell width in this client exists to prevent.
        assertThat(OffsetRack.pad("abcdefg")).hasSize(WIDTH);
    }

    @Test
    @DisplayName("a hex byte is always two digits, so the two published rows are the same width")
    void hexIsPadded() {
        assertThat(OffsetBoard.hex(0)).isEqualTo("00");
        assertThat(OffsetBoard.hex(9)).isEqualTo("09");
        assertThat(OffsetBoard.hex(255)).isEqualTo("FF");
    }

    @Test
    @DisplayName("an entered offset always carries its sign; an empty cell is marked, not blank")
    void signIsAlwaysShown() {
        // Both signs are printed. An unsigned positive next to a signed negative reads as two
        // different kinds of number in one row.
        assertThat(OffsetBoard.offsetText(-9)).isEqualTo("-9");
        assertThat(OffsetBoard.offsetText(9)).isEqualTo("+9");
        // An empty cell gets a placeholder rather than blank space: a column of nothing is
        // indistinguishable from a column the widget failed to draw.
        assertThat(OffsetBoard.offsetText(null)).isEqualTo("..");
    }
}
