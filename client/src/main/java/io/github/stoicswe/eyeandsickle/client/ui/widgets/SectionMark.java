package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * The illustration in the top-right of a Security Center section — detective, castle, alarm clock.
 *
 * <h2>⚠ DRAWN, never a glyph, and that is settled rather than a preference</h2>
 *
 * {@code GlyphCoverageTest} parses both bundled TTFs and fails the build on any codepoint neither
 * carries. It has already rejected {@code U+26A0} <em>in this very panel</em>, and a detective, a
 * castle and an alarm clock are certainly absent from Martian Mono and IBM Plex Mono. §9 also bans
 * icon fonts and Material/Lucide sets outright. So these are {@link Polygon}s, {@link Circle}s,
 * {@link Arc}s and {@link Line}s — the same decision {@link SecurityMark}, the flash overlay's
 * warning mark and the carousel's dots all record.
 *
 * <h2>⚠ No colour of its own</h2>
 *
 * Every part resolves a stylesheet token through a style class. §2.1 spends amber on cycles doing
 * work and income and rations {@code alarm} to loss and hostile state at twice a screen — an
 * illustration is neither, and {@link SecurityMark} already spends this panel's whole alarm budget
 * on the verdict. These sit on the neutral ramp, which is also what keeps them from competing with
 * the one thing on the screen that is allowed to shout.
 *
 * <h2>Motion</h2>
 *
 * Only the detective's magnifying glass moves, and it moves in whole steps on {@code Pulse.animate}
 * — the decorative channel — so Reduce motion holds one frame. ⚠ The test {@code SecurityMark}'s
 * comment sets is the one that matters: <b>if it stopped forever, would the player still know what
 * it says?</b> A detective holding a magnifying glass still reads as a detective at rest, so the
 * search sweep is safe to suppress. The castle and the clock never move at all.
 */
public final class SectionMark extends Pane {

    /** Which illustration. One per Security Center section that has one. */
    public enum Kind {
        /** AUDIT — a detective, with the magnifying glass sweeping. */
        DETECTIVE,
        /** DEFENSE — a small castle. */
        CASTLE,
        /** SCHEDULE — an analog alarm clock. */
        CLOCK
    }

    /** Steps in the magnifier's sweep. Whole steps: a continuous arc is an easing curve (§5). */
    private static final int STEPS = 24;

    private final Kind kind;
    private final Group art = new Group();
    private Rectangle glare;
    private double glareTravel;
    private double glareHome;
    private AutoCloseable ticker;
    private int step;

    public SectionMark(Kind kind) {
        this.kind = kind;
        setMinSize(UiTokens.SECTION_MARK, UiTokens.SECTION_MARK);
        setPrefSize(UiTokens.SECTION_MARK, UiTokens.SECTION_MARK);
        setMaxSize(UiTokens.SECTION_MARK, UiTokens.SECTION_MARK);
        getStyleClass().add("es-sectionmark");
        getChildren().add(art);
        // ⚠ Mouse-transparent, like SecurityMark: a picture, not a control, and a silent dead zone
        // over a panel's corner is the sort of thing nobody ever manages to explain.
        setMouseTransparent(true);

        switch (kind) {
            case DETECTIVE -> buildDetective();
            case CASTLE -> buildCastle();
            case CLOCK -> buildClock();
        }
        setAccessibleText(describe());

        // ⚠ Follows the SCENE, not construction — a Pulse subscription on a node nobody is looking
        // at is work with no observer, and Pulse needs a live toolkit, so subscribing from the
        // constructor would make this widget untestable without starting one.
        sceneProperty().addListener((observable, was, now) -> {
            if (now == null) {
                dispose();
            } else if (ticker == null && glare != null) {
                ticker = Pulse.shared().animate(UiTokens.SECURITY_MARK_STEP_MS, this::advance);
            }
        });
    }

    public Kind kind() {
        return kind;
    }

    /**
     * The glare crosses the lens, in whole steps, and rests off it for most of the cycle.
     *
     * <p>⚠ It is a light passing the glass, so it <b>sweeps one way and starts again</b> rather than
     * travelling out and back — a reflection that retraces its path reads as the lens rocking. The
     * long dark tail is what makes it a passing light rather than a blinking one.
     */
    private void advance() {
        if (glare == null) {
            return;
        }
        step = (step + 1) % STEPS;
        // ⚠ Only the first part of the cycle moves it across; the rest is the glass sitting dark.
        // A glare present on every frame is a highlight painted on, not a light going past.
        double sweep = STEPS * 0.45;
        if (step >= sweep) {
            glare.setVisible(false);
            return;
        }
        glare.setVisible(true);
        glare.setX(glareHome + (step / sweep) * glareTravel * 2);
    }

