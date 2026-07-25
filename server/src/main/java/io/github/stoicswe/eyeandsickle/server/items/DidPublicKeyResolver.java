package io.github.stoicswe.eyeandsickle.server.items;

import java.security.PublicKey;

/**
 * Resolves a signature's {@code kid} to the Ed25519 public key it names, by resolving the DID it
 * belongs to.
 *
 * <h2>Why this is a seam owned here, not the real resolver</h2>
 *
 * A provenance {@code kid} is a DID fragment ({@code did:plc:xxxx#key1}), and turning that into a
 * public key is DID resolution — an AT Protocol concern ({@code docs/architecture/02-identity-and-auth.md})
 * that is a different slice of this server. This slice needs the capability but does not own it, so it
 * declares the narrow interface it depends on here and lets identity supply the implementation. Until
 * it does, {@link #unresolved()} is wired in: it resolves nothing, which makes any record signed by a
 * peer this server cannot yet resolve simply <em>not recognized</em> — the correct, conservative
 * default, not a security hole.
 *
 * <p>Returning {@code null} for an unknown {@code kid} is the contract, matching {@link
 * io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory}: an unresolvable key is one
 * specific, reportable reason a chain is not recognized.
 */
@FunctionalInterface
public interface DidPublicKeyResolver {

    /**
     * @param kid a DID fragment, e.g. {@code did:plc:yyyyyyyy#key1}
     * @return the public key, or {@code null} if this resolver cannot resolve it
     */
    PublicKey resolve(String kid);

    /** A resolver that resolves nothing — the placeholder until the identity slice supplies a real one. */
    static DidPublicKeyResolver unresolved() {
        return kid -> null;
    }
}
