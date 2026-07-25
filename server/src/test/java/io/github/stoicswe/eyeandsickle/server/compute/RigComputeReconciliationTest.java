package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link RigComputeReconciliation} — the authoritative, <em>signed</em>
 * arithmetic that is the manual-audit signal in its rawest form ({@code docs/design/04-mining.md}
 * §3.1).
 *
 * <p>The point of this type is that {@code availableCycles} is a signed {@code long}, not a {@link
 * Cycles} — because {@code Cycles} refuses negatives and an over-subscription must stay observable
 * during a read rather than throwing. These tests defend exactly that boundary.
 */
class RigComputeReconciliationTest {

    private static final UUID RIG = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();

    private static RigComputeReconciliation reconciliation(long total, long active, long recovering) {
        return new RigComputeReconciliation(
                RIG, PLAYER, Cycles.of(total), Cycles.of(active), Cycles.of(recovering), total - active - recovering);
    }

    @Nested
    @DisplayName("over-subscription is a state, not an error")
    class OverSubscription {

        @Test
        @DisplayName("a negative available reads as over-subscribed")
        void negativeAvailableIsOverSubscribed() {
            // total 100, but 120 committed by a parasite (Invariant I6): available is -20.
            RigComputeReconciliation r = reconciliation(100, 120, 0);
            assertThat(r.availableCycles()).isEqualTo(-20L);
            assertThat(r.isOverSubscribed()).isTrue();
        }

        @Test
        @DisplayName("exactly-full and under-full rigs are not over-subscribed")
        void fullOrUnderIsNotOverSubscribed() {
            assertThat(reconciliation(100, 100, 0).isOverSubscribed()).isFalse();
            assertThat(reconciliation(100, 40, 20).isOverSubscribed()).isFalse();
        }
    }

    @Nested
    @DisplayName("availableForAllocation clamps at zero for callers that cannot represent a negative")
    class AvailableForAllocation {

        @Test
        @DisplayName("a healthy rig reports its true free capacity")
        void healthyRigReportsFree() {
            assertThat(reconciliation(100, 40, 20).availableForAllocation()).isEqualTo(Cycles.of(40));
        }

        @Test
        @DisplayName("an over-subscribed rig reports zero free, never a negative Cycles")
        void overSubscribedClampsToZero() {
            // The HUD's non-negative available is fed from here; the raw negative stays on availableCycles().
            RigComputeReconciliation r = reconciliation(100, 130, 0);
            assertThat(r.availableForAllocation()).isEqualTo(Cycles.ZERO);
            assertThat(r.availableCycles()).isEqualTo(-30L);
        }
    }

    @Test
    @DisplayName("the non-nullable fields are required")
    void nullsRejected() {
        assertThatThrownBy(() -> new RigComputeReconciliation(null, PLAYER, Cycles.of(1), Cycles.ZERO, Cycles.ZERO, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RigComputeReconciliation(RIG, null, Cycles.of(1), Cycles.ZERO, Cycles.ZERO, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RigComputeReconciliation(RIG, PLAYER, null, Cycles.ZERO, Cycles.ZERO, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RigComputeReconciliation(RIG, PLAYER, Cycles.of(1), null, Cycles.ZERO, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RigComputeReconciliation(RIG, PLAYER, Cycles.of(1), Cycles.ZERO, null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
