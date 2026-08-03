package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One block this character put hashrate into, as the save stores it.
 *
 * <h2>⚠ Only what was ROLLED is here. Everything else derives from the height.</h2>
 *
 * The same discipline as {@link ChainState#blocksWon}, for the same reason: a block's transaction
 * count, its fee total and its subsidy are stable functions of its height, so storing them would be
 * caching a value that can be recomputed — and a cache of game state eventually disagrees with the
 * game state, on the exact surface a player uses to decide whether a readout has been tampered with.
 * {@code ChainExplorer} derives them for the block card; the contributor row derives them the same
 * way from the same place, so the two cannot come apart.
 *
 * <p>What genuinely cannot be recomputed is what the world looked like <em>at the time</em> — the
 * rig's allocation, the network's hashrate, the difficulty — and what the player was actually
 * credited, which carries a rounding residue that is not a pure function of anything. Those are
 * fields.
 *
 * <h2>Why this is not just {@code blocksWon} widened</h2>
 *
 * {@code blocksWon} answers "did this rig mine that block", which the explorer asks for every height
 * on screen, so it is a bounded index of bare longs and is kept that way. This answers "where has my
 * hashrate gone", which is a history — it includes blocks the rig's *pool* won, and blocks that paid
 * nothing at all under pay-per-share.
 */
public final class ContributionState {

    /** The block. Everything derived is derived from this. */
    public long height;

    /** When it was found. */
    public Instant at = Instant.EPOCH;

    /** {@code SOLO} or {@code POOLED} — the {@code MiningMode} name. */
    public String mode = "POOLED";

    /** {@code SOLO}, {@code PPS} or {@code PPLNS} — how this row was (or was not) paid. */
    public String scheme = "PPS";

    /** The pool, or empty when solo. Stored by id, so a renamed pool re-renders correctly. */
    public String poolId = "";

    /** True when this rig itself mined the block, as against its pool doing so. */
    public boolean won;

    /**
     * True when the block landed during the post-logout spin-down window.
     *
     * <p>Stored rather than inferred from {@code at}, because the window is defined against the
     * session that had just ended and a save carries no record of past session boundaries. It is the
     * one piece of evidence a player has that Invariant I5's cap is doing what it says.
     */
    public boolean offline;

    /** What this rig was contributing, in hashes per second, when the block was found. */
    public long hashrate;

    /** The whole chain's hashrate at the time. Stored: it is a balance value that may be re-tuned. */
    public long networkHashrate;

    /** The difficulty the block was mined against — it moves at every retarget. */
    public double difficulty;

    /**
     * What actually reached this character from this block, in minor units.
     *
     * <p>Zero is a real and correct value under pay-per-share, which pays a fixed price per accepted
     * share out of the pool's own balance rather than dividing up a block. See
     * {@code MiningRules.rewardBaseWei}.
     */
    public BigInteger creditedWei = BigInteger.ZERO;

    /**
     * How many rows the history keeps.
     *
     * <p>Generous, because the rows are small and the tab is a history rather than an index. At a
     * PPLNS pool holding 32% of the chain a block lands about every 44 minutes, so this is roughly
     * 750 hours of play — and the ledger remains the authoritative record of everything that was
     * actually paid, exactly as it is for {@code ChainState.blocksWon}.
     */
    public static final int LIMIT = 1024;
}
