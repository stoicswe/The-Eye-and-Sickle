package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the {@link RowMapper} for {@code rigs} — the house pattern (see {@link RowMappers}).
 *
 * <p>One class per table means a column rename is a single edit and every query that reads the table
 * shares one spelling. The total-cycles column is read through {@link EconomyColumns#cycles} rather
 * than a bare {@code int64}, so Invariant I1 is enforced at the read: this column cannot be
 * accidentally read as an ethecoin amount, because {@code EconomyColumns} refuses a name that does not
 * end in {@code _cycles}.
 */
final class RigRows {

    static final String RIG_ID = "rig_id";
    static final String PLAYER_ID = "player_id";
    static final String TOTAL_CYCLES = "total_cycles";
    static final String THERMAL_BUDGET_TIER = "thermal_budget_tier";
    static final String BANDWIDTH = "bandwidth";
    static final String MEMORY_BUFFER = "memory_buffer";
    static final String INSTALLED_MODULES = "installed_modules";
    static final String CREATED_AT = "created_at";
    static final String ROW_VERSION = "row_version";

    /** The column list every {@code rigs} read should select, in mapper order. Never {@code SELECT *}. */
    static final String COLUMNS = String.join(
            ", ",
            RIG_ID,
            PLAYER_ID,
            TOTAL_CYCLES,
            THERMAL_BUDGET_TIER,
            BANDWIDTH,
            MEMORY_BUFFER,
            INSTALLED_MODULES,
            CREATED_AT,
            ROW_VERSION);

    static final RowMapper<Rig> MAPPER = RowMappers.of(
            Rig.class,
            row -> new Rig(
                    row.uuid(RIG_ID),
                    row.uuid(PLAYER_ID),
                    EconomyColumns.cycles(row, TOTAL_CYCLES),
                    row.int32(THERMAL_BUDGET_TIER),
                    row.int32(BANDWIDTH),
                    row.int32(MEMORY_BUFFER),
                    row.json(INSTALLED_MODULES),
                    row.instant(CREATED_AT),
                    row.int64(ROW_VERSION)));

    private RigRows() {}
}
