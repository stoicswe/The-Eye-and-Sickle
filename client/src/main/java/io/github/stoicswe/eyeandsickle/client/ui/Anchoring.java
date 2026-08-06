package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Hanging an overlay off a cell in the top strip.
 *
 * <h2>Why this is shared rather than written twice</h2>
 *
 * Two things now drop out from under the strip — the chain-sync report ({@link SyncBanner}) and the
 * balance movement ({@code widgets/BalanceDelta}) — and getting one of them on screen cost four
 * separate debugging rounds, none of which produced an error message. Every one of them is a JavaFX
 * behaviour that is correct, documented and completely invisible when you get it wrong:
 *
 * <ul>
 *   <li>⚠ <b>{@code getLayoutBounds()}, never {@code getBoundsInLocal()}.</b> On a {@code Parent},
 *       {@code boundsInLocal} is the union of its <em>children's</em> bounds. The top strip reported
 *       <b>957px</b> tall on a 900px window, which put the panel off the bottom of the screen while
 *       every figure in the calculation looked plausible.
 *   <li>⚠ <b>An unmanaged node is never resized by its parent.</b> Being unmanaged is what lets an
 *       overlay be placed by translate instead of by the layout — and it also means
 *       {@code setPrefSize} is a request to a pass that will never run on it. {@code getWidth()}
 *       stays 0, the content lays out into nothing, and a clip crops whatever is left.
 *   <li>⚠ <b>Both bounds properties, on both anchors and the parent.</b> {@code layoutBounds} changes
 *       when a node is given a size and {@code boundsInParent} when it is given a position; watching
 *       one leaves the overlay pinned to a stale measurement with nothing to correct it.
 *   <li>⚠ <b>{@code applyCss()} before measuring.</b> Padding, font and border all come from the
 *       stylesheet, so {@code prefWidth(-1)} on a node that has never had CSS applied is zero.
 * </ul>
 *
 * <h2>⚠ Two anchors, and they are different nodes</h2>
 *
 * The horizontal anchor is the <b>cell</b> the overlay belongs to — its right edge, because these
 * hang off readouts near the right-hand end of the strip and are wider than the cell itself. The
 * vertical anchor is the <b>strip</b>. A cell is centred in a strip taller than it, so anchoring the
 * top to the cell leaves a few pixels of overlay painted over the readouts; measured at 27 against
 * 31, which looked right and was right by luck.
 *
 * <h2>⚠ RIGHT-ALIGNED IS NOT UNIVERSAL, and assuming it was put a panel in the screen's corner</h2>
 *
 * "Right-align to the cell, clamp at zero" is correct for a cell near the right-hand <em>end</em> of
 * the strip, which every overlay was until the operator panel. The operator cell is the <b>first</b>
 * cell: right-aligning a 420px panel to a cell whose right edge is at 290 asks for {@code -130}, the
 * clamp turns that into {@code 0}, and what lands is a panel jammed against the window edge, over
 * the rail, lined up with nothing. It reads as having slid in from off screen — which is exactly how
 * it was reported.
 *
 * <p>So the alignment is chosen rather than assumed: right-aligned when there is room to the left of
 * the cell, <b>left-aligned to the cell</b> when there is not. And {@code within} names the region
 * the overlay must stay inside — the <b>desk</b>, so an overlay never covers the rail and never runs
 * off the far edge. A clamp against the whole root cannot express that: the rail is part of the root
 * and is precisely what an overlay must not be clamped on top of.
 */
public final class Anchoring {

    private Anchoring() {}

    /** Places {@code self} under the strip with nothing constraining it but the deck's own root. */
    public static Size place(Region self, Node xAnchor, Node yAnchor) {
        return place(self, xAnchor, yAnchor, null);
    }

