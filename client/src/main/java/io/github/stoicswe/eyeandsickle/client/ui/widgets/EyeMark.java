package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import java.util.Random;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/**
 * The Eye — the mark that labels the personal-heat readout on the top strip. It watches.
 *
 * <h2>⚠ DRAWN, NEVER A GLYPH, and this is not a preference</h2>
 *
 * {@code U+1F441} is in neither bundled face, and {@code GlyphCoverageTest} scans <b>source</b> for
 * literals and fails the build on an uncovered one — it has already rejected {@code U+26A0} in two
 * places and four block elements in the file manager. A glyph here would not degrade politely; it
 * would render as somebody else's font at somebody else's advance width, differently on each
 * platform, in a strip whose cells are laid out on character widths. Same decision {@code MailMark},
 * {@code SecurityMark} and {@code SectionMark} record. It is also what makes the animation below
 * possible at all: a glyph has one shape and this one has to open and close.
 *
 * <h2>⚠ ONE MARK FOR ONE READOUT, which is what keeps §9's icon-set ban intact</h2>
 *
 * {@code docs/design/ui-design-language.md} §9 makes <b>icon sets</b> build-blocking. What that
 * forbids is a <em>vocabulary</em> — a general-purpose tray of symbols that ends up standing in for
 * words across the interface. This is a single shape for a single readout. ⚠ It is also the fifth
 * drawn mark in this package, which is the point at which the question stops being rhetorical: the
 * test is whether any of them is reused for a second subject. None is, and none may be — an eye
 * means <b>this</b> readout, not "surveillance" wherever surveillance is mentioned.
 *
 * <h2>Why an eye is the right mark for heat specifically</h2>
 *
 * Personal heat is not a temperature; it is <b>how much attention the Eye is paying to you</b>. The
 * faction is called The Eye, and its own emblem is the one symbol in this game that means exactly
 * what the readout measures. That is what makes this a label rather than decoration — a flame or a
 * thermometer icon would restate the widget below it, which already looks like a thermometer.
 *
 * <h2>⚠ STROKED, AND DRAWN AT SIZE rather than authored large and scaled</h2>
 *
 * An outline is the deck's own idiom (§2.3 — depth from brightness, drawn not filled), and the
 * geometry is computed from the requested size instead of being drawn in a box and scaled down,
 * because <b>a scale transform scales the stroke with it</b>: at the ~11px this renders at, a 1px
 * hairline authored in a 24px box arrives at well under half a pixel and greys into a smudge.
 * {@code MailMark} records the same trap, and the ring wallpaper records it from the other side.
 *
 * <p>⚠ It is also why the <b>blink redraws the path</b> rather than scaling the node vertically. A
 * {@code scaleY} would squash the stroke along with the lids, so a half-closed eye would be drawn in
 * a thinner line than an open one — the mark would appear to fade as it blinks.
 *
 * <h2>⚠ The pupil is a SECOND node, and that is why there are two style classes</h2>
 *
 * {@code MailMark} puts its flap in the same {@code SVGPath} as its body so the whole mark takes one
 * paint. That is not available here: the lens is <b>stroked with no fill</b> and the pupil is
 * <b>filled</b>, so one node cannot express both. The two classes resolve the same token, and the
 * hover rules in {@code theme.css} name both — a rule that moved one and not the other would leave a
 * pupil floating in a lens of a different colour.
 *
 * <h2>Motion: it looks around slowly, and blinks rarely</h2>
 *
 * Both are decoration in the strict sense {@link Pulse} means it, so both ride {@link Pulse#animate}
 * and <b>stop dead under Reduce motion</b> (WCAG 2.2.2) — see {@link #tick()} for the trap that
 * hides in "stop dead". Nothing about the readout is carried by the movement: the heat, the band and
 * its consequence are the thermometer's, and a player who never sees this mark move has lost
 * nothing. That is the test for whether a flourish is allowed to be suppressible, and this passes it.
 *
 * <p>⚠ <b>No {@code Timeline}, no {@code AnimationTimer}, no interpolation.</b> §5 permits no easing
 * anywhere and {@code UiContractTest} rations {@code AnimationTimer} to two files by name; §7.3 wants
 * one shared driver rather than a timer per widget. So this counts ticks of the shared {@link Pulse}
 * exactly as {@code DiskLamp} does, and every position it takes is one it computed for that frame.
 */
