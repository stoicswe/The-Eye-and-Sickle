package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * The Security Center's one big state mark: a shield, a warning triangle, or a quarantine trefoil.
 *
 * <h2>⚠ EVERY MARK IS DRAWN. None of them is a glyph.</h2>
 *
 * {@code GlyphCoverageTest} fails the build on any codepoint missing from the two bundled faces, and
 * it has already rejected {@code U+26A0} in this very panel. Shield and biohazard are certainly
 * absent too. So these are {@code Polygon}s, {@code Arc}s and {@code Circle}s — the same decision
 * {@code CLAUDE.md} records for the firmware flash's warning mark, for the carousel's dots and for
 * the credits' network marks. A drawn shape cannot be uncovered and cannot fall back to a host font
 * with different metrics.
 *
 * <h2>⚠ The mark is NEVER the only signal — §4.4</h2>
 *
 * The verdict beside it already says <em>Clear</em>, <em>Check</em> or <em>Quarantine</em> in words,
 * and every mark carries {@code accessibleText}. State that exists only as a picture does not survive
 * greyscale and does not reach a screen reader; this is decoration on top of a sentence, which is the
 * only thing a picture is allowed to be here.
 *
 * <h2>⚠ Motion is STEPPED and decorative</h2>
 *
 * §5 permits no easing anywhere and {@code UiContractTest} rations {@code AnimationTimer} to two
 * files by name, so the shield's sweep and the trefoil's turn move in whole steps on the shared
 * {@link Pulse}. Both are on {@code Pulse.animate}, i.e. <b>decoration</b>: under Reduce motion they
 * never fire and the mark holds one frame — which is WCAG 2.2.2's pause, and is safe here precisely
 * because the shape alone identifies the state. ⚠ That is the test for whether a mark's animation is
 * decoration: <b>if it stopped forever, would the player still know what it says?</b>
 */
public final class SecurityMark extends Pane {

    /** What the rig's security looks like right now. */
    public enum State {
        /** Audited recently, nothing found, something standing guard. */
        CLEAR,

        /**
         * Something to attend to, but nothing has been found.
         *
         * <p>Nothing armed, or the last audit is older than {@code ScanSchedule.STALE_AFTER}, or
         * there has never been one. ⚠ Deliberately not the same as a finding: "nobody has checked"
         * and "something is here" are different sentences and must not share a mark.
         */
        CHECK,

        /** An audit named something. */
        QUARANTINE
    }

    /** How many discrete positions the shield's sweep and the trefoil's turn have. */
    private static final int STEPS = 24;

    private final State state;
    private final Group art = new Group();
    private AutoCloseable ticker;
    private int step;

    /**
     * @param state what to draw
     */
    public SecurityMark(State state) {
        this.state = state;
        setMinSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        setPrefSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        setMaxSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        getStyleClass().add("es-secmark");
        getChildren().add(art);
        // ⚠ Mouse-transparent. It is a status picture, not a control, and a 120px target that
        // swallows clicks over the panel's empty half would be a dead zone nobody could explain.
        setMouseTransparent(true);

        switch (state) {
            case CLEAR -> buildShield();
            case CHECK -> buildWarning();
            case QUARANTINE -> buildTrefoil();
        }
        setAccessibleText(describe());

        // ⚠ Follows the SCENE, not construction. A Pulse subscription on a node nobody is looking at
        // is work with no observer, and Pulse needs a live toolkit — subscribing from the
        // constructor would make this widget untestable without starting one.
        sceneProperty().addListener((observable, was, now) -> {
            if (now == null) {
                dispose();
            } else if (ticker == null && moves()) {
                ticker = Pulse.shared().animate(UiTokens.SECURITY_MARK_STEP_MS, this::advance);
            }
        });
    }

    /** ⚠ CHECK does not move. A warning that pulsed would read as an alarm, which it is not. */
    private boolean moves() {
        return state != State.CHECK;
    }

    private void advance() {
        step = (step + 1) % STEPS;
        double fraction = step / (double) STEPS;
        if (state == State.CLEAR) {
            // The sweep travels down the shield and wraps.
            sweep.setTranslateY(-UiTokens.SECURITY_MARK / 2 + fraction * UiTokens.SECURITY_MARK);
        } else if (state == State.QUARANTINE) {
            // ⚠ Whole steps of 360/STEPS. A continuous rotation is an easing curve however it is
            // spelled, and §5 permits none.
            art.setRotate(fraction * 360);
        }
    }

    /** What this mark is showing, so a caller can tell whether it needs replacing. */
    public State state() {
        return state;
    }

