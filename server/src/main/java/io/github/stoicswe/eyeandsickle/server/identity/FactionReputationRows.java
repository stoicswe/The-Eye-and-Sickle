package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.FactionReputation;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for {@code faction_reputations}.
 *
 * <h2>⚠ This is faction reputation, never validator reputation</h2>
 *
 * The row maps to the protocol {@link FactionReputation} — a <em>player's</em> Eye/Sickle standing —
 * and to nothing in the federation {@code validators} table. The two share no column and no key by
 * design ({@code docs/design/glossary.md}); there is no join between them and this mapper adds none. The
 * {@code faction} column is read through {@link EnumColumns#faction(String)}, whose named-faction
 * vocabulary already excludes {@code none}, matching {@link FactionReputation}'s rule that standing is
 * always with a named side.
 *
 * <p>{@code row_version} is projected because standing is mutated under optimistic concurrency; the
 * value the mapper returns is what a version-checked update must match.
 */
final class FactionReputationRows {

    static final String PLAYER_ID = "player_id";
    static final String FACTION = "faction";
    static final String STANDING = "standing";
    static final String UPDATED_AT = "updated_at";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    /** The explicit projection for every {@code faction_reputations} read in this slice. */
    static final String COLUMNS = String.join(", ", PLAYER_ID, FACTION, STANDING, UPDATED_AT, ROW_VERSION);

    /**
     * Maps to the protocol value type the profile displays. The player id and row version are read
     * separately where a caller needs them; the reputation value itself is exactly faction + standing.
     */
    static final RowMapper<FactionReputation> MAPPER = RowMappers.of(
            FactionReputation.class,
            row -> new FactionReputation(EnumColumns.faction(row.text(FACTION)), row.int64(STANDING)));

    private FactionReputationRows() {}
}
