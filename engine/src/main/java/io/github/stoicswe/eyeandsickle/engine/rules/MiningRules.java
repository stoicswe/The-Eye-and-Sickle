package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.PoolScheme;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Pools;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.state.ChainState;
import io.github.stoicswe.eyeandsickle.engine.state.ContributionState;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.RigState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * Income: the safe floor, and the risky ceiling.
 *
 * <h2>The two sources are deliberately asymmetric</h2>
 *
 * <b>Self-mining</b> is the income floor and is protected by two invariants: it generates zero heat
 * and cannot be detected or seized (I4), and it stops a bounded time after the client closes (I5).
 * It is boring on purpose — it is what stops a run from becoming unrecoverable.
 *
 * <p><b>Deployed miners</b> are the <em>volume</em> offline income, and every one of them costs the
 * deployer a permanent control channel while charging its actual work to the <em>host</em> (I6).
 * Their yield accrues into an on-host buffer that stops dead at a cap, so time away is worth
 * something but not proportionally — and the buffer is the prize somebody takes when they crack the
 * miner.
 *
 * <p>⚠ <b>I5 was amended on 2026-07-29 and the two are no longer separated by "offline".</b> The rig
 * keeps hashing for {@code Balance.OFFLINE_MINING_HOURS} after the client closes and then stops dead,
 * so both streams now pay across a bounded absence. What still separates them is <b>exposure and
 * volume</b>: a deployed miner spends somebody else's compute, so five of them buffer five hosts'
 * worth of the same window — and a miner's buffer sits on a machine an attacker can reach, where
 * self-mining cannot be touched. Had the distinction ever rested on "one works offline and one does
 * not", this change would have deleted it. See {@code docs/design/04-mining.md} §1.2.
 *
 * <h2>Self-mining is a real proof-of-work simulation since 2026-07-27</h2>
 *
 * It used to be a rate: cycles in, ethecoin out, linearly. It is now a Poisson process against a real
 * difficulty ({@link ChainRules}), read at one of two difficulties:
 *
 * <ul>
 *   <li><b>Pooled</b> — mining against a share target the pool retunes to this rig, paid a fixed
 *       amount per accepted share whether or not the pool found a block. This is pay-per-share, and
 *       the fee is what the pool charges for absorbing the variance. Income is near-constant.
 *   <li><b>Solo</b> — mining against the full network difficulty, paid the whole block subsidy or
 *       nothing at all. Roughly 470× the variance and, because there is no fee, about 2% more in
 *       expectation.
 * </ul>
 *
 * <p>⚠ <b>Deployed miners are deliberately NOT converted.</b> They are pooled by construction — a
 * buffer that fills at a rate, capped, collected on a visit — and giving them a lottery would put
 * variance on the one income stream the player cannot watch, cannot react to, and collects hours
 * later in a lump anyway. It would also break {@code 04} §5.1's crack timing bet, which is priced on
 * "payout scales with buffer fullness": a buffer that filled in jumps would make "found at minute
 * five, it holds almost nothing" false about half the time. Bots are unchanged for the same reason.
 */
public final class MiningRules {

    private MiningRules() {}

    /**
     * Deployed-miner yield for an elapsed interval, in minor units.
     *
     * <p>Integral throughout: fractional minor units would accumulate rounding differences between a
     * session played in one sitting and the same session played in ten, which is exactly the kind of
     * bug nobody reports and everybody feels.
     *
     * <p>⚠ This is the <b>flat</b> rate, and it is deliberately still flat. Self-mining moved to a
     * proof-of-work simulation on 2026-07-27; deployed miners did not, for the reasons in this
     * class's comment. The shared constant is not laziness — a deployed miner earns the same
     * 0.4 EC per cycle-hour because {@code docs/design/03-economy.md} §1 prices both against it, and
     * the pooled self-mining rate is calibrated to land on exactly this figure.
     */
    public static BigInteger deployedYield(long allocatedCycles, Duration elapsed) {
        if (allocatedCycles <= 0 || elapsed.isNegative() || elapsed.isZero()) {
            return BigInteger.ZERO;
        }
        // cycles × (wei per cycle-hour) × hours, done in seconds to avoid truncating short
        // sessions to zero.
        long seconds = elapsed.toSeconds();
        // ⚠ Exact integer arithmetic. At 18 decimals this product passes a long within seconds of
        // one cycle mining, so narrowing it buys nothing and costs the low digits.
        return Balance.SELF_MINING_WEI_PER_CYCLE_HOUR
                .multiply(BigInteger.valueOf(allocatedCycles))
                .multiply(BigInteger.valueOf(seconds))
                .divide(BigInteger.valueOf(3600L));
    }

