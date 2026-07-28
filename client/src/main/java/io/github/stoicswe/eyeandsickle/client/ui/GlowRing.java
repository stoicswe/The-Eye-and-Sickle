package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * The lit ring — the {@code O} of uOS, and the client's one piece of pure emblem.
 *
 * <h2>⚠ The glow is CONCENTRIC STROKES, not an effect</h2>
 *
 * {@code ui-design-language.md} §9 lists drop shadows, blur and glassmorphism as build-blocking —
 * the 2026-07-28 amendment reversed the <em>rounded corner</em> ban and left that one standing — and
 * {@code UiContractTest} fails the build on a {@code dropshadow(} anywhere in the stylesheet. So the
 * halo is eight circles sharing a centre, six outside the bright ring and two inside it.
 *
 * <p>⚠ The offsets are close and the stroke widths (stylesheet) are wide, so consecutive strokes
 * <b>overlap</b> and their alphas accumulate into a falloff. The first cut spaced four strokes
 * evenly across thirteen points and rendered as four concentric circles — banding, not glow. A glow
 * is a falloff, and a falloff drawn in strokes needs the strokes to touch.
 *
 * <p>Two of the eight sit <em>inside</em> the bright ring. Light spills both ways, and an
 * outward-only halo reads as a printed ring with a shadow rather than as something lit.
 *
 * <h2>⚠ It overflows its own layout box, deliberately</h2>
 *
 * This pane is sized to the <b>bright ring</b>, not to the halo. On the power-on splash the ring is
 * the middle letter of a three-character word, and a box that contained the glow would push {@code u}
 * and {@code S} seventeen points further out on each side — at which point the three characters stop
 * reading as one word. Panes do not clip in JavaFX, so the overflow costs nothing.
 *
 * <p>Extracted so {@link PowerOn} and the setup assistant draw the same emblem from the same recipe.
 * Two copies of eight tuned alphas would drift the first time either was touched.
 */
public final class GlowRing extends StackPane {

    /**
     * The halo, outermost first: how far each stroke sits from the bright ring, at radius 33.
     *
     * <p>Scaled proportionally for other radii, so the emblem looks the same at any size.
     */
    private static final double[] HALO_OFFSETS = {16.5, 12.5, 9, 6, 3.5, 1.5, -2, -4};

    /** The radius the offsets and the stylesheet's stroke widths were tuned against. */
    private static final double REFERENCE_RADIUS = 33;

    /** Matches {@code .es-poweron-ring}'s stroke width — the layout box has to allow for it. */
    private static final double CORE_STROKE = 3;

    private final Group halo = new Group();

    public GlowRing(double radius) {
        double scale = radius / REFERENCE_RADIUS;
        // Outermost first, so the bright core paints last and stays crisp. The offsets shrink and
        // the stylesheet's alphas rise toward it — that ramp IS the glow.
        for (int i = 0; i < HALO_OFFSETS.length; i++) {
            Circle ring = new Circle(radius + HALO_OFFSETS[i] * scale);
            ring.getStyleClass().add("es-poweron-glow-" + (i + 1));
            halo.getChildren().add(ring);
        }

        Circle core = new Circle(radius);
        core.getStyleClass().add("es-poweron-ring");

        getChildren().add(new Group(halo, core));
        double span = radius * 2 + CORE_STROKE * scale;
        setMinSize(span, span);
        setPrefSize(span, span);
        setMaxSize(span, span);
    }

    /**
     * Sets how brightly the halo is burning, 0 to 1.
     *
     * <p>The core never dims — a ring that faded out entirely would read as a thing switching off
     * rather than as a thing glowing.
     */
    public void setGlow(double amount) {
        halo.setOpacity(Math.max(0, Math.min(1, amount)));
    }
}
