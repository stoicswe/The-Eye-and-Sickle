package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the {@link RowMapper} for {@code compute_allocations} — the compute ledger, mapped
 * straight to the wire type {@link ComputeAllocation}.
 *
 * <h2>The enum and unit columns go through the boundary helpers on purpose</h2>
 *
 * {@code consumer_type} and {@code state} are read via {@link EnumColumns}, the one place a protocol
 * enum is spelled as a database value — a stored value this build does not recognise is rejected, never
 * mapped to a fallback. {@code allocated_cycles} is read via {@link EconomyColumns#cycles}, which
 * refuses to hand back an ethecoin amount (Invariant I1). Between them, a row that does not mean what
 * the mapper thinks fails loudly at the read rather than several frames later as a wrong number.
 *
 * <p>The mapper produces exactly the protocol {@link ComputeAllocation}, including its two-rig shape
 * (Invariant I6): {@code counterparty_rig_id} is the informational far end, never a second charge.
 */
final class ComputeAllocationRows {

    static final String ALLOCATION_ID = "allocation_id";
    static final String CHARGED_RIG_ID = "charged_rig_id";
    static final String COUNTERPARTY_RIG_ID = "counterparty_rig_id";
    static final String CONSUMER_TYPE = "consumer_type";
    static final String CONSUMER_REF = "consumer_ref";
    static final String ALLOCATED_CYCLES = "allocated_cycles";
    static final String STATE = "state";
    static final String RECOVERS_AT = "recovers_at";
    static final String CREATED_AT = "created_at";
    static final String ROW_VERSION = "row_version";

    /** The column list every ledger read should select, in mapper order. Never {@code SELECT *}. */
    static final String COLUMNS = String.join(
            ", ",
            ALLOCATION_ID,
            CHARGED_RIG_ID,
            COUNTERPARTY_RIG_ID,
            CONSUMER_TYPE,
            CONSUMER_REF,
            ALLOCATED_CYCLES,
            STATE,
            RECOVERS_AT,
            CREATED_AT,
            ROW_VERSION);

    static final RowMapper<ComputeAllocation> MAPPER = RowMappers.of(
            ComputeAllocation.class,
            row -> new ComputeAllocation(
                    row.uuid(ALLOCATION_ID),
                    row.uuid(CHARGED_RIG_ID),
                    // Nullable: null for a purely local allocation, a rig id for a cross-rig one (I6).
                    row.uuidOrNull(COUNTERPARTY_RIG_ID),
                    EnumColumns.computeConsumer(row.text(CONSUMER_TYPE)),
                    // Nullable: null where the consumer is not a distinct entity (self-mining is the rig).
                    row.uuidOrNull(CONSUMER_REF),
                    EconomyColumns.cycles(row, ALLOCATED_CYCLES),
                    EnumColumns.allocationState(row.text(STATE)),
                    // Present iff recovering; ComputeAllocation's own constructor cross-checks that.
                    row.instantOrNull(RECOVERS_AT)));

    private ComputeAllocationRows() {}
}
