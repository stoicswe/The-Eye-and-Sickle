package io.github.stoicswe.eyeandsickle.engine.state;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.time.Instant;

/**
 * The chain the rig mines against: height, difficulty, and the retarget clock.
 *
 * <h2>Why the game simulates a chain at all</h2>
 *
 * Self-mining used to be a rate — cycles in, ethecoin out, linearly, forever. That is a perfectly
 * good abstraction and it is also the reason the shipped {@code self-mining(7)} page had to open by
 * saying "nothing about this resembles real cryptocurrency mining". Since 2026-07-27 it does resemble
 * it, because the pooled-versus-solo choice {@code docs/design/04-mining.md} §1.3 now offers is not a
 * choice at all unless the underlying process is genuinely random: the whole difference between the
 * two modes is <b>variance</b>, and variance needs a distribution to come from.
 *
 * <h2>⚠ This is a real Poisson process, not a timer with jitter</h2>
 *
 * Blocks arrive as a Poisson process because every hash is an independent trial against a fixed
 * target, which makes the wait between blocks <b>exponentially distributed</b> and therefore
 * <b>memoryless</b>. Three consequences the rest of the code depends on:
 *
 * <ul>
 *   <li><b>Nothing accumulates.</b> A rig three hours into a block is exactly as close to finding one
 *       as a rig that started a second ago. This is what let {@code 04} §1.3's proposed
 *       "pulling cycles mid-block forfeits that block's progress" rule be deleted rather than
 *       implemented — there is no progress to forfeit, so the rule had nothing to describe.
 *   <li><b>Being overdue is not a thing.</b> A chain that has gone an hour without a block is not
 *       "due" one. Publishing a progress figure would teach the opposite, which is why
 *       {@code MiningSnapshot} deliberately has none.
 *   <li><b>The mean badly misdescribes the median.</b> For an exponential the median is
 *       {@code ln 2 ≈ 69%} of the mean: more than half of all waits come in under the average and a
 *       long tail runs to several times it. Solo mining <em>feels</em> unluckier than it is, and that
 *       is a true fact about the distribution rather than a tuning failure.
 * </ul>
 *
 * <h2>What is real and what is simplified</h2>
 *
 * Real and reused exactly: the {@code difficulty × 2^32} work relation, the ten-minute target, the
 * 2016-block retarget window, and the factor-of-four clamp on an adjustment. Simplified: <b>the rest
 * of the network's hashrate never changes</b>, so difficulty has no trend — it sits at the
 * equilibrium {@code Balance.chainDifficultyFor} put it at. A real chain's difficulty <em>trends</em>,
 * because the hashrate behind it grows.
 *
 * <p>⚠ It does <b>not</b> sit perfectly still, and an earlier version of this comment wrongly said it
 * did. 2016 random block times do not add up to exactly two weeks — the relative spread is
 * {@code 1/√2016 ≈ 2.2%} — so every retarget nudges difficulty by a percent or two in whichever
 * direction the last window happened to run. Measured over 2000 simulated hours: difficulty wandered
 * from 344.5 to 351.1 across five retargets while income stayed within 0.3% of its expectation. That
 * jitter is <em>real Bitcoin behaviour</em> and not an artefact; a difficulty that held a constant
 * value to the decimal would be the thing worth investigating.
 */
public final class ChainState {

    /** Blocks mined so far. Starts partway in so a new character joins a chain with a history. */
    public long height = 0L;

    /** Expected hashes per block is {@code difficulty × 2^32}. */
    public double difficulty = 1.0d;

    /** Everyone else's hashrate, in hashes per second. */
    public double networkHashrate = Balance.chainNetworkHashrate();

    /** How far into the current 2016-block window the chain is. */
    public long blocksSinceRetarget = 0L;

    /** When the current retarget window opened — the retarget compares elapsed against expected. */
    public Instant retargetStartedAt = Instant.EPOCH;

    /** When the chain last produced a block, from anyone. */
    public Instant lastBlockAt = Instant.EPOCH;

    /** How many blocks the explorer's strip shows. Two dozen at fourteen minutes is about six hours. */
    public static final int RECENT_BLOCKS = 24;

    /**
     * Heights this rig won, newest last, bounded.
     *
     * <h2>⚠ No blocks are stored, and this list is the reason that works</h2>
     *
     * Every other thing about a block — its hash, its miner, its transactions, their senders and fees
     * — is <b>derived from its height</b> by {@code ChainExplorer}, so any height renders identically
     * every time for no bytes on disk. That is what lets the chain start at height 124 with all 124
     * blocks inspectable, and keep growing without the save growing with it.
     *
     * <p>The one thing that cannot be derived is what was <em>rolled</em>: whether the player won a
     * given block. That is this list. It is bounded because the authoritative record is the ledger —
     * a won block writes a row naming its height — and this is only a fast index for the explorer.
     */
    public java.util.List<Long> blocksWon = new java.util.ArrayList<>();

    /** How many won heights the index keeps. Older wins are still in the ledger. */
    public static final int WON_INDEX = 256;

    /**
     * Every block this character put hashrate into, oldest first, bounded.
     *
     * <p>Wider than {@link #blocksWon} in two directions and narrower in none: it includes blocks the
     * rig's <em>pool</em> won, and it keeps what the world looked like at the time — the allocation,
     * the network hashrate, the difficulty. See {@link ContributionState}, and note that everything
     * derivable from the height deliberately is not stored here.
     */
    public java.util.List<ContributionState> contributions = new java.util.ArrayList<>();

    /** This rig's transactions that no miner has packed into a block yet. */
    public java.util.List<PendingTxState> mempool = new java.util.ArrayList<>();

    /** Transactions sent, ever — a chain nonce, which real explorers show per address. */
    public long nonce = 0L;

    /**
     * Seeds every derived block field on this chain.
     *
     * <p>Set once at genesis and never again. It is what makes a block's hash a stable function of
     * its height: without it, two characters would render the same height with the same hash, and the
     * chain would look like a shared fixture rather than each character's own world.
     */
    public long blockSeed = 0L;

    /**
     * Normalised work the rest of the network has done toward its next block, and the exponential
     * variate it is racing.
     *
     * <p>⚠ Stored as a <b>difficulty-normalised</b> pair — {@code workDone} is measured in units of
     * "expected blocks" and {@code workTarget} is a draw from {@code Exp(1)}. Storing raw hashes
     * would mean rescaling both every time difficulty moved; normalising means a difficulty change
     * simply changes how fast progress accrues, which is exactly what it does on a real chain.
     */
    public double networkWorkDone = 0.0d;

    public double networkWorkTarget = 1.0d;
}
