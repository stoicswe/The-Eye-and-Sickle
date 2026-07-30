package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@link PlayerRepository} against a real PostgreSQL — the character/slot core (09 §1, §8). The behaviour
 * that needs the database: a DID is an account that may hold several characters (the dropped
 * {@code uq_players_did}), the {@code (did, slot)} uniqueness that replaces it, the did/slot pairing and
 * slot-bound CHECKs, the {@code status} CHECK, and the version-checked mutations a lost write must fail
 * rather than clobber. The failures are tested hardest.
 */
class PlayerRepositoryIT extends PostgresIntegrationTestBase {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did OTHER = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Instant FIRST = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-07-25T09:30:00Z");

    private PlayerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PlayerRepository(jdbcClient());
    }

    // ------------------------------------------------------------------ creation and reads

    @Test
    @DisplayName("createCharacter makes an active, slotted, uncommitted, zero-heat, zero-balance character")
    void createCharacter() {
        Player character = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);

        assertThat(character.did()).isEqualTo(DID);
        assertThat(character.slot()).isEqualTo(1);
        assertThat(character.status()).isEqualTo(CharacterStatus.ACTIVE);
        assertThat(character.handle()).isEqualTo("alice.bsky.social");
        assertThat(character.faction()).isEqualTo(Faction.NONE);
        assertThat(character.personalHeat().value()).isEqualByComparingTo("0");
        // Invariant I1 / I14: creation never touches the balance.
        assertThat(character.ethecoinBalance()).isEqualTo(Ethecoin.ZERO);
        assertThat(character.createdAt()).isEqualTo(FIRST);
        assertThat(character.lastSeenAt()).isEqualTo(FIRST);
        assertThat(character.rowVersion()).isZero();
    }

    @Test
    @DisplayName("an account may hold several characters — the dropped uq_players_did — listed by slot")
    void oneAccountManyCharacters() {
        repository.createCharacter(DID, "alice.bsky.social", 2, FIRST);
        repository.createCharacter(DID, "alice.bsky.social", 1, SECOND);
        repository.createCharacter(OTHER, "bob", 1, FIRST);

        List<Player> mine = repository.findCharactersByDid(DID);
        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(Player::slot).containsExactly(1, 2); // ordered by slot
        assertThat(repository.findCharactersByDid(OTHER)).hasSize(1);
    }

    @Test
    @DisplayName("lookups resolve by character id; requireCharacter turns absence into a 404")
    void lookups() {
        Player created = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);

        assertThat(repository.findCharacter(created.playerId())).contains(created);
        assertThat(repository.requireCharacter(created.playerId())).isEqualTo(created);
        assertThat(repository.findCharacter(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> repository.requireCharacter(UUID.randomUUID()))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    @DisplayName("a null handle is allowed — the provider may resolve none")
    void nullHandleAllowed() {
        Player character = repository.createCharacter(DID, null, 1, FIRST);
        assertThat(character.handle()).isNull();
    }

    @Test
    @DisplayName("countActiveCharacters counts only this account's active characters")
    void countActive() {
        Player active = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);
        repository.createCharacter(DID, "alice.bsky.social", 2, FIRST);
        repository.createCharacter(OTHER, "bob", 1, FIRST);
        repository.updateStatus(active.playerId(), CharacterStatus.RETIRED, active.rowVersion());

        // One of DID's two was retired, so one active remains for DID; OTHER is unaffected.
        assertThat(repository.countActiveCharacters(DID)).isEqualTo(1);
        assertThat(repository.countActiveCharacters(OTHER)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ local characters

    @Test
    @DisplayName("a local character has a null DID and null slot, and many may coexist")
    void localCharacters() {
        Player first = repository.createLocalCharacter("solo-1", FIRST);
        Player second = repository.createLocalCharacter("solo-2", FIRST);

        assertThat(first.did()).isNull();
        assertThat(first.slot()).isNull();
        assertThat(first.isLocal()).isTrue();
        // uq_players_did_slot treats NULLs as distinct, so two (NULL, NULL) rows are permitted.
        assertThat(second.playerId()).isNotEqualTo(first.playerId());
        assertThat(countPlayers()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ status transitions

    @Test
    @DisplayName("updateStatus moves the character to a terminal state and bumps the version")
    void updateStatus() {
        Player created = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);

        repository.updateStatus(created.playerId(), CharacterStatus.MIGRATED, created.rowVersion());

        Player after = repository.requireCharacter(created.playerId());
        assertThat(after.status()).isEqualTo(CharacterStatus.MIGRATED);
        assertThat(after.rowVersion()).isEqualTo(created.rowVersion() + 1);
    }

    @Test
    @DisplayName("updateStatus on a stale version matches nothing and is reported as a conflict")
    void updateStatusOptimisticLock() {
        Player created = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);
        repository.updateStatus(created.playerId(), CharacterStatus.RETIRED, 0);

        assertThatThrownBy(() -> repository.updateStatus(created.playerId(), CharacterStatus.MIGRATED, 0))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.requireCharacter(created.playerId()).status()).isEqualTo(CharacterStatus.RETIRED);
    }

    // ------------------------------------------------------------------ faction / heat (unchanged behaviour)

    @Test
    @DisplayName("updateFactionAndHeat advances faction and heat together in one version bump")
    void updateFactionAndHeat() {
        Player created = repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);
        repository.updateFaction(created.playerId(), Faction.EYE, 0); // version -> 1

        repository.updateFactionAndHeat(created.playerId(), Faction.NONE, new Heat(new java.math.BigDecimal("7.5")), 1);

        Player after = repository.requireCharacter(created.playerId());
        assertThat(after.faction()).isEqualTo(Faction.NONE);
        assertThat(after.personalHeat().value()).isEqualByComparingTo("7.5");
        assertThat(after.rowVersion()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ the SQL constraints bite

    @Test
    @DisplayName("uq_players_did_slot: two characters cannot share a slot within one account")
    void slotUniquenessBites() {
        repository.createCharacter(DID, "alice.bsky.social", 1, FIRST);
        assertThatThrownBy(() -> repository.createCharacter(DID, "alice.bsky.social", 1, SECOND))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_players_slot_pairing: a DID-bound row with no slot, or a slot with no DID, is refused")
    void pairingBites() {
        // did set, slot NULL
        assertThatThrownBy(() -> rawInsert("VALUES (:id, :did, NULL, 'h', 'active', 'none', 0, 0, now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // did NULL, slot set
        assertThatThrownBy(() -> rawInsert("VALUES (:id, NULL, 1, 'h', 'active', 'none', 0, 0, now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_players_slot_bound: a slot outside 1..16 is refused")
    void slotBoundBites() {
        assertThatThrownBy(() -> rawInsert("VALUES (:id, :did, 0, 'h', 'active', 'none', 0, 0, now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawInsert("VALUES (:id, :did, 17, 'h', 'active', 'none', 0, 0, now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ck_players_status: an unknown status value is refused")
    void statusCheckBites() {
        assertThatThrownBy(() -> rawInsert("VALUES (:id, :did, 1, 'h', 'bogus', 'none', 0, 0, now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ helpers

    /** Raw insert with a caller-supplied VALUES clause, for exercising CHECK/UNIQUE constraints directly. */
    private void rawInsert(String valuesClause) {
        jdbcClient()
                .sql("INSERT INTO players (player_id, did, slot, handle, status, faction, personal_heat, "
                        + "ethecoin_balance_wei, created_at, last_seen_at, row_version) " + valuesClause)
                .param("id", UUID.randomUUID())
                .param("did", DID.value())
                .update();
    }

    private long countPlayers() {
        return jdbcClient()
                .sql("SELECT count(*) FROM players")
                .query(Long.class)
                .single();
    }
}
