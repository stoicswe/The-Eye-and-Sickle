package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * The only two animations in the client that are not a {@link Pulse} subscription.
 *
 * <h2>{@link Interpolator#DISCRETE}, everywhere, without exception</h2>
 *
 * {@code docs/design/ui-design-language.md} §5: "Any spring, bounce, or ease-out reads as web UI
 * immediately and will undo the whole aesthetic." §9 lists easing curves as build-blocking, and §10
 * criterion 7 makes it checkable: <b>no {@code Interpolator.EASE_*} appears anywhere in the
 * codebase.</b> {@code MotionTest} greps the source tree for exactly that, because this is the kind
 * of rule that survives review nine times and then loses to one plausible "just this dialog".
 *
 * <p>{@link Interpolator#LINEAR} is permitted and is used in exactly one place —
 * {@link io.github.stoicswe.eyeandsickle.client.ui.widgets.SweepPanel}, which §5 specifies as a
 * linear loop.
 *
 * <h2>Why the reveal is a clip and not an opacity fade</h2>
 *
 * §5 gives the panel reveal as "horizontal clip wipe, ~0.34s, 9 discrete steps". A fade would be a
 * continuous interpolation of a continuous property — the exact thing the section bans — and it
 * would also read as a web page loading. A wipe in nine jumps reads as a raster being drawn by
 * something with a fixed refresh, which is the intended illusion.
 */
public final class Motion {

    private Motion() {}

    /**
     * Wipes a node in from the left in {@link UiTokens#REVEAL_STEPS} discrete jumps.
     *
     * <p>Under reduced motion the node is simply shown, unclipped, immediately — §5: "static final
     * state". Note that the clip is <em>removed</em> at the end rather than left at full width: a
     * lingering clip rectangle would silently crop the panel the next time the window is resized,
     * which is a bug that only appears on the second resize and is therefore hard to attribute.
     *
     * @param delayMs stagger, so panes wake in sequence rather than together
     */
    public static void reveal(Region node, double delayMs) {
        if (Pulse.shared().reducedMotion()) {
            node.setClip(null);
            return;
        }

        Rectangle clip = new Rectangle();
        clip.setHeight(4000);
        clip.setWidth(0);
        node.setClip(clip);

        Timeline timeline = new Timeline();
        double step = UiTokens.REVEAL_MS / UiTokens.REVEAL_STEPS;
        for (int i = 1; i <= UiTokens.REVEAL_STEPS; i++) {
            double fraction = i / (double) UiTokens.REVEAL_STEPS;
            timeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(delayMs + step * i),
                    // DISCRETE holds the previous value for the whole interval and jumps at the end
                    // of it. Nine of these is a nine-step wipe; one of them across the full duration
                    // would be a single jump, which is the mistake to avoid here.
                    new KeyValue(clip.widthProperty(), 4000 * fraction, Interpolator.DISCRETE)));
        }
        timeline.setOnFinished(e -> node.setClip(null));
        timeline.play();
    }

    /**
     * Brings a node up from dark in discrete steps — the deck waking, not a cross-fade.
     *
     * <h2>⚠ DISCRETE, like every other motion in the client</h2>
     *
     * §5 permits step timing and nothing else, and {@code UiContractTest} fails the build on any
     * {@code Interpolator.EASE_*} and on {@code LINEAR} anywhere but the sweep bar. A
     * {@code FadeTransition} is a linear tween by default and would be exactly that violation, so
     * this is the same nine-step {@code DISCRETE} ladder {@link #reveal} uses, applied to opacity.
     *
     * <p>Stepping is also the truer effect. A phosphor coming up to brightness is not smooth — it
     * is a fast rise through a few visible levels, which is what a discrete ramp draws for free.
     *
     * <p>⚠ Under reduced motion the node is simply shown at full opacity. §5 requires the "static
     * final state", and a node left at zero opacity because the animation was suppressed is an
     * interface that never appears.
     *
     * @param totalMs how long the whole ramp takes
     */
    public static void fadeIn(Node node, double totalMs) {
        if (Pulse.shared().reducedMotion()) {
            node.setOpacity(1);
            return;
        }
        node.setOpacity(0);
        Timeline timeline = new Timeline();
        double step = totalMs / UiTokens.REVEAL_STEPS;
        for (int i = 1; i <= UiTokens.REVEAL_STEPS; i++) {
            double fraction = i / (double) UiTokens.REVEAL_STEPS;
            timeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(step * i),
                    new KeyValue(node.opacityProperty(), fraction, Interpolator.DISCRETE)));
        }
        // ⚠ Pinned to exactly 1 at the end rather than left on the last computed fraction. Nine
        // steps of 1/9 sums to 0.9999999999999999 in double arithmetic, and a deck sitting at
        // 99.99999% opacity is a compositing layer that never goes away.
        timeline.setOnFinished(e -> node.setOpacity(1));
        timeline.play();
    }

    /**
     * A blinking block caret, on the shared driver.
     *
     * <p>{@code steps(1,end)} in the reference — a hard on/off, never a fade. Under reduced motion
     * it holds solid rather than disappearing, because the caret marks <em>where typing goes</em>
     * and removing it would remove information rather than motion.
     *
     * @return the caret node; subscribe management is handled by {@link Pulse}
     */
    public static Region caret() {
        Region caret = Ui.block(7, 13, "es-caret");
        if (Pulse.shared().reducedMotion()) {
            return caret;
        }
        Pulse.shared().animate(UiTokens.CARET_MS / 2, () -> caret.setVisible(!caret.isVisible()));
        return caret;
    }

    /** Marks a node as having no motion at all — used by tests to assert a static tree. */
    public static void freeze(Node node) {
        node.setClip(null);
    }
}
