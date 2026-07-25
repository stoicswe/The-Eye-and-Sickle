package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PayloadDigest} — the RFC 9530 content-digest checksum carried between servers and
 * to the client.
 *
 * <p>The digest itself is verified against the published SHA-256 vectors, so the tests say "correct"
 * independently of the implementation. The rest is parsing discipline: the field value arrives from
 * an untrusted peer, so every malformed spelling of it must be refused rather than misread.
 */
class PayloadDigestTest {

    @Nested
    @DisplayName("SHA-256 vectors")
    class Vectors {

        @Test
        @DisplayName("the empty payload matches the published SHA-256 of empty input")
        void emptyInput() {
            // NIST/FIPS-180 known answer: SHA-256("") .
            assertThat(HexFormat.of().formatHex(PayloadDigest.sha256(new byte[0])))
                    .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        }

        @Test
        @DisplayName("\"abc\" matches the published SHA-256")
        void abc() {
            assertThat(HexFormat.of().formatHex(PayloadDigest.sha256("abc".getBytes(StandardCharsets.UTF_8))))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        }
    }

    @Nested
    @DisplayName("RFC 9530 field format")
    class FieldFormat {

        @Test
        @DisplayName("a content-digest is sha-256=:<base64>:")
        void fieldValueShape() {
            byte[] payload = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
            String field = PayloadDigest.contentDigest(payload);

            assertThat(field).startsWith("sha-256=:").endsWith(":");
            String base64 = field.substring("sha-256=:".length(), field.length() - 1);
            assertThat(Base64.getDecoder().decode(base64))
                    .as("the framed base64 must decode to the raw 32 digest bytes")
                    .isEqualTo(PayloadDigest.sha256(payload));
        }

        @Test
        @DisplayName("decode recovers exactly what contentDigest encoded")
        void roundTrip() {
            byte[] payload = "federation sync payload".getBytes(StandardCharsets.UTF_8);
            assertThat(PayloadDigest.decode(PayloadDigest.contentDigest(payload)))
                    .isEqualTo(PayloadDigest.sha256(payload));
        }
    }

    @Nested
    @DisplayName("verification")
    class Verification {

        @Test
        @DisplayName("an intact payload matches its own digest")
        void intactMatches() {
            byte[] payload = "{\"op\":\"item-transfer\",\"amount\":500}".getBytes(StandardCharsets.UTF_8);
            assertThat(PayloadDigest.matches(payload, PayloadDigest.contentDigest(payload)))
                    .isTrue();
        }

        @Test
        @DisplayName("a single flipped bit is caught")
        void corruptionIsCaught() {
            // The whole reason the field exists: a byte changed anywhere in transit must not verify.
            byte[] original = "deployed miner yield: 42 ec".getBytes(StandardCharsets.UTF_8);
            String digest = PayloadDigest.contentDigest(original);

            byte[] corrupted = original.clone();
            corrupted[10] ^= 0x01;
            assertThat(PayloadDigest.matches(corrupted, digest)).isFalse();
        }

        @Test
        @DisplayName("truncation is caught")
        void truncationIsCaught() {
            byte[] original = "a payload that arrives one byte short".getBytes(StandardCharsets.UTF_8);
            String digest = PayloadDigest.contentDigest(original);
            byte[] truncated = java.util.Arrays.copyOf(original, original.length - 1);
            assertThat(PayloadDigest.matches(truncated, digest)).isFalse();
        }

        @Test
        @DisplayName("an empty payload has a valid, checkable digest")
        void emptyPayloadRoundTrips() {
            assertThat(PayloadDigest.matches(new byte[0], PayloadDigest.contentDigest(new byte[0])))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("malformed field values are refused, never misread")
    class MalformedInput {

        @Test
        @DisplayName("null is not a match and decodes to null")
        void nullValue() {
            assertThat(PayloadDigest.decode(null)).isNull();
            assertThat(PayloadDigest.matches("x".getBytes(StandardCharsets.UTF_8), null))
                    .isFalse();
        }

        @Test
        @DisplayName("an unsupported algorithm is refused rather than silently accepted")
        void unsupportedAlgorithm() {
            // A peer offering only sha-512 is not interoperable; accepting the field while checking
            // nothing would be worse than rejecting it.
            byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
            String sha512Shaped = "sha-512=:" + Base64.getEncoder().encodeToString(new byte[64]) + ":";
            assertThat(PayloadDigest.decode(sha512Shaped)).isNull();
            assertThat(PayloadDigest.matches(payload, sha512Shaped)).isFalse();
        }

        @Test
        @DisplayName("a list of algorithms is refused — we do not guess which member to trust")
        void algorithmListIsRefused() {
            byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
            String list = PayloadDigest.contentDigest(payload) + ", sha-512=:abc:";
            assertThat(PayloadDigest.decode(list)).isNull();
        }

        @Test
        @DisplayName("missing frame colons, bad base64, and wrong digest length are all refused")
        void assortedMalformations() {
            assertThat(PayloadDigest.decode("sha-256=abcdef"))
                    .as("no colon frame")
                    .isNull();
            assertThat(PayloadDigest.decode("sha-256=:not valid base64!:"))
                    .as("undecodable base64")
                    .isNull();
            // Correctly framed base64, but of the wrong number of bytes for a SHA-256 digest.
            String tooShort = "sha-256=:" + Base64.getEncoder().encodeToString(new byte[16]) + ":";
            assertThat(PayloadDigest.decode(tooShort))
                    .as("16 bytes is not a SHA-256")
                    .isNull();
            assertThat(PayloadDigest.decode("")).as("empty").isNull();
            assertThat(PayloadDigest.decode("garbage")).isNull();
        }

        @Test
        @DisplayName("surrounding whitespace is tolerated")
        void whitespaceTolerated() {
            byte[] payload = "y".getBytes(StandardCharsets.UTF_8);
            String padded = "  " + PayloadDigest.contentDigest(payload) + "  ";
            assertThat(PayloadDigest.matches(payload, padded)).isTrue();
        }
    }
}