public final class EyeMark extends StackPane {

    /** Height as a fraction of the width. An eye is an almond; at 1.0 this is a circle in a lens. */
    private static final double ASPECT = 0.60;

    /**
     * The pupil's RADIUS as a fraction of the mark's height.
     *
     * <h2>⚠ It looks far too small written down, and 0.26 was measured and rejected</h2>
     *
     * The interior is not the mark's height — it is the height less a stroke at the top and another
     * at the bottom, and less the room the lids need to still read as lids. At the ~11px this draws
     * at, 0.26 gave a 5.7px pupil in an 8.6px interior: the ink met the lids top and bottom and the
     * whole mark rendered as a filled blob. Found by rendering and zooming; at strip size it read as
     * a flying saucer.
     */
    private static final double PUPIL = 0.17;

    /**
     * How far the pupil travels each way, as a fraction of the room it has.
     *
     * <p>⚠ Measured against the room, not against the mark's width, so it cannot walk into the lid: a
     * lens is thinnest at its corners, and a gaze expressed as a fraction of the <em>width</em> would
     * be safe at this size and clip the moment somebody asked for a taller eye.
     */
    private static final double GAZE_TRAVEL = 0.55;

    /**
     * One full look — left, right and back — in {@link Pulse} ticks. 120 × 100ms is twelve seconds.
     *
     * <p>⚠ "Very slowly" is the requirement, and slow motion is where a stepped animation is most
     * likely to read as a stutter. It does not here, because the travel is a couple of pixels: the
     * pupil moves well under a pixel per tick, so the steps are smaller than the thing being moved.
     * <b>Speeding this up is the change that would expose the steps</b>, not slowing it down.
     */
    static final int GAZE_PERIOD_TICKS = 120;

    /**
     * How far past the ends the linear sweep runs before it is clamped.
     *
     * <h2>⚠ THIS IS HOW THE EYE GETS ITS DWELL, and it is NOT an easing curve</h2>
     *
     * A pure triangle wave reverses the instant it arrives, which reads as a pupil batted between two
     * walls rather than as something looking. What it wants is to <em>arrive, hold, and go back</em>.
     * The obvious way to get that shape is to ease the ends, and §5 permits no easing anywhere —
     * {@code RingField} records the same refusal ("a triangle envelope, never a sine"). So the sweep
     * stays perfectly linear and simply overshoots: at 1.6 the pupil reaches the end at 62% of each
     * half-cycle and rests there for the other 38%. Piecewise linear, no curve, and the flats are
     * arithmetic rather than a tween.
     */
    static final double GAZE_OVERSHOOT = 1.6;

    /**
     * The blink, one entry per tick, as how open the eye is.
     *
     * <p>⚠ <b>A table, not a function</b> — {@code SyncSpin}'s rule, for its reason: a formula for
     * "how open is an eye part way through a blink" is an easing function in the source whatever it
     * is called, and the next person to need one would import it. Three ticks is 300ms, which is at
     * the slow end of a real blink (100–400ms) and is the fastest this can be: {@link Pulse}
     * quantises every subscription to a multiple of its 100ms driver, so asking for a crisper blink
     * would silently round to this anyway — and reaching for a private clock to get it would mean a
     * second driver on the deck for one widget's flourish.
     */
    static final double[] BLINK = {0.55, 0.0, 0.55};

