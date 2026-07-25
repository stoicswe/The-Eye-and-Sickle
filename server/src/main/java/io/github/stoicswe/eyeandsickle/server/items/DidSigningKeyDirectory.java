package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;

/**
 * The server-side {@link SigningKeyDirectory}: resolves a signature's {@code kid} to a public key,
 * checking this server's own keys first and then delegating to DID resolution.
 *
 * <p>The verifier ({@code
 * io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceChainVerifier}) does no I/O and asks a
 * directory for every key. This implementation is that directory. It looks in two places, in order:
 *
 * <ol>
 *   <li>the server's own {@linkplain ServerSigningIdentity#localVerificationKeys() local keys}, so a
 *       home-minted item that has been traded away and returned re-verifies offline, with no dependence
 *       on this server's own DID being resolvable from outside;
 *   <li>the {@link DidPublicKeyResolver} seam, which turns a peer's {@code kid} into a key by resolving
 *       its DID.
 * </ol>
 *
 * <p>Returning {@code null} when neither resolves is deliberate and load-bearing: it is one specific,
 * reportable reason a chain is not recognized ({@code UNKNOWN_SIGNING_KEY}), distinct from a key that
 * resolved and then failed to verify. The verifier turns each into a different fault the player-facing
 * history view can explain.
 */
public final class DidSigningKeyDirectory implements SigningKeyDirectory {

    private final Map<String, PublicKey> localKeys;
    private final DidPublicKeyResolver resolver;

    /**
     * @param identity this server's identity, whose local keys are consulted first
     * @param resolver DID resolution for peer keys
     */
    public DidSigningKeyDirectory(ServerSigningIdentity identity, DidPublicKeyResolver resolver) {
        Objects.requireNonNull(identity, "identity");
        this.localKeys = Map.copyOf(identity.localVerificationKeys());
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public PublicKey publicKeyFor(String kid) {
        if (kid == null) {
            return null;
        }
        PublicKey local = localKeys.get(kid);
        if (local != null) {
            return local;
        }
        return resolver.resolve(kid);
    }
}
