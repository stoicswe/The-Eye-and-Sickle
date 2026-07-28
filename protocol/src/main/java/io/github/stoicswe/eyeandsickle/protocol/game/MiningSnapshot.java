package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * What a mining dashboard shows: the chain, the rig's part in it, and what has actually landed.
 *
 * <h2>⚠ There is deliberately no progress figure, and that absence is the mechanic</h2>
 *
 * Mining is a <b>memoryless</b> process. Every hash is an independent trial against the same target,
 * so a rig that has been mining for three hours is exactly as close to the next block as one that
 * started a second ago — there is no partial credit, nothing accumulates, and nothing is lost by
 * stopping. A progress bar would be a lie about all four of those, and a specific and expensive one:
 * a player watching a bar fill would reasonably conclude that pulling cycles mid-bar forfeits
 * something, and would hold cycles on mining to protect progress that does not exist.
 *
 * <p>What a real mining readout shows instead is here — the <em>expected</em> time between payouts,
 * how long it has actually been, and what has landed. "Overdue" is a feeling, never a state.
 *
 * @param mode pooled or solo
 * @param cycles cycles committed to self-mining
 * @param hashrate the rig's hashrate in hashes per second
 * @param networkHashrate the whole chain's hashrate in hashes per second
 * @param difficulty network difficulty — expected hashes per block is {@code difficulty * 2^32}
 * @param shareDifficulty the difficulty this rig's pool shares are set to, or 0 when solo
 * @param height the chain's current block height
 * @param blocksUntilRetarget how many blocks until difficulty is recalculated
 * @param expectedPayoutSeconds mean seconds between payouts at this hashrate and mode
 * @param secondsSinceLastPayout how long it has actually been, or -1 if nothing has ever landed
 * @param expectedMinorUnitsPerHour the long-run rate — equal in both modes but for the pool's fee
 * @param payoutMinorUnits what one payout is worth: a whole block subsidy, or one share
 * @param lifetimePayouts blocks found, or shares accepted
 * @param lifetimeMinorUnits everything mining has ever paid this character
 * @param poolFeeBasisPoints the pool's cut, in hundredths of a percent; 0 when solo
 * @param lastPayoutAt when the last payout landed, or null
 * @param pool the pool this rig mines with, or null when solo
 * @param pendingMinorUnits earned but not yet paid out — the pool's internal balance for this rig
 * @param secondsUntilSettle how long until the pool pays up, or 0 when solo or nothing is pending
 * @param noiseCycles how loud being pooled makes this rig, in cycle-equivalents; 0 when solo
 */
public record MiningSnapshot(
        MiningMode mode,
        long cycles,
        long hashrate,
        long networkHashrate,
        double difficulty,
        double shareDifficulty,
        long height,
        long blocksUntilRetarget,
        double expectedPayoutSeconds,
        long secondsSinceLastPayout,
        long expectedMinorUnitsPerHour,
        long payoutMinorUnits,
        long lifetimePayouts,
        long lifetimeMinorUnits,
        int poolFeeBasisPoints,
        Instant lastPayoutAt,
        MiningPool pool,
        long pendingMinorUnits,
        long secondsUntilSettle,
        long noiseCycles) {

    /** What a payout is worth as a fraction of a whole block — 1 when solo. */
    public double payoutFraction() {
        return difficulty <= 0 ? 0.0d : shareDifficulty / difficulty;
    }

    /** Whether any cycles are committed at all. */
    public boolean active() {
        return cycles > 0;
    }

    /**
     * The chance of at least one payout in {@code seconds}, from the exponential distribution.
     *
     * <p>{@code 1 - exp(-t/T)}. This is the honest way to answer "will I get one this session?" and
     * it is the number a solo miner actually needs — the mean on its own badly misleads, because for
     * an exponential the median is only {@code ln(2) ≈ 69%} of the mean and well over half of all
     * waits come in under it while a long tail runs to several times it.
     */
    public double chanceWithin(double seconds) {
        if (expectedPayoutSeconds <= 0 || seconds <= 0) {
            return 0.0d;
        }
        return 1.0d - Math.exp(-seconds / expectedPayoutSeconds);
    }
}
