package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Turns {@link ServerSigningProperties} into a {@link ServerSigningIdentity}, and is the one place that
 * decides how a missing or broken key is treated.
 *
 * <h2>The two failure modes, treated differently on purpose</h2>
 *
 * <ul>
 *   <li><strong>Not configured at all</strong> — no private-key path — yields a {@link
 *       MissingSigningIdentity}. The server boots; the first mint fails loudly. This keeps a
 *       client-only or receive-only deployment from needing a key it will never use.
 *   <li><strong>Configured but unreadable</strong> — a path that does not resolve, a file that is not
 *       a valid PKCS#8 key, a DID that was left unset — throws here, at startup. A server that is
 *       <em>meant</em> to sign but cannot must not reach a state where it silently does nothing (or,
 *       worse, mints against a key other servers never see); surfacing it before any record is signed
 *       is the whole point.
 * </ul>
 *
 * The one thing this loader will never do is generate a key. That is stated in {@link
 * ServerSigningProperties} and enforced by omission: there is no {@code KeyPairGenerator} call on any
 * path through here.
 *
 * <h2>Key formats</h2>
 *
 * The private key is PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}); the optional public key is X.509
 * {@code SubjectPublicKeyInfo} ({@code -----BEGIN PUBLIC KEY-----}). Both are accepted as PEM or as raw
 * DER, so an operator can point at whatever {@code openssl genpkey -algorithm ed25519} produced without
 * a conversion step.
 */
public final class ServerSigningKeyLoader {

    private ServerSigningKeyLoader() {}

    /**
     * Loads the configured identity.
     *
     * @param properties the bound signing configuration
     * @return a {@link LoadedSigningIdentity} if a key is configured, else a {@link
     *     MissingSigningIdentity}
     * @throws IllegalStateException if a key path is configured but the key cannot be loaded, or a
     *     private key was given without a DID
     */
    public static ServerSigningIdentity load(ServerSigningProperties properties) {
        if (!properties.signingConfigured()) {
            return new MissingSigningIdentity();
        }
        if (properties.did() == null || properties.did().isBlank()) {
            throw new IllegalStateException(
                    "eyeandsickle.items.signing.private-key-path is set but eyeandsickle.items.signing.did is not. "
                            + "A record's issuerDid must be this server's DID, so signing cannot be configured "
                            + "without one.");
        }

        PrivateKey privateKey = loadPrivateKey(properties.privateKeyPath());
        PublicKey publicKey = properties.publicKeyPath() == null ? null : loadPublicKey(properties.publicKeyPath());
        return new LoadedSigningIdentity(properties.did(), properties.kid(), privateKey, publicKey);
    }

    /**
     * Loads an Ed25519 private key from a PKCS#8 file. Exposed for tests that write a temp key.
     *
     * @param path filesystem path to a PEM or DER PKCS#8 key
     * @return the private key
     * @throws IllegalStateException if the file cannot be read or is not a valid Ed25519 PKCS#8 key
     */
    static PrivateKey loadPrivateKey(String path) {
        byte[] der = readDer(path, "PRIVATE KEY");
        try {
            return keyFactory().generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "File '" + path + "' is not a valid Ed25519 PKCS#8 private key. It must be the key this "
                            + "server's DID resolves to, or every item it signs is unrecognizable.",
                    e);
        }
    }

    /**
     * Loads an Ed25519 public key from an X.509 {@code SubjectPublicKeyInfo} file.
     *
     * @param path filesystem path to a PEM or DER public key
     * @return the public key
     * @throws IllegalStateException if the file cannot be read or is not a valid Ed25519 public key
     */
    static PublicKey loadPublicKey(String path) {
        byte[] der = readDer(path, "PUBLIC KEY");
        try {
            return keyFactory().generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("File '" + path + "' is not a valid Ed25519 X.509 public key.", e);
        }
    }

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance(Ed25519Signatures.ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Ed25519 unavailable on this JVM; it is required for provenance signing", e);
        }
    }

    /**
     * Reads a key file, accepting PEM or raw DER. A PEM file is detected by its {@code -----BEGIN}
     * armor and stripped to the base64 body; anything else is treated as DER bytes.
     */
    private static byte[] readDer(String path, String pemLabel) {
        byte[] raw;
        try {
            raw = Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot read signing key at '" + path + "'. A configured key that is not present is a "
                            + "misconfiguration, not a reason to generate a new identity.",
                    e);
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.contains("-----BEGIN")) {
            String body = text.replace("-----BEGIN " + pemLabel + "-----", "")
                    .replace("-----END " + pemLabel + "-----", "")
                    .replaceAll("\\s", "");
            try {
                return Base64.getDecoder().decode(body);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Key file '" + path + "' has PEM armor but an undecodable body", e);
            }
        }
        return raw;
    }
}
