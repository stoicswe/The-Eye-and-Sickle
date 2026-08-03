package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.time.Duration;

/**
 * How spent cycles come back.
 *
 * <h2>The shape, and the ceiling</h2>
 *
 * {@code docs/design/01-core-resources.md} §1.3: cycles spent on a discrete action do not return
 * instantly, they recover on a curve that is <em>slower the closer the rig sits to capacity</em>.
 * That is the design commitment and it is unchanged. What is new is a <b>published ceiling</b>: five
 * minutes on a rig with nothing stealing from it, ten on one being comprehensively robbed.
 *
 * <h2>⚠ The old formula had no ceiling, and the tail was the bug</h2>
 *
 * It was {@code time = cycles ÷ (0.5 × (1 − load)² × thermalBudget)}. As load approaches capacity the
 * denominator approaches zero, so the time approaches infinity — and it got there fast enough to
 * matter. Measured: a Thorough Scan's 35 cycles on a rig at 90% load took <b>36 minutes</b>; two
 * cycles on a rig at 82% took a hundred seconds. Over-committing is supposed to be a mistake the
 * player feels, not one that benches them for half an hour, and there was nothing in the design that
 * said where the ceiling was because the formula did not have one.
 *
 * <p>This states the ceiling first and derives the time as a <em>fraction</em> of it, so the worst
 * case is a number somebody chose rather than a limit of the arithmetic:
 *
 * <pre>
 *   size    = √(cycles ÷ totalCycles)                        how much of the rig is coming back
 *   loadTerm= IDLE_FLOOR + (1 − IDLE_FLOOR) × load^k         how busy it is while it does
 *   ceiling = MAX_CLEAN + (MAX_INFESTED − MAX_CLEAN) × theft
 *   seconds = ceiling × size × loadTerm ÷ thermalBudget      clamped to [MIN, ceiling]
 * </pre>
 *
 * <p>The ceiling is an <b>asymptote, not a clip</b>. Both factors are strictly below 1 in every real
 * situation, so load keeps reading all the way up instead of flattening into a plateau where 80% and
 * 95% feel the same — which is what a naive {@code min(time, 300)} would have produced.
 *
 * <h2>Why the size term is a square root</h2>
 *
 * Linear in {@code cycles ÷ total} would make small returns effectively instant: two cycles back on a
 * hundred-cycle rig would be 2% of the ceiling however loaded the machine was, and the sweep ladder
 * would have no recovery cost at all. The root keeps small spends cheap and still perceptible, and it
 * is the reason the ceiling is reachable in play rather than only in theory.
 *
 * <h2>Rogue processes slow recovery twice, and the second time is the interesting one</h2>
 *
 * A parasite holds cycles, so it raises the load factor, so it slows recovery through the same curve
 * everything else uses. That much needs no special case. The {@code theft} term is the <em>second</em>
 * effect and it is deliberately separate: a player who has released every allocation they own and
 * still watches a slow recovery has been handed the discrepancy {@code docs/design/04-mining.md} §3.1
 * is built on, without being told anything.
 */
public final class ThermalRules {

    private ThermalRules() {}

    /**
     * Seconds for {@code cycles} to recover.
     *
     * @param cycles how many cycles are coming back
     * @param totalCycles the rig's ceiling — the denominator the return is measured against. A
     *     non-positive value is treated as "the whole rig", which is the reading that cannot make a
     *     malformed save recover instantly
     * @param loadFactor allocated ÷ total, clamped to {@code [0, 1]}
     * @param thermalBudget the rig's Thermal Budget stat; higher recovers faster
     * @param stolenShare fraction of the rig held by processes that are not the player's, {@code [0, 1]}
     *     — the only thing that may lift the ceiling past {@link Balance#THERMAL_MAX_CLEAN_SECONDS}
     */
    public static Duration recoveryTime(
            long cycles, long totalCycles, double loadFactor, int thermalBudget, double stolenShare) {

        if (cycles <= 0) {
            return Duration.ZERO;
        }
        long capacity = totalCycles > 0 ? totalCycles : cycles;
        double size = Math.sqrt(clamp(cycles / (double) capacity));
        double loadTerm = Balance.THERMAL_IDLE_FLOOR
                + (1.0d - Balance.THERMAL_IDLE_FLOOR) * Math.pow(clamp(loadFactor), Balance.THERMAL_LOAD_EXPONENT);

        long ceiling = ceilingSeconds(stolenShare);
        double seconds = ceiling * size * loadTerm / Math.max(1, thermalBudget);

        return Duration.ofSeconds(Math.max(Balance.THERMAL_MIN_SECONDS, Math.min(ceiling, Math.round(seconds))));
    }

    /**
     * The longest any recovery on this rig may take, in seconds.
     *
     * <p>Published separately because it is the number the readout should be able to promise — "this
     * will be back within N" is a far more useful thing to show than a rate, and it is exactly the
     * guarantee the old formula could not make.
     */
    public static long ceilingSeconds(double stolenShare) {
        double theft = clamp(stolenShare);
        return Math.round(Balance.THERMAL_MAX_CLEAN_SECONDS
                + (Balance.THERMAL_MAX_INFESTED_SECONDS - Balance.THERMAL_MAX_CLEAN_SECONDS) * theft);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
