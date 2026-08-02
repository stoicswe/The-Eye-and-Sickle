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

    /**
     * The halo's stroke widths, outermost first, at {@link #REFERENCE_RADIUS}.
     *
     * <h2>⚠ Scaled with the offsets, or the glow bands</h2>
     *
     * The offsets above are scaled by the radius and these have to be scaled by the same factor,
     * because <b>the glow is the overlap between them</b>. Leave the widths fixed and blow the radius
     * up to desk scale and the strokes stop touching — which draws eight separate concentric circles,
     * the exact "banding, not glow" failure this class was written to avoid. Seen on the first cut of
     * the ring wallpaper at eight times the reference radius.
     *
     * <p>⚠ They live here rather than in the stylesheet because a stroke width is a <b>size</b>, and
     * this repo splits on exactly that line: colours in {@code theme.css}, sizes in Java. The splash
     * still sets its widths in CSS and is left alone — it is drawn at the reference radius, so its
     * scale factor is 1 and there is nothing to scale.
     */
    private static final double[] GLOW_STROKES = {9, 8, 7, 6, 5, 4, 4, 5};

    private final Group halo = new Group();

    public GlowRing(double radius) {
        this(radius, "es-poweron", false);
    }

    /**
     * The same emblem in another palette.
     *
     * <h2>⚠ Why a style base rather than a second widget</h2>
     *
     * The eight offsets and the eight alphas that go with them are <b>tuned as a set</b> — the whole
     * class comment above is about why they are the values they are. A second copy for the wallpaper
     * would drift from this one the first time either was touched, which is exactly what extracting
     * this class was meant to prevent. So the geometry is shared and only the tokens differ.
     *
     * <p>⚠ The two palettes are not interchangeable. {@code es-poweron}'s colours are declared as
     * literal white and black and <b>resolve nowhere else</b>, because firmware runs before anything
     * knows who the player is. A wallpaper is the opposite case: it is the largest surface in the
     * client and has to follow the theme, so it resolves palette tokens.
     *
     * @param styleBase the class prefix — {@code <base>-ring} for the core, {@code <base>-glow-1..8}
     *     for the halo, outermost first
     */
    public GlowRing(double radius, String styleBase) {
        this(radius, styleBase, true);
    }

    /**
     * @param scaleStrokes whether to scale the stroke widths with the radius. True for anything drawn
     *     far from {@link #REFERENCE_RADIUS} — see {@link #GLOW_STROKES}. The splash passes false and
     *     keeps its widths in the stylesheet, because at the reference radius the factor is 1
     */
    public GlowRing(double radius, String styleBase, boolean scaleStrokes) {
        double scale = radius / REFERENCE_RADIUS;
        // Outermost first, so the bright core paints last and stays crisp. The offsets shrink and
        // the stylesheet's alphas rise toward it — that ramp IS the glow.
        for (int i = 0; i < HALO_OFFSETS.length; i++) {
            Circle ring = new Circle(radius + HALO_OFFSETS[i] * scale);
            ring.getStyleClass().add(styleBase + "-glow-" + (i + 1));
            if (scaleStrokes) {
                // ⚠ Set here and NOT in the stylesheet for this variant. A styleable property that
                // CSS also declares is overwritten on the next applyCss, so the two cannot both
                // specify it — `.es-ringfield-glow-*` deliberately declares colour and opacity only.
                ring.setStrokeWidth(GLOW_STROKES[i] * scale);
            }
            halo.getChildren().add(ring);
        }

        Circle core = new Circle(radius);
        core.getStyleClass().add(styleBase + "-ring");
        if (scaleStrokes) {
            core.setStrokeWidth(CORE_STROKE * scale);
        }

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
    /**
     * The core stroke width this class would use at {@code radius}.
     *
     * <p>Exposed so a caller drawing something that has to sit exactly on the bright ring — the
     * wallpaper's colour fringes — gets the same width rather than guessing one that nearly matches.
     */
    public static double coreStrokeFor(double radius) {
        return CORE_STROKE * (radius / REFERENCE_RADIUS);
    }

    public void setGlow(double amount) {
        halo.setOpacity(Math.max(0, Math.min(1, amount)));
    }
}
