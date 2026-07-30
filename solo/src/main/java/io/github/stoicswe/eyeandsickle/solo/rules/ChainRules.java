package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainSync;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import io.github.stoicswe.eyeandsickle.solo.state.ChainState;
import io.github.stoicswe.eyeandsickle.solo.state.PendingTxState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
     * One block that is going to pay this rig something, and everything the payer needs to know.
     *
     * <h2>⚠ The height and the fees travel together, and that is not convenience</h2>
     *
     * A block's fee total is a function of its height, so anything that wants to pay it has to know
     * <em>which</em> block it is paying for. {@code Minted} used to carry a count and a summed fee
     * total, which was enough to credit a balance and not enough to write a contributor row — by the
     * time {@code MiningRules} saw it, the heights that earned the money were gone. Carrying the
     * blocks themselves costs a few objects per tick, and a tick produces a block about once every
     * fourteen minutes.
     *
     * @param height the block
     * @param at when it was found — the walked cursor instant, not the tick's
     * @param feesWei what its transactions paid, collected by whoever mined it
     * @param offline whether it landed during the post-logout spin-down window ({@code 04} §1.2)
     */
    public record Won(long height, Instant at, java.math.BigInteger feesWei, boolean offline) {}

    /**
     * What a stretch of chain produced, and how much of it was the player's.
     *
     * @param blocks every block the chain produced, whoever won it
     * @param yourBlocks blocks this rig won outright — solo only, and empty otherwise
     * @param poolBlocks blocks this rig's pool won. ⚠ Populated under <b>both</b> schemes: the rig
     *     contributed hashrate to them either way, which is what the contributor record is about.
     *     Only PPLNS is <em>paid</em> out of them — PPS buys a fixed price per share instead, so it
     *     reads this list to record the contribution and takes no money from it. See
     *     {@code MiningRules.rewardBaseWei}.
     */
    public record Minted(int blocks, List<Won> yourBlocks, List<Won> poolBlocks) {

        public static final Minted NOTHING = new Minted(0, List.of(), List.of());

        /** How many blocks this rig won. */
        public int yours() {
            return yourBlocks.size();
        }

        /** How many its pool won. */
        public int yourPool() {
            return poolBlocks.size();
        }

        /** The fees carried by the blocks in {@link #yourBlocks} — paid on top of the subsidy. */
        public java.math.BigInteger yoursFeesWei() {
            return fees(yourBlocks);
        }

        /** The same for {@link #poolBlocks}. Divided among the pool under PPLNS, ignored under PPS. */
        public java.math.BigInteger yourPoolFeesWei() {
            return fees(poolBlocks);
        }

        private static java.math.BigInteger fees(List<Won> blocks) {
            java.math.BigInteger total = java.math.BigInteger.ZERO;
            for (Won block : blocks) {
                total = total.add(block.feesWei());
            }
            return total;
        }
    }

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
        if (elapsed.isNegative() || elapsed.isZero()) {
            return Minted.NOTHING;
        }
        // Online: the rig is running for the whole interval, so it competes for every block in it.
        return advance(save, now.minus(elapsed), now, now, rng, TICK_BLOCK_LIMIT, false).minted();
    }

    /**
     * What one call to {@link #advance} did, for the two callers that need more than the payouts.
     *
     * @param minted the blocks that are going to pay this rig
     * @param competed how many of them landed while the rig was still hashing. Equal to
     *     {@code minted.blocks()} online; the whole point of the record when filling in an absence.
     * @param retargets difficulty adjustments that closed inside the interval
     * @param confirmed the player's own pending transactions that were mined in it
     * @param truncated whether the block limit stopped the walk short of {@code to}
     */
    private record Advance(
            Minted minted, int competed, int retargets, int confirmed, boolean truncated) {}

    /**
     * The most blocks one tick will produce, so a machine that slept with the client open cannot
     * spin. Surplus work stays on the counter and settles next tick.
     */
    private static final int TICK_BLOCK_LIMIT = 4096;

    /**
     * The one block loop, walked on a time cursor.
     *
     * <h2>⚠ Every block gets its own instant, and stamping them all at {@code to} is a real bug</h2>
     *
     * {@link #retarget} computes {@code expected / actual} from
     * {@code Duration.between(retargetStartedAt, now)}. Stamp every block in the interval at the
     * instant the interval <em>ended</em> and {@code actual} becomes the whole interval, however
     * long it was — so a window that closes twelve hours into an absence is measured as having taken
     * twelve hours instead of its real fourteen, and the adjustment goes straight into the ÷4 clamp.
     * The online path never noticed because it ticks once a second and the error is bounded by one
     * tick; filling in days at a time makes it the difference between a chain and a broken one.
     *
     * <p>So the cursor is walked forward by the time each block actually took, which is
     * {@code (target − done) × mean} seconds — the inverse of the work accrual, and exact rather than
     * a plausible-looking spacing. It also means {@code mean} can be recomputed after a retarget and
     * the rest of the interval runs at the new difficulty, which the fixed-{@code mean} loop this
     * replaced could not do.
     *
     * <p>⚠ The elapsed figure is accumulated as a double and added to {@code from} each time rather
     * than added to the previous cursor. Adding a rounded millisecond count per block would drift by
     * the rounding error times the block count, which over a long fill is minutes.
     *
     * @param competesUntil the last instant the player's own hashrate is in the draw. The load
     *     instant when online; {@code from + } the spin-down window when filling in an absence
     *     (Invariant <b>I5</b> as amended — {@code docs/design/04-mining.md} §1.2).
     * @param limit the most blocks to produce before giving up and leaving the rest on the counter
     * @param offline whether these blocks landed while the client was closed, which the contributor
     *     record keeps and nothing else reads
     */
    private static Advance advance(
            SoloSave save,
            Instant from,
            Instant to,
            Instant competesUntil,
            Rng rng,
            int limit,
            boolean offline) {
        ChainState chain = save.chain;
        double span = millisBetween(from, to) / 1000.0d;
        if (chain == null || span <= 0 || chain.networkHashrate <= 0) {
            return new Advance(Minted.NOTHING, 0, 0, 0, false);
        }

        boolean solo = MiningRules.modeOf(save.rig) == MiningMode.SOLO;
        String poolId = MiningRules.poolOf(save.rig).id();
        List<Won> yours = new ArrayList<>();
        List<Won> poolBlocks = new ArrayList<>();
        int blocks = 0;
        int competed = 0;
        int retargets = 0;
        int confirmed = 0;

        double elapsed = 0.0d;
        double mean = expectedSeconds(chain.difficulty, chain.networkHashrate);
        while (blocks < limit) {
            // How much longer the block currently being mined has to run. Memorylessness is what
            // makes this answerable at all: the outstanding draw is not consumed by the passage of
            // time, only measured against it.
            // Clamped at zero: a hand-edited save can carry more work done than the target it is
            // racing, and a negative remainder would walk the cursor backwards and mint a block
            // dated before its own parent.
            double remaining = Math.max(0.0d, (chain.networkWorkTarget - chain.networkWorkDone) * mean);
            if (!Double.isFinite(remaining) || elapsed + remaining > span) {
                break;
            }
            elapsed += remaining;
            Instant at = from.plusMillis(Math.round(elapsed * 1000.0d));
            long height = chain.height + 1;

            chain.networkWorkDone = 0.0d;
            chain.networkWorkTarget = drawWork(rng);

            // ⚠ The draw happens for every block whatever the mode, so the RNG stream does not
            // depend on how the player is mining. Rng's contract: consumption that varies with what
            // was produced stops a stored seed being a replay.
            boolean competing = !at.isAfter(competesUntil);
            if (competing) {
                competed++;
            }
            String winner = drawWinner(save, rng, solo && competing, offline);
            if (solo && "you".equals(winner)) {
                // ⚠ Read against the height this block is ABOUT to take — recordBlock has not run
                // yet, so chain.height is still the parent. The fee total is a function of height,
                // so reading it a line later would pay the previous block's fees.
                yours.add(new Won(height, at, MempoolRules.blockFeesWei(save, height), offline));
                chain.blocksWon.add(height);
                while (chain.blocksWon.size() > ChainState.WON_INDEX) {
                    chain.blocksWon.removeFirst();
                }
            } else if (!solo && competing && winner.equals(poolId)) {
                poolBlocks.add(new Won(height, at, MempoolRules.blockFeesWei(save, height), offline));
            }

            double before = chain.difficulty;
            recordBlock(chain, at);
            if (chain.difficulty != before) {
                retargets++;
                // The rest of the interval runs at the new difficulty, which is what a retarget is.
                mean = expectedSeconds(chain.difficulty, chain.networkHashrate);
            }
            confirmed += confirm(save, chain.height, at).size();
            blocks++;
        }

        boolean truncated = blocks >= limit;
        if (!truncated) {
            // Whatever is left of the interval is partial progress toward the outstanding draw. It
            // stays on the counter and settles next time, exactly as it did before the cursor.
            chain.networkWorkDone += Math.max(0.0d, span - elapsed) / mean;
        }
        return new Advance(
                new Minted(blocks, yours, poolBlocks), competed, retargets, confirmed, truncated);
    }

    /**
     * Milliseconds between two instants, saturating rather than overflowing.
     *
     * <p>⚠ {@code Duration.toMillis()} throws {@code ArithmeticException} past ~292 million years,
     * and a hand-edited {@code lastPlayedAt} reaches that trivially. This is the one place a save
     * file's own numbers set the size of a loop, so it is the one place that has to be careful about
     * them.
     */
    private static long millisBetween(Instant from, Instant to) {
        try {
            return Duration.between(from, to).toMillis();
        } catch (ArithmeticException absurd) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Picks who won a block, weighted by hashrate.
     *
     * <p>The player is a competitor in their own right only when <b>solo</b>, and only while their
     * rig is actually hashing. When pooled their hashrate is inside their pool's, so drawing them
     * separately would count it twice — the pool would win its full share and the player would win
     * on top of it.
     *
     * <p>⚠ The draw is unconditional and happens once per block whatever the outcome, so the RNG
     * stream does not depend on who won. A generator whose consumption varies with what it produced
     * stops a stored seed being a replay ({@code Rng}). That is also why a rig that has spun down
     * still draws: the blocks it is absent from consume the stream identically to the ones it
     * contested, so an absence does not shift what a later block would have rolled.
     *
     * <p>⚠ A block filled in during a synchronisation weights the player's own share by
     * {@link Balance#OFFLINE_SOLO_WIN_WEIGHT} — see that constant for why. It scales the
     * <b>threshold</b> and never the number of draws, so the stream is byte-identical to what it
     * would have been; the freed probability lands in the unpooled remainder below, because a block
     * this rig did not win was still won by somebody. Pools are untouched: a pool competes whether or
     * not one member's client is open, and each keeps its exact {@code networkShare} either way.
     *
     * @param offline whether this block is being filled in for an absence rather than mined live
     */
    private static String drawWinner(SoloSave save, Rng rng, boolean competing, boolean offline) {
        double roll = rng.nextDouble();
        double you = competing
                ? Math.min(1.0d, hashrate(save.rig.selfMiningCycles) / save.chain.networkHashrate)
                : 0.0d;
        if (offline) {
            you *= Balance.OFFLINE_SOLO_WIN_WEIGHT;
        }
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
    private static List<PendingTxState> confirm(SoloSave save, long height, Instant now) {
        return MempoolRules.confirmInto(save, height, now);
    }

    // ================================================================== catching up

    /**
     * Fills in the blocks the chain made while the client was closed.
     *
     * <h2>The chain does not stop when the client does</h2>
     *
     * Until 2026-07-29 it did: height froze at the last tick and resumed from there, so a character
     * played on Monday and again on Friday found four days of wall-clock time and zero blocks — on
     * the one readout in this game whose whole subject is that nobody owns it and nobody can stop it.
     * {@code docs/design/04-mining.md} §1.3d. A ledger that waits for you is not a decentralised
     * ledger, and this is the most legible possible way to say so.
     *
     * <h2>⚠ The rig competes for the first {@code Balance.OFFLINE_MINING_HOURS} and then stops dead</h2>
     *
     * That is Invariant <b>I5</b> as amended on 2026-07-29. It used to read "self-mining runs
     * online-only"; the cap was always the thing doing the work, because what the rule protects
     * against is <em>absence out-earning play</em> on an income stream that is also zero-heat and
     * unseizable (I4). Past the window a longer absence is worth exactly nothing more, so there is no
     * absence to optimise toward — and play is uncapped, so an hour played always beats an hour away.
     *
     * <p>Blocks past the cap still happen, still have real winners, and still confirm the player's
     * pending transactions. What they do not do is pay.
     *
     * <h2>⚠ Confirming transactions while away is NOT offline income</h2>
     *
     * A broadcast transaction is on the network and gets mined whether or not its sender is watching.
     * The value moved when the ledger row was written; confirmation only stamps it with the height
     * that carried it. A transaction left unconfirmed across a four-day absence would be the lie, and
     * would also mean a player could park money in the mempool to hide it.
     *
     * @param from when the client was last running — {@code save.lastPlayedAt}
     * @param to now
     * @return what happened, for the {@code SYNCHRONIZING} screen. Never null; a fill with nothing to
     *     do reports zero blocks and the screen does not appear.
     */
    public static Sync sync(SoloSave save, Instant from, Instant to, Rng rng) {
        ChainState chain = save.chain;
        if (chain == null || from == null || !from.isBefore(to)) {
            return new Sync(ChainSync.none(to), Minted.NOTHING, Duration.ZERO);
        }
        long away = Duration.between(from, to).getSeconds();
        // ⚠ Capped against the ABSENCE, not to the window. A player away ten minutes mined for ten
        // minutes; a player away a week mined for four hours.
        long mined = Math.min(away, Balance.offlineMiningSeconds());

        long fromHeight = chain.height;
        double difficultyBefore = chain.difficulty;
        Advance walked = advance(
                save,
                from,
                to,
                from.plusSeconds(mined),
                rng,
                Balance.CHAIN_SYNC_BLOCK_LIMIT,
                true);

        ChainSync report = new ChainSync(
                from,
                to,
                away,
                mined,
                fromHeight,
                chain.height,
                walked.minted().blocks(),
                walked.competed(),
                walked.minted().yours(),
                walked.minted().yourPool(),
                // ⚠ Zero here and filled in by the caller. What a block PAYS is MiningRules'
                // question — this class runs the chain and decides who won, and a payout figure
                // computed in two places is two places for it to be computed differently.
                java.math.BigInteger.ZERO,
                walked.retargets(),
                difficultyBefore,
                chain.difficulty,
                walked.confirmed(),
                walked.truncated());
        return new Sync(report, walked.minted(), Duration.ofSeconds(mined));
    }

    /**
     * A completed fill, and the two things the caller still has to settle.
     *
     * <p>The split is the same one {@code tick()} already makes: the chain decides <em>who won</em>
     * and {@code MiningRules} decides <em>what that is worth</em>. {@code minedFor} is how much of
     * the absence to run the share clock over, which is not the same as the absence — a pay-per-share
     * pool accrues across the spin-down window and not one second past it.
     *
     * @param report what to show the player, with {@code creditedWei} still unset
     * @param minted the blocks that are going to pay, for {@code MiningRules.runSelfMining}
     * @param minedFor how long the rig was actually hashing — the capped window, never the absence
     */
    public record Sync(ChainSync report, Minted minted, Duration minedFor) {}

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
