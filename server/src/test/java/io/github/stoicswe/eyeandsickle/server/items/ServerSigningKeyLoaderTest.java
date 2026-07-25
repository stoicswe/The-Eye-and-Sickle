package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ServerSigningKeyLoader} — turning configuration into a signing identity, and the one place
 * that decides how a missing or broken key is treated ({@code
 * docs/architecture/04-item-provenance.md} §5; {@link ServerSigningProperties}).
 *
 * <p>The two failure modes are handled differently on purpose and both are tested: a key not
 * configured at all boots into a {@link MissingSigningIdentity} that refuses at first mint, while a key
 * configured-but-unreadable fails loudly at load time — a server meant to sign must never silently do
 * nothing. The loader never generates a key; that is enforced by omission, so the closest test is that
 * an absent configuration yields the non-signing identity rather than a fresh one.
 */
class ServerSigningKeyLoaderTest {

    private static final String DID = "did:plc:server00000000000000";

    @TempDir
    Path dir;

    private final KeyPair keys = Ed25519Signatures.generateKeyPair();

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static byte[] pem(String label, byte[] der) {
        String body =
                Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return ("-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ loading raw key files

    @Nested
    @DisplayName("loading a key file")
    class LoadingKeyFiles {

        @Test
        @DisplayName("reads a PKCS#8 private key as raw DER")
        void privateKeyFromDer() throws IOException {
            Path path = write("key.der", keys.getPrivate().getEncoded());

            // Compare the PKCS#8 encoding rather than the key object, to stay independent of a provider's
            // Key.equals implementation.
            assertThat(ServerSigningKeyLoader.loadPrivateKey(path.toString()).getEncoded())
                    .isEqualTo(keys.getPrivate().getEncoded());
        }

        @Test
        @DisplayName("reads a PKCS#8 private key as PEM")
        void privateKeyFromPem() throws IOException {
            Path path = write("key.pem", pem("PRIVATE KEY", keys.getPrivate().getEncoded()));

            // Whatever `openssl genpkey -algorithm ed25519` produced, with no conversion step.
            assertThat(ServerSigningKeyLoader.loadPrivateKey(path.toString()).getEncoded())
                    .isEqualTo(keys.getPrivate().getEncoded());
        }

        @Test
        @DisplayName("reads an X.509 public key as DER and as PEM")
        void publicKeyFromDerAndPem() throws IOException {
            Path der = write("pub.der", keys.getPublic().getEncoded());
            Path pem = write("pub.pem", pem("PUBLIC KEY", keys.getPublic().getEncoded()));

            assertThat(ServerSigningKeyLoader.loadPublicKey(der.toString()).getEncoded())
                    .isEqualTo(keys.getPublic().getEncoded());
            assertThat(ServerSigningKeyLoader.loadPublicKey(pem.toString()).getEncoded())
                    .isEqualTo(keys.getPublic().getEncoded());
        }

        @Test
        @DisplayName("a configured key that is not present is a misconfiguration, not a reason to generate one")
        void missingFileThrows() {
            assertThatThrownBy(() -> ServerSigningKeyLoader.loadPrivateKey(
                            dir.resolve("absent.pem").toString()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot read");
        }

        @Test
        @DisplayName("a file that is not a valid Ed25519 key is refused")
        void garbageKeyThrows() throws IOException {
            Path path = write("garbage.der", "this is not a key".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> ServerSigningKeyLoader.loadPrivateKey(path.toString()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("PEM armor around an undecodable body is refused")
        void pemWithBadBodyThrows() throws IOException {
            Path path = write(
                    "bad.pem",
                    "-----BEGIN PRIVATE KEY-----\n!!! not base64 !!!\n-----END PRIVATE KEY-----\n"
                            .getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> ServerSigningKeyLoader.loadPrivateKey(path.toString()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("undecodable body");
        }
    }

    // ------------------------------------------------------------------ load(properties)

    @Nested
    @DisplayName("load(properties)")
    class LoadProperties {

        @Test
        @DisplayName("no private key path yields a non-signing identity that still boots")
        void unconfiguredYieldsMissing() {
            ServerSigningIdentity identity =
                    ServerSigningKeyLoader.load(new ServerSigningProperties(null, null, null, null));

            assertThat(identity).isInstanceOf(MissingSigningIdentity.class);
            assertThat(identity.canSign()).isFalse();
        }

        @Test
        @DisplayName("a private key with no DID is refused — a record's issuerDid must be this server's DID")
        void privateKeyWithoutDidThrows() throws IOException {
            Path priv = write("key.pem", pem("PRIVATE KEY", keys.getPrivate().getEncoded()));

            assertThatThrownBy(() ->
                            ServerSigningKeyLoader.load(new ServerSigningProperties(null, null, priv.toString(), null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did");
        }

        @Test
        @DisplayName("a full configuration loads a signing identity that can self-verify")
        void fullConfigLoadsSigner() throws IOException {
            Path priv = write("key.pem", pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
            Path pub = write("pub.pem", pem("PUBLIC KEY", keys.getPublic().getEncoded()));

            ServerSigningIdentity identity = ServerSigningKeyLoader.load(
                    new ServerSigningProperties(DID, null, priv.toString(), pub.toString()));

            assertThat(identity.canSign()).isTrue();
            assertThat(identity.issuerDid()).isEqualTo(DID);
            assertThat(identity.signingKeyId()).isEqualTo(DID + "#key1");
            assertThat(identity.localVerificationKeys()).containsKey(DID + "#key1");

            byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
            var block = identity.sign(bytes);
            assertThat(Ed25519Signatures.verify(
                            keys.getPublic(), bytes, Base64.getUrlDecoder().decode(block.sig())))
                    .isTrue();
        }

        @Test
        @DisplayName("a private key with no public key still signs, but resolves nothing offline")
        void privateOnlyConfigLoads() throws IOException {
            Path priv = write("key.der", keys.getPrivate().getEncoded());

            ServerSigningIdentity identity =
                    ServerSigningKeyLoader.load(new ServerSigningProperties(DID, null, priv.toString(), null));

            assertThat(identity.canSign()).isTrue();
            assertThat(identity.localVerificationKeys()).isEmpty();
        }
    }
}
