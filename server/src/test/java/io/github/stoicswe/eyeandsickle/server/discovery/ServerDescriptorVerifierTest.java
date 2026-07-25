package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.math.BigInteger;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServerDescriptorVerifier} — the trust boundary of the discovery layer.
 *
 * <p>A descriptor arrives from a server this one does not control, so the interesting behaviour is
 * refusal. These tests walk {@link DescriptorFault} end to end: every reason a peer can be turned away
 * is provoked and asserted, because a verifier that quietly accepts a malformed, oversized, wrongly-
 * signed, or replayed descriptor is exactly the hole the federation model is built to close.
 *
 * <p>Verification is clock-injected: {@code now} is a parameter, so nothing here reads a wall clock and
 * every result is reproducible. The one time comparison that exists — the transport-key validity window
 * — is exercised against fixed instants, and the ordering of descriptors is asserted (in
 * {@link PeerDirectoryServiceTest}) to be on the signed sequence, never on a self-claimed time.
 */
class ServerDescriptorVerifierTest {

    private final DescriptorFixture fixture = new DescriptorFixture();

    private static DiscoveryProperties defaults() {
        return new DiscoveryProperties(null, null, null, null, null, null, null, null, null, null);
    }

    private static DiscoveryProperties withMaxBytes(int maxBytes) {
        return new DiscoveryProperties(null, null, null, null, maxBytes, null, null, null, null, null);
    }

    private ServerDescriptorVerifier verifier() {
        return new ServerDescriptorVerifier(defaults(), fixture.resolver());
    }

    /** Builds a valid descriptor map, applies a mutation, and wraps it with a structurally-valid dummy signature. */
    private String tampered(Consumer<Map<String, Object>> mutation) {
        Map<String, Object> descriptor = fixture.validDescriptorMap(5);
        mutation.accept(descriptor);
        return fixture.envelopeWithDummySignature(descriptor);
    }

    // ==================================================================== acceptance

    @Nested
    @DisplayName("acceptance")
    class Acceptance {

        @Test
        @DisplayName("a well-formed, correctly-signed descriptor is accepted and its fields are mapped")
        void accepts() {
            String envelope = fixture.signed(7, List.of(ServerDescriptor.CAPABILITY_FEDERATION), null);

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            ServerDescriptor descriptor = result.descriptor();
            assertThat(descriptor.peerDid()).isEqualTo(DescriptorFixture.PEER_DID);
            assertThat(descriptor.endpointUrl()).isEqualTo(DescriptorFixture.ENDPOINT);
            assertThat(descriptor.sequenceNumber()).isEqualTo(7);
            assertThat(descriptor.capabilities()).containsExactly(ServerDescriptor.CAPABILITY_FEDERATION);
            // Stored verbatim: the exact received bytes, because the signature covers them.
            assertThat(descriptor.rawEnvelope()).isEqualTo(envelope);
            assertThat(X25519KeyExchange.encodePublicKey(descriptor.transportKey()))
                    .isEqualTo(X25519KeyExchange.encodePublicKey(fixture.transportKey));
        }

        @Test
        @DisplayName("a large but in-range sequence (Long.MAX_VALUE) is accepted")
        void acceptsLargeInRangeSequence() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(Long.MAX_VALUE);
            String envelope = fixture.envelopeSignedBy(descriptor, DescriptorFixture.KID, fixture.signing.getPrivate());

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.descriptor().sequenceNumber()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("a self-claimed future issuedAt grants nothing — the descriptor is accepted, no more, no less")
        void issuedAtIsNotAuthority() {
            // issuedAt is advisory; a peer setting it to the year 3000 must not gain any standing from it.
            // The only thing that orders descriptors is the signed sequence (see PeerDirectoryServiceTest).
            Instant farFuture = Instant.parse("3000-01-01T00:00:00Z");
            String envelope = fixture.signed(3, List.of(), farFuture);

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.descriptor().issuedAt()).isEqualTo(farFuture);
        }

