package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the {@link RowMapper} for the {@code rig_compute_reconciliation} view.
 *
 * <h2>Why {@code available_cycles} is read <em>signed</em></h2>
 *
 * The view is the authoritative arithmetic over <b>all</b> allocations, and its {@code available_cycles}
 * ({@code total - SUM(all)}) goes negative on an over-subscribed rig — which is the manual-audit signal,
 * not an error ({@code docs/design/04-mining.md} §3.1). {@link EconomyColumns#signedCycles} exists for
 * exactly this column: {@code cycles(...)} would refuse the negative (a rig cannot have less than no
 * capacity), and refusing it during a read would throw away the very thing an audit is looking for.
 * The three non-negative totals go through {@link EconomyColumns#cycles} as usual.
 */
final class RigComputeReconciliationRows {

    static final String RIG_ID = "rig_id";
    static final String PLAYER_ID = "player_id";
    static final String TOTAL_CYCLES = "total_cycles";
    static final String ACTIVE_CYCLES = "active_cycles";
    static final String RECOVERING_CYCLES = "recovering_cycles";
    static final String AVAILABLE_CYCLES = "available_cycles";

    /** The column list a reconciliation read should select, in mapper order. Never {@code SELECT *}. */
    static final String COLUMNS =
            String.join(", ", RIG_ID, PLAYER_ID, TOTAL_CYCLES, ACTIVE_CYCLES, RECOVERING_CYCLES, AVAILABLE_CYCLES);

    static final RowMapper<RigComputeReconciliation> MAPPER = RowMappers.of(
            RigComputeReconciliation.class,
            row -> new RigComputeReconciliation(
                    row.uuid(RIG_ID),
                    row.uuid(PLAYER_ID),
                    EconomyColumns.cycles(row, TOTAL_CYCLES),
                    EconomyColumns.cycles(row, ACTIVE_CYCLES),
                    EconomyColumns.cycles(row, RECOVERING_CYCLES),
                    // Signed: negative means over-subscribed, which is a state to surface, not to reject.
                    EconomyColumns.signedCycles(row, AVAILABLE_CYCLES)));

    private RigComputeReconciliationRows() {}
}