    /** The mode a rig is mining in, tolerant of a save that predates the field or was hand-edited. */
    public static MiningMode modeOf(RigState rig) {
        try {
            return MiningMode.valueOf(rig.miningMode);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            // Pooled, because it is the safe one. A save that fell back into the lottery without
            // saying so would be the worst possible reading of an unrecognised value.
            return MiningMode.POOLED;
        }
    }

    /** The pool this rig mines with, or the default if the save names one that is gone. */
    public static MiningPool poolOf(RigState rig) {
        return Pools.byId(rig.miningPoolId == null ? Pools.DEFAULT_ID : rig.miningPoolId);
    }

    /**
     * What one payout is worth <em>as a fraction of a block</em> — the one number that varies.
     *
     * <h2>⚠ Solo, PPS and PPLNS are the same equation with three different fractions</h2>
     *
     * Every mode pays {@code subsidy × fraction × (1 - fee)} at intervals of
     * {@code difficulty × fraction × 2^32 / hashrate}. Multiply those out and the fraction cancels:
     * expected income is {@code subsidy × hashrate × (1 - fee) / (difficulty × 2^32)} in <b>all</b>
     * of them. That identity is the entire design — <b>only the fee changes what you earn; the
     * fraction changes only how lumpily you earn it</b> — and writing the three as one function is
     * what stops them drifting apart the next time one is tuned.
     *
     * <ul>
     *   <li><b>Solo</b> — 1. A whole block or nothing.
     *   <li><b>PPS</b> — the fraction of a block that one share represents, chosen so shares land
     *       every {@code shareSeconds}. This is vardiff, and because it is defined by a target
     *       <em>time</em> it smooths a small rig exactly as well as a large one.
     *   <li><b>PPLNS</b> — this rig's share of the pool. You are paid when the <em>pool</em> finds a
     *       block, so the interval is the pool's block interval and <b>pool size is the variance
     *       knob</b>: 5% of the chain is a payout every three hours.
     * </ul>
     *
     * <p>Clamped to 1: a rig that grew past its own PPLNS pool's hashrate would otherwise be owed
     * more than a block. In that situation you are simply most of the pool, and a block is all there
     * is to divide.
     */
    public static double payoutFraction(RigState rig, ChainState chain) {
        long hashrate = ChainRules.hashrate(rig.selfMiningCycles);
        if (hashrate <= 0 || chain.difficulty <= 0) {
            return 0.0d;
        }
        if (modeOf(rig) == MiningMode.SOLO) {
            return 1.0d;
        }
        MiningPool pool = poolOf(rig);
        double fraction = pool.scheme() == PoolScheme.PPLNS
                ? hashrate / Math.max(1.0d, chain.networkHashrate * pool.networkShare())
                : pool.shareSeconds() * hashrate / (chain.difficulty * Balance.HASHES_PER_DIFFICULTY);
        return Math.min(1.0d, fraction);
    }

    /** The fee that applies right now: the pool's, or none at all when solo. */
    public static double feeOf(RigState rig) {
        return modeOf(rig) == MiningMode.SOLO ? 0.0d : poolOf(rig).fee();
    }

    /** The difficulty this rig is actually racing — the network's, scaled by what a payout is worth. */
    public static double workingDifficulty(RigState rig, ChainState chain) {
        return chain.difficulty * payoutFraction(rig, chain);
    }

    /**
     * The block reward this mode is paid out of — and the one place PPS differs from PPLNS.
     *
     * <h2>⚠ PPLNS passes fees on; classic PPS does not. That is not an oversight.</h2>
     *
     * PPLNS pays out of blocks the pool <em>actually won</em>, so whatever those blocks carried in
     * fees is part of what there is to divide. Classic <b>pay-per-share</b> buys something different:
     * the pool pays a fixed price per accepted share whether or not anybody found a block at all,
     * which is the entire product — and a fixed price cannot depend on the fees of a block that may
     * not exist. Pools that <em>do</em> pass fees through under a share model are called <b>PPS+</b>
     * precisely because it is a different product with a different name.
     *
     * <p>So this is a real axis rather than a tuning knob: since 2026-07-27 the roster trades
     * <b>fee exposure</b> as well as fee percentage and variance. A PPS miner takes 10.55% less
     * expected income for a payout that cannot miss; a PPLNS miner takes the block's luck in both
     * directions. Solo takes all of it.
     */
    public static BigInteger rewardBaseWei(RigState rig) {
        boolean passesFeesOn = modeOf(rig) == MiningMode.SOLO || poolOf(rig).scheme() == PoolScheme.PPLNS;
        return Balance.BLOCK_SUBSIDY_WEI.add(passesFeesOn ? Balance.expectedBlockFeesWei() : BigInteger.ZERO);
    }

