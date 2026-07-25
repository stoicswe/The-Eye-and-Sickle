package io.github.stoicswe.eyeandsickle.server.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * Integration tests for {@link CharacterDirectoryRepository} against a real, Flyway-migrated PostgreSQL.
 *
 * <p>These cover the things a fake cannot: that {@code updateIfNewer}'s {@code WHERE sequence_number <
 * :seq} predicate really is monotonic; that the database's {@code
 * character_directory_no_sequence_rollback} trigger is a second, independent guarantee against a
 * downgrade even when a raw UPDATE bypasses the service; that the {@code (account_did, slot)} unique
 * constraint makes a duplicate a no-op rather than a double home; that two accounts may share a slot but
 * one account may not; and that the ordering and bounds of the reads hold as the design names them.
 */
class CharacterDirectoryRepositoryIT extends PostgresIntegrationTestBase {

    private static final String ACCOUNT_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ACCOUNT_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HOME_A = "did:plc:home0aaaaaaaaaaaaa";
    private static final String HOME_B = "did:plc:home0bbbbbbbbbbbbb";
    private static final Instant BASE = Instant.parse("2026-07-24T00:00:00Z");

    private final CharacterDirectoryRepository repository = new CharacterDirectoryRepository(jdbcClient());

    private static byte[] key() {
        return X25519KeyExchange.encodePublicKey(
                X25519KeyExchange.generateKeyPair().getPublic());
    }

    private static CharacterHomeRecord record(
            String accountDid, UUID characterId, int slot, String homeDid, String endpoint, long sequence, byte[] key) {
        return new CharacterHomeRecord(
                accountDid, characterId, slot, homeDid, homeDid + "#key1", endpoint, key, sequence, new byte[64]);
    }

    // ==================================================================== insert + round-trip

    @Test
    @DisplayName("a new binding inserts and reads back with both timestamps at first-seen and row_version zero")
    void insertsAndReadsBack() {
        UUID characterId = UUID.randomUUID();
        byte[] key = key();
        int inserted = repository.insertNew(
                record(ACCOUNT_A, characterId, 2, HOME_A, "https://home-a.example.test", 5, key), BASE);
        assertThat(inserted).isEqualTo(1);

        CharacterHomeEntry entry = repository.findByAccountAndSlot(ACCOUNT_A, 2).orElseThrow();
        assertThat(entry.accountDid()).isEqualTo(ACCOUNT_A);
        assertThat(entry.characterId()).isEqualTo(characterId);
        assertThat(entry.slot()).isEqualTo(2);
        assertThat(entry.homeServerDid()).isEqualTo(HOME_A);
        assertThat(entry.homeEndpoint()).isEqualTo("https://home-a.example.test");
        assertThat(entry.homeTransportPublicKey()).isEqualTo(key);
        assertThat(entry.signingKeyId()).isEqualTo(HOME_A + "#key1");
        assertThat(entry.sequenceNumber()).isEqualTo(5);
        assertThat(entry.firstSeenAt()).isEqualTo(BASE);
        assertThat(entry.lastSeenAt()).isEqualTo(BASE);
        assertThat(entry.rowVersion()).isZero();
    }

