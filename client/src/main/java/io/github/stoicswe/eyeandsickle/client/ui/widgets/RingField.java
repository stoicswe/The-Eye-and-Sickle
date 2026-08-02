package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.GlowRing;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;

/**
 * The lit ring, at desk scale, behind everything — and the fault that runs through it.
 *
 * <h2>What it is</h2>
 *
 * The emblem from the power-on splash used as wallpaper: the same {@link GlowRing} recipe, so the
 * thing the player sees for two seconds at launch is the thing behind their desk all session.
 * Nothing here is read off game state; it is texture, and {@code ui-design-language.md} §4 budgets
 * roughly 10–15% of pixels to exactly that.
 *
 * <h2>⚠ Never in amber, and that is not a style preference</h2>
 *
 * §2.1 reserves amber for cycles doing work and income, and the design language is explicit that the
 * reservation "matters most on the largest surface in the client" — the character wallpaper is held
 * to {@code dim-3} for the same reason. So the ring resolves {@code -es-text-hi}, which is also what
 * makes <b>uOS Classic invert for free</b>: that palette runs its ramp the other way, so the same
 * token is a faint lit ring on the dark decks and a faint drawn one on the light.
 *
 * <h2>⚠ The glitch is SLICED GEOMETRY, not a filter</h2>
 *
 * §9 lists blur and drop shadows as build-blocking, so the datamosh look cannot come from an effect.
 * The ring is drawn {@link #BANDS} times, each copy clipped to one slice, and slices are displaced
 * along the slice axis. At intensity zero the copies line up into one clean ring — the resting state
 * is this path at rest rather than a second path that could drift from it.
 *
 * <h2>⚠ It never fully rests, and the axis turns</h2>
 *
 * The fault runs continuously between a calm floor and a violent peak, and the <b>slice axis flips
 * between horizontal and vertical</b> at the calm point of every cycle — tearing sideways for a
 * while, then downward. Flipping at the floor rather than at an arbitrary moment is what keeps it
 * from snapping: there is almost nothing displaced at the moment the axis turns.
 *
 * <p>⚠ The flip is a <b>rotation of the whole stack</b>, not a second set of slices. The ring is a
 * circle, so a ring sliced horizontally and turned a quarter <em>is</em> a ring sliced vertically —
 * and building both would double a node count that is already {@link #BANDS} copies of a
 * nine-circle emblem.
 * That is also why the slices cover a <b>square</b>: a band region shaped like the desk would leave
 * two uncovered wedges the moment it turned.
 *
 * <h2>⚠ The far end is disproportionate, on purpose</h2>
 *
 * Displacement goes as the {@link #EXTREMITY} power of the envelope rather than linearly with it, so
 * the calm stretches stay genuinely calm while the peak tears the emblem apart. A linear ramp with
 * the same peak spends most of its time visibly wobbling, which behind text is a legibility problem
 * rather than an effect.
 *
 * <p>⚠ Driven by {@link Pulse#animate} — decorative, so <b>reduced motion holds one frame</b>. §5
 * rations continuous motion and {@code UiContractTest} allows {@code AnimationTimer} in two files by
 * name; this is not one of them.
 */
public final class RingField extends Region {

    /**
     * Slices the ring is cut into.
     *
     * <p>⚠ This is the expensive number. Each slice is its own copy of a nine-circle emblem plus two
     * fringe circles, so the node count is {@code BANDS × 11} — 792 at this value. It buys the fine
     * tearing the reference images have; doubling it again would buy diminishing thinness for a
     * linear cost in shapes the renderer has to rasterise every frame.
     *
     * <p>⚠ The per-tick cost is <b>not</b> proportional to this. A tick sets one translate per band —
     * {@code BANDS} property writes, not {@code BANDS × 11} — because the copy is a {@link Group} and
     * the transform is on the group.
     */
    private static final int BANDS = 72;

    /** Steps in one axis-cycle. At the tick below, a little under a minute on each axis. */
    private static final int CYCLE_STEPS = 700;

