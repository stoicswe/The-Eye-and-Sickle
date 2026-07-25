package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link ComputeBudgetAssembler} — the one piece of logic that decides what the
 * §1.4 HUD says. It is the crux of the manual-audit design ({@code docs/design/04-mining.md} §3.1):
 * {@code available} is the <em>true</em> free capacity from the reconciliation over ALL rows, while the
 * disclosed list is only what the owner may see — so a hidden row shows up as an unaccounted-for gap
 * rather than being papered over.
 */
class ComputeBudgetAssemblerTest {

    private static final UUID RIG = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_RIG = UUID.randomUUID();

    @Nested
    @DisplayName("available comes from the reconciliation, never from the disclosed rows")
    class AvailableIsAuthoritative {

        @Test
        @DisplayName("with everything disclosed the budget reconciles exactly")
        void reconcilesWhenNothingHidden() {
            RigComputeReconciliation reconciliation = recon(100, 40, 35, 25);
            List<ComputeAllocation> disclosed = List.of(active(40, ComputeConsumer.SELF_MINING), recovering(35));

            ComputeBudget budget = ComputeBudgetAssembler.assemble(reconciliation, disclosed);

            assertThat(budget.rigId()).isEqualTo(RIG);
            assertThat(budget.total()).isEqualTo(Cycles.of(100));
            assertThat(budget.available()).isEqualTo(Cycles.of(25));
            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.recovering()).isEqualTo(Cycles.of(35));
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.ZERO);
        }

        @Test
        @DisplayName("a hidden allocation leaves the true available intact, surfacing the gap it steals")
        void hiddenRowBecomesTheGap() {
            // The reconciliation sees a 30-cycle hidden miner (available = 100 - 40 - 30 = 30), but the
            // disclosed list omits it. Deriving available from the disclosed rows would delete this signal.
            RigComputeReconciliation reconciliation = recon(100, 70, 0, 30);
            List<ComputeAllocation> disclosed = List.of(active(40, ComputeConsumer.SELF_MINING));

            ComputeBudget budget = ComputeBudgetAssembler.assemble(reconciliation, disclosed);

            assertThat(budget.available()).isEqualTo(Cycles.of(30));
            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(30));
            assertThat(budget.reconciles()).isFalse();
        }
    }

    @Nested
    @DisplayName("the over-subscription boundary")
    class OverSubscription {

        @Test
        @DisplayName("a negative signed available is fed to the HUD clamped at zero")
        void negativeAvailableClampsToZero() {
            // A parasite pushed the rig to -20 available; the disclosed rows stay within the ceiling
            // (the parasite is hidden), so the ComputeBudget is constructible and reports zero free.
            RigComputeReconciliation reconciliation =
                    new RigComputeReconciliation(RIG, PLAYER, Cycles.of(100), Cycles.of(120), Cycles.ZERO, -20L);
            List<ComputeAllocation> disclosed = List.of(active(40, ComputeConsumer.SELF_MINING));

            ComputeBudget budget = ComputeBudgetAssembler.assemble(reconciliation, disclosed);

            assertThat(budget.available()).isEqualTo(Cycles.ZERO);
            // The gap is loud even though available clamped: 100 - 40 - 0 - 0 = 60 unaccounted for.
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(60));
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("a disclosed row charged to a different rig is rejected by the budget it is placed in")
        void mischargedDisclosedRowRejected() {
            RigComputeReconciliation reconciliation = recon(100, 20, 0, 80);
            // Every disclosed row must be charged to the reconciliation's rig; a foreign row would tell a
            // player someone else's miner costs them cycles (Invariant I6).
            ComputeAllocation foreign = new ComputeAllocation(
                    UUID.randomUUID(),
                    OTHER_RIG,
                    null,
                    ComputeConsumer.SELF_MINING,
                    null,
                    Cycles.of(20),
                    ComputeAllocation.State.ACTIVE,
                    null);

            assertThatThrownBy(() -> ComputeBudgetAssembler.assemble(reconciliation, List.of(foreign)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null arguments are rejected")
        void nullsRejected() {
            RigComputeReconciliation reconciliation = recon(100, 0, 0, 100);
            assertThatThrownBy(() -> ComputeBudgetAssembler.assemble(null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ComputeBudgetAssembler.assemble(reconciliation, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static RigComputeReconciliation recon(long total, long active, long recovering, long available) {
        return new RigComputeReconciliation(
                RIG, PLAYER, Cycles.of(total), Cycles.of(active), Cycles.of(recovering), available);
    }

    private static ComputeAllocation active(long cycles, ComputeConsumer consumer) {
        return new ComputeAllocation(
                UUID.randomUUID(), RIG, null, consumer, null, Cycles.of(cycles), ComputeAllocation.State.ACTIVE, null);
    }

    private static ComputeAllocation recovering(long cycles) {
        return new ComputeAllocation(
                UUID.randomUUID(),
                RIG,
                null,
                ComputeConsumer.ACTIVE_TOOL,
                UUID.randomUUID(),
                Cycles.of(cycles),
                ComputeAllocation.State.RECOVERING,
                java.time.Instant.parse("2026-07-24T12:20:00Z"));
    }
}
