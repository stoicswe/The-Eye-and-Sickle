package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.FactionReputation;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes {@code faction_reputations} — a player's per-faction standing
 * ({@code docs/design/01-core-resources.md} §5).
 *
 * <h2>⚠ Faction reputation, not validator reputation</h2>
 *
 * Everything here is keyed by {@code player_id} and constrained to a named faction. It has no
 * relationship to the federation {@code validators} table's {@code validator_reputation}: different
 * subject, different lifetime, no shared column, no join ({@code docs/design/glossary.md}). This
 * repository cannot express one because the schema cannot.
 *
 * <h2>Both sides before the commitment</h2>
 *
 * A player can hold standing with Eye and Sickle at once — the design has reputation only
 * <em>eventually</em> force a binary commitment — so standings are per-{@code (player, faction)} rows,
 * not one column. Which side the player has committed to is {@code players.faction}, owned by
 * {@link PlayerRepository}; this table is the standings that feed that decision.
 */
@Repository
public class FactionReputationRepository {

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient Spring's JdbcClient over the server's Postgres
     */
    public FactionReputationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * @param playerId the player
     * @return the player's standings, one per named faction they have any standing with, ordered by
     *     faction for a stable read
     */
    public List<FactionReputation> findByPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return jdbcClient
                .sql("SELECT " + FactionReputationRows.COLUMNS
                        + " FROM faction_reputations WHERE player_id = :playerId ORDER BY faction")
                .param("playerId", playerId)
                .query(FactionReputationRows.MAPPER)
                .list();
    }

    /**
     * Moves a player's standing with a faction by a delta, creating the row at the delta if none exists.
     *
     * <p>The change is a single atomic {@code standing = standing + :delta}, so two concurrent
     * adjustments both land rather than one clobbering the other with a stale read-modify-write. Standing
     * may go negative ({@code docs/design/15} P-11: actively hostile), which the schema permits.
     *
     * @param playerId the player
     * @param faction a named faction; {@link Faction#NONE} is rejected
     * @param delta the signed change
     * @param now the instant to stamp
     * @throws IllegalArgumentException if {@code faction} is {@link Faction#NONE}
     */
    public void adjustStanding(UUID playerId, Faction faction, long delta, Instant now) {
        requireNamed(faction);
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        jdbcClient
                .sql("""
                        MERGE INTO faction_reputations AS t
                        USING (VALUES (CAST(:playerId AS uuid), CAST(:faction AS varchar),
                                       CAST(:delta AS bigint), CAST(:now AS timestamp with time zone)))
                              AS s(player_id, faction, delta, now)
                           ON t.player_id = s.player_id AND t.faction = s.faction
                         WHEN MATCHED THEN UPDATE
                              SET standing    = t.standing + s.delta,
                                  updated_at  = s.now,
                                  row_version = t.row_version + 1
                         WHEN NOT MATCHED THEN INSERT (player_id, faction, standing, updated_at, row_version)
                              VALUES (s.player_id, s.faction, s.delta, s.now, 0)
                        """)
                .param("playerId", playerId)
                .param("faction", EnumColumns.faction(faction))
                .param("delta", delta)
                .param("now", at(now))
                .update();
    }

    /**
     * Sets a player's standing with a faction to an absolute value, used to reset it on abandonment
     * ({@code docs/design/01-core-resources.md} §5).
     *
     * @param playerId the player
     * @param faction a named faction; {@link Faction#NONE} is rejected
     * @param standing the value to set
     * @param now the instant to stamp
     * @throws IllegalArgumentException if {@code faction} is {@link Faction#NONE}
     */
    public void setStanding(UUID playerId, Faction faction, long standing, Instant now) {
        requireNamed(faction);
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        jdbcClient
                .sql("""
                        MERGE INTO faction_reputations AS t
                        USING (VALUES (CAST(:playerId AS uuid), CAST(:faction AS varchar),
                                       CAST(:standing AS bigint), CAST(:now AS timestamp with time zone)))
                              AS s(player_id, faction, standing, now)
                           ON t.player_id = s.player_id AND t.faction = s.faction
                         WHEN MATCHED THEN UPDATE
                              SET standing    = s.standing,
                                  updated_at  = s.now,
                                  row_version = t.row_version + 1
                         WHEN NOT MATCHED THEN INSERT (player_id, faction, standing, updated_at, row_version)
                              VALUES (s.player_id, s.faction, s.standing, s.now, 0)
                        """)
                .param("playerId", playerId)
                .param("faction", EnumColumns.faction(faction))
                .param("standing", standing)
                .param("now", at(now))
                .update();
    }

    private static void requireNamed(Faction faction) {
        Objects.requireNonNull(faction, "faction");
        if (faction == Faction.NONE) {
            // The DB would reject 'none' via ck_faction_reputations_named_faction; catching it here
            // names the rule instead of surfacing a raw constraint violation. Standing is always with a
            // named side; NONE is the absence of a side, not a third one.
            throw new IllegalArgumentException(
                    "Standing is held with a named faction; Faction.NONE is the absence of one, not a target for it");
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
