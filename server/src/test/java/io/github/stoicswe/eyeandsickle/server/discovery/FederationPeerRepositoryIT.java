package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * Integration tests for {@link FederationPeerRepository} against a real, Flyway-migrated PostgreSQL.
 *
 * <p>These cover the things a fake cannot: that {@code updateIfNewer}'s {@code WHERE sequence_number <
 * :seq} predicate really is monotonic; that the database's {@code federation_peers_no_sequence_rollback}
 * trigger is a second, independent guarantee against a downgrade even when a raw UPDATE bypasses the
 * service; that {@code ON CONFLICT DO NOTHING} makes a duplicate insert a no-op; that the two liveness
 * timestamps diverge correctly (an unreachable peer never looks reached); and that the ordering and
 * bounds of the read queries hold as the design names them.
 */
class FederationPeerRepositoryIT extends PostgresIntegrationTestBase {

    private static final String DID_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DID_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DID_C = "did:plc:cccccccccccccccccccccccc";
    private static final Instant BASE = Instant.parse("2026-07-24T00:00:00Z");

    private final FederationPeerRepository repository = new FederationPeerRepository(jdbcClient());

    private static byte[] key() {
        return X25519KeyExchange.encodePublicKey(
                X25519KeyExchange.generateKeyPair().getPublic());
    }

    private static ServerDescriptor descriptor(String did, String endpoint, long sequence, byte[] key) {
        return new ServerDescriptor(
                did,
                endpoint,
                key,
                did + "#transport-1",
                null,
                null,
                sequence,
                List.of("federation"),
                null,
                io.github.stoicswe.eyeandsickle.server.persistence.Jsonb.writeObject(
                        Map.of("descriptor", Map.of("peerDid", did, "capabilities", List.of("federation")))));
    }

    // ==================================================================== insert + round-trip

    @Test
    @DisplayName("a new peer inserts and reads back with both liveness timestamps at first-seen and counters zeroed")
    void insertsAndReadsBack() {
        byte[] key = key();
        int inserted = repository.insertNew(descriptor(DID_A, "https://a.example.test", 5, key), BASE);
        assertThat(inserted).isEqualTo(1);

        PeerRecord record = repository.findByDid(DID_A).orElseThrow();
        assertThat(record.peerDid()).isEqualTo(DID_A);
        assertThat(record.endpointUrl()).isEqualTo("https://a.example.test");
        assertThat(record.transportPublicKey()).isEqualTo(key);
        assertThat(record.sequenceNumber()).isEqualTo(5);
        assertThat(record.firstSeenAt()).isEqualTo(BASE);
        assertThat(record.lastSeenAt()).isEqualTo(BASE);
        assertThat(record.lastSuccessfulContactAt()).as("never contacted yet").isNull();
        assertThat(record.contactSuccesses()).isZero();
        assertThat(record.contactFailures()).isZero();
        assertThat(record.consecutiveFailures()).isZero();
        assertThat(record.rowVersion()).isZero();
        assertThat(record.declaredCapabilities()).containsExactly("federation");
    }

