package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes {@code players} — the identity slice's core table, now one row per <em>character</em>
 * ({@code docs/architecture/09-player-state-portability.md} §1).
 *
 * <h2>A DID is an account, not a player (09 §1, §8)</h2>
 *
 * V2 keyed one player per DID ({@code uq_players_did}); V3 drops that and moves uniqueness to
 * {@code (did, slot)}, because a DID is now an <em>account</em> that may hold up to
 * {@code CharacterProperties.maxCharacters} characters. So this repository no longer has a
 * create-or-refresh "upsert on sign-in": sign-in yields an account and its characters, and creating a
 * character is a separate, cap-checked step owned by {@link CharacterService}. The methods here are the
 * primitives that step is built from — list an account's characters, create one, look one up, and move a
 * character's lifecycle status forward.
 *
 * <h2>What this repository will not do (Invariant I1, I14)</h2>
 *
 * It never writes {@code ethecoin_balance_wei} — a balance change is a ledger transaction owned by
 * the economy, written in the same transaction as its ledger row, not a side effect here. New characters
 * are created with a zero balance and the schema's defaults, and this repository leaves it there. It also
 * does not create a {@code rigs} row: a starting rig is the compute system's concern, and this slice
 * deliberately does not reach into it.
 */
@Repository
public class PlayerRepository {

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient Spring's JdbcClient over the server's Postgres
     */
    public PlayerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    // ------------------------------------------------------------------ reads

    /**
     * Every character on this server belonging to an account, all lifecycle states, ordered by slot.
     *
     * <p>Returns migrated and retired shells as well as active characters, because two callers need the
     * full set: slot assignment must avoid <em>any</em> slot the {@code (did, slot)} unique constraint
     * still holds (a migrated character keeps its slot number — 09 §6.1), and an audit view wants the
     * history. Callers that only want playable characters filter on {@link Player#status()}.
     *
     * @param did the account identity
     * @return the account's characters, ordered by slot; empty if the account has none here
     */
    public List<Player> findCharactersByDid(Did did) {
        Objects.requireNonNull(did, "did");
        return jdbcClient
                .sql("SELECT " + PlayerRows.COLUMNS + " FROM players WHERE did = :did ORDER BY slot")
                .param("did", did.value())
                .query(PlayerRows.MAPPER)
                .list();
    }

    /**
     * @param characterId the character (a {@code players} row) id
     * @return the character, or empty if none exists
     */
    public Optional<Player> findCharacter(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        return jdbcClient
                .sql("SELECT " + PlayerRows.COLUMNS + " FROM players WHERE player_id = :characterId")
                .param("characterId", characterId)
                .query(PlayerRows.MAPPER)
                .optional();
    }

    /**
     * @param characterId the character id
     * @return the character
     * @throws PlayerNotFoundException if no such row exists — for callers that treat absence as a bug,
     *     not a branch
     */
    public Player requireCharacter(UUID characterId) {
        return findCharacter(characterId).orElseThrow(() -> new PlayerNotFoundException(characterId));
    }

    /**
     * How many <em>active</em> characters this account holds on this server.
     *
     * <p>The single-server default behind {@link RecognizedCharacterCount}: it counts only rows this
     * server hosts, and only {@code active} ones, because a migrated character now counts against its new
     * home and a retired one counts nowhere (09 §6.1). A directory-backed count that sees the whole
     * federation supersedes this; until one is wired, this is the honest local answer.
     *
     * @param did the account identity
     * @return the count of active DID-bound characters for the account
     */
    public long countActiveCharacters(Did did) {
        Objects.requireNonNull(did, "did");
        return jdbcClient
                .sql("SELECT count(*) FROM players WHERE did = :did AND status = 'active'")
                .param("did", did.value())
                .query(Long.class)
                .single();
    }

    // ------------------------------------------------------------------ creation

    // ⚠ `SELECT ... FROM FINAL TABLE (INSERT ...)` is the SQL:2011 delta-table form, and it is what
    // replaced PostgreSQL's `INSERT ... RETURNING` here. Both read back the row the engine actually
    // wrote, in one statement, so the schema defaults (`row_version`, timestamps) come from the row
    // rather than being predicted by this class — which is the property worth keeping. Splitting it
    // into an INSERT and a follow-up SELECT would be two statements a concurrent writer can get
    // between, and would report a row this call did not necessarily create.

