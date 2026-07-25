package io.github.stoicswe.eyeandsickle.server.directory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CharacterHomeRecordVerifier} — the trust boundary of the character directory.
 *
 * <p>A published home record arrives from a home server this one does not control, so the interesting
 * behaviour is refusal. These tests walk {@link CharacterHomeFault} end to end: every reason a binding
 * can be turned away is provoked and asserted, because a verifier that quietly accepts a malformed,
 * oversized, wrongly-signed, or forged binding is exactly the hole the federation model is built to
 * close. The number-overflow and bad-base64 cases that a naive parser would <em>throw</em> on are
 * asserted to be typed refusals instead — an untrusted record never throws out of {@code verify}.
 */
class CharacterHomeRecordVerifierTest {

    private final CharacterHomeFixture fixture = new CharacterHomeFixture();

    private static CharacterDirectoryProperties defaults() {
        return new CharacterDirectoryProperties(null, null, null);
    }

    private static CharacterDirectoryProperties withMaxBytes(int maxBytes) {
        return new CharacterDirectoryProperties(maxBytes, null, null);
    }

    private CharacterHomeRecordVerifier verifier() {
        return new CharacterHomeRecordVerifier(defaults(), fixture.resolver());
    }

    /** Builds a valid field map, applies a mutation, and serializes it. */
    private String tampered(Consumer<Map<String, Object>> mutation) {
        Map<String, Object> fields = fixture.fieldMap(5);
        mutation.accept(fields);
        return fixture.envelope(fields);
    }

    // ==================================================================== acceptance

    @Nested
    @DisplayName("acceptance")
    class Acceptance {

        @Test
        @DisplayName("a well-formed, correctly-signed record is accepted and its fields are mapped")
        void accepts() {
            String envelope = fixture.signed(7);

            CharacterHomeVerification result = verifier().verify(envelope);

            assertThat(result.isAccepted()).isTrue();
            CharacterHomeRecord record = result.record();
            assertThat(record.accountDid()).isEqualTo(CharacterHomeFixture.ACCOUNT_DID);
            assertThat(record.characterId()).isEqualTo(CharacterHomeFixture.CHARACTER_ID);
            assertThat(record.slot()).isEqualTo(CharacterHomeFixture.SLOT);
            assertThat(record.homeServerDid()).isEqualTo(CharacterHomeFixture.HOME_DID);
            assertThat(record.signingKeyId()).isEqualTo(CharacterHomeFixture.KID);
            assertThat(record.homeEndpoint()).isEqualTo(CharacterHomeFixture.ENDPOINT);
            assertThat(record.sequenceNumber()).isEqualTo(7);
        }

        @Test
        @DisplayName("a large but in-range sequence (Long.MAX_VALUE) is accepted")
        void acceptsLargeInRangeSequence() {
            CharacterHomeRecord record = fixture.record(
                    CharacterHomeFixture.CHARACTER_ID,
                    CharacterHomeFixture.SLOT,
                    Long.MAX_VALUE,
                    fixture.signing.getPrivate());
            String envelope = CharacterHomeCodec.writeEnvelope(record);

            CharacterHomeVerification result = verifier().verify(envelope);

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.record().sequenceNumber()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("the highest structural slot (16) is accepted")
        void acceptsHighestSlot() {
            CharacterHomeRecord record =
                    fixture.record(CharacterHomeFixture.CHARACTER_ID, 16, 1, fixture.signing.getPrivate());
            CharacterHomeVerification result = verifier().verify(CharacterHomeCodec.writeEnvelope(record));
            assertThat(result.isAccepted()).isTrue();
            assertThat(result.record().slot()).isEqualTo(16);
        }
    }

    // ==================================================================== rejection — walk every fault

    @Nested
    @DisplayName("rejection — the whole CharacterHomeFault ladder")
    class Rejection {

