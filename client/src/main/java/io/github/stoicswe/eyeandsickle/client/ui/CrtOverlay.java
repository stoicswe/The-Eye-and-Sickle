package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import javafx.geometry.Bounds;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * The screen artefact layer: scanlines, chromatic aberration and VHS-style signal glitch.
 *
 * <h2>This exists because §9 changed, not because it was worked around</h2>
 *
 * {@code docs/design/ui-design-language.md} §9 listed "CRT scanlines, vignette, bezel, chromatic
 * aberration" as build-blocking and said <em>do not reintroduce</em>. That list was amended on
 * 2026-07-26 on explicit direction: <b>scanlines, chromatic aberration and light VHS glitch are now
 * permitted as optional, player-toggleable effects. Bezel and vignette remain cut.</b> The
 * distinction §9 now draws is the one that matters — an effect the player switches on is a costume;
 * an effect welded to the interface is a lie about what the interface can show. Everything here is
 * therefore <b>off by default</b> and reachable from Settings and from the {@code crt} command.
 *
 * <h2>What "chromatic aberration" honestly means here</h2>
 *
 * ⚠ <b>This is not full-scene aberration, and it cannot be.</b> Real CA offsets the red and blue
 * channels of the composited frame, which in JavaFX would mean snapshotting the whole scene every
 * frame and recompositing it three times — at deck size that is tens of milliseconds per frame for
 * an effect nobody asked to pay for. There are no pixel shaders available to us.
 *
 * <p>So aberration is applied where it is both affordable and actually visible:
 *
 * <ul>
 *   <li><b>The wallpaper</b> ({@code widgets/Substrate}) draws its text three times — red, cyan and
 *       the base colour — at one-pixel offsets. That is genuine per-channel separation on real
 *       glyphs, because the layer is text and duplicating it is three labels rather than a raster.
 *   <li><b>The glitch slivers</b> below carry a red/cyan fringe, which is where a real tape or
 *       convergence artefact shows colour bleed anyway.
 * </ul>
 *
 * <p>Anyone expecting a fringe on every character of the terminal will not get one. Saying so here
 * is cheaper than the bug report.
 *
 * <h2>Constraints this layer works inside</h2>
 *
 * <ul>
 *   <li><b>No blur.</b> §9 still cuts blur and glow, and {@code UiContractTest} fails the build on
 *       {@code dropshadow(}, {@code gaussian} or {@code innershadow(} in the stylesheet. Every
 *       artefact here is a hard-edged gradient or a flat band — which is also what a real scanline
 *       is, so nothing is lost.
 *   <li><b>Gradients spell differently in JavaFX.</b> {@code repeating-linear-gradient} does not
 *       exist and fails <em>silently at runtime</em> as an unknown function; JavaFX spells it
 *       {@code linear-gradient(from … to …, repeat, …)}. That trap has already cost this project a
 *       debugging round once, recorded in {@code docs/design/15-open-questions.md} §3.
 *   <li><b>Mouse-transparent, all of it.</b> An overlay above the whole deck that accepted a click
 *       would eat every click in the client. Set on this node and on each layer.
 *   <li><b>Reduced motion stops every moving part and keeps every still one.</b> Both tickers go
 *       through {@link Pulse#animate}, which classifies them as decoration — so under
 *       {@code prefers-reduced-motion} the scanlines stay drawn but stop drifting, the refresh bar
 *       halts, and the glitch stops firing. Aberration never moved. §5's non-optional rule is
 *       satisfied without a single branch on it here.
 * </ul>
 */
public final class CrtOverlay extends Pane {

    /**
     * The glitch tick.
     *
     * <p>Fast, and that is the whole correction. This ran at 700ms with each event held for two
     * ticks — so a "glitch" sat on screen for 1.4 seconds, which reads as a rendering fault rather
     * than as tape damage. A VHS tear is a <em>snap</em>: a few frames of displacement and gone.
     */
    private static final double GLITCH_TICK_MS = 90;

    /** A burst re-randomises every tick, so it stutters instead of holding one displaced pose. */
    private static final int BURST_MIN_TICKS = 3;

    private static final int BURST_MAX_TICKS = 8;

    /**
     * Quiet ticks between bursts.
     *
     * <p>Long, and unevenly so. Intermittency is what makes an artefact read as damage — something
     * that fires on a regular beat reads as a feature of the interface, and something that fires
     * constantly stops being noticed at all within a minute.
     */
    private static final int QUIET_MIN_TICKS = 38;

    private static final int QUIET_MAX_TICKS = 140;

    /** How many elements one burst-frame displaces. */
    private static final int SLIVERS_MIN = 2;

    private static final int SLIVERS_MAX = 6;

    /** How far an element is jogged sideways, in whole pixels. */
    private static final int JOG_MAX = 9;

    /** A sliver's thickness across the edge it sits on. */
    private static final double SLIVER_THICKNESS = 2;

    /** How far along an edge a sliver runs, before it is clipped to the edge's own length. */
    private static final double RUN_MIN = 18;

    private static final double RUN_MAX = 120;

    /** Horizontal displacement. Whole pixels, small, and always sideways — see {@link #spawnBand}. */
    private static final int SHIFT_MAX = 6;

    /**
     * The scanline pattern's period, in pixels.
     *
     * <p>⚠ <b>This number also lives in {@code theme.css}</b>, as the {@code to 0px 4px} in the
     * gradient — JavaFX has no way to look a size up from CSS (§7.2: looked-up values are colours
     * only), so the roll cannot read the pattern it is rolling. If the two disagree the drift stops
     * wrapping cleanly and the lines visibly jump once per cycle. {@code ScreenArtefactTest} asserts
     * they match, which is the only defence available.
     */
    private static final int SCAN_PERIOD = 4;

    /** The animation tick. Also the roll bar's step interval. */
    private static final double SCAN_TICK_MS = 100;

    /**
     * Ticks between one-pixel drifts of the line pattern.
     *
     * <p>Deliberately slow. On a real CRT this drift is the beat between the tube's refresh and the
     * camera's shutter, and it is nearly still; fast drift over body text is a shimmer that is
     * genuinely tiring to read through, which would undo the reason §9.1 permits scanlines at all.
     * At this rate the pattern takes about two seconds to travel one full period.
     */
    private static final int SCAN_DRIFT_EVERY = 5;

    /** How far the refresh bar travels per tick. ~7 seconds for a full pass at deck height. */
    private static final double ROLL_STEP = 13;

    /** The refresh bar's height, as a fraction of the deck. */
    private static final double ROLL_HEIGHT_FRACTION = 0.22;

    /**
     * How far the aberration bands reach in from each edge at full curvature, as a fraction of the
     * deck's shorter side. Generous, because the gradient inside them does the real falloff — the
     * band is only the envelope.
     */
    private static final double EDGE_REACH_FRACTION = 0.30;

    private final Region scanlines = new Region();
    private final Region roll = new Region();

    /**
     * The four radial-aberration bands, one per screen edge.
     *
     * <h2>Why four flat bands add up to a radial effect</h2>
     *
     * Real tube and lens aberration is <b>radial</b>: zero at the optical centre, worst at the rim,
     * worst of all in the corners. A single gradient cannot express two-axis falloff, but four can —
     * each band fades inward from its own edge, so a corner is covered by two of them at once and is
     * therefore the strongest fringe on screen, with the edge midpoints weaker and the centre
     * untouched. That is the correct profile, reached with four Regions and no per-frame work.
     *
     * <p>Left and top carry the warm channel, right and bottom the cool one, so the separation
     * reverses across the screen the way a real convergence error does rather than tinting the whole
     * border one colour.
     */
    private final Region edgeLeft = new Region();

    private final Region edgeRight = new Region();

    private final Region edgeTop = new Region();

    private final Region edgeBottom = new Region();

    /** 0–1. Scales both the aberration bands' reach and their strength. */
    private double curvature;

    private final Pane glitch = new Pane();
    private final Random random = new Random();

    private int scanTick;
    private int scanDrift;
    private double rollY = Double.NaN;
    private AutoCloseable scanTicker;

    /**
     * Where the edges are, in this overlay's own coordinates.
     *
     * <p>Supplied rather than discovered, because the overlay sits above the whole deck and has no
     * business walking the window manager's scene graph. {@code DeckShell} owns that walk — it is
     * the only object that knows which windows are open and what is inside them.
     */
    private Supplier<List<javafx.scene.Node>> edgeSource = List::of;

    /**
     * Elements currently jogged out of place, and what their translation was before.
     *
     * <p>⚠ The previous value is stored rather than assumed to be zero. This layer mutates nodes it
     * does not own, and restoring a hard 0 would silently destroy any translation another part of
     * the client had set — a decorative effect that quietly breaks a real animation is far worse
     * than one that does not run.
     */
    private final Map<javafx.scene.Node, Double> displaced = new LinkedHashMap<>();

    private boolean glitchEnabled;
    private int burstLeft;
    private int quietLeft;
    private AutoCloseable ticker;

    public CrtOverlay() {
        getStyleClass().add("es-crt");
        setMouseTransparent(true);
        setPickOnBounds(false);

        scanlines.getStyleClass().add("es-crt-scanlines");
        scanlines.setMouseTransparent(true);
        scanlines.setManaged(false);
        scanlines.setVisible(false);

        roll.getStyleClass().add("es-crt-roll");
        roll.setMouseTransparent(true);
        roll.setManaged(false);
        roll.setVisible(false);

        edgeLeft.getStyleClass().add("es-crt-edge-left");
        edgeRight.getStyleClass().add("es-crt-edge-right");
        edgeTop.getStyleClass().add("es-crt-edge-top");
        edgeBottom.getStyleClass().add("es-crt-edge-bottom");
        for (Region edge : List.of(edgeLeft, edgeRight, edgeTop, edgeBottom)) {
            edge.setMouseTransparent(true);
            edge.setManaged(false);
            edge.setVisible(false);
        }

        glitch.setMouseTransparent(true);
        glitch.setManaged(false);

        getChildren().addAll(scanlines, roll, edgeLeft, edgeRight, edgeTop, edgeBottom, glitch);
    }

    /**
     * Scanlines on or off — the lines themselves, their drift, and the refresh bar.
     *
     * <h2>Why the animation is folded into this one switch</h2>
     *
     * A static line pattern is a texture; what makes it read as a <em>tube</em> is the slow vertical
     * drift and the refresh bar rolling down it, which is the artefact a camera pointed at a CRT
     * actually records. Splitting them into two settings would offer a choice nobody wants — nobody
     * turns on scanlines to get a still Moiré.
     *
     * <p><b>Reduced motion is the pause</b>, and it does the right thing without a third state: the
     * ticker is registered through {@link Pulse#animate}, so under {@code prefers-reduced-motion} it
     * paints one frame and holds — leaving the lines <em>on</em> and perfectly still. That is the
     * same trade the wallpaper's STILL mode makes, reached here without another control, and it is
     * why §5's rule does not need a special case.
     */
    public void setScanlines(boolean on) {
        scanlines.setVisible(on);
        roll.setVisible(on);
        stopScanTicker();
        if (on) {
            scanTicker = Pulse.shared().animate(SCAN_TICK_MS, this::advanceScan);
        }
    }

    /**
     * One frame of tube behaviour: drift the lines, move the refresh bar.
     *
     * <p>Both are whole-pixel steps (§5 permits step timing and nothing that tweens), and the drift
     * wraps at {@link #SCAN_PERIOD} so the pattern is continuous rather than snapping back.
     */
    private void advanceScan() {
        double h = getHeight();
        if (h <= 0) {
            return;
        }
        scanTick++;
        if (scanTick % SCAN_DRIFT_EVERY == 0) {
            scanDrift = (scanDrift + 1) % SCAN_PERIOD;
            scanlines.setTranslateY(scanDrift);
        }
        double barHeight = Math.max(1, h * ROLL_HEIGHT_FRACTION);
        if (Double.isNaN(rollY)) {
            rollY = -barHeight;
        }
        rollY += ROLL_STEP;
        if (rollY > h) {
            rollY = -barHeight;
        }
        roll.resizeRelocate(0, rollY, getWidth(), barHeight);
    }

    /**
     * Signal glitch on or off.
     *
     * <p>The ticker is decorative, so reduced motion freezes it after one frame. Bands are cleared
     * on the way out rather than left frozen on screen — a tracking band that stopped mid-flicker
     * and stayed there would read as a rendering bug, which is the one thing an artefact must not.
     */
    public void setGlitch(boolean on) {
        this.glitchEnabled = on;
        stopTicker();
        restore();
        burstLeft = 0;
        quietLeft = 0;
        if (on) {
            ticker = Pulse.shared().animate(GLITCH_TICK_MS, this::advance);
        }
    }

    /**
     * Simulated tube curvature, 0–1.
     *
     * <h2>⚠ What this does not do</h2>
     *
     * <b>It does not warp the interface, and it cannot.</b> Real barrel distortion is a per-pixel
     * remap, which needs either a pixel shader — JavaFX exposes none — or rendering the whole scene
     * to a texture every frame and mapping it onto a 3D mesh. The second is not merely expensive at
     * deck size; it <em>breaks input</em>, because hit-testing would still use the undistorted
     * geometry and every click would land somewhere other than where the player sees the control.
     * A curvature setting that quietly made the UI unclickable would be a far worse outcome than one
     * that does less than its name suggests.
     *
     * <p>So this scales the part of curvature that is both visible and affordable: <b>the radial
     * chromatic aberration at the rim</b>, which is the artefact curved glass actually produces and
     * the thing that reads as "this is a tube" from across a room. Text stays straight, and the
     * Settings copy says so rather than letting the slider imply otherwise.
     */
    public void setCurvature(double amount) {
        this.curvature = Math.max(0, Math.min(1, amount));
        boolean on = this.curvature > 0;
        for (Region edge : List.of(edgeLeft, edgeRight, edgeTop, edgeBottom)) {
            edge.setVisible(on);
            // Opacity carries the strength and CSS carries the colour, which is what keeps §10
            // criterion 2 intact — there is no colour constant anywhere in this class.
            edge.setOpacity(this.curvature);
        }
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double reach = curvature * Math.min(w, h) * EDGE_REACH_FRACTION;
        edgeLeft.resizeRelocate(0, 0, reach, h);
        edgeRight.resizeRelocate(w - reach, 0, reach, h);
        edgeTop.resizeRelocate(0, 0, w, reach);
        edgeBottom.resizeRelocate(0, h - reach, w, reach);
        // Overscanned by one period at each end, so drifting the pattern down by up to SCAN_PERIOD
        // never uncovers a strip at the bottom of the screen. Cheap, and the alternative — redrawing
        // the gradient per frame — is not.
        scanlines.resizeRelocate(0, -SCAN_PERIOD, w, h + 2 * SCAN_PERIOD);
        glitch.resizeRelocate(0, 0, w, h);
        roll.resizeRelocate(0, Double.isNaN(rollY) ? -h : rollY, w, Math.max(1, h * ROLL_HEIGHT_FRACTION));
    }

    /**
     * The burst/quiet state machine.
     *
     * <p>Quiet for several seconds, then a short burst that re-randomises on every tick, then quiet
     * again for a different length of time. That shape — not the individual frame — is what reads as
     * a tape fault.
     */
    private void advance() {
        if (!glitchEnabled) {
            return;
        }
        if (burstLeft > 0) {
            burstLeft--;
            if (burstLeft == 0) {
                restore();
                quietLeft = QUIET_MIN_TICKS + random.nextInt(QUIET_MAX_TICKS - QUIET_MIN_TICKS);
            } else {
                spawnBand();
            }
            return;
        }
        if (quietLeft > 0) {
            quietLeft--;
            return;
        }
        burstLeft = BURST_MIN_TICKS + random.nextInt(BURST_MAX_TICKS - BURST_MIN_TICKS);
        spawnBand();
    }

    /** Puts every jogged element back exactly where it was. */
    private void restore() {
        for (Map.Entry<javafx.scene.Node, Double> entry : displaced.entrySet()) {
            entry.getKey().setTranslateX(entry.getValue());
        }
        displaced.clear();
        glitch.getChildren().clear();
    }

    /**
     * One frame of a burst: jog a few real elements sideways and fringe their edges.
     *
     * <h2>It displaces the picture, it does not paint over it</h2>
     *
     * The first version drew coloured slivers on top of the interface. That was the wrong model — a
     * tape or timebase fault <b>moves the image</b>, it does not add marks to it, and the difference
     * is instantly legible: painted slivers read as decoration sitting on the screen, while a row of
     * text that jumps four pixels left and back reads as the signal failing. So this sets
     * {@code translateX} on real nodes — window frames, table rows, labels, controls — which is a
     * render-time transform and therefore costs no layout pass and cannot disturb geometry.
     *
     * <p>The drawn fringes stay, but their job changed: they now sit on the edges of the elements
     * that <em>moved</em>, which is where a real convergence error shows colour.
     *
     * <p>⚠ Every displacement is recorded in {@link #displaced} and undone in {@link #restore}. This
     * is the one place the overlay writes to nodes it does not own, so leaving one behind would be a
     * permanently crooked element that no layout pass would ever correct.
     */
    private void spawnBand() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        List<javafx.scene.Node> candidates = edgeSource.get();
        if (candidates.isEmpty()) {
            return;
        }
        restore();

        int count = SLIVERS_MIN + random.nextInt(SLIVERS_MAX - SLIVERS_MIN + 1);
        for (int i = 0; i < count; i++) {
            javafx.scene.Node node = candidates.get(random.nextInt(candidates.size()));
            if (displaced.containsKey(node)) {
                continue;
            }
            int jog = (random.nextBoolean() ? 1 : -1) * (2 + random.nextInt(JOG_MAX - 1));
            displaced.put(node, node.getTranslateX());
            node.setTranslateX(node.getTranslateX() + jog);

            // Bounds read AFTER the jog, so the fringe travels with the element it belongs to.
            Bounds b = sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            if (b.getWidth() <= 1 || b.getHeight() <= 1) {
                continue;
            }
            addFringe(b, jog);
        }
    }

    /**
     * The two colour edges of a displaced element.
     *
     * <p>Placed on the leading and trailing sides of the jog — a fault that pushed the picture left
     * leaves red behind it and cyan ahead of it, because the channels do not arrive together. A
     * fringe on both sides regardless of direction would read as an outline, which is a shape rather
     * than an artefact.
     */
    private void addFringe(Bounds b, int jog) {
        double thickness = SLIVER_THICKNESS;
        Region warm = new Region();
        warm.getStyleClass().add("es-crt-fringe-warm");
        warm.setManaged(false);
        warm.resizeRelocate(jog < 0 ? b.getMaxX() : b.getMinX() - thickness, b.getMinY(), thickness, b.getHeight());

        Region cool = new Region();
        cool.getStyleClass().add("es-crt-fringe-cool");
        cool.setManaged(false);
        cool.resizeRelocate(jog < 0 ? b.getMinX() - thickness : b.getMaxX(), b.getMinY(), thickness, b.getHeight());

        // A thin lift along the top of the displaced element — the horizontal tear line itself.
        Region body = new Region();
        body.getStyleClass().add("es-crt-band");
        body.setManaged(false);
        body.resizeRelocate(b.getMinX(), b.getMinY(), b.getWidth(), Math.min(thickness, b.getHeight()));

        glitch.getChildren().addAll(body, warm, cool);
    }

    private void stopScanTicker() {
        if (scanTicker != null) {
            try {
                scanTicker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            scanTicker = null;
        }
    }

    private void stopTicker() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
    }

    public void dispose() {
        stopTicker();
        stopScanTicker();
        // ⚠ Not optional. A displaced element left behind outlives this overlay — the deck is torn
        // down and rebuilt on the way back from the main menu, and a node jogged four pixels at that
        // moment would stay crooked with nothing left running to put it back.
        restore();
    }

    /** Test seam. */
    boolean scanlinesVisible() {
        return scanlines.isVisible();
    }

    /** Test seam: the scanline period Java rolls by, which theme.css must agree with. */
    static int scanPeriod() {
        return SCAN_PERIOD;
    }

    /** Test seam: advance the tube animation without waiting on Pulse. */
    void advanceScanForTest() {
        advanceScan();
    }

    /** Test seam: how many artefact nodes are on screen right now. */
    int artefactCount() {
        return glitch.getChildren().size();
    }

    /**
     * Where the glitch may tear, in this overlay's coordinates.
     *
     * <p>Window frames and the elements inside them. An empty list means no artefact — which is the
     * correct behaviour and not a degenerate case: the effect is <em>of</em> edges, so a desk with
     * nothing on it has nothing to break up.
     */
    public void setEdgeSource(Supplier<List<javafx.scene.Node>> source) {
        this.edgeSource = source == null ? List::of : source;
    }

    /** Test seam: force an event, so the shape can be asserted without waiting on a random roll. */
    void spawnBandForTest() {
        spawnBand();
    }
}
