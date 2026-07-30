package io.github.stoicswe.eyeandsickle.server.persistence;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.jdbc.core.RowMapper;

/**
 * Turns a {@code Row -> record} function into a Spring {@link RowMapper}.
 *
 * <h2>The house pattern</h2>
 *
 * Each table gets one class holding its column names as constants and its mapper as a static field.
 * A column rename is then exactly one edit, and every query that reads the table shares the same
 * spelling:
 *
 * {@snippet lang = java:
 * final class LedgerRows {
 *
 *     static final String TX_ID = "tx_id";
 *     static final String FROM_DID = "from_did";
 *     static final String TO_DID = "to_did";
 *     static final String AMOUNT = "amount_wei";
 *     static final String TX_TYPE = "tx_type";
 *     static final String TRACEABLE = "traceable";
 *     static final String CREATED_AT = "created_at";
 *
 *     static final RowMapper<LedgerTransaction> MAPPER = RowMappers.of(
 *             LedgerTransaction.class,
 *             row -> new LedgerTransaction(
 *                     row.uuid(TX_ID),
 *                     row.textOrNull(FROM_DID),          // null for a mining reward: no payer
 *                     row.text(TO_DID),
 *                     EconomyColumns.ethecoin(row, AMOUNT),
 *                     row.text(TX_TYPE),
 *                     row.bool(TRACEABLE),
 *                     row.instant(CREATED_AT)));
 *
 *     private LedgerRows() {}
 * }
 *}
 *
 * and the query reads:
 *
 * {@snippet lang = java:
 * List<LedgerTransaction> byEitherCounterparty(String did) {
 *     return jdbcClient
 *             .sql("""
 *                  SELECT tx_id, from_did, to_did, amount_wei, tx_type, traceable, created_at
 *                    FROM ledger_transactions
 *                   WHERE from_did = :did OR to_did = :did
 *                   ORDER BY created_at DESC
 *                   LIMIT :limit
 *                  """)
 *             .param("did", did)
 *             .param("limit", pageSize)
 *             .query(LedgerRows.MAPPER)
 *             .list();
 * }
 *}
 *
 * <h2>Why not reflection</h2>
 *
 * See {@link Row}. In short: a reflective mapper that misses a column can hand back a plausible
 * default, and on this schema a plausible default is a zero balance or a zero cycle allocation.
 *
 * <h2>Never {@code SELECT *}</h2>
 *
 * Listing columns is what makes the mapper and the query reviewable against each other in one screen,
 * and it is what stops a later migration silently widening a hot query. It also keeps a column that a
 * caller has no business seeing — a rootkit-wrapped miner's row, say — out of the result set by
 * construction rather than by remembering to filter.
 */
public final class RowMappers {

    private RowMappers() {}

    /**
     * A mapper described by the type it produces.
     *
     * @param <T> the record type
     * @param type the record being mapped; used only to name the mapper in failure messages
     * @param reader reads one row into the record
     * @return a {@link RowMapper} for use with {@code JdbcClient}
     */
    public static <T> RowMapper<T> of(Class<T> type, Function<Row, T> reader) {
        Objects.requireNonNull(type, "type");
        return of(type.getSimpleName(), reader);
    }

    /**
     * A mapper described by an arbitrary label, for projections that have no record of their own.
     *
     * @param <T> the mapped type
     * @param description names this mapper in {@link RowMappingException} messages; make it specific
     *     enough that an operator reading the log knows which query failed
     * @param reader reads one row into the mapped value
     * @return a {@link RowMapper} for use with {@code JdbcClient}
     */
    public static <T> RowMapper<T> of(String description, Function<Row, T> reader) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(reader, "reader");
        // The RowMapper contract passes a ResultSet already positioned on the row, and forbids the
        // mapper from calling next(). Row exposes no cursor movement at all, so that contract cannot
        // be broken by accident here.
        return (resultSet, rowNumber) -> reader.apply(new Row(resultSet, description));
    }
}
