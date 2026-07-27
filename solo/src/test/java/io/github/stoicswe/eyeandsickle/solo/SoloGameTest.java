package io.github.stoicswe.eyeandsickle.solo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.MiningRules;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
        return SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", new TestClock(T0));
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
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", clock);
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
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", clock);
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
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", clock);
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
            SoloGame game = SoloGame.open(new SaveStore(save), "operator", clock);
            game.scan(SoloGame.ScanTier.THOROUGH);
            game.persist();

            // A week away. The scan ended six minutes in and its recovery finished long before now,
            // so the rig must be whole — not still nursing Tuesday's scan in front of the player.
            TestClock later = new TestClock(T0.plus(Duration.ofDays(7)));
            SoloGame resumed = SoloGame.open(new SaveStore(save), "operator", later);
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
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", clock);
            game.allocateSelfMining(100);
            clock.advance(Duration.ofHours(1));
            game.tick();

            // 100 cycles × 0.4 EC/cycle-hr = 40 EC = 4000 minor units.
            assertThat(game.balance().minorUnits()).isEqualTo(4_000L);
        }

        @Test
        @DisplayName("INVARIANT I5 — self-mining earns nothing while the client is closed")
        void selfMiningIsOnlineOnly(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            SoloGame first = SoloGame.open(new SaveStore(file), "operator", new TestClock(T0));
            first.allocateSelfMining(100);
            first.persist();

            // Reopen a week later. If self-mining paid out for time away it would be the safest AND
            // the best income in the game, and every other risk in the design would be optional.
            SoloGame later =
                    SoloGame.open(new SaveStore(file), "operator", new TestClock(T0.plus(Duration.ofDays(7))));
            assertThat(later.balance().minorUnits()).isZero();
            assertThat(Files.exists(file)).isTrue();
        }

        @Test
        @DisplayName("INVARIANT I5 — a deployed miner does accrue while away, up to the cap")
        void deployedMinersAreTheOnlyOfflineIncome(@TempDir Path dir) {
            Path file = dir.resolve("save.json");
            SoloGame game = SoloGame.open(new SaveStore(file), "operator", new TestClock(T0));

            NodeState node = new NodeState();
            node.address = "10.0.0.7";
            MinerState miner = new MinerState();
            miner.hostCycles = 10;
            miner.deployedAt = T0;
            miner.lastAccruedAt = T0;
            node.deployedMiners.add(miner);
            game.state().knownNodes.add(node);
            game.persist();

            SoloGame later =
                    SoloGame.open(new SaveStore(file), "operator", new TestClock(T0.plus(Duration.ofDays(7))));
            MinerState after = later.state().knownNodes.getFirst().deployedMiners.getFirst();

            long cap = MiningRules.bufferCap(after);
            assertThat(after.bufferedMinorUnits).isEqualTo(cap);
            // A week away yields four hours, not a week: 10 cycles × 0.4 EC × 4 hr = 16 EC.
            assertThat(cap).isEqualTo(10L * 40L * Balance.YIELD_BUFFER_HOURS);
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
            miner.bufferedMinorUnits = 2_500L;
            node.deployedMiners.add(miner);
            game.state().knownNodes.add(node);

            assertThat(game.collect()).isEqualTo(2_500L);
            assertThat(game.balance().minorUnits()).isEqualTo(2_500L);
            assertThat(miner.bufferedMinorUnits).isZero();
            assertThat(game.state().ledger).hasSize(1);
            // The balance and the log that explains it are written by one method, so they cannot
            // disagree — which is what makes the ledger usable as a readout.
            assertThat(game.state().ledger.getFirst().balanceAfterMinorUnits).isEqualTo(2_500L);
        }

        @Test
        @DisplayName("spending more than you have is refused, not overdrawn")
        void cannotOverdraw(@TempDir Path dir) {
            SoloGame game = freshGame(dir);
            game.credit(1_000L, "TEST", "seed");

            assertThat(game.debit(2_000L, "TEST", "too much")).isFalse();
            assertThat(game.balance().minorUnits()).isEqualTo(1_000L);
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
            SoloGame game = SoloGame.open(new SaveStore(file), "ghost", new TestClock(T0));
            game.credit(1_234L, "TEST", "seed");
            game.allocateSelfMining(25);
            game.persist();

            SoloSave reloaded = new SaveStore(file).load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.handle).isEqualTo("ghost");
            assertThat(reloaded.ethecoinMinorUnits).isEqualTo(1_234L);
            assertThat(reloaded.rig.selfMiningCycles).isEqualTo(25L);
            assertThat(reloaded.ledger).hasSize(1);
        }

        @Test
        @DisplayName("timestamps are written as readable ISO-8601, not epoch numbers")
        void timestampsAreReadable(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            SoloGame.open(new SaveStore(file), "operator", new TestClock(T0)).persist();

            // It is the player's file on the player's disk; they should be able to read it.
            assertThat(Files.readString(file)).contains("2026-");
        }

        @Test
        @DisplayName("a save from a newer build is refused rather than half-read")
        void futureSaveIsRefused(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            Files.writeString(file, "{\"format\":9999,\"handle\":\"from-the-future\"}");

            assertThatThrownBy(() -> new SaveStore(file).load())
                    .isInstanceOf(SaveStore.UnreadableSaveException.class)
                    .hasMessageContaining("9999");
        }

        @Test
        @DisplayName("a corrupt save is refused rather than partially applied")
        void corruptSaveIsRefused(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            Files.writeString(file, "{ this is not json");

            assertThatThrownBy(() -> new SaveStore(file).load())
                    .isInstanceOf(SaveStore.UnreadableSaveException.class);
        }

        @Test
        @DisplayName("no temporary file is left behind after a save")
        void noTempFileLeftBehind(@TempDir Path dir) throws IOException {
            Path file = dir.resolve("save.json");
            SoloGame.open(new SaveStore(file), "operator", new TestClock(T0)).persist();

            try (var entries = Files.list(dir)) {
                assertThat(entries.map(p -> p.getFileName().toString()))
                        .containsExactly("save.json");
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
            long tenMinutes = MiningRules.selfMiningYield(100, Duration.ofMinutes(10));
            assertThat(tenMinutes).isEqualTo(666L);
        }

        @Test
        @DisplayName("zero allocation earns zero")
        void zeroEarnsZero() {
            assertThat(MiningRules.selfMiningYield(0, Duration.ofHours(5))).isZero();
            assertThat(MiningRules.selfMiningYield(100, Duration.ZERO)).isZero();
        }

        @Test
        @DisplayName("available cycles never go negative")
        void availableNeverNegative() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            save.rig.selfMiningCycles = 5_000L; // hand-edited save, which is a thing that happens
            assertThat(ComputeRules.availableCycles(save.rig)).isZero();
        }
    }
}
