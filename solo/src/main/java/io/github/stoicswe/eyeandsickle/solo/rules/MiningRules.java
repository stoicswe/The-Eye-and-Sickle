package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;

/**
 * Income: the safe floor, and the risky ceiling.
 *
 * <h2>The two sources are deliberately asymmetric</h2>
 *
 * <b>Self-mining</b> is the income floor and is protected by two invariants: it generates zero heat
 * and cannot be detected or seized (I4), and it only runs while the player is online (I5). It is
 * boring on purpose — it is what stops a run from becoming unrecoverable.
 *
 * <p><b>Deployed miners</b> are the only offline income (I5), and every one of them costs the deployer
 * a permanent control channel while charging its actual work to the <em>host</em> (I6). Their yield
 * accrues into an on-host buffer that stops dead at a cap, so time away is worth something but not
 * proportionally — and the buffer is the prize somebody takes when they crack the miner.
 */
public final class MiningRules {

    private MiningRules() {}

    /**
     * Self-mining yield for an elapsed interval, in minor units.
     *
     * <p>Integral throughout: fractional minor units would accumulate rounding differences between a
     * session played in one sitting and the same session played in ten, which is exactly the kind of
     * bug nobody reports and everybody feels.
     */
    public static long selfMiningYield(long allocatedCycles, Duration elapsed) {
        if (allocatedCycles <= 0 || elapsed.isNegative() || elapsed.isZero()) {
            return 0L;
        }
        // cycles × (minorUnits per cycle-hour) × hours, done in seconds to avoid truncating short
        // sessions to zero.
        long seconds = elapsed.toSeconds();
        return Math.floorDiv(allocatedCycles * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR * seconds, 3600L);
    }

    /** The most a single miner's buffer may hold, in minor units. */
    public static long bufferCap(MinerState miner) {
        return miner.hostCycles * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR * Balance.YIELD_BUFFER_HOURS;
    }

    /**
     * Accrues buffered yield for every deployed miner up to {@code now}, respecting the cap.
     *
     * <p>Called on load as well as on tick, which is what makes offline income work: a player who
     * closed the client eight hours ago gets four hours' worth, because the cap bit four hours in.
     *
     * @return total minor units added across all miners
     */
    public static long accrueDeployedMiners(SoloSave save, Instant now) {
        long added = 0L;
        for (NodeState node : save.knownNodes) {
            for (MinerState miner : node.deployedMiners) {
                Duration elapsed = Duration.between(miner.lastAccruedAt, now);
                if (elapsed.isNegative() || elapsed.isZero()) {
                    continue;
                }
                long cap = bufferCap(miner);
                long yield = selfMiningYield(miner.hostCycles, elapsed);
                long before = miner.bufferedMinorUnits;
                miner.bufferedMinorUnits = Math.min(cap, before + yield);
                miner.lastAccruedAt = now;
                added += miner.bufferedMinorUnits - before;
            }
        }
        return added;
    }

    /** Sweeps every buffer into the balance. This is what {@code collect} does. */
    public static long collectAll(SoloSave save, Instant now) {
        long collected = 0L;
        for (NodeState node : save.knownNodes) {
            for (MinerState miner : node.deployedMiners) {
                collected += miner.bufferedMinorUnits;
                miner.bufferedMinorUnits = 0L;
            }
        }
        if (collected > 0) {
            LedgerRules.apply(save, collected, "MINING_COLLECT", "Collected deployed-miner yield", now);
        }
        return collected;
    }
}