    /**
     * The chance, per tick, that the eye blinks. 0.002 at ten ticks a second is about once a minute.
     *
     * <p>⚠ <b>Rare on purpose, and the rarity is the feature.</b> A person blinks every few seconds;
     * an eye that did would be a spinner, and the strip's discipline is that it is quiet. This is
     * meant to be caught out of the corner of the eye once in a while and never to demand attention.
     * {@code EyeMarkTest} asserts the mean interval rather than the constant, so a change to it fails
     * against the property somebody actually cares about.
     */
    static final double BLINK_CHANCE_PER_TICK = 0.002;

    /**
     * ⚠ SEEDED, AND NEITHER {@code Math.random()} NOR THE GAME'S {@code Rng}. Three separate reasons,
     * and the third is the one that matters.
     *
     * <ul>
     *   <li><b>Not {@code Math.random()}</b>, because two renders of the same deck would then differ
     *       for no reason anybody could reproduce — {@code RingField} seeds its glitch for exactly
     *       this, and {@code DiskLamp} goes further and uses a fixed pattern.
     *   <li><b>Deterministic across players</b>, so a bug report about the mark describes something
     *       another person can see.
     *   <li><b>⚠ NEVER {@code engine/breach/Rng}.</b> That stream is committed to the save, so a draw
     *       taken from it here would shift every later draw — a decorative blink would silently
     *       change which puzzle a breach generates. Decoration must not touch the game's randomness,
     *       in either direction.
     * </ul>
     */
    private final Random random = new Random(BLINK_SEED);

    /** The date this was written. Any fixed value does; a recognisable one says it was chosen. */
    private static final long BLINK_SEED = 20_260_808L;

    private final SVGPath lens = new SVGPath();
    private final Circle pupil = new Circle();

    private final double w;
    private final double h;
    private final double stroke;
    private final double pupilRadius;
    private final double gazeRange;

    private Look look = Look.REST;
    private double painted = -1;

    /**
     * An eye {@code size} wide, in a frame that reserves exactly that much room.
     *
     * <p>⚠ Inset by half the stroke on every side. A path drawn on the frame's own edge is clipped
     * along its outer half, which reads as a lighter line on two sides — a rendering fault rather
     * than a thin shape.
     *
     * <p>⚠ <b>The caller owns the words.</b> A mark alone cannot name a readout: a screen reader
     * cannot see an eye, and neither can a player who has not met this one before. The frame is
     * deliberately silent to assistive technology ({@code accessibleText} cleared) because the node
     * it labels — {@code ThermoMeter} — already announces the full sentence, and an unlabelled
     * graphic announced ahead of it is noise. What the caller must supply is a <b>tooltip</b>, since
     * with the word removed there is otherwise nothing on screen that says what the meter measures.
     *
     * <p>⚠ It subscribes to {@link Pulse} here and never unsubscribes, which is right for <em>this</em>
     * widget and would be a leak in most: the strip is built once and lives as long as the deck, so
     * there is no second instance to accumulate. {@code DiskLamp} is the same shape for the same
     * reason. A tool-window widget must not copy it — {@code CycleGrid} and {@code CoreCage} both
     * needed a real {@code dispose}, and leaked a subscription per open until they got one.
     */
    public EyeMark(double size, double strokeWidth) {
        this.w = size;
        this.h = size * ASPECT;
        this.stroke = strokeWidth;
        this.pupilRadius = h * PUPIL;
        // The room the pupil's CENTRE has: half the width, less its own radius, less the stroke it
        // must not touch. Then the fraction of that room a look actually uses.
        this.gazeRange = (w / 2 - pupilRadius - strokeWidth) * GAZE_TRAVEL;

        lens.setStrokeWidth(strokeWidth);
        lens.getStyleClass().add("es-eye-mark");
        pupil.getStyleClass().add("es-eye-mark-pupil");

        getChildren().addAll(lens, pupil);
        getStyleClass().add("es-eye");
        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);
        // ⚠ Decoration to a reader — the meter beside it carries the sentence. Left announceable,
        // this is an unlabelled graphic read out before the readout's own name. SocialMark's rule.
        setAccessibleText("");