        @Test
        @DisplayName("an unparseable issuedAt is dropped, not fatal — it never orders anything")
        void unparseableIssuedAtIsDropped() {
            // issuedAt is advisory only, so a garbage value must not fail an otherwise-valid descriptor.
            Map<String, Object> descriptor = fixture.validDescriptorMap(4);
            descriptor.put(ServerDescriptorCodec.ISSUED_AT, "definitely-not-a-timestamp");
            String envelope = fixture.envelopeSignedBy(descriptor, DescriptorFixture.KID, fixture.signing.getPrivate());

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.descriptor().issuedAt()).isNull();
        }

        @Test
        @DisplayName("capabilities are capped to 32 (an untrusted list cannot be an abuse vector)")
        void capabilitiesAreCapped() {
            List<String> many = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                many.add("cap-" + i);
            }
            String envelope = fixture.signed(1, many, null);

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.descriptor().capabilities()).hasSize(32);
        }

        @Test
        @DisplayName("an over-long capability string is dropped, and the rest are kept")
        void overLongCapabilityDropped() {
            String tooLong = "x".repeat(65); // MAX_CAPABILITY_LENGTH is 64
            List<String> caps = new ArrayList<>(List.of(tooLong, "federation", "validator"));
            String envelope = fixture.signed(1, caps, null);

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.descriptor().capabilities()).containsExactly("federation", "validator");
        }

        @Test
        @DisplayName("a transport key not-yet-valid but within the skew tolerance is accepted")
        void notBeforeWithinSkewIsAccepted() {
            // Self-hosted clocks legitimately disagree; a key that becomes valid one minute from now, with
            // a five-minute tolerance, is a normal directory entry, not a refusal.
            Instant notBefore = DescriptorFixture.NOW.plus(Duration.ofMinutes(1));
            Map<String, Object> descriptor = fixture.validDescriptorMap(2);
            descriptor.put(ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE, notBefore.toString());
            String envelope = fixture.envelopeSignedBy(descriptor, DescriptorFixture.KID, fixture.signing.getPrivate());

            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);

            assertThat(result.isAccepted()).isTrue();
        }
    }

    // ==================================================================== rejection — walk every fault

    @Nested
    @DisplayName("rejection — the whole DescriptorFault ladder")
    class Rejection {

        @Test
        @DisplayName("TOO_LARGE: an oversized envelope is refused before parsing")
        void tooLarge() {
            ServerDescriptorVerifier verifier = new ServerDescriptorVerifier(withMaxBytes(10), fixture.resolver());
            assertReject(verifier, fixture.signed(1), DescriptorFault.TOO_LARGE);
        }

        @Test
        @DisplayName("MALFORMED_JSON: bytes that are not a JSON object")
        void malformedJson() {
            assertReject("this is not json", DescriptorFault.MALFORMED_JSON);
        }

        @Test
        @DisplayName("MISSING_FIELD: no peerDid")
        void missingPeerDid() {
            assertReject(tampered(d -> d.remove(ServerDescriptorCodec.PEER_DID)), DescriptorFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_DID: peerDid is not DID-shaped (mirrors is_did)")
        void malformedDid() {
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.PEER_DID, "not-a-did")), DescriptorFault.MALFORMED_DID);
        }

        @Test
        @DisplayName("MISSING_FIELD: no endpoint")
        void missingEndpoint() {
            assertReject(tampered(d -> d.remove(ServerDescriptorCodec.ENDPOINT)), DescriptorFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_ENDPOINT: not an http(s) URL")
        void malformedEndpointScheme() {
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.ENDPOINT, "ftp://example.test")),
                    DescriptorFault.MALFORMED_ENDPOINT);
        }

        @Test
        @DisplayName("MALFORMED_ENDPOINT: over the length cap")
        void malformedEndpointTooLong() {
            String tooLong = "https://" + "a".repeat(2048) + ".test";
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.ENDPOINT, tooLong)), DescriptorFault.MALFORMED_ENDPOINT);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: missing")
        void missingSequence() {
            assertReject(tampered(d -> d.remove(ServerDescriptorCodec.SEQUENCE)), DescriptorFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: negative")
        void negativeSequence() {
            assertReject(tampered(d -> d.put(ServerDescriptorCodec.SEQUENCE, -5)), DescriptorFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: a fractional number is never a version counter")
        void fractionalSequence() {
            assertReject(tampered(d -> d.put(ServerDescriptorCodec.SEQUENCE, 1.5)), DescriptorFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MISSING_FIELD: no transport key")
        void missingTransportKey() {
            assertReject(
                    tampered(d -> d.remove(ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY)), DescriptorFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_TRANSPORT_KEY: not decodable base64url")
        void transportKeyNotBase64() {
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY, "!!!not base64!!!")),
                    DescriptorFault.MALFORMED_TRANSPORT_KEY);
        }

        @Test
        @DisplayName("MALFORMED_TRANSPORT_KEY: decodes but is the wrong length for X25519")
        void transportKeyWrongLength() {
            assertReject(
                    tampered(d -> d.put(
                            ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY, DescriptorFixture.base64Url(new byte[10]))),
                    DescriptorFault.MALFORMED_TRANSPORT_KEY);
        }

        @Test
        @DisplayName("MALFORMED_KEY_WINDOW: notAfter equals notBefore (a zero-width window)")
        void keyWindowZeroWidth() {
            assertReject(
                    tampered(d -> {
                        d.put(ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE, DescriptorFixture.NOW.toString());
                        d.put(ServerDescriptorCodec.TRANSPORT_KEY_NOT_AFTER, DescriptorFixture.NOW.toString());
                    }),
                    DescriptorFault.MALFORMED_KEY_WINDOW);
        }

        @Test
        @DisplayName("MALFORMED_KEY_WINDOW: notAfter before notBefore (inverted)")
        void keyWindowInverted() {
            assertReject(
                    tampered(d -> {
                        d.put(ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE, DescriptorFixture.NOW.toString());
                        d.put(
                                ServerDescriptorCodec.TRANSPORT_KEY_NOT_AFTER,
                                DescriptorFixture.NOW.minus(Duration.ofDays(1)).toString());
                    }),
                    DescriptorFault.MALFORMED_KEY_WINDOW);
        }

        @Test
        @DisplayName("MALFORMED_KEY_WINDOW: an unparseable instant")
        void keyWindowUnparseable() {
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE, "not-a-date")),
                    DescriptorFault.MALFORMED_KEY_WINDOW);
        }

        @Test
        @DisplayName("NOT_YET_VALID: notBefore is beyond the tolerated clock skew")
        void notYetValid() {
            // A descriptor whose key does not become valid until a day from now, with a five-minute
            // tolerance, is not worth storing as current.
            assertReject(
                    tampered(d -> d.put(
                            ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE,
                            DescriptorFixture.NOW.plus(Duration.ofDays(1)).toString())),
                    DescriptorFault.NOT_YET_VALID);
        }

        @Test
        @DisplayName("NOT_YET_VALID does NOT let a peer win by claiming a far-future key: it is refused, not preferred")
        void futureKeyIsRefusedNotRewarded() {
            // The anti-clock property from the verifier's side: a future time is a reason to refuse, never
            // a reason to treat the descriptor as 'newest'.
            String envelope = tampered(d -> d.put(
                    ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE,
                    Instant.parse("3000-01-01T00:00:00Z").toString()));
            DescriptorVerification result = verifier().verify(envelope, DescriptorFixture.NOW);
            assertThat(result.isAccepted()).isFalse();
            assertThat(result.fault()).isEqualTo(DescriptorFault.NOT_YET_VALID);
        }

        @Test
        @DisplayName("SIGNER_NOT_OWNER: the signing kid belongs to a different DID than the descriptor")
        void signerNotOwner() {
            // A server may only speak for itself. A descriptor for PEER_DID signed under OTHER_DID's key is
            // one server trying to publish another's contact card.
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            String envelope = fixture.envelope(
                    descriptor,
                    Ed25519Signatures.JOSE_ALG,
                    DescriptorFixture.OTHER_DID + "#key1",
                    DescriptorFixture.DUMMY_SIG);
            assertReject(envelope, DescriptorFault.SIGNER_NOT_OWNER);
        }

        @Test
        @DisplayName("WRONG_SIGNATURE_ALGORITHM: anything but EdDSA")
        void wrongAlgorithm() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            String envelope = fixture.envelope(descriptor, "ES256", DescriptorFixture.KID, DescriptorFixture.DUMMY_SIG);
            assertReject(envelope, DescriptorFault.WRONG_SIGNATURE_ALGORITHM);
        }

        @Test
        @DisplayName("UNKNOWN_SIGNING_KEY: the kid resolves to no key (distinct from a key that then fails)")
        void unknownSigningKey() {
            // Distinguishing 'unknown' from 'invalid' is deliberate: it says whether a peer is unheard-of
            // or lying. The closed default — a resolver that resolves nothing — refuses every descriptor.
            ServerDescriptorVerifier verifier = new ServerDescriptorVerifier(defaults(), PeerKeyResolver.empty());
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            String envelope = fixture.envelope(
                    descriptor, Ed25519Signatures.JOSE_ALG, DescriptorFixture.KID, DescriptorFixture.DUMMY_SIG);
            assertReject(verifier, envelope, DescriptorFault.UNKNOWN_SIGNING_KEY);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: the sig field is not decodable base64url")
        void signatureNotDecodable() {
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            String envelope = fixture.envelope(descriptor, Ed25519Signatures.JOSE_ALG, DescriptorFixture.KID, "!!!");
            assertReject(envelope, DescriptorFault.INVALID_SIGNATURE);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: signed by a key other than the one the kid resolves to")
        void signedByWrongKey() {
            KeyPair impostor = Ed25519Signatures.generateKeyPair();
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            // The kid maps (in the resolver) to fixture.signing, but this envelope was signed by impostor.
            String envelope = fixture.envelopeSignedBy(descriptor, DescriptorFixture.KID, impostor.getPrivate());
            assertReject(envelope, DescriptorFault.INVALID_SIGNATURE);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: a field is altered after signing")
        void tamperedAfterSigning() {
            // Keep a genuine signature, then rewrite the endpoint it vouches for — the classic downgrade of
            // "sign a benign record, ship a malicious one".
            Map<String, Object> descriptor = fixture.validDescriptorMap(5);
            String signed = fixture.envelopeSignedBy(descriptor, DescriptorFixture.KID, fixture.signing.getPrivate());

            Map<String, Object> envelope = Jsonb.readObject(signed);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedDescriptor =
                    (Map<String, Object>) envelope.get(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR);
            parsedDescriptor.put(ServerDescriptorCodec.ENDPOINT, "https://attacker.example.evil");
            String rebuilt = Jsonb.writeObject(envelope);

            assertReject(rebuilt, DescriptorFault.INVALID_SIGNATURE);
        }
    }

    // ==================================================================== known limitation (BUG)

    @Nested
    @DisplayName("known limitation: an out-of-long-range sequence throws instead of being a typed refusal")
    class OutOfRangeSequence {

        @Test
        @DisplayName("a sequence larger than Long.MAX is refused as MALFORMED_SEQUENCE, never thrown")
        void oversizedIntegerSequenceIsRefused() {
            // Regression guard for a real defect the test suite surfaced: ServerDescriptorVerifier.integer()
            // once called BigInteger.longValueExact() unguarded, so an attacker-supplied integer sequence
            // beyond Long's range escaped verify() as an unchecked ArithmeticException — a crash into the
            // gossip round from untrusted input, breaking the "an untrusted descriptor never throws"
            // contract (compare Ed25519Signatures.verify, which is explicitly never-throws). Now it is a
            // typed refusal like every other malformation.
            assertReject(
                    tampered(d -> d.put(ServerDescriptorCodec.SEQUENCE, new BigInteger("9".repeat(40)))),
                    DescriptorFault.MALFORMED_SEQUENCE);
        }
    }

    // ==================================================================== helpers

    private void assertReject(String envelope, DescriptorFault expected) {
        assertReject(verifier(), envelope, expected);
    }

    private void assertReject(ServerDescriptorVerifier verifier, String envelope, DescriptorFault expected) {
        DescriptorVerification result = verifier.verify(envelope, DescriptorFixture.NOW);
        assertThat(result.isAccepted())
                .as("expected refusal with fault %s", expected)
                .isFalse();
        assertThat(result.descriptor()).isNull();
        assertThat(result.asDescriptor()).isEmpty();
        assertThat(result.fault()).isEqualTo(expected);
    }
}
