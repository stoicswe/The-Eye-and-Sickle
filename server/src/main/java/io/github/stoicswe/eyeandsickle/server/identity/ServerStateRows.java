package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the {@code server_state} singleton.
 *
 * <p>The identity slice reads this row for server heat and the server's own DID; it does not write it.
 * {@code only_row} is not projected — it is a constant {@code true} that exists only to pin the table
 * to one row, and reading it back carries no information.
 */
final class ServerStateRows {

    static final String SERVER_DID = "server_did";
    static final String SERVER_HEAT = "server_heat";
    static final String HEAT_UPDATED_AT = "heat_updated_at";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    /** The explicit projection for the {@code server_state} read. */
    static final String COLUMNS = String.join(", ", SERVER_DID, SERVER_HEAT, HEAT_UPDATED_AT, ROW_VERSION);

    static final RowMapper<ServerState> MAPPER = RowMappers.of(
            ServerState.class,
            row -> new ServerState(
                    Did.ofNullable(row.textOrNull(SERVER_DID)),
                    new Heat(row.decimal(SERVER_HEAT)),
                    row.instant(HEAT_UPDATED_AT),
                    row.int64(ROW_VERSION)));

    private ServerStateRows() {}
}
