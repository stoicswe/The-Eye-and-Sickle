package io.github.stoicswe.eyeandsickle.server.discovery;

import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a signing {@code kid} — a DID fragment such as {@code did:plc:xxx#key1} — to the Ed25519
 * public key it names, so {@link ServerDescriptorVerifier} can check a descriptor's signature.
 *
 * <h2>A seam to the identity slice</h2>
 *
 * Turning a DID into a public key is AT Protocol DID-document resolution ({@code
 * docs/architecture/02-identity-and-auth.md}), which the identity slice owns. Discovery declares the
 * dependency as this interface rather than reaching into that slice, and the identity slice provides
 * the implementation (a DID resolver, possibly cached). It is intentionally the same shape as the
 * protocol's {@code SigningKeyDirectory}, which resolves provenance signing keys the same way — one
 * mental model for "kid to key" across the codebase.
 *
 * <h2>Returning {@code null} is the contract</h2>
 *
 * An unresolvable key is one specific, reportable reason a descriptor is refused
 * ({@link DescriptorFault#UNKNOWN_SIGNING_KEY}), and it is a different outcome from a key that resolved
 * and then failed to verify ({@link DescriptorFault#INVALID_SIGNATURE}). Conflating the two would hide
 * whether a peer is unknown or lying. Until an identity-slice resolver is wired in, a resolver that
 * resolves nothing simply means no peer's descriptor verifies — a safe closed default, never an open
 * one.
 */
@FunctionalInterface
public interface PeerKeyResolver {

    /**
     * @param kid a DID fragment, e.g. {@code did:plc:xxx#key1}
     * @return the Ed25519 public key it names, or {@code null} if this resolver cannot resolve it
     */
    PublicKey resolve(String kid);

    /**
     * A resolver backed by a fixed key set — the shape tests use, and the shape a server uses to pin a
     * small set of known peers without live DID resolution.
     *
     * @param keysByKid keys indexed by full DID fragment
     * @return the resolver
     */
    static PeerKeyResolver ofMap(Map<String, PublicKey> keysByKid) {
        Map<String, PublicKey> snapshot = Map.copyOf(Objects.requireNonNull(keysByKid, "keysByKid"));
        return snapshot::get;
    }

    /** A resolver that resolves nothing; every descriptor becomes unverifiable. The closed default. */
    static PeerKeyResolver empty() {
        return kid -> null;
    }
}
