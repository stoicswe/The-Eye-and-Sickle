package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import io.github.stoicswe.eyeandsickle.solo.state.ChainState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;

/**
 * Proof of work, done properly: difficulty, the retarget, and the exponential wait.
 *
 * <h2>The three equations, and they are the real ones</h2>
 *
 * <pre>
 *   expected hashes per block   = difficulty × 2^32
 *   expected seconds to a block = difficulty × 2^32 / hashrate
 *   difficulty holding interval = interval × networkHashrate / 2^32
 * </pre>
 *
 * The first is Bitcoin's, exactly — its difficulty-1 target makes the expected hash count
 * {@code difficulty × 2^48 / 0xffff}, which is {@code difficulty × 2^32} to within one part in
 * 65 536. The second is the first divided by a rate. The third is the second rearranged to solve for
 * the difficulty that holds a chosen block interval, which is what a retarget does.
 *
 * <p>Keeping the real constants means the arithmetic in {@code docs/education/07} checks out against
 * a live block explorer. Inventing a tidier constant would have made every worked example in the
 * curriculum false, and {@code CLAUDE.md} treats teaching something false as worse than teaching
 * nothing.
 *
 * <h2>⚠ Every wait here is drawn, never scheduled</h2>
 *
 * {@link #drawWork} returns a sample from {@code Exp(1)} by inverse transform: if {@code U} is
 * uniform on (0,1] then {@code -ln U} is exponential with mean 1. Progress is then measured in
 * <em>expected blocks</em> rather than in hashes, so a difficulty change re-rates the accrual instead
 * of invalidating the outstanding draw — which is what makes the pool's vardiff retune (and the
 * player's own reallocation) free of both penalty and exploit.
 *
 * <p>A scheduled interval with noise added would look identical for about ten minutes and then
 * diverge: it would have a bounded tail, it would not be memoryless, and the pooled-versus-solo
 * choice — which is <em>entirely</em> a choice about the shape of that tail — would stop meaning
 * anything.
 */
public final class ChainRules {

    private ChainRules() {}

    /** Builds the chain a new character joins, already at a plausible height. */
    public static ChainState genesis(Instant now, Rng rng) {
        ChainState chain = new ChainState();
        chain.networkHashrate = Balance.chainNetworkHashrate();
        chain.difficulty = Balance.chainDifficultyFor(chain.networkHashrate);
        // Joining a chain with a past rather than at block zero. A player who installs a wallet today
        // does not start a new blockchain, and a height of 0 would say this one had been waiting for
        // them — which is the opposite of what the fiction wants from a decentralised ledger. All 124
        // are inspectable in the explorer, because every field of a block is derived from its height.
        chain.height = Balance.CHAIN_START_HEIGHT;
        chain.blocksSinceRetarget = chain.height % Balance.CHAIN_RETARGET_BLOCKS;
        chain.retargetStartedAt = now.minusSeconds(chain.blocksSinceRetarget * Balance.CHAIN_TARGET_BLOCK_SECONDS);
        chain.lastBlockAt = now;
        chain.networkWorkTarget = drawWork(rng);
        chain.blockSeed = rng.nextLong();
        return chain;
    }

    /**
     * A draw from {@code Exp(1)}: the number of <em>expected</em> blocks of work this one will take.
     *
     * <p>Inverse transform sampling. {@code u} is clamped off zero because {@code ln(0)} is negative
     * infinity, which would be a block that can never be found — a one-in-2^53 hang rather than a
     * one-in-2^53 unlucky streak.
     */
    public static double drawWork(Rng rng) {
        double u = Math.max(1e-12d, rng.nextDouble());
        return -Math.log(u);
    }