    /**
     * What one payout is worth, in exact (possibly fractional) minor units.
     *
     * <p>See {@link #payoutFraction}: a share, a PPLNS cut and a whole block are one expression.
     *
     * <p>⚠ Uses the <b>expected</b> fee total, so this is the long-run rate the UI publishes and the
     * price of a PPS share. The actual credit for a solo or PPLNS block uses that block's real fees,
     * which {@code ChainRules.Minted} carries — a published expectation and a realised payout are
     * different questions and only the second one can know which block it was.
     */
    public static BigDecimal payoutWei(RigState rig, ChainState chain) {
        // ⚠ The money is BigDecimal; the two multipliers stay doubles and that is correct. A payout
        // fraction and a pool fee are pure RATIOS with no scale in them, so a double carries them
        // exactly as well as anything else. What must not be a double is the wei amount they scale.
        return new BigDecimal(rewardBaseWei(rig))
                .multiply(BigDecimal.valueOf(payoutFraction(rig, chain)))
                .multiply(BigDecimal.valueOf(1.0d - feeOf(rig)));
    }

    /** The long-run rate, in wei per hour. Equal in both modes but for the pool's fee. */
    public static BigInteger expectedWeiPerHour(RigState rig, ChainState chain) {
        double seconds =
                ChainRules.expectedSeconds(workingDifficulty(rig, chain), ChainRules.hashrate(rig.selfMiningCycles));
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return BigInteger.ZERO;
        }
        // ⚠ The wei amount stays in BigDecimal all the way to the rounding. `payout * 3600 / seconds`
        // in double would round the amount to a double's 15-16 significant digits first, and at this
        // scale that lands squarely inside the digits the readout now prints.
        return payoutWei(rig, chain)
                .multiply(BigDecimal.valueOf(3600.0d))
                .divide(BigDecimal.valueOf(seconds), java.math.MathContext.DECIMAL128)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toBigIntegerExact();
    }

    /**
     * How loud this rig is for being pooled, in cycle-equivalents. Zero when solo or idle.
     *
     * <p>See {@code Balance.POOL_SHARE_NOISE_CYCLES} for why pooled mining is audible, why solo is
     * not, and why Invariant I4 survives both. Scaled by how often the pool wants shares, floored at
     * one so a slow pool is quiet rather than free.
     */
    public static long poolNoiseCycles(RigState rig) {
        if (rig.selfMiningCycles <= 0 || modeOf(rig) == MiningMode.SOLO) {
            return 0L;
        }
        double shareSeconds = Math.max(1.0d, poolOf(rig).shareSeconds());
        return Math.max(1L, Math.round(Balance.POOL_SHARE_NOISE_CYCLES * Balance.POOL_SHARE_SECONDS / shareSeconds));
    }

    /**
     * What a hypothetical allocation would earn per hour, in minor units.
     *
     * <p>Exists so the client can price the slider it is dragging without owning the rule. Expected
     * income is linear in cycles, so this is a scale — but it is a scale of a <b>balance value</b>,
     * and a view that did the multiplication itself would be the fourth copy of a rate that has
     * already been wrong once (see {@code RigStatus}).
     */
    public static BigInteger rateFor(RigState rig, ChainState chain, long cycles) {
        if (chain == null || cycles <= 0) {
            return BigInteger.ZERO;
        }
        long was = rig.selfMiningCycles;
        try {
            rig.selfMiningCycles = cycles;
            return expectedWeiPerHour(rig, chain);
        } finally {
            // Restored unconditionally. This mutates live state to reuse one equation rather than
            // maintaining a second copy of it, and a throw between the two would leave the rig
            // mining an allocation the player never asked for.
            rig.selfMiningCycles = was;
        }
    }

    /**
     * Runs the rig's mining for an elapsed interval and credits whatever landed.
     *
     * <h2>⚠ Called from tick(), and from resume() with a CAPPED elapsed — never with the absence</h2>
     *
     * Invariant <b>I5</b> as amended on 2026-07-29: the rig keeps hashing for
     * {@code Balance.OFFLINE_MINING_HOURS} after the client closes and then stops dead. The resume
     * path therefore passes {@code min(away, that window)} as {@code elapsed}, and
     * {@code ChainRules.sync} has already excluded the player from the draw on every block past it,
     * so the two halves agree by construction rather than by both remembering to clamp.
     *
     * <p>⚠ Passing the raw absence here would break the invariant <b>silently and only for
     * pay-per-share</b>, which runs on its own share clock and would happily accrue a week of shares
     * off a single {@code elapsed}. Solo and PPLNS would look correct, because their payouts come
     * from {@code minted} and the chain already capped that.
     *
     * <p>Memorylessness is what makes the cap clean rather than punitive: a player who logs off is
     * not abandoning progress, because there is none to abandon.
     *
     * <h2>Two clocks, because two things are being paid for</h2>
     *
     * <ul>
     *   <li><b>Solo</b> and <b>PPLNS</b> are paid out of <em>blocks</em>, so their payouts arrive
     *       with {@code minted} — the chain decided who won and this credits it. Nothing accrues
     *       here at all.
     *   <li><b>PPS</b> is paid per accepted share whether or not anybody found a block, which is
     *       exactly the product a PPS miner buys. It runs on its own share clock below and is
     *       deliberately indifferent to what the chain did this tick.
     * </ul>
     *
     * @param minted what the chain produced this tick, from {@code ChainRules.advanceNetwork}
     * @return minor units credited, which the caller ledgers
     */
    public static BigInteger runSelfMining(
            GameSave save, Duration elapsed, Instant now, Rng rng, ChainRules.Minted minted) {
        RigState rig = save.rig;
        ChainState chain = save.chain;
        double seconds = elapsed.toMillis() / 1000.0d;
        if (chain == null || rig.selfMiningCycles <= 0 || seconds <= 0) {
            return BigInteger.ZERO;
        }
        boolean solo = modeOf(rig) == MiningMode.SOLO;
        int payouts = 0;

        if (solo) {
            // ⚠ Subsidy PLUS the block's fees, which is what a real miner is paid. Until 2026-07-27
            // the fees players paid into the mempool were debited and then vanished, so the fee
            // market was a pure sink and the block card's "fees 0.38 EC" named money nobody ever
            // received. See MempoolRules.blockFeesWei for the ~10.6% this adds and where the
            // economy absorbs it.
            //
            // ⚠ Banked block by block rather than as one sum, so each block's own credit is known
            // and can be written to the contributor record. The total is bit-for-bit identical —
            // floor(r+a+b) == floor(r+a) + floor(r+a-floor(r+a)+b) — so this is an attribution
            // change and not an economy one.
            for (ChainRules.Won block : minted.yourBlocks()) {
                BigDecimal value = new BigDecimal(Balance.BLOCK_SUBSIDY_WEI.add(block.feesWei()));
                record(save, block, bank(rig, value), true);
                payouts++;
            }
        } else if (poolOf(rig).scheme() == PoolScheme.PPLNS) {
            // Paid out of blocks the POOL won, in proportion to what this rig contributed to it —
            // including their fees, because a block's fees are part of what the pool has to divide.
            // ⚠ The REAL fees of the blocks that were won, not payoutWei' expectation: this
            // is a realised payout, and a PPLNS miner's exposure to what a block happened to carry
            // is the thing that distinguishes it from PPS.
            double cut = payoutFraction(rig, chain) * (1.0d - feeOf(rig));
            for (ChainRules.Won block : minted.poolBlocks()) {
                BigDecimal value = new BigDecimal(Balance.BLOCK_SUBSIDY_WEI.add(block.feesWei()))
                        .multiply(BigDecimal.valueOf(cut));
                record(save, block, bank(rig, value), false);
                payouts++;
            }
        } else {
            // ⚠ PPS records the pool's blocks and takes NOTHING from them. The rig's hashrate really
            // did go into them, so they belong in the contributor record; a share pool simply is not
            // dividing a block up — it pays a fixed price per accepted share out of its own balance,
            // which is the entire product. See rewardBaseWei. A zero in that column beside a
            // real hashrate is the difference between the two schemes, rendered.
            for (ChainRules.Won block : minted.poolBlocks()) {
                record(save, block, BigInteger.ZERO, false);
            }
            // PPS: a share clock, independent of the chain. Progress in units of "expected shares",
            // so the pool retuning this rig's target — or the player reallocating mid-flight —
            // re-rates the accrual instead of invalidating the draw.
            double mean = ChainRules.expectedSeconds(
                    workingDifficulty(rig, chain), ChainRules.hashrate(rig.selfMiningCycles));
            if (!Double.isFinite(mean) || mean <= 0) {
                return BigInteger.ZERO;
            }
            rig.miningWorkDone += seconds / mean;
            while (rig.miningWorkDone >= rig.miningWorkTarget && payouts < 4096) {
                rig.miningWorkDone -= rig.miningWorkTarget;
                rig.miningWorkTarget = ChainRules.drawWork(rng);
                bank(rig, payoutWei(rig, chain));
                payouts++;
            }
        }

        if (payouts > 0) {
            rig.miningPendingPayouts += payouts;
            rig.miningPayouts += payouts;
            rig.miningLastPayoutAt = now;
        }
        return settle(rig, now, solo);
    }

    /**
     * Moves one payout into the pool's internal balance and reports what whole units it added.
     *
     * <h2>⚠ The residue is carried, never truncated</h2>
     *
     * A share is worth about 33.3 minor units and dropping the third would skim roughly 40 EC per
     * hundred hours — invisible per payout and a real leak at 120 payouts an hour.
     *
     * <h2>⚠ Banking per payout rather than per tick is an ATTRIBUTION change, not an economy one</h2>
     *
     * {@code floor(r + a + b) == floor(r + a) + floor(r + a − floor(r + a) + b)} for any non-negative
     * {@code a}, {@code b}, so a tick that lands two blocks banks exactly what it banked when the two
     * were summed first. What it buys is a per-block figure to write into the contributor record —
     * and one that sums to the ledger row, which a separately-rounded display figure would not.
     *
     * @return the whole minor units this payout added, which is what the contributor row records
     */
    private static BigInteger bank(RigState rig, BigDecimal value) {
        rig.miningResidueWei = rig.miningResidueWei.add(value);
        BigInteger banked =
                rig.miningResidueWei.setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
        rig.miningResidueWei = rig.miningResidueWei.subtract(new BigDecimal(banked));
        rig.miningPendingWei = rig.miningPendingWei.add(banked);
        return banked;
    }

    /**
     * Writes one block into the contributor record.
     *
     * <h2>⚠ Called for blocks that paid nothing, and that is the point</h2>
     *
     * Under pay-per-share the rig's hashrate goes into every block its pool finds and none of those
     * blocks pay it — the pool pays a fixed price per accepted share out of its own balance instead.
     * Recording only the blocks that paid would make PPS look like a mode that mines nothing, and
     * would delete the one surface where the difference between the two schemes is visible.
     *
     * <p>Only what was <b>rolled</b> is stored. The transaction count, the fee total and the subsidy
     * are stable functions of the height and are derived at read time from the same place
     * {@code ChainExplorer} derives them for the block card, so a contributor row and the card it
     * names cannot come apart. See {@code ContributionState}.
     */
    private static void record(GameSave save, ChainRules.Won block, BigInteger credited, boolean won) {
        ChainState chain = save.chain;
        if (chain == null) {
            return;
        }
        ContributionState row = new ContributionState();
        row.height = block.height();
        row.at = block.at();
        row.offline = block.offline();
        row.won = won;
        row.mode = modeOf(save.rig).name();
        row.scheme = won ? "SOLO" : poolOf(save.rig).scheme().name();
        row.poolId = won ? "" : poolOf(save.rig).id();
        row.hashrate = ChainRules.hashrate(save.rig.selfMiningCycles);
        row.networkHashrate = Math.round(chain.networkHashrate);
        row.difficulty = chain.difficulty;
        row.creditedWei = credited;

        chain.contributions.add(row);
        while (chain.contributions.size() > ContributionState.LIMIT) {
            chain.contributions.removeFirst();
        }
    }

    /**
     * Hands over whatever the pool owes, if it is time.
     *
     * <p>A solo block never waits — it is a real coinbase and earns its own ledger row. A pool holds
     * shares in an internal balance and settles every {@code Balance.POOL_SETTLE_SECONDS}, which is
     * what real pools do and what keeps {@code ledger(1)} legible at 120 shares an hour.
     *
     * <p>⚠ Settling is also what <em>credits</em>. Crediting on every share and ledgering on a timer
     * would leave the balance ahead of the last ledger row, and {@code docs/design/04-mining.md} §3.1
     * makes a disagreement between two readouts the way a player detects an intruder.
     *
     * @return wei the caller should ledger, or zero if the pool is still holding
     */
    private static BigInteger settle(RigState rig, Instant now, boolean solo) {
        if (rig.miningPendingWei.signum() <= 0) {
            return BigInteger.ZERO;
        }
        // ⚠ A null or BACKWARDS clock settles immediately, and both matter.
        //
        // Null is the first payout of a character's life: making it wait a full window would hold
        // back the one payout a new player is watching for. Backwards is the hazard — a host clock
        // correction, a timezone shift, or a test that rewinds — and a naive `elapsed >= window`
        // check goes permanently false against a settledAt in the future, so the pool holds the
        // player's money forever and the balance silently stops moving. Measured: a harness whose
        // clock restarted each hour credited nothing after the first hour and the failure looked
        // like a variance bug.
        long elapsed = rig.miningSettledAt == null
                ? Long.MAX_VALUE
                : Duration.between(rig.miningSettledAt, now).toSeconds();
        if (!solo && elapsed >= 0 && elapsed < Balance.POOL_SETTLE_SECONDS) {
            return BigInteger.ZERO;
        }
        BigInteger paid = rig.miningPendingWei;
        rig.miningPendingWei = BigInteger.ZERO;
        rig.miningPendingPayouts = 0;
        rig.miningSettledAt = now;
        rig.miningWei = rig.miningWei.add(paid);
        return paid;
    }
    /** The most a single miner's buffer may hold, in wei. */
    public static BigInteger bufferCap(MinerState miner) {
        return Balance.SELF_MINING_WEI_PER_CYCLE_HOUR
                .multiply(BigInteger.valueOf(miner.hostCycles))
                .multiply(BigInteger.valueOf(Balance.YIELD_BUFFER_HOURS));
    }

    /**
     * Accrues buffered yield for every deployed miner up to {@code now}, respecting the cap.
     *
     * <p>Called on load as well as on tick, which is what makes offline income work: a player who
     * closed the client eight hours ago gets four hours' worth, because the cap bit four hours in.
     *
     * @return total minor units added across all miners
     */
    public static BigInteger accrueDeployedMiners(GameSave save, Instant now) {
        BigInteger added = BigInteger.ZERO;
        for (NodeState node : save.knownNodes) {
            for (MinerState miner : node.deployedMiners) {
                Duration elapsed = Duration.between(miner.lastAccruedAt, now);
                if (elapsed.isNegative() || elapsed.isZero()) {
                    continue;
                }
                BigInteger cap = bufferCap(miner);
                BigInteger yield = deployedYield(miner.hostCycles, elapsed);
                BigInteger before = miner.bufferedWei;
                miner.bufferedWei = before.add(yield).min(cap);
                miner.lastAccruedAt = now;
                added = added.add(miner.bufferedWei.subtract(before));
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
    public static BigInteger accrueForeignMiners(GameSave save, Instant now) {
        BigInteger added = BigInteger.ZERO;
        for (MinerState miner : save.rig.foreignMiners) {
            Duration elapsed = Duration.between(miner.lastAccruedAt, now);
            if (elapsed.isNegative() || elapsed.isZero()) {
                continue;
            }
            BigInteger cap = bufferCap(miner);
            BigInteger before = miner.bufferedWei;
            miner.bufferedWei =
                    before.add(deployedYield(miner.hostCycles, elapsed)).min(cap);
            miner.lastAccruedAt = now;
            added = added.add(miner.bufferedWei.subtract(before));
        }
        return added;
    }

    /** Sweeps every buffer into the balance. This is what {@code collect} does. */
    public static BigInteger collectAll(GameSave save, Instant now) {
        BigInteger collected = BigInteger.ZERO;
        for (NodeState node : save.knownNodes) {
            for (MinerState miner : node.deployedMiners) {
                collected = collected.add(miner.bufferedWei);
                miner.bufferedWei = BigInteger.ZERO;
            }
        }
        if (collected.signum() > 0) {
            LedgerRules.apply(save, collected, "MINING_COLLECT", "Collected deployed-miner yield", now);
        }
        return collected;
    }
}
