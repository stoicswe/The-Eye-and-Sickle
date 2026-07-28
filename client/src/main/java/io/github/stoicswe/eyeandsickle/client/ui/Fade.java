package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;

/**
 * A continuous opacity ramp — the one place in the client that has one.
 *
 * <h2>⚠ This contradicts §5 as it was written, and the contradiction is the point</h2>
 *
 * {@code docs/design/ui-design-language.md} §5 said "step and linear timing only", and
 * {@link Motion}'s own header argued the case: <em>"A fade would be a continuous interpolation of a
 * continuous property — the exact thing the section bans."</em> That argument is right about
 * <b>the interface</b>. It is wrong about a <b>title card</b>, which is what §5.1 now says.
 *
 * <p>The distinction §5 is really drawing is between motion the player is <em>working inside</em>
 * and motion they are only watching. A panel that fades in makes a player wait to read it, and a
 * value that tweens is a number lying about what it is. Neither applies to a splash handing over to
 * a login screen: nothing is readable during it, nothing is interactive, and nothing is being
 * measured. §5's rules still govern every screen where the player is doing something.
 *
 * <h2>Why an AnimationTimer and not a Timeline</h2>
 *
 * A {@code Timeline} + {@code KeyValue} would interpolate with {@link javafx.animation.Interpolator}
 * — implicitly {@code LINEAR}, which {@code UiContractTest} rations to exactly one file. Reaching
 * continuous motion by relying on a <em>default</em> that the contract test cannot see would be
 * getting past the check rather than passing it. An {@code AnimationTimer} computes the ramp from
 * elapsed nanoseconds with plain arithmetic: there is no interpolator, the linearity is written
 * down, and {@code UiContractTest} rations {@code AnimationTimer} by filename instead.
 *
 * <p>It also runs per frame rather than on {@link Pulse}'s 100ms driver, which is the whole reason
 * the fade and the progress bar are smooth and everything else in the client is not.
 */
public final class Fade {

    private Fade() {}

    /** Long enough to register as a handover, short enough that nobody waits on it. */
    public static final double HANDOVER_MS = 420;

    /**
     * Ramps a node's opacity and runs {@code onDone} when it lands.
     *
     * <p>Under reduced motion the node jumps straight to {@code to} and {@code onDone} runs on the
     * spot — §5: "static final state". ⚠ {@code onDone} runs <b>exactly once</b> either way; a
     * handover that dropped it would leave the player on a screen with no way forward.
     *
     * @param millis ramp duration; a non-positive value is treated as an immediate jump
     */
    public static void ramp(Node node, double from, double to, double millis, Runnable onDone) {
        node.setOpacity(from);
        if (millis <= 0 || Pulse.shared().reducedMotion()) {
            node.setOpacity(to);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }

        AnimationTimer timer = new AnimationTimer() {
            private long startedAt;

            @Override
            public void handle(long now) {
                // ⚠ The frame's own timestamp, never Instant.now(). AnimationTimer hands over the
                // pulse time in nanoseconds; mixing in a wall clock makes the ramp's duration depend
                // on which clock the test harness installed.
                if (startedAt == 0) {
                    startedAt = now;
                    return;
                }
                double elapsed = (now - startedAt) / 1_000_000.0;
                double progress = Math.min(1.0, elapsed / millis);
                node.setOpacity(from + (to - from) * progress);
                if (progress >= 1.0) {
                    stop();
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }
        };
        timer.start();
    }

    /** Fades a node out and then runs {@code onDone}. */
    public static void out(Node node, Runnable onDone) {
        ramp(node, 1, 0, HANDOVER_MS, onDone);
    }

    /** Fades a node in. */
    public static void in(Node node) {
        ramp(node, 0, 1, HANDOVER_MS, null);
    }
}
