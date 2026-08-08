package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The hover response every clickable thing in the client gets.
 *
 * <h2>What is actually being defended</h2>
 *
 * Two properties, and the second is the one an accessibility setting can quietly break:
 *
 * <ul>
 *   <li><b>The tear ends where it started.</b> The offset is left on the node between ticks, so a
 *       table that stopped anywhere but zero would park every control the player had ever hovered a
 *       pixel off its own layout — permanently, and only for the controls they touched.
 *   <li><b>Reduce motion removes the motion and nothing else.</b> The outline is CSS and is present
 *       for as long as the pointer is; the tear is decoration. If the tear ever became the
 *       affordance, "can I click this" would be an answer only some players got.
 * </ul>
 *
 * <p>⚠ No toolkit. A {@code Region} is a plain object until it is laid out, and the whole state
 * machine is style classes and a translate — so this runs headlessly, unlike the two window-manager
 * tests that need real frames.
 */
class HoverGlitchTest {

    private final HoverGlitch glitch = HoverGlitch.shared();

    @AfterEach
    void reset() {
        glitch.leave();
        Pulse.shared().setReducedMotion(false);
    }

    private static Region control() {
        return new Region();
    }

    @Nested
    @DisplayName("the outline")
    class Outline {

        @Test
        @DisplayName("is on for as long as the pointer is, and is the whole affordance")
        void marksTheHoveredControl() {
            Region button = control();
            glitch.install(button);

            assertThat(button.getStyleClass()).contains(HoverGlitch.HOVERABLE);
            assertThat(button.getStyleClass()).doesNotContain(HoverGlitch.HOVERED);

            glitch.hover(button);
            assertThat(button.getStyleClass()).contains(HoverGlitch.HOVERED);

            glitch.leave();
            assertThat(button.getStyleClass()).doesNotContain(HoverGlitch.HOVERED);
        }

        @Test
        @DisplayName("survives the tear finishing — the control stays lit while the pointer is on it")
        void outlastsTheTear() {
            Region button = control();
            glitch.install(button);
            glitch.hover(button);
            for (int i = 0; i < HoverGlitch.tearFrames() + 3; i++) {
                glitch.advance();
            }
            assertThat(button.getStyleClass())
                    .as("the tear is over and the affordance is not")
                    .contains(HoverGlitch.HOVERED);
        }
    }

    @Nested
    @DisplayName("the tear")
    class Tear {

        @Test
        @DisplayName("⚠ ends at exactly zero, so no control is left parked off its own layout")
        void settlesAtZero() {
            Region button = control();
            glitch.install(button);
            glitch.hover(button);

            boolean moved = false;
            for (int i = 0; i < HoverGlitch.tearFrames(); i++) {
                glitch.advance();
                moved |= button.getTranslateX() != 0;
            }
            assertThat(moved).as("it actually moved").isTrue();
            assertThat(button.getTranslateX()).as("and came all the way back").isZero();

            // And stays there however long the pointer rests.
            for (int i = 0; i < 20; i++) {
                glitch.advance();
            }
            assertThat(button.getTranslateX()).isZero();
        }

        @Test
        @DisplayName("⚠ a control the pointer leaves mid-tear is put back exactly as it was")
        void leavingMidTearIsClean() {
            // The ordinary case: a pointer crossing a row of buttons on its way somewhere else. Every
            // one of them starts a tear and none of them finishes it.
            Region button = control();
            glitch.install(button);
            glitch.hover(button);
            glitch.advance();
            assertThat(button.getTranslateX()).isNotZero();

            glitch.leave();
            assertThat(button.getTranslateX()).isZero();
            assertThat(button.getStyleClass()).doesNotContain(HoverGlitch.HOVERED, "es-hover-cut");
        }

        @Test
        @DisplayName("⚠ moving to a second control releases the first, even without an exit event")
        void handsOver() {
            // A pointer moved fast between adjacent buttons can deliver the second ENTER before the
            // first EXIT. Without an explicit release the first would be left parked at whatever
            // offset its tear had reached — and nothing would ever come back to it.
            Region first = control();
            Region second = control();
            glitch.install(first);
            glitch.install(second);

            glitch.hover(first);
            glitch.advance();
            assertThat(first.getTranslateX()).isNotZero();

            glitch.hover(second);
            assertThat(first.getTranslateX()).as("the first was let go").isZero();
            assertThat(first.getStyleClass()).doesNotContain(HoverGlitch.HOVERED);
            assertThat(second.getStyleClass()).contains(HoverGlitch.HOVERED);
        }
    }

    @Nested
    @DisplayName("reduce motion")
    class Reduced {

        @Test
        @DisplayName("⚠ takes the motion away and leaves the affordance")
        void outlineOnly() {
            Region button = control();
            glitch.install(button);
            Pulse.shared().setReducedMotion(true);

            glitch.hover(button);
            for (int i = 0; i < HoverGlitch.tearFrames(); i++) {
                glitch.advance();
            }

            assertThat(button.getTranslateX()).as("no displacement at all").isZero();
            assertThat(button.getStyleClass()).doesNotContain("es-hover-cut");
            assertThat(button.getStyleClass())
                    .as("the outline is what answers 'can I click this'")
                    .contains(HoverGlitch.HOVERED);
        }

        @Test
        @DisplayName("⚠ turned on MID-TEAR puts the control back rather than freezing it askew")
        void tidiesUpWhenSwitchedOnMidTear() {
            // ⚠ Pulse stops calling a decorative subscription the moment the setting goes on, so this
            // tick is the only chance to undo the offset. Without it, a player who turns Reduce motion
            // on while hovering a button leaves that button permanently two pixels to the left — the
            // accessibility path getting the one broken state, which is this repo's recurring shape.
            Region button = control();
            glitch.install(button);
            glitch.hover(button);
            glitch.advance();
            assertThat(button.getTranslateX()).isNotZero();

            Pulse.shared().setReducedMotion(true);
            glitch.advance();

            assertThat(button.getTranslateX()).isZero();
        }
    }

    @Nested
    @DisplayName("installation")
    class Installation {

        @Test
        @DisplayName("is idempotent, because the clickable registry reaches a node more than once")
        void installTwice() {
            // Cursors.clickable is called from individual call sites AND from a subtree walker, so a
            // node can legitimately arrive here twice.
            Region button = control();
            glitch.install(button);
            glitch.install(button);

            assertThat(button.getStyleClass().stream().filter(HoverGlitch.HOVERABLE::equals).count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("a null node is ignored rather than thrown at")
        void nullIsSafe() {
            glitch.install(null);
        }
    }
}