    /** Mean seconds to a payout at this hashrate against this difficulty. */
    public static double expectedSeconds(double difficulty, double hashrate) {
        if (hashrate <= 0 || difficulty <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return difficulty * Balance.HASHES_PER_DIFFICULTY / hashrate;
    }

    /**
     * The share difficulty a pool would set for this rig — <b>vardiff</b>.
     *
     * <p>A pool gives each miner a target scaled to that miner's own hashrate so shares arrive at a
     * steady pace whatever the rig: an easy target for a small miner, a hard one for a large miner,
     * a share every thirty seconds from both. It is why pooling smooths a 10-cycle rig's income as
     * well as a 100-cycle rig's, which a single fixed share target would not do.
     */
    public static double shareDifficulty(double hashrate) {
        if (hashrate <= 0) {
            return 0.0d;
        }
        return Balance.POOL_SHARE_SECONDS * hashrate / Balance.HASHES_PER_DIFFICULTY;
    }

    /** A rig's hashrate, in hashes per second. */
    public static long hashrate(long cycles) {
        return Math.max(0L, cycles) * Balance.HASHES_PER_CYCLE_SECOND;
    }

    /**
     * What a stretch of chain produced, and how much of it was the player's.
     *
     * @param yoursFeesMinorUnits the fees carried by the blocks in {@link #yours} — paid to the
     *     miner on top of the subsidy, exactly as on a real chain. Summed here rather than
     *     recomputed later because {@link #yours} is only a count, and by the time
     *     {@code MiningRules} sees it the heights that earned it are no longer identifiable.
     * @param yourPoolFeesMinorUnits the same for the blocks in {@link #yourPool}. Divided among the
     *     pool under PPLNS and <b>ignored under PPS</b>, which buys a fixed price per share rather
     *     than a share of what a block happened to carry — see {@code MiningRules.rewardBase}.
     */
    public record Minted(
            int blocks,
            int yours,
            int yourPool,
            long yoursFeesMinorUnits,
            long yourPoolFeesMinorUnits) {}

    /**
     * Runs the chain forward and decides who won each block.
     *
     * <h2>⚠ Blocks are WON, not raced — and that is a deliberate change from 2026-07-27</h2>
     *
     * The chain produces a block roughly every fourteen minutes, and at each one a single draw picks
     * the winner with probability equal to their share of the hashrate. That is the standard
     * formulation of mining and it is exactly equivalent in expectation to every miner racing their
     * own exponential: a miner with 5% of the network wins 5% of the blocks either way.
     *
     * <p>It replaced the race because it is <b>legible</b>. Every block now has a winner, which is a
     * field a block explorer can show, and "the chance you get this block is your share of the
     * chain" is a sentence a player can check against the readout. The race said the same thing and
     * said it nowhere.
     *
     * <p>⚠ <b>Memorylessness survives intact.</b> The player's wait is now a geometric number of
     * blocks rather than an exponential time, and the geometric is the discrete memoryless
     * distribution: losing forty blocks in a row tells you nothing about the forty-first. Nothing
     * accumulates, nothing is owed, and there is still no progress to draw.
     *
     * <p>⚠ Pay-per-share pools are <b>not</b> settled here. A PPS miner is paid per accepted share
     * whether or not anybody's pool found anything, which is the entire product they are buying; it
     * runs on its own share clock in {@code MiningRules}.
     */
    public static Minted advanceNetwork(SoloSave save, Duration elapsed, Instant now, Rng rng) {
        ChainState chain = save.chain;
        double seconds = elapsed.toMillis() / 1000.0d;
        if (chain == null || seconds <= 0 || chain.networkHashrate <= 0) {
            return new Minted(0, 0, 0, 0L, 0L);
        }
        double mean = expectedSeconds(chain.difficulty, chain.networkHashrate);
        chain.networkWorkDone += seconds / mean;

        int blocks = 0;
        int yours = 0;
        int yourPool = 0;
        long yoursFees = 0L;
        long yourPoolFees = 0L;
        boolean solo = MiningRules.modeOf(save.rig) == MiningMode.SOLO;
        String poolId = MiningRules.poolOf(save.rig).id();
        // Bounded so a machine that slept with the client open cannot spin. Surplus work stays on
        // the counter and settles next tick.
        while (chain.networkWorkDone >= chain.networkWorkTarget && blocks < 4096) {
            chain.networkWorkDone -= chain.networkWorkTarget;
            chain.networkWorkTarget = drawWork(rng);

            // ⚠ The draw happens for every block whatever the mode, so the RNG stream does not
            // depend on how the player is mining. Rng's contract: consumption that varies with what
            // was produced stops a stored seed being a replay.
            String winner = drawWinner(save, rng, solo);
            boolean mine = solo && "you".equals(winner);
            if (mine) {
                yours++;
                // ⚠ Read against the height this block is ABOUT to take — recordBlock has not run
                // yet, so chain.height is still the parent. The fee total is a function of height,
                // so reading it a line later would pay the previous block's fees.
                yoursFees += MempoolRules.blockFeesMinorUnits(save, chain.height + 1);
                chain.blocksWon.add(chain.height + 1);
                while (chain.blocksWon.size() > ChainState.WON_INDEX) {
                    chain.blocksWon.removeFirst();
                }
            } else if (!solo && winner.equals(poolId)) {
                yourPool++;
                // Same height caveat as above: recordBlock has not run yet.
                yourPoolFees += MempoolRules.blockFeesMinorUnits(save, chain.height + 1);
            }
            recordBlock(chain, now);
            confirm(save, chain.height, now);
            blocks++;
        }
        return new Minted(blocks, yours, yourPool, yoursFees, yourPoolFees);
    }

    /**
     * Picks who won a block, weighted by hashrate.
     *
     * <p>The player is a competitor in their own right only when <b>solo</b>. When pooled their
     * hashrate is inside their pool's, so drawing them separately would count it twice — the pool
     * would win its full share and the player would win on top of it.
     *
     * <p>⚠ The draw is unconditional and happens once per block whatever the outcome, so the RNG
     * stream does not depend on who won. A generator whose consumption varies with what it produced
     * stops a stored seed being a replay ({@code Rng}).
     */
    private static String drawWinner(SoloSave save, Rng rng, boolean solo) {
        double roll = rng.nextDouble();
        double you = solo
                ? Math.min(1.0d, hashrate(save.rig.selfMiningCycles) / save.chain.networkHashrate)
                : 0.0d;
        if (roll < you) {
            return "you";
        }
        double at = you;
        for (MiningPool pool : Pools.all()) {
            at += pool.networkShare();
            if (roll < at) {
                return pool.id();
            }
        }
        // The remainder: everyone mining alone who is not this player. Pools.all() comes to 93%, so
        // there is a real unpooled population rather than a rounding artefact.
        return "unpooled";
    }

    /**
     * Packs the player's pending transactions into a block, highest fee rate first.
     *
     * <h2>⚠ The player's transactions compete with the NPC mempool, they do not bypass it</h2>
     *
     * A block holds {@code BLOCK_TRANSACTION_LIMIT} transactions and the NPC mempool runs deeper than
     * that, so a slot has to be won on fee rate. {@code MempoolRules.confirmable} works out how many
     * of the block's slots this rig's transactions actually reach given what everyone else is paying —
     * which is what makes {@link io.github.stoicswe.eyeandsickle.protocol.game.FeeTier} mean anything.
     * Confirming the player's transactions unconditionally would have made the fee a cosmetic choice
     * and the mempool a decoration.
     */
    private static void confirm(SoloSave save, long height, Instant now) {
        MempoolRules.confirmInto(save, height, now);
    }

    /** Adds one block to the chain and retargets if that closed the window. */
    public static void recordBlock(ChainState chain, Instant now) {
        chain.height++;
        chain.blocksSinceRetarget++;
        chain.lastBlockAt = now;
        if (chain.blocksSinceRetarget >= Balance.CHAIN_RETARGET_BLOCKS) {
            retarget(chain, now);
        }
    }

    /**
     * Recalculates difficulty from how long the last window actually took.
     *
     * <p>The real rule: {@code newDifficulty = oldDifficulty × expectedTime / actualTime}, clamped so
     * one adjustment can never move it by more than a factor of four. A window that ran fast means
     * hashrate arrived and difficulty must rise; a slow window means the opposite. The clamp is what
     * stops a hashrate collapse stranding the chain — without it a network that lost 99% of its
     * miners would need a window that takes a hundred times as long before it could correct, and the
     * correction is the thing that would never arrive.
     *
     * <p>⚠ With this game's fixed network hashrate the adjustment averages 1.0, so difficulty has no
     * <em>trend</em> — but it is not constant. 1440 random block times have a spread of about
     * {@code 1/√1440 ≈ 2.6%}, so each retarget moves difficulty by a couple of percent either way.
     * The absent thing is the trend, which is what a growing network supplies. See {@code ChainState}.
     */
    public static void retarget(ChainState chain, Instant now) {
        long expected = Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
        long actual = Math.max(1L, Duration.between(chain.retargetStartedAt, now).toSeconds());
        double adjustment = expected / (double) actual;
        adjustment = Math.max(1.0d / Balance.CHAIN_RETARGET_CLAMP,
                Math.min(Balance.CHAIN_RETARGET_CLAMP, adjustment));
        chain.difficulty = Math.max(1e-9d, chain.difficulty * adjustment);
        chain.blocksSinceRetarget = 0L;
        chain.retargetStartedAt = now;
    }

    /** Blocks left in the current retarget window. */
    public static long blocksUntilRetarget(ChainState chain) {
        return Math.max(0L, Balance.CHAIN_RETARGET_BLOCKS - chain.blocksSinceRetarget);
    }
}
