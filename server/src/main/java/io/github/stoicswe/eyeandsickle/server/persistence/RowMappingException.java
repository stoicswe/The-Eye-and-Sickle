package io.github.stoicswe.eyeandsickle.server.persistence;

import org.springframework.dao.DataRetrievalFailureException;

/**
 * Thrown when a row cannot be turned into a record: a column that is not in the result set, a NULL in
 * a column the mapper requires, or a value of the wrong SQL type.
 *
 * <h2>Why a distinct exception and not a bare {@code SQLException}</h2>
 *
 * Hand-written SQL mapped to records ({@code docs/design/15-open-questions.md} A-4) trades away the
 * ORM's compile-time column checking. What it buys back is legibility — the manual-audit queries that
 * make {@code docs/design/04-mining.md} §3.1 playable read as SQL, not as a derived projection — and
 * the price is that a column rename surfaces at runtime.
 *
 * <p>So the runtime failure has to be worth reading. {@code SQLException}'s "column 'total_cycle' not
 * found" tells an operator nothing about which query or which record; this one names the mapper, the
 * column, and what was expected, because the whole point of choosing JdbcClient was that the failure
 * modes stay obvious rather than magical.
 *
 * <p>Extends Spring's {@link DataRetrievalFailureException} so it lands in the same hierarchy as every
 * other data-access failure and a caller that catches {@code DataAccessException} does not have to
 * learn about this class to behave correctly.
 */
public class RowMappingException extends DataRetrievalFailureException {

    /**
     * @param message what went wrong, naming the mapper and the column
     */
    public RowMappingException(String message) {
        super(message);
    }

    /**
     * @param message what went wrong, naming the mapper and the column
     * @param cause the underlying JDBC failure, kept for the operator's log
     */
    public RowMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
