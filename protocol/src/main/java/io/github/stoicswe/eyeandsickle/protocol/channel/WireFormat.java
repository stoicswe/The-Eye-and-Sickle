package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Minimal length-prefixed binary encoding for handshake and frame headers.
 *
 * <p>Deliberately not JSON. These bytes are hashed into the handshake transcript and fed to a
 * signature, so the encoding must be unambiguous and byte-exact — and a reader must never be able to
 * be walked off the end of a buffer by a hostile peer. Length-prefixing every field gives both: no
 * two distinct field tuples can encode to the same bytes, and every read is bounds-checked against
 * the declared length.
 *
 * <p>All lengths are 32-bit big-endian and bounded, so a malicious {@code Integer.MAX_VALUE} length
 * fails immediately rather than causing a huge allocation.
 */
final class WireFormat {

    /** Refuse absurd field lengths outright — a handshake field is never megabytes. */
    private static final int MAX_FIELD_LENGTH = 1 << 20;

    private WireFormat() {}

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void writeBytes(byte[] value) {
            if (value.length > MAX_FIELD_LENGTH) {
                throw new SecureChannelException("Field too long to encode: " + value.length);
            }
            out.writeBytes(
                    ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
            out.writeBytes(value);
        }

        void writeString(String value) {
            writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    static final class Reader {
        private final ByteBuffer buffer;

        Reader(byte[] source) {
            if (source == null) {
                throw new SecureChannelException("Malformed message");
            }
            this.buffer = ByteBuffer.wrap(source).asReadOnlyBuffer();
        }

        byte[] readBytes() {
            if (buffer.remaining() < Integer.BYTES) {
                throw new SecureChannelException("Malformed message");
            }
            int length = buffer.getInt();
            if (length < 0 || length > MAX_FIELD_LENGTH || length > buffer.remaining()) {
                throw new SecureChannelException("Malformed message");
            }
            byte[] value = new byte[length];
            buffer.get(value);
            return value;
        }

        String readString() {
            return new String(readBytes(), StandardCharsets.UTF_8);
        }

        /**
         * Rejects trailing bytes.
         *
         * <p>Matters more than it looks: a peer that can append ignored bytes to a message can
         * often change how it hashes without changing how it parses, which is the shape of a
         * transcript-confusion attack.
         */
        void requireExhausted() {
            if (buffer.hasRemaining()) {
                throw new SecureChannelException("Malformed message");
            }
        }
    }
}
