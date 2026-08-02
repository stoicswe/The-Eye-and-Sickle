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
 */
public final class Anchoring {

    private Anchoring() {}

    /**
     * Places {@code self} under the strip, right-aligned to its cell, and sizes it.
     *
     * @param self the overlay — must be {@code setManaged(false)} and a child of the deck's root
     * @param xAnchor the cell whose right edge the overlay lines up with
     * @param yAnchor the band whose bottom edge is the overlay's top edge
     * @return the size it was given, so a caller can drive a clip or a slide from it
     */
    public static Size place(Region self, Node xAnchor, Node yAnchor) {
        if (xAnchor == null || self.getParent() == null) {
            return new Size(0, 0);
        }
        Node band = yAnchor == null ? xAnchor : yAnchor;
        Bounds cell = xAnchor.localToScene(xAnchor.getLayoutBounds());
        Bounds strip = band.localToScene(band.getLayoutBounds());
        Bounds parent = self.getParent().localToScene(self.getParent().getLayoutBounds());

        double width = self.prefWidth(-1);
        double height = self.prefHeight(width);

        // ⚠ Clamped at zero. On a narrow deck — or with the strip wrapped onto two rows — the overlay
        // can be wider than everything to the left of its cell, and a negative translate would put it
        // off the left-hand edge rather than merely overlapping.
        self.setTranslateX(Math.max(0, cell.getMaxX() - parent.getMinX() - width));
        self.setTranslateY(strip.getMaxY() - parent.getMinY());
        self.resize(width, height);
        return new Size(width, height);
    }

    /** Runs {@code onChange} whenever anything that could move the overlay moves. */
    public static void watch(Region self, Node xAnchor, Node yAnchor, Runnable onChange) {
        for (Node node : new Node[] {xAnchor, yAnchor, self.getParent()}) {
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
