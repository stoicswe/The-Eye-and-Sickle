package io.github.stoicswe.eyeandsickle.server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/**
 * Refuses any UPDATE or DELETE — the append-only guard for evidence tables.
 *
 * <h2>What it protects, and why it lives in the database</h2>
 *
 * The ledger is an evidence surface ({@code docs/design/01-core-resources.md} §2.2 — investigators
 * follow ethecoin flows) and a provenance chain is a signature over history. Both are things an
 * attacker, or a well-meaning "data fix", would want to edit in place, and both lose their entire
 * meaning if that succeeds.
 *
 * <p>⚠ <strong>The service layer intends never to issue such a statement; this is what makes the
 * intention enforceable.</strong> It fires inside the engine on every write regardless of which code
 * path issued it, so a new repository method, a migration, or a console session cannot route around
 * it. That property is the whole point and it is why this is not a Java-side check.
 *
 * <h2>⚠ Ported from PL/pgSQL, and the reviewable surface grew</h2>
 *
 * This was {@code refuse_mutation()} in Postgres. H2 has no PL/pgSQL, so the guard is Java — which
 * means it is <em>our</em> code now rather than the database's, and it earns the same scrutiny the
 * original had. The behaviour is unchanged: refuse, always, loudly.
 *
 * <p>⚠ UPDATE and DELETE only. <strong>TRUNCATE is deliberately unaffected</strong>, so a test
 * harness can reset between tests without a privileged escape hatch that would then also exist in
 * production.
 */
public class AppendOnlyTrigger implements Trigger {

    private String table = "a table";

    @Override
    public void init(
            Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
        // ⚠ H2 hands this a working alias during some operations — "ledger_transactions_COPY_3_6".
        // An operator reading a refusal should see the table they know, not an engine internal.
        int copy = tableName.indexOf("_COPY_");
        this.table = copy < 0 ? tableName : tableName.substring(0, copy);
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        // ⚠ SQLState 23000 (integrity constraint violation), matching the restrict_violation the
        // PL/pgSQL version raised. Callers that branch on constraint failures keep working.
        throw new SQLException(table + " is append-only; correct it with a new row, never by editing history", "23000");
    }
}
