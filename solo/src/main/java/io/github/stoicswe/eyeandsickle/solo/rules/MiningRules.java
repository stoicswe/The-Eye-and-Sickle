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

    /**
     * Accrues buffered yield for every <em>foreign</em> miner squatting on the player's own rig.
     *
     * <h2>Why this is a second method and not a wider loop in the first one</h2>
     *
     * The two accruals look identical and mean opposite things. {@link #accrueDeployedMiners} grows
     * a buffer the player will collect; this one grows a buffer that belongs to <em>somebody
     * else</em> and is sitting on the player's disk. Merging them would put a stranger's income into
     * the number the resume log reports as "deployed miners buffered X while away", which is the
     * kind of readout error {@code docs/design/04-mining.md} §3.1 trains the player to treat as
     * evidence — and here it would be evidence of a bug rather than of an intruder.
     *
     * <p>It has to accrue at all because {@code 04} §5.1 makes the crack a timing bet: "payout scales
     * with buffer fullness. Found at minute five, it holds almost nothing; found at hour four, the
     * full cap. Killing immediately is safe and worth little; leaving it to fatten means bleeding
     * compute meanwhile and risking the deployer returning to collect first." A buffer that never
     * grows makes both branches of that decision worth zero and turns cracking into a formality.
     *
     * <p>The same buffer cap applies, and it is what bounds the prize: four hours of the miner's own
     * host draw, never more, however long the player leaves it.
     *
     * <p>⚠ This must be called on the resume path as well as on tick, for the same reason
     * {@link #accrueDeployedMiners} is: {@code resume()} sets {@code lastTick = now}, so the first
     * {@code tick()} after a load sees zero elapsed time and returns early. Offline growth belongs
     * on the offline path.
     *
     * @return total minor units added, which the player does not own and must never be credited
     */
    public static long accrueForeignMiners(SoloSave save, Instant now) {
        long added = 0L;
        for (MinerState miner : save.rig.foreignMiners) {
            Duration elapsed = Duration.between(miner.lastAccruedAt, now);
            if (elapsed.isNegative() || elapsed.isZero()) {
                continue;
            }
            long cap = bufferCap(miner);
            long before = miner.bufferedMinorUnits;
            miner.bufferedMinorUnits = Math.min(cap, before + selfMiningYield(miner.hostCycles, elapsed));
            miner.lastAccruedAt = now;
            added += miner.bufferedMinorUnits - before;
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