        paint();
        // ⚠ animate, not every: this is decoration in the strict sense, so Reduce motion must stop it.
        Pulse.shared().animate(Pulse.tickMs(), this::tick);
    }

    /**
     * One tick: advance the look, then paint whatever it says.
     *
     * <h2>⚠ REDUCE MOTION RESETS TO REST — it does not freeze, and freezing is a real bug</h2>
     *
     * {@link Pulse#setReducedMotion} fires every decorative subscription <b>once</b> when it is
     * switched on, so that a widget caught mid-animation paints its finished state before it stops.
     * Without the branch below, a player who turned Reduce motion on during the 200ms the eye is shut
     * would get a <b>permanently closed eye</b> — the accessibility setting leaving the interface in
     * the one state the animation is never supposed to rest in. Exactly the shape of the carousel's
     * defect, which also landed only on the Reduce-motion path.
     *
     * <p>⚠ {@code Pulse.animate} also invokes its action <b>once immediately</b>, which is a trap for
     * an action that advances rather than paints. It is harmless here only because the resting phase
     * is the middle of the sweep: one step off centre is a fraction of a pixel, so a synchronous
     * render — where that immediate call is the only one that ever runs — photographs an eye looking
     * straight ahead rather than one parked at an extreme.
     */
    private void tick() {
        if (Pulse.shared().reducedMotion()) {
            look = Look.REST;
            paint();
            return;
        }
        look = look.next(random.nextDouble() < BLINK_CHANCE_PER_TICK);
        paint();
    }

    /**
     * Steps the animation by hand, ignoring Reduce motion.
     *
     * <h2>⚠ EXISTS FOR THE RENDER HARNESS AND FOR TESTS. Nothing in the client calls it.</h2>
     *
     * A synchronous {@code Scene.snapshot} runs no {@link Pulse} tick, and every render harness here
     * sets Reduce motion — so a screenshot of the deck shows the eye at rest, which is the one state
     * indistinguishable from the animation being absent. {@code DeskManager.frostNow()} exists for
     * the same reason and the ring wallpaper's {@code -Ddeck.glitchPhase} records the same trap.
     *
     * <p>⚠ It drives the <b>real</b> state machine — the same {@link Look#next} and the same
     * {@link #paint()} the live path uses — rather than posing the nodes directly. A harness that set
     * the lids itself would agree with itself and prove nothing.
     *
     * @param ticks how many ticks to run
     * @param blinkNow whether the first of them starts a blink, rather than waiting on the odds
     */
    public void wind(int ticks, boolean blinkNow) {
        for (int i = 0; i < ticks; i++) {
            look = look.next(blinkNow && i == 0);
        }
        paint();
    }

    /**
     * Draws the current look.
     *
     * <p>⚠ The path is rebuilt only when the <b>opening</b> changes, which is during a blink and
     * never otherwise. Gaze is a translate, so the resting case — which is almost all of them — costs
     * one field assignment per tick rather than re-parsing a path string ten times a second forever.
     */
    private void paint() {
        double open = look.open();
        if (open != painted) {
            painted = open;
            double half = stroke / 2;
            double cy = h / 2;
            // How far each lid rises off the centre line. At open == 0 both sit ON it and the lens is
            // a closed eye: a horizontal stroke, which is exactly right and needs no special case.
            double rise = (cy - half) * open;
            // ⚠ THE CONTROL POINTS SIT OUTSIDE THE FRAME, AND THEY HAVE TO. A quadratic passes
            // nowhere near its control point: its midpoint is (P0 + 2C + P2) / 4, so a lid controlled
            // from the frame's own edge peaks only a QUARTER of the way up and the mark comes out a
            // flat slit with a dot in it — which is exactly what the first render showed. Solving
            // that expression for the control that puts the peak ON the edge gives 2·peak − cy. The
            // curve still draws entirely inside the frame; only the control is outside, and JavaFX
            // bounds a Shape by its ink rather than by its controls, so the StackPane still centres
            // on what is drawn.
            double topLid = 2 * (cy - rise) - cy;
            double bottomLid = 2 * (cy + rise) - cy;
            lens.setContent(String.format(
                    java.util.Locale.ROOT,
                    "M %.2f %.2f Q %.2f %.2f %.2f %.2f Q %.2f %.2f %.2f %.2f Z",
                    half, cy, w / 2, topLid, w - half, cy, w / 2, bottomLid, half, cy));
            // ⚠ The pupil closes WITH the lids. A pupil at full size behind a shut eye is a dot
            // sitting on the closed line, which reads as a fault rather than a blink; at zero it is
            // simply not drawn, which is what a closed eye looks like.
            pupil.setRadius(pupilRadius * open);
        }
        pupil.setTranslateX(look.gaze() * gazeRange);
    }

    /**
     * Where the eye is looking and how open it is, as a value.
     *
     * <h2>⚠ Extracted so it can be tested at all</h2>
     *
     * A twelve-second sweep and a once-a-minute blink are things a screenshot cannot catch and
     * staring at the deck cannot confirm. "Does the gaze reach both ends", "does it rest there rather
     * than bouncing", "does a blink last exactly three ticks", "can a blink retrigger itself and hold
     * the eye shut" are questions only a headless test answers. Same seam {@code DiskLamp.Flicker},
     * {@code SyncSpin.advance} and {@code SecurityCenterView.latestOf} exist for.
     *
     * <p>⚠ The <b>decision</b> to blink is a parameter rather than a draw taken in here, which is
     * what keeps this pure: the widget owns the randomness, and a test can ask what a blink does
     * without owning a seed.
     *
     * @param phase where in the sweep, already reduced modulo the period
     * @param blink ticks of blink left; {@code 0} is an open eye
     */
    record Look(int phase, int blink) {

        /**
         * ⚠ A QUARTER of the way through the sweep, which is where the gaze crosses centre.
         *
         * <p>This is the pose Reduce motion holds and the pose a render captures, so it has to be the
         * one that reads as an eye rather than one caught mid-look. Phase 0 is the far left.
         */
        static final Look REST = new Look(GAZE_PERIOD_TICKS / 4, 0);

        /**
         * ⚠ A BLINK CANNOT RESTART ITSELF, and this is the opposite of {@code DiskLamp}, deliberately.
         * There, a write mid-burst restarts the flicker, because the lamp's job is to report that
         * something just happened. Here a retrigger would extend the closed frames — and with a draw
         * every tick, an eye that could re-blink while blinking would occasionally hold shut for an
         * unbounded stretch, which does not read as a blink at all.
         */
        Look next(boolean wantsBlink) {
            int nextBlink = blink > 0 ? blink - 1 : (wantsBlink ? BLINK.length : 0);
            // ⚠ Reduced modulo the period rather than counted upward: a monotonic tick counter on a
            // client somebody leaves running overflows an int in a few years, and the phase is the
            // only thing anybody needs.
            return new Look((phase + 1) % GAZE_PERIOD_TICKS, nextBlink);
        }

        /** Where the pupil is looking: {@code -1} hard left, {@code 0} centre, {@code 1} hard right. */
        double gaze() {
            int half = GAZE_PERIOD_TICKS / 2;
            // A triangle: up over the first half of the period, back down over the second.
            double up = phase < half ? phase : GAZE_PERIOD_TICKS - phase;
            double signed = up / half * 2 - 1;
            return Math.max(-1, Math.min(1, signed * GAZE_OVERSHOOT));
        }

        /** How open the eye is: {@code 1} wide, {@code 0} shut. */
        double open() {
            return blink == 0 ? 1 : BLINK[BLINK.length - blink];
        }
    }
}
