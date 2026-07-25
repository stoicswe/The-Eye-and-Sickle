package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

/**
 * X25519 Diffie-Hellman key agreement — the key-establishment half of the secure channel.
 *
 * <h2>Why a second key type when we already have Ed25519</h2>
 *
 * Ed25519 signs; it does not do key agreement. X25519 does key agreement; it does not sign. They
 * live on the same underlying curve, and it <em>is</em> possible to convert an Ed25519 key to an
 * X25519 one — which is exactly why it needs saying that this codebase does not do that.
 *
 * <p>Reusing one key pair across two algorithms is a well-known footgun: the security proofs for
 * each assume the key is used for that algorithm alone, and cross-protocol attacks exploit the gap.
 * Instead, a server or player holds a <em>separate</em> X25519 transport key whose ownership is
 * proven by an Ed25519 signature from their DID key — see {@code TransportKeyAttestation}. That also
 * buys something practical: a transport key can be rotated on a short schedule without touching the
 * long-lived DID identity that item provenance depends on.
 *
 * <h2>Key encoding</h2>
 *
 * Public keys travel in X.509 {@code SubjectPublicKeyInfo} form (44 bytes for X25519) rather than as
 * a raw 32-byte u-coordinate. Raw is more compact, but round-tripping it through the JCA means
 * hand-converting little-endian {@code BigInteger} u-coordinates, which is a fiddly, easy-to-get-
 * subtly-wrong step in the one place where subtle wrongness is most expensive. Twelve extra bytes
 * per handshake is not a real cost; a broken key decoder is.
 */
public final class X25519KeyExchange {

    /** JCA algorithm name for the key type. */
    public static final String ALGORITHM = "X25519";

    /** Length of an X.509-encoded X25519 public key. */
    public static final int ENCODED_PUBLIC_KEY_LENGTH = 44;

    /** Length of the raw shared secret X25519 produces. */
    public static final int SHARED_SECRET_LENGTH = 32;

    private X25519KeyExchange() {}

    /**
     * Generates an ephemeral or static X25519 key pair.
     *
     * @return a new key pair
     */
    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("X25519 unavailable on this JVM; the secure channel requires it", e);
        }
    }

    /**
     * Computes the shared secret between our private key and a peer's public key.
     *
     * <p>The result is never used as a key directly — it goes into {@link Hkdf} along with the
     * handshake transcript. A raw DH output has structure and is not uniformly random, which is
     * precisely what a KDF exists to fix.
     *
     * @param ourPrivate our X25519 private key
     * @param theirPublic the peer's X25519 public key
     * @return the 32-byte shared secret
     */
    public static byte[] agree(PrivateKey ourPrivate, PublicKey theirPublic) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance(ALGORITHM);
            agreement.init(ourPrivate);
            agreement.doPhase(theirPublic, true);
            byte[] secret = agreement.generateSecret();
            if (secret.length != SHARED_SECRET_LENGTH) {
                throw new IllegalStateException("Unexpected X25519 secret length: " + secret.length);
            }
            // An all-zero shared secret means the peer sent a low-order point, which forces a
            // known secret regardless of our private key. RFC 7748 §6.1 says to reject it.
            byte[] zero = new byte[SHARED_SECRET_LENGTH];
            if (Arrays.equals(secret, zero)) {
                throw new SecureChannelException("Peer contributed a low-order X25519 point");
            }
            return secret;
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("X25519 key agreement failed", e);
        }
    }

    /**
     * Encodes a public key for the wire.
     *
     * @param publicKey an X25519 public key
     * @return the X.509 encoding
     */
    public static byte[] encodePublicKey(PublicKey publicKey) {
        return publicKey.getEncoded();
    }

    /**
     * Decodes a peer's public key received over the wire.
     *
     * @param encoded an X.509-encoded X25519 public key
     * @return the public key
     * @throws SecureChannelException if the bytes are not a valid X25519 public key
     */
    public static PublicKey decodePublicKey(byte[] encoded) {
        if (encoded == null || encoded.length != ENCODED_PUBLIC_KEY_LENGTH) {
            throw new SecureChannelException("Malformed X25519 public key: expected " + ENCODED_PUBLIC_KEY_LENGTH
                    + " bytes, got " + (encoded == null ? "null" : encoded.length));
        }
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("Malformed X25519 public key", e);
        }
    }
}
