package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.security.KeyPair;
import java.security.PublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link DidSigningKeyDirectory} — resolves a signature's {@code kid} by checking this server's own
 * keys first, then delegating to DID resolution. The two-tier order lets a home-minted item that was
 * traded away and returned re-verify offline ({@code docs/architecture/04-item-provenance.md} §6.2),
 * and an unresolvable key returns {@code null} rather than throwing, which is one specific reportable
 * reason a chain is not recognized ({@code UNKNOWN_SIGNING_KEY}).
 */
class DidSigningKeyDirectoryTest {

    private static final String DID = "did:plc:server00000000000000";
    private static final String KID = DID + "#key1";
    private static final String PEER_KID = "did:plc:peerserver0000000000#key1";

    private final KeyPair local = Ed25519Signatures.generateKeyPair();
    private final KeyPair peer = Ed25519Signatures.generateKeyPair();

    @Test
    @DisplayName("resolves this server's own key from its local set, with no round-trip")
    void resolvesLocalKey() {
        DidSigningKeyDirectory directory = new DidSigningKeyDirectory(
                new LoadedSigningIdentity(DID, KID, local.getPrivate(), local.getPublic()),
                DidPublicKeyResolver.unresolved());

        assertThat(directory.publicKeyFor(KID)).isEqualTo(local.getPublic());
    }

    @Test
    @DisplayName("delegates an unknown local key to the resolver")
    void delegatesToResolver() {
        DidPublicKeyResolver resolver = kid -> PEER_KID.equals(kid) ? peer.getPublic() : null;
        DidSigningKeyDirectory directory = new DidSigningKeyDirectory(new MissingSigningIdentity(), resolver);

        assertThat(directory.publicKeyFor(PEER_KID)).isEqualTo(peer.getPublic());
    }

    @Test
    @DisplayName("the local set wins over the resolver for the same kid")
    void localWinsOverResolver() {
        // If the resolver could shadow a local key, a peer could substitute its own key for this
        // server's and have this server verify its own records against the wrong one.
        DidPublicKeyResolver shadowing = kid -> peer.getPublic();
        DidSigningKeyDirectory directory = new DidSigningKeyDirectory(
                new LoadedSigningIdentity(DID, KID, local.getPrivate(), local.getPublic()), shadowing);

        assertThat(directory.publicKeyFor(KID)).isEqualTo(local.getPublic());
    }

    @Nested
    @DisplayName("unresolvable keys")
    class Unresolvable {

        private final DidSigningKeyDirectory directory =
                new DidSigningKeyDirectory(new MissingSigningIdentity(), DidPublicKeyResolver.unresolved());

        @Test
        @DisplayName("an unknown kid resolves to null, not an exception")
        void unknownKidIsNull() {
            assertThat(directory.publicKeyFor(PEER_KID)).isNull();
        }

        @Test
        @DisplayName("a null kid resolves to null")
        void nullKidIsNull() {
            assertThat(directory.publicKeyFor(null)).isNull();
        }
    }

    @Test
    @DisplayName("the placeholder resolver resolves nothing")
    void placeholderResolverResolvesNothing() {
        PublicKey resolved = DidPublicKeyResolver.unresolved().resolve(KID);
        // The conservative default until the identity slice supplies a real resolver: an unresolvable
        // peer key makes its records simply not recognized, never a security hole.
        assertThat(resolved).isNull();
    }
}
