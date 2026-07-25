package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF-SHA256 (RFC 5869) — turns raw Diffie-Hellman output into usable keys.
 *
 * <p>A DH shared secret is <em>secret</em> but not <em>uniform</em>: it is a curve point with
 * algebraic structure, not 32 random bytes. Using it directly as an AES key is the classic mistake.
 * HKDF's extract step condenses that structure into a uniform pseudorandom key, and its expand step
 * stretches it into as many independent keys as the protocol needs.
 *
 * <p>Two properties matter for how this is used in the handshake:
 *
 * <ul>
 *   <li><strong>The salt is the transcript hash.</strong> Binding derivation to a hash of everything
 *       both parties said means a tampered handshake derives different keys on each side, so the
 *       first encrypted frame simply fails to decrypt. Downgrade and message-injection attacks turn
 *       into a dead connection instead of a subtle compromise.
 *   <li><strong>The info string separates the two directions.</strong> Client-to-server and
 *       server-to-client get different keys from the same secret, so a captured frame cannot be
 *       reflected back at its sender and accepted.
 * </ul>
 *
 * <p>Uses the JDK's {@code javax.crypto.KDF} API, so there is still no third-party crypto anywhere
 * in this module.
 */
public final class Hkdf {

    private static final String ALGORITHM = "HKDF-SHA256";

    private Hkdf() {}

    /**
     * Derives key material.
     *
     * @param salt the handshake transcript hash; binds output to everything both peers exchanged
     * @param inputKeyMaterial concatenated Diffie-Hellman shared secrets
     * @param info a domain-separation label — distinct per direction and per purpose
     * @param outputLength how many bytes to produce
     * @return derived key material of exactly {@code outputLength} bytes
     */
    public static byte[] derive(byte[] salt, byte[] inputKeyMaterial, byte[] info, int outputLength) {
        if (outputLength <= 0 || outputLength > 8160) {
            // 255 * 32 is HKDF-SHA256's hard ceiling; anything above it silently means a bug.
            throw new IllegalArgumentException("Invalid HKDF output length: " + outputLength);
        }
        try {
            KDF hkdf = KDF.getInstance(ALGORITHM);
            return hkdf.deriveData(HKDFParameterSpec.ofExtract()
                    .addIKM(new SecretKeySpec(inputKeyMaterial, "HKDF-IKM"))
                    .addSalt(salt)
                    .thenExpand(info, outputLength));
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("HKDF derivation failed", e);
        }
    }
}