    /** Releases the ticker. Called on detach; safe to call twice. */
    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // A subscription that will not close is not worth failing a repaint over.
            }
            ticker = null;
        }
    }

    private String describe() {
        return switch (state) {
            case CLEAR -> "Shield. The rig audited clean and something is standing guard.";
            case CHECK -> "Warning. Nothing has been found, but the rig needs attention.";
            case QUARANTINE -> "Quarantine. An audit named something on this rig.";
        };
    }

    // ── the three marks ───────────────────────────────────────────────────────────────────────

    private Rectangle sweep;

    /**
     * A shield, with a scan line travelling down it.
     *
     * <p>The outline is a polygon rather than a rounded path: §9's radius ban is about the
     * interface's own geometry and this is an emblem, but a straight-edged shield also reads better
     * against a character grid than a soft one would.
     */
    private void buildShield() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;
        Polygon shield = new Polygon(
                0, -half * 0.86,
                half * 0.72, -half * 0.52,
                half * 0.72, half * 0.12,
                0, half * 0.88,
                -half * 0.72, half * 0.12,
                -half * 0.72, -half * 0.52);
        shield.getStyleClass().add("es-secmark-shield");
        shield.setStrokeWidth(2.5);
        shield.setStrokeLineCap(StrokeLineCap.BUTT);

        // The sweep, clipped to the shield so it reads as travelling INSIDE it rather than across it.
        sweep = new Rectangle(s * 0.72 * 2, 3);
        sweep.setX(-half * 0.72);
        sweep.setY(-1.5);
        sweep.getStyleClass().add("es-secmark-sweep");

        Group inner = new Group(sweep);
        Polygon clip = new Polygon(shield.getPoints().stream().mapToDouble(Double::doubleValue).toArray());
        inner.setClip(clip);

        // A tick inside the shield, so a still frame is still obviously "good" rather than an
        // empty outline. ⚠ This is what makes the animation safe to suppress.
        Polygon tick = new Polygon(
                -half * 0.28, 0,
                -half * 0.10, half * 0.20,
                half * 0.32, -half * 0.26,
                half * 0.32, -half * 0.10,
                -half * 0.10, half * 0.38,
                -half * 0.28, half * 0.16);
        tick.getStyleClass().add("es-secmark-tick");

        art.getChildren().addAll(shield, inner, tick);
        art.setTranslateX(half);
        art.setTranslateY(half);
    }

    /**
     * A warning triangle.
     *
     * <p>⚠ A {@code Polygon} plus two {@code Region}-equivalent shapes, exactly as the firmware
     * flash's mark is built, and for the same reason: {@code U+26A0} is in neither bundled face.
     */
    private void buildWarning() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;
        Polygon triangle = new Polygon(0, -half * 0.84, half * 0.90, half * 0.66, -half * 0.90, half * 0.66);
        triangle.getStyleClass().add("es-secmark-warn");
        triangle.setStrokeWidth(2.5);

        Rectangle bar = new Rectangle(4, half * 0.62);
        bar.setX(-2);
        bar.setY(-half * 0.40);
        bar.getStyleClass().add("es-secmark-warn-fill");

        Rectangle dot = new Rectangle(4, 4);
        dot.setX(-2);
        dot.setY(half * 0.34);
        dot.getStyleClass().add("es-secmark-warn-fill");

        art.getChildren().addAll(triangle, bar, dot);
        art.setTranslateX(half);
        art.setTranslateY(half);
    }

    /**
     * A quarantine trefoil.
     *
     * <p>Three rings at 120° around a centre, which is how the real mark is constructed — three
     * overlapping circles whose intersections form the arms. Drawn as arcs rather than filled
     * circles so the character-grid look survives: a solid disc this size is the heaviest thing on
     * the screen and would fight the verdict for attention.
     */
    private void buildTrefoil() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;
        // Tuned so the rings very nearly meet the centre, which is what makes the three arms read as
        // one emblem rather than as three separate circles.
        double ring = half * 0.40;
        double offset = half * 0.44;

        Circle centre = new Circle(half * 0.17);
        centre.getStyleClass().add("es-secmark-bio");
        centre.setStrokeWidth(2.5);
        art.getChildren().add(centre);

        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(90 + i * 120);
            // ⚠ THE ARC FACES OUTWARD, and getting this backwards is what makes a trefoil read as a
            // propeller. A biohazard's arms are the parts of three overlapping rings that point AWAY
            // from the centre; an arc centred on the inward direction draws the parts that point at
            // it, which renders as three blades around a hub. Found by rendering — the first version
            // used `+ 180` and looked like a fan.
            //
            // JavaFX arcs measure counter-clockwise from 3 o'clock, so the outward-facing 240° is
            // centred on the arm's own direction.
            Arc arm = new Arc(
                    Math.cos(angle) * offset,
                    -Math.sin(angle) * offset,
                    ring,
                    ring,
                    Math.toDegrees(angle) - 120,
                    240);
            arm.setType(ArcType.OPEN);
            arm.getStyleClass().add("es-secmark-bio");
            arm.setStrokeWidth(2.5);
            art.getChildren().add(arm);
        }
        art.setTranslateX(half);
        art.setTranslateY(half);
    }
}