    /**
     * How long one step lasts.
     *
     * <p>⚠ Smoothness here comes from a <b>finer ladder</b>, never from interpolation. §5 permits no
     * easing anywhere and §9 makes it build-blocking, so the way to make stepped motion read as
     * continuous is to make the steps small — the same thing {@code UiTokens.REVEAL_STEPS} does for
     * every reveal in the client. At 70ms the individual steps are below the threshold at which a
     * slow drift reads as stepping.
     */
    private static final double STEP_MS = 70;

    /**
     * Steps in one colour-shift cycle.
     *
     * <p>⚠ Deliberately <b>not</b> a multiple of {@link #CYCLE_STEPS}, and co-prime with it. The
     * colour breathes on its own period, so the tear and the fringe drift in and out of phase instead
     * of pulsing together — two effects locked to one clock read as one effect, which is precisely
     * what makes a loop obvious.
     */
    private static final int CHROMA_CYCLE_STEPS = 1131;

    /** How far the worst slice may slide at full intensity, as a fraction of the ring's radius. */
    private static final double MAX_SLIP = 0.95;

    /**
     * How much the far end of the range dominates.
     *
     * <p>Above 1, so displacement climbs slowly and then runs away. At 2.2 an envelope of 0.3 moves a
     * slice about 7% of the distance it would at 1.0 — the difference between a wallpaper that is
     * always slightly unsettled and one that is calm until it is not.
     */
    private static final double EXTREMITY = 2.2;

    /**
     * The quietest the fault ever gets.
     *
     * <p>⚠ Not zero: the ring is meant to be continuously alive rather than resting between events.
     * The floor is small and {@link #EXTREMITY} is steep, so the calm phase is a shimmer rather than
     * a tear — but the emblem is never perfectly whole.
     */
    private static final double FLOOR = 0.10;

    /** Fraction of the shorter edge the ring's radius takes. Large enough to be a presence. */
    private static final double RADIUS_FRACTION = 0.30;

    /** How far the colour fringes separate at full colour, as a fraction of the slice's own slip. */
    private static final double CHROMATIC_SPREAD = 0.28;

    /** The quietest the colour ever gets, as a fraction of full. Never zero while it is switched on. */
    private static final double CHROMA_FLOOR = 0.18;

    /** How strong a fringe is at full colour. Its own opacity, since CSS cannot scale on a tick. */
    private static final double CHROMA_OPACITY = 0.62;

    private final Group stack = new Group();
    private final Rotate spin = new Rotate();
    private final List<Group> bands = new ArrayList<>();
    private final List<Rectangle> clips = new ArrayList<>();
    private final List<Circle> warm = new ArrayList<>();
    private final List<Circle> cool = new ArrayList<>();
    private final double[] slip = new double[BANDS];

    private boolean glitching;
    private boolean chromatic;
    private int step;
    private double radius;
    private AutoCloseable ticker;

    public RingField() {
        getStyleClass().add("es-ringfield");
        setMouseTransparent(true);
        stack.getTransforms().add(spin);
        getChildren().add(stack);

        // ⚠ A FIXED seed. The displacements must be identical on every machine and every run, or a
        // render cannot be compared against the last one and nobody can review a change to this.
        Random random = new Random(0x5E1CE);
        for (int i = 0; i < BANDS; i++) {
            // Biased small: most slices barely move and a few go a long way, which is what makes it
            // read as tearing rather than as a shear.
            double magnitude = Math.pow(random.nextDouble(), 2.2);
            slip[i] = (random.nextBoolean() ? 1 : -1) * magnitude;
        }

        // ⚠ The ticker follows the SCENE, not just the setting. A Pulse subscription on a node nobody
        // has put on screen is work with no observer — and because Pulse needs a live toolkit,
        // subscribing from a plain setter would make this widget impossible to exercise without
        // starting one, which this repo keeps to a single file by convention.
        sceneProperty().addListener((o, was, now) -> syncTicker());
        setGlitching(false);
    }

    /** Whether the fault runs at all. Off leaves a clean ring, still. */
    public void setGlitching(boolean on) {
        this.glitching = on;
        step = 0;
        syncTicker();
        rebuildIfNeeded();
        apply();
    }