    /**
     * Places {@code self} under the strip, aligned to its cell and kept inside {@code within}.
     *
     * @param self the overlay — must be {@code setManaged(false)} and a child of the deck's root
     * @param xAnchor the cell the overlay lines up with; see the class note on which edge
     * @param yAnchor the band whose bottom edge is the overlay's top edge
     * @param within the region the overlay must stay horizontally inside — the desk, so it never
     *     covers the rail. Null means the whole parent, which is only right for an overlay that
     *     cannot reach the rail anyway.
     * @return the size it was given, so a caller can drive a clip or a slide from it
     */
    public static Size place(Region self, Node xAnchor, Node yAnchor, Node within) {
        if (xAnchor == null || self.getParent() == null) {
            return new Size(0, 0);
        }
        Node band = yAnchor == null ? xAnchor : yAnchor;
        Bounds cell = xAnchor.localToScene(xAnchor.getLayoutBounds());
        Bounds strip = band.localToScene(band.getLayoutBounds());
        Bounds parent = self.getParent().localToScene(self.getParent().getLayoutBounds());

        double width = self.prefWidth(-1);
        double height = self.prefHeight(width);

        // The band the overlay may occupy, in the parent's own coordinates.
        double leftLimit = 0;
        double rightLimit = parent.getWidth();
        if (within != null) {
            Bounds field = within.localToScene(within.getLayoutBounds());
            leftLimit = field.getMinX() - parent.getMinX();
            rightLimit = field.getMaxX() - parent.getMinX();
        }

        self.setTranslateX(horizontal(
                cell.getMinX() - parent.getMinX(), cell.getMaxX() - parent.getMinX(),
                width, leftLimit, rightLimit));
        self.setTranslateY(strip.getMaxY() - parent.getMinY());
        self.resize(width, height);
        return new Size(width, height);
    }

    /**
     * Where the overlay's left edge goes, in the parent's coordinates.
     *
     * <h2>⚠ Pure and package-private SO IT CAN BE TESTED WITHOUT A TOOLKIT</h2>
     *
     * The same seam {@code SecurityCenterView.latestOf} and {@code markStateFor} exist for, and for
     * the same reason: the previous version of this rule lived inside a method that needs live scene
     * bounds, so the only way to check it was to render the deck and look — which is how it shipped
     * wrong. Geometry that decides <em>where</em> rather than <em>what</em> is exactly the kind this
     * codebase keeps getting wrong invisibly, and arithmetic is the part that can be pinned.
     *
     * @param cellMinX the anchor cell's left edge
     * @param cellMaxX its right edge
     * @param width the overlay's width
     * @param leftLimit the left edge of the region it must stay inside
     * @param rightLimit that region's right edge
     */
    static double horizontal(double cellMinX, double cellMaxX, double width, double leftLimit, double rightLimit) {
        // Right-aligned to the cell, which is what a readout near the right-hand end of the strip
        // wants — the overlay is far wider than its cell and grows leftward into the space there.
        double x = cellMaxX - width;
        if (x < leftLimit) {
            // ⚠ LEFT-aligned when that does not fit, because a cell near the left-hand END has
            // nothing to its left to align against. Clamping instead — which is what this did until
            // the operator panel existed — produces an overlay flush with the edge of the field,
            // touching neither its own cell nor anything else, which reads as half off screen.
            x = cellMinX;
        }
        // ⚠ min BEFORE max. An overlay wider than the field cannot satisfy both bounds, and this
        // order leaves it flush with the field's LEFT edge overflowing right — visible, and readable
        // from its first character — rather than flush right and running off under the rail.
        return Math.max(leftLimit, Math.min(x, rightLimit - width));
    }

    /** Runs {@code onChange} whenever anything that could move the overlay moves. */
    public static void watch(Region self, Node xAnchor, Node yAnchor, Runnable onChange) {
        watch(self, xAnchor, yAnchor, null, onChange);
    }

    /**
     * The same, also watching the region the overlay is confined to.
     *
     * <p>⚠ The field has to be watched in its own right. The rail collapses below
     * {@code NARROW_WIDTH}, so the desk's left edge moves without the deck root's bounds changing at
     * all — and an overlay placed against the old edge would sit in the wrong place until something
     * else happened to move.
     */
    public static void watch(Region self, Node xAnchor, Node yAnchor, Node within, Runnable onChange) {
        for (Node node : new Node[] {xAnchor, yAnchor, within, self.getParent()}) {
            if (node == null) {
                continue;
            }
            node.layoutBoundsProperty().addListener((o, was, now) -> onChange.run());
            node.boundsInParentProperty().addListener((o, was, now) -> onChange.run());
        }
    }

    /** What {@link #place} settled on. */
    public record Size(double width, double height) {

        /** Whether there is anything to show — a zero size means CSS has not landed yet. */
        public boolean real() {
            return width > 0 && height > 0;
        }
    }
}