    @Test
    @DisplayName("a duplicate insert for the same DID is a no-op, not a second row")
    void duplicateInsertIsNoOp() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 5, key()), BASE);

        // ON CONFLICT (peer_did) DO NOTHING: the concurrent-insert path the service relies on.
        int second = repository.insertNew(descriptor(DID_A, "https://other.example.test", 9, key()), BASE);

        assertThat(second).isZero();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.currentSequence(DID_A)).contains(5L);
    }

    @Test
    @DisplayName("currentSequence is empty for an unknown peer")
    void currentSequenceEmptyForUnknown() {
        assertThat(repository.currentSequence(DID_A)).isEmpty();
    }

    // ==================================================================== monotonic advance

    @Test
    @DisplayName("updateIfNewer advances a strictly-higher sequence, replacing endpoint and key, bumping row_version")
    void updateIfNewerAdvances() {
        repository.insertNew(descriptor(DID_A, "https://old.example.test", 5, key()), BASE);
        byte[] newKey = key();

        int updated = repository.updateIfNewer(
                descriptor(DID_A, "https://new.example.test", 6, newKey), BASE.plusSeconds(10));

        assertThat(updated).isEqualTo(1);
        PeerRecord record = repository.findByDid(DID_A).orElseThrow();
        assertThat(record.sequenceNumber()).isEqualTo(6);
        assertThat(record.endpointUrl()).isEqualTo("https://new.example.test");
        assertThat(record.transportPublicKey()).isEqualTo(newKey);
        assertThat(record.rowVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateIfNewer matches nothing for an equal or lower sequence — the WHERE guard is the rule")
    void updateIfNewerRefusesEqualOrLower() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 6, key()), BASE);

        assertThat(repository.updateIfNewer(descriptor(DID_A, "https://a.example.test", 6, key()), BASE))
                .as("equal sequence advances nothing")
                .isZero();
        assertThat(repository.updateIfNewer(descriptor(DID_A, "https://a.example.test", 5, key()), BASE))
                .as("lower sequence advances nothing")
                .isZero();
        assertThat(repository.currentSequence(DID_A)).contains(6L);
    }

    @Test
    @DisplayName("the database trigger refuses a raw rollback UPDATE even when the service guard is bypassed")
    void triggerRefusesRawRollback() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 7, key()), BASE);

        // A rollback that slips past the WHERE clause (a bug, or a hand-written statement) is still refused
        // at the database boundary — the second, independent anti-downgrade guarantee.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE federation_peers SET sequence_number = 6 WHERE peer_did = :did")
                        .param("did", DID_A)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("must not go backwards");

        // Equality is allowed — a same-sequence re-announcement is a normal refresh, not a rollback.
        assertThatCode(() -> jdbcClient()
                        .sql("UPDATE federation_peers SET sequence_number = 7 WHERE peer_did = :did")
                        .param("did", DID_A)
                        .update())
                .doesNotThrowAnyException();

        assertThat(repository.currentSequence(DID_A)).contains(7L);
    }

    // ==================================================================== liveness bookkeeping

    @Test
    @DisplayName("touchLastSeen advances only last_seen, not the descriptor or successful-contact time")
    void touchLastSeenMovesOnlyLastSeen() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 5, key()), BASE);

        repository.touchLastSeen(DID_A, BASE.plus(Duration.ofHours(2)));

        PeerRecord record = repository.findByDid(DID_A).orElseThrow();
        assertThat(record.lastSeenAt()).isEqualTo(BASE.plus(Duration.ofHours(2)));
        assertThat(record.lastSuccessfulContactAt()).isNull();
        assertThat(record.sequenceNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("a successful contact marks both timestamps, increments successes, and clears the failure streak")
    void successRecorded() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 5, key()), BASE);
        UUID peerId = repository.findByDid(DID_A).orElseThrow().peerId();
        // Build a failure streak first, so we can see the success clear it.
        repository.recordContactFailure(peerId, BASE.plus(Duration.ofMinutes(1)));
        repository.recordContactFailure(peerId, BASE.plus(Duration.ofMinutes(2)));

        Instant contact = BASE.plus(Duration.ofHours(1));
        repository.recordContactSuccess(peerId, contact);

        PeerRecord record = repository.findByDid(DID_A).orElseThrow();
        assertThat(record.contactSuccesses()).isEqualTo(1);
        assertThat(record.contactFailures()).isEqualTo(2);
        assertThat(record.consecutiveFailures()).isZero();
        assertThat(record.lastSuccessfulContactAt()).isEqualTo(contact);
        assertThat(record.lastSeenAt()).isEqualTo(contact);
    }

    @Test
    @DisplayName("a failed contact advances the streak and last_seen but never last_successful_contact_at")
    void failureRecorded() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 5, key()), BASE);
        UUID peerId = repository.findByDid(DID_A).orElseThrow().peerId();

        repository.recordContactFailure(peerId, BASE.plus(Duration.ofMinutes(5)));
        repository.recordContactFailure(peerId, BASE.plus(Duration.ofMinutes(10)));

        PeerRecord record = repository.findByDid(DID_A).orElseThrow();
        assertThat(record.contactFailures()).isEqualTo(2);
        assertThat(record.consecutiveFailures()).isEqualTo(2);
        assertThat(record.lastSeenAt()).isEqualTo(BASE.plus(Duration.ofMinutes(10)));
        assertThat(record.lastSuccessfulContactAt())
                .as("a peer that never answered must not look reached")
                .isNull();
        assertThat(record.successRatio(0.5)).isEqualTo(0.0);
    }

    // ==================================================================== ordered, bounded reads

    @Test
    @DisplayName("livePeers is most-recently-contacted first, with never-contacted peers last")
    void livePeersOrdersByFreshestContact() {
        seedThreePeersWithMixedContact();

        List<String> order =
                repository.livePeers(10).stream().map(PeerRecord::peerDid).toList();

        // A contacted 3h ago, B contacted 1h ago, C never contacted -> A, B, then C (NULLS LAST).
        assertThat(order).containsExactly(DID_A, DID_B, DID_C);
    }

    @Test
    @DisplayName("probeCandidates is stalest-first, so probing cycles the directory instead of hammering a few")
    void probeCandidatesOrdersByStalest() {
        seedThreePeersWithMixedContact();

        List<String> order =
                repository.probeCandidates(10).stream().map(PeerRecord::peerDid).toList();

        // last_seen after seeding: C = BASE, B = BASE+1h, A = BASE+3h -> stalest first is C, B, A.
        assertThat(order).containsExactly(DID_C, DID_B, DID_A);
    }

    @Test
    @DisplayName("a read honours its limit — one round's work is bounded regardless of directory size")
    void readsAreBounded() {
        seedThreePeersWithMixedContact();

        assertThat(repository.livePeers(2)).hasSize(2);
        assertThat(repository.probeCandidates(1)).hasSize(1);
        assertThat(repository.exchangeSample(2)).hasSize(2);
        assertThat(repository.operatorView(2)).hasSize(2);
    }

    @Test
    @DisplayName("exchangeSample returns stored self-descriptors, freshest first")
    void exchangeSampleReturnsDescriptors() {
        seedThreePeersWithMixedContact();

        List<String> descriptors = repository.exchangeSample(2);

        assertThat(descriptors).hasSize(2);
        // Fresh-first ordering means A then B; parse the (jsonb-normalised) blobs and check the DID inside.
        assertThat(peerDidOf(descriptors.get(0))).isEqualTo(DID_A);
        assertThat(peerDidOf(descriptors.get(1))).isEqualTo(DID_B);
    }

    @Test
    @DisplayName("count reflects the directory size")
    void countReflectsSize() {
        assertThat(repository.count()).isZero();
        seedThreePeersWithMixedContact();
        assertThat(repository.count()).isEqualTo(3);
    }

    // ==================================================================== helpers

    private void seedThreePeersWithMixedContact() {
        repository.insertNew(descriptor(DID_A, "https://a.example.test", 1, key()), BASE);
        repository.insertNew(descriptor(DID_B, "https://b.example.test", 1, key()), BASE);
        repository.insertNew(descriptor(DID_C, "https://c.example.test", 1, key()), BASE);

        UUID a = repository.findByDid(DID_A).orElseThrow().peerId();
        UUID b = repository.findByDid(DID_B).orElseThrow().peerId();
        repository.recordContactSuccess(a, BASE.plus(Duration.ofHours(3)));
        repository.recordContactSuccess(b, BASE.plus(Duration.ofHours(1)));
        // C is left never-contacted: last_successful_contact_at stays null, last_seen stays BASE.
    }

    @SuppressWarnings("unchecked")
    private static String peerDidOf(String selfDescriptor) {
        Map<String, Object> envelope =
                io.github.stoicswe.eyeandsickle.server.persistence.Jsonb.readObject(selfDescriptor);
        Map<String, Object> descriptor = (Map<String, Object>) envelope.get("descriptor");
        return (String) descriptor.get("peerDid");
    }
}
