package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a signature block's {@code kid} to the Ed25519 public key it names.
 *
 * <p>This exists so the verifier itself does no I/O. {@code
 * docs/architecture/04-item-provenance.md} §6.2 requires the chain walk to run on a player's client,
 * offline, without trusting the server's UI to have checked anything — which it cannot do if
 * verification reaches out to resolve a DID mid-walk. The caller decides where keys come from: a
 * resolved AT Protocol DID document, the federation directory, or a locally cached key set carried
 * with the item's history.
 *
 * <p>Returning {@code null} for an unknown {@code kid} is the contract, not an oversight. An
 * unresolvable key is one specific, reportable reason a chain is not recognized, and it is a
 * different thing from a key that resolved and then failed to verify — the player-facing history
 * view (§6.1) should be able to say which.
 */
@FunctionalInterface
public interface SigningKeyDirectory {

    /**
     * @param kid a DID fragment, e.g. {@code did:plc:yyyyyyyy#key1}
     * @return the public key, or {@code null} if this directory cannot resolve it
     */
    PublicKey publicKeyFor(String kid);

    /**
     * A directory backed by a fixed key set — the shape a client uses when it verifies an item's
     * history from a cached bundle with no network available.
     *
     * @param keysByKid keys indexed by full DID fragment
     * @return the directory
     */
    static SigningKeyDirectory ofMap(Map<String, PublicKey> keysByKid) {
        Map<String, PublicKey> snapshot = Map.copyOf(Objects.requireNonNull(keysByKid, "keysByKid"));
        return snapshot::get;
    }

    /** A directory that resolves nothing; every signature becomes unverifiable. */
    static SigningKeyDirectory empty() {
        return kid -> null;
    }
}
