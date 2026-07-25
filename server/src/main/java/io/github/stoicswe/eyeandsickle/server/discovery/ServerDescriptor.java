package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A federated server's <em>self-asserted</em>, non-adversarial description of itself, after its
 * signature has been verified.
 *
 * <h2>What a descriptor is, and what it is not</h2>
 *
 * It is the answer to "how do I reach this server and seal traffic to it": the endpoint, the X25519
 * transport key ({@code docs/architecture/07-transport-security.md}), the capabilities it claims, and
 * a monotonic {@link #sequenceNumber()} that orders successive versions of the record. It is
 * <strong>not</strong> a claim about anything adversarial — not item ownership, not a duel outcome, not
 * another server's state. Those never travel as self-asserted data ({@code
 * docs/architecture/03-server-and-federation.md} §2); they travel as signed provenance and quorum
 * outcomes and converge by validity, not by whoever spoke last. See {@code
 * docs/architecture/08-discovery-and-sync.md}.
 *
 * <h2>Why last-writer-wins is safe for exactly this record</h2>
 *
 * Only the DID that owns this descriptor can sign it, and the signature proves it. So when two versions
 * disagree, taking the one with the higher sequence number is not a trust decision about an adversary
 * — it is the owner updating its own contact card. The sequence number is a <em>signed</em> counter,
 * never a wall clock: a clock is attacker-controlled and self-hosted clocks legitimately disagree,
 * whereas a counter the owner signs cannot be advanced by anyone else, and an old copy replayed to roll
 * the owner back to a retired transport key is refused because its sequence is not greater ({@code 03}
 * §2 anti-rollback; enforced again in the database by a trigger).
 *
 * <p>Instances of this type only ever exist <em>after</em> {@link ServerDescriptorVerifier} has checked
 * the signature. Holding an unverified one is not representable — the verifier is the only producer.
 *
 * @param peerDid the DID that owns and signed this descriptor
 * @param endpointUrl where to reach the server; {@code http(s)://...}. An endpoint moves when a
 *     self-hoster changes address, which is exactly why nothing is ever keyed off it — the DID is
 * @param transportPublicKey X.509-encoded X25519 public key, the form {@code
 *     X25519KeyExchange.encodePublicKey} produces and the {@code federation_peers.transport_public_key}
 *     column stores
 * @param transportKeyId the DID fragment naming the transport key, e.g. {@code did:plc:xxx#transport-1},
 *     or {@code null} if the descriptor did not name it
 * @param transportKeyNotBefore when the transport key becomes valid, or {@code null} if unbounded
 * @param transportKeyNotAfter when the transport key expires, or {@code null} if unbounded
 * @param sequenceNumber the signed monotonic version counter; a higher value supersedes a lower one
 * @param capabilities self-declared capabilities (e.g. {@code federation}, {@code validator}); advisory
 *     only, never a grant of authority
 * @param issuedAt when the owner says it produced this descriptor; advisory, and never used to order
 *     descriptors — {@link #sequenceNumber()} does that
 * @param rawEnvelope the exact bytes received, verbatim: the descriptor payload AND its signature, the
 *     one self-contained blob stored in {@code federation_peers.self_descriptor}. Kept unmodified
 *     because the signature covers specific bytes, so re-serializing it before storage is how a
 *     signature stops verifying for reasons nobody can reproduce
 */
public record ServerDescriptor(
        String peerDid,
        String endpointUrl,
        byte[] transportPublicKey,
        String transportKeyId,
        Instant transportKeyNotBefore,
        Instant transportKeyNotAfter,
        long sequenceNumber,
        List<String> capabilities,
        Instant issuedAt,
        String rawEnvelope) {

    /** The capability string a server sets when it opts into the federation directory ({@code 03} §2). */
    public static final String CAPABILITY_FEDERATION = "federation";

    /** The capability string a server sets when it is willing to act as a validator ({@code 05}). */
    public static final String CAPABILITY_VALIDATOR = "validator";

    public ServerDescriptor {
        Objects.requireNonNull(peerDid, "peerDid");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        transportPublicKey =
                Objects.requireNonNull(transportPublicKey, "transportPublicKey").clone();
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        Objects.requireNonNull(rawEnvelope, "rawEnvelope");
        if (sequenceNumber < 0) {
            // The schema forbids a negative sequence too; catching it here means a caller building a
            // descriptor by hand fails at construction rather than at the INSERT.
            throw new IllegalArgumentException("sequenceNumber must not be negative, was " + sequenceNumber);
        }
    }

    /**
     * Decodes the attested transport key.
     *
     * @return the X25519 public key to seal traffic to this server with
     * @throws SecureChannelException if the stored bytes are not a valid X25519 key — which the
     *     verifier already rejected, so this only fires on a descriptor built without going through it
     */
    public PublicKey transportKey() {
        return X25519KeyExchange.decodePublicKey(transportPublicKey);
    }

    /**
     * Whether this descriptor claims a given capability.
     *
     * @param capability the capability string, e.g. {@link #CAPABILITY_VALIDATOR}
     * @return whether it is present; advisory, never an authorization
     */
    public boolean declares(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Whether the attested transport key is within its validity window at {@code now}.
     *
     * <p>A {@code null} bound means "unbounded on that side". This is checked at dial time, not at
     * storage time: a descriptor whose key has since expired is still a legitimate directory entry
     * worth keeping (the peer will refresh it), it just cannot be dialled until it does.
     *
     * @param now the instant to evaluate against
     * @return whether the key is currently usable
     */
    public boolean transportKeyValidAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (transportKeyNotBefore != null && now.isBefore(transportKeyNotBefore)) {
            return false;
        }
        return transportKeyNotAfter == null || now.isBefore(transportKeyNotAfter);
    }

    @Override
    public byte[] transportPublicKey() {
        return transportPublicKey.clone();
    }
}