    /**
     * Whether the tears carry colour fringes.
     *
     * <p>⚠ Off by default, like every other artefact (§9.1): an effect the player switches on is a
     * costume, one welded to the interface is a claim about fidelity the interface then has to keep
     * making. It also does nothing unless the fault is running — a fringe is an artefact <em>of</em>
     * the displacement, so with nothing displaced there is nothing to fringe.
     */
    public void setChromatic(boolean on) {
        this.chromatic = on;
        apply();
    }

    /**
     * Jumps the envelope to a point in its cycle.
     *
     * <p>⚠ A render seam, and the only way to see the fault in a snapshot: a synchronous render never
     * runs a {@link Pulse} tick, so a harness left to itself photographs whatever the cycle starts on
     * and reports the effect as working. {@code 0.5} is the peak; adding 1 moves to the next axis.
     * Nothing in the client calls it.
     *
     * @param phase 0–2, where the whole number selects the axis and the fraction the point in it
     */
    public void seekForRender(double phase) {
        double clamped = Math.max(0, Math.min(2, phase));
        step = (int) Math.round(clamped * CYCLE_STEPS);
        apply();
    }

    private void syncTicker() {
        boolean wanted = glitching && getScene() != null;
        if (wanted == (ticker != null)) {
            return;
        }
        if (wanted) {
            ticker = Pulse.shared().animate(STEP_MS, this::advance);
        } else {
            stopTicker();
        }
    }

    private void advance() {
        step++;
        apply();
    }

    /**
     * How hard the fault is biting, {@link #FLOOR} to 1.
     *
     * <p>A triangle: it climbs to the peak and comes back down. ⚠ Deliberately not a sine — §5 permits
     * no easing anywhere, and an eased envelope is an easing curve however it is spelled.
     */
    private double envelope() {
        double phase = (step % CYCLE_STEPS) / (double) CYCLE_STEPS;
        double ramp = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
        return FLOOR + (1 - FLOOR) * ramp;
    }

    /** Which way the slices run. Flips every cycle, at the moment the fault is quietest. */
    private boolean vertical() {
        return (step / CYCLE_STEPS) % 2 == 1;
    }

    /**
     * How much colour there is right now, {@link #CHROMA_FLOOR} to 1.
     *
     * <p>Its own triangle on its own period — the fringes intensify and fall back independently of
     * the tearing, so the two never quite repeat together. ⚠ A triangle again, not a sine: §5's rule
     * about easing does not stop applying because the thing being eased is a colour.
     */
    private double chroma() {
        double phase = (step % CHROMA_CYCLE_STEPS) / (double) CHROMA_CYCLE_STEPS;
        double ramp = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
        return CHROMA_FLOOR + (1 - CHROMA_FLOOR) * ramp;
    }

