package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the compute slice's typed failures. These exist so the REST layer can map each to the
 * right status code, and they carry the facts a caller needs without leaking anything a caller should
 * not learn.
 */
class ComputeExceptionsTest {

    @Nested
    @DisplayName("InsufficientComputeException — refused, never clamped (Invariant I14)")
    class Insufficient {

        @Test
        @DisplayName("carries the rig, the requested amount and the true free amount, and quotes I14")
        void carriesTheFacts() {
            UUID rig = UUID.randomUUID();
            InsufficientComputeException e = new InsufficientComputeException(rig, Cycles.of(60), Cycles.of(40));

            assertThat(e.rigId()).isEqualTo(rig);
            assertThat(e.requested()).isEqualTo(Cycles.of(60));
            assertThat(e.available()).isEqualTo(Cycles.of(40));
            // The message names the invariant so a log reader sees why 40 was not simply handed back.
            assertThat(e.getMessage()).contains("Invariant I14").contains("refused");
        }

        @Test
        @DisplayName("its fields are required")
        void nullsRejected() {
            assertThatThrownBy(() -> new InsufficientComputeException(null, Cycles.of(1), Cycles.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new InsufficientComputeException(UUID.randomUUID(), null, Cycles.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new InsufficientComputeException(UUID.randomUUID(), Cycles.of(1), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("RigNotFoundException")
    class RigNotFound {

        @Test
        @DisplayName("carries the missing id")
        void carriesId() {
            UUID rig = UUID.randomUUID();
            assertThat(new RigNotFoundException(rig).rigId()).isEqualTo(rig);
        }

        @Test
        @DisplayName("the id is required")
        void nullRejected() {
            assertThatThrownBy(() -> new RigNotFoundException(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("AllocationNotFoundException — absence and mis-ownership are the same answer")
    class AllocationNotFound {

        @Test
        @DisplayName("carries both ids and does not distinguish 'not yours' from 'not here'")
        void doesNotLeakOwnership() {
            UUID rig = UUID.randomUUID();
            UUID allocation = UUID.randomUUID();
            AllocationNotFoundException e = new AllocationNotFoundException(rig, allocation);

            assertThat(e.rigId()).isEqualTo(rig);
            assertThat(e.allocationId()).isEqualTo(allocation);
            // Telling a caller "it exists but is not yours" would leak another rig's allocation; the one
            // message covers both cases so this endpoint cannot be used for reconnaissance.
            assertThat(e.getMessage()).contains(allocation.toString()).contains(rig.toString());
        }

        @Test
        @DisplayName("its ids are required")
        void nullsRejected() {
            assertThatThrownBy(() -> new AllocationNotFoundException(null, UUID.randomUUID()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AllocationNotFoundException(UUID.randomUUID(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
