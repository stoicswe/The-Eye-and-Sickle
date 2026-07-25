package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two {@link ServerSigningIdentity} implementations, each proved to behave the way its class doc
 * promises: {@link LoadedSigningIdentity} signs and can self-verify; {@link MissingSigningIdentity}
 * boots fine but refuses to sign, so an unprovisioned node can still receive items yet never mints one
 * ({@code docs/architecture/04-item-provenance.md} §5).
 */
class SigningIdentityTest {

    private static final String DID = "did:plc:server00000000000000";
    private static final String KID = DID + "#key1";

    // ------------------------------------------------------------------ loaded

    @Nested
    @DisplayName("a loaded identity")
    class Loaded {

        private final KeyPair keys = Ed25519Signatures.generateKeyPair();
        private final LoadedSigningIdentity identity =
                new LoadedSigningIdentity(DID, KID, keys.getPrivate(), keys.getPublic());

        @Test
        @DisplayName("can sign, and exposes its DID and key id")
        void reportsItsIdentity() {
            assertThat(identity.canSign()).isTrue();
            assertThat(identity.issuerDid()).isEqualTo(DID);
            assertThat(identity.issuerDidOrNull()).isEqualTo(DID);
            assertThat(identity.signingKeyId()).isEqualTo(KID);
        }

        @Test
        @DisplayName("produces an EdDSA block whose signature verifies over the same bytes")
        void signatureVerifies() {
            byte[] bytes = "canonical-payload-bytes".getBytes(StandardCharsets.UTF_8);

            SignatureBlock block = identity.sign(bytes);

            assertThat(block.alg()).isEqualTo(Ed25519Signatures.JOSE_ALG);
            assertThat(block.kid()).isEqualTo(KID);
            byte[] signature = Base64.getUrlDecoder().decode(block.sig());
            assertThat(Ed25519Signatures.verify(keys.getPublic(), bytes, signature))
                    .as("the block must verify against the identity's own public key")
                    .isTrue();
        }

        @Test
        @DisplayName("signs a real provenance payload so its own directory recognizes it")
        void signsARealPayload() {
            ProvenancePayload payload = new ProvenancePayload(
                    ProvenancePayload.CURRENT_RECORD_VERSION,
                    TestChains.ITEM_ID,
                    TestChains.ITEM_TYPE,
                    java.util.Map.of("power", 1),
                    io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType.INITIAL_MINT,
                    TestChains.HOLDER,
                    DID,
                    null,
                    0,
                    "2026-08-01T12:00:00Z",
                    "nonce-1");
            SignatureBlock block = identity.sign(ProvenanceJson.canonicalBytes(payload));

            assertThat(Ed25519Signatures.verify(
                            keys.getPublic(),
                            ProvenanceJson.canonicalBytes(payload),
                            Base64.getUrlDecoder().decode(block.sig())))
                    .isTrue();
        }

        @Test
        @DisplayName("publishes its own public key for offline self-verification")
        void exposesLocalVerificationKey() {
            assertThat(identity.localVerificationKeys()).containsOnlyKeys(KID);
            assertThat(identity.localVerificationKeys()).containsEntry(KID, keys.getPublic());
        }

        @Test
        @DisplayName("with no public key configured, it still signs but resolves nothing locally")
        void privateOnlyIdentityHasNoLocalKeys() {
            LoadedSigningIdentity privateOnly = new LoadedSigningIdentity(DID, KID, keys.getPrivate(), null);

            assertThat(privateOnly.canSign()).isTrue();
            // A peer can still resolve this via DID resolution; this server just cannot do it offline.
            assertThat(privateOnly.localVerificationKeys()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ missing

    @Nested
    @DisplayName("a missing identity")
    class Missing {

        private final MissingSigningIdentity identity = new MissingSigningIdentity();

        @Test
        @DisplayName("cannot sign and has no local keys")
        void cannotSign() {
            assertThat(identity.canSign()).isFalse();
            assertThat(identity.issuerDidOrNull())
                    .as("the non-throwing form tolerates an unconfigured node")
                    .isNull();
            assertThat(identity.localVerificationKeys()).isEmpty();
        }

        @Test
        @DisplayName("refuses to sign loudly rather than inventing a throwaway key")
        void refusesToSign() {
            byte[] bytes = "anything".getBytes(StandardCharsets.UTF_8);
            // A silently auto-generated key would orphan every item a previous key signed.
            assertThatThrownBy(() -> identity.sign(bytes))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no signing key");
        }

        @Test
        @DisplayName("throws on issuerDid() and signingKeyId(), the forms that must fail fast")
        void throwsOnIdentityAccessors() {
            assertThatThrownBy(identity::issuerDid).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(identity::signingKeyId).isInstanceOf(IllegalStateException.class);
        }
    }
}
