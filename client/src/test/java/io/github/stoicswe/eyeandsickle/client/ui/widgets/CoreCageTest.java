package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The core cutaway's turn rate, which is the rig's load.
 *
 * <h2>⚠ NO TOOLKIT HERE, WHICH IS WHY THE RULE IS A PURE METHOD</h2>
 *
 * Constructing a {@code CoreCage} subscribes to {@code Pulse}, which needs a live FX toolkit — and
 * {@code NodeMenuTest} is the only JUnit test in this client permitted to start one. So the speed rule
 * lives in a pure, package-private {@code stepsPerTick} and is checked here directly. Same seam as
 * {@code SyncSpinTest}, and for the same reason: a rule that could only be verified by running the
 * client and watching it is a rule nobody verifies.
 */
@DisplayName("the core cutaway's turn rate")
class CoreCageTest {

    /** Steps per tick → seconds for a full revolution, which is the reviewable quantity. */
    private static double revolutionSeconds(double load) {
        return 48 / CoreCage.stepsPerTick(load) * 0.100;
    }

    @Test
    @DisplayName("a fully loaded rig turns faster than a lightly loaded one")
    void loadMakesItFaster() {
        // The whole feature, in one assertion.
        assertThat(CoreCage.stepsPerTick(1.0)).as("full load").isGreaterThan(CoreCage.stepsPerTick(0.1));
    }

    @Test
    @DisplayName("speed rises with load at every point, never dipping in the middle")
    void monotonic() {
        // A non-monotonic mapping would be the worst possible version of this: the cage would speed
        // up as the rig filled and then slow down again, so the reading would be ambiguous — two
        // different loads showing the same rate — with nothing on screen to disambiguate them.
        double previous = -1;
        for (int percent = 0; percent <= 100; percent++) {
            double now = CoreCage.stepsPerTick(percent / 100.0);
            assertThat(now).as("at %d%% load", percent).isGreaterThan(previous);
            previous = now;
        }
    }

    @Test
    @DisplayName("the ends land on the intended revolution times")
    void endpoints() {
        // Asserted as SECONDS PER REVOLUTION rather than as steps per tick, because that is the
        // number a person can judge — "eighteen seconds to go round" is reviewable, "0.267 steps per
        // tick" is not, and the constants are declared in seconds for the same reason.
        assertThat(revolutionSeconds(0.0)).as("lightest").isEqualTo(18.0, within(0.01));
        assertThat(revolutionSeconds(1.0)).as("fullest").isEqualTo(3.0, within(0.01));
    }

    @Test
    @DisplayName("it never stops on its own, however light the load")
    void neverStalls() {
        // ⚠ THE FLOOR IS LOAD-BEARING. If a barely-loaded rig crawled imperceptibly, a player could
        // not tell it from a stopped one — and "stopped" is the widget's clearest reading, meaning
        // the rig is idle. A rate that ramped to zero would destroy the very signal the stopped state
        // exists to carry. Stopping is `advance`'s guard's job, not this method's.
        assertThat(CoreCage.stepsPerTick(0.0)).isPositive();
        assertThat(CoreCage.stepsPerTick(0.001)).isPositive();
    }

    @Test
    @DisplayName("the fast end is still slow enough to read as a machine")
    void notABlur() {
        // ⚠ The failure this guards is the one the whole widget is arranged against: a fast ASCII
        // tumble reads as a screensaver rather than as instrumentation. Under about two seconds a
        // revolution the glyph churn stops looking like a machine redrawing itself. It also sits
        // beside HexStream, and two fast-moving things in one panel compete for the same attention.
        assertThat(revolutionSeconds(1.0)).as("a full revolution").isGreaterThan(2.0);
        // And a step at a time, so the stepping stays visible rather than skipping whole faces.
        assertThat(CoreCage.stepsPerTick(1.0)).as("steps per frame").isLessThan(3.0);
    }

    @Test
    @DisplayName("out-of-range load is clamped rather than producing a stall or a blur")
    void clamped() {
        // Load is a fraction computed from cycle counts, and a hand-edited save can produce numbers
        // no legitimate rig has. Negative would run the cage BACKWARDS; a large one would spin it
        // into noise.
        assertThat(CoreCage.stepsPerTick(-5)).isEqualTo(CoreCage.stepsPerTick(0));
        assertThat(CoreCage.stepsPerTick(99)).isEqualTo(CoreCage.stepsPerTick(1));
    }

    @Test
    @DisplayName("a lightly loaded rig advances less than a step per tick, which is why position is a double")
    void slowLoadsNeedFractionalSteps() {
        // ⚠ This is the constraint that ruled out the obvious implementation. An integer step per
        // tick can only express whole divisors of the tick rate — 14.4s, 7.2s, 4.8s per revolution —
        // so the speed would come in four notches and the fast end would jump 15° a frame. Anything
        // under 1.0 here is unreachable without accumulating between steps.
        assertThat(CoreCage.stepsPerTick(0.0)).isLessThan(1.0);
        assertThat(CoreCage.stepsPerTick(0.46)).as("the rig in the screenshot").isLessThan(1.0);
    }
}
