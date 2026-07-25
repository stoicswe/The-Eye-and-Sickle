package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Appends to, and reads from, the public ledger {@code ledger_transactions}.
 *
 * <h2>Append-only, by construction and by trigger</h2>
 *
 * There is no update and no delete here, and there could not be: the table refuses both at the
 * database level ({@code refuse_mutation}). A reversal is a new row. That is what makes the ledger
 * evidence rather than rumour — an evidence surface you can quietly edit is neither.
 *
 * <h2>The Dead Drop visibility rule lives in the query, not in the data</h2>
 *
 * {@link #query(LedgerQuery, String)} takes a <em>viewer</em> — the authenticated DID asking, or
 * {@code null} for an anonymous public investigator — and always applies the same rule: a
 * {@code traceable} row is visible to anyone, an untraceable one (a Dead Drop) only to its own
 * counterparties. This is the server half of {@code docs/design/01-core-resources.md} §2.2: the row is
 * always written, and it is the investigator-facing query that obscures it. The viewer is an argument,
 * never a filter field, so it can only ever be the caller's true identity (Invariant I14).
 */
@Repository
public class LedgerRepository {

    private final JdbcClient jdbcClient;

    LedgerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * Appends one transaction.
     *
     * <p>Called from inside {@link LedgerService}'s transaction, together with the balance changes it
     * describes — the ledger row and the money move are one atomic act, or the ledger stops being
     * evidence.
     *
     * @param transaction the row to write; {@code created_at} is written explicitly so the returned
     *     record and the stored row carry the same instant
     */
    public void append(LedgerTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        int affected = jdbcClient
                .sql("""
                        INSERT INTO ledger_transactions
                            (tx_id, from_did, to_did, amount_ec_minor, tx_type, traceable, memo, created_at)
                        VALUES (:txId, :fromDid, :toDid, :amount, :txType, :traceable, :memo::jsonb, :createdAt)
                        """)
                .param("txId", transaction.txId())
                .param("fromDid", transaction.fromDid())
                .param("toDid", transaction.toDid())
                .param("amount", EconomyColumns.ethecoinValue(LedgerRows.AMOUNT, transaction.amount()))
                .param("txType", transaction.type().wireValue())
                .param("traceable", transaction.traceable())
                .param("memo", Jsonb.writeObject(transaction.memo()))
                .param("createdAt", OffsetDateTime.ofInstant(transaction.createdAt(), ZoneOffset.UTC))
                .update();
        Mutations.requireInserted(affected, "ledger_transactions");
    }

    /**
     * Runs an investigator query from a given viewpoint.
     *
     * @param query the client-chosen filters
     * @param viewerDid the authenticated DID asking, or {@code null} for an anonymous public
     *     investigator (who sees only traceable rows)
     * @return the matching rows, newest first, at most {@link LedgerQuery#MAX_LIMIT}
     */
    public List<LedgerTransaction> query(LedgerQuery query, String viewerDid) {
        Objects.requireNonNull(query, "query");

        StringBuilder sql =
                new StringBuilder("SELECT ").append(LedgerRows.COLUMNS).append(" FROM ledger_transactions WHERE ");
        Map<String, Object> params = new LinkedHashMap<>();

        // Dead Drop visibility: traceable rows are public; an untraceable row is visible only to its own
        // counterparties. Applied first and always — it is the rule, not a filter.
        if (viewerDid == null) {
            sql.append("traceable");
        } else {
            sql.append("(traceable OR from_did = :viewer OR to_did = :viewer)");
            params.put("viewer", viewerDid);
        }

        query.participantDid().ifPresent(participant -> {
            params.put("participant", participant);
            sql.append(
                    switch (query.direction()) {
                        case SENT -> " AND from_did = :participant";
                        case RECEIVED -> " AND to_did = :participant";
                        case EITHER -> " AND (from_did = :participant OR to_did = :participant)";
                    });
        });

        query.counterpartyDid().ifPresent(counterparty -> {
            params.put("counterparty", counterparty);
            sql.append(" AND (from_did = :counterparty OR to_did = :counterparty)");
        });

        query.typeFilter().ifPresent(type -> {
            params.put("type", type.wireValue());
            sql.append(" AND tx_type = :type");
        });

        // Clamp the page: a caller cannot ask the server to scan the whole ledger into memory.
        int limit = Math.min(query.limit(), LedgerQuery.MAX_LIMIT);
        params.put("limit", limit);
        sql.append(" ORDER BY created_at DESC LIMIT :limit");

        return jdbcClient
                .sql(sql.toString())
                .params(params)
                .query(LedgerRows.MAPPER)
                .list();
    }
}
