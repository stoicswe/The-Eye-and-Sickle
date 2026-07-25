package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Integration tests for {@link ComputeLedgerRepository} against a real, Flyway-migrated PostgreSQL.
 *
 * <p>These cover what genuinely needs a database and cannot be seen through a fake: that the
 * repository's own SQL and row mappers round-trip a rig and its allocations; that the {@code
 * rig_compute_reconciliation} view really computes a <em>signed</em>, over-subscribable {@code
 * available_cycles} (the audit signal); that the recovering-sweep DELETE honours its timestamp
 * predicate; and that a mutation which matched no row surfaces as a retryable conflict rather than a
 * silent no-op. Pure arithmetic and rule logic are tested against the fake in {@code
 * ComputeLedgerServiceTest} — not re-tested here through the database.
 */
class ComputeLedgerRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant CREATED = Instant.parse("2026-07-24T12:00:00Z");

    private ComputeLedgerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ComputeLedgerRepository(jdbcClient());
    }

    // ------------------------------------------------------------------ rigs

    @Nested
    @DisplayName("rigs round-trip through the repository's own SQL and mapper")
    class Rigs {

        @Test
        @DisplayName("insertRig then findRig returns every field, with cycles typed as Cycles")
        void insertAndReadBack() {
            UUID player = insertPlayer();
            Rig rig = new Rig(UUID.randomUUID(), player, Cycles.of(140), 2, 5, 7, "{}", CREATED, 0L);

            repository.insertRig(rig);

            Rig readBack = repository.findRig(rig.rigId()).orElseThrow();
            assertThat(readBack.rigId()).isEqualTo(rig.rigId());
            assertThat(readBack.playerId()).isEqualTo(player);
            assertThat(readBack.totalCycles()).isEqualTo(Cycles.of(140));
            assertThat(readBack.thermalBudgetTier()).isEqualTo(2);
            assertThat(readBack.bandwidth()).isEqualTo(5);
            assertThat(readBack.memoryBuffer()).isEqualTo(7);
            assertThat(readBack.installedModules()).isEqualTo("{}");
            assertThat(readBack.createdAt()).isEqualTo(CREATED);
            assertThat(readBack.rowVersion()).isZero();
        }

        @Test
        @DisplayName("findRig on an unknown id is empty, not an error")
        void unknownRigIsEmpty() {
            assertThat(repository.findRig(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("a rig charged to a non-existent player is refused by the foreign key")
        void rigPlayerForeignKeyBites() {
            Rig orphan = new Rig(UUID.randomUUID(), UUID.randomUUID(), Cycles.of(100), 1, 4, 6, "{}", CREATED, 0L);
            assertThatThrownBy(() -> repository.insertRig(orphan)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("lockRig reads the rig inside a transaction (FOR UPDATE)")
        void lockRigReadsWithinTransaction() {
            UUID rig = seedRig(insertPlayer(), 100);

            Rig locked =
                    transactions().execute(status -> repository.lockRig(rig).orElseThrow());
            assertThat(locked.rigId()).isEqualTo(rig);

            // Explicit type avoids assertThat(...) overload ambiguity on a raw execute() result.
            Optional<Rig> missing = transactions().execute(status -> repository.lockRig(UUID.randomUUID()));
            assertThat(missing).isEmpty();
        }
    }

    // ------------------------------------------------------------------ the reconciliation view

    @Nested
    @DisplayName("the rig_compute_reconciliation view is the authoritative, signed arithmetic")
    class ReconciliationView {

        @Test
        @DisplayName("active, recovering and a SIGNED negative available are computed over ALL rows")
        void computesSignedAvailableOverAllRows() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID otherRig = seedRig(insertPlayer(), 100);

            // 40 self-mining (active) + a 35 tool sent to recovery + a 45 parasite that over-subscribes.
            insertActive(rig, null, ComputeConsumer.SELF_MINING, Cycles.of(40));
            UUID tool = insertActive(rig, null, ComputeConsumer.ACTIVE_TOOL, Cycles.of(35));
            repository.markRecovering(tool, CREATED.plusSeconds(1200));
            insertActive(rig, otherRig, ComputeConsumer.DEPLOYED_MINER, Cycles.of(45));

            RigComputeReconciliation r = repository.reconciliation(rig).orElseThrow();

            assertThat(r.totalCycles()).isEqualTo(Cycles.of(100));
            assertThat(r.activeCycles()).isEqualTo(Cycles.of(85)); // 40 + 45
            assertThat(r.recoveringCycles()).isEqualTo(Cycles.of(35));
            // 100 - 85 - 35 = -20: an over-subscription, surfaced not clamped (docs/design/04 §3.1).
            assertThat(r.availableCycles()).isEqualTo(-20L);
            assertThat(r.isOverSubscribed()).isTrue();
        }

        @Test
        @DisplayName("an empty rig reconciles to its full ceiling available")
        void emptyRigIsFullyAvailable() {
            UUID rig = seedRig(insertPlayer(), 100);
            RigComputeReconciliation r = repository.reconciliation(rig).orElseThrow();

            assertThat(r.activeCycles()).isEqualTo(Cycles.ZERO);
            assertThat(r.recoveringCycles()).isEqualTo(Cycles.ZERO);
            assertThat(r.availableCycles()).isEqualTo(100L);
        }

        @Test
        @DisplayName("reconciliation of an unknown rig is empty")
        void unknownRigIsEmpty() {
            assertThat(repository.reconciliation(UUID.randomUUID())).isEmpty();
        }
    }

    // ------------------------------------------------------------------ allocations

    @Nested
    @DisplayName("allocations: reads, cross-rig mapping, and the sweep")
    class Allocations {

        @Test
        @DisplayName("allocationsChargedTo returns only rows charged to the rig, and a cross-rig row maps cleanly")
        void chargedRowsOnlyAndCrossRigMapping() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID otherRig = seedRig(insertPlayer(), 100);

            UUID mine = insertActive(rig, otherRig, ComputeConsumer.DEPLOYED_MINER, Cycles.of(20));
            // A control channel charged to the OTHER rig whose counterparty is this rig: it must NOT
            // appear on this rig's ledger — counterparty is informational, never a charge (Invariant I6).
            insertActive(otherRig, rig, ComputeConsumer.CONTROL_CHANNEL, Cycles.of(3));

            List<ComputeAllocation> charged = repository.allocationsChargedTo(rig);
            assertThat(charged).hasSize(1);
            ComputeAllocation row = charged.get(0);
            assertThat(row.allocationId()).isEqualTo(mine);
            assertThat(row.chargedRigId()).isEqualTo(rig);
            assertThat(row.counterpartyRigId()).isEqualTo(otherRig);
            assertThat(row.consumer()).isEqualTo(ComputeConsumer.DEPLOYED_MINER);
            assertThat(row.state()).isEqualTo(ComputeAllocation.State.ACTIVE);
            assertThat(row.recoversAt()).isNull();
        }

        @Test
        @DisplayName("markRecovering sets state and a return deadline that findAllocation reads back")
        void markRecoveringRoundTrips() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID tool = insertActive(rig, null, ComputeConsumer.ACTIVE_TOOL, Cycles.of(35));
            Instant recoversAt = CREATED.plusSeconds(700);

            repository.markRecovering(tool, recoversAt);

            ComputeAllocation recovered = repository.findAllocation(tool).orElseThrow();
            assertThat(recovered.state()).isEqualTo(ComputeAllocation.State.RECOVERING);
            assertThat(recovered.recoversAt()).isEqualTo(recoversAt);
        }

        @Test
        @DisplayName("deleteRecoveredBefore reclaims only due rows and honours the recovers_at predicate")
        void sweepReclaimsOnlyDueRows() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID due = insertActive(rig, null, ComputeConsumer.ACTIVE_TOOL, Cycles.of(10));
            UUID notYet = insertActive(rig, null, ComputeConsumer.RELAY_HOP, Cycles.of(10));
            repository.markRecovering(due, CREATED.minusSeconds(1));
            repository.markRecovering(notYet, CREATED.plusSeconds(60));

            int reclaimed = repository.deleteRecoveredBefore(CREATED);

            assertThat(reclaimed).isEqualTo(1);
            assertThat(repository.findAllocation(due)).isEmpty();
            assertThat(repository.findAllocation(notYet)).isPresent();
        }

        @Test
        @DisplayName("an active row is never swept — only recovering rows past their deadline are")
        void activeRowsAreNeverSwept() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID active = insertActive(rig, null, ComputeConsumer.SELF_MINING, Cycles.of(10));

            assertThat(repository.deleteRecoveredBefore(CREATED.plusSeconds(10_000)))
                    .isZero();
            assertThat(repository.findAllocation(active)).isPresent();
        }

        @Test
        @DisplayName("deleteAllocation removes the row; deleting an absent row is a retryable conflict")
        void deleteAndMissingDelete() {
            UUID rig = seedRig(insertPlayer(), 100);
            UUID allocation = insertActive(rig, null, ComputeConsumer.BOT_FRAME, Cycles.of(15));

            repository.deleteAllocation(allocation);
            assertThat(repository.findAllocation(allocation)).isEmpty();

            // A zero-row delete means the row was already gone; Mutations turns that into a conflict, so a
            // caller never mistakes a no-op for success.
            assertThatThrownBy(() -> repository.deleteAllocation(allocation))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("markRecovering on an absent allocation is a retryable conflict, not a silent no-op")
        void markRecoveringMissingIsConflict() {
            assertThatThrownBy(() -> repository.markRecovering(UUID.randomUUID(), CREATED))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("the two-rig CHECK is the last line of defence for insertActiveAllocation (Invariant I6)")
        void insertRefusesSameRigTwice() {
            UUID rig = seedRig(insertPlayer(), 100);
            // charged == counterparty is the shape a double-charge takes; the database refuses it even if
            // a service bug reached here.
            assertThatThrownBy(() -> insertActive(rig, rig, ComputeConsumer.DEPLOYED_MINER, Cycles.of(20)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_compute_allocations_two_rigs");
        }
    }

    // ------------------------------------------------------------------ helpers

    private UUID insertPlayer() {
        UUID playerId = UUID.randomUUID();
        jdbcClient()
                .sql("INSERT INTO players (player_id) VALUES (:id)")
                .param("id", playerId)
                .update();
        return playerId;
    }

    private UUID seedRig(UUID playerId, long totalCycles) {
        Rig rig = new Rig(UUID.randomUUID(), playerId, Cycles.of(totalCycles), 1, 4, 6, "{}", CREATED, 0L);
        repository.insertRig(rig);
        return rig.rigId();
    }

    private UUID insertActive(UUID chargedRig, UUID counterpartyRig, ComputeConsumer consumer, Cycles cycles) {
        ComputeAllocation allocation = repository.insertActiveAllocation(
                UUID.randomUUID(), chargedRig, counterpartyRig, consumer, UUID.randomUUID(), cycles, CREATED);
        return allocation.allocationId();
    }
}