        @Test
        @DisplayName("TOO_LARGE: an oversized record is refused before parsing")
        void tooLarge() {
            CharacterHomeRecordVerifier verifier =
                    new CharacterHomeRecordVerifier(withMaxBytes(10), fixture.resolver());
            assertReject(verifier, fixture.signed(1), CharacterHomeFault.TOO_LARGE);
        }

        @Test
        @DisplayName("MALFORMED_JSON: bytes that are not a JSON object")
        void malformedJson() {
            assertReject("this is not json", CharacterHomeFault.MALFORMED_JSON);
        }

        @Test
        @DisplayName("MISSING_FIELD: no accountDid")
        void missingAccountDid() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.ACCOUNT_DID)), CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_ACCOUNT_DID: accountDid is not DID-shaped")
        void malformedAccountDid() {
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.ACCOUNT_DID, "not-a-did")),
                    CharacterHomeFault.MALFORMED_ACCOUNT_DID);
        }

        @Test
        @DisplayName("MISSING_FIELD: no homeServerDid")
        void missingHomeDid() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.HOME_SERVER_DID)), CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_HOME_DID: homeServerDid is not DID-shaped")
        void malformedHomeDid() {
            // Keep the signing kid consistent so the home-DID shape check is what fires, not SIGNER_NOT_HOME.
            assertReject(
                    tampered(f -> {
                        f.put(CharacterHomeCodec.HOME_SERVER_DID, "not-a-did");
                        f.put(CharacterHomeCodec.SIGNING_KEY_ID, "not-a-did#key1");
                    }),
                    CharacterHomeFault.MALFORMED_HOME_DID);
        }

        @Test
        @DisplayName("MALFORMED_CHARACTER_ID: characterId is not a UUID")
        void malformedCharacterId() {
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.CHARACTER_ID, "not-a-uuid")),
                    CharacterHomeFault.MALFORMED_CHARACTER_ID);
        }

        @Test
        @DisplayName("MALFORMED_SLOT: missing")
        void missingSlot() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.SLOT)), CharacterHomeFault.MALFORMED_SLOT);
        }

        @Test
        @DisplayName("MALFORMED_SLOT: zero is not a slot")
        void zeroSlot() {
            assertReject(tampered(f -> f.put(CharacterHomeCodec.SLOT, 0)), CharacterHomeFault.MALFORMED_SLOT);
        }

        @Test
        @DisplayName("MALFORMED_SLOT: above the structural bound (17)")
        void slotTooLarge() {
            assertReject(tampered(f -> f.put(CharacterHomeCodec.SLOT, 17)), CharacterHomeFault.MALFORMED_SLOT);
        }

        @Test
        @DisplayName("MALFORMED_SLOT: a fractional number is never a slot")
        void fractionalSlot() {
            assertReject(tampered(f -> f.put(CharacterHomeCodec.SLOT, 1.5)), CharacterHomeFault.MALFORMED_SLOT);
        }

        @Test
        @DisplayName("MISSING_FIELD: no endpoint")
        void missingEndpoint() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.HOME_ENDPOINT)), CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_ENDPOINT: not an http(s) URL")
        void malformedEndpointScheme() {
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.HOME_ENDPOINT, "ftp://example.test")),
                    CharacterHomeFault.MALFORMED_ENDPOINT);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: missing")
        void missingSequence() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.SEQUENCE)), CharacterHomeFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: negative")
        void negativeSequence() {
            assertReject(tampered(f -> f.put(CharacterHomeCodec.SEQUENCE, -5)), CharacterHomeFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: a fractional number is never a version counter")
        void fractionalSequence() {
            assertReject(tampered(f -> f.put(CharacterHomeCodec.SEQUENCE, 1.5)), CharacterHomeFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MALFORMED_SEQUENCE: an out-of-long-range integer is a typed refusal, never thrown")
        void oversizedSequenceIsRefused() {
            // Regression guard against the exact defect the discovery verifier once had: an attacker-supplied
            // integer beyond Long's range must not escape verify() as an unchecked ArithmeticException.
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.SEQUENCE, new BigInteger("9".repeat(40)))),
                    CharacterHomeFault.MALFORMED_SEQUENCE);
        }

        @Test
        @DisplayName("MISSING_FIELD: no signingKeyId")
        void missingSigningKeyId() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.SIGNING_KEY_ID)), CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MISSING_FIELD: no transport key")
        void missingTransportKey() {
            assertReject(
                    tampered(f -> f.remove(CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY)),
                    CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("MALFORMED_TRANSPORT_KEY: not decodable base64url")
        void transportKeyNotBase64() {
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY, "!!!not base64!!!")),
                    CharacterHomeFault.MALFORMED_TRANSPORT_KEY);
        }

        @Test
        @DisplayName("MALFORMED_TRANSPORT_KEY: decodes but is the wrong length for X25519")
        void transportKeyWrongLength() {
            assertReject(
                    tampered(f -> f.put(
                            CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY,
                            CharacterHomeFixture.base64Url(new byte[10]))),
                    CharacterHomeFault.MALFORMED_TRANSPORT_KEY);
        }

        @Test
        @DisplayName("MISSING_FIELD: no signature")
        void missingSignature() {
            assertReject(tampered(f -> f.remove(CharacterHomeCodec.SIGNATURE)), CharacterHomeFault.MISSING_FIELD);
        }

        @Test
        @DisplayName("SIGNER_NOT_HOME: the signing kid belongs to a different DID than the home server")
        void signerNotHome() {
            // A home server may only speak for itself. A binding homed at HOME_DID but signed under another
            // home's key is one server trying to publish another's binding.
            assertReject(
                    tampered(f ->
                            f.put(CharacterHomeCodec.SIGNING_KEY_ID, CharacterHomeFixture.OTHER_HOME_DID + "#key1")),
                    CharacterHomeFault.SIGNER_NOT_HOME);
        }

        @Test
        @DisplayName("UNKNOWN_SIGNING_KEY: the kid resolves to no key (distinct from a key that then fails)")
        void unknownSigningKey() {
            CharacterHomeRecordVerifier verifier =
                    new CharacterHomeRecordVerifier(defaults(), CharacterHomeKeyResolver.empty());
            assertReject(verifier, fixture.signed(5), CharacterHomeFault.UNKNOWN_SIGNING_KEY);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: the sig field is not decodable base64url")
        void signatureNotDecodable() {
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.SIGNATURE, "!!!")), CharacterHomeFault.INVALID_SIGNATURE);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: signed by a key other than the one the kid resolves to")
        void signedByWrongKey() {
            // The kid maps (in the resolver) to fixture.signing, but this record was signed by an impostor.
            KeyPair impostor = Ed25519Signatures.generateKeyPair();
            CharacterHomeRecord record = fixture.record(
                    CharacterHomeFixture.CHARACTER_ID, CharacterHomeFixture.SLOT, 5, impostor.getPrivate());
            assertReject(CharacterHomeCodec.writeEnvelope(record), CharacterHomeFault.INVALID_SIGNATURE);
        }

        @Test
        @DisplayName("INVALID_SIGNATURE: the endpoint is rewritten after signing")
        void tamperedAfterSigning() {
            // Keep a genuine signature, then rewrite the endpoint it vouches for — sign-benign, ship-malicious.
            assertReject(
                    tampered(f -> f.put(CharacterHomeCodec.HOME_ENDPOINT, "https://attacker.example.evil")),
                    CharacterHomeFault.INVALID_SIGNATURE);
        }
    }

    // ==================================================================== helpers

    private void assertReject(String envelope, CharacterHomeFault expected) {
        assertReject(verifier(), envelope, expected);
    }

    private void assertReject(CharacterHomeRecordVerifier verifier, String envelope, CharacterHomeFault expected) {
        CharacterHomeVerification result = verifier.verify(envelope);
        assertThat(result.isAccepted())
                .as("expected refusal with fault %s", expected)
                .isFalse();
        assertThat(result.record()).isNull();
        assertThat(result.asRecord()).isEmpty();
        assertThat(result.fault()).isEqualTo(expected);
    }
}
