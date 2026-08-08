package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.Node;

/**
 * What a control does when the pointer arrives: a brief signal tear, and an outline while it stays.
 *
 * <h2>Two layers, and the lower one is the whole affordance</h2>
 *
 * <ul>
 *   <li><b>The outline</b> — {@code .es-hovered} in {@code theme.css}, pure CSS, no motion. It
 *       brightens the control's border and its text and it is on for as long as the pointer is. This
 *       is the layer that answers "can I click this", and it is the ONLY layer under Reduce motion.
 *   <li><b>The tear</b> — a few frames of horizontal displacement as the pointer lands, settling to
 *       nothing. Decoration, and suppressible by definition.
 * </ul>
 *
 * <p>⚠ <b>The split is not a nicety — it is what makes the motion safe to remove.</b> The test this
 * repo applies to any flourish is: <em>if it stopped forever, would the player still know what it
 * says?</em> An outline that is present the whole time a control is hovered passes it outright. A
 * glitch that carried the meaning would not, and would put the affordance behind an accessibility
 * setting.
 *
 * <h2>⚠ ONE SUBSCRIPTION FOR THE WHOLE APPLICATION, because one control is hovered at a time</h2>
 *
 * §7.3 wants a single shared driver rather than a timer per widget, and this is the strongest case
 * for it in the client: there are several hundred clickable nodes and a pointer can only be over one.
 * So there is one {@link Pulse} subscription, taken lazily on the first install, driving whichever
 * node is currently under the pointer. Installing on a node costs two event handlers and nothing
 * else — no per-node subscription, no per-node state, nothing to dispose.
 *
 * <h2>⚠ A TABLE, NOT A FUNCTION</h2>
 *
 * {@code SyncSpin}'s rule, for its reason: §5 permits no easing anywhere and
 * {@code UiContractTest} rations {@code AnimationTimer} to two files by name. A formula for "how far
 * has the tear travelled" is an easing function in the source however it is spelled, and the next
 * person needing one would import it. These are hand-authored offsets walked on {@code Pulse}, so
 * neither contract test is in play — nothing interpolates and no timer is created.
 *
 * <h2>⚠ A TRANSIENT, NOT A LOOP</h2>
 *
 * The tear runs once as the pointer lands and then rests at zero for as long as the control stays
 * hovered. A control that jittered continuously would be a control demanding attention it has not
 * earned — on a deck where a dozen of them can be on screen at once, and where §2.1's whole
 * discipline is that the interface is quiet. It reads as the control noticing the pointer, which is
 * the sentence a hover state is supposed to say.
 */
public final class HoverGlitch {

    /** Applied on install, so a stylesheet can style every control the same way. */
    public static final String HOVERABLE = "es-hoverable";

    /** Applied while the pointer is over the control. The outline hangs off this. */
    public static final String HOVERED = "es-hovered";

    /**
     * The tear, one entry per tick, in pixels of horizontal displacement.
     *
     * <p>⚠ It <b>ends at zero</b> and the last entry is not optional: the offset is left on the node
     * between ticks, so a table that stopped anywhere else would park every control it had ever
     * touched a pixel off its own layout — permanently, and only for controls the player had happened
     * to hover.
     *
     * <p>Authored to read as a signal losing lock and recovering: a sharp throw, a smaller counter,
     * then settled. Under two hundred milliseconds in total.
     */
    private static final double[] TEAR = {-2, 3, -1, 1, 0};

    /**
     * Which frames of {@link #TEAR} also drop the outline, so the control flickers rather than slides.
     *
     * <p>⚠ Displacement alone reads as a wobble. What makes it read as a <em>glitch</em> is the
     * outline cutting out on the frames the shape is furthest from where it belongs — the same
     * structural trick {@code RingField} uses, where the datamosh is sliced geometry rather than a
     * filter because §9 makes blur build-blocking.
     */
    private static final boolean[] CUT = {true, true, false, false, false};

    /**
     * ⚠ DECLARED AFTER THE TABLES, and it has to be.
     *
     * <p>Static initialisers run in declaration order, and the instance initialiser below reads
     * {@link #TEAR}{@code .length}. With this line at the top of the class — which is where a
     * singleton conventionally goes, and where it was written — {@code new HoverGlitch()} ran while
     * {@code TEAR} was still {@code null}, and the class failed to initialise with an
     * {@code ExceptionInInitializerError} whose message says only "NullPointerException". Every test
     * in the file reported {@code NoClassDefFoundError} at its own constructor, which points at the
     * wrong file entirely.
     */
    private static final HoverGlitch INSTANCE = new HoverGlitch();

