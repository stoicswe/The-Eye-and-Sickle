package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for {@code ledger_transactions}.
 *
 * <p>The house pattern ({@code persistence/RowMappers}): one class per table, column names as
 * constants, mapper as a static field, and never {@code SELECT *}. {@code from_did} is read with the
 * nullable accessor because a mining reward has no payer, and the amount goes through {@link
 * EconomyColumns}, which will not read it as anything but ethecoin (Invariant I1).
 */
final class LedgerRows {

    static final String TX_ID = "tx_id";
    static final String FROM_DID = "from_did";
    static final String TO_DID = "to_did";
    static final String AMOUNT = "amount_ec_minor";
    static final String TX_TYPE = "tx_type";
    static final String TRACEABLE = "traceable";
    static final String MEMO = "memo";
    static final String CREATED_AT = "created_at";

    /** The full column list, for a SELECT that maps to a {@link LedgerTransaction}. */
    static final String COLUMNS =
            String.join(", ", TX_ID, FROM_DID, TO_DID, AMOUNT, TX_TYPE, TRACEABLE, MEMO, CREATED_AT);

    static final RowMapper<LedgerTransaction> MAPPER = RowMappers.of(
            LedgerTransaction.class,
            row -> new LedgerTransaction(
                    row.uuid(TX_ID),
                    row.textOrNull(FROM_DID),
                    row.text(TO_DID),
                    EconomyColumns.ethecoin(row, AMOUNT),
                    LedgerEntryType.fromWire(row.text(TX_TYPE)),
                    row.bool(TRACEABLE),
                    Jsonb.objectColumn(row, MEMO),
                    row.instant(CREATED_AT)));

    private LedgerRows() {}
}