    /**
     * Creates a new DID-bound character at a specific slot, active and empty.
     *
     * <p>The caller ({@link CharacterService}) decides the slot — it owns the cap policy and the free-slot
     * choice — and this method only inserts. The character starts uncommitted ({@code none}), zero heat,
     * zero balance and the schema defaults; a balance is never set at creation (Invariant I1/I14). Two
     * characters cannot share a slot within an account: {@code uq_players_did_slot} makes a collision a
     * constraint violation rather than a silent second character.
     *
     * @param did the account identity; never {@code null} for an online character
     * @param handle the current display handle, or {@code null} if the account has none
     * @param slot the slot to occupy (1..{@link Player#MAX_SLOT})
     * @param now the creation instant, supplied by the caller's clock for testability
     * @return the created character
     */
    public Player createCharacter(Did did, String handle, int slot, Instant now) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(now, "now");
        return jdbcClient
                .sql("SELECT " + PlayerRows.COLUMNS + """
                         FROM FINAL TABLE (
                              INSERT INTO players (player_id, did, slot, handle, status, faction, personal_heat,
                                                   ethecoin_balance_wei, created_at, last_seen_at, row_version)
                              VALUES (:playerId, :did, :slot, :handle, :status, 'none', 0, 0, :now, :now, 0))
                        """)
                .param("playerId", UUID.randomUUID())
                .param("did", did.value())
                .param("slot", slot)
                .param("handle", handle)
                .param("status", CharacterStatus.ACTIVE.dbValue())
                .param("now", Timestamps.at(now))
                .query(PlayerRows.MAPPER)
                .single();
    }

    /**
     * Creates a local, DID-less character — offline, single-player, exempt from the cap (09 §1).
     *
     * <p>{@code did} and {@code slot} are both NULL (the schema's {@code ck_players_slot_pairing}), so
     * such a character is outside the account/slot system entirely: no directory, no federation, no cap.
     * The NULLs are written as SQL literals rather than bound parameters so the driver never has to infer
     * a type for a null-valued typed column.
     *
     * @param handle the display handle, or {@code null}
     * @param now the creation instant
     * @return the created local character
     */
    public Player createLocalCharacter(String handle, Instant now) {
        Objects.requireNonNull(now, "now");
        return jdbcClient
                .sql("SELECT " + PlayerRows.COLUMNS + """
                         FROM FINAL TABLE (
                              INSERT INTO players (player_id, did, slot, handle, status, faction, personal_heat,
                                                   ethecoin_balance_wei, created_at, last_seen_at, row_version)
                              VALUES (:playerId, NULL, NULL, :handle, :status, 'none', 0, 0, :now, :now, 0))
                        """)
                .param("playerId", UUID.randomUUID())
                .param("handle", handle)
                .param("status", CharacterStatus.ACTIVE.dbValue())
                .param("now", Timestamps.at(now))
                .query(PlayerRows.MAPPER)
                .single();
    }

    // ------------------------------------------------------------------ mutations

    /**
     * Moves a character's lifecycle status forward, under optimistic concurrency.
     *
     * <p>This is the write half of the one-way status transition (09 §6.1). It does not itself enforce
     * that the transition is legal — that {@code active -> migrated/retired} and never back — because the
     * legality check needs the current status, which the caller ({@link CharacterService}) has already
     * read; this applies the decided change against the version it was decided on. A concurrent writer
     * that advanced the row first makes this match zero rows, which {@link Mutations#requireUpdated} turns
     * into a retryable conflict rather than a lost write.
     *
     * @param characterId the character to transition
     * @param status the new lifecycle status
     * @param expectedVersion the {@code row_version} the caller read
     * @throws org.springframework.dao.OptimisticLockingFailureException if the row was concurrently
     *     changed or is gone
     */
    public void updateStatus(UUID characterId, CharacterStatus status, long expectedVersion) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(status, "status");
        int updated = jdbcClient
                .sql("""
                        UPDATE players
                           SET status      = :status,
                               row_version = row_version + 1
                         WHERE player_id = :characterId
                           AND row_version = :expectedVersion
                        """)
                .param("status", status.dbValue())
                .param("characterId", characterId)
                .param("expectedVersion", expectedVersion)
                .update();
        Mutations.requireUpdated(updated, "players", characterId);
    }

    /**
     * Sets a character's committed faction, under optimistic concurrency.
     *
     * @param characterId the character
     * @param faction the new commitment
     * @param expectedVersion the {@code row_version} the caller read
     * @throws org.springframework.dao.OptimisticLockingFailureException if the row was concurrently
     *     changed or is gone
     */
    public void updateFaction(UUID characterId, Faction faction, long expectedVersion) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(faction, "faction");
        int updated = jdbcClient
                .sql("""
                        UPDATE players
                           SET faction     = :faction,
                               row_version = row_version + 1
                         WHERE player_id = :characterId
                           AND row_version = :expectedVersion
                        """)
                .param("faction", EnumColumns.faction(faction))
                .param("characterId", characterId)
                .param("expectedVersion", expectedVersion)
                .update();
        Mutations.requireUpdated(updated, "players", characterId);
    }

    /**
     * Sets a character's faction and personal heat together, under optimistic concurrency.
     *
     * <p>One statement, one version bump: faction abandonment resets the side and applies a heat spike as
     * a single fact, so they advance the row together rather than as two updates a reader could observe
     * half-applied.
     *
     * @param characterId the character
     * @param faction the new commitment (abandonment sets this to {@link Faction#NONE})
     * @param heat the new personal-heat reading
     * @param expectedVersion the {@code row_version} the caller read
     * @throws org.springframework.dao.OptimisticLockingFailureException if the row was concurrently
     *     changed or is gone
     */
    public void updateFactionAndHeat(UUID characterId, Faction faction, Heat heat, long expectedVersion) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(heat, "heat");
        int updated = jdbcClient
                .sql("""
                        UPDATE players
                           SET faction       = :faction,
                               personal_heat = :heat,
                               row_version   = row_version + 1
                         WHERE player_id = :characterId
                           AND row_version = :expectedVersion
                        """)
                .param("faction", EnumColumns.faction(faction))
                .param("heat", heat.value())
                .param("characterId", characterId)
                .param("expectedVersion", expectedVersion)
                .update();
        Mutations.requireUpdated(updated, "players", characterId);
    }
}
