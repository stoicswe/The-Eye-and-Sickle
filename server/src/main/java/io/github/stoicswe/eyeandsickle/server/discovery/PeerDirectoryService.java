package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The directory of known federated peers, and the one place self-asserted directory data converges.
 *
 * <h2>Convergence rule for self-asserted data: last-writer-wins on a signed sequence, never a clock</h2>
 *
 * This is the safe half of what "sync latest state" was asking for ({@code
 * docs/architecture/08-discovery-and-sync.md} §3). A server's own descriptor is non-adversarial: only
 * that server can sign it, so accepting the highest sequence it has signed is just letting it update
 * its own contact card. {@link #accept} implements exactly that — strictly-higher supersedes, equal is
 * a harmless refresh, lower is refused as stale (a possible rollback attack). The comparison is on the
 * signed {@code sequence} the owner controls; a wall clock, which an attacker controls and which
 * self-hosted servers legitimately disagree about, is never consulted.
 *
 * <p>Adversarial data — item ownership, duel outcomes — does <strong>not</strong> flow through here.
 * It converges by cryptographic validity via {@link ProvenanceConvergence}, and a conflict there is a
 * fork routed to flagging, not a row to overwrite.
 *
 * <h2>Verify, then store — always in that order</h2>
 *
 * {@link #ingest} verifies a raw descriptor before a single byte of it can become directory state. A
 * {@link ServerDescriptor} handed to {@link #accept} is therefore already trusted, because the only way
 * to obtain one is through {@link ServerDescriptorVerifier}.
 */
@Service
public class PeerDirectoryService {

    private final FederationPeerRepository repository;
    private final ServerDescriptorVerifier verifier;
    private final DiscoveryProperties properties;

    PeerDirectoryService(
            FederationPeerRepository repository, ServerDescriptorVerifier verifier, DiscoveryProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Verifies a raw self-descriptor and, if it is valid, converges it into the directory.
     *
     * @param rawEnvelope the received JSON, verbatim
     * @param now the instant to verify and record against
     * @return the verification verdict and, if accepted, what convergence did with it
     */
    public IngestResult ingest(String rawEnvelope, Instant now) {
        DescriptorVerification verification = verifier.verify(rawEnvelope, now);
        if (!verification.isAccepted()) {
            return new IngestResult(verification, null);
        }
        AcceptOutcome outcome = accept(verification.descriptor(), now);
        return new IngestResult(verification, outcome);
    }

    /**
     * Converges a verified descriptor into the directory by the sequence-number rule.
     *
     * <p>Runs in one transaction so the "is it known, and is it newer" decision and the write it implies
     * cannot interleave with a concurrent ingest of the same peer in a way that regresses the stored
     * sequence. The monotonic {@code WHERE sequence_number &lt; :seq} predicate and the database's
     * anti-rollback trigger are the two independent guarantees that it never does.
     *
     * @param descriptor a verified descriptor
     * @param now the instant to record as last-seen
     * @return what happened
     */
    @Transactional
    public AcceptOutcome accept(ServerDescriptor descriptor, Instant now) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(now, "now");

        Optional<Long> stored = repository.currentSequence(descriptor.peerDid());
        if (stored.isEmpty()) {
            // Unknown peer. Bound directory growth from gossip flooding before inserting.
            if (repository.count() >= properties.maxDirectorySize()) {
                return AcceptOutcome.IGNORED_AT_CAPACITY;
            }
            if (repository.insertNew(descriptor, now) == 1) {
                return AcceptOutcome.ACCEPTED_NEW;
            }
            // A concurrent ingest won the insert; fall through and treat it as an existing peer.
            stored = repository.currentSequence(descriptor.peerDid());
            if (stored.isEmpty()) {
                // Vanishingly rare: inserted-then-deleted between the two reads. Nothing to converge onto.
                return AcceptOutcome.IGNORED_STALE;
            }
        }

        long storedSequence = stored.get();
        if (descriptor.sequenceNumber() > storedSequence) {
            // Strictly newer: advance. If a concurrent writer advanced past us first, updateIfNewer
            // matches nothing and the newer stored descriptor rightly stands.
            return repository.updateIfNewer(descriptor, now) == 1
                    ? AcceptOutcome.ACCEPTED_UPDATED
                    : AcceptOutcome.IGNORED_STALE;
        }
        if (descriptor.sequenceNumber() == storedSequence) {
            repository.touchLastSeen(descriptor.peerDid(), now);
            return AcceptOutcome.IGNORED_DUPLICATE;
        }
        return AcceptOutcome.IGNORED_STALE;
    }

    /**
     * Records that a peer was reached (a probe or exchange succeeded).
     *
     * @param peerId the peer's local id
     * @param now the contact instant
     */
    public void recordContactSuccess(UUID peerId, Instant now) {
        repository.recordContactSuccess(peerId, now);
    }

    /**
     * Records that a contact attempt failed, feeding the back-off scheduler.
     *
     * @param peerId the peer's local id
     * @param now the attempt instant
     */
    public void recordContactFailure(UUID peerId, Instant now) {
        repository.recordContactFailure(peerId, now);
    }

    /**
     * @param peerDid the peer's DID
     * @return the stored record, if known
     */
    public Optional<PeerRecord> findByDid(String peerDid) {
        return repository.findByDid(peerDid);
    }

    /**
     * Live peers to pull peer lists from, bounded by the gossip fan-out.
     *
     * @return peers most likely to answer, freshest contact first
     */
    public List<PeerRecord> gossipTargets() {
        return repository.livePeers(properties.gossipFanout());
    }

    /**
     * Peers to probe for liveness this round, bounded by the per-exchange cap so one round's probing is
     * bounded regardless of directory size.
     *
     * @return stalest-first probe candidates
     */
    public List<PeerRecord> probeCandidates() {
        return repository.probeCandidates(properties.maxPeersPerExchange());
    }

    /**
     * The bounded set of self-descriptors to serve to a peer requesting this server's directory.
     *
     * @return verbatim self-descriptor blobs, capped for anti-amplification
     */
    public List<String> descriptorsForExchange() {
        return repository.exchangeSample(properties.maxPeersPerExchange());
    }

    /**
     * The directory for the operator view.
     *
     * @param limit the maximum to return
     * @return full peer records, most-recently-contacted first
     */
    public List<PeerRecord> operatorView(int limit) {
        return repository.operatorView(Math.min(limit, properties.maxDirectorySize()));
    }

    /**
     * The result of {@link #ingest}: the verification verdict, plus what convergence did if it was
     * accepted.
     *
     * @param verification the verification verdict; {@link DescriptorVerification#isAccepted()} says
     *     whether the descriptor was valid
     * @param outcome what {@link #accept} did, or {@code null} if the descriptor was rejected
     */
    public record IngestResult(DescriptorVerification verification, AcceptOutcome outcome) {

        /** @return whether the descriptor verified and was converged into the directory */
        public boolean stored() {
            return outcome == AcceptOutcome.ACCEPTED_NEW || outcome == AcceptOutcome.ACCEPTED_UPDATED;
        }
    }
}
