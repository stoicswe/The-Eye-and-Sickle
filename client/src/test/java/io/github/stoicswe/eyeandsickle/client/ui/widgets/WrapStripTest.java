package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The top strip's wrap decision, which is chrome height and therefore everything below it.
 *
 * <h2>The bug this was written for</h2>
 *
 * A strip cell carries {@code -fx-padding: 7 14 7 14} plus a 1px divider, so a cell holding a label
 * with no text is <b>29 pixels wide and draws a rule</b>. The refusal cell is empty almost always,
 * and it was charged against the width budget on every pass. Measured on the real deck at 1200px:
 * the strip wanted 1113 and had 1104, so it wrapped — by <b>nine pixels</b>, doubling the height of
 * the chrome and pushing every window down, over an overflow three times smaller than the dead cell
 * causing it.
 *
 * <p>⚠ No toolkit needed. {@code Region} does its own layout maths, so this exercises the real
 * {@code layoutChildren} rather than a reimplementation of it — which matters, because the defect
 * was in the measurement and a test that recomputed the measurement would have agreed with the bug.
 */
@DisplayName("the top strip's wrap decision")
class WrapStripTest {

    private static Region cell(double width) {
        Region region = new Region();
        region.setPrefSize(width, 40);
        region.setMinSize(width, 40);
        region.setMaxSize(width, 40);
        return region;
    }

    /** Lays the strip out at a real width, the way a parent would. */
    private static WrapStrip laidOut(WrapStrip strip, double width) {
        strip.resize(width, strip.prefHeight(width));
        strip.layout();
        return strip;
    }

    @Nested
    @DisplayName("an unmanaged child is not in the layout")
    class Unmanaged {

        @Test
        @DisplayName("it does not count toward the width that decides a wrap")
        void doesNotCauseAWrap() {
            WrapStrip strip = new WrapStrip();
            strip.add(cell(500));
            strip.add(cell(400));
            Region dead = cell(200);
            dead.setManaged(false);
            strip.add(dead);

            // 900 of live content in 1000: one row. The dead cell would take it to 1100 and wrap.
            laidOut(strip, 1000);
            assertThat(strip.prefHeight(1000))
                    .as("a cell nobody can see must not double the height of the chrome")
                    .isEqualTo(40);
        }

        @Test
        @DisplayName("it is not given a position either")
        void isNotPlaced() {
            WrapStrip strip = new WrapStrip();
            strip.add(cell(200));
            Region dead = cell(200);
            dead.setManaged(false);
            strip.add(dead);
            Region after = cell(200);
            strip.add(after);

            laidOut(strip, 1000);
            // The cell after it slides up into the gap; leaving a hole would show as a stray divider
            // and a band of empty strip, which is what the player actually saw.
            assertThat(after.getLayoutX()).isEqualTo(200);
        }

        @Test
        @DisplayName("re-managing it puts it back")
        void comesBack() {
            WrapStrip strip = new WrapStrip();
            strip.add(cell(200));
            Region refusal = cell(200);
            refusal.setManaged(false);
            strip.add(refusal);
            Region after = cell(200);
            strip.add(after);

            laidOut(strip, 1000);
            assertThat(after.getLayoutX()).isEqualTo(200);

            // A refusal arrives. The strip must make room rather than paint it over a neighbour.
            refusal.setManaged(true);
            laidOut(strip, 1000);
            assertThat(refusal.getLayoutX()).isEqualTo(200);
            assertThat(after.getLayoutX()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("wrapping still happens when it genuinely must")
    class StillWraps {

        @Test
        @DisplayName("content wider than the strip goes to a second row")
        void wrapsWhenTooWide() {
            WrapStrip strip = new WrapStrip();
            strip.add(cell(600));
            strip.add(cell(600));

            laidOut(strip, 1000);
            // The whole reason this widget is not an HBox: at 200% UI scale in a 1280px window the
            // deck is 640 logical pixels and an HBox silently clipped the readouts on the right.
            assertThat(strip.prefHeight(1000)).isEqualTo(80);
        }

        @Test
        @DisplayName("the pinned child is reserved for and never wraps")
        void pinnedStaysPut() {
            WrapStrip strip = new WrapStrip();
            strip.add(cell(500));
            strip.add(cell(400));
            strip.setPinned(cell(200));

            // 900 of flow with only 800 usable once the window controls are reserved.
            laidOut(strip, 1000);
            assertThat(strip.prefHeight(1000)).isEqualTo(80);
        }
    }
}
