package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * The one clock every animated thing on the deck runs from.
 *
 * <h2>One driver, not one per widget</h2>
 *
 * {@code docs/design/ui-design-language.md} §7.3 is explicit: "All timers on one shared
 * {@code Timeline} driver, not one per widget." The deck has a caret, several greeble strips, a
 * sweep bar, recovery cells and a handful of twitching readouts on screen simultaneously — each of
 * which would otherwise be its own {@code INDEFINITE} {@code Timeline}, each with its own pulse
 * thread bookkeeping, none of them in phase. Sharing a driver also makes the recovery blink and the
 * caret land on the same frames, which is what makes the deck feel like one machine rather than
 * several widgets that happen to be adjacent.
 *
 * <h2>Reduced motion is a hard split, not a slow-down</h2>
 *
 * §5: "{@code prefers-reduced-motion} kills all of it — static final state, caret solid. Not
 * optional." So the two subscription kinds are genuinely different:
 *
 * <ul>
 *   <li>{@link #every} — <b>data</b>. A session clock, a live figure. Runs regardless; suppressing
 *       it would not remove animation, it would remove information.
 *   <li>{@link #animate} — <b>decoration</b>. Greeble, blink, sweep. Under reduced motion these are
 *       invoked exactly once, so the widget paints its resting state and then holds still.
 * </ul>
 *
 * <p>Note that reduced motion is a real OS preference JavaFX exposes — {@code
 * Platform.getPreferences().isReducedMotion()} — which {@code ThemeManager} already reads. (§10
 * criterion 8 asserts the toolkit cannot; that is the one factual error in the design language, and
 * it is corrected in the copy checked in here.)
 */
public final class Pulse {

    /** The driver's own period. Every subscription's period is quantised to a multiple of this. */
    private static final double TICK_MS = 100;

    private static final Pulse INSTANCE = new Pulse();

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Timeline driver;
    private long elapsedMs;
    private boolean reducedMotion;

    private Pulse() {
        driver = new Timeline(new KeyFrame(Duration.millis(TICK_MS), e -> tick()));
        driver.setCycleCount(Animation.INDEFINITE);
    }

    public static Pulse shared() {
        return INSTANCE;
    }

    /**
     * Switches decorative motion on or off for the whole client.
     *
     * <p>Turning it <em>on</em> fires every decorative subscription once, so a widget that has been
     * frozen since it was built paints its resting state immediately rather than waiting up to a
     * full period to look right.
     */
    public void setReducedMotion(boolean reduced) {
        this.reducedMotion = reduced;
        if (reduced) {
            for (Subscription s : List.copyOf(subscriptions)) {
                if (s.decorative) {
                    s.action.run();
                }
            }
        }
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    /** A repeating <b>data</b> update. Runs under reduced motion too. */
    public AutoCloseable every(double periodMs, Runnable action) {
        return subscribe(periodMs, action, false);
    }

    /**
     * A repeating <b>decorative</b> update.
     *
     * <p>Invoked once immediately and then on its period — or once and never again, if reduced
     * motion is on. Either way the widget is painted before the first period elapses, so nothing is
     * ever blank while waiting for its first tick.
     */
    public AutoCloseable animate(double periodMs, Runnable action) {
        action.run();
        return subscribe(periodMs, action, true);
    }

    private AutoCloseable subscribe(double periodMs, Runnable action, boolean decorative) {
        long period = Math.max((long) TICK_MS, Math.round(periodMs / TICK_MS) * (long) TICK_MS);
        Subscription subscription = new Subscription(period, action, decorative, elapsedMs + period);
        subscriptions.add(subscription);
        if (driver.getStatus() != Animation.Status.RUNNING) {
            driver.play();
        }
        return () -> subscriptions.remove(subscription);
    }

    private void tick() {
        elapsedMs += (long) TICK_MS;
        // Copied because an action may unsubscribe itself — a one-shot reveal is the obvious case,
        // and mutating the list mid-iteration would be a ConcurrentModificationException on a
        // perfectly reasonable widget.
        for (Subscription s : List.copyOf(subscriptions)) {
            if (elapsedMs < s.dueAt) {
                continue;
            }
            s.dueAt = elapsedMs + s.period;
            if (s.decorative && reducedMotion) {
                continue;
            }
            s.action.run();
        }
    }

    /** Stops the driver. Called on shutdown; the next subscription restarts it. */
    public void stop() {
        driver.stop();
        subscriptions.clear();
    }

    private static final class Subscription {
        private final long period;
        private final Runnable action;
        private final boolean decorative;
        private long dueAt;

        private Subscription(long period, Runnable action, boolean decorative, long dueAt) {
            this.period = period;
            this.action = action;
            this.decorative = decorative;
            this.dueAt = dueAt;
        }
    }
}
