package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.server.discovery.ServerDescriptorCodec.DescriptorCodecException;
import io.github.stoicswe.eyeandsickle.server.discovery.ServerDescriptorCodec.ParsedEnvelope;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServerDescriptorCodec} — the format layer that produces and structurally parses
 * the signed envelope, with no policy of its own.
 *
 * <p>Two properties matter most here. First, {@link ServerDescriptorCodec#parse} classifies every
 * structural failure as a typed {@link DescriptorFault} rather than throwing something the verifier
 * cannot translate. Second — the load-bearing one — verification re-derives the canonical bytes from the
 * parsed descriptor object, so key order and whitespace in the received JSON are irrelevant: a signature
 * made by the signer verifies no matter how a relaying peer re-serialised the envelope.
 */
class ServerDescriptorCodecTest {

    private static final int CAP = DiscoveryProperties.DEFAULT_MAX_DESCRIPTOR_BYTES;
    private final DescriptorFixture fixture = new DescriptorFixture();

    @Nested
    @DisplayName("sign then parse")
    class RoundTrip {

        @Test
        @DisplayName("a signed envelope parses, and its canonical bytes verify against the signer's key")
        void roundTrips() {
            String envelope = fixture.signed(7);

            ParsedEnvelope parsed = ServerDescriptorCodec.parse(envelope, CAP);

            assertThat(parsed.alg()).isEqualTo(Ed25519Signatures.JOSE_ALG);
            assertThat(parsed.kid()).isEqualTo(DescriptorFixture.KID);
            assertThat(parsed.descriptor())
                    .containsEntry(ServerDescriptorCodec.PEER_DID, DescriptorFixture.PEER_DID)
                    .containsEntry(ServerDescriptorCodec.SEQUENCE, 7);

            byte[] signature = ServerDescriptorCodec.decodeBase64Url(parsed.sig());
            assertThat(Ed25519Signatures.verify(fixture.signing.getPublic(), parsed.canonicalBytes(), signature))
                    .isTrue();
        }

        @Test
        @DisplayName("the signature is order-independent: re-ordering the descriptor's keys still verifies")
        void canonicalizationIsOrderIndependent() {
            String signed = fixture.signed(7);
            Map<String, Object> envelope = Jsonb.readObject(signed);

            // A relaying peer re-serialises the envelope with the descriptor's keys in a different order.
            @SuppressWarnings("unchecked")
            Map<String, Object> descriptor =
                    (Map<String, Object>) envelope.get(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR);
            Map<String, Object> reordered = new LinkedHashMap<>();
            List<String> keys = descriptor.keySet().stream()
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList();
            for (String key : keys) {
                reordered.put(key, descriptor.get(key));
            }
            envelope.put(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR, reordered);
            String rebuilt = Jsonb.writeObject(envelope);

            ParsedEnvelope parsed = ServerDescriptorCodec.parse(rebuilt, CAP);
            byte[] signature = ServerDescriptorCodec.decodeBase64Url(parsed.sig());

            // If canonicalization keyed off byte layout instead of JCS, this would fail — and every peer
            // that touched a descriptor in transit would break its signature.
            assertThat(Ed25519Signatures.verify(fixture.signing.getPublic(), parsed.canonicalBytes(), signature))
                    .as("JCS canonicalization must reproduce the signer's exact bytes regardless of key order")
                    .isTrue();
        }

        @Test
        @DisplayName("sign refuses a negative sequence")
        void signRejectsNegativeSequence() {
            assertThatThrownBy(() -> ServerDescriptorCodec.sign(
                            DescriptorFixture.PEER_DID,
                            DescriptorFixture.ENDPOINT,
                            -1,
                            List.of(),
                            fixture.transportKey,
                            null,
                            null,
                            null,
                            null,
                            DescriptorFixture.KID,
                            fixture.signing.getPrivate()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sequence");
        }
    }

    @Nested
    @DisplayName("parse — structural faults are typed, not thrown raw")
    class StructuralFaults {

        @Test
        @DisplayName("an oversized envelope is refused before any parsing")
        void oversizeRefused() {
            String envelope = fixture.signed(1);
            int tinyCap = 10;

            assertThatThrownBy(() -> ServerDescriptorCodec.parse(envelope, tinyCap))
                    .isInstanceOf(DescriptorCodecException.class)
                    .extracting(e -> ((DescriptorCodecException) e).fault())
                    .isEqualTo(DescriptorFault.TOO_LARGE);
        }

        @Test
        @DisplayName("non-JSON is MALFORMED_JSON")
        void notJson() {
            assertFault("this is not json", DescriptorFault.MALFORMED_JSON);
        }

        @Test
        @DisplayName("a JSON array (not an object) is MALFORMED_JSON")
        void jsonArrayIsMalformed() {
            assertFault("[1,2,3]", DescriptorFault.MALFORMED_JSON);
        }

        @Test
        @DisplayName("an envelope with no descriptor object is MISSING_FIELD")
        void noDescriptor() {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(ServerDescriptorCodec.ENVELOPE_CANONICALIZATION, ProvenanceEnvelope.JCS_RFC8785);
            envelope.put(ServerDescriptorCodec.ENVELOPE_SIGNATURE, Map.of("kid", "k", "sig", "s"));
            assertFault(Jsonb.writeObject(envelope), DescriptorFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("an unsupported canonicalization is refused (this build verifies exactly one)")
        void unsupportedCanonicalization() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(1);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR, descriptor);
            envelope.put(ServerDescriptorCodec.ENVELOPE_CANONICALIZATION, "JCS-RFC9999");
            envelope.put(ServerDescriptorCodec.ENVELOPE_SIGNATURE, Map.of("kid", "k", "sig", "s"));
            assertFault(Jsonb.writeObject(envelope), DescriptorFault.UNSUPPORTED_CANONICALIZATION);
        }

        @Test
        @DisplayName("a missing canonicalization is refused too (null is not the supported value)")
        void missingCanonicalization() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(1);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR, descriptor);
            envelope.put(ServerDescriptorCodec.ENVELOPE_SIGNATURE, Map.of("kid", "k", "sig", "s"));
            assertFault(Jsonb.writeObject(envelope), DescriptorFault.UNSUPPORTED_CANONICALIZATION);
        }

        @Test
        @DisplayName("an envelope with no signature block is MISSING_FIELD")
        void noSignature() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(1);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR, descriptor);
            envelope.put(ServerDescriptorCodec.ENVELOPE_CANONICALIZATION, ProvenanceEnvelope.JCS_RFC8785);
            assertFault(Jsonb.writeObject(envelope), DescriptorFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("a signature block missing kid or sig is MISSING_FIELD")
        void signatureMissingKidOrSig() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(1);
            // sig present, kid absent
            assertFault(
                    fixture.envelope(descriptor, Ed25519Signatures.JOSE_ALG, null, DescriptorFixture.DUMMY_SIG),
                    DescriptorFault.MISSING_FIELD);
            // kid present, sig absent
            assertFault(
                    fixture.envelope(descriptor, Ed25519Signatures.JOSE_ALG, DescriptorFixture.KID, null),
                    DescriptorFault.MISSING_FIELD);
        }
    }

    @Nested
    @DisplayName("base64url decode")
    class Base64Url {

        @Test
        @DisplayName("decodes a value the codec itself produced")
        void decodesOwnOutput() {
            byte[] original = "some bytes".getBytes(StandardCharsets.UTF_8);
            String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(original);
            assertThat(ServerDescriptorCodec.decodeBase64Url(encoded)).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects text that is not valid base64url")
        void rejectsBadBase64() {
            // The verifier relies on this throwing for a corrupt signature/key, so the throw is the
            // contract, not an accident.
            assertThatThrownBy(() -> ServerDescriptorCodec.decodeBase64Url("!!!not base64!!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ParsedEnvelope defensively copies the canonical bytes")
    class ParsedEnvelopeCopies {

        @Test
        @DisplayName("mutating the returned canonical bytes does not corrupt the record")
        void canonicalBytesAreCopied() {
            byte[] canonical = JsonCanonicalization.canonicalize(Jsonb.writeObject(fixture.validDescriptorMap(1)));
            ParsedEnvelope parsed = new ParsedEnvelope(fixture.validDescriptorMap(1), canonical, "EdDSA", "kid", "sig");

            byte[] first = parsed.canonicalBytes();
            first[0] ^= 0x7F;

            assertThat(parsed.canonicalBytes())
                    .as("the bytes the signature is checked against must not be reachable for mutation")
                    .isNotEqualTo(first);
        }
    }

    private static void assertFault(String rawEnvelope, DescriptorFault expected) {
        assertThatThrownBy(() -> ServerDescriptorCodec.parse(rawEnvelope, CAP))
                .isInstanceOf(DescriptorCodecException.class)
                .extracting(e -> ((DescriptorCodecException) e).fault())
                .isEqualTo(expected);
    }
}
