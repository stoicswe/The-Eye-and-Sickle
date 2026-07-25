package io.github.stoicswe.eyeandsickle.protocol.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WireFormat} — the parser hostile bytes reach first.
 *
 * <p>Everything else in this package sits behind this encoding: attestation signing input, the
 * handshake transcript, the framing of every handshake message. Two failures here would defeat the
 * cryptography above without ever attacking it — a reader that can be walked off the end of a buffer,
 * and an encoding in which two different field tuples produce the same bytes. So the round-trip tests
 * are the small half of this file and the adversarial ones are the point.
 *
 * <p>Covers the length-prefixing requirement in {@code docs/architecture/07-transport-security.md}
 * §4.1.
 */
class WireFormatTest {

    /**
     * Mirrors {@code WireFormat.MAX_FIELD_LENGTH}, which is private. Duplicated deliberately: if the
     * production constant moves, the boundary tests below should fail and be re-derived rather than
     * silently follow it.
     *
     * <p><strong>[PROPOSAL]</strong> — {@code docs/architecture/07-transport-security.md} §4.1 says
     * lengths are "bounded" and names no number. 1 MiB is the implementation's choice; it is a
     * denial-of-service bound, not a balance value, so it belongs here rather than on the server, but
     * the number itself is undecided.
     */
    private static final int MAX_FIELD_LENGTH = 1 << 20;

    private static byte[] writeStrings(String... values) {
        WireFormat.Writer writer = new WireFormat.Writer();
        for (String value : values) {
            writer.writeString(value);
        }
        return writer.toByteArray();
    }

    private static byte[] writeFields(byte[]... values) {
        WireFormat.Writer writer = new WireFormat.Writer();
        for (byte[] value : values) {
            writer.writeBytes(value);
        }
        return writer.toByteArray();
    }

    /** A single field whose declared length is under the test's control, so it can lie about it. */
    private static byte[] field(int declaredLength, byte... payload) {
        return ByteBuffer.allocate(Integer.BYTES + payload.length)
                .putInt(declaredLength)
                .put(payload)
                .array();
    }

    // ------------------------------------------------------------------ round trip

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("byte fields come back in order, unchanged")
        void byteFieldsRoundTrip() {
            byte[] first = {0x00, 0x01, 0x02, 0x03};
            byte[] second = {(byte) 0xFF, (byte) 0x80};

            WireFormat.Reader reader = new WireFormat.Reader(writeFields(first, second));
            assertThat(reader.readBytes()).isEqualTo(first);
            assertThat(reader.readBytes()).isEqualTo(second);
            reader.requireExhausted();
        }

        @Test
        @DisplayName("string fields come back in order, unchanged")
        void stringFieldsRoundTrip() {
            byte[] encoded = writeStrings("did:plc:eye00000000000000", "#transport-1");

            WireFormat.Reader reader = new WireFormat.Reader(encoded);
            assertThat(reader.readString()).isEqualTo("did:plc:eye00000000000000");
            assertThat(reader.readString()).isEqualTo("#transport-1");
            reader.requireExhausted();
        }

        @Test
        @DisplayName("an empty field still occupies its length prefix")
        void emptyFieldsAreEncodedNotOmitted() {
            byte[] encoded = writeFields(new byte[0], new byte[0]);

            // Eight bytes: two length prefixes, no payload. An empty field that encoded to nothing
            // would be indistinguishable from an absent one, which is the same ambiguity that
            // length prefixes exist to remove.
            assertThat(encoded).hasSize(2 * Integer.BYTES);

            WireFormat.Reader reader = new WireFormat.Reader(encoded);
            assertThat(reader.readBytes()).isEmpty();
            assertThat(reader.readBytes()).isEmpty();
            reader.requireExhausted();
        }

        @Test
        @DisplayName("an empty string round trips as an empty string")
        void emptyStringRoundTrips() {
            WireFormat.Reader reader = new WireFormat.Reader(writeStrings("", "after"));
            assertThat(reader.readString()).isEmpty();
            assertThat(reader.readString()).isEqualTo("after");
            reader.requireExhausted();
        }

        @Test
        @DisplayName("multi-byte UTF-8 survives intact")
        void multiByteUtf8RoundTrips() {
            String cyrillic = "Серп"; // two bytes per character in UTF-8
            String japanese = "監視"; // three bytes per character

            WireFormat.Reader reader = new WireFormat.Reader(writeStrings(cyrillic, japanese));
            assertThat(reader.readString()).isEqualTo(cyrillic);
            assertThat(reader.readString()).isEqualTo(japanese);
            reader.requireExhausted();
        }

