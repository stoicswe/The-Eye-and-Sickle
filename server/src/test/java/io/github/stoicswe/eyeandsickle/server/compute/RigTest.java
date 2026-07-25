package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the {@link Rig} record's constructor guards. These mirror the {@code rigs}
 * CHECK constraints (V2) so an in-memory {@code Rig} cannot describe a machine the database would
 * refuse — a zero-cycle rig has no capacity to allocate, and thermal tier 0 would divide the recovery
 * curve by a tier that does not exist.
 */
class RigTest {

    private static final UUID RIG_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-07-24T12:00:00Z");

    private static Rig rig(Cycles total, int tier, int bandwidth, int memoryBuffer, long rowVersion) {
        return new Rig(RIG_ID, PLAYER_ID, total, tier, bandwidth, memoryBuffer, "{}", CREATED, rowVersion);
    }

    @Test
    @DisplayName("a well-formed rig is accepted")
    void wellFormedRigAccepted() {
        assertThatCode(() -> rig(Cycles.of(100), 1, 4, 0, 0L)).doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("the CHECK-mirroring guards bite")
    class Guards {

        @Test
        @DisplayName("a zero-cycle rig has no capacity to allocate and is refused")
        void zeroCeilingRejected() {
            assertThatThrownBy(() -> rig(Cycles.ZERO, 1, 4, 0, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("total_cycles");
        }

        @Test
        @DisplayName("thermal tier 0 would divide the recovery curve by a tier that does not exist")
        void thermalTierBelowOneRejected() {
            assertThatThrownBy(() -> rig(Cycles.of(100), 0, 4, 0, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("thermalBudgetTier");
        }

        @Test
        @DisplayName("bandwidth must be positive")
        void nonPositiveBandwidthRejected() {
            assertThatThrownBy(() -> rig(Cycles.of(100), 1, 0, 0, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bandwidth");
        }

        @Test
        @DisplayName("memory buffer is never negative")
        void negativeMemoryBufferRejected() {
            assertThatThrownBy(() -> rig(Cycles.of(100), 1, 4, -1, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("memoryBuffer");
        }

        @Test
        @DisplayName("row version is never negative")
        void negativeRowVersionRejected() {
            assertThatThrownBy(() -> rig(Cycles.of(100), 1, 4, 0, -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rowVersion");
        }

        @Test
        @DisplayName("the non-nullable fields are required")
        void nullsRejected() {
            assertThatThrownBy(() -> new Rig(null, PLAYER_ID, Cycles.of(100), 1, 4, 0, "{}", CREATED, 0L))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Rig(RIG_ID, null, Cycles.of(100), 1, 4, 0, "{}", CREATED, 0L))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Rig(RIG_ID, PLAYER_ID, null, 1, 4, 0, "{}", CREATED, 0L))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Rig(RIG_ID, PLAYER_ID, Cycles.of(100), 1, 4, 0, null, CREATED, 0L))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Rig(RIG_ID, PLAYER_ID, Cycles.of(100), 1, 4, 0, "{}", null, 0L))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
