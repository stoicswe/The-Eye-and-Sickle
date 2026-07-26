package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import java.time.Duration;

/**
 * How spent cycles come back.
 *
 * <p>{@code docs/design/01-core-resources.md} §1.3: cycles spent on a discrete action do not return
 * instantly, they recover on a curve that is <em>slower the closer the rig sits to capacity</em>. That
 * shape is the design commitment. The two constants behind it are explicitly marked
 * <strong>[PROPOSAL]</strong> "for playtest", which is why they live in {@link Balance} and why no
 * teaching page in {@code docs/education/} quotes them.
 *
 * <p>The consequence a player actually feels: running the rig flat out is not merely a capacity
 * problem, it is a <em>recovery</em> problem. A rig at 90% load takes far longer to get a Thorough
 * Scan's 35 cycles back than an idle one does, so over-committing compounds.
 */
public final class ThermalRules {

    private ThermalRules() {}

    /**
     * Seconds for {@code cycles} to recover on a rig at the given load.
     *
     * @param cycles how many cycles are coming back
     * @param loadFactor allocated ÷ total, clamped to {@code [0, 1)}
     * @param thermalBudget the rig's Thermal Budget stat; higher recovers faster
     */
    public static Duration recoveryTime(long cycles, double loadFactor, int thermalBudget) {
        if (cycles <= 0) {
            return Duration.ZERO;
        }
        // Clamped strictly below 1: a rig at exactly full load would otherwise divide by zero and
        // recover never, which is a plausible-looking way to hard-lock a save.
        double load = Math.max(0.0d, Math.min(0.999d, loadFactor));
        double rate = Balance.THERMAL_BASE_RATE_CYCLES_PER_SECOND
                * Math.pow(1.0d - load, Balance.THERMAL_LOAD_EXPONENT)
                * Math.max(1, thermalBudget);
        if (rate <= 0.0d) {
            return Duration.ofDays(1);
        }
        return Duration.ofSeconds(Math.max(1L, Math.round(cycles / rate)));
    }
}
