package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.Region;
import javafx.util.Duration;

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
        -1.7, -3.2, -4.6, -5.8, -6.8, -7.6, -8.2, -8.6, -8.9, -9,
        // release — back through zero and round, fastest in the middle
        -8.9, -8, -5.8, -1.9, 4.1, 12.4, 23.1, 36.1, 51.3, 68.7, 87.8, 108.5, 130.3, 152.9, 176,
        199.1, 221.7, 243.5, 264.2, 283.3, 300.7, 315.9, 328.9, 339.6, 347.9, 353.9, 357.8, 360,
        360.9, 361,
        // one small overshoot the other way, then rest
        362.4, 363, 363.2, 362.9, 362.3, 361.6, 361, 360.6, 360.3, 360.1, 360
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
        Timeline clock = new Timeline(new KeyFrame(Duration.millis(UiTokens.SPIN_MS), event -> {
            // ⚠ REDUCE MOTION IS ASKED EVERY TICK, and it used to be free. On `Pulse.animate` a
            // decorative subscription simply never fires there, so the mark held still without this
            // widget knowing why. Off Pulse, that has to be explicit — and it is asked per tick
            // rather than at start-up because the setting can be turned on while a sync is running,
            // and a spinner that kept turning would be motion the player had just switched off.
            if (Pulse.shared().reducedMotion()) {
                // ⚠ The ONE case that stops mid-turn, and it must. Reduce motion is a request to
                // stop moving now, not to finish the flourish first.
                step[0] = -1;
                node.setRotate(REST);
                return;
            }
            step[0] = advance(step[0], running.getAsBoolean());
            node.setRotate(step[0] < 0 ? REST : ANGLES[step[0]]);
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        return clock::stop;
    }

    /**
     * Where the mark goes next. {@code -1} is at rest.
     *
     * <h2>⚠ ONCE A TURN HAS STARTED IT ALWAYS RUNS TO THE END OF THE TABLE</h2>
     *
     * A sync check is usually fast — a {@code getLog} poll that finds nothing can be back in a couple
     * of hundred milliseconds — and the earlier version snapped the mark home the instant it
     * finished. So the common case never showed a spin at all: it showed a <b>twitch</b>, a few
     * degrees of lean and then nothing, which reads as a rendering glitch rather than as work
     * happening. Worse, how far it got was a function of somebody else's server latency, so the same
     * event looked different every time.
     *
     * <p>⚠ The honest cost, stated because it reverses a rule this file used to hold: the mark can
     * now still be turning for up to one table's worth of time <em>after</em> the sync it reports has
     * finished — about 1.7 seconds. That was previously called "the one lie a progress indicator can
     * tell". The trade was made deliberately, on explicit direction: an indicator that is legible
     * slightly too long beats one that is illegible every time, and the alternative — a spin whose
     * length encodes latency — is not information anybody can read at this size.
     *
     * <p>⚠ It <b>restarts</b> rather than easing out while the sync is still going, so a slow sync
     * turns continuously.
     *
     * <p>Package-private and pure so the rule can be checked without a toolkit — the same seam
     * {@code DirectView.state} and {@code SecurityCenterView.latestOf} exist for.
     *
     * @param step where it is now, or {@code -1} at rest
     * @param syncing whether a sync is still in flight
     */
    static int advance(int step, boolean syncing) {
        if (step < 0) {
            // At rest. Only a new sync starts a turn.
            return syncing ? 0 : -1;
        }
        int next = step + 1;
        if (next >= ANGLES.length) {
            return syncing ? 0 : -1;
        }
        return next;
    }

    /** The table, for a test that has to know the motion without a toolkit. */
    static double[] angles() {
        return ANGLES.clone();
    }
}
