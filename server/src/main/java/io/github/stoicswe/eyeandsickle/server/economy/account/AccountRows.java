package io.github.stoicswe.eyeandsickle.server.economy.account;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the money-and-heat projection of {@code players}.
 *
 * <p>The house pattern ({@code persistence/RowMappers}): one class per table holding its column names
 * as constants and its mapper as a static field, so a column rename is one edit and every query shares
 * the spelling. This projection is deliberately narrow — it selects only what an {@link Account} needs,
 * never {@code SELECT *}, so a caller of this repository can never accidentally read a player's handle
 * or faction through the money path.
 *
 * <p>{@code slot} rides along with {@code did} because together they are the character's identity: the
 * mapper reproduces the character DID an {@link Account} exposes as its ledger counterparty. Both are
 * read nullable — a local, DID-less character has neither — even though the character-keyed queries that
 * use this mapper only ever match DID-bound rows.
 *
 * <p>Balance goes through {@link EconomyColumns}, which refuses to read it as anything but ethecoin —
 * Invariant I1 at the persistence boundary.
 */
final class AccountRows {

    static final String PLAYER_ID = "player_id";
    static final String DID = "did";
    static final String SLOT = "slot";
    static final String BALANCE = "ethecoin_balance_wei";
    static final String PERSONAL_HEAT = "personal_heat";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    static final RowMapper<Account> MAPPER = RowMappers.of(Account.class, row -> {
        // slot is a smallint, read through the nullable bigint accessor (Row has no int32OrNull) and
        // narrowed: a save slot is 1..16, well within an int. NULL slot means a local character.
        Long slot = row.int64OrNull(SLOT);
        return new Account(
                row.uuid(PLAYER_ID),
                // did is nullable in the schema (local-only play); an account read for the ledger has one,
                // but the mapper must not require it, or a legitimate local-only row fails to map.
                row.textOrNull(DID),
                slot == null ? null : slot.intValue(),
                EconomyColumns.ethecoin(row, BALANCE),
                row.decimal(PERSONAL_HEAT),
                row.int64(ROW_VERSION));
    });

    private AccountRows() {}
}
