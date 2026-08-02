package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sync banner's clip, and the one way it silently ate the report.
 *
 * <h2>The bug</h2>
 *
 * {@code ChainSyncPanel} is not a fixed size. It opens as a title, a height range, a meter and a
 * caption, and then — about two seconds later, when the replay finishes — it <b>adds its summary
 * lines</b>, which are the part the player actually needs. The banner measured the panel once, when
 * it was built, so the clip kept the height it had before those lines existed and cropped them off.
 * On screen the report simply stopped mid-sentence.
 *
 * <p>⚠ A snapshot cannot catch this. Render harnesses run under reduced motion, where the panel
 * paints its finished state on the first call — so the content never grows and the frame looks
 * right. It needs a test that grows the content after placement, which is what this is.
 *
 * <p>⚠ No toolkit needed: {@code Region} does its own layout maths, and reduced motion is forced so
 * the slide never builds a {@code Timeline}.
 */
@DisplayName("the sync banner")
class SyncBannerTest {

    private StackPane root;
    private Region strip;
    private Region cell;
    private SyncBanner banner;

    @BeforeEach
    void setUp() {
        // Suppresses the slide, which would otherwise need a running toolkit for its Timeline.
        Pulse.shared().setReducedMotion(true);

        root = new StackPane();
        root.resize(1200, 800);

        // ⚠ Unmanaged, or the StackPane lays them out over this geometry on the next pass and the
        // fixture stops describing a strip at all — the same managed-child trap DeskManager
        // documents, hit here from the test side.
        strip = new Region();
        strip.setManaged(false);
        strip.resizeRelocate(0, 0, 1200, 40);
        cell = new Region();
        cell.setManaged(false);
        cell.resizeRelocate(1000, 6, 160, 28);

        banner = new SyncBanner();
        root.getChildren().addAll(strip, cell, banner);
        root.layout();
    }

    private double clipHeight() {
        return ((Rectangle) banner.getClip()).getHeight();
    }

    /** A panel that can be made taller, the way ChainSyncPanel is when its summary lands. */
    private static Region growable(double width, double height) {
        Region panel = new Region();
        panel.setPrefSize(width, height);
        panel.setMinSize(width, height);
        return panel;
    }

    @Test
    @DisplayName("the clip follows the panel when its summary arrives")
    void clipGrowsWithTheContent() {
        Region panel = growable(400, 90);
        banner.show(cell, strip, panel, () -> {});
        root.layout();
        assertThat(clipHeight()).as("opens at the panel's height").isEqualTo(90);

        // The replay finishes and ChainSyncPanel un-hides its summary — the panel gets taller.
        panel.setPrefHeight(210);
        panel.setMinHeight(210);
        root.layout();

        assertThat(clipHeight())
                .as("a clip that kept its old height would cut the report off mid-sentence")
                .isEqualTo(210);
    }

    @Test
    @DisplayName("it hangs from the strip's bottom edge, not the cell's")
    void anchorsToTheStripNotTheCell() {
        banner.show(cell, strip, growable(400, 90), () -> {});
        root.layout();
        // The cell is centred in a taller strip, so anchoring to the cell would put a few pixels of
        // panel over the readouts — this layer paints above the deck, so they would cover them.
        assertThat(banner.getTranslateY()).isEqualTo(40);
    }

    @Test
    @DisplayName("it is right-aligned to its cell")
    void rightAlignedToTheCell() {
        banner.show(cell, strip, growable(400, 90), () -> {});
        root.layout();
        // Cell right edge is 1160, panel is 400 wide.
        assertThat(banner.getTranslateX()).isEqualTo(760);
    }

    @Test
    @DisplayName("a panel wider than the room to its left is clamped, not pushed off-screen")
    void clampedAtZero() {
        banner.show(cell, strip, growable(1400, 90), () -> {});
        root.layout();
        assertThat(banner.getTranslateX()).isEqualTo(0);
    }

    @Test
    @DisplayName("dismissing runs the release exactly once, however it is dismissed")
    void releaseIsIdempotent() {
        int[] released = {0};
        banner.show(cell, strip, growable(400, 90), () -> released[0]++);
        root.layout();

        banner.dismiss();
        banner.dismiss();
        // The caller dismisses on a timer, the player by clicking, and a character swap by tearing
        // the deck down. All three can race, and the release closes a Pulse subscription that must
        // not be closed twice.
        assertThat(released[0]).isEqualTo(1);
    }
}