    private String describe() {
        return switch (kind) {
            case DETECTIVE -> "Audit";
            case CASTLE -> "Defense";
            case CLOCK -> "Schedule";
        };
    }

    /**
     * A detective in silhouette — fedora, trench coat, magnifying glass held up.
     *
     * <h2>⚠ The hat and the coat collar are what make it a DETECTIVE</h2>
     *
     * A head-and-shoulders outline with a circle beside it is a person holding a lens. What names
     * the figure is the fedora's <b>pinched crown and wide brim</b> and the coat's <b>peaked
     * lapels</b> — three shapes, no face. Faceless is not a shortcut: the reference is faceless too,
     * and a silhouette that grows eyes at this size reads as a cartoon rather than as a mark.
     *
     * <p>⚠ The glass is held at the LEFT, in front of the body, and is large — a small lens tucked
     * beside the head reads as a lollipop. It overlaps the shoulder deliberately, which is what puts
     * it in front of the figure rather than beside it.
     */
    private void buildDetective() {
        double s = UiTokens.SECTION_MARK;

        // The coat: wide shoulders falling away, with the collar cut as two peaks at the neck.
        Polygon coat = new Polygon(
                s * 0.12, s * 1.00,
                s * 0.17, s * 0.72,
                s * 0.30, s * 0.60,
                s * 0.44, s * 0.56,
                s * 0.50, s * 0.68,
                s * 0.56, s * 0.56,
                s * 0.70, s * 0.60,
                s * 0.83, s * 0.72,
                s * 0.88, s * 1.00);
        coat.getStyleClass().add("es-sectionmark-ink");

        // Head and neck, the neck squared off so the collar has something to sit against.
        Circle head = new Circle(s * 0.50, s * 0.42, s * 0.135);
        head.getStyleClass().add("es-sectionmark-ink");
        Rectangle neck = new Rectangle(s * 0.43, s * 0.50, s * 0.14, s * 0.10);
        neck.getStyleClass().add("es-sectionmark-ink");

        // The fedora. ⚠ The brim is a flattened ELLIPSE and the crown a tapered polygon — a
        // rectangle crown and a rectangle brim read as a top hat, which is a different character.
        javafx.scene.shape.Ellipse brim = new javafx.scene.shape.Ellipse(s * 0.50, s * 0.305, s * 0.255, s * 0.052);
        brim.getStyleClass().add("es-sectionmark-ink");
        Polygon crown = new Polygon(
                s * 0.345, s * 0.305,
                s * 0.385, s * 0.165,
                s * 0.615, s * 0.165,
                s * 0.655, s * 0.305);
        crown.getStyleClass().add("es-sectionmark-ink");
        // The pinch: a notch of panel colour bitten out of the crown's top, which is the single
        // detail that separates a fedora from a bowler at this size.
        Polygon pinch = new Polygon(
                s * 0.455, s * 0.165,
                s * 0.500, s * 0.225,
                s * 0.545, s * 0.165);
        pinch.getStyleClass().add("es-sectionmark-cut");

        // The glass: a heavy ring, a handle running down to the right, and the lens itself.
        double lensX = s * 0.245;
        double lensY = s * 0.545;
        double lensR = s * 0.175;
        Circle ring = new Circle(lensX, lensY, lensR);
        ring.getStyleClass().add("es-sectionmark-lens");
        ring.setStrokeWidth(s * 0.070);
        Line handle = new Line(
                lensX + lensR * 0.70, lensY + lensR * 0.70,
                lensX + lensR * 1.55, lensY + lensR * 1.55);
        handle.getStyleClass().add("es-sectionmark-line");
        handle.setStrokeWidth(s * 0.070);
        handle.setStrokeLineCap(StrokeLineCap.ROUND);

        // ⚠ THE GLARE MOVES, THE GLASS DOES NOT. A bar of light travelling across the lens, clipped
        // to the lens, so it reads as a reflection crossing curved glass rather than as the prop
        // being waved about. Same construction as SecurityMark's shield sweep, and the same reason:
        // a clipped bar is a reflection, an unclipped one is a stripe drawn over the picture.
        glare = new Rectangle(lensR * 0.42, lensR * 2.8);
        glare.getStyleClass().add("es-sectionmark-glare");
        glare.setRotate(28);
        Group lensGroup = new Group(glare);
        Circle lensClip = new Circle(lensX, lensY, lensR - s * 0.035);
        lensGroup.setClip(lensClip);
        glareTravel = lensR * 2.2;
        glareHome = lensX - glareTravel / 2;
        glare.setY(lensY - lensR * 1.4);
        glare.setX(glareHome);

        art.getChildren().addAll(coat, neck, head, brim, crown, pinch, lensGroup, ring, handle);
    }

