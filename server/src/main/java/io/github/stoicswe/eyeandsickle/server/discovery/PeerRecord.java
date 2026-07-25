package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code federation_peers} — this server's local, low-trust knowledge of another server.
 *
 * <p>The table is "the federation directory as this server knows it" ({@code
 * docs/architecture/03-server-and-federation.md} §2): a directory entry, never an authority. Holding a
 * {@code PeerRecord} says only "this server exists and claims to federate" — it never adjudicates an
 * item, a balance, or a duel. Those decisions come from provenance ({@code 04}) and the quorum
 * ({@code 05}).
 *
 * <p>Timestamps carry two distinct liveness notions on purpose, mirroring the column comments:
 * {@link #lastSeenAt()} is the last time this server observed <em>any</em> activity for the peer (a
 * third-party announcement in a gossip response, or a probe attempt), while
 * {@link #lastSuccessfulContactAt()} is the last time this server actually reached it. A peer that has
 * been announced but never answered has a {@code lastSeenAt} and a null {@code lastSuccessfulContactAt}
 * — collapsing the two would let a server that has never once responded look healthy.
 *
 * @param peerId local surrogate key
 * @param peerDid the peer's DID; the only stable identifier — never key off the endpoint
 * @param endpointUrl where to reach it
 * @param transportPublicKey X.509-encoded X25519 transport key
 * @param transportKeyId the DID fragment naming the transport key, or {@code null}
 * @param transportKeyNotBefore transport-key validity start, or {@code null}
 * @param transportKeyNotAfter transport-key validity end, or {@code null}
 * @param selfDescriptor the peer's signed self-descriptor, verbatim
 * @param sequenceNumber the monotonic version of the stored descriptor
 * @param firstSeenAt when this server first learned of the peer
 * @param lastSeenAt last observed activity (announcement or probe attempt)
 * @param lastSuccessfulContactAt last successful contact, or {@code null} if never reached
 * @param contactSuccesses running count of successful contacts
 * @param contactFailures running count of failed contacts
 * @param consecutiveFailures failures since the last success; drives back-off
 * @param rowVersion optimistic-concurrency version
 */
public record PeerRecord(
        UUID peerId,
        String peerDid,
        String endpointUrl,
        byte[] transportPublicKey,
        String transportKeyId,
        Instant transportKeyNotBefore,
        Instant transportKeyNotAfter,
        String selfDescriptor,
        long sequenceNumber,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant lastSuccessfulContactAt,
        long contactSuccesses,
        long contactFailures,
        int consecutiveFailures,
        long rowVersion) {

    public PeerRecord {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(peerDid, "peerDid");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        transportPublicKey =
                Objects.requireNonNull(transportPublicKey, "transportPublicKey").clone();
        Objects.requireNonNull(selfDescriptor, "selfDescriptor");
    }

    /**
     * The success ratio, the raw availability measurement {@link PeerUptimeSource} exposes for the
     * quorum's sampling weight ({@code docs/architecture/05-validator-quorum.md} §2.2).
     *
     * @return successes / (successes + failures), or the {@code whenNoData} value if neither has
     *     happened yet — a peer with no contact history has no measured uptime, and inventing one as
     *     0 or 1 would either bury a new peer or flatter it
     */
    public double successRatio(double whenNoData) {
        long attempts = contactSuccesses + contactFailures;
        return attempts == 0 ? whenNoData : (double) contactSuccesses / attempts;
    }

    /**
     * Which capabilities this peer's stored descriptor declares, without re-verifying the signature.
     *
     * <p>Safe because the descriptor was verified before it was stored, and stored verbatim; this only
     * reads the already-trusted blob. Returns an empty list if the descriptor cannot be read, never an
     * exception, since a display path must not fail on one odd row.
     *
     * @return declared capability strings, advisory only
     */
    public List<String> declaredCapabilities() {
        try {
            Object caps = io.github.stoicswe.eyeandsickle.server.persistence.Jsonb.readObject(selfDescriptor)
                    .get(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR);
            if (caps instanceof java.util.Map<?, ?> descriptor
                    && descriptor.get(ServerDescriptorCodec.CAPABILITIES) instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }
        } catch (RuntimeException ignored) {
            // A malformed stored descriptor is not worth failing a directory listing over.
        }
        return List.of();
    }

    @Override
    public byte[] transportPublicKey() {
        return transportPublicKey.clone();
    }
}
