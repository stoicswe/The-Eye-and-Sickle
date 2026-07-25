package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A hand-written, in-memory {@link FederationPeerRepository} for the Docker-free unit tests.
 *
 * <p>It models the sequence-convergence rule faithfully — {@link #insertNew} refuses a duplicate DID,
 * {@link #updateIfNewer} advances only on a strictly-higher sequence — so a service test exercises the
 * same decision the real SQL makes, without a container. Contact counters and the two liveness
 * timestamps mirror the repository's, so {@link PeerRecord#successRatio(double)} and the uptime source
 * can be driven directly. The {@code lastXxxLimit} fields capture the bound each read was called with,
 * so a test can prove the service passes its configured caps through.
 *
 * <p>{@code super(null)} is deliberate: the base constructor merely stores the {@code JdbcClient}, and
 * every method that would touch it is overridden here, so no query is ever issued.
 */
class FakeFederationPeerRepository extends FederationPeerRepository {

    /** Mutable in-memory peer state. */
    static final class Stored {
        final UUID peerId = UUID.randomUUID();
        ServerDescriptor descriptor;
        long sequence;
        Instant firstSeen;
        Instant lastSeen;
        Instant lastSuccessfulContact;
        long successes;
        long failures;
        int consecutiveFailures;
        long rowVersion;
    }

    final Map<String, Stored> byDid = new LinkedHashMap<>();

    Integer lastLivePeersLimit;
    Integer lastProbeLimit;
    Integer lastExchangeLimit;
    Integer lastOperatorLimit;

    /** Runs at the top of {@link #insertNew}, letting a test simulate a concurrent inserter winning the race. */
    Runnable onInsertAttempt = () -> {};

    FakeFederationPeerRepository() {
        super(null);
    }

    /** Seeds a peer with a chosen contact history, for uptime/liveness tests. */
    Stored seed(String peerDid, long successes, long failures, int consecutiveFailures, Instant lastSuccess) {
        Stored stored = new Stored();
        stored.descriptor = descriptorFor(peerDid, 1);
        stored.sequence = 1;
        stored.firstSeen = Instant.EPOCH;
        stored.lastSeen = Instant.EPOCH;
        stored.successes = successes;
        stored.failures = failures;
        stored.consecutiveFailures = consecutiveFailures;
        stored.lastSuccessfulContact = lastSuccess;
        byDid.put(peerDid, stored);
        return stored;
    }

    private static ServerDescriptor descriptorFor(String peerDid, long sequence) {
        return new ServerDescriptor(
                peerDid,
                "https://" + peerDid.replace(':', '-') + ".example.test",
                new byte[44],
                null,
                null,
                null,
                sequence,
                List.of(),
                null,
                "{\"descriptor\":{}}");
    }

    @Override
    public Optional<Long> currentSequence(String peerDid) {
        Stored stored = byDid.get(peerDid);
        return stored == null ? Optional.empty() : Optional.of(stored.sequence);
    }

    @Override
    public long count() {
        return byDid.size();
    }

    @Override
    public int insertNew(ServerDescriptor descriptor, Instant now) {
        onInsertAttempt.run();
        if (byDid.containsKey(descriptor.peerDid())) {
            return 0;
        }
        Stored stored = new Stored();
        stored.descriptor = descriptor;
        stored.sequence = descriptor.sequenceNumber();
        stored.firstSeen = now;
        stored.lastSeen = now;
        byDid.put(descriptor.peerDid(), stored);
        return 1;
    }

    @Override
    public int updateIfNewer(ServerDescriptor descriptor, Instant now) {
        Stored stored = byDid.get(descriptor.peerDid());
        if (stored == null || stored.sequence >= descriptor.sequenceNumber()) {
            return 0;
        }
        stored.descriptor = descriptor;
        stored.sequence = descriptor.sequenceNumber();
        stored.lastSeen = now;
        stored.rowVersion++;
        return 1;
    }

    @Override
    public int touchLastSeen(String peerDid, Instant now) {
        Stored stored = byDid.get(peerDid);
        if (stored == null) {
            return 0;
        }
        stored.lastSeen = now;
        return 1;
    }

    @Override
    public int recordContactSuccess(UUID peerId, Instant now) {
        Stored stored = findById(peerId);
        if (stored == null) {
            return 0;
        }
        stored.successes++;
        stored.consecutiveFailures = 0;
        stored.lastSuccessfulContact = now;
        stored.lastSeen = now;
        return 1;
    }

    @Override
    public int recordContactFailure(UUID peerId, Instant now) {
        Stored stored = findById(peerId);
        if (stored == null) {
            return 0;
        }
        stored.failures++;
        stored.consecutiveFailures++;
        stored.lastSeen = now;
        return 1;
    }

    @Override
    public Optional<PeerRecord> findByDid(String peerDid) {
        Stored stored = byDid.get(peerDid);
        return stored == null ? Optional.empty() : Optional.of(toRecord(peerDid, stored));
    }

    @Override
    public List<PeerRecord> livePeers(int limit) {
        lastLivePeersLimit = limit;
        return records(limit);
    }

    @Override
    public List<PeerRecord> probeCandidates(int limit) {
        lastProbeLimit = limit;
        return records(limit);
    }

    @Override
    public List<String> exchangeSample(int limit) {
        lastExchangeLimit = limit;
        List<String> out = new ArrayList<>();
        for (Stored stored : byDid.values()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(stored.descriptor.rawEnvelope());
        }
        return out;
    }

    @Override
    public List<PeerRecord> operatorView(int limit) {
        lastOperatorLimit = limit;
        return records(limit);
    }

    private List<PeerRecord> records(int limit) {
        List<PeerRecord> out = new ArrayList<>();
        for (Map.Entry<String, Stored> entry : byDid.entrySet()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(toRecord(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private Stored findById(UUID peerId) {
        for (Stored stored : byDid.values()) {
            if (stored.peerId.equals(peerId)) {
                return stored;
            }
        }
        return null;
    }

    private static PeerRecord toRecord(String peerDid, Stored stored) {
        return new PeerRecord(
                stored.peerId,
                peerDid,
                stored.descriptor.endpointUrl(),
                stored.descriptor.transportPublicKey(),
                stored.descriptor.transportKeyId(),
                stored.descriptor.transportKeyNotBefore(),
                stored.descriptor.transportKeyNotAfter(),
                stored.descriptor.rawEnvelope(),
                stored.sequence,
                stored.firstSeen,
                stored.lastSeen,
                stored.lastSuccessfulContact,
                stored.successes,
                stored.failures,
                stored.consecutiveFailures,
                stored.rowVersion);
    }
}
