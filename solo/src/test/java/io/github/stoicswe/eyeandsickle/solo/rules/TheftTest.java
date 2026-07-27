package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a parasite costs before anybody has found it.
 *
 * <h2>The three consequences, and why they are all pre-audit</h2>
 *
 * A stolen cycle is stolen whether or not the player knows. So an unaudited parasite makes the rig
 * <b>slower</b> (every task's duration), makes it <b>slower to recover</b>
 * ({@code ThermalRules}), and makes work <b>refuse to start</b> that the visible numbers say should
 * fit. Those three are the entire evidence base for {@code docs/design/04-mining.md} §3.1's
 * discrepancy hunt — and the fourth thing, the readout naming the theft, is what §3.2 sells audits
 * for and must not happen until one runs.
 */
class TheftTest {

    private static SoloSave rig(long... minerCycles) {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 100L;
        for (long cycles : minerCycles) {
            MinerState miner = new MinerState();
            miner.hostCycles = cycles;
            miner.label = "unregistered process";
            save.rig.foreignMiners.add(miner);
        }
        return save;
    }

    /** Gives a parasite the {@code DEPLOYED_MINER} allocation it holds on a rig with room for it. */
    private static AllocationState hold(SoloSave save, MinerState miner) {
        AllocationState allocation = ComputeRules.reserve(
                save.rig, ComputeConsumer.DEPLOYED_MINER, miner.label, miner.hostCycles);
        miner.allocationId = allocation.allocationId;
        return allocation;
    }

    @Nested
    @DisplayName("what counts as stolen")
    class Accounting {

        @Test
        @DisplayName("a parasite's appetite counts even when the rig had no room to record it")
        void appetiteNotAllocation() {
            // NetRules.counterHack and Targets.plantTutorialMiner both plant a miner WITHOUT an
            // allocation when the rig is full, deliberately — "a parasite that declined to install
            // because the machine was busy would be the wrong lesson entirely". Reading the
            // DEPLOYED_MINER rows instead of the miners would make that parasite free.
            SoloSave save = rig(12L);
            assertThat(save.rig.allocations).isEmpty();
            assertThat(ComputeRules.stolenCycles(save.rig)).isEqualTo(12L);
            assertThat(ComputeRules.stolenShare(save.rig)).isEqualTo(0.12d);
        }

        @Test
        @DisplayName("several parasites add up, and the share never exceeds the rig")
        void sums() {
            assertThat(ComputeRules.stolenCycles(rig(10L, 20L, 5L).rig)).isEqualTo(35L);
            assertThat(ComputeRules.stolenShare(rig(80L, 80L).rig)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("a clean rig is clean, and a malformed one does not throw")
        void degenerate() {
            assertThat(ComputeRules.stolenShare(rig().rig)).isZero();
            assertThat(ComputeRules.stolenCycles(null)).isZero();
            assertThat(ComputeRules.stolenShare(null)).isZero();
        }
    }

    @Nested
    @DisplayName("tools run slower")
    class Slowdown {

        @Test
        @DisplayName("a clean rig runs everything at its published duration")
        void cleanIsPublished() {
            assertThat(ComputeRules.slowedSeconds(rig().rig, 20L)).isEqualTo(20L);
        }

        @Test
        @DisplayName("the penalty is proportional to what is being taken")
        void proportional() {
            // THEFT_SLOWDOWN of 1.0: half the rig stolen means half again as long.
            assertThat(ComputeRules.slowedSeconds(rig(50L).rig, 20L)).isEqualTo(30L);
            assertThat(ComputeRules.slowedSeconds(rig(6L).rig, 100L)).isEqualTo(106L);
        }

        @Test
        @DisplayName("it never makes anything faster")
        void neverFaster() {
            for (long stolen = 0; stolen <= 100; stolen += 7) {
                assertThat(ComputeRules.slowedSeconds(rig(stolen).rig, 45L))
                        .as("%d stolen", stolen)
                        .isGreaterThanOrEqualTo(45L);
            }
        }
    }

    @Nested
    @DisplayName("what the readout is allowed to say")
    class Visibility {

        @Test
        @DisplayName("an unaudited parasite is absent from the ledger, so the rig comes up short")
        void hiddenUntilFound() {
            SoloSave save = rig(6L);
            hold(save, save.rig.foreignMiners.getFirst());

            ComputeBudget budget = ComputeRules.snapshot(save);

            // The cycles are genuinely gone — availableCycles is computed from the real rig — and
            // there is no row anywhere that says where they went.
            assertThat(budget.available()).isEqualTo(Cycles.of(94));
            assertThat(budget.allocations()).isEmpty();
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(6));
            assertThat(budget.reconciles())
                    .as("docs/design/04 §3.1: the numbers genuinely stop adding up")
                    .isFalse();
        }

        @Test
        @DisplayName("once audited the same cycles are an ordinary named row and the ledger balances")
        void namedAfterTheAudit() {
            SoloSave save = rig(6L);
            MinerState miner = save.rig.foreignMiners.getFirst();
            hold(save, miner);

            miner.discovered = true;
            ComputeBudget budget = ComputeRules.snapshot(save);

            assertThat(budget.allocations()).hasSize(1);
            assertThat(budget.allocations().getFirst().consumer()).isEqualTo(ComputeConsumer.DEPLOYED_MINER);
            assertThat(budget.reconciles()).isTrue();
        }

        @Test
        @DisplayName("hiding a row can only ever under-reconcile, which the budget permits by design")
        void neverOverSubscribes() {
            // ComputeBudget rejects over-reconciliation and permits under-reconciliation, and this
            // is the caller that asymmetry was written for. Dropping rows is safe; synthesising them
            // would not be, which is why an unaudited parasite is omitted rather than anonymised.
            SoloSave save = rig(30L, 20L);
            for (MinerState miner : save.rig.foreignMiners) {
                hold(save, miner);
            }
            ComputeBudget budget = ComputeRules.snapshot(save);
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(50));
            assertThat(budget.allocations().stream().map(ComputeAllocation::cycles)).isEmpty();
        }

        @Test
        @DisplayName("a parasite with no allocation to hide is short on the rig, not in the ledger")
        void noAllocationToHide() {
            // Nothing was reserved, so nothing is omitted and the published ledger is honest — the
            // rig simply never lost the capacity in the first place. The slowdown still applies,
            // because the appetite is real; see Accounting.appetiteNotAllocation.
            SoloSave save = rig(6L);
            assertThat(ComputeRules.snapshot(save).reconciles()).isTrue();
            assertThat(ComputeRules.slowedSeconds(save.rig, 100L)).isEqualTo(106L);
        }
    }
}