    private void apply() {
        if (bands.isEmpty()) {
            return;
        }
        // ⚠ Rotated about the DESK's centre, not the stack's own. A Group's bounds are whatever its
        // children happen to occupy, so pivoting on those would swing the ring across the screen as
        // it turned rather than turning it in place.
        spin.setAngle(glitching && vertical() ? 90 : 0);
        spin.setPivotX(getWidth() / 2);
        spin.setPivotY(getHeight() / 2);

        double amount = glitching ? Math.pow(envelope(), EXTREMITY) : 0;
        boolean fringed = chromatic && glitching;
        double colour = fringed ? chroma() : 0;
        for (int i = 0; i < bands.size(); i++) {
            double offset = slip[i] * amount * radius * MAX_SLIP;
            bands.get(i).setTranslateX(offset);
            // The fringe follows the tear and scales with it, so colour separates where the damage is
            // and stays invisible where the ring is whole — which is what a convergence error
            // actually does, and what the reference images show. On top of that it breathes on its
            // own period, so the colour drifts in and out of step with the tearing.
            warm.get(i).setVisible(fringed);
            cool.get(i).setVisible(fringed);
            if (fringed) {
                warm.get(i).setTranslateX(-offset * CHROMATIC_SPREAD * colour);
                cool.get(i).setTranslateX(offset * CHROMATIC_SPREAD * colour);
                // ⚠ Set here rather than in the stylesheet. Opacity has to change every tick and CSS
                // cannot be driven on a clock — and a property CSS also declares would be overwritten
                // on the next applyCss. Same split as the stroke widths.
                warm.get(i).setOpacity(CHROMA_OPACITY * colour);
                cool.get(i).setOpacity(CHROMA_OPACITY * colour);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        rebuildIfNeeded();
    }

    /**
     * Builds the sliced ring, or rebuilds it when the desk changes size.
     *
     * <p>⚠ Rebuilt on a size change rather than scaled. A scaled ring scales its stroke widths with
     * it, and the halo's falloff is tuned in stroke widths — see {@link GlowRing}. Rebuilding is cheap
     * because it happens on resize, never on a tick.
     */
    private void rebuildIfNeeded() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        double wanted = Math.min(w, h) * RADIUS_FRACTION;
        if (Math.abs(wanted - radius) < 1 && !bands.isEmpty()) {
            layoutBands(w, h);
            apply();
            return;
        }
        radius = wanted;
        stack.getChildren().clear();
        bands.clear();
        clips.clear();
        warm.clear();
        cool.clear();
        for (int i = 0; i < BANDS; i++) {
            // ⚠ The fringes are the CORE circle only, never a whole halo. A fringe is an edge
            // artefact, and giving each one the full nine-circle emblem would triple a node count
            // that is already 234 circles for the ring itself.
            Circle warmCore = fringe("es-ringfield-warm");
            Circle coolCore = fringe("es-ringfield-cool");
            GlowRing ring = new GlowRing(radius, "es-ringfield");

            Group band = new Group(warmCore, coolCore, ring);
            Rectangle clip = new Rectangle();
            band.setClip(clip);

            warm.add(warmCore);
            cool.add(coolCore);
            bands.add(band);
            clips.add(clip);
            stack.getChildren().add(band);
        }
        layoutBands(w, h);
        apply();
    }

    /** One colour fringe: the bright ring's circle, in one channel, hidden until it is asked for. */
    private Circle fringe(String styleClass) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().add(styleClass);
        // Matched to the ring rather than guessed, so a fringe sits exactly on the edge it fringes.
        circle.setStrokeWidth(GlowRing.coreStrokeFor(radius));
        circle.setVisible(false);
        return circle;
    }

    /**
     * Puts every copy on the desk's centre and crops it to its own slice.
     *
     * <p>⚠ The slices cover a <b>square</b> of the longer edge, centred on the ring. The band region
     * turns a quarter on alternate cycles, and a region shaped like the desk would leave two
     * uncovered wedges the moment it did — the ring's top and bottom simply missing.
     */
    private void layoutBands(double w, double h) {
        double side = Math.max(w, h);
        double left = w / 2 - side / 2;
        double top = h / 2 - side / 2;
        double bandHeight = side / BANDS;
        for (int i = 0; i < bands.size(); i++) {
            for (Node child : bands.get(i).getChildren()) {
                if (child instanceof Region ring) {
                    ring.relocate(w / 2 - ring.prefWidth(-1) / 2, h / 2 - ring.prefHeight(-1) / 2);
                } else if (child instanceof Circle circle) {
                    circle.setCenterX(w / 2);
                    circle.setCenterY(h / 2);
                }
            }
            Rectangle clip = clips.get(i);
            clip.setX(left);
            clip.setY(top + i * bandHeight);
            clip.setWidth(side);
            // ⚠ A hair of overlap. Clips that met exactly left a seam on every boundary — a row of
            // hairlines across the emblem that reads as a defect rather than as a slice.
            clip.setHeight(bandHeight + 1);
        }
    }

    private void stopTicker() {
        if (ticker == null) {
            return;
        }
        try {
            ticker.close();
        } catch (Exception ignored) {
            // Unsubscribing cannot fail, and a wallpaper is not a reason to take the deck down.
        }
        ticker = null;
    }

    /** Stops the driver. Called when the wallpaper changes mode or the deck goes away. */
    public void dispose() {
        stopTicker();
    }
}
