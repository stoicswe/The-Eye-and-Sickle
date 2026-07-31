package io.github.stoicswe.eyeandsickle.solo.proc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.net.NetRules;
import io.github.stoicswe.eyeandsickle.solo.net.SweepTier;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What killing a row costs, and what it buys.
 *
 * <h2>The one price, stated three ways</h2>
 *
 * Stopping work early <b>buys back time and never capacity</b>. A killed tool keeps what it managed
 * and its cycles still take the full thermal recovery; a restarted daemon charges the same to
 * everything that depended on it; a parasite gives its cycles back and takes its buffer with it. If
 * any of those became free, "start everything and kill the losers" would be the optimal opening move
 * and the compute economy would stop meaning anything.
 */
class ProcessRulesTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    private static SoloSave bare() {
        SoloSave save = SoloGame.newCharacter("operator", T0);
        for (MinerState miner : java.util.List.copyOf(save.rig.foreignMiners)) {
            ComputeRules.release(save.rig, miner.allocationId);
        }
        save.rig.foreignMiners.clear();
        return save;
    }

    /** A running audit that had named two parasites by the time it was interrupted. */
    private static TaskState scanInFlight(SoloSave save, String... foundIds) {
        AllocationState allocation = ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "scan --full", 15L);
        allocation.startedAt = T0;
        TaskState task = new TaskState("scan", "scan --full", allocation.allocationId, 15L, T0, T0.plusSeconds(100));
        task.outcome = foundIds.length + " foreign miners found.";
        task.foundMinerIds = new java.util.ArrayList<>(java.util.List.of(foundIds));
        save.tasks.add(task);
        return task;
    }

    @Nested
    @DisplayName("killing a tool of your own")
    class KillTool {

        @Test
        @DisplayName("it stops where it is and the cycles still recover in full")
        void timeBackNeverCapacity() {
            SoloSave save = bare();
            TaskState task = scanInFlight(save);

            ProcessRules.Outcome outcome = ProcessRules.kill(save, "task:" + task.taskId, T0.plusSeconds(50));

            assertThat(outcome.refused()).isFalse();
            assertThat(save.tasks).isEmpty();
            // ⚠ The whole price. The allocation is RECOVERING for its full 15 cycles, dated from the
            // kill — not shortened, not refunded. Stopping early returns the player's session time.
            assertThat(ComputeRules.recoveringCycles(save.rig)).isEqualTo(15L);
            assertThat(ComputeRules.availableCycles(save.rig)).isEqualTo(save.rig.totalCycles - 15L);
        }

        @Test
        @DisplayName("a half-finished audit names half of what it would have")
        void partialResults() {
            SoloSave save = bare();
            MinerState a = plant(save, "one");
            MinerState b = plant(save, "two");
            TaskState task = scanInFlight(save, a.minerId, b.minerId);

            ProcessRules.kill(save, "task:" + task.taskId, T0.plusSeconds(50));

            // Half the run, half the names — and it is the FIRST half of the frozen answer rather
            // than a fresh smaller roll, which is what stops a kill being a re-roll a player could
            // force at will.
            assertThat(a.discovered).isTrue();
            assertThat(b.discovered).isFalse();
        }

        @Test
        @DisplayName("a partial audit says it is partial rather than reporting a clean rig")
        void partialIsNotClean() {
            SoloSave save = bare();
            MinerState only = plant(save, "one");
            TaskState task = scanInFlight(save, only.minerId);

            ProcessRules.kill(save, "task:" + task.taskId, T0.plusSeconds(1));

            // A partial audit reporting a clean bill of health is a lie the player would reasonably
            // act on — and the difference between "clean" and "unfinished" is the whole value of the
            // result.
            assertThat(save.log.stream().map(e -> e.message))
                    .anySatisfy(message -> assertThat(message).contains("partial"));
            assertThat(only.discovered).isFalse();
        }

        @Test
        @DisplayName("a killed sweep keeps the machines it reached and provokes no counter-hack")
        void partialSweep() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            TaskState task = NetRules.beginSweep(save, SweepTier.BASE, T0).orElseThrow();
            int wouldFind = NetRules.report(task).found();

            ProcessRules.kill(save, "task:" + task.taskId, T0.plusSeconds(2));

            assertThat(save.knownNodes.size()).isLessThanOrEqualTo(wouldFind);
            // The counter-hack is the network answering a sweep that RAN. One the player pulled the
            // plug on did not finish provoking anybody, which is a real reason to kill a deep sweep
            // that is making you nervous.
            assertThat(save.rig.foreignMiners).hasSize(1);
        }
    }

    @Nested
    @DisplayName("killing a parasite")
    class KillParasite {

        @Test
        @DisplayName("the cycles come back and the buffer does not")
        void killIsNotACrack() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            MinerState miner = save.rig.foreignMiners.getFirst();
            miner.bufferedWei = Balance.ec("50");
            java.math.BigInteger balance = save.ethecoinWei;
            long free = ComputeRules.availableCycles(save.rig);

            assertThat(ProcessRules.kill(save, "miner:" + miner.minerId, T0).refused())
                    .isFalse();

            assertThat(save.rig.foreignMiners).isEmpty();
            assertThat(ComputeRules.availableCycles(save.rig)).isEqualTo(free + miner.hostCycles);
            // ⚠ docs/design/04 §5 prices four responses against each other. A kill that also swept
            // the buffer would collapse three of those four into one; what a kill buys is
            // immediacy — no breach, no attention, no puzzle.
            assertThat(save.ethecoinWei).isEqualTo(balance);
        }

        @Test
        @DisplayName("it can be killed without an audit — which is the point of the whole table")
        void noAuditRequired() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            MinerState miner = save.rig.foreignMiners.getFirst();
            assertThat(miner.discovered).isFalse();

            // docs/design/04 §3.1's "manual audit still sees things a scan does not" has been a
            // sentence with no mechanic behind it. This is the mechanic.
            assertThat(ProcessRules.kill(save, "miner:" + miner.minerId, T0).refused())
                    .isFalse();
            assertThat(save.rig.foreignMiners).isEmpty();
        }
    }

    @Nested
    @DisplayName("system processes")
    class System {

        @Test
        @DisplayName("kill is refused in words, and the refusal names the alternative")
        void neverKillable() {
            SoloSave save = bare();
            ProcessRules.Outcome outcome = ProcessRules.kill(save, "sys:netd", T0);
            assertThat(outcome.refused()).isTrue();
            assertThat(outcome.why()).contains("restarted");
        }

        @Test
        @DisplayName("restarting takes down the tools that depended on it, at the same price")
        void cascade() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            TaskState sweep = NetRules.beginSweep(save, SweepTier.BASE, T0).orElseThrow();

            ProcessRules.Outcome outcome = ProcessRules.restart(save, "sys:netd", T0.plusSeconds(5));

            // A sweep is packets on somebody else's machine and netd is what puts them there.
            assertThat(outcome.stopped()).isEqualTo(1);
            assertThat(save.tasks).doesNotContain(sweep);
            // Charged exactly as a direct kill is: full recovery for the full cycles. A free restart
            // would make "restart everything" the optimal opening move of every audit.
            assertThat(ComputeRules.recoveringCycles(save.rig)).isGreaterThanOrEqualTo(sweep.cycles);
        }

        @Test
        @DisplayName("restarting a daemon nothing depends on takes nothing with it")
        void leafDaemon() {
            SoloSave save = SoloGame.newCharacter("operator", T0);
            NetRules.beginSweep(save, SweepTier.BASE, T0);
            assertThat(ProcessRules.restart(save, "sys:logd", T0).stopped()).isZero();
            assertThat(save.tasks).hasSize(1);
        }

        @Test
        @DisplayName("an audit goes down with auditd, and a sweep does not")
        void dependenciesAreByKind() {
            SoloSave save = bare();
            TaskState scan = scanInFlight(save);
            assertThat(ProcessRules.restart(save, "sys:netd", T0).stopped()).isZero();
            assertThat(save.tasks).contains(scan);
            assertThat(ProcessRules.restart(save, "sys:auditd", T0.plusSeconds(1))
                            .stopped())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("restart is refused for anything that is not a system process")
        void restartIsSystemOnly() {
            SoloSave save = bare();
            assertThat(ProcessRules.restart(save, "task:whatever", T0).refused())
                    .isTrue();
            assertThat(ProcessRules.restart(save, "sys:nosuchd", T0).refused()).isTrue();
        }
    }

    @Nested
    @DisplayName("standing reservations")
    class Reservations {

        @Test
        @DisplayName("stopping self-mining forfeits the block in progress")
        void selfMining() {
            SoloSave save = bare();
            save.rig.selfMiningCycles = 40L;
            assertThat(ProcessRules.kill(save, "alloc:self-mining", T0).refused())
                    .isFalse();
            assertThat(save.rig.selfMiningCycles).isZero();
            assertThat(save.log.stream().map(e -> e.message))
                    .anySatisfy(message -> assertThat(message).contains("forfeit"));
        }

        @Test
        @DisplayName("cycles already on their way back have nothing left to stop")
        void recoveringIsNotKillable() {
            SoloSave save = bare();
            AllocationState allocation = ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "held", 9L);
            ComputeRules.beginRecovery(save.rig, allocation.allocationId, T0);
            ProcessRules.Outcome outcome =
                    ProcessRules.kill(save, "alloc:" + allocation.allocationId, T0.plusSeconds(1));
            assertThat(outcome.refused()).isTrue();
        }

        @Test
        @DisplayName("releasing a defence puts its cycles on the curve rather than handing them back")
        void releaseCosts() {
            SoloSave save = bare();
            AllocationState allocation =
                    ComputeRules.reserve(save.rig, ComputeConsumer.DEFENSIVE_ARRAY, "firewall", 10L);
            allocation.startedAt = T0;

            ProcessRules.kill(save, "alloc:" + allocation.allocationId, T0.plusSeconds(30));

            // Capacity that was committed is capacity that has to cool, however the commitment ended.
            assertThat(ComputeRules.recoveringCycles(save.rig)).isEqualTo(10L);
        }
    }

    @Test
    @DisplayName("an unknown id is refused rather than silently doing nothing")
    void unknownIds() {
        SoloSave save = bare();
        assertThat(ProcessRules.kill(save, "", T0).refused()).isTrue();
        assertThat(ProcessRules.kill(save, null, T0).refused()).isTrue();
        assertThat(ProcessRules.kill(save, "nonsense:1", T0).refused()).isTrue();
        assertThat(ProcessRules.kill(save, "task:not-a-task", T0).refused()).isTrue();
    }

    private static MinerState plant(SoloSave save, String label) {
        MinerState miner = new MinerState();
        miner.label = label;
        miner.hostCycles = 4L;
        miner.deployedAt = T0.minus(Duration.ofHours(1));
        miner.lastAccruedAt = miner.deployedAt;
        save.rig.foreignMiners.add(miner);
        return miner;
    }
}
