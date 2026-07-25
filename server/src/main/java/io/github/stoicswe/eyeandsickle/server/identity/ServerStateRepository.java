package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the {@code server_state} singleton.
 *
 * <p>Read-only from this slice. The identity layer needs server heat (to report alongside a player's
 * profile) and the server's own DID (to attribute operator actions), but it does not <em>drive</em>
 * server heat — that reading is maintained by the mining/heat systems as population-wide Sickle activity
 * accrues ({@code docs/design/01-core-resources.md} §4.2). Keeping this repository read-only is how the
 * slice boundary stays honest: writing server heat here would be this slice quietly taking on another's
 * authority.
 *
 * <p>The row always exists — V2 seeds it so no caller has to handle an absent singleton — so
 * {@link #read()} returns a value rather than an {@link java.util.Optional}. A missing row would be a
 * corrupted install, and {@code .single()} failing loudly is the right response to that, not a silent
 * default.
 */
@Repository
public class ServerStateRepository {

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient Spring's JdbcClient over the server's Postgres
     */
    public ServerStateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * @return the current server state, including server heat and this server's DID
     */
    public ServerState read() {
        return jdbcClient
                .sql("SELECT " + ServerStateRows.COLUMNS + " FROM server_state WHERE only_row = true")
                .query(ServerStateRows.MAPPER)
                .single();
    }
}
