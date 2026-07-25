package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import java.security.PublicKey;
import java.util.Map;

/**
 * The signing identity of a server that has no signing key configured.
 *
 * <p>Its whole job is to fail correctly. A server with no key must not mint — but it also must not
 * quietly generate a throwaway key to get past startup, because a key that changes between restarts
 * orphans every item the previous key signed ({@link ServerSigningProperties}). So this identity boots
 * fine and only refuses at the moment someone actually asks it to sign, with a message that says what
 * to configure.
 *
 * <p>A server in this state can still <em>verify and receive</em> items minted elsewhere: verification
 * resolves peer keys through DID resolution, none of which needs a local signing key. It simply cannot
 * be an issuer of its own.
 */
final class MissingSigningIdentity implements ServerSigningIdentity {

    private static final String MESSAGE =
            "This server has no signing key configured, so it cannot issue provenance records. "
                    + "Set eyeandsickle.items.signing.did and eyeandsickle.items.signing.private-key-path. "
                    + "A key is never auto-generated: a fresh key would orphan every item a previous key signed "
                    + "(docs/architecture/04-item-provenance.md §5).";

    @Override
    public String issuerDid() {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public String issuerDidOrNull() {
        return null;
    }

    @Override
    public String signingKeyId() {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public SignatureBlock sign(byte[] canonicalPayloadBytes) {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public boolean canSign() {
        return false;
    }

    @Override
    public Map<String, PublicKey> localVerificationKeys() {
        return Map.of();
    }
}
