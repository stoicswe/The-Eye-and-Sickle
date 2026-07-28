package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The window-size presets and the UI scale, and the one rule that couples them.
 *
 * <h2>⚠ No toolkit anywhere in here</h2>
 *
 * {@link WindowSize} is JavaFX-free for exactly this reason, and {@link UiScale}'s two static
 * members are reachable without constructing one. {@code UiContractTest}'s own comment states the
 * standard: "a contract test that only runs on a machine with a display is a contract test that does
 * not run in CI."
 */
class WindowSizingTest {

    @Nested
    @DisplayName("the presets")
    class Presets {

        @Test
        @DisplayName("every preset round-trips through its persisted id")
        void idsRoundTrip() {
            for (WindowSize size : WindowSize.selectable()) {
                assertThat(WindowSize.byId(size.id())).contains(size);
            }
        }

        @Test
        @DisplayName("an unknown id falls back rather than throwing")
        void unknownIdIsEmpty() {
            // A profile written by a client with one more preset than this one still has to load —
            // the alternative is a player losing their settings file to an enum constant.
            assertThat(WindowSize.byId("640x480")).isEmpty();
            assertThat(WindowSize.byId(null)).isEmpty();
        }

        @Test
        @DisplayName("the default is the size the client has always shipped at")
        void defaultIsUnchanged() {
            assertThat(WindowSize.HD_1280.width()).isEqualTo(1280);
            assertThat(WindowSize.HD_1280.height()).isEqualTo(800);
            // The persisted default in ClientProfile.Settings is this id, spelled out.
            assertThat(WindowSize.HD_1280.id()).isEqualTo("1280x800");
        }

        @Test
        @DisplayName("a window larger than the display does not fit")
        void doesNotFitAScreenTooSmall() {
            // Visual bounds, not total: a 1080p panel with a menu bar has less than 1920×1080 to
            // give, and an undecorated Stage sized past it has no OS chrome to drag it back with.
            assertThat(WindowSize.FULL_HD.fitsOnScreen(1920, 1055, 1.0d, 0)).isFalse();
            assertThat(WindowSize.FULL_HD.fitsOnScreen(1920, 1080, 1.0d, 0)).isTrue();
            assertThat(WindowSize.HD_1280.fitsOnScreen(1920, 1055, 1.0d, 0)).isTrue();
        }

        @Test
        @DisplayName("every preset clears the supported layout floor")
        void everyPresetMeetsTheMinimum() {
            for (WindowSize size : WindowSize.selectable()) {
                assertThat(size.meetsMinimum()).as("%s", size).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("the viewport, the casing and the scale")
    class Viewport {

        @Test
        @DisplayName("⚠ the casing is OUTSIDE the viewport, so it costs window room")
        void casingIsOutsideTheViewport() {
            // The resolution is the deck's, not the window's: choosing 1920 × 1080 gives the deck
            // 1920 × 1080 and puts the casing beyond it. A 20px casing therefore needs a 1960-wide
            // window, and a display that only has 1920 can no longer hold it.
            assertThat(WindowSize.FULL_HD.fitsOnScreen(1920, 1080, 1.0d, 0)).isTrue();
            assertThat(WindowSize.FULL_HD.fitsOnScreen(1920, 1080, 1.0d, 20)).isFalse();
            assertThat(WindowSize.FULL_HD.fitsOnScreen(1960, 1120, 1.0d, 20)).isTrue();
        }

        @Test
        @DisplayName("scale multiplies the whole window, casing included")
        void scaleMultipliesEverything() {
            // (1280 + 2×10) × 1.5 = 1950 wide, (800 + 20) × 1.5 = 1230 tall.
            assertThat(WindowSize.HD_1280.fitsOnScreen(1949, 1230, 1.5d, 10)).isFalse();
            assertThat(WindowSize.HD_1280.fitsOnScreen(1950, 1230, 1.5d, 10)).isTrue();
        }

        @Test
        @DisplayName("⚠ scale no longer shrinks the viewport — that coupling is gone by design")
        void scaleDoesNotEatTheViewport() {
            // This is the behaviour change. The window used to be sized TO the resolution and the
            // deck laid out at physical/scale, so 1280 × 800 at 150% gave the deck 853 logical
            // pixels and fell under the 860 floor. Now the window is sized FROM the viewport, so the
            // deck always gets the full resolution and the scale only changes how big it is drawn.
            assertThat(WindowSize.HD_1280.meetsMinimum()).isTrue();
            // Which means the old "unusable at 150%" case is simply a bigger window now, and fits
            // wherever there is room for it.
            assertThat(WindowSize.HD_1280.fitsOnScreen(2560, 1440, 1.5d, 0)).isTrue();
        }

        @Test
        @DisplayName("a nonsense scale is treated as 100% rather than multiplying by zero")
        void zeroScaleIsSafe() {
            assertThat(WindowSize.HD_1280.fitsOnScreen(1280, 800, 0, 0)).isTrue();
            assertThat(WindowSize.HD_1280.fitsOnScreen(1280, 800, -1, 0)).isTrue();
        }
    }

    @Nested
    @DisplayName("the scale factors")
    class Scale {

        @Test
        @DisplayName("100% is offered and is the default")
        void defaultIsOffered() {
            assertThat(UiScale.PERCENTAGES).contains(UiScale.DEFAULT_PERCENT);
            assertThat(UiScale.DEFAULT_PERCENT).isEqualTo(100);
        }

        @Test
        @DisplayName("the offered factors ascend, so the choice list reads in order")
        void ascending() {
            for (int i = 1; i < UiScale.PERCENTAGES.length; i++) {
                assertThat(UiScale.PERCENTAGES[i]).isGreaterThan(UiScale.PERCENTAGES[i - 1]);
            }
        }

        @Test
        @DisplayName("a persisted value that is not offered falls back to 100")
        void sanitiseFallsBack() {
            // Same argument as WindowSize.byId: a profile from a client with a different ladder
            // must load. A control cannot show 137% as selected, so it would render blank.
            assertThat(UiScale.sanitise(137)).isEqualTo(100);
            assertThat(UiScale.sanitise(0)).isEqualTo(100);
            assertThat(UiScale.sanitise(-50)).isEqualTo(100);
            assertThat(UiScale.sanitise(150)).isEqualTo(150);
        }
    }
}
