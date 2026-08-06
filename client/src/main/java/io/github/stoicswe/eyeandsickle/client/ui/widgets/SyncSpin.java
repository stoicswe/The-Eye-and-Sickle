package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import javafx.scene.layout.Region;

/**
 * The wind-up-and-release spin the Bluesky mark does while a sync is running.
 *
 * <h2>⚠ THIS IS A NARROW AMENDMENT TO §5, AND §9's REJECTION LIST NAMES IT BY NAME</h2>
 *
 * {@code docs/design/ui-design-language.md} §5 reads <i>"step timing only. No easing curve anywhere
 * in the product"</i> and adds that <i>"any spring, bounce, or ease-out reads as web UI immediately
 * and will undo the whole aesthetic."</i> §9's build-blocking rejection list names
 * <b>"Easing curves — spring, bounce, ease-in-out, ease-out"</b>. This widget is a spring, on
 * explicit direction (2026-08-06), and it is logged in {@code docs/design/15} §3.
 *
 * <h2>⚠ What keeps the amendment narrow — four conditions, all of which must stay true</h2>
 *
 * <ol>
 *   <li><b>No new animation machinery.</b> There is no {@code Interpolator}, no {@code Timeline},
 *       no {@code KeyValue} and no {@code AnimationTimer} — {@code UiContractTest} rations all four
 *       and none of them is touched. The motion is a <b>hand-authored table of absolute angles</b>
 *       walked one entry per {@code Pulse} tick, which is the same stepped mechanism
 *       {@code Motion.reveal}, {@code SizeReadout} and the ring wallpaper already use.
 *   <li><b>One widget, one mark.</b> It is not a shared easing utility and must not become one. The
 *       day a second caller wants it, that is the moment to ask whether §5 is being kept at all.
 *   <li><b>It only ever runs while a network sync is running</b>, and stops dead at rest. It is a
 *       progress indicator, not decoration — which is what earns it a place at all.
 *   <li><b>Reduce motion holds it still.</b> {@code Pulse.animate} never fires there, so the mark
 *       simply does not turn. Nothing is lost: the pane says "Syncing conversations…" in words.
 * </ol>
 *
 * <p>⚠ The honest reading is that the table's <em>shape</em> is an easing curve however it is
 * spelled — the ring wallpaper's note makes exactly that argument against a sine envelope. What is
 * defensible is that it is confined to one 20px mark that turns only while real work is happening,
 * and that removing it is deleting one file.
 */
public final class SyncSpin {

    private SyncSpin() {}

    /**
     * The motion, in absolute degrees, one entry per tick.
     *
     * <h2>⚠ A TABLE, NOT A FUNCTION — and that distinction is what keeps this checkable</h2>
     *
     * A formula would be an easing function in the source, and the next person would be tempted to
     * reuse it. Written out, it is data: a reviewer can see every position the mark takes, and there
     * is nothing here for a second widget to import.
     *
     * <p>The shape, which is what was asked for: <b>wind up</b> against the tension (a slight lean
     * left, slowing as it loads), <b>release</b> through a fast full turn, then <b>settle</b> into
     * rest with one small overshoot the other way. Read down the deltas and the acceleration is
     * visible: 3, 3, 2, 1 winding; then 34, 52, 61 released; then 12, 6, 3, 1 settling.
     */
    private static final double[] ANGLES = {
        // wind up — leaning into the tension, and slowing as it loads
        -3, -6, -8, -9,
        // release — through zero and round, fastest in the middle
        -5, 12, 46, 98, 159, 220, 274, 316,
        // settle — the last of the turn, decelerating hard
        340, 352, 358, 361, 363,
        // one small overshoot the other way, then rest
        362, 360
    };

    /** Where the mark sits when nothing is happening. */
    public static final double REST = 0;

    /**
     * Spins {@code node} for as long as {@code running} says a sync is in flight.
     *
     * <p>⚠ The subscription is returned so the caller can release it — a {@code Pulse} subscription
     * outlives the node that made it, and {@code CycleGrid.dispose} and {@code CoreCage.dispose} were
     * written, correct and called by nobody, leaking one per open of the rig monitor.
     *
     * <p>⚠ {@code Pulse.animate} — <b>decoration</b>, so Reduce motion holds the mark still. That is
     * WCAG 2.2.2's pause, and nothing is lost because the pane says what it is doing in words. ⚠ It
     * also invokes once immediately, which is harmless here (the first entry is a 3° lean) but is the
     * trap the market carousel records for an action that <em>advances</em> rather than paints.
     *
     * @param running asked on every tick — true while a sync is in flight
     */
    public static AutoCloseable spin(Region node, java.util.function.BooleanSupplier running) {
        int[] step = {-1};
        return Pulse.shared().animate(60, () -> {
            if (!running.getAsBoolean()) {
                // ⚠ Snaps home rather than finishing the table. A sync that ends mid-turn should
                // leave the mark upright at once — carrying on to the end would show motion after
                // the thing it reports has stopped, which is the one lie a progress indicator can
                // tell.
                step[0] = -1;
                node.setRotate(REST);
                return;
            }
            step[0] = (step[0] + 1) % ANGLES.length;
            node.setRotate(ANGLES[step[0]]);
        });
    }

    /** The table, for a test that has to know the motion without a toolkit. */
    static double[] angles() {
        return ANGLES.clone();
    }
}
