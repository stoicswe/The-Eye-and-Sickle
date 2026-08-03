package io.github.stoicswe.eyeandsickle.server.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.h2.api.Trigger;

/**
 * Refuses an UPDATE that moves {@code sequence_number} backwards.
 *
 * <h2>Why the database and not the application</h2>
 *
 * {@code docs/architecture/08-discovery-and-sync.md} §2: a peer's self-descriptor is ordered by a
 * <strong>signed monotonic sequence</strong>, never a wall clock, because clocks are attacker-
 * controlled. The doc then says the ordering is <em>"enforced at the database boundary (a trigger on
 * federation_peers refuses a rollback), so even a bug cannot regress a peer's record"</em>.
 *
 * <p>⚠ That sentence is the reason this exists. Without it, a replayed older descriptor is refused
 * only by whichever code path happens to check — and a rolled-back peer record is how a revoked
 * transport key comes back.
 *
 * <h2>⚠ Ported from PL/pgSQL</h2>
 *
 * Was {@code federation_peers_sequence_is_monotonic()}. H2 has no PL/pgSQL, so this is Java and is
 * therefore ours to review. Behaviour is unchanged.
 *
 * <p>⚠ The column index is resolved <strong>once</strong>, in {@link #init}, from live metadata
 * rather than hard-coded. A hard-coded ordinal silently guards the <em>wrong column</em> the first
 * time somebody adds a column above it — and the failure would be a rollback guard that quietly
 * compares two timestamps instead.
 */
public class MonotonicSequenceTrigger implements Trigger {

    private static final String COLUMN = "SEQUENCE_NUMBER";

    private String table = "a table";
    private int column = -1;

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type)
            throws SQLException {
        int copy = tableName.indexOf("_COPY_");
        this.table = copy < 0 ? tableName : tableName.substring(0, copy);
        try (ResultSet columns = conn.getMetaData().getColumns(null, schemaName, tableName, null)) {
            while (columns.next()) {
                if (COLUMN.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    // JDBC ordinals are 1-based; trigger row arrays are 0-based.
                    column = columns.getInt("ORDINAL_POSITION") - 1;
                    return;
                }
            }
        }
        // ⚠ Fail at INSTALL time, not at first use. A trigger that cannot find its column is a guard
        // that silently permits everything, and it would be discovered by the rollback it failed to
        // stop.
        throw new SQLException("No " + COLUMN + " column on " + tableName + "; cannot install rollback guard");
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow == null || newRow == null) {
            return;
        }
        long previous = ((Number) oldRow[column]).longValue();
        long offered = ((Number) newRow[column]).longValue();
        if (offered < previous) {
            throw new SQLException(
                    table + ".sequence_number must not go backwards (stored " + previous + ", offered " + offered + ")",
                    "23000");
        }
    }
}
