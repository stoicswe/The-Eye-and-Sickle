package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The resize grip.
 *
 * <h2>Why this has its own test</h2>
 *
 * The grip shipped broken once. {@link DeskManager} reads the pointer position inside a JavaFX
 * <em>event filter</em>, and in a filter {@code MouseEvent.getX()} is relative to the event's target
 * node rather than to the node the filter is installed on. Since the target is nearly always some
 * label or scroll pane inside the tool, the raw coordinates were that child's — so the grip worked
 * on an empty panel and stopped working wherever a tool had put content, which is everywhere.
 *
 * <p>The fix is a {@code sceneToLocal} conversion at the call site; this test covers the arithmetic
 * that conversion feeds. It is deliberately pure — no toolkit, no Stage — because a grip test that
 * only runs on a machine with a display would not have caught the original bug either.
 */
class DeskGripTest {

    private static final double W = 400;
    private static final double H = 300;

    private static int edge(double x, double y) {
        return DeskManager.edgeAt(x, y, W, H);
    }

    @Test
    @DisplayName("the middle of a panel grips nothing")
    void centreIsNotAGrip() {
        // The failure this guards is the opposite of the original bug and worse: a panel whose whole
        // surface resizes cannot have anything clicked inside it.
        assertThat(edge(200, 150)).isZero();
        assertThat(edge(100, 150)).isZero();
    }

    @Test
    @DisplayName("each edge grips its own axis")
    void edges() {
        assertThat(edge(200, 1)).isEqualTo(1); // NORTH
        assertThat(edge(200, H - 1)).isEqualTo(2); // SOUTH
        assertThat(edge(1, 150)).isEqualTo(4); // WEST
        assertThat(edge(W - 1, 150)).isEqualTo(8); // EAST
    }

    @Test
    @DisplayName("corners grip both axes, and from further away than an edge does")
    void cornersHaveABiggerGrip() {
        // The diagonal is the resize people reach for most and the hardest to hit, because it is the
        // only one that needs the pointer close to two edges at once. A 6px corner is the standard
        // frustration of every hand-rolled window manager.
        assertThat(edge(2, 2)).isEqualTo(1 | 4); // NW
        assertThat(edge(W - 2, 2)).isEqualTo(1 | 8); // NE
        assertThat(edge(2, H - 2)).isEqualTo(2 | 4); // SW
        assertThat(edge(W - 2, H - 2)).isEqualTo(2 | 8); // SE

        double justOutsideEdgeGrip = DeskManager.RESIZE_MARGIN + 3;
        assertThat(justOutsideEdgeGrip).isLessThan(DeskManager.CORNER_GRIP);
        assertThat(edge(justOutsideEdgeGrip, justOutsideEdgeGrip))
                .as("still a corner grip past the edge margin")
                .isEqualTo(1 | 4);
        assertThat(edge(justOutsideEdgeGrip, 150))
                .as("but not a grip at all along a plain edge")
                .isZero();
    }

    @Test
    @DisplayName("a point outside the frame grips nothing")
    void outsideIsNotAGrip() {
        // sceneToLocal returns negatives for a pointer above or left of the panel. Without the
        // bounds guard, -40 reads as "<= RESIZE_MARGIN" and arms a resize from outside the window —
        // so moving the mouse anywhere up and to the left would start dragging a panel edge.
        assertThat(edge(-40, -40)).isZero();
        assertThat(edge(W + 40, 150)).isZero();
        assertThat(edge(200, H + 1)).isZero();
    }

    @Test
    @DisplayName("the tile zones cover the edges and the corners, and corners win")
    void tileZonesArePickedCornersFirst() {
        // A pointer in the top-left is inside BOTH the top zone and the left zone. The player who
        // dragged there meant the quarter, so corners are tested first — without that ordering a
        // corner drag silently produces a half.
        assertThat(DeskManager.CORNER_GRIP).isGreaterThan(DeskManager.RESIZE_MARGIN);
    }

    @Test
    @DisplayName("a panel too small to tell corners apart falls back to edge grips")
    void tinyPanels() {
        // With a 14px corner grip, a 40×30 panel would be entirely "corner" and could only ever be
        // resized diagonally — every point in it is within 14px of two edges. Below the threshold
        // the plain edge test takes over, so the panel still has four distinct edges.
        assertThat(DeskManager.edgeAt(20, 1, 40, 30)).as("top edge, mid-span").isEqualTo(1);
        assertThat(DeskManager.edgeAt(1, 15, 40, 30)).as("left edge, mid-height").isEqualTo(4);
        assertThat(DeskManager.edgeAt(2, 2, 40, 30)).as("actual top-left").isEqualTo(1 | 4);

        // The point that proves the fallback fired: 10,10 is a corner grip in a normal panel and
        // nothing at all in this one.
        assertThat(DeskManager.edgeAt(10, 10, 400, 300)).as("corner grip at full size").isEqualTo(1 | 4);
        assertThat(DeskManager.edgeAt(10, 10, 40, 30)).as("no grip in a tiny panel").isZero();
    }
}
