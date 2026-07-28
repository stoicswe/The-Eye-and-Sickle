package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.PoolScheme;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.state.ChainState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The mining simulation, checked against the distribution it claims to be.
 *
 * <h2>Why these are statistical tests and have to be</h2>
 *
 * Every interesting property here is a property of a <em>distribution</em>: that the two modes pay
 * the same in expectation, that one of them is enormously lumpier, that the wait is exponential
 * rather than merely random. None of those can be asserted from one sample, and a golden-value test
 * over a fixed seed would pass while the model was wrong in every way that matters — it would only
 * be checking that the arithmetic is deterministic, which it trivially is.
 *
 * <p>So these simulate long runs against a seeded {@link Rng} and assert on the aggregate. They are
 * deterministic (same seed, same answer, so no flakes) while still measuring the thing.
 *
 * <h2>⚠ The tolerances are honest, not generous</h2>
 *
 * A solo run of a few hundred blocks has a standard error of {@code 1/sqrt(n)} — about 5% at 400
 * blocks — so a 3% tolerance on solo income would be a test that fails on luck. The bands below are
 * sized from that arithmetic rather than tightened until they passed, which is the failure mode a
 * statistical test invites.
 */
class MiningChainTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    /** A rig mining {@code cycles} in {@code mode}, on a fresh chain. */
    private static SoloSave rig(long cycles, MiningMode mode) {
        SoloSave save = new SoloSave();
        save.rngSeed = 0xC0FFEEL;
        save.rig.totalCycles = 100L;
        save.rig.selfMiningCycles = cycles;
        save.rig.miningMode = mode.name();
        Rng rng = Rng.of(save);
        save.chain = ChainRules.genesis(T0, rng);
        rng.commit(save);
        return save;
    }

    /**
     * Mines for {@code hours}, one step per {@code stepSeconds}, and returns what was credited.
     *
     * <p>⚠ Advances the rest of the network too, exactly as {@code SoloGame.tick} does. Without it
     * the chain sees only the player's blocks, the retarget reads that as a hashrate collapse, and
     * difficulty spirals down — which is how the 3.8×-high solo income this harness first produced
     * came about. A test harness that skips a step the real loop takes measures a different game.
     */
    private static long mine(SoloSave save, double hours, long stepSeconds) {
        Rng rng = Rng.of(save);
        long total = 0L;
        long steps = Math.round(hours * 3600 / stepSeconds);
        // ⚠ The clock is MONOTONIC ACROSS CALLS, and it has to be. Restarting at T0 on every call
        // rewinds time, which the pool's settlement window reads as "the last payout was in the
        // future" — it held everything from the second hour on and the failure presented as a
        // variance bug rather than a clock bug. The real loop's clock only goes forward; a harness
        // whose clock does not is measuring a different game.
        Instant at = CLOCKS.getOrDefault(save, T0);
        Duration step = Duration.ofSeconds(stepSeconds);
        for (long i = 0; i < steps; i++) {
            at = at.plusSeconds(stepSeconds);
            ChainRules.Minted minted = ChainRules.advanceNetwork(save, step, at, rng);
            total += MiningRules.runSelfMining(save, step, at, rng, minted);
        }
        CLOCKS.put(save, at);
        rng.commit(save);
        return total;
    }

    /** A tick in which the chain produced nothing — for driving settlement directly. */
    private static final ChainRules.Minted NOTHING = new ChainRules.Minted(0, 0, 0, 0L, 0L);

    /** Where each save's simulated clock has reached. See {@link #mine}. */
    private static final java.util.Map<SoloSave, Instant> CLOCKS = new java.util.IdentityHashMap<>();

    /** Relative standard deviation — the standard way to say "how lumpy". */
    private static double coefficientOfVariation(long[] samples) {
        double mean = 0;
        for (long value : samples) {
            mean += value;
        }
        mean /= samples.length;
        if (mean <= 0) {
            return 0;
        }
        double sumSquares = 0;
        for (long value : samples) {
            sumSquares += (value - mean) * (value - mean);
        }
        return Math.sqrt(sumSquares / samples.length) / mean;
    }

    @Nested
    @DisplayName("the chain")
    class Chain {

        @Test
        @DisplayName("difficulty holds the ten-minute target at the network's hashrate")
        void difficultyHoldsTheInterval() {
            ChainState chain = rig(0, MiningMode.POOLED).chain;
            double seconds = ChainRules.expectedSeconds(chain.difficulty, chain.networkHashrate);
            // The definition of a correctly retargeted difficulty, and the anchor everything else
            // is derived from.
            assertThat(seconds).isCloseTo(Balance.CHAIN_TARGET_BLOCK_SECONDS, within(0.001));
        }

        @Test
        @DisplayName("the network really produces about one block every ten minutes")
        void networkProducesBlocks() {
            SoloSave save = rig(0, MiningMode.POOLED);
            Rng rng = Rng.of(save);
            long before = save.chain.height;
            Instant at = T0;
            // 1000 expected blocks. Standard error 1/sqrt(1000) ≈ 3.2%, so 10% is ~3 sigma.
            for (int i = 0; i < 1000; i++) {
                at = at.plusSeconds(840);
                ChainRules.advanceNetwork(save, Duration.ofSeconds(840), at, rng);
            }
            assertThat(save.chain.height - before).isBetween(900L, 1100L);
        }

        @Test
        @DisplayName("a retarget with a fixed network hashrate leaves difficulty where it was")
        void retargetIsStableWhenNothingChanges() {
            ChainState chain = rig(0, MiningMode.POOLED).chain;
            chain.retargetStartedAt = T0;
            double before = chain.difficulty;
            // A window that took exactly as long as it should: the adjustment is 1.0.
            ChainRules.retarget(chain, T0.plusSeconds(
                    Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS));
            assertThat(chain.difficulty).isCloseTo(before, within(1e-9));
            assertThat(chain.blocksSinceRetarget).isZero();
        }

        @Test
        @DisplayName("a fast window raises difficulty and a slow one lowers it")
        void retargetTracksTheWindow() {
            long window = Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
            ChainState fast = rig(0, MiningMode.POOLED).chain;
            fast.retargetStartedAt = T0;
            double before = fast.difficulty;
            ChainRules.retarget(fast, T0.plusSeconds(window / 2));
            // Blocks came twice as fast, so hashrate doubled, so difficulty must double.
            assertThat(fast.difficulty).isCloseTo(before * 2.0d, within(before * 0.01d));

            ChainState slow = rig(0, MiningMode.POOLED).chain;
            slow.retargetStartedAt = T0;
            ChainRules.retarget(slow, T0.plusSeconds(window * 2));
            assertThat(slow.difficulty).isCloseTo(before / 2.0d, within(before * 0.01d));
        }

        @Test
        @DisplayName("no single retarget may move difficulty by more than four times")
        void retargetIsClamped() {
            long window = Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
            ChainState chain = rig(0, MiningMode.POOLED).chain;
            chain.retargetStartedAt = T0;
            double before = chain.difficulty;
            // A window that finished a hundred times too fast still only moves difficulty 4x. The
            // clamp is what stops a hashrate collapse stranding a chain that can then never correct.
            ChainRules.retarget(chain, T0.plusSeconds(window / 100));
            assertThat(chain.difficulty).isCloseTo(before * Balance.CHAIN_RETARGET_CLAMP, within(before * 0.01d));
        }
    }

    @Nested
    @DisplayName("blocks are won, not raced")
    class BlockWins {

        @Test
        @DisplayName("the chain mints a block about every fourteen minutes")
        void fourteenMinuteBlocks() {
            assertThat(Balance.CHAIN_TARGET_BLOCK_SECONDS).isEqualTo(840L);
            ChainState chain = rig(0, MiningMode.POOLED).chain;
            // The definition of a correctly retargeted difficulty at this chain's own interval.
            assertThat(ChainRules.expectedSeconds(chain.difficulty, chain.networkHashrate))
                    .isCloseTo(840.0d, within(0.001));
        }

        @Test
        @DisplayName("the retarget window is still a fortnight, at this chain's own numbers")
        void aFortnightEitherWay() {
            // Bitcoin uses 2016 BECAUSE 2016 x 10min is two weeks. This chain keeps the property and
            // drops the number — a fortnight is long enough for luck to average out and short enough
            // to answer a real hashrate change, which is the whole reason the window has a length.
            long window = Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
            assertThat(Duration.ofSeconds(window).toDays()).isEqualTo(14L);
        }

        @Test
        @DisplayName("⚠ every block has exactly one winner, and the player wins their share of them")
        void winnersAreHashrateProportional() {
            SoloSave save = rig(100, MiningMode.SOLO);
            Rng rng = Rng.of(save);
            double share = ChainRules.hashrate(100) / save.chain.networkHashrate;

            int blocks = 0;
            int yours = 0;
            Instant at = T0;
            for (int i = 0; i < 20_000; i++) {
                at = at.plusSeconds(840);
                ChainRules.Minted minted = ChainRules.advanceNetwork(save, Duration.ofSeconds(840), at, rng);
                blocks += minted.blocks();
                yours += minted.yours();
            }
            // The claim the whole model rests on: your chance at each block is your share of the
            // chain. About 20 000 blocks, so the standard error on the share is ~1/sqrt(20000x0.06)
            // = 2.9% of itself; a 20% band is comfortably over three sigma.
            assertThat(blocks).isGreaterThan(19_000);
            assertThat(yours / (double) blocks)
                    .as("share of blocks won against %.4f share of hashrate", share)
                    .isCloseTo(share, within(share * 0.2d));
        }

        @Test
        @DisplayName("a pooled rig is never drawn separately — its hashrate is inside its pool")
        void pooledIsNotDrawnTwice() {
            SoloSave save = rig(100, MiningMode.POOLED);
            Rng rng = Rng.of(save);
            int yours = 0;
            Instant at = T0;
            for (int i = 0; i < 2000; i++) {
                at = at.plusSeconds(840);
                yours += ChainRules.advanceNetwork(save, Duration.ofSeconds(840), at, rng).yours();
            }
            // Drawing them separately would count the same hashrate twice: the pool would win its
            // full share and the player would win on top of it, inflating the chain's block rate and
            // the player's income together.
            assertThat(yours).isZero();
        }

        @Test
        @DisplayName("the explorer keeps a bounded window, newest last in the save")
        void windowIsBounded() {
            SoloSave save = rig(100, MiningMode.SOLO);
            Rng rng = Rng.of(save);
            Instant at = T0;
            for (int i = 0; i < 200; i++) {
                at = at.plusSeconds(840);
                ChainRules.advanceNetwork(save, Duration.ofSeconds(840), at, rng);
            }
            // ⚠ NO blocks are stored at all — every field of one is derived from its height, which
            // is what lets the chain open at 124 with all 124 inspectable and keep growing while the
            // save does not. The only stored thing is which heights the player WON, because that was
            // rolled and cannot be derived, and even that is a bounded index over the ledger.
            assertThat(save.chain.blocksWon.size()).isLessThanOrEqualTo(ChainState.WON_INDEX);
            assertThat(ChainExplorer.recentBlocks(save)).hasSize(ChainState.RECENT_BLOCKS);
            // Every height renders, including ones long out of the strip.
            assertThat(ChainExplorer.header(save, 1).hash()).startsWith("0x").hasSize(66);
            assertThat(ChainExplorer.header(save, save.chain.height).number()).isEqualTo(save.chain.height);
        }
    }

    @Nested
    @DisplayName("what each mode pays")
    class Rates {

        @Test
        @DisplayName("⚠ pooled pays exactly the rate docs/design/03 §1 prices the economy against")
        void pooledHoldsTheEconomyAnchor() {
            for (long cycles : new long[] {100, 50, 25, 10}) {
                SoloSave save = rig(cycles, MiningMode.POOLED);
                long perHour = MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain);
                // 0.4 EC per cycle-hour, unchanged since before there was a chain. This is the whole
                // reason Balance.chainNetworkHashrate() is derived rather than chosen: if it were a
                // hand-picked constant this assertion would be the thing that quietly stopped holding.
                assertThat(perHour)
                        .as("%d cycles", cycles)
                        .isEqualTo(cycles * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR);
            }
        }

        @Test
        @DisplayName("solo pays more than the default pool — by its fee AND by fee exposure")
        void soloKeepsTheFeeAndTheBlockFees() {
            SoloSave pooled = rig(100, MiningMode.POOLED);
            SoloSave solo = rig(100, MiningMode.SOLO);
            long p = MiningRules.expectedMinorUnitsPerHour(pooled.rig, pooled.chain);
            long s = MiningRules.expectedMinorUnitsPerHour(solo.rig, solo.chain);

            // The trade has to be a real one in both directions. A pool that paid the same as solo
            // would be free insurance and nobody sane would ever mine solo.
            assertThat(s).isGreaterThan(p);
            // ⚠ Two components since 2026-07-27, and the default pool is PPS so it has neither: the
            // 2% it keeps, and the block fees a share price cannot include. Together that is about
            // 12.8%, where it used to be 2.0% — which is a deliberate widening, not a drift.
            double feeExposure =
                    (Balance.BLOCK_SUBSIDY_MINOR_UNITS + Balance.expectedBlockFeesMinorUnits())
                            / Balance.BLOCK_SUBSIDY_MINOR_UNITS;
            assertThat(s).isCloseTo(Math.round(p / (1 - Balance.POOL_FEE) * feeExposure), within(2L));
        }

        @Test
        @DisplayName("expected income is linear in cycles, in both modes")
        void incomeIsLinear() {
            for (MiningMode mode : MiningMode.values()) {
                SoloSave small = rig(25, mode);
                SoloSave large = rig(100, mode);
                // Within a minor unit: the published figure is rounded to whole minor units, and at
                // the solo rate a quarter rig rounds down where a full rig rounds up.
                assertThat(MiningRules.expectedMinorUnitsPerHour(large.rig, large.chain))
                        .as("%s", mode)
                        .isCloseTo(4 * MiningRules.expectedMinorUnitsPerHour(small.rig, small.chain), within(4L));
            }
        }

        @Test
        @DisplayName("committing nothing earns nothing, in both modes")
        void zeroEarnsZero() {
            for (MiningMode mode : MiningMode.values()) {
                SoloSave save = rig(0, mode);
                assertThat(mine(save, 10, 60)).as("%s", mode).isZero();
                assertThat(MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain)).isZero();
            }
        }
    }

    @Nested
    @DisplayName("simulated over a long run")
    class LongRun {

        @Test
        @DisplayName("pooled income converges on the published rate")
        void pooledConverges() {
            SoloSave save = rig(100, MiningMode.POOLED);
            long earned = mine(save, 200, 10);
            long expected = 200 * 100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR;
            // 200 hours is 24 000 shares; standard error 1/sqrt(24000) ≈ 0.6%. A 4% band is ~6 sigma
            // and still tight enough to catch a rate that is wrong by a fee or a factor.
            assertThat(earned).isCloseTo(expected, within(Math.round(expected * 0.04)));
        }

        @Test
        @DisplayName("solo income converges on the same rate, plus the fee it did not pay")
        void soloConverges() {
            SoloSave save = rig(100, MiningMode.SOLO);
            long earned = mine(save, 4000, 60);
            // ⚠ Includes block fees since 2026-07-27 — a won block pays subsidy + fees, which is
            // 10.55% more than the subsidy alone. This was a deliberate decision to let mining
            // income rise rather than re-solving chainNetworkHashrate to absorb it; see
            // Balance.expectedBlockFeesMinorUnits and design/03 §1.1.
            double withFees =
                    (Balance.BLOCK_SUBSIDY_MINOR_UNITS + Balance.expectedBlockFeesMinorUnits())
                            / Balance.BLOCK_SUBSIDY_MINOR_UNITS;
            long expected = Math.round(4000 * 100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR
                    / (1 - Balance.POOL_FEE) * withFees);
            // 4000 hours is about 1020 blocks; standard error ≈ 3.1%, so 12% is ~4 sigma. This is
            // the test that would catch a solo payout that was secretly worth more or less than a
            // block, which is the single easiest thing to get wrong here.
            assertThat(earned).isCloseTo(expected, within(Math.round(expected * 0.12)));
        }

        @Test
        @DisplayName("⚠ solo is enormously lumpier than pooled — the entire point of the choice")
        void soloIsLumpy() {
            SoloSave pooled = rig(100, MiningMode.POOLED);
            SoloSave solo = rig(100, MiningMode.SOLO);

            int hours = 400;
            long[] pooledHours = new long[hours];
            long[] soloHours = new long[hours];
            int pooledDry = 0;
            int soloDry = 0;
            for (int hour = 0; hour < hours; hour++) {
                pooledHours[hour] = mine(pooled, 1, 10);
                soloHours[hour] = mine(solo, 1, 10);
                if (pooledHours[hour] == 0) {
                    pooledDry++;
                }
                if (soloHours[hour] == 0) {
                    soloDry++;
                }
            }

            // Pooled never has an empty hour: 120 shares an hour, and all 120 failing is not a thing
            // that happens. This is what makes it the floor docs/design/03 §1 calls it.
            assertThat(pooledDry).as("empty pooled hours").isZero();
            // Solo is empty most hours — expected block time is about 3h55, so the chance of nothing
            // in an hour is exp(-1/3.92) ≈ 77%.
            assertThat(soloDry).as("empty solo hours").isBetween((int) (hours * 0.65), (int) (hours * 0.88));

            // ⚠ The headline number, and the whole reason the choice exists. Theory: the relative
            // standard deviation of a Poisson count is 1/sqrt(n), so pooled at 120 shares an hour is
            // about 9% and solo at 0.26 blocks an hour is about 196% — a factor of roughly 21.
            double pooledCv = coefficientOfVariation(pooledHours);
            double soloCv = coefficientOfVariation(soloHours);
            assertThat(pooledCv).as("pooled hour-to-hour variation").isLessThan(0.20d);
            assertThat(soloCv).as("solo hour-to-hour variation").isGreaterThan(1.5d);
            assertThat(soloCv / pooledCv).as("variance ratio").isGreaterThan(8.0d);
        }

        @Test
        @DisplayName("more cycles make a solo block likelier, and never certain")
        void moreCyclesRaiseTheChance() {
            double previous = 0;
            for (long cycles : new long[] {10, 25, 50, 100}) {
                SoloSave save = rig(cycles, MiningMode.SOLO);
                double chance = 1 - Math.exp(-3600.0d / ChainRules.expectedSeconds(
                        save.chain.difficulty, ChainRules.hashrate(cycles)));
                assertThat(chance).as("%d cycles", cycles).isGreaterThan(previous);
                // ⚠ Never certain, at any rig this game can build. That is the request — "a very
                // large amount of cycles to make it likely" — and it is also just true of a Poisson
                // process: the chance of nothing in an hour is exp(-t/T), which is never zero.
                assertThat(chance).as("%d cycles", cycles).isLessThan(1.0d);
                previous = chance;
            }
            // A full rig is still a minority chance in any given hour.
            assertThat(previous).isBetween(0.15d, 0.35d);
        }
    }

    @Nested
    @DisplayName("the pools")
    class PoolRoster {

        private static SoloSave onPool(long cycles, String poolId) {
            SoloSave save = rig(cycles, MiningMode.POOLED);
            save.rig.miningPoolId = poolId;
            return save;
        }

        @Test
        @DisplayName("⚠ the default pool pays exactly the docs/design/03 §1 rate")
        void defaultPoolIsTheAnchor() {
            SoloSave save = onPool(100, Pools.DEFAULT_ID);
            // The whole economy table is priced against this one figure, and a new character gets
            // this pool. If the default's fee ever stops matching Balance.POOL_FEE, this is the
            // assertion that says so.
            assertThat(MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain))
                    .isEqualTo(100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR);
            assertThat(Pools.defaultPool().feeBasisPoints()).isEqualTo(Balance.POOL_FEE_BASIS_POINTS);
        }

        @Test
        @DisplayName("⚠ no pool dominates — the cheapest is also the lumpiest")
        void noPoolDominates() {
            MiningPool cheapest = Pools.all().stream()
                    .min(java.util.Comparator.comparingInt(MiningPool::feeBasisPoints))
                    .orElseThrow();
            MiningPool dearest = Pools.all().stream()
                    .max(java.util.Comparator.comparingInt(MiningPool::feeBasisPoints))
                    .orElseThrow();

            SoloSave cheap = onPool(100, cheapest.id());
            SoloSave dear = onPool(100, dearest.id());

            // Cheapest really does pay more per hour — the fee is the only thing that moves income.
            assertThat(MiningRules.expectedMinorUnitsPerHour(cheap.rig, cheap.chain))
                    .isGreaterThan(MiningRules.expectedMinorUnitsPerHour(dear.rig, dear.chain));
            // ...and really does pay far less often. A roster where one row wins on both axes is a
            // roster with one row in it.
            assertThat(ChainRules.expectedSeconds(
                            MiningRules.workingDifficulty(cheap.rig, cheap.chain),
                            ChainRules.hashrate(100)))
                    .isGreaterThan(20 * ChainRules.expectedSeconds(
                            MiningRules.workingDifficulty(dear.rig, dear.chain),
                            ChainRules.hashrate(100)));
        }

        @Test
        @DisplayName("the fee and the SCHEME move income — pool size still never does")
        void onlyTheFeeAndSchemeMoveIncome() {
            for (MiningPool pool : Pools.all()) {
                SoloSave save = onPool(100, pool.id());
                // ⚠ Two factors since 2026-07-27, where there was one. Blocks now pay their fees to
                // whoever mined them, and PPLNS divides those among the pool while classic PPS does
                // not — a PPS pool sells a fixed price per share, which cannot depend on what a
                // block it may never find happened to carry. So the scheme is a real income axis
                // now. Pool SIZE is still not one, which is the half of the old identity that has
                // to survive: it moves the payout interval and cancels out of the rate.
                double feeShare = pool.scheme() == PoolScheme.PPLNS
                        ? (Balance.BLOCK_SUBSIDY_MINOR_UNITS + Balance.expectedBlockFeesMinorUnits())
                                / Balance.BLOCK_SUBSIDY_MINOR_UNITS
                        : 1.0d;
                long expected = Math.round(
                        100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR
                                * (1 - pool.fee()) / (1 - Balance.POOL_FEE) * feeShare);
                assertThat(MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain))
                        .as("%s", pool.name())
                        .isCloseTo(expected, within(2L));
            }
        }

        @Test
        @DisplayName("⚠ pool SIZE is still not an income axis, only a variance one")
        void poolSizeStillDoesNotMoveIncome() {
            // The half of the old identity that had to survive the fee change. Two PPLNS pools of
            // very different sizes and the same scheme must pay the same rate once their fees are
            // accounted for — if size ever started moving income the roster would be a ladder and
            // the choice would collapse to "join the biggest".
            List<MiningPool> pplns = Pools.all().stream()
                    .filter(pool -> pool.scheme() == PoolScheme.PPLNS)
                    .toList();
            assertThat(pplns).hasSizeGreaterThan(1);
            for (MiningPool pool : pplns) {
                SoloSave save = onPool(100, pool.id());
                double perHourAtZeroFee = MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain)
                        / (1 - pool.fee());
                double reference = 100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR
                        / (1 - Balance.POOL_FEE)
                        * (Balance.BLOCK_SUBSIDY_MINOR_UNITS + Balance.expectedBlockFeesMinorUnits())
                        / Balance.BLOCK_SUBSIDY_MINOR_UNITS;
                assertThat(perHourAtZeroFee).as("%s", pool.name()).isCloseTo(reference, within(4.0d));
            }
        }

        @Test
        @DisplayName("the expected fee total is derived, and matches what blocks actually pay")
        void expectedFeesMatchReality() {
            SoloSave save = rig(0, MiningMode.POOLED);
            long total = 0;
            int blocks = 20_000;
            for (long height = 1; height <= blocks; height++) {
                total += MempoolRules.blockFeesMinorUnits(save, height);
            }
            // Balance derives this from the two distributions rather than pasting a measured
            // number, so that a change to the fee ladder or the block limit cannot leave the
            // published income expectation quietly describing the old economy.
            assertThat(total / (double) blocks)
                    .isCloseTo(Balance.expectedBlockFeesMinorUnits(), within(30.0d));
        }

        @Test
        @DisplayName("PPLNS pays at the pool's block interval — pool size IS the variance knob")
        void pplnsTracksPoolSize() {
            List<MiningPool> pplns = Pools.all().stream()
                    .filter(pool -> pool.scheme() == PoolScheme.PPLNS)
                    .sorted(java.util.Comparator.comparingDouble(MiningPool::networkShare))
                    .toList();
            assertThat(pplns).hasSizeGreaterThan(1);

            for (MiningPool pool : pplns) {
                SoloSave save = onPool(100, pool.id());
                double interval = ChainRules.expectedSeconds(
                        MiningRules.workingDifficulty(save.rig, save.chain), ChainRules.hashrate(100));
                // You are paid when the POOL finds a block, so your interval is its block interval:
                // ten minutes divided by its share of the chain. Nothing about your own rig moves it.
                assertThat(interval)
                        .as("%s at %s of the chain", pool.name(), pool.shareText())
                        .isCloseTo(Balance.CHAIN_TARGET_BLOCK_SECONDS / pool.networkShare(),
                                within(Balance.CHAIN_TARGET_BLOCK_SECONDS / pool.networkShare() * 0.02));
            }
        }

        @Test
        @DisplayName("PPS pays at its share target however small the pool is")
        void ppsIgnoresPoolSize() {
            for (MiningPool pool : Pools.all()) {
                if (pool.scheme() != PoolScheme.PPS) {
                    continue;
                }
                SoloSave save = onPool(100, pool.id());
                double interval = ChainRules.expectedSeconds(
                        MiningRules.workingDifficulty(save.rig, save.chain), ChainRules.hashrate(100));
                // ⚠ The thing people get wrong about pools. Under PPS the smoothing comes from the
                // share target, not from the pool's size — a one-rack PPS pool smooths exactly as
                // well as the biggest on the chain.
                assertThat(interval).as("%s", pool.name()).isCloseTo(pool.shareSeconds(), within(0.01));
            }
        }

        @Test
        @DisplayName("a PPS pool smooths a small rig as well as a large one")
        void ppsSmoothsEveryRig() {
            for (long cycles : new long[] {5, 25, 100}) {
                SoloSave save = onPool(cycles, "commons");
                double interval = ChainRules.expectedSeconds(
                        MiningRules.workingDifficulty(save.rig, save.chain), ChainRules.hashrate(cycles));
                // Vardiff is defined by a target TIME, so the interval is the same at every rig size.
                // A fixed share difficulty would have left a 5-cycle rig waiting twenty times longer.
                assertThat(interval).as("%d cycles", cycles).isCloseTo(30.0d, within(0.01));
            }
        }

        @Test
        @DisplayName("simulated: a small PPLNS pool really is lumpier than a big PPS one")
        void measuredVarianceOrdering() {
            SoloSave steady = onPool(100, "meridian");
            SoloSave lumpy = onPool(100, "small-hours");

            int hours = 300;
            long[] steadyHours = new long[hours];
            long[] lumpyHours = new long[hours];
            for (int hour = 0; hour < hours; hour++) {
                steadyHours[hour] = mine(steady, 1, 10);
                lumpyHours[hour] = mine(lumpy, 1, 10);
            }
            double steadyCv = coefficientOfVariation(steadyHours);
            double lumpyCv = coefficientOfVariation(lumpyHours);

            // The measured version of the trade the roster is built on. A player who picked on fee
            // alone bought this.
            assertThat(steadyCv).as("MERIDIAN hour-to-hour").isLessThan(0.15d);
            assertThat(lumpyCv).as("SMALL HOURS hour-to-hour").isGreaterThan(0.8d);
        }

        @Test
        @DisplayName("⚠ simulated income matches the published figure, on EVERY pool")
        void everyPoolPaysWhatItAdvertises() {
            for (MiningPool pool : Pools.all()) {
                SoloSave save = onPool(100, pool.id());
                long advertised = MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain);

                // Long enough that even SMALL HOURS — one payout every 3.3 hours — accumulates
                // enough events for the mean to mean something. 1200 hours is ~360 payouts there and
                // ~144 000 on MERIDIAN.
                long earned = mine(save, 1200, 30);
                long expected = advertised * 1200;

                // ⚠ THE assertion for this whole feature. The published rate is what the panel, the
                // top strip, the `mine` readout and the `pools` table all print, and it is derived
                // arithmetic; this is the only check that the SIMULATION actually pays it. A bug in
                // payoutFraction could give the right variance and the wrong mean, and nothing else
                // here would notice.
                //
                // The band is sized from the pool: standard error is 1/sqrt(payouts), which is ~5%
                // for SMALL HOURS and negligible for MERIDIAN, so 15% is comfortably over three
                // sigma for the worst case and still catches a fee applied twice or a factor lost.
                assertThat(earned)
                        .as("%s: %.2f EC/hr advertised over 1200h", pool.name(), advertised / 100.0d)
                        .isCloseTo(expected, within(Math.round(expected * 0.15)));
            }
        }

        @Test
        @DisplayName("nothing is lost or invented between earning and being paid")
        void pendingReconciles() {
            SoloSave save = onPool(100, "commons");
            long paid = mine(save, 40, 10);
            // Everything the rig ever earned is either in the player's hands or on the pool's books,
            // and the sub-minor-unit residue accounts for the rest. A settlement path that dropped a
            // payout would show up here and nowhere else.
            assertThat(save.rig.miningMinorUnits).isEqualTo(paid);
            assertThat(save.rig.miningPendingMinorUnits).isGreaterThanOrEqualTo(0L);
            assertThat(save.rig.miningResidueMinorUnits).isBetween(0.0d, 1.0d);

            long accountedFor = paid + save.rig.miningPendingMinorUnits;
            long expected = 40 * MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain);
            assertThat(accountedFor).isCloseTo(expected, within(Math.round(expected * 0.05)));
        }

        @Test
        @DisplayName("a solo block is paid at once; a pool holds shares between settlements")
        void settlementDiffersByMode() {
            SoloSave pooled = onPool(100, "commons");

            // ⚠ The FIRST payout of a character's life never waits, deliberately: holding back the
            // one payout a new player is watching for would make mining look broken for a minute.
            assertThat(mine(pooled, 60.0d / 3600, 5)).as("the first share").isPositive();

            // Driven directly rather than simulated, because when the first settlement lands is
            // stochastic and "is something pending right now" is therefore not a stable assertion.
            // The rule is: inside the window the pool holds, outside it the pool pays.
            Rng rng = Rng.of(pooled);
            pooled.rig.miningPendingMinorUnits = 500L;
            pooled.rig.miningSettledAt = T0.plusSeconds(1000);
            assertThat(MiningRules.runSelfMining(pooled, Duration.ofSeconds(1), T0.plusSeconds(1030), rng, NOTHING))
                    .as("inside the window, the pool holds")
                    .isZero();
            assertThat(pooled.rig.miningPendingMinorUnits).isGreaterThanOrEqualTo(500L);

            long held = pooled.rig.miningPendingMinorUnits;
            assertThat(MiningRules.runSelfMining(pooled, Duration.ofSeconds(1), T0.plusSeconds(1090), rng, NOTHING))
                    .as("past the window, the pool pays")
                    .isGreaterThanOrEqualTo(held);
            assertThat(pooled.rig.miningPendingMinorUnits).isZero();

            SoloSave solo = rig(100, MiningMode.SOLO);
            long earned = mine(solo, 40, 60);
            // A block is a coinbase. It never waits on anyone's schedule, so nothing is ever pending.
            assertThat(earned).isPositive();
            assertThat(solo.rig.miningPendingMinorUnits).isZero();
        }

        @Test
        @DisplayName("⚠ settlements pace the LEDGER, not the income — one a minute, not one a share")
        void settlementPacesTheLedger() {
            SoloSave save = onPool(100, "commons");
            int hours = 5;
            int settlements = 0;
            long paid = 0;
            // One call per second, exactly as SoloGame.tick does, and count the calls that hand money
            // over. Each one of those is one ledger row.
            for (int second = 0; second < hours * 3600; second++) {
                long got = mine(save, 1.0d / 3600, 1);
                if (got > 0) {
                    settlements++;
                    paid += got;
                }
            }

            // Shares land about every 30s — 600 of them over five hours. Crediting each one would put
            // 600 rows in `ledger(1)`, whose own shipped page calls itself "the only record of where
            // your money went"; buried under a wall of identical 0.31 EC rows it records nothing.
            assertThat(save.rig.miningPayouts).as("shares accepted").isGreaterThan(500L);
            // Settling on a sixty-second window gives about 300 instead, and the arithmetic is
            // untouched — this paces the LEDGER and nothing else.
            assertThat(settlements).as("ledger rows").isBetween(hours * 45, hours * 62);
            assertThat(settlements).as("far fewer rows than shares").isLessThan((int) save.rig.miningPayouts);

            long expected = hours * MiningRules.expectedMinorUnitsPerHour(save.rig, save.chain);
            assertThat(paid + save.rig.miningPendingMinorUnits)
                    .as("aggregating rows must not change what was earned")
                    .isCloseTo(expected, within(Math.round(expected * 0.08)));
        }

        @Test
        @DisplayName("⚠ every PPLNS pool out-hashes a maxed rig, or the clamp fires silently")
        void pplnsPoolsOutHashAMaxedRig() {
            SoloSave save = onPool(100, Pools.DEFAULT_ID);
            double maxedRig = ChainRules.hashrate(100);
            for (MiningPool pool : Pools.all()) {
                if (pool.scheme() != PoolScheme.PPLNS) {
                    continue;
                }
                double poolHashrate = save.chain.networkHashrate * pool.networkShare();
                // A PPLNS payout is playerHashrate/poolHashrate of a block, clamped at 1. A rig
                // bigger than its own pool clamps, and the pool then behaves like solo mining with a
                // fee attached — the worst of both, and nothing on screen would say so. This caught
                // it once already: the 14-minute block interval shrank the network from 2352 to 1680
                // cycles and a 100-cycle rig became larger than the 5% pool it was mining with.
                assertThat(poolHashrate)
                        .as("%s at %s of the chain vs a 100-cycle rig", pool.name(), pool.shareText())
                        .isGreaterThan(maxedRig * 1.5d);
            }
        }

        @Test
        @DisplayName("an unknown pool id falls back to the default rather than throwing")
        void unknownPoolIsSafe() {
            SoloSave save = onPool(100, "a-pool-that-was-shut-down");
            // A content change must never lock a player out of their own save.
            assertThat(MiningRules.poolOf(save.rig).id()).isEqualTo(Pools.DEFAULT_ID);
            save.rig.miningPoolId = null;
            assertThat(MiningRules.poolOf(save.rig).id()).isEqualTo(Pools.DEFAULT_ID);
        }

        @Test
        @DisplayName("solo pays no fee to anyone, whichever pool the save remembers")
        void soloIgnoresThePool() {
            SoloSave save = onPool(100, "meridian");
            save.rig.miningMode = MiningMode.SOLO.name();
            assertThat(MiningRules.feeOf(save.rig)).isZero();
            assertThat(MiningRules.payoutFraction(save.rig, save.chain)).isEqualTo(1.0d);
            // Subsidy AND the block's fees: a solo miner keeps the whole block, which since
            // 2026-07-27 means both halves of what a block is worth.
            assertThat(MiningRules.payoutMinorUnits(save.rig, save.chain))
                    .isEqualTo(Balance.BLOCK_SUBSIDY_MINOR_UNITS
                            + Balance.expectedBlockFeesMinorUnits());
        }
    }

    @Nested
    @DisplayName("the properties that come from being memoryless")
    class Memoryless {

        @Test
        @DisplayName("⚠ the tick rate cannot change what is earned")
        void tickRateIsIrrelevant() {
            // The property that makes the model honest rather than an animation. Work accrues
            // continuously and payouts are drawn against it, so a client running at 1Hz and one
            // running at 1/60Hz mine identically. A per-tick roll would have made income depend on
            // frame rate — invisible in testing, and a real advantage to whoever had the better
            // machine.
            long fine = mine(rig(100, MiningMode.POOLED), 100, 1);
            long coarse = mine(rig(100, MiningMode.POOLED), 100, 60);
            long expected = 100 * 100 * Balance.SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR;
            assertThat(fine).isCloseTo(expected, within(Math.round(expected * 0.05)));
            assertThat(coarse).isCloseTo(expected, within(Math.round(expected * 0.05)));
        }

        @Test
        @DisplayName("switching modes forfeits nothing, so the old mid-block penalty has nothing to describe")
        void switchingIsFree() {
            SoloSave save = rig(100, MiningMode.POOLED);
            mine(save, 0.4, 10);
            double workBefore = save.rig.miningWorkDone;

            save.rig.miningMode = MiningMode.SOLO.name();
            // docs/design/04 §1.3 used to propose that pulling cycles mid-block forfeited that
            // block's progress. There is no progress: the outstanding draw survives the switch and
            // the remaining wait on an exponential is distributed exactly like a fresh one, so
            // neither keeping nor re-rolling it advantages anyone. The proposal was deleted rather
            // than implemented because it described a thing that does not exist.
            assertThat(save.rig.miningWorkDone).isEqualTo(workBefore);
            assertThat(save.rig.miningWorkTarget).isPositive();
        }

        @Test
        @DisplayName("the residue is carried, so ten short sessions pay what one long one does")
        void noRoundingDrift() {
            long oneSitting = mine(rig(100, MiningMode.POOLED), 50, 10);

            SoloSave split = rig(100, MiningMode.POOLED);
            long inChunks = 0;
            for (int i = 0; i < 10; i++) {
                inChunks += mine(split, 5, 10);
            }
            // A share is worth about 33.3 minor units. Truncating each one would skim a third of a
            // unit per share — the bug nobody reports and everybody feels.
            assertThat(inChunks).isCloseTo(oneSitting, within(Math.round(oneSitting * 0.05)));
        }

        @Test
        @DisplayName("a pool share is never a block — shares must not touch the chain height")
        void sharesAreNotBlocks() {
            SoloSave save = rig(100, MiningMode.POOLED);
            long before = save.chain.height;
            assertThat(mine(save, 5, 10)).isPositive();

            long blocks = save.chain.height - before;
            long shares = save.rig.miningPayouts;
            // Five hours is about 600 shares and about 30 blocks — and the 30 are the rest of the
            // network's, not this rig's.
            assertThat(shares).isGreaterThan(400L);
            assertThat(blocks).isBetween(15L, 50L);
            // ⚠ A share is a proof of PARTIAL work. Publishing one as a block would inflate the
            // chain by a factor of several hundred and make the retarget meaningless — the window
            // would close in minutes and difficulty would climb until nobody could mine anything.
            assertThat(blocks).isLessThan(shares / 4);
        }

        @Test
        @DisplayName("a solo block is a real block and counts toward the retarget")
        void soloBlocksAreRealBlocks() {
            SoloSave save = rig(100, MiningMode.SOLO);
            long before = save.chain.height;
            long earned = mine(save, 100, 60);
            assertThat(earned).isPositive();
            assertThat(save.chain.height).isGreaterThan(before);
            assertThat(save.rig.miningPayouts).isPositive();
        }
    }

    @Nested
    @DisplayName("invariants")
    class Invariants {

        @Test
        @DisplayName("I5 — mining pays nothing for time spent logged off")
        void onlineOnly() {
            SoloSave save = rig(100, MiningMode.POOLED);
            Rng rng = Rng.of(save);
            // The offline path never calls runSelfMining at all; this asserts the shape that makes
            // that safe — zero elapsed earns zero, which is what resume() leaves behind when it sets
            // lastTick = now.
            assertThat(MiningRules.runSelfMining(save, Duration.ZERO, T0, rng, NOTHING)).isZero();
            assertThat(MiningRules.runSelfMining(save, Duration.ofSeconds(-60), T0, rng, NOTHING)).isZero();
        }

        @Test
        @DisplayName("an unrecognised mode falls back to pooled, never to the lottery")
        void unknownModeIsSafe() {
            SoloSave save = rig(100, MiningMode.POOLED);
            save.rig.miningMode = "MARTINGALE";
            // A hand-edited or future save must not silently opt the player into variance. I4 makes
            // self-mining the floor; a floor that sometimes pays nothing is not one.
            assertThat(MiningRules.modeOf(save.rig)).isEqualTo(MiningMode.POOLED);
            save.rig.miningMode = null;
            assertThat(MiningRules.modeOf(save.rig)).isEqualTo(MiningMode.POOLED);
        }

        @Test
        @DisplayName("a new character is pooled")
        void defaultIsPooled() {
            assertThat(MiningRules.modeOf(new SoloSave().rig)).isEqualTo(MiningMode.POOLED);
        }
    }
}