    @Test
    @DisplayName("a duplicate insert for the same (account, slot) is a no-op, not a second home")
    void duplicateAccountSlotInsertIsNoOp() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_A, "https://a.example.test", 5, key()), BASE);

        int second = repository.insertNew(
                record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_B, "https://b.example.test", 9, key()), BASE);

        assertThat(second).isZero();
        assertThat(repository.count()).isEqualTo(1);
        CharacterHomeEntry entry = repository.findByAccountAndSlot(ACCOUNT_A, 1).orElseThrow();
        assertThat(entry.homeServerDid()).isEqualTo(HOME_A);
        assertThat(entry.sequenceNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("two different accounts may occupy the same slot number")
    void differentAccountsShareSlot() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(record(ACCOUNT_B, UUID.randomUUID(), 1, HOME_B, "https://b.example.test", 1, key()), BASE);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByAccountAndSlot(ACCOUNT_A, 1)).isPresent();
        assertThat(repository.findByAccountAndSlot(ACCOUNT_B, 1)).isPresent();
    }

    // ==================================================================== monotonic advance

    @Test
    @DisplayName(
            "updateIfNewer advances a strictly-higher sequence, replacing home/char/endpoint/key, bumping row_version")
    void updateIfNewerAdvances() {
        repository.insertNew(
                record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://old.example.test", 5, key()), BASE);
        UUID freshId = UUID.randomUUID();
        byte[] newKey = key();

        int updated = repository.updateIfNewer(
                record(ACCOUNT_A, freshId, 2, HOME_B, "https://new.example.test", 6, newKey), BASE.plusSeconds(10));

        assertThat(updated).isEqualTo(1);
        CharacterHomeEntry entry = repository.findByAccountAndSlot(ACCOUNT_A, 2).orElseThrow();
        assertThat(entry.sequenceNumber()).isEqualTo(6);
        assertThat(entry.homeServerDid()).isEqualTo(HOME_B);
        assertThat(entry.characterId()).isEqualTo(freshId);
        assertThat(entry.homeEndpoint()).isEqualTo("https://new.example.test");
        assertThat(entry.homeTransportPublicKey()).isEqualTo(newKey);
        assertThat(entry.rowVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateIfNewer matches nothing for an equal or lower sequence — the WHERE guard is the rule")
    void updateIfNewerRefusesEqualOrLower() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 6, key()), BASE);

        assertThat(repository.updateIfNewer(
                        record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 6, key()), BASE))
                .as("equal sequence advances nothing")
                .isZero();
        assertThat(repository.updateIfNewer(
                        record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 5, key()), BASE))
                .as("lower sequence advances nothing")
                .isZero();
        assertThat(repository.findByAccountAndSlot(ACCOUNT_A, 2).orElseThrow().sequenceNumber())
                .isEqualTo(6);
    }

    @Test
    @DisplayName("the database trigger refuses a raw rollback UPDATE even when the service guard is bypassed")
    void triggerRefusesRawRollback() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 7, key()), BASE);

        // A rollback that slips past the WHERE clause (a bug, or a hand-written statement) is still refused
        // at the database boundary — the second, independent anti-downgrade guarantee.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE character_directory SET sequence_number = 6 WHERE account_did = :did")
                        .param("did", ACCOUNT_A)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("must not go backwards");

        // Equality is allowed — a same-sequence re-announcement is a normal refresh, not a rollback.
        assertThatCode(() -> jdbcClient()
                        .sql("UPDATE character_directory SET sequence_number = 7 WHERE account_did = :did")
                        .param("did", ACCOUNT_A)
                        .update())
                .doesNotThrowAnyException();

        assertThat(repository.findByAccountAndSlot(ACCOUNT_A, 2).orElseThrow().sequenceNumber())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("touchLastSeen advances only last_seen, not the binding or the sequence")
    void touchLastSeenMovesOnlyLastSeen() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 5, key()), BASE);

        repository.touchLastSeen(ACCOUNT_A, 2, BASE.plus(Duration.ofHours(2)));

        CharacterHomeEntry entry = repository.findByAccountAndSlot(ACCOUNT_A, 2).orElseThrow();
        assertThat(entry.lastSeenAt()).isEqualTo(BASE.plus(Duration.ofHours(2)));
        assertThat(entry.firstSeenAt()).isEqualTo(BASE);
        assertThat(entry.sequenceNumber()).isEqualTo(5);
    }

    // ==================================================================== ordered, bounded reads

    @Test
    @DisplayName("findByAccount is ordered by slot and counts one per occupied slot")
    void findByAccountOrdersBySlot() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 3, HOME_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 1, key()), BASE);
        // A different account's binding must not appear in this account's resolution or count.
        repository.insertNew(record(ACCOUNT_B, UUID.randomUUID(), 1, HOME_B, "https://b.example.test", 1, key()), BASE);

        List<Integer> slots = repository.findByAccount(ACCOUNT_A, 10).stream()
                .map(CharacterHomeEntry::slot)
                .toList();

        assertThat(slots).containsExactly(1, 2, 3);
        assertThat(repository.countByAccount(ACCOUNT_A)).isEqualTo(3);
        assertThat(repository.countByAccount(ACCOUNT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("findByAccount honours its limit — one resolution's work is bounded")
    void findByAccountIsBounded() {
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 2, HOME_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 3, HOME_A, "https://a.example.test", 1, key()), BASE);

        assertThat(repository.findByAccount(ACCOUNT_A, 2)).hasSize(2);
    }

    @Test
    @DisplayName("count reflects the directory size and countByAccount is empty for an unknown account")
    void countReflectsSize() {
        assertThat(repository.count()).isZero();
        assertThat(repository.countByAccount(ACCOUNT_A)).isZero();
        repository.insertNew(record(ACCOUNT_A, UUID.randomUUID(), 1, HOME_A, "https://a.example.test", 1, key()), BASE);
        assertThat(repository.count()).isEqualTo(1);
    }
}
