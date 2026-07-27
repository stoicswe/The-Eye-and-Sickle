package io.github.stoicswe.eyeandsickle.client.ui.breach;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Logic board's carets sit over the tumbler they control.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * {@code centre} left-padded and stopped: three characters over a box that is five
 * ({@code ┌───┐}). The column is a {@code VBox} with {@code TOP_CENTER}, so JavaFX centred the short
 * string inside the wider column and the glyph landed <b>one cell right of its own box's centre</b>,
 * hard against the gap to the next tumbler.
 *
 * <p>Reported exactly as it looks: <em>"clicking the second arrow changes the left-most column"</em>.
 * The handler was right the whole time — every caret was bound to its own position — and the picture
 * was lying about which control was which. A player cannot debug that, they can only conclude the
 * game is mis-wired, which makes a one-cell offset a much more expensive bug than it sounds.
 *
 * <p>No toolkit is started here, and none can be: every {@code Label} in that class throws at static
 * init with no display. {@code centre} is a pure function and is the part that was wrong.
 */
class TumblerRackTest {

    /** {@code TUMBLER_COLS}, restated — a test that read the constant could not catch it changing. */
    private static final int WIDTH = 5;

    @Test
    @DisplayName("a caret is padded to the full tumbler width, on both sides")
    void fullWidth() {
        assertThat(TumblerRack.centre("^")).hasSize(WIDTH);
        assertThat(TumblerRack.centre(" ")).hasSize(WIDTH);
        assertThat(TumblerRack.centre("")).hasSize(WIDTH);
    }

    @Test
    @DisplayName("the glyph lands on the same cell the box's own centre does")
    void alignedWithTheBox() {
        // The box is five cells with its centre at index 2. Anything else and the arrow points at a
        // tumbler it does not control.
        assertThat(TumblerRack.centre("^").indexOf('^')).isEqualTo(WIDTH / 2);
    }

    @Test
    @DisplayName("every caret in a rack lines up identically, so the row reads as a row")
    void uniform() {
        // The failure was uniform too — every caret was off by one in the same direction, which is
        // why it read as "the arrows belong to the next column" rather than as a rendering glitch.
        for (String glyph : new String[] {"^", "v", " ", "-"}) {
            assertThat(TumblerRack.centre(glyph))
                    .as("caret %s", glyph)
                    .isEqualTo("  " + glyph + "  ");
        }
    }

    @Test
    @DisplayName("an over-wide glyph is returned untouched rather than clipped")
    void neverClips() {
        // Clipping would shear the column, which is the failure every other character-cell width in
        // this client exists to prevent.
        assertThat(TumblerRack.centre("abcdefg")).isEqualTo("abcdefg");
    }
}