    /**
     * A small castle: a curtain wall with crenellations between two towers.
     *
     * <p>⚠ Crenellations are drawn as gaps in a filled band rather than as separate blocks, so the
     * silhouette stays one shape. Separate blocks read as a bar chart at this size — which, on a
     * panel full of meters, is the one thing it must not look like.
     */
    private void buildCastle() {
        double s = UiTokens.SECTION_MARK;

        Polygon keep = new Polygon(
                // left tower, up and over its battlements
                s * 0.14, s * 0.92,
                s * 0.14, s * 0.34,
                s * 0.20, s * 0.34,
                s * 0.20, s * 0.26,
                s * 0.26, s * 0.26,
                s * 0.26, s * 0.34,
                s * 0.32, s * 0.34,
                s * 0.32, s * 0.26,
                s * 0.38, s * 0.26,
                s * 0.38, s * 0.34,
                // the curtain wall between the towers, lower than both
                s * 0.38, s * 0.46,
                s * 0.44, s * 0.46,
                s * 0.44, s * 0.38,
                s * 0.50, s * 0.38,
                s * 0.50, s * 0.46,
                s * 0.56, s * 0.46,
                s * 0.56, s * 0.38,
                s * 0.62, s * 0.38,
                s * 0.62, s * 0.46,
                // right tower
                s * 0.62, s * 0.34,
                s * 0.68, s * 0.34,
                s * 0.68, s * 0.26,
                s * 0.74, s * 0.26,
                s * 0.74, s * 0.34,
                s * 0.80, s * 0.34,
                s * 0.80, s * 0.26,
                s * 0.86, s * 0.26,
                s * 0.86, s * 0.92);
        keep.getStyleClass().add("es-sectionmark-ink");

        // The gate. Cut as a separate shape in the PANEL colour rather than by subtracting from the
        // polygon: a Shape.subtract would flatten the whole silhouette into one path and make every
        // later edit a geometry problem instead of a coordinate one.
        Arc gate = new Arc(s * 0.50, s * 0.78, s * 0.10, s * 0.10, 0, 180);
        gate.setType(ArcType.ROUND);
        gate.getStyleClass().add("es-sectionmark-cut");
        Rectangle gateBody = new Rectangle(s * 0.40, s * 0.78, s * 0.20, s * 0.14);
        gateBody.getStyleClass().add("es-sectionmark-cut");

        art.getChildren().addAll(keep, gate, gateBody);
    }

    /**
     * An analog alarm clock: a face with two bells and two legs.
     *
     * <p>⚠ The hands sit at a readable angle rather than at twelve o'clock. A clock showing exactly
     * 12:00 reads as a stopped clock, and this panel is about a schedule that is running.
     */
    private void buildClock() {
        double s = UiTokens.SECTION_MARK;
        double cx = s * 0.50;
        double cy = s * 0.56;
        double r = s * 0.28;

        Circle bellLeft = new Circle(s * 0.27, s * 0.24, s * 0.10);
        bellLeft.getStyleClass().add("es-sectionmark-ink");
        Circle bellRight = new Circle(s * 0.73, s * 0.24, s * 0.10);
        bellRight.getStyleClass().add("es-sectionmark-ink");

        Line legLeft = new Line(s * 0.30, s * 0.82, s * 0.20, s * 0.95);
        legLeft.getStyleClass().add("es-sectionmark-line");
        legLeft.setStrokeWidth(s * 0.05);
        legLeft.setStrokeLineCap(StrokeLineCap.ROUND);
        Line legRight = new Line(s * 0.70, s * 0.82, s * 0.80, s * 0.95);
        legRight.getStyleClass().add("es-sectionmark-line");
        legRight.setStrokeWidth(s * 0.05);
        legRight.setStrokeLineCap(StrokeLineCap.ROUND);

        Circle face = new Circle(cx, cy, r);
        face.getStyleClass().add("es-sectionmark-face");
        face.setStrokeWidth(s * 0.05);

        Line hour = new Line(cx, cy, cx + r * 0.45, cy - r * 0.30);
        hour.getStyleClass().add("es-sectionmark-line");
        hour.setStrokeWidth(s * 0.045);
        hour.setStrokeLineCap(StrokeLineCap.ROUND);
        Line minute = new Line(cx, cy, cx - r * 0.18, cy - r * 0.66);
        minute.getStyleClass().add("es-sectionmark-line");
        minute.setStrokeWidth(s * 0.038);
        minute.setStrokeLineCap(StrokeLineCap.ROUND);

        art.getChildren().addAll(bellLeft, bellRight, legLeft, legRight, face, hour, minute);
    }

    /**
     * Releases the ticker.
     *
     * <p>⚠ A {@code Pulse} subscription outlives the node that made it, and this panel rebuilds its
     * sections constantly — {@code CycleGrid.dispose} and {@code CoreCage.dispose} were written,
     * correct, and called by nobody, and every open of the rig monitor leaked one.
     */
    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
    }
}