    private Node active;
    private int step = TEAR.length;
    private AutoCloseable driver;

    private HoverGlitch() {}

    public static HoverGlitch shared() {
        return INSTANCE;
    }

    /**
     * Gives a control the hover response.
     *
     * <p>Idempotent by style class, because {@code Cursors.clickable} is called from the subtree
     * walker as well as from individual call sites and a node can legitimately be reached twice.
     */
    public void install(Node node) {
        if (node == null || node.getStyleClass().contains(HOVERABLE)) {
            return;
        }
        node.getStyleClass().add(HOVERABLE);
        start();

        node.setOnMouseEntered(event -> {
            // ⚠ The previous control is reset explicitly. A pointer moved fast between two adjacent
            // buttons can deliver the second enter before the first exit, which would otherwise leave
            // the first parked at whatever offset the tear had reached.
            release();
            active = node;
            step = 0;
            node.getStyleClass().add(HOVERED);
        });
        node.setOnMouseExited(event -> {
            if (active == node) {
                release();
            } else {
                node.getStyleClass().remove(HOVERED);
            }
        });
    }

    /** One frame of the tear. Rests once the table is spent, and does nothing under Reduce motion. */
    private void tick() {
        Node node = active;
        if (node == null) {
            return;
        }
        // ⚠ Asked EVERY tick rather than at subscribe time, because the setting can be turned on
        // while a control is hovered — and if it is, the node has to be put back where it belongs
        // rather than left wherever the tear had reached. Pulse stops calling this immediately
        // afterwards, so this is the only chance to tidy up.
        if (Pulse.shared().reducedMotion()) {
            node.setTranslateX(0);
            node.getStyleClass().remove("es-hover-cut");
            step = TEAR.length;
            return;
        }
        if (step >= TEAR.length) {
            return;
        }
        node.setTranslateX(TEAR[step] * UiTokens.HOVER_TEAR_SCALE);
        node.getStyleClass().remove("es-hover-cut");
        if (CUT[step]) {
            node.getStyleClass().add("es-hover-cut");
        }
        step++;
        if (step >= TEAR.length) {
            node.setTranslateX(0);
            node.getStyleClass().remove("es-hover-cut");
        }
    }

    /** Puts the active control back exactly as it was. */
    private void release() {
        if (active == null) {
            return;
        }
        active.setTranslateX(0);
        active.getStyleClass().removeAll(HOVERED, "es-hover-cut");
        active = null;
        step = TEAR.length;
    }

    private void start() {
        if (driver != null) {
            return;
        }
        try {
            // ⚠ animate, not every: this is decoration in the strict sense, so Reduce motion stops it
            // and the outline carries the affordance alone. `every` here would be an accessibility
            // setting that does nothing, which is worse than one that does too much.
            driver = Pulse.shared().animate(Pulse.tickMs(), this::tick);
        } catch (RuntimeException | Error noToolkit) {
            // ⚠ A CONTROL MUST STILL BE A CONTROL WITHOUT A CLOCK. `Pulse`'s constructor builds a
            // Timeline, which throws "Toolkit not initialized" wherever there is no toolkit — a
            // headless test, and anything that builds nodes before the FX thread is up. Swallowing it
            // costs the tear and keeps the OUTLINE, which is the layer that carries the affordance,
            // because that layer is CSS and needs no clock at all. The same call `Cursors.build`
            // makes for the same reason: cosmetics never stop the client from starting.
            driver = null;
        }
    }

    /**
     * Lights a control as if the pointer were on it.
     *
     * <h2>⚠ FOR TESTS AND THE RENDER HARNESS. Nothing in the client calls it.</h2>
     *
     * A hover state cannot be photographed otherwise: {@code Scene.snapshot} has no pointer, every
     * harness here sets Reduce motion, and no {@code Pulse} frame fires in a synchronous render. All
     * three independently produce the resting frame — the one state indistinguishable from the
     * feature being absent, which is this repo's most-recorded way of reporting a render as a pass.
     * It drives the REAL state machine, so a harness cannot agree with itself.
     */
    public void hover(Node node) {
        release();
        active = node;
        step = 0;
        node.getStyleClass().add(HOVERED);
    }

    /** Test seam — one frame of the tear. See {@link #hover}. */
    public void advance() {
        tick();
    }

    /** Test seam — see {@link #hover}. */
    public void leave() {
        release();
    }

    /** Test seam — how far into the tear the active control is. */
    int stepIndex() {
        return step;
    }

    /** How many frames the tear runs for. */
    static int tearFrames() {
        return TEAR.length;
    }
}
