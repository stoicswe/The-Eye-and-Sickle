package io.github.stoicswe.eyeandsickle.server.directory;

import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a home server's signing {@code kid} — a DID fragment such as {@code did:plc:home#key1} — to
 * the Ed25519 public key it names, so {@link CharacterHomeRecordVerifier} can check a record's signature.
 *
 * <h2>A narrow seam to the identity slice</h2>
 *
 * Turning a home server's DID into a public key is AT Protocol DID-document resolution ({@code
 * docs/architecture/02-identity-and-auth.md} §5), which the identity slice owns. The directory declares
 * the dependency as this interface rather than reaching into that slice, exactly as the discovery slice
 * declares {@code PeerKeyResolver} for the same job. This slice does not import the discovery resolver:
 * it names its own dependency so the two stay independently wireable, and the identity slice's real DID
 * resolver can satisfy both.
 *
 * <h2>Returning {@code null} is the contract</h2>
 *
 * An unresolvable key is one specific, reportable reason a record is refused ({@link
 * CharacterHomeFault#UNKNOWN_SIGNING_KEY}), distinct from a key that resolved and then failed to verify
 * ({@link CharacterHomeFault#INVALID_SIGNATURE}). Conflating the two would hide whether a home server is
 * unheard-of or lying. Until an identity-slice resolver is wired in, a resolver that resolves nothing
 * simply means no home binding verifies — a safe closed default, never an open one.
 */
@FunctionalInterface
public interface CharacterHomeKeyResolver {

    /**
     * @param kid a DID fragment, e.g. {@code did:plc:home#key1}
     * @return the Ed25519 public key it names, or {@code null} if this resolver cannot resolve it
     */
    PublicKey resolve(String kid);

    /**
     * A resolver backed by a fixed key set — the shape tests use, and the shape a server uses to pin a
     * small set of known home servers without live DID resolution.
     *
     * @param keysByKid keys indexed by full DID fragment
     * @return the resolver
     */
    static CharacterHomeKeyResolver ofMap(Map<String, PublicKey> keysByKid) {
        Map<String, PublicKey> snapshot = Map.copyOf(Objects.requireNonNull(keysByKid, "keysByKid"));
        return snapshot::get;
    }

    /** A resolver that resolves nothing; every home binding becomes unverifiable. The closed default. */
    static CharacterHomeKeyResolver empty() {
        return kid -> null;
    }
}
