package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for how loud the rig is.
 *
 * <p>Two things are being defended and both are load-bearing. The first is that <b>a busy rig is not
 * a loud rig</b> — Invariants I4 and I9 and {@code docs/design/04-mining.md} §3.1 each make one kind
 * of work silent, and together they are the quiet-play strategy the economy is built to reward. The
 * second is that <b>loudness is present-tense</b>: a sweep is one of the loudest acts in the game and
 * contributes exactly nothing the moment it ends.
 */
class NoiseRulesTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    private static SoloSave rig() {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 100L;
        return save;
    }

    private static void allocate(SoloSave save, ComputeConsumer consumer, long cycles, boolean recovering) {
        AllocationState allocation = new AllocationState();
        allocation.consumer = consumer.name();
        allocation.cycles = cycles;
        allocation.state = recovering ? "RECOVERING" : "ACTIVE";
        save.rig.allocations.add(allocation);
    }

    private static TaskState sweep(SoloSave save, long noise, long seconds) {
        TaskState task = new TaskState("sweep", "sweep", "", 2L, T0, T0.plusSeconds(seconds));
        task.noiseCycles = noise;
        save.tasks.add(task);
        return task;
    }

    @Nested
    @DisplayName("a busy rig is not a loud rig")
    class Quiet {

        @Test
        @DisplayName("a rig at full load on self-mining, defences and local tools reads zero")
        void theQuietStrategyIsQuiet() {
            SoloSave save = rig();
            save.rig.selfMiningCycles = 60L;
            allocate(save, ComputeConsumer.DEFENSIVE_ARRAY, 25L, false);
            allocate(save, ComputeConsumer.ACTIVE_TOOL, 15L, false);

            // The headline result of the whole model. I4 makes self-mining silent (a floor that made
            // you loud would not be one), I9 makes defence silent (being wanted must come from
            // aggression), and scanning your own rig is free and silent. All three at once is a rig
            // running flat out and radiating nothing.
            assertThat(NoiseRules.level(save, T0)).isZero();
        }

        @Test
        @DisplayName("a foreign miner on your own rig is not your noise")
        void beingAVictimIsNotBeingLoud() {
            SoloSave save = rig();
            allocate(save, ComputeConsumer.DEPLOYED_MINER, 30L, false);

            // By I6 a deployed miner is charged to the HOST's rig, so seeing one in your own budget
            // means somebody else's miner is running on you. Counting it would make being a victim
            // look like being an aggressor.
            assertThat(NoiseRules.level(save, T0)).isZero();
        }

        @Test
        @DisplayName("work that reaches other machines is loud")
        void outwardWorkCounts() {
            SoloSave save = rig();
            allocate(save, ComputeConsumer.CONTROL_CHANNEL, 20L, false);
            allocate(save, ComputeConsumer.RELAY_HOP, 5L, false);
            assertThat(NoiseRules.outwardCycles(save, T0)).isEqualTo(25L);
            assertThat(NoiseRules.level(save, T0)).isEqualTo(0.25d);
        }

        @Test
        @DisplayName("cycles already on their way back are not still shouting")
        void recoveringIsSilent() {
            SoloSave save = rig();
            allocate(save, ComputeConsumer.CONTROL_CHANNEL, 20L, true);
            assertThat(NoiseRules.level(save, T0)).isZero();
        }

        @Test
        @DisplayName("a consumer this build never wrote is silent rather than invented")
        void unknownConsumer() {
            SoloSave save = rig();
            AllocationState allocation = new AllocationState();
            allocation.consumer = "SOMETHING_A_TEXT_EDITOR_PUT_HERE";
            allocation.cycles = 40L;
            save.rig.allocations.add(allocation);
            assertThat(NoiseRules.level(save, T0)).isZero();
        }
    }

    @Nested
    @DisplayName("a sweep is cheap and loud, and only while it runs")
    class Sweeps {

        @Test
        @DisplayName("a running sweep is loud out of all proportion to the cycles it holds")
        void loudWhileRunning() {
            SoloSave save = rig();
            // What the rules actually do: reserve the tier's cycles AND declare its noise.
            allocate(save, ComputeConsumer.CONTROL_CHANNEL, Balance.NET_SWEEP_BASE_CYCLES, false);
            sweep(save, Balance.NET_SWEEP_BASE_NOISE, 20);

            // ⚠ The regression this test exists for. Noise used to be the cycle count alone, so a
            // base sweep moved a 100-cycle rig's meter by two percent — indistinguishable from
            // silence, and quieter the bigger the rig grew. It is now over a third.
            assertThat(NoiseRules.level(save, T0)).isGreaterThan(0.3d);
        }

        @Test
        @DisplayName("the moment it ends it contributes nothing — noise is a rate, not a debt")
        void silentAfterwards() {
            SoloSave save = rig();
            sweep(save, Balance.NET_SWEEP_DEEP_NOISE, 90);

            assertThat(NoiseRules.level(save, T0.plusSeconds(89))).isGreaterThan(0.0d);
            // At endsAt exactly, not a moment later: `isFinishedAt` is inclusive, and a task that is
            // over but not yet settled sits on save.tasks for up to a frame. Counting it there would
            // hold the meter past the countdown reaching zero.
            assertThat(NoiseRules.level(save, T0.plusSeconds(90))).isZero();
            assertThat(NoiseRules.level(save, T0.plusSeconds(900))).isZero();
        }

        @Test
        @DisplayName("the ladder is loudness as well as sensitivity")
        void louderTiersAreLouder() {
            assertThat(Balance.NET_SWEEP_BASE_NOISE)
                    .isLessThan(Balance.NET_SWEEP_WIDE_NOISE);
            assertThat(Balance.NET_SWEEP_WIDE_NOISE)
                    .isLessThan(Balance.NET_SWEEP_DEEP_NOISE);
            // Below a full rig even at the top, so a player can still read a sweep running ON TOP of
            // something else. A tier that pinned the meter would erase that distinction.
            assertThat(Balance.NET_SWEEP_DEEP_NOISE).isLessThan(100L);
        }

        @Test
        @DisplayName("two sweeps at once are twice the racket, clamped at a full meter")
        void summedAndClamped() {
            SoloSave save = rig();
            sweep(save, Balance.NET_SWEEP_DEEP_NOISE, 90);
            sweep(save, Balance.NET_SWEEP_DEEP_NOISE, 90);
            assertThat(NoiseRules.outwardCycles(save, T0))
                    .isEqualTo(2 * Balance.NET_SWEEP_DEEP_NOISE);
            assertThat(NoiseRules.level(save, T0)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("a task from an older save states no loudness and so adds none")
        void oldTasksAreSilent() {
            SoloSave save = rig();
            save.tasks.add(new TaskState("scan", "scan", "", 35L, T0, T0.plusSeconds(360)));
            assertThat(NoiseRules.level(save, T0)).isZero();
        }
    }

    @Test
    @DisplayName("a rig with no capacity reads zero rather than dividing by it")
    void noCapacity() {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 0L;
        assertThat(NoiseRules.level(save, T0)).isZero();
        assertThat(NoiseRules.level(null, T0)).isZero();
    }
}
