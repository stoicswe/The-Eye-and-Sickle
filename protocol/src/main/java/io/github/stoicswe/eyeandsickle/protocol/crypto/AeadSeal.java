package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM authenticated encryption — the per-message protection on an established channel.
 *
 * <p>GCM is an AEAD: it provides confidentiality <em>and</em> integrity in one pass. The 128-bit
 * authentication tag is what makes the channel tamper-evident — flip any bit of the ciphertext, the
 * associated data, or the tag itself, and decryption fails rather than returning altered plaintext.
 * That property is the whole point of this class.
 *
 * <h2>The nonce rule</h2>
 *
 * GCM's one catastrophic failure mode is nonce reuse: encrypting two different messages under the
 * same key and nonce leaks the XOR of the plaintexts <em>and</em> the authentication subkey, which
 * lets an attacker forge arbitrary messages from then on. It is not a degradation, it is a total
 * break.
 *
 * <p>So this class does not generate nonces. It requires the caller to pass one, and the channel
 * derives it from a strictly increasing per-direction counter that can never repeat within a session
 * — no randomness involved, because random 96-bit nonces have a birthday bound that a long-lived
 * connection can actually reach. A fresh key per session plus a counter per direction is the
 * construction with no collision risk at all.
 */
public final class AeadSeal {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    /** Required key length in bytes (AES-256). */
    public static final int KEY_LENGTH = 32;

    /** Required nonce length in bytes — 96 bits, the size GCM is designed around. */
    public static final int NONCE_LENGTH = 12;

    /** Authentication tag length in bits. */
    public static final int TAG_LENGTH_BITS = 128;

    /** Authentication tag length in bytes, i.e. the ciphertext expansion. */
    public static final int TAG_LENGTH = TAG_LENGTH_BITS / 8;

    private AeadSeal() {}

    /**
     * Encrypts and authenticates.
     *
     * @param key 32-byte AES-256 key
     * @param nonce 12-byte nonce, never repeated under this key
     * @param associatedData authenticated but not encrypted — the frame header, so its sequence
     *     number and direction cannot be altered in flight
     * @param plaintext the message
     * @return ciphertext followed by the 16-byte tag
     */
    public static byte[] seal(byte[] key, byte[] nonce, byte[] associatedData, byte[] plaintext) {
        checkKeyAndNonce(key, nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("AEAD encryption failed", e);
        }
    }

    /**
     * Verifies and decrypts.
     *
     * @param key 32-byte AES-256 key
     * @param nonce the 12-byte nonce the sender used
     * @param associatedData the same associated data the sender authenticated
     * @param ciphertext ciphertext followed by its tag
     * @return the original plaintext
     * @throws SecureChannelException if the data was altered, truncated, or encrypted under a
     *     different key or nonce — the failures are deliberately indistinguishable
     */
    public static byte[] open(byte[] key, byte[] nonce, byte[] associatedData, byte[] ciphertext) {
        checkKeyAndNonce(key, nonce);
        if (ciphertext == null || ciphertext.length < TAG_LENGTH) {
            throw new SecureChannelException("Frame rejected");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            // Note the uniform message: see SecureChannelException's class doc on why the reason is
            // not reported to the peer.
            throw new SecureChannelException("Frame rejected");
        }
    }

    /**
     * Builds a GCM nonce from a direction-scoped counter.
     *
     * <p>Layout is 4 zero bytes followed by the 64-bit big-endian counter. Because each direction of
     * each session has its own key and its own counter starting at zero, a nonce can never repeat
     * under a given key.
     *
     * @param counter the message sequence number, starting at 0
     * @return a 12-byte nonce
     */
    public static byte[] nonceForCounter(long counter) {
        if (counter < 0) {
            throw new IllegalArgumentException("Sequence counter overflowed");
        }
        return ByteBuffer.allocate(NONCE_LENGTH).putInt(0).putLong(counter).array();
    }

    private static void checkKeyAndNonce(byte[] key, byte[] nonce) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("AES-256 key must be " + KEY_LENGTH + " bytes");
        }
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("GCM nonce must be " + NONCE_LENGTH + " bytes");
        }
    }
}
