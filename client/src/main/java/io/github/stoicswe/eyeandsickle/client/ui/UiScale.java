package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;

/**
 * Draws the whole interface larger or smaller, by scaling the scene rather than the fonts.
 *
 * <h2>Why a transform and not a font size</h2>
 *
 * The usual way to scale a JavaFX interface is to set a root {@code -fx-font-size} and express every
 * other dimension in {@code em}. That is not available here, and not by accident: {@code theme.css}
 * is a contract written in pixels, and {@code UiTokens} owns every size in Java pixels
 * ({@code CLAUDE.md} — "sizes, spacings and durations live in {@code ui/UiTokens.java} and nowhere
 * else"). Converting both to relative units to add one setting would rewrite the contract §10's
 * acceptance criteria are checked against.
 *
 * <p>A {@link Scale} on the content costs none of that. Every pixel in the stylesheet keeps its
 * meaning, the character-cell layouts the design language depends on stay exactly proportional, and
 * JavaFX's picking accounts for transforms — so clicks land where they are drawn, which is the thing
 * {@code CrtOverlay} explicitly refuses to risk for curvature.
 *
 * <h2>⚠ The content is unmanaged, and that is load-bearing</h2>
 *
 * {@code CLAUDE.md} records the trap: a <b>managed</b> child of a {@code Pane} is repositioned by
 * that pane's {@code layoutChildren}, silently undoing any {@code resizeRelocate}. The content here
 * is sized to {@code scene / factor} — deliberately not the holder's own size — so a layout pass
 * that "corrected" it back to the holder's bounds would cancel the scale on the next resize and
 * leave the interface cropped. It is unmanaged and this class does the one layout call itself.
 *
 * <h2>⚠ Scaling shrinks the room, so the Stage minimum has to grow with it</h2>
 *
 * At 150% a 1280px window gives the deck 853 logical pixels, under {@link WindowSize#MIN_DECK_WIDTH}.
 * The Stage minimum is therefore {@code floor × factor} and not the floor —
 * {@link WindowSize#usableAt} is the same rule from the preset's side, and the two have to agree or
 * Settings offers a size the Stage then refuses.
 */
public final class UiScale {

    /** The factors offered, as percentages. 100 is the default and the shipped look. */
    public static final int[] PERCENTAGES = {80, 90, 100, 110, 125, 150, 175, 200};

    public static final int DEFAULT_PERCENT = 100;

    private final Scale scale = new Scale(1, 1);
    private final Region content;
    private final Holder holder = new Holder();
    private double factor = 1;

    public UiScale(Region content) {
        this.content = content;
        // Pivot at the origin. The default pivot is (0,0) already, but stating it means a future
        // centre-pivot experiment cannot silently offset every coordinate in the scene.
        scale.setPivotX(0);
        scale.setPivotY(0);
        content.getTransforms().add(scale);
        content.setManaged(false);
        holder.attach(content);
    }

    /** The node to hand to {@code new Scene(...)}. */
    public Parent root() {
        return holder;
    }

    /** Sets the factor as a percentage; anything outside the offered range is clamped. */
    public void setPercent(int percent) {
        int clamped = Math.max(PERCENTAGES[0], Math.min(PERCENTAGES[PERCENTAGES.length - 1], percent));
        this.factor = clamped / 100.0d;
        scale.setX(factor);
        scale.setY(factor);
        holder.requestLayout();
    }

    public double factor() {
        return factor;
    }

    /** Reads a persisted percentage, falling back to 100 for anything unrecognised. */
    public static int sanitise(int percent) {
        for (int offered : PERCENTAGES) {
            if (offered == percent) {
                return percent;
            }
        }
        return DEFAULT_PERCENT;
    }

    /**
     * A Region that lays out exactly one child, at its own size divided by the factor.
     *
     * <p>Not a {@code StackPane}: a StackPane would lay the child out at the holder's full size, and
     * the scale would then draw a 1280-wide deck into a 1280-wide window at 150% — three-quarters of
     * it off the right edge. The division is the entire job.
     */
    private final class Holder extends Region {

        /** {@code Region.getChildren()} is protected, so the attach has to happen from inside. */
        void attach(javafx.scene.Node node) {
            getChildren().add(node);
        }

        @Override
        protected void layoutChildren() {
            double width = getWidth() / factor;
            double height = getHeight() / factor;
            // resizeRelocate, not relocate-then-resize: the content is a Region whose own layout
            // runs off the size it is given, and setting the two separately lays it out twice.
            content.resizeRelocate(0, 0, width, height);
        }
    }
}
