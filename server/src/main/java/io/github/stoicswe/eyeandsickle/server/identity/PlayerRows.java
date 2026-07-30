package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for {@code players}.
 *
 * <p>Follows the house pattern documented on {@code RowMappers}: one class per table holding the column
 * spellings as constants and a single static {@link #MAPPER}, so a column rename is one edit and every
 * query reads the table the same way. Never {@code SELECT *} — {@link #COLUMNS} is the explicit
 * projection every read uses, which keeps the mapper and the queries reviewable against each other and
 * stops a later migration silently widening a hot read.
 *
 * <p>The economy columns are read through {@link EconomyColumns} and the faction through
 * {@link EnumColumns} on purpose: those helpers are where Invariant I1 (ethecoin is not cycles) and the
 * enum-vocabulary discipline are enforced, and going around them is how those guarantees erode. The
 * character {@code status} is read through {@link CharacterStatus#fromDb(String)} for the same reason —
 * one authority for that vocabulary.
 */
final class PlayerRows {

    static final String PLAYER_ID = "player_id";
    static final String DID = "did";
    static final String SLOT = "slot";
    static final String HANDLE = "handle";
    static final String STATUS = "status";
    static final String FACTION = "faction";
    static final String PERSONAL_HEAT = "personal_heat";
    static final String BALANCE = "ethecoin_balance_wei";
    static final String CREATED_AT = "created_at";
    static final String LAST_SEEN_AT = "last_seen_at";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    /** The explicit projection for every {@code players} read in this slice. */
    static final String COLUMNS = String.join(
            ", ",
            PLAYER_ID,
            DID,
            SLOT,
            HANDLE,
            STATUS,
            FACTION,
            PERSONAL_HEAT,
            BALANCE,
            CREATED_AT,
            LAST_SEEN_AT,
            ROW_VERSION);

    static final RowMapper<Player> MAPPER = RowMappers.of(
            Player.class,
            row -> new Player(
                    row.uuid(PLAYER_ID),
                    Did.ofNullable(row.textOrNull(DID)),
                    // slot is a nullable smallint; Row has no int32OrNull, so read it as a nullable long
                    // (getLong reads a smallint fine) and narrow it. Math.toIntExact is defensive: a slot
                    // that did not fit an int would be a corrupt row, not a value to truncate silently.
                    slotOf(row.int64OrNull(SLOT)),
                    row.textOrNull(HANDLE),
                    CharacterStatus.fromDb(row.text(STATUS)),
                    EnumColumns.faction(row.text(FACTION)),
                    new Heat(row.decimal(PERSONAL_HEAT)),
                    EconomyColumns.ethecoin(row, BALANCE),
                    row.instant(CREATED_AT),
                    row.instantOrNull(LAST_SEEN_AT),
                    row.int64(ROW_VERSION)));

    private static Integer slotOf(Long raw) {
        return raw == null ? null : Math.toIntExact(raw);
    }

    private PlayerRows() {}
}
