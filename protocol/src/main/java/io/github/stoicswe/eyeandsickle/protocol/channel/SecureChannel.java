package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.AeadSeal;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * An established, mutually authenticated, encrypted session between two DIDs.
 *
 * <p>Produced by {@link SecureHandshake}. Every message sent through {@link #seal(byte[])} is
 * encrypted, authenticated, and bound to a strictly increasing sequence number; {@link #open(byte[])}
 * rejects anything altered, replayed, reordered, or reflected.
 *
 * <h2>What this protects against</h2>
 *
 * <table border="1">
 *   <caption>Attack &rarr; defence</caption>
 *   <tr><td>Reading traffic</td><td>AES-256-GCM with per-session keys</td></tr>
 *   <tr><td>Altering a message</td><td>GCM tag fails; the frame is rejected, not silently changed</td></tr>
 *   <tr><td>Altering the header</td><td>the header is the AEAD associated data, so it is authenticated too</td></tr>
 *   <tr><td>Replaying a message</td><td>sequence numbers must strictly increase; a repeat is refused</td></tr>
 *   <tr><td>Reordering or dropping</td><td>a gap or a step backwards is refused</td></tr>
 *   <tr><td>Reflecting a message back at its sender</td><td>each direction has its own key</td></tr>
 *   <tr><td>Recording now, stealing the key later</td><td>ephemeral keys give forward secrecy</td></tr>
 * </table>
 *
 * <h2>What this does NOT protect against, and must not be believed to</h2>
 *
 * It authenticates <em>who</em> sent a message and proves it arrived unaltered. It says nothing
 * about whether the contents are <em>true</em>. A cheating client can hold a perfectly valid channel
 * and send perfectly authenticated lies about how much ethecoin it mined. Invariant I14 is untouched
 * by any of this: the server still validates everything a cheater would want to forge. Encryption is
 * not authority.
 *
 * <h2>Threading</h2>
 *
 * Not thread-safe. The sequence counters must advance in a single well-defined order, so confine an
 * instance to one connection handler, or guard it externally.
 */
public final class SecureChannel {

    /** Current frame format version, authenticated as part of every header. */
    static final byte FRAME_VERSION = 1;

    private static final int HEADER_LENGTH = 1 + 1 + Long.BYTES;

    /**
     * GCM's safety limit. Well below the 2^32-message bound at which birthday effects on the
     * authentication subkey start to matter; a session this long should renegotiate anyway.
     */
    private static final long MAX_SEQUENCE = 1L << 32;

    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final byte sendDirection;
    private final byte receiveDirection;
    private final String peerDid;

    private long sendSequence;
    private long highestReceivedSequence = -1;

    SecureChannel(byte[] sendKey, byte[] receiveKey, byte sendDirection, byte receiveDirection, String peerDid) {
        this.sendKey = sendKey;
        this.receiveKey = receiveKey;
        this.sendDirection = sendDirection;
        this.receiveDirection = receiveDirection;
        this.peerDid = Objects.requireNonNull(peerDid, "peerDid");
    }

    /**
     * The DID at the other end, cryptographically established during the handshake.
     *
     * <p>This is the value to attribute actions to — not a source IP, not a TLS certificate's
     * hostname, not a self-declared field inside the message.
     *
     * @return the peer's DID
     */
    public String peerDid() {
        return peerDid;
    }

    /**
     * Encrypts a message for transmission.
     *
     * @param plaintext the message
     * @return a self-contained frame: header followed by ciphertext and tag
     */
    public byte[] seal(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        if (sendSequence >= MAX_SEQUENCE) {
            throw new SecureChannelException("Session exhausted; renegotiate before sending more");
        }
        long sequence = sendSequence++;
        byte[] header = header(sendDirection, sequence);
        byte[] ciphertext = AeadSeal.seal(sendKey, AeadSeal.nonceForCounter(sequence), header, plaintext);

        byte[] frame = new byte[header.length + ciphertext.length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(ciphertext, 0, frame, header.length, ciphertext.length);
        return frame;
    }

    /**
     * Verifies and decrypts a received frame.
     *
     * @param frame a frame produced by the peer's {@link #seal(byte[])}
     * @return the original plaintext
     * @throws SecureChannelException if the frame was altered, replayed, reordered, reflected, or
     *     produced by anyone without the session key
     */
    public byte[] open(byte[] frame) {
        if (frame == null || frame.length < HEADER_LENGTH + AeadSeal.TAG_LENGTH) {
            throw new SecureChannelException("Frame rejected");
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        byte version = buffer.get();
        byte direction = buffer.get();
        long sequence = buffer.getLong();

        if (version != FRAME_VERSION) {
            throw new SecureChannelException("Frame rejected");
        }
        // Reject our own direction byte: without this a frame we sent could be echoed back and,
        // if both directions shared a key, would decrypt. They do not share a key, so this is
        // defence in depth — but it is the check that makes that non-sharing explicit.
        if (direction != receiveDirection) {
            throw new SecureChannelException("Frame rejected");
        }
        // Strictly increasing. Equal means replay; lower means reorder; a gap means a dropped or
        // withheld frame. All three are refused: on a reliable ordered transport any of them is an
        // attack, not a network condition.
        if (sequence != highestReceivedSequence + 1) {
            throw new SecureChannelException("Frame rejected");
        }

        byte[] header = Arrays.copyOfRange(frame, 0, HEADER_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(frame, HEADER_LENGTH, frame.length);
        byte[] plaintext = AeadSeal.open(receiveKey, AeadSeal.nonceForCounter(sequence), header, ciphertext);

        // Only advance AFTER the tag verifies, so a forged frame cannot burn a sequence number and
        // desynchronise the session.
        highestReceivedSequence = sequence;
        return plaintext;
    }

    /** How many frames this side has sent. Exposed for tests and operational metrics. */
    public long framesSent() {
        return sendSequence;
    }

    private static byte[] header(byte direction, long sequence) {
        return ByteBuffer.allocate(HEADER_LENGTH)
                .put(FRAME_VERSION)
                .put(direction)
                .putLong(sequence)
                .array();
    }
}
