package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Hand-written SQL over {@code federation_peers}, the discovery directory table.
 *
 * <h2>Thin primitives; the decision lives in the service</h2>
 *
 * These methods are individual, legible statements. The multi-step convergence decision — insert vs.
 * advance vs. ignore-as-stale — is composed in {@link PeerDirectoryService} inside one
 * {@code @Transactional} method, following the house rule that a decision spanning several statements
 * gets one transaction boundary ({@code Mutations}).
 *
 * <h2>Monotonic advance is expressed in the WHERE clause</h2>
 *
 * {@link #updateIfNewer} advances a peer only {@code WHERE sequence_number < :seq}. That single
 * predicate is the whole last-writer-wins-on-sequence rule ({@code
 * docs/architecture/08-discovery-and-sync.md} §3): it is race-safe (only the highest offered sequence
 * wins, whichever connection runs first), and it can never regress a peer, which the database's
 * {@code federation_peers_no_sequence_rollback} trigger then guarantees a second time. A wall clock
 * never enters into it.
 *
 * <h2>{@code last_seen_at} means "last observed activity"</h2>
 *
 * The column comment distinguishes <em>seen</em> (announced in the directory) from <em>contacted</em>
 * (a handshake completed). This repository bumps {@code last_seen_at} on any observation of a peer — an
 * announcement <em>or</em> a probe attempt, success or failure — and reserves
 * {@code last_successful_contact_at} for an actual success. That gives the back-off scheduler a
 * "when did we last touch this peer" timestamp without a schema change, while true reachability stays
 * in {@code last_successful_contact_at}.
 */
@Repository
public class FederationPeerRepository {

    private final JdbcClient jdbcClient;

    FederationPeerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * The stored sequence number for a peer, if this server knows it.
     *
     * @param peerDid the peer's DID
     * @return the sequence, or empty if the peer is unknown
     */
    public Optional<Long> currentSequence(String peerDid) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.SEQUENCE_NUMBER + " FROM " + FederationPeerRows.TABLE + " WHERE "
                        + FederationPeerRows.PEER_DID + " = :did")
                .param("did", peerDid)
                .query(Long.class)
                .optional();
    }

    /**
     * Inserts a previously-unknown peer, doing nothing if a concurrent writer inserted it first.
     *
     * @param descriptor the verified descriptor
     * @param now the observation instant
     * @return 1 if inserted, 0 if a row for this DID already existed
     */
    public int insertNew(ServerDescriptor descriptor, Instant now) {
        return jdbcClient
                .sql("""
                        MERGE INTO federation_peers AS t
                        -- ⚠ :key is VARBINARY, never varchar. `transport_public_key` is bytea, and
                        -- casting it through a character type UTF-8-decodes it: every byte >= 0x80
                        -- becomes U+FFFD and a 32-byte key reads back as 64 bytes of replacement
                        -- characters. It stores and reads without an error — the key is simply not
                        -- the key any more, and the first thing to notice would be a peer whose
                        -- signatures never verify.
                        USING (VALUES (CAST(:peerId AS uuid), CAST(:did AS varchar), CAST(:endpoint AS varchar),
                                       CAST(:key AS varbinary), CAST(:keyId AS varchar),
                                       CAST(:notBefore AS timestamp with time zone),
                                       CAST(:notAfter AS timestamp with time zone),
                                       :descriptor FORMAT JSON, CAST(:seq AS bigint),
                                       CAST(:now AS timestamp with time zone)))
                              AS s(peer_id, peer_did, endpoint_url, transport_public_key, transport_key_id,
                                   transport_key_not_before, transport_key_not_after, self_descriptor,
                                   sequence_number, now)
                           ON t.peer_did = s.peer_did
                         WHEN NOT MATCHED THEN INSERT
                              (peer_id, peer_did, endpoint_url, transport_public_key, transport_key_id,
                               transport_key_not_before, transport_key_not_after, self_descriptor,
                               sequence_number, first_seen_at, last_seen_at)
                              VALUES
                              (s.peer_id, s.peer_did, s.endpoint_url, s.transport_public_key, s.transport_key_id,
                               s.transport_key_not_before, s.transport_key_not_after, s.self_descriptor,
                               s.sequence_number, s.now, s.now)
                        """)
                .param("peerId", UUID.randomUUID())
                .param("did", descriptor.peerDid())
                .param("endpoint", descriptor.endpointUrl())
                .param("key", descriptor.transportPublicKey())
                .param("keyId", descriptor.transportKeyId())
                .param("notBefore", ts(descriptor.transportKeyNotBefore()))
                .param("notAfter", ts(descriptor.transportKeyNotAfter()))
                .param("descriptor", descriptor.rawEnvelope())
                .param("seq", descriptor.sequenceNumber())
                .param("now", ts(now))
                .update();
    }

    /**
     * Advances a known peer to a strictly-higher sequence, replacing its descriptor and transport key.
     *
     * @param descriptor the verified, newer descriptor
     * @param now the observation instant
     * @return 1 if advanced, 0 if the stored sequence was already at or above this one
     */
    public int updateIfNewer(ServerDescriptor descriptor, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE federation_peers
                           SET endpoint_url = :endpoint,
                               transport_public_key = :key,
                               transport_key_id = :keyId,
                               transport_key_not_before = :notBefore,
                               transport_key_not_after = :notAfter,
                               self_descriptor = :descriptor FORMAT JSON,
                               sequence_number = :seq,
                               last_seen_at = :now,
                               row_version = row_version + 1
                         WHERE peer_did = :did
                           AND sequence_number < :seq
                        """)
                .param("endpoint", descriptor.endpointUrl())
                .param("key", descriptor.transportPublicKey())
                .param("keyId", descriptor.transportKeyId())
                .param("notBefore", ts(descriptor.transportKeyNotBefore()))
                .param("notAfter", ts(descriptor.transportKeyNotAfter()))
                .param("descriptor", descriptor.rawEnvelope())
                .param("seq", descriptor.sequenceNumber())
                .param("did", descriptor.peerDid())
                .param("now", ts(now))
                .update();
    }

    /**
     * Refreshes a peer's last-observed time without changing its descriptor — a same-sequence
     * re-announcement.
     *
     * @param peerDid the peer's DID
     * @param now the observation instant
     * @return rows touched (0 if the peer is unknown)
     */
    public int touchLastSeen(String peerDid, Instant now) {
        return jdbcClient
                .sql("UPDATE federation_peers SET last_seen_at = :now WHERE peer_did = :did")
                .param("now", ts(now))
                .param("did", peerDid)
                .update();
    }

    /**
     * Records a successful contact: bumps the success counter, clears the consecutive-failure streak,
     * and marks both liveness timestamps.
     *
     * @param peerId the peer's local id
     * @param now the contact instant
     * @return rows touched
     */
    public int recordContactSuccess(UUID peerId, Instant now) {
        return jdbcClient.sql("""
                        UPDATE federation_peers
                           SET contact_successes = contact_successes + 1,
                               consecutive_failures = 0,
                               last_successful_contact_at = :now,
                               last_seen_at = :now
                         WHERE peer_id = :peerId
                        """).param("now", ts(now)).param("peerId", peerId).update();
    }

    /**
     * Records a failed contact: bumps the failure counters. {@code last_seen_at} advances (we did try
     * the peer) but {@code last_successful_contact_at} does not, so an unreachable peer never looks
     * reached.
     *
     * @param peerId the peer's local id
     * @param now the attempt instant
     * @return rows touched
     */
    public int recordContactFailure(UUID peerId, Instant now) {
        return jdbcClient.sql("""
                        UPDATE federation_peers
                           SET contact_failures = contact_failures + 1,
                               consecutive_failures = consecutive_failures + 1,
                               last_seen_at = :now
                         WHERE peer_id = :peerId
                        """).param("now", ts(now)).param("peerId", peerId).update();
    }

    /**
     * @param peerDid the peer's DID
     * @return the full record, if known
     */
    public Optional<PeerRecord> findByDid(String peerDid) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.ALL_COLUMNS + " FROM " + FederationPeerRows.TABLE + " WHERE "
                        + FederationPeerRows.PEER_DID + " = :did")
                .param("did", peerDid)
                .query(FederationPeerRows.MAPPER)
                .optional();
    }

    /** @return how many peers are in the directory */
    public long count() {
        return jdbcClient
                .sql("SELECT count(*) FROM " + FederationPeerRows.TABLE)
                .query(Long.class)
                .single();
    }

    /**
     * The peers most likely to answer, freshest contact first — the set to pull peer lists from in a
     * gossip round. Uses {@code ix_federation_peers_liveness}.
     *
     * @param limit the maximum to return
     * @return live peers, most-recently-contacted first
     */
    public List<PeerRecord> livePeers(int limit) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.ALL_COLUMNS + " FROM " + FederationPeerRows.TABLE
                        + " ORDER BY " + FederationPeerRows.LAST_SUCCESSFUL_CONTACT_AT + " DESC NULLS LAST"
                        + " LIMIT :limit")
                .param("limit", limit)
                .query(FederationPeerRows.MAPPER)
                .list();
    }

    /**
     * The peers this server has touched least recently — the set to probe for liveness, stalest first,
     * so probing cycles through the directory rather than hammering the same few.
     *
     * @param limit the maximum to return
     * @return probe candidates, stalest first
     */
    public List<PeerRecord> probeCandidates(int limit) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.ALL_COLUMNS + " FROM " + FederationPeerRows.TABLE
                        + " ORDER BY " + FederationPeerRows.LAST_SEEN_AT + " ASC"
                        + " LIMIT :limit")
                .param("limit", limit)
                .query(FederationPeerRows.MAPPER)
                .list();
    }

    /**
     * A bounded sample of stored self-descriptors, verbatim, to serve to a peer asking for this
     * server's directory. Freshest first, capped for anti-amplification.
     *
     * @param limit the maximum to return
     * @return raw self-descriptor blobs
     */
    public List<String> exchangeSample(int limit) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.SELF_DESCRIPTOR + " FROM " + FederationPeerRows.TABLE
                        + " ORDER BY " + FederationPeerRows.LAST_SUCCESSFUL_CONTACT_AT + " DESC NULLS LAST"
                        + " LIMIT :limit")
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    /**
     * The directory as an operator sees it, most-recently-contacted first.
     *
     * @param limit the maximum to return
     * @return full peer records
     */
    public List<PeerRecord> operatorView(int limit) {
        return jdbcClient
                .sql("SELECT " + FederationPeerRows.ALL_COLUMNS + " FROM " + FederationPeerRows.TABLE
                        + " ORDER BY " + FederationPeerRows.LAST_SUCCESSFUL_CONTACT_AT + " DESC NULLS LAST"
                        + " LIMIT :limit")
                .param("limit", limit)
                .query(FederationPeerRows.MAPPER)
                .list();
    }

    /**
     * Binds an {@link Instant} as a UTC {@link OffsetDateTime}, the form the schema's {@code timestamptz}
     * columns take without depending on the JVM's default zone — the same reasoning {@code Row} uses
     * when reading them back.
     */
    private static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
