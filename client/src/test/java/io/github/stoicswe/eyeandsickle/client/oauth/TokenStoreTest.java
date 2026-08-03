package io.github.stoicswe.eyeandsickle.client.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The encrypted-file fallback.
 *
 * <p>The keychain store is not unit-tested here on purpose: it spawns the platform's own credential
 * manager, so a test would either prompt a real user, mutate a developer's real keychain, or assert
 * against a stub that proves nothing about the platform call. Its correctness rests on the canary
 * probe in {@code KeychainTokenStore.available()}, which is the same check that runs in production.
 */
class TokenStoreTest {

    private static final TokenStore.Credentials CREDENTIALS = new TokenStore.Credentials(
            "did:plc:abc", "refresh-token-value", "cHJpdmF0ZQ==", "cHVibGlj", "https://as.example");

    @Nested
    @DisplayName("the encrypted file")
    class EncryptedFile {

        @Test
        @DisplayName("round-trips")
        void roundTrip(@TempDir Path dir) {
            TokenStore store = new EncryptedFileTokenStore(dir);
            store.save(CREDENTIALS);

            assertThat(store.load()).isEqualTo(CREDENTIALS);
        }

        @Test
        @DisplayName("the refresh token never appears in PLAINTEXT on disk")
        void nothingGreppable(@TempDir Path dir) throws IOException {
            new EncryptedFileTokenStore(dir).save(CREDENTIALS);

            // The whole point of the fallback: a synced backup or a copied profile directory holds
            // ciphertext, not a live credential.
            try (var files = Files.walk(dir)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                                .as("%s", file.getFileName())
                                .doesNotContain("refresh-token-value");
                    } catch (IOException failed) {
                        throw new AssertionError(failed);
                    }
                });
            }
        }

        @Test
        @DisplayName("a NEW IV on every write — a repeated nonce under one GCM key is catastrophic")
        void freshIvEachTime(@TempDir Path dir) throws IOException {
            // Reusing a GCM nonce leaks the XOR of the plaintexts and the authentication subkey. If
            // this ever regressed, two saves of the same credentials would produce identical files.
            TokenStore store = new EncryptedFileTokenStore(dir);
            store.save(CREDENTIALS);
            String first = Files.readString(dir.resolve("session.enc"));
            store.save(CREDENTIALS);
            String second = Files.readString(dir.resolve("session.enc"));

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("a TAMPERED file is refused, not silently half-read")
        void tamperingIsDetected(@TempDir Path dir) throws IOException {
            // GCM is authenticated, so this fails the tag rather than yielding a mangled token that
            // would produce a confusing failure much later.
            TokenStore store = new EncryptedFileTokenStore(dir);
            store.save(CREDENTIALS);

            byte[] raw = Base64.getDecoder()
                    .decode(Files.readString(dir.resolve("session.enc")).trim());
            raw[raw.length - 1] ^= 0x01;
            Files.writeString(dir.resolve("session.enc"), Base64.getEncoder().encodeToString(raw));

            assertThatThrownBy(store::load)
                    .isInstanceOf(OauthException.class)
                    .extracting(e -> ((OauthException) e).kind())
                    .isEqualTo(OauthException.Kind.STORAGE);
        }

        @Test
        @DisplayName("files are owner-only where POSIX permissions exist")
        void ownerOnly(@TempDir Path dir) throws IOException {
            new EncryptedFileTokenStore(dir).save(CREDENTIALS);

            for (String name : new String[] {"session.enc", "session.key"}) {
                var permissions = Files.getPosixFilePermissions(dir.resolve(name));
                assertThat(PosixFilePermissions.toString(permissions))
                        .as("%s", name)
                        .isEqualTo("rw-------");
            }
        }

        @Test
        @DisplayName("nothing stored means null, not an exception")
        void emptyStore(@TempDir Path dir) {
            assertThat(new EncryptedFileTokenStore(dir).load()).isNull();
        }

        @Test
        @DisplayName("clear removes both the data and the key")
        void clearRemovesEverything(@TempDir Path dir) {
            TokenStore store = new EncryptedFileTokenStore(dir);
            store.save(CREDENTIALS);
            store.clear();

            assertThat(store.load()).isNull();
            assertThat(dir.resolve("session.key")).doesNotExist();
        }

        @Test
        @DisplayName("reports itself as NOT platform-secured, so the UI can say so")
        void honestAboutItself(@TempDir Path dir) {
            // The key sits beside the data. Against a local attacker this is obfuscation, and a
            // player who is told otherwise cannot account for it.
            assertThat(new EncryptedFileTokenStore(dir).isPlatformSecured()).isFalse();
        }
    }

    @Nested
    @DisplayName("the credential codec")
    class Codec {

        @Test
        @DisplayName("round-trips, including values containing the separator")
        void separatorIsSafe() {
            // Two of these come from an authorization server the player named, so a token containing
            // '|' is not hypothetical. Base64 per field is what stops it forging a field boundary.
            TokenStore.Credentials awkward =
                    new TokenStore.Credentials("did:plc:a|b", "rt|with|pipes", "k", "p", "https://as.example");

            assertThat(CredentialCodec.decode(CredentialCodec.encode(awkward))).isEqualTo(awkward);
        }

        @Test
        @DisplayName("an unrecognised version is REFUSED rather than half-parsed")
        void versionIsChecked() {
            // A partially-understood credential set produces a session that authenticates and then
            // fails on first refresh, which reads as the server logging the player out at random.
            assertThatThrownBy(() -> CredentialCodec.decode("v99|a|b|c|d|e"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("unrecognised");
        }

        @Test
        @DisplayName("a truncated line is refused")
        void truncated() {
            assertThatThrownBy(() -> CredentialCodec.decode("v1|only|three")).isInstanceOf(OauthException.class);
        }
    }
}
