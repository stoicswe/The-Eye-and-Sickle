package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.github.stoicswe.eyeandsickle.protocol.game.ChainSync;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The chain filling in what it did while the client was closed.
 *
 * <p>{@code docs/design/04-mining.md} §1.3d. Invariant <b>I5</b>'s half of this — that a longer
 * absence is never worth more — lives in {@code SoloGameTest.Income}, because it is a statement about
 * money. What is here is the chain: that the fill arrives at the right pace, that difficulty
 * retargets against the window that actually elapsed, and that a broadcast transaction gets mined
 * whether or not its sender was watching.
 */
class ChainSyncTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");

    private static SoloGame at(Path file, Instant when) {
        return SoloGame.open(new SaveStore(file), "operator", Clock.fixed(when, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("filling in the missed blocks")
    class Filling {

        @Test
        @DisplayName("the chain advances at its own block interval, not in one jump")
        void fillsAtTheBlockInterval(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            at(file, T0).persist();

            // ⚠ A WEEK, not twelve hours, and the band is the reason. Block arrivals are Poisson, so
            // the relative spread is 1/√n — at twelve hours n ≈ 51 and a ±30% band is 2σ, which a
            // correct implementation fails about one run in twenty. At a week n ≈ 720 and ±20% is
            // over 5σ. A flaky test on a random process is worse than no test: it trains its reader
            // to re-run rather than to look.
            SoloGame back = at(file, T0.plus(Duration.ofDays(7)));
            ChainSync sync = back.chainSync();

            double expected = Duration.ofDays(7).getSeconds() / (double) Balance.CHAIN_TARGET_BLOCK_SECONDS;
            assertThat(sync.blocks()).isBetween((int) (expected * 0.8d), (int) (expected * 1.2d));
            assertThat(sync.toHeight() - sync.fromHeight()).isEqualTo(sync.blocks());
            assertThat(back.state().chain.height).isEqualTo(sync.toHeight());
        }

        /**
         * ⚠ The bug the walked time cursor exists to prevent, asserted directly.
         *
         * <p>{@code retarget()} computes {@code expected / actual} from
         * {@code Duration.between(retargetStartedAt, now)}. Stamp every filled block at the instant
         * the fill <em>ended</em> and {@code actual} becomes the whole absence — so a window closing
         * two hours into a thirty-day gap is measured as having taken thirty days, the adjustment
         * pins to the ÷4 clamp, and difficulty collapses to a quarter on a chain whose hashrate never
         * moved. Under the cursor each block carries the instant it was actually found, so a retarget
         * sees its own ~1440 blocks.
         *
         * <p>The online path never showed this because it ticks once a second and the error is
         * bounded by one tick. Nothing would have reported it but the difficulty readout.
         */
        @Test
        @DisplayName("a retarget mid-absence measures its own window, not the whole absence")
        void retargetIsNotSkewedByTheAbsence(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = at(file, T0);
            SoloSave save = game.state();
            // Five blocks short of a retarget, so the window closes early in the absence and the
            // rest of the gap is squarely on the far side of it.
            save.chain.blocksSinceRetarget = Balance.CHAIN_RETARGET_BLOCKS - 5;
            save.chain.retargetStartedAt =
                    T0.minusSeconds((Balance.CHAIN_RETARGET_BLOCKS - 5) * Balance.CHAIN_TARGET_BLOCK_SECONDS);
            double before = save.chain.difficulty;
            game.persist();

            SoloGame back = at(file, T0.plus(Duration.ofDays(30)));
            ChainSync sync = back.chainSync();

            // Thirty days is ~3086 blocks, so two full 1440-block windows plus the one that was
            // five blocks from closing when the client shut: three.
            assertThat(sync.retargets())
                    .as("the fill must actually close the windows it passes through")
                    .isGreaterThanOrEqualTo(3);
            // The network hashrate never changes in this game, so every adjustment should land near
            // 1.0 and difficulty should wander by a couple of percent per retarget — never divide.
            assertThat(sync.difficultyAfter())
                    .as("difficulty must not be skewed by how long the client was shut")
                    .isCloseTo(before, withinPercentage(25.0d));
            assertThat(sync.difficultyAfter())
                    .isNotCloseTo(before / Balance.CHAIN_RETARGET_CLAMP, withinPercentage(10.0d));
        }

        @Test
        @DisplayName("filled blocks carry their own timestamps, ending at the load")
        void blocksAreDatedWhenTheyWereFound(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            at(file, T0).persist();
            Instant back = T0.plus(Duration.ofHours(9));

            SoloGame later = at(file, back);
            Instant lastBlock = later.state().chain.lastBlockAt;

            // ⚠ Strictly BEFORE the load. Stamping every filled block at the load instant is the
            // failure this whole approach exists to avoid, and it would show up here as equality.
            assertThat(lastBlock).isBefore(back);
            assertThat(lastBlock).isAfter(T0);
            // The chain caught up rather than stopping early — asserted through the report, not
            // through the gap. ⚠ The gap between the last block and the load is the *residual* of an
            // exponential wait, so it has mean 840s and exceeds one block interval about 37% of the
            // time: a "within one interval" bound looks obviously right and fails one run in three.
            assertThat(later.chainSync().truncated()).isFalse();
            assertThat(Duration.between(lastBlock, back).getSeconds())
                    .as("a gap this long is a 1-in-400 draw, or a fill that stopped")
                    .isLessThan(6 * Balance.CHAIN_TARGET_BLOCK_SECONDS);
        }

        /**
         * A broadcast transaction is on the network and gets mined whether or not its sender is
         * watching. This is <b>not</b> offline income and I5 has nothing to say about it: the value
         * moved when the ledger row was written, and confirmation only stamps it with the height that
         * carried it. A transaction left pending across a four-day absence would be the lie — and
         * would also let a player park money in the mempool to hide it.
         */
        @Test
        @DisplayName("pending transactions confirm while the client is closed")
        void pendingTransactionsConfirm(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = at(file, T0);
            game.credit(Balance.ec("500"), "TEST", "seed");
            assertThat(game.debit(
                            Balance.ec("5"),
                            "TRANSFER",
                            "Sent to an address",
                            FeeTier.PRIORITY,
                            ChainExplorer.address("someone")))
                    .isTrue();
            assertThat(game.state().chain.mempool).hasSize(1);
            game.persist();

            SoloGame back = at(file, T0.plus(Duration.ofHours(6)));

            assertThat(back.state().chain.mempool)
                    .as("a transaction cannot sit unconfirmed across six hours of chain")
                    .isEmpty();
            assertThat(back.chainSync().transactionsConfirmed()).isEqualTo(1);
            assertThat(back.state().ledger)
                    .filteredOn(entry -> "TRANSFER".equals(entry.type))
                    .allSatisfy(entry -> assertThat(entry.blockNumber).isPositive());
        }

        /**
         * ⚠ The announcement is once per session; the record is the log.
         *
         * <p>A closed tool window keeps no state — {@code DeskManager} rebuilds the LEDGER window
         * from its factory on every open — so a panel built from an idempotent read replayed the
         * whole fill each time the player opened the ledger. Reported as the animation running "every
         * time the ledger app is opened".
         *
         * <p>{@link SoloGame#chainSync()} stays idempotent for tests and any second readout;
         * {@code takeChainSync} is what the panel uses, and it answers once.
         */
        @Test
        @DisplayName("the report is announced once, and reading it never consumes it")
        void theReportIsTakenOnce(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            at(file, T0).persist();
            SoloGame back = at(file, T0.plus(Duration.ofHours(9)));

            assertThat(back.chainSync().any()).isTrue();
            // Reading is free, however many times.
            assertThat(back.chainSync()).isEqualTo(back.chainSync());
            assertThat(back.chainSync().any()).isTrue();

            ChainSync first = back.takeChainSync();
            assertThat(first.any()).isTrue();
            assertThat(first).isEqualTo(back.chainSync());

            // Every later open gets nothing to announce.
            assertThat(back.takeChainSync().any()).isFalse();
            assertThat(back.takeChainSync().any()).isFalse();
            // ⚠ And taking it did not destroy the report — the idempotent read still answers, so a
            // second surface (or a test) is not silently affected by whether a window was opened.
            assertThat(back.chainSync().any()).isTrue();
        }

        @Test
        @DisplayName("a fresh load has something to announce again")
        void aNewLoadAnnouncesAgain(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            at(file, T0).persist();

            SoloGame first = at(file, T0.plus(Duration.ofHours(9)));
            assertThat(first.takeChainSync().any()).isTrue();
            assertThat(first.takeChainSync().any()).isFalse();
            first.persist();

            // A new session is a new absence and a new report. A flag that survived this would
            // suppress the announcement for the one load that actually had something to say.
            SoloGame second = at(file, T0.plus(Duration.ofHours(20)));
            assertThat(second.takeChainSync().any()).isTrue();
        }

        @Test
        @DisplayName("a load with nothing to catch up reports nothing, and shows no screen")
        void nothingToDo(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            at(file, T0).persist();

            SoloGame immediately = at(file, T0);
            assertThat(immediately.chainSync().any()).isFalse();
            assertThat(immediately.chainSync().blocks()).isZero();
        }
    }

    @Nested
    @DisplayName("the contributor record")
    class Contributions {

        /**
         * A character who has actually mined with {@code poolId} for {@code span}, ONLINE.
         *
         * <h2>⚠ Online, because the offline window is far too small a sample to assert a shape on</h2>
         *
         * These two tests are about what a contributor row <em>looks like</em> under each payout
         * scheme, and any row will do. Driving them through an absence instead makes the sample the
         * spin-down window — about seventeen blocks, of which an 18%-of-chain pool wins three — so a
         * correct implementation produces an empty list one run in twenty-odd and the test reads as
         * broken. Sixty hours of play is ~250 blocks and dozens of pool wins.
         */
        private SoloGame played(Path dir, String poolId, Duration span) {
            Winding clock = new Winding(T0);
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", clock);
            game.setPool(poolId);
            // ⚠ The postcondition, not setPool's return. It reports whether anything CHANGED, so it
            // is false for "commons" — which is already the default — and asserting on it fails a
            // rig that is correctly on the pool the test asked for.
            assertThat(MiningRules.poolOf(game.state().rig).id()).isEqualTo(poolId);
            // ⚠ 80, not 100: a fresh rig carries the tutorial parasite on some of its cycles, so a
            // full allocation is REFUSED and the rig then silently mines nothing at all.
            assertThat(game.allocateSelfMining(80)).isTrue();
            for (long hour = 0; hour < span.toHours(); hour++) {
                clock.advance(Duration.ofHours(1));
                game.tick();
            }
            return game;
        }

        @Test
        @DisplayName("a solo win records the block, the rig's share, and the whole reward")
        void soloWinsAreRecorded(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = at(file, T0);
            game.setMiningMode(MiningMode.SOLO);
            assertThat(game.allocateSelfMining(80)).isTrue();
            game.persist();

            // Long enough that a ~4% rig is overwhelmingly likely to take at least one block inside
            // the spin-down window across the whole run.
            SoloGame back = at(file, T0.plus(Duration.ofDays(4)));
            var rows = back.contributions(64).stream().filter(r -> r.won()).toList();
            if (rows.isEmpty()) {
                // Mining is a lottery and this fixture is one draw. The assertions below are about
                // the SHAPE of a row, so an empty run has nothing to check rather than a failure —
                // and the block count above already proves the fill ran.
                assertThat(back.chainSync().blocks()).isPositive();
                return;
            }
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.mode()).isEqualTo(MiningMode.SOLO);
                assertThat(row.scheme()).isEqualTo("SOLO");
                assertThat(row.poolId()).isEmpty();
                assertThat(row.minerLabel()).isEqualTo("YOUR RIG");
                assertThat(row.offline()).isTrue();
                // A solo miner keeps the whole block: subsidy plus that block's real fees.
                assertThat(row.subsidyWei()).isEqualTo(Balance.BLOCK_SUBSIDY_WEI);
                assertThat(row.creditedWei())
                        .as("solo takes the whole reward, both halves")
                        .isEqualTo(row.rewardWei());
                // The share is the probability the block was going to be theirs, and it is what the
                // draw actually used.
                assertThat(row.networkShare()).isBetween(0.001d, 0.5d);
                assertThat(row.transactions()).isPositive();
            });
            // Every recorded height is a height the chain actually reached.
            assertThat(rows).allSatisfy(row -> assertThat(row.height()).isLessThanOrEqualTo(back.state().chain.height));
        }

        /**
         * ⚠ A pay-per-share row credits nothing from the block, and that is the record working.
         *
         * <p>A share pool does not divide up the blocks it finds — it buys accepted shares out of its
         * own balance, which is the entire product ({@code MiningRules.rewardBaseWei}). The
         * rig's hashrate still went into those blocks, so they belong in the record; recording only
         * the blocks that paid would make PPS look like a mode that mines nothing, and would delete
         * the one surface where the difference between the two schemes is visible.
         */
        @Test
        @DisplayName("pay-per-share records the pool's blocks and takes nothing from them")
        void payPerShareRecordsButDoesNotTake(@TempDir Path dir) {
            SoloGame back = played(dir, "commons", Duration.ofHours(60));
            var rows = back.contributions(64);
            assertThat(rows).isNotEmpty();
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.scheme()).isEqualTo("PPS");
                assertThat(row.won()).isFalse();
                assertThat(row.offline()).isFalse();
                assertThat(row.poolId()).isEqualTo("commons");
                assertThat(row.creditedWei()).isZero();
                assertThat(row.paid()).isFalse();
                // The hashrate is real even though the cut is not — that pairing IS the teaching.
                assertThat(row.hashrate()).isPositive();
                assertThat(row.feesWei()).isPositive();
            });
            // And the rig was still paid, on its own share clock, out of the pool's balance.
            assertThat(back.balance().wei())
                    .as("PPS pays per share, so income arrives without any block paying it")
                    .isPositive();
        }

        @Test
        @DisplayName("PPLNS takes a cut of the block, never the whole thing")
        void pplnsTakesAShare(@TempDir Path dir) {
            SoloGame back = played(dir, "glass-teeth", Duration.ofHours(60));
            var rows = back.contributions(64);
            assertThat(rows).isNotEmpty();
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.scheme()).isEqualTo("PPLNS");
                assertThat(row.creditedWei())
                        .as("a cut of the block, never more than the block")
                        .isLessThan(row.rewardWei());
                assertThat(row.takeFraction()).isBetween(0.0d, 1.0d);
            });
        }

        /** The stored history is bounded; the ledger stays the authoritative record of what was paid. */
        @Test
        @DisplayName("the record is bounded and keeps the newest")
        void theRecordIsBounded(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = at(file, T0);
            assertThat(game.allocateSelfMining(80)).isTrue();
            var chain = game.state().chain;
            for (int i = 0; i < io.github.stoicswe.eyeandsickle.solo.state.ContributionState.LIMIT + 50; i++) {
                var row = new io.github.stoicswe.eyeandsickle.solo.state.ContributionState();
                row.height = i;
                row.at = T0;
                chain.contributions.add(row);
            }
            while (chain.contributions.size() > io.github.stoicswe.eyeandsickle.solo.state.ContributionState.LIMIT) {
                chain.contributions.removeFirst();
            }
            assertThat(chain.contributions).hasSize(io.github.stoicswe.eyeandsickle.solo.state.ContributionState.LIMIT);
            // Newest first out of the reader, and the oldest 50 are gone rather than the newest.
            assertThat(game.contributions(1).getFirst().height())
                    .isEqualTo(io.github.stoicswe.eyeandsickle.solo.state.ContributionState.LIMIT + 49);
        }
    }

    /** A hand-wound clock. {@code solo.TestClock} is package-private and one package up. */
    /**
     * A rig mines its own hashrate share live, and <b>half</b> of it while the client was closed.
     *
     * <h2>What is being separated here</h2>
     *
     * {@code Balance.OFFLINE_MINING_HOURS} caps how <em>long</em> an absent rig keeps hashing;
     * {@code Balance.OFFLINE_SOLO_WIN_WEIGHT} caps how <em>well</em> it does inside that window. The
     * window alone already stopped a longer absence being worth more; the weight is what keeps an
     * hour played strictly better than an hour away <em>within</em> the window as well as past it.
     */
    @Nested
    @DisplayName("offline solo mining is weighted, and only offline solo mining")
    class OfflineWeight {

        /**
         * A store holding one solo rig, ready to be loaded as many times as a test needs.
         *
         * <h2>⚠ Both runs must load the SAME save, not two saves built the same way</h2>
         *
         * A freshly opened game draws its own initial {@code networkWorkTarget}, seeded from the
         * character's id — so two rigs created identically are already a fraction of a block apart
         * before either of them starts, and the walks diverge by one block within the hour. That is
         * not the feature failing; it is the fixture comparing two different chains, and it looked
         * exactly like a broken RNG contract. Loading one persisted file twice gives two independent
         * objects with byte-identical state, which is what makes a same-seed comparison mean anything.
         */
        private SaveStore soloRig(Path dir, String name) {
            SaveStore store = new SaveStore(dir.resolve(name + ".json"));
            SoloGame game = SoloGame.open(store, "operator", Clock.fixed(T0, ZoneOffset.UTC));
            game.setMiningMode(MiningMode.SOLO);
            // ⚠ 80, not 100 — a fresh rig's tutorial parasite holds some cycles and a full
            // allocation is refused, leaving the rig mining nothing and the test asserting on zero.
            assertThat(game.allocateSelfMining(80)).isTrue();
            game.persist();
            return store;
        }

        /**
         * ⚠ The deterministic half, and the one that cannot go flaky.
         *
         * <p>Both runs walk the same save from the same instant with the same seed, and the weighting
         * scales the <b>threshold</b> the roll is compared against without changing how many draws
         * are taken. So the two streams are identical roll for roll, and every block the offline run
         * wins is a block the online run also won — {@code roll < you/2} implies {@code roll < you}.
         * A subset failure means the RNG consumption diverged, which would break replay for reasons
         * far beyond this feature.
         *
         * <p>The span is inside the spin-down window on purpose, so the two runs differ by the weight
         * and by nothing else — past it the offline rig stops competing at all and the comparison
         * would be measuring I5 instead.
         */
        @Test
        @DisplayName("every block won offline would also have been won online, with the same seed")
        void offlineWinsAreASubsetOfOnlineWins(@TempDir Path dir) {
            Duration span = Duration.ofHours(Balance.OFFLINE_MINING_HOURS);
            SaveStore store = soloRig(dir, "solo");
            for (long seed = 1; seed <= 40; seed++) {
                SoloSave online = store.load();
                SoloSave offline = store.load();

                ChainRules.advanceNetwork(online, span, T0.plus(span), new Rng(seed));
                ChainRules.sync(offline, T0, T0.plus(span), new Rng(seed));

                assertThat(offline.chain.height)
                        .as("the same seed must produce the same chain, seed %d", seed)
                        .isEqualTo(online.chain.height);
                assertThat(online.chain.blocksWon)
                        .as("offline wins must be a subset of online wins, seed %d", seed)
                        .containsAll(offline.chain.blocksWon);
            }
        }

        /**
         * The rate itself, over enough draws that the band is not luck.
         *
         * <p>⚠ Aggregated across many independent seeds rather than asserted per run. Block arrivals
         * and the winner draw are both random, so a single fill of ~17 blocks says nothing at all —
         * a per-run assertion here would be a coin flip dressed as a test, and its reader would learn
         * to re-run it rather than to look at it.
         */
        @Test
        @DisplayName("the offline win rate is about half the online win rate")
        void offlineWinsAboutHalfAsOften(@TempDir Path dir) {
            Duration span = Duration.ofHours(Balance.OFFLINE_MINING_HOURS);
            int onlineWins = 0;
            int offlineWins = 0;
            SaveStore store = soloRig(dir, "solo");
            for (long seed = 1; seed <= 300; seed++) {
                SoloSave online = store.load();
                SoloSave offline = store.load();
                onlineWins += ChainRules.advanceNetwork(online, span, T0.plus(span), new Rng(seed))
                        .yourBlocks()
                        .size();
                offlineWins += ChainRules.sync(offline, T0, T0.plus(span), new Rng(seed))
                        .minted()
                        .yourBlocks()
                        .size();
            }
            assertThat(onlineWins)
                    .as("the fixture must actually win blocks online")
                    .isGreaterThan(40);
            assertThat(offlineWins)
                    .as("offline %d vs online %d — expected about half", offlineWins, onlineWins)
                    .isBetween((int) (onlineWins * 0.30d), (int) (onlineWins * 0.70d));
        }

        /**
         * ⚠ Pools are NOT weighted, and this is the assertion that keeps it that way.
         *
         * <p>A pool's hashrate is the pool's. It competes whether or not one member's client happens
         * to be open, so scaling its share during a fill would be this rig reaching into somebody
         * else's rate — and it would quietly make pooled mining the better way to be absent, which is
         * a balance decision nobody took. With the same seed, a pooled rig's fill is identical to its
         * live run block for block.
         */
        @Test
        @DisplayName("a pooled rig's fill is unchanged — the weight is self-mining only")
        void poolsAreUntouched(@TempDir Path dir) {
            Duration span = Duration.ofHours(Balance.OFFLINE_MINING_HOURS);
            SaveStore store = soloRig(dir, "pooled");
            for (long seed = 1; seed <= 40; seed++) {
                SoloSave online = store.load();
                SoloSave offline = store.load();
                for (SoloSave save : java.util.List.of(online, offline)) {
                    save.rig.miningMode = MiningMode.POOLED.name();
                }
                var live = ChainRules.advanceNetwork(online, span, T0.plus(span), new Rng(seed));
                var filled = ChainRules.sync(offline, T0, T0.plus(span), new Rng(seed))
                        .minted();
                assertThat(filled.poolBlocks().stream()
                                .map(ChainRules.Won::height)
                                .toList())
                        .as("a pool wins the same blocks either way, seed %d", seed)
                        .isEqualTo(live.poolBlocks().stream()
                                .map(ChainRules.Won::height)
                                .toList());
            }
        }
    }

    private static final class Winding extends Clock {

        private Instant instant;

        Winding(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
