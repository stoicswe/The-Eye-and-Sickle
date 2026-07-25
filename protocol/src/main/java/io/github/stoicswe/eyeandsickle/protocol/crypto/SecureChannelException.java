package io.github.stoicswe.eyeandsickle.protocol.crypto;

/**
 * Thrown when a secure-channel operation fails for any reason.
 *
 * <h2>One exception type, on purpose</h2>
 *
 * Handshake failures, bad signatures, failed AEAD tags, replayed sequence numbers and malformed keys
 * all raise this same type with a deliberately unspecific message. That is not laziness — it is the
 * standard defence against oracle attacks. An attacker who can tell "signature invalid" apart from
 * "authentication tag invalid" apart from "sequence already seen" learns where in the protocol their
 * forgery got, and can grind toward a valid one.
 *
 * <p>So: log the detail server-side if you need it for operations, but do not vary what you tell the
 * peer based on <em>why</em> a frame was rejected. From outside, every failure looks the same.
 */
public class SecureChannelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SecureChannelException(String message) {
        super(message);
    }

    public SecureChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