        @Test
        @DisplayName("non-BMP characters survive intact, and lengths count bytes not characters")
        void nonBmpUtf8RoundTrips() {
            String eye = Character.toString(0x1F441); // EYE: one code point, two chars, four UTF-8 bytes
            byte[] encoded = writeStrings(eye);

            // The prefix must be the UTF-8 byte count. A char count would leave two bytes of the
            // character in the buffer for the next field to read, silently reframing every field
            // after it — a reader desynchronisation a peer can trigger just by using an emoji.
            assertThat(ByteBuffer.wrap(encoded).getInt()).isEqualTo(4);
            assertThat(encoded).hasSize(Integer.BYTES + 4);

            WireFormat.Reader reader = new WireFormat.Reader(encoded);
            assertThat(reader.readString()).isEqualTo(eye);
            reader.requireExhausted();
        }

        @Test
        @DisplayName("a field of exactly the maximum length round trips")
        void maximumLengthFieldRoundTrips() {
            // The reader's cap must not be tighter than the writer's, or the writer could produce a
            // message its own peer refuses to parse.
            byte[] atTheCap = new byte[MAX_FIELD_LENGTH];
            WireFormat.Reader reader = new WireFormat.Reader(writeFields(atTheCap));
            assertThat(reader.readBytes()).hasSize(MAX_FIELD_LENGTH);
            reader.requireExhausted();
        }
    }

    // ------------------------------------------------------------------ unambiguity

    @Nested
    @DisplayName("unambiguity")
    class Unambiguity {

        @Test
        @DisplayName("moving a field boundary changes the encoding")
        void fieldBoundariesChangeTheEncoding() {
            // This is the whole reason fields are length-prefixed. Concatenated, both are "abc";
            // if the encoding did not record where one field ends, a signature or transcript hash
            // over ("ab","c") would equally cover ("a","bc") — see TransportKeyAttestationTest for
            // what that buys an attacker in practice.
            assertThat(writeStrings("ab", "c")).isNotEqualTo(writeStrings("a", "bc"));
            assertThat(writeFields(new byte[] {1, 2}, new byte[] {3}))
                    .isNotEqualTo(writeFields(new byte[] {1}, new byte[] {2, 3}));
        }

        @Test
        @DisplayName("each encoding parses back to its own field split, not the other's")
        void eachEncodingParsesToItsOwnSplit() {
            WireFormat.Reader left = new WireFormat.Reader(writeStrings("ab", "c"));
            assertThat(left.readString()).isEqualTo("ab");
            assertThat(left.readString()).isEqualTo("c");

            WireFormat.Reader right = new WireFormat.Reader(writeStrings("a", "bc"));
            assertThat(right.readString()).isEqualTo("a");
            assertThat(right.readString()).isEqualTo("bc");
        }

        @Test
        @DisplayName("an empty field is not the same encoding as a missing one")
        void emptyIsNotAbsent() {
            assertThat(writeStrings("", "value")).isNotEqualTo(writeStrings("value"));
        }
    }

    // ------------------------------------------------------------------ hostile input

    @Nested
    @DisplayName("hostile input")
    class HostileInput {

        @Test
        @DisplayName("a null source is rejected as a protocol error, not a NullPointerException")
        void nullSourceIsRejected() {
            // Defends against: a null slipping out of a transport layer and surfacing as an NPE deep
            // in a connection handler, where it reads as a local bug rather than a bad peer.
            assertThatThrownBy(() -> new WireFormat.Reader(null))
                    .isInstanceOf(SecureChannelException.class)
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("an empty buffer is rejected")
        void emptyBufferIsRejected() {
            // Defends against: a zero-length message being read as a zero-length field.
            WireFormat.Reader reader = new WireFormat.Reader(new byte[0]);
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a truncated length prefix is rejected")
        void truncatedLengthPrefixIsRejected() {
            // Defends against: a peer sending fewer than four bytes and the reader reading whatever
            // is adjacent in memory, or throwing BufferUnderflowException out of the parser.
            WireFormat.Reader reader = new WireFormat.Reader(new byte[] {0, 0, 1});
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a length longer than the remaining buffer is rejected")
        void lengthBeyondTheBufferIsRejected() {
            // Defends against: the classic over-declared length that walks a reader past the end of
            // the message it was given.
            WireFormat.Reader reader = new WireFormat.Reader(field(64, "abc".getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a negative length is rejected")
        void negativeLengthIsRejected() {
            // Defends against: a signed 32-bit length being used as an array size (NegativeArraySize)
            // or, worse, sneaking past a naive `length <= remaining` bounds check.
            WireFormat.Reader reader = new WireFormat.Reader(field(-1, (byte) 'x'));
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("Integer.MAX_VALUE as a length is rejected without allocating")
        void integerMaxLengthIsRejected() {
            // Defends against: a two-gigabyte allocation triggered by four attacker-chosen bytes —
            // an OutOfMemoryError is a denial of service that costs the attacker nothing.
            WireFormat.Reader reader = new WireFormat.Reader(field(Integer.MAX_VALUE));
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a length above the field cap is rejected even when the bytes are really there")
        void lengthAboveTheCapIsRejected() {
            // Defends against: a peer that is willing to actually send the megabytes. The bounds
            // check alone would accept this; the cap is what says a handshake field is never this
            // large, so refuse it before allocating.
            byte[] oversized = new byte[Integer.BYTES + MAX_FIELD_LENGTH + 1];
            ByteBuffer.wrap(oversized).putInt(MAX_FIELD_LENGTH + 1);

            WireFormat.Reader reader = new WireFormat.Reader(oversized);
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("reading past the last field is rejected")
        void readingPastTheEndIsRejected() {
            // Defends against: a message with fewer fields than the parser expects returning empty
            // or stale values instead of failing.
            WireFormat.Reader reader = new WireFormat.Reader(writeStrings("only-one"));
            assertThat(reader.readString()).isEqualTo("only-one");
            assertThatThrownBy(reader::readBytes).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("trailing bytes are rejected")
        void trailingBytesAreRejected() {
            // Defends against transcript confusion: a peer that can append bytes which parse
            // identically but hash differently can make two sides agree on a message and disagree on
            // its transcript — or make one signed statement carry an unsigned tail. The parse must
            // consume the message exactly.
            byte[] encoded = writeStrings("field");
            byte[] withTail = Arrays.copyOf(encoded, encoded.length + 1);

            WireFormat.Reader reader = new WireFormat.Reader(withTail);
            assertThat(reader.readString()).isEqualTo("field");
            assertThatThrownBy(reader::requireExhausted).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a fully consumed buffer passes the exhaustion check")
        void exhaustedBufferIsAccepted() {
            WireFormat.Reader reader = new WireFormat.Reader(writeStrings("a", "b"));
            assertThat(reader.readString()).isEqualTo("a");
            assertThat(reader.readString()).isEqualTo("b");
            reader.requireExhausted();
        }

        @Test
        @DisplayName("an unread field counts as trailing bytes")
        void unreadFieldsAreTrailingBytes() {
            // Defends against: a parser that reads the fields it knows about and ignores extra ones,
            // which is how an attacker smuggles data past a version check.
            WireFormat.Reader reader = new WireFormat.Reader(writeStrings("read", "ignored"));
            assertThat(reader.readString()).isEqualTo("read");
            assertThatThrownBy(reader::requireExhausted).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a field longer than the cap is rejected at write time")
        void overLongFieldIsRejectedOnWrite() {
            // Defends against: a caller building a message no peer would accept and only finding out
            // at the far end, where it looks like the peer's fault.
            WireFormat.Writer writer = new WireFormat.Writer();
            assertThatThrownBy(() -> writer.writeBytes(new byte[MAX_FIELD_LENGTH + 1]))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("the write cap counts UTF-8 bytes, not characters")
        void overLongStringIsRejectedOnWrite() {
            // Defends against: a cap that measures String.length() and lets a three-bytes-per-char
            // script through at three times the intended size.
            String tooLong = "監".repeat((MAX_FIELD_LENGTH / 3) + 1);
            WireFormat.Writer writer = new WireFormat.Writer();
            assertThatThrownBy(() -> writer.writeString(tooLong)).isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ documented behaviour

    @Nested
    @DisplayName("documented behaviour (not defences)")
    class DocumentedBehaviour {

        @Test
        @DisplayName("malformed UTF-8 decodes to replacement characters rather than throwing")
        void malformedUtf8IsReplacedNotRejected() {
            // NOT a defence — pinned so nobody assumes readString() validates. 0xFF is not a legal
            // UTF-8 byte anywhere; Java substitutes U+FFFD. Callers that re-encode the decoded string
            // to check a signature therefore fail closed (the re-encoded bytes differ from what was
            // signed), which is why this is acceptable here. A caller that treats readString() as
            // proof of well-formed UTF-8 would be wrong.
            WireFormat.Reader reader = new WireFormat.Reader(field(2, (byte) 0xFF, (byte) 0xFE));
            assertThat(reader.readString()).contains(Character.toString(0xFFFD));
        }

        @Test
        @DisplayName("an unpaired surrogate is encoded lossily")
        void unpairedSurrogateIsLossy() {
            // NOT a defence — pinned because it is a collision in the encoding: a lone surrogate and
            // a literal '?' produce identical bytes, so they produce identical signing input. Real
            // DIDs and key ids are ASCII, so nothing here is exposed, but any future field that
            // accepts arbitrary user text must be validated before it is signed.
            String lone = "\uD800";
            WireFormat.Reader reader = new WireFormat.Reader(writeStrings(lone));

            assertThat(writeStrings(lone)).isEqualTo(writeStrings("?"));
            assertThat(reader.readString()).isEqualTo("?").isNotEqualTo(lone);
        }

        @Test
        @DisplayName("the reader aliases the caller's array rather than copying it")
        void readerAliasesItsSource() {
            byte[] source = writeStrings("original");
            WireFormat.Reader reader = new WireFormat.Reader(source);
            source[source.length - 1] ^= 0x20;

            // NOT a defence. The read-only view stops writes *through* the buffer; it does not
            // snapshot the bytes. The handshake hashes the same array it parses, so a caller that
            // mutates a buffer after handing it over would desynchronise its own transcript.
            assertThat(reader.readString()).isEqualTo("originaL");
        }
    }
}
