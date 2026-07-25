package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Hand-written SQL over {@code rigs}, {@code compute_allocations} and the {@code
 * rig_compute_reconciliation} view — the data access for the compute ledger.
 *
 * <h2>What this class does and does not decide</h2>
 *
 * It reads and writes rows; it does not decide whether an allocation is <em>allowed</em>. "May this
 * rig reserve these cycles" is a question about a sum that concurrently-inserted rows can change, so it
 * is answered in {@link ComputeLedgerService} while holding a row lock, not here. Keeping the
 * rule-free SQL in one place keeps the queries legible against the schema (the reason the project chose
 * {@code JdbcClient} over an ORM — see {@code server/pom.xml}).
 *
 * <p>Every read names its columns through the {@code *Rows} constants and never {@code SELECT *}, so a
 * migration cannot silently widen a hot query, and unit and enum columns cross the boundary through
 * {@link EconomyColumns} / {@link EnumColumns} so a mis-typed or unrecognised value fails at the read.
 */
@Repository
public class ComputeLedgerRepository {

    private final JdbcClient jdbcClient;

    ComputeLedgerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    // ------------------------------------------------------------------ rigs

    /**
     * Inserts a freshly provisioned rig.
     *
     * <p>The only writer of {@code total_cycles} in this slice. There is no update path for the ceiling
     * here at all, which is Invariant I1 made structural: capacity is set once, at provisioning, from
     * progression — never raised by anything touching a balance.
     *
     * @param rig the rig to persist; its {@code rowVersion} is written verbatim (0 for a new rig)
     */
    void insertRig(Rig rig) {
        Objects.requireNonNull(rig, "rig");
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO rigs (rig_id, player_id, total_cycles, thermal_budget_tier,
                                          bandwidth, memory_buffer, installed_modules, created_at, row_version)
                        VALUES (:rigId, :playerId, :totalCycles, :thermalTier,
                                :bandwidth, :memoryBuffer, :installedModules::jsonb, :createdAt, :rowVersion)
                        """)
                .param("rigId", rig.rigId())
                .param("playerId", rig.playerId())
                .param("totalCycles", EconomyColumns.cyclesValue(RigRows.TOTAL_CYCLES, rig.totalCycles()))
                .param("thermalTier", rig.thermalBudgetTier())
                .param("bandwidth", rig.bandwidth())
                .param("memoryBuffer", rig.memoryBuffer())
                .param("installedModules", rig.installedModules())
                .param("createdAt", Timestamps.at(rig.createdAt()))
                .param("rowVersion", rig.rowVersion())
                .update();
        Mutations.requireInserted(inserted, "rigs");
    }

    /**
     * Reads a rig without locking it.
     *
     * @param rigId the rig
     * @return the rig, or empty if none
     */
    Optional<Rig> findRig(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return jdbcClient
                .sql("SELECT " + RigRows.COLUMNS + " FROM rigs WHERE rig_id = :rigId")
                .param("rigId", rigId)
                .query(RigRows.MAPPER)
                .optional();
    }

    /**
     * Reads a rig and takes a row lock on it for the duration of the transaction.
     *
     * <p>This is the serialization point for every allocation decision on the rig ({@link Mutations}
     * documents why the lock is on the parent, not the allocations: the rows an over-allocation must be
     * checked against are the ones that do not exist yet, and only the rig row is there to lock). Call
     * this <em>before</em> reading {@link #reconciliation} inside the same transaction, so the sum the
     * decision is made against cannot move under it.
     *
     * @param rigId the rig to lock
     * @return the locked rig, or empty if none exists (nothing to lock)
     */
    Optional<Rig> lockRig(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return jdbcClient
                .sql("SELECT " + RigRows.COLUMNS + " FROM rigs WHERE rig_id = :rigId FOR UPDATE")
                .param("rigId", rigId)
                .query(RigRows.MAPPER)
                .optional();
    }

    // ------------------------------------------------------------------ reconciliation

    /**
     * The authoritative compute arithmetic for a rig, over <em>all</em> allocations charged to it.
     *
     * @param rigId the rig
     * @return the reconciliation, or empty if the rig does not exist
     */
    Optional<RigComputeReconciliation> reconciliation(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return jdbcClient
                .sql("SELECT " + RigComputeReconciliationRows.COLUMNS
                        + " FROM rig_compute_reconciliation WHERE rig_id = :rigId")
                .param("rigId", rigId)
                .query(RigComputeReconciliationRows.MAPPER)
                .optional();
    }

    // ------------------------------------------------------------------ allocations

    /**
     * Every allocation charged to a rig, active and recovering, as raw rows.
     *
     * <p>Deliberately unfiltered by disclosure: this returns the true ledger, and hiding rows from the
     * owner's view is a service-layer decision ({@link AllocationDisclosurePolicy}), never a query that
     * forgets the hidden rows exist. Ordered by consumer then id so a read is stable across refreshes.
     *
     * @param rigId the rig
     * @return the allocations charged to it, in a stable order
     */
    List<ComputeAllocation> allocationsChargedTo(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return jdbcClient
                .sql("SELECT " + ComputeAllocationRows.COLUMNS + " FROM compute_allocations"
                        + " WHERE charged_rig_id = :rigId ORDER BY consumer_type, allocation_id")
                .param("rigId", rigId)
                .query(ComputeAllocationRows.MAPPER)
                .list();
    }

    /**
     * Reads a single allocation by id.
     *
     * @param allocationId the allocation
     * @return the allocation, or empty if none
     */
    Optional<ComputeAllocation> findAllocation(UUID allocationId) {
        Objects.requireNonNull(allocationId, "allocationId");
        return jdbcClient
                .sql("SELECT " + ComputeAllocationRows.COLUMNS
                        + " FROM compute_allocations WHERE allocation_id = :allocationId")
                .param("allocationId", allocationId)
                .query(ComputeAllocationRows.MAPPER)
                .optional();
    }

    /**
     * Inserts a new active allocation and returns it.
     *
     * <p>Always inserts in the {@code active} state with a null {@code recovers_at}: an allocation is
     * born held, and only a later {@link #markRecovering} moves per-use cycles onto the Thermal Budget
     * curve. The database CHECKs enforce the pairing ({@code recovers_at} present iff recovering) and
     * the two-rig rule ({@code charged_rig_id} distinct from {@code counterparty_rig_id}); this method
     * passes the values through and lets those constraints be the last line of defence.
     *
     * @param allocationId the new allocation's id
     * @param chargedRigId the rig these cycles count against
     * @param counterpartyRigId the far end of a cross-rig relationship (I6), or null
     * @param consumer what the cycles are going to
     * @param consumerRef the specific miner/bot/tool, or null
     * @param cycles the reserved amount
     * @param createdAt when the allocation was made
     * @return the inserted allocation
     */
    ComputeAllocation insertActiveAllocation(
            UUID allocationId,
            UUID chargedRigId,
            UUID counterpartyRigId,
            ComputeConsumer consumer,
            UUID consumerRef,
            Cycles cycles,
            Instant createdAt) {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(chargedRigId, "chargedRigId");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(createdAt, "createdAt");
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO compute_allocations (allocation_id, charged_rig_id, counterparty_rig_id,
                                          consumer_type, consumer_ref, allocated_cycles, state, recovers_at,
                                          created_at, row_version)
                        VALUES (:allocationId, :chargedRigId, :counterpartyRigId,
                                :consumerType, :consumerRef, :cycles, 'active', NULL,
                                :createdAt, 0)
                        """)
                .param("allocationId", allocationId)
                .param("chargedRigId", chargedRigId)
                .param("counterpartyRigId", counterpartyRigId)
                .param("consumerType", EnumColumns.computeConsumer(consumer))
                .param("consumerRef", consumerRef)
                .param("cycles", EconomyColumns.cyclesValue(ComputeAllocationRows.ALLOCATED_CYCLES, cycles))
                .param("createdAt", Timestamps.at(createdAt))
                .update();
        Mutations.requireInserted(inserted, "compute_allocations");
        return new ComputeAllocation(
                allocationId,
                chargedRigId,
                counterpartyRigId,
                consumer,
                consumerRef,
                cycles,
                ComputeAllocation.State.ACTIVE,
                null);
    }

    /**
     * Moves an allocation into the recovering state with a return deadline, by id.
     *
     * <p>No row-version predicate: the service transitions an allocation only while holding the {@code
     * FOR UPDATE} lock on its rig ({@link #lockRig}), and every other mutation of an allocation takes
     * that same lock first, so the row cannot change between the caller's read and this update. A zero
     * affected-row count therefore means the row was removed by a prior committed transaction, which
     * {@link Mutations#requireUpdated} turns into a retryable failure rather than a silent no-op.
     *
     * @param allocationId the allocation to transition
     * @param recoversAt when the cycles return
     */
    void markRecovering(UUID allocationId, Instant recoversAt) {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(recoversAt, "recoversAt");
        int updated = jdbcClient
                .sql("""
                        UPDATE compute_allocations
                           SET state = 'recovering',
                               recovers_at = :recoversAt,
                               row_version = row_version + 1
                         WHERE allocation_id = :allocationId
                        """)
                .param("recoversAt", Timestamps.atOrNull(recoversAt))
                .param("allocationId", allocationId)
                .update();
        Mutations.requireUpdated(updated, "compute_allocations", allocationId);
    }

    /**
     * Deletes an allocation, freeing its cycles immediately, by id.
     *
     * <p>No row-version predicate, for the same reason as {@link #markRecovering}: the service deletes
     * only while holding the rig lock, so the row is stable. A zero count means it was already gone —
     * turned into a retryable failure, never treated as success.
     *
     * @param allocationId the allocation to delete
     */
    void deleteAllocation(UUID allocationId) {
        Objects.requireNonNull(allocationId, "allocationId");
        int deleted = jdbcClient
                .sql("DELETE FROM compute_allocations WHERE allocation_id = :allocationId")
                .param("allocationId", allocationId)
                .update();
        Mutations.requireUpdated(deleted, "compute_allocations", allocationId);
    }

    /**
     * Returns recovered cycles to the pool by deleting recovering allocations whose deadline has passed.
     *
     * <p>Deletion, not a state flip: once cycles are back they are simply free, and a recovered-but-still
     * -present row would be double-counted by the reconciliation view. The {@code recovers_at <= :now}
     * predicate is served by the partial index {@code ix_compute_allocations_recovering}.
     *
     * @param now the sweep instant (supplied, never read from a clock here)
     * @return how many allocations were reclaimed
     */
    int deleteRecoveredBefore(Instant now) {
        Objects.requireNonNull(now, "now");
        return jdbcClient
                .sql("DELETE FROM compute_allocations WHERE state = 'recovering' AND recovers_at <= :now")
                .param("now", Timestamps.at(now))
                .update();
    }
}
