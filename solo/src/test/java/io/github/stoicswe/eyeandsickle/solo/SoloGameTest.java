package io.github.stoicswe.eyeandsickle.solo;

import static io.github.stoicswe.eyeandsickle.solo.support.Money.ec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.MiningRules;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the single-player runtime.
 *
 * <p>These concentrate on the four invariants a solo game can actually violate — I1 (compute is not
 * bought), I4/I5 (which income is safe and which is online-only), I6 (whose compute a miner spends)
 * and the compute readout's exact reconciliation. Everything else here is arithmetic, and arithmetic
 * that a player would notice.
 */
class SoloGameTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");

    private static SoloGame freshGame(Path dir) {
        return bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                new TestClock(T0));
    }

    /**
     * A character with the tutorial parasite removed.
     *
     * <p>{@code SoloGame.newCharacter} plants a foreign miner on every new rig, because
     * {@code docs/design/04} §5.1 makes cracking one the tutorial for the whole breach system and a
     * fresh character would otherwise have no reachable target at all. By <b>Invariant I6</b> that
     * miner draws the <em>host's</em> cycles, so a brand-new rig genuinely has
     * {@code 100 - Balance.TUTORIAL_MINER_HOST_CYCLES} available rather than 100.
     *
     * <p>⚠ The tests in this class are about <b>compute arithmetic</b> — allocation, the recovery
     * curve, the budget reconciling exactly — and not about the tutorial. Rewriting every
     * expectation to {@code 100 - 6} would bury that arithmetic under an unrelated constant and
     * would have to be redone the day the tutorial's cost changes. Removing the parasite keeps each
     * assertion saying the thing it was written to say; {@link Breach} covers the parasite itself,
     * which is where that behaviour belongs.
     */
    private static SoloGame bare(SaveStore store, java.time.Clock clock) {
        SoloGame game = SoloGame.open(store, "operator", clock);
        var rig = game.state().rig;
        for (var miner : List.copyOf(rig.foreignMiners)) {
            rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
        }
        rig.foreignMiners.clear();
        return game;
    }

    @Nested
    @DisplayName("the compute budget")
    class Compute {

        @Test
        @DisplayName("a fresh rig has 100 cycles, all available, and reconciles exactly")
        void freshRig(@TempDir Path dir) {
            ComputeBudget budget = freshGame(dir).computeBudget();

            assertThat(budget.total()).isEqualTo(Cycles.of(Balance.STARTING_CYCLES));
            assertThat(budget.available()).isEqualTo(Cycles.of(Balance.STARTING_CYCLES));
            // The rig monitor is always on screen and design/04 §3.1 requires a player to be able to
            // catch a hidden miner by noticing the numbers do not add up. That only works if they
            // add up in the first place.
            assertThat(budget.reconciles()).isTrue();
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(0));
        }

        @Test
        @DisplayName("self-mining shows up as an allocation, and the budget still reconciles")
        void selfMiningIsVisible(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            assertThat(game.allocateSelfMining(40)).isTrue();

            ComputeBudget budget = game.computeBudget();
            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.available()).isEqualTo(Cycles.of(60));
            assertThat(budget.reconciles()).isTrue();
            // Not tracked off to one side: a player must be able to see in the per-consumer
            // breakdown that self-mining is where the rig went.
            assertThat(budget.allocatedByConsumer()).containsValue(Cycles.of(40));
        }

        @Test
        @DisplayName("a rig cannot commit more than it has")
        void cannotOverCommit(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            assertThat(game.allocateSelfMining(101)).isFalse();
            assertThat(game.allocateSelfMining(100)).isTrue();
            assertThat(game.scan(SoloGame.ScanTier.QUICK)).isEmpty();
        }

        @Test
        @DisplayName("UI-6: a running scan HOLDS its cycles — they do not start recovering yet")
        void scanHoldsWhileItRuns(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            assertThat(game.scan(SoloGame.ScanTier.THOROUGH)).isPresent();

            ComputeBudget budget = game.computeBudget();
            // The point of the decision: while the scan runs the cycles are gone, not coming back.
            assertThat(budget.recovering()).isEqualTo(Cycles.of(0));
            assertThat(budget.allocated()).isEqualTo(Cycles.of(Balance.SCAN_THOROUGH_CYCLES));
            assertThat(budget.available()).isEqualTo(Cycles.of(100 - Balance.SCAN_THOROUGH_CYCLES));
            assertThat(budget.reconciles()).isTrue();
        }

        @Test
        @DisplayName("UI-6: the cycles start recovering only once the scan ends")
        void scanRecoversAfterItEnds(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            game.scan(SoloGame.ScanTier.THOROUGH);

            // Just short of the published ~6 min: still held, still not recovering.
            clock.advance(Duration.ofSeconds(SoloGame.ScanTier.THOROUGH.seconds() - 5));
            game.tick();
            assertThat(game.computeBudget().allocated()).isEqualTo(Cycles.of(Balance.SCAN_THOROUGH_CYCLES));
            assertThat(game.computeBudget().recovering()).isEqualTo(Cycles.of(0));

            // Past the end: the scan is done and NOW the thermal curve starts.
            clock.advance(Duration.ofSeconds(10));
            game.tick();
            assertThat(game.computeBudget().allocated()).isEqualTo(Cycles.of(0));
            assertThat(game.computeBudget().recovering()).isEqualTo(Cycles.of(Balance.SCAN_THOROUGH_CYCLES));
            assertThat(game.computeBudget().reconciles()).isTrue();
        }

        @Test
        @DisplayName("recovery is slower on a loaded rig — the Thermal Budget shape")
        void recoveryIsSlowerUnderLoad(@TempDir Path dir, @TempDir Path dir2) {
            // Under hold-then-recover, recoversAt only exists after the scan has finished — so both
            // rigs have to be run past the tier's duration before there is anything to compare.
            Instant idleReady = recoveryDeadlineAfterFullScan(dir, 0);
            Instant busyReady = recoveryDeadlineAfterFullScan(dir2, 80);

            // design/01 §1.3: "slower the closer the rig sits to capacity". This is the whole
            // reason over-committing compounds rather than merely costing.
            assertThat(busyReady).isAfter(idleReady);
        }

        /** Runs a Full Scan to completion on a rig carrying {@code selfMining} cycles. */
        private Instant recoveryDeadlineAfterFullScan(Path dir, int selfMining) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            if (selfMining > 0) {
                game.allocateSelfMining(selfMining);
            }
            game.scan(SoloGame.ScanTier.FULL);
            clock.advance(Duration.ofSeconds(SoloGame.ScanTier.FULL.seconds() + 1));
            game.tick();
            return game.state().rig.allocations.stream()
                    .filter(a -> "RECOVERING".equals(a.state))
                    .findFirst()
                    .orElseThrow()
                    .recoversAt;
        }

        @Test
        @DisplayName("recovered cycles come back once their time has passed")
        void recoveredCyclesReturn(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            game.scan(SoloGame.ScanTier.QUICK);
            assertThat(game.computeBudget().available()).isEqualTo(Cycles.of(95));

            clock.advance(Duration.ofHours(1));
            game.tick();
            assertThat(game.computeBudget().available()).isEqualTo(Cycles.of(100));
            assertThat(game.computeBudget().recovering()).isEqualTo(Cycles.of(0));
        }

        @Test
        @DisplayName("UI-6: a scan that finished while the game was closed recovers from when it ended")
        void offlineScanDoesNotRestartItsRecoveryClock(@TempDir Path dir) {
            Path save = dir.resolve("save.json");
            TestClock clock = new TestClock(T0);
            SoloGame game = bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(save), clock);
            game.scan(SoloGame.ScanTier.THOROUGH);
            game.persist();

            // A week away. The scan ended six minutes in and its recovery finished long before now,
            // so the rig must be whole — not still nursing Tuesday's scan in front of the player.
            TestClock later = new TestClock(T0.plus(Duration.ofDays(7)));
            SoloGame resumed = bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(save), later);
            assertThat(resumed.computeBudget().available()).isEqualTo(Cycles.of(100));
            assertThat(resumed.computeBudget().recovering()).isEqualTo(Cycles.of(0));
            assertThat(resumed.tasks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("income")
    class Income {

        @Test
        @DisplayName("a full rig self-mines 40 EC/hr — the design/03 §1 figure")
        void selfMiningRate(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            game.allocateSelfMining(100);

            // ⚠ Since 2026-07-27 this is a Poisson process, not a rate, so the EXPECTATION is the
            // thing that is exactly 40 EC/hr and a single simulated hour is a sample around it.
            // Asserting both: the published figure is pinned to the minor unit, because that is the
            // number docs/design/03 §1 prices the whole economy against, and the simulation is
            // checked to actually track it.
            // ⚠ To double precision. The rate is derived through the network hashrate, which is a
            // double, so it is exact to ~16 significant figures and no further — at 18 decimals that
            // is a residue of ~2000 wei in 4e19. See MiningChainTest.defaultPoolIsTheAnchor.
            assertThat(ec(game.mining().expectedWeiPerHour())).isCloseTo(40.0d, withinPercentage(1e-10d));

            for (int hour = 0; hour < 24; hour++) {
                clock.advance(Duration.ofHours(1));
                game.tick();
            }
            // 24 hours is about 2880 pool shares; a 6% band is roughly three standard errors.
            // ⚠ Compared in EC, not wei. The band is a statistical statement about a day's pooled
            // income — 900-1020 EC — and eighteen-digit bounds would say the same thing unreadably.
            assertThat(ec(game.balance().wei())).isBetween(900.0d, 1_020.0d);
        }

        @Test
        @DisplayName("pooled mining never has an empty hour, which is what makes it the floor (I4)")
        void pooledIsAFloor(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            game.allocateSelfMining(100);

            double previous = 0;
            for (int hour = 0; hour < 12; hour++) {
                clock.advance(Duration.ofHours(1));
                game.tick();
                double now = ec(game.balance().wei());
                // docs/design/04 §1.1: heat can destroy a deployment network but never the floor.
                // A floor with dry hours in it is not one, and a player who went hot and then earned
                // nothing for an hour would be punished twice for the same mistake.
                assertThat(now).as("hour %d", hour).isGreaterThan(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("solo mining pays in rare lumps, and the choice is the player's")
        void soloIsALottery(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game =
                    bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")), clock);
            game.allocateSelfMining(100);
            assertThat(game.setMiningMode(MiningMode.SOLO)).isTrue();

            // Same rig, same cycles, and now a payout worth four hours of the pooled rate arriving
            // about once every four hours. Nothing was bought and nothing was unlocked — Invariants
            // I1 and I2 are untouched, because the only thing that changed is where the cycles point.
            // Subsidy plus the block's fees — a solo miner keeps the whole block, and since
            // 2026-07-27 a block is worth both halves of what its miner collects.
            // ⚠ Exact, in wei: this is an identity rather than an estimate — a solo payout IS the
            // subsidy plus the block's fees, and rounding it to compare would hide a real drift.
            assertThat(game.mining().payoutWei())
                    .isEqualTo(Balance.BLOCK_SUBSIDY_WEI.add(Balance.expectedBlockFeesWei()));
            assertThat(game.mining().expectedPayoutSeconds()).isBetween(13_000.0d, 15_000.0d);
            assertThat(game.mining().chanceWithin(3600)).isBetween(0.15d, 0.35d);

            int dry = 0;
            for (int hour = 0; hour < 24; hour++) {
                java.math.BigInteger before = game.balance().wei();
                clock.advance(Duration.ofHours(1));
                game.tick();
                if (game.balance().wei().equals(before)) {
                    dry++;
                }
            }
            // Most hours pay nothing at all. That is the trade, and it is why pooled is the default.
            assertThat(dry).isGreaterThan(12);
        }

        /**
         * The amended I5, and the one assertion that keeps it honest.
         *
         * <h2>⚠ This test used to assert ZERO, and the change is a design decision, not a fix</h2>
         *
         * I5 read "self-mining runs online-only" and this asserted a week away paid nothing. It was
         * amended on 2026-07-29 ({@code docs/design/15-open-questions.md} §3): the rig keeps hashing
         * for {@code Balance.OFFLINE_MINING_HOURS} after the client closes and then stops dead.
         *
         * <p>What the old rule actually protected against was <b>absence out-earning play</b> on an
         * income stream that is also zero-heat and unseizable (I4) — and a cap defeats that on its
         * own. So the assertion that matters is no longer "nothing" but <b>"a month away pays what
         * four hours pays"</b>: the moment income tracks the absence, it is farmable and the
         * invariant is gone.
         *
         * <h2>⚠ Asserted structurally first, and only then on the money</h2>
         *
         * The two structural figures — how long the rig was credited as hashing, and how many blocks
         * it was in the draw for — are <b>exact</b> and are what the cap actually does. The balances
         * are then compared as a band rather than for equality, because they legitimately differ: a
         * longer fill walks more blocks, each block consumes RNG draws, and the pay-per-share clock
         * downstream therefore lands on different share targets. That is realised variance on an
         * unchanged expectation, and it is the same variance an online session has. Asserting exact
         * equality there would be asserting that the generator is not being used.
         */
        @Test
        @DisplayName("INVARIANT I5 — a month away pays no more than the spin-down window")
        void offlineSelfMiningIsCappedNotProportional(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            SoloGame first = bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), new TestClock(T0));
            first.allocateSelfMining(100);
            first.persist();
            byte[] saved = Files.readAllBytes(file);

            // The same save reopened after four hours, a day, a week and a month. Same seed, same
            // rig, same chain — the only difference is how long the client was shut, which is
            // exactly the thing that must stop mattering at the cap.
            double atCap = 0.0d;
            for (Duration away : List.of(
                    Duration.ofHours(Balance.OFFLINE_MINING_HOURS),
                    Duration.ofDays(1),
                    Duration.ofDays(7),
                    Duration.ofDays(30))) {
                Path each = dir.resolve("away-" + away.toHours() + ".json");
                Files.write(each, saved);
                SoloGame game = bare(
                        new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(each),
                        new TestClock(T0.plus(away)));

                assertThat(game.chainSync().minedSeconds())
                        .as("the rig is credited with the window, never the absence (%s)", away)
                        .isEqualTo(Balance.offlineMiningSeconds());
                // ⚠ False at exactly the window, and correctly so — nothing has been withheld from a
                // player who came back the moment the rig stopped. capped() answers "did the cap
                // bite", which is a different question from "was the window applied".
                assertThat(game.chainSync().capped()).isEqualTo(away.getSeconds() > Balance.offlineMiningSeconds());

                if (atCap == 0.0d) {
                    atCap = ec(game.balance().wei());
                    assertThat(atCap).isPositive();
                    continue;
                }
                // The chain ran the whole time — filling the blocks in is the change. Paying for
                // them is what the cap refuses.
                //
                // ⚠ Asserted as a RATE against the target interval, not as a floor. A flat "more
                // than 100 blocks" is both weaker and wrong: it says nothing about a day's fill
                // arriving at the right pace, and 24h expects ~103 blocks with σ ≈ 10, so it fails
                // on ordinary variance about a third of the time. This band is ±3σ at the shortest
                // absence tested and far wider than needed at the longest.
                double expected = away.getSeconds() / (double) Balance.CHAIN_TARGET_BLOCK_SECONDS;
                assertThat(game.chainSync().blocks())
                        .as("the chain fills in at its own block interval (%s)", away)
                        .isBetween((int) (expected * 0.7d), (int) (expected * 1.3d));
                assertThat(ec(game.balance().wei()))
                        .as("income must not track the absence (%s)", away)
                        .isCloseTo(atCap, withinPercentage(20.0d));
            }
        }

        @Test
        @DisplayName("the rig is absent from every block after it spins down")
        void nothingIsWonPastTheCap(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame first = bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), new TestClock(T0));
            first.setMiningMode(MiningMode.SOLO);
            first.allocateSelfMining(100);
            first.persist();

            // Three days away. At a ~4% share and a 14-minute block that is ~300 blocks the rig
            // would have expected to win a dozen of — it must win none of them past hour four.
            SoloGame later = bare(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file),
                    new TestClock(T0.plus(Duration.ofDays(3))));
            var sync = later.chainSync();

            // ⚠ A 4σ Poisson bound, not a flat "+8". How many blocks land inside a FIXED four-hour
            // window is itself random — mean 17.1, σ = √17.1 ≈ 4.1 — so the old ceiling of 26 sat
            // 2.1σ above the mean and failed about one run in fifty on ordinary variance, on a
            // fixture whose seed is fresh every run. Caught in a routine build; the assertion was
            // measuring the Poisson tail rather than the cap. What the test is actually about is
            // that the window bounds the contest at all, which the uncontested count below carries.
            double window = Balance.offlineMiningSeconds() / (double) Balance.CHAIN_TARGET_BLOCK_SECONDS;
            assertThat(sync.competedBlocks())
                    .as("only blocks inside the spin-down window are contested")
                    .isLessThanOrEqualTo((int) Math.ceil(window + 4 * Math.sqrt(window)));
            assertThat(sync.uncontestedBlocks()).isGreaterThan(200);
            // Every contributor row the fill produced is inside the window, by construction.
            assertThat(later.contributions(64))
                    .allSatisfy(row ->
                            assertThat(row.height()).isLessThanOrEqualTo(sync.fromHeight() + sync.competedBlocks()));
        }

        @Test
        @DisplayName("INVARIANT I5 — a deployed miner does accrue while away, up to the cap")
        void deployedMinersAreTheOnlyOfflineIncome(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), new TestClock(T0));

            NodeState node = new NodeState();
            node.address = "10.0.0.7";
            MinerState miner = new MinerState();
            miner.hostCycles = 10;
            miner.deployedAt = T0;
            miner.lastAccruedAt = T0;
            node.deployedMiners.add(miner);
            game.state().knownNodes.add(node);
            game.persist();

            SoloGame later = bare(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file),
                    new TestClock(T0.plus(Duration.ofDays(7))));
            MinerState after =
                    later.state().knownNodes.getFirst().deployedMiners.getFirst();

            BigInteger cap = MiningRules.bufferCap(after);
            assertThat(after.bufferedWei).isEqualTo(cap);
            // A week away yields four hours, not a week: 10 cycles × 0.4 EC × 4 hr = 16 EC.
            // 10 host cycles × 0.4 EC/cycle-hour × the buffer window. Written from the rate rather
            // than from a literal so a re-tune of either moves this with it.
            assertThat(cap)
                    .isEqualTo(Balance.SELF_MINING_WEI_PER_CYCLE_HOUR.multiply(
                            java.math.BigInteger.valueOf(10L * Balance.YIELD_BUFFER_HOURS)));
        }

        @Test
        @DisplayName("INVARIANT I6 — a deployed miner costs the host's compute, not the deployer's")
        void minerSpendsHostCompute(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            NodeState node = new NodeState();
            node.address = "10.0.0.7";
            MinerState miner = new MinerState();
            miner.hostCycles = 40;
            node.deployedMiners.add(miner);
            game.state().knownNodes.add(node);

            // 40 cycles of mining work happens on someone else's machine. The deployer's rig is
            // untouched by it — only the control channel is theirs to pay, and that is charged
            // separately when the miner is deployed.
            assertThat(game.computeBudget().available()).isEqualTo(Cycles.of(Balance.STARTING_CYCLES));
        }

        @Test
        @DisplayName("collect sweeps every buffer into the balance and writes one ledger row")
        void collectSweepsBuffers(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            NodeState node = new NodeState();
            MinerState miner = new MinerState();
            miner.bufferedWei = Balance.ec("25");
            node.deployedMiners.add(miner);
            game.state().knownNodes.add(node);

            assertThat(game.collect()).isEqualTo(Balance.ec("25"));
            assertThat(game.balance().wei()).isEqualTo(Balance.ec("25"));
            assertThat(miner.bufferedWei).isZero();
            assertThat(game.state().ledger).hasSize(1);
            // The balance and the log that explains it are written by one method, so they cannot
            // disagree — which is what makes the ledger usable as a readout.
            assertThat(game.state().ledger.getFirst().balanceAfterWei).isEqualTo(Balance.ec("25"));
        }

        @Test
        @DisplayName("spending more than you have is refused, not overdrawn")
        void cannotOverdraw(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            game.credit(Balance.ec("10"), "TEST", "seed");

            assertThat(game.debit(Balance.ec("20"), "TEST", "too much")).isFalse();
            assertThat(game.balance().wei()).isEqualTo(Balance.ec("10"));
            assertThat(game.state().ledger).hasSize(1);
        }
    }

    @Nested
    @DisplayName("the save file")
    class Saves {

        @Test
        @DisplayName("a save round-trips through the file")
        void roundTrip(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = SoloGame.open(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), "ghost", new TestClock(T0));
            game.credit(Balance.ec("12.34"), "TEST", "seed");
            game.allocateSelfMining(25);
            game.persist();

            SoloSave reloaded = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file).load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.handle).isEqualTo("ghost");
            assertThat(reloaded.ethecoinWei).isEqualTo(Balance.ec("12.34"));
            assertThat(reloaded.rig.selfMiningCycles).isEqualTo(25L);
            assertThat(reloaded.ledger).hasSize(1);
        }

        @Test
        @DisplayName("timestamps are written as readable ISO-8601, not epoch numbers")
        void timestampsAreReadable(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), new TestClock(T0))
                    .persist();

            // It is the player's file on the player's disk; they should be able to read it.
            assertThat(Files.readString(file)).contains("2026-");
        }

        @Test
        @DisplayName("a save from a newer build is refused rather than half-read")
        void futureSaveIsRefused(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            Files.writeString(file, "{\"format\":9999,\"handle\":\"from-the-future\"}");

            assertThatThrownBy(() -> new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file).load())
                    .isInstanceOf(SaveStore.UnreadableSaveException.class)
                    .hasMessageContaining("9999");
        }

        @Test
        @DisplayName("a corrupt save is refused rather than partially applied")
        void corruptSaveIsRefused(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            Files.writeString(file, "{ this is not json");

            assertThatThrownBy(() -> new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file).load())
                    .isInstanceOf(SaveStore.UnreadableSaveException.class);
        }

        @Test
        @DisplayName("no temporary file is left behind after a save")
        void noTempFileLeftBehind(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            bare(new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(file), new TestClock(T0))
                    .persist();

            try (var entries = Files.list(dir)) {
                assertThat(entries.map(p -> p.getFileName().toString())).containsExactly("save.json");
            }
        }

        @Test
        @DisplayName("a solo character is never federable, and nothing offers to change that")
        void soloCharactersNeverFederate(@TempDir Path dir) {
            // Invariant I14 is preserved not by trusting this file but by ensuring nothing
            // downstream ever does. A save created here has no route into the federated economy.
            assertThat(freshGame(dir).state().federable).isFalse();
        }
    }

    @Nested
    @DisplayName("rules arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("a short session still earns something rather than truncating to zero")
        void shortSessionsEarn() {
            // Naive hour-based integer maths would pay nothing for anything under an hour, so a
            // player doing five-minute sessions would earn nothing at all and never know why.
            BigInteger tenMinutes = MiningRules.deployedYield(100, Duration.ofMinutes(10));
            // 100 cycles × 0.4 EC/cycle-hour × (1/6) hour = 6.666… EC, floored to the wei.
            assertThat(tenMinutes).isEqualTo(Balance.ec("6.666666666666666666"));
        }

        @Test
        @DisplayName("zero allocation earns zero")
        void zeroEarnsZero() {
            assertThat(MiningRules.deployedYield(0, Duration.ofHours(5))).isZero();
            assertThat(MiningRules.deployedYield(100, Duration.ZERO)).isZero();
        }

        @Test
        @DisplayName("available cycles never go negative")
        void availableNeverNegative() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            save.rig.selfMiningCycles = 5_000L; // hand-edited save, which is a thing that happens
            assertThat(ComputeRules.availableCycles(save.rig)).isZero();
        }
    }

    @Nested
    @DisplayName("the breach")
    class Breach {

        @Test
        @DisplayName("a new character is born with a parasite, and Invariant I6 makes the HOST pay")
        void tutorialMinerCostsTheHost(@TempDir Path dir) {
            // Not decoration: docs/design/04 §5.1 makes cracking a miner the tutorial for the whole
            // breach system, and without one planted here the core loop is unreachable on a fresh
            // save. It also makes §3.1's audit mechanic true on day one — the ledger no longer adds
            // up, so there is finally a discrepancy to notice.
            SoloGame game = SoloGame.open(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                    "operator",
                    new TestClock(T0));

            assertThat(game.state().rig.foreignMiners).hasSize(1);
            assertThat(game.computeBudget().total()).isEqualTo(Cycles.of(Balance.STARTING_CYCLES));
            assertThat(game.computeBudget().available())
                    .as("Invariant I6: a deployed miner spends the host's cycles, not the deployer's")
                    .isEqualTo(Cycles.of(Balance.STARTING_CYCLES - Balance.TUTORIAL_MINER_HOST_CYCLES));

            // ⚠ The ledger does NOT reconcile, and that is now the point rather than a bug.
            //
            // This assertion used to read isTrue(), because the parasite's allocation was published
            // from the moment it was planted — so the rig monitor said "Foreign miner 6C" to a player
            // who had never run an audit, which hands them free the one thing docs/design/04 §3.2
            // sells the whole scan ladder for. An UNDISCOVERED parasite is omitted from the snapshot
            // instead: the cycles are gone, nothing attributes them, and claimed + recovering + free
            // comes up exactly six short of the rig's ceiling.
            //
            // That gap IS §3.1's "second-strongest tutorial vector" — the player notices the numbers
            // do not add up, and nobody tells them why.
            assertThat(game.computeBudget().reconciles())
                    .as("an unaudited parasite is unattributed, so the ledger is short by its appetite")
                    .isFalse();
            assertThat(game.computeBudget().unaccountedFor()).isEqualTo(Cycles.of(Balance.TUTORIAL_MINER_HOST_CYCLES));
        }

        @Test
        @DisplayName("an audit that names the parasite is what makes its cycles appear on the readout")
        void auditingAttributesTheTheft(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game = SoloGame.open(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                    "operator",
                    clock);

            // A Thorough Scan sees everything, including a rootkit-wrapped miner (docs/design/04
            // §3.2). Before it lands the theft is real and unattributed; after it lands the same
            // cycles are a named row and the ledger balances again.
            assertThat(game.scan(SoloGame.ScanTier.THOROUGH)).isPresent();
            assertThat(game.state().rig.foreignMiners.getFirst().discovered).isFalse();

            clock.advance(Duration.ofHours(1));
            game.tick();

            assertThat(game.state().rig.foreignMiners.getFirst().discovered)
                    .as("the audit is the only thing in the engine that sets this")
                    .isTrue();
            assertThat(game.computeBudget().reconciles())
                    .as("a named parasite is attributed, so the readout adds up again")
                    .isTrue();
        }

        @Test
        @DisplayName("that parasite becomes a breach target once an audit has found it — not before")
        void tutorialMinerIsABreachTargetAfterTheAudit(@TempDir Path dir) {
            TestClock clock = new TestClock(T0);
            SoloGame game = SoloGame.open(
                    new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                    "operator",
                    clock);

            // ⚠ This used to assert a target on the first frame. Listing an unaudited parasite told
            // the player a process was stealing from them at the same moment the rig monitor was
            // being careful not to — two windows disagreeing about what the player knows, which is
            // worse than either answer on its own. The pipeline is now the one docs/design/04 §3.1
            // and §3.2 actually describe: notice, audit, then crack.
            assertThat(game.breachTargets()).isEmpty();

            // `--full`, not `--quick`: the tutorial miner is T1 and a Quick Scan sees unhidden T2+
            // only (§3.2). The cheap scan genuinely cannot find it, which is the ladder working.
            assertThat(game.scan(SoloGame.ScanTier.FULL)).isPresent();
            clock.advance(Duration.ofHours(1));
            game.tick();

            assertThat(game.breachTargets()).hasSize(1);
            assertThat(game.breachTargets().getFirst().minerCrack()).isTrue();
        }
    }
}
