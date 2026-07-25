package io.github.stoicswe.eyeandsickle.protocol.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransportKeyAttestation} — the bridge from an AT Protocol DID to the X25519 key a
 * session is actually established with.
 *
 * <p>This record is the only thing standing between "someone answered on that port" and "that DID is
 * at the other end" ({@code docs/architecture/07-transport-security.md} §4.1). Every property it
 * claims is tested here as an attack: a signature that transfers to a different field split, a window
 * that is honoured at one edge and not the other, an array a caller can mutate out from under a
 * verified record. {@link SecureChannelTest} exercises the same record end-to-end; this file pins the
 * pieces individually so a regression names itself.
 */
class TransportKeyAttestationTest {

    private static final String DID = "did:plc:eye00000000000000";
    private static final String KEY_ID = DID + "#transport-1";
    private static final String NOT_BEFORE = "2026-07-01T00:00:00Z";
    private static final String NOT_AFTER = "2026-08-01T00:00:00Z";
    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    /** Domain-separation prefix, mirrored from the private constant it must keep matching. */
    private static final String CONTEXT = "eyeandsickle/transport-key-attestation/v1";

    private final KeyPair didKeys = Ed25519Signatures.generateKeyPair();
    private final KeyPair transportKeys = X25519KeyExchange.generateKeyPair();

    private TransportKeyAttestation signed() {
        return signed(NOT_BEFORE, NOT_AFTER);
    }

    private TransportKeyAttestation signed(String notBefore, String notAfter) {
        return TransportKeyAttestation.sign(
                DID, KEY_ID, transportKeys.getPublic(), notBefore, notAfter, didKeys.getPrivate());
    }

    private PublicKey didKey() {
        return didKeys.getPublic();
    }

    private byte[] encodedTransportKey() {
        return X25519KeyExchange.encodePublicKey(transportKeys.getPublic());
    }

    // ------------------------------------------------------------------ signing

    @Nested
    @DisplayName("signing and verification")
    class SigningAndVerification {

        @Test
        @DisplayName("an attestation verifies against the DID key that signed it")
        void signedAttestationVerifies() {
            TransportKeyAttestation attestation = signed();

            assertThat(attestation.did()).isEqualTo(DID);
            assertThat(attestation.keyId()).isEqualTo(KEY_ID);
            assertThat(attestation.transportPublicKey()).isEqualTo(encodedTransportKey());
            assertThat(attestation.isValidAt(didKey(), NOW)).isTrue();
        }

        @Test
        @DisplayName("the attested key decodes back to the key that was attested")
        void attestedKeyDecodes() {
            assertThat(X25519KeyExchange.encodePublicKey(signed().transportKey()))
                    .isEqualTo(encodedTransportKey());
        }

        @Test
        @DisplayName("another DID's key does not validate this attestation")
        void wrongDidKeyIsRejected() {
            // Defends against the whole point of the record: if any DID key validated it, the
            // attestation would prove nothing about who owns the transport key.
            KeyPair otherDid = Ed25519Signatures.generateKeyPair();
            assertThat(signed().isValidAt(otherDid.getPublic(), NOW)).isFalse();
        }

        @Test
        @DisplayName("a bit-flipped signature is rejected")
        void corruptedSignatureIsRejected() {
            // Defends against: an in-flight edit to the signature bytes being ignored rather than fatal.
            TransportKeyAttestation original = signed();
            byte[] signature = original.signature();
            signature[0] ^= 0x01;

            TransportKeyAttestation tampered = new TransportKeyAttestation(
                    original.did(),
                    original.keyId(),
                    original.transportPublicKey(),
                    original.notBefore(),
                    original.notAfter(),
                    signature);
            assertThat(tampered.isValidAt(didKey(), NOW)).isFalse();
        }

        @Test
        @DisplayName("the signing input is domain-separated from every other signature in the game")
        void signingInputIsDomainSeparated() {
            // ISO-8859-1 so the search is byte-for-byte and cannot be perturbed by UTF-8 decoding.
            String bytes = new String(
                    TransportKeyAttestation.signingBytes(DID, KEY_ID, encodedTransportKey(), NOT_BEFORE, NOT_AFTER),
                    StandardCharsets.ISO_8859_1);
            assertThat(bytes).contains(CONTEXT);
        }

        @Test
        @DisplayName("a signature over the same fields without the context prefix does not validate")
        void contextlessSignatureIsRejected() {
            // Defends against cross-protocol replay: provenance envelopes are signed with the same
            // Ed25519 DID key (docs/architecture/04-item-provenance.md §5). Without the context
            // prefix, a signature harvested from one subsystem could be presented to the other.
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeString(DID);
            writer.writeString(KEY_ID);
            writer.writeBytes(encodedTransportKey());
            writer.writeString(NOT_BEFORE);
            writer.writeString(NOT_AFTER);
            byte[] contextless = Ed25519Signatures.sign(didKeys.getPrivate(), writer.toByteArray());

            TransportKeyAttestation forged =
                    new TransportKeyAttestation(DID, KEY_ID, encodedTransportKey(), NOT_BEFORE, NOT_AFTER, contextless);
            assertThat(forged.isValidAt(didKey(), NOW)).isFalse();
        }
    }

    // ------------------------------------------------------------------ tampering

    @Nested
    @DisplayName("field tampering")
    class FieldTampering {

        /** Keeps a genuine signature and swaps exactly one field, which is what an attacker gets to do. */
        private TransportKeyAttestation withFields(
                String did, String keyId, byte[] transportPublicKey, String notBefore, String notAfter) {
            return new TransportKeyAttestation(
                    did, keyId, transportPublicKey, notBefore, notAfter, signed().signature());
        }

        @Test
        @DisplayName("a swapped DID is rejected")
        void tamperedDidIsRejected() {
            // Defends against: claiming someone else's identity while presenting a real signature.
            assertThat(withFields("did:plc:attacker00000000", KEY_ID, encodedTransportKey(), NOT_BEFORE, NOT_AFTER)
                            .isValidAt(didKey(), NOW))
                    .isFalse();
        }

        @Test
        @DisplayName("a swapped key id is rejected")
        void tamperedKeyIdIsRejected() {
            // Defends against: pointing the attestation at a different key fragment of the same DID.
            assertThat(withFields(DID, DID + "#transport-2", encodedTransportKey(), NOT_BEFORE, NOT_AFTER)
                            .isValidAt(didKey(), NOW))
                    .isFalse();
        }

        @Test
        @DisplayName("a swapped transport key is rejected")
        void tamperedTransportKeyIsRejected() {
            // Defends against the attack the record exists to stop: substituting a key the attacker
            // holds the private half of, under a DID they do not control.
            byte[] attackerKey = X25519KeyExchange.encodePublicKey(
                    X25519KeyExchange.generateKeyPair().getPublic());

            assertThat(withFields(DID, KEY_ID, attackerKey, NOT_BEFORE, NOT_AFTER)
                            .isValidAt(didKey(), NOW))
                    .isFalse();
        }

        @Test
        @DisplayName("a moved notBefore is rejected")
        void tamperedNotBeforeIsRejected() {
            // Defends against: back-dating a key into a window in which it was not yet attested.
            assertThat(withFields(DID, KEY_ID, encodedTransportKey(), "2020-01-01T00:00:00Z", NOT_AFTER)
                            .isValidAt(didKey(), NOW))
                    .isFalse();
        }

        @Test
        @DisplayName("a moved notAfter is rejected")
        void tamperedNotAfterIsRejected() {
            // Defends against the most valuable edit of the five: extending a compromised transport
            // key's life. Short windows are the only bound on damage until T-3 settles revocation
            // (docs/architecture/07-transport-security.md §6).
            assertThat(withFields(DID, KEY_ID, encodedTransportKey(), NOT_BEFORE, "2099-01-01T00:00:00Z")
                            .isValidAt(didKey(), NOW))
                    .isFalse();
        }
    }

    // ------------------------------------------------------------------ validity window

    /**
     * <strong>[PROPOSAL]</strong> — {@code docs/architecture/07-transport-security.md} §4.1 says an
     * attestation is "valid from T1 to T2" and does not say which edges are included. These tests pin
     * the implementation's answer, {@code [notBefore, notAfter)}, because back-to-back rotations need
     * a definite answer to avoid a one-instant gap or overlap. Needs deciding, not just testing.
     */
    @Nested
    @DisplayName("validity window")
    class ValidityWindow {

        @Test
        @DisplayName("the window is inclusive at notBefore")
        void validAtTheOpeningInstant() {
            // Pinned deliberately: the two edges are asymmetric, and a reader who assumes otherwise
            // would introduce a one-instant gap or overlap when rotating keys back-to-back.
            assertThat(signed().isValidAt(didKey(), Instant.parse(NOT_BEFORE))).isTrue();
        }

        @Test
        @DisplayName("one nanosecond before the window it is invalid")
        void invalidJustBeforeTheOpeningInstant() {
            assertThat(signed().isValidAt(didKey(), Instant.parse(NOT_BEFORE).minusNanos(1)))
                    .isFalse();
        }

        @Test
        @DisplayName("the window is exclusive at notAfter")
        void invalidAtTheExpiryInstant() {
            // The expiry instant itself is already outside. A short window is the entire mitigation
            // for a stolen transport key, so the boundary belongs to the closed side.
            assertThat(signed().isValidAt(didKey(), Instant.parse(NOT_AFTER))).isFalse();
        }

        @Test
        @DisplayName("one nanosecond before expiry it is still valid")
        void validJustBeforeExpiry() {
            assertThat(signed().isValidAt(didKey(), Instant.parse(NOT_AFTER).minusNanos(1)))
                    .isTrue();
        }

        @Test
        @DisplayName("long after expiry it is invalid")
        void invalidAfterExpiry() {
            assertThat(signed().isValidAt(didKey(), Instant.parse("2027-01-01T00:00:00Z")))
                    .isFalse();
        }

        @Test
        @DisplayName("an unparseable notBefore is rejected even though the signature is genuine")
        void unparseableNotBeforeIsRejected() {
            // Defends against fail-open: the signature over "whenever" is perfectly valid, so a
            // verifier that only checked the signature would accept a window it cannot evaluate.
            TransportKeyAttestation attestation = signed("whenever", NOT_AFTER);
            assertThat(Ed25519Signatures.verify(
                            didKey(),
                            TransportKeyAttestation.signingBytes(
                                    DID, KEY_ID, encodedTransportKey(), "whenever", NOT_AFTER),
                            attestation.signature()))
                    .as("the signature itself must be genuine, or this proves nothing")
                    .isTrue();
            assertThat(attestation.isValidAt(didKey(), NOW)).isFalse();
        }

        @Test
        @DisplayName("an unparseable notAfter is rejected")
        void unparseableNotAfterIsRejected() {
            assertThat(signed(NOT_BEFORE, "never").isValidAt(didKey(), NOW)).isFalse();
        }

        @Test
        @DisplayName("a date without a time is rejected rather than guessed at")
        void dateOnlyTimestampIsRejected() {
            // Defends against a verifier that fills in a missing time-of-day — which would make the
            // window's edges depend on the verifying machine rather than on what was signed.
            assertThat(signed("2026-07-01", NOT_AFTER).isValidAt(didKey(), NOW)).isFalse();
        }

        @Test
        @DisplayName("an inverted window is never valid")
        void invertedWindowIsNeverValid() {
            // notAfter before notBefore: nothing rejects it at signing time, so pin that it simply
            // never opens rather than always opening.
            TransportKeyAttestation inverted = signed(NOT_AFTER, NOT_BEFORE);
            assertThat(inverted.isValidAt(didKey(), NOW)).isFalse();
            assertThat(inverted.isValidAt(didKey(), Instant.parse(NOT_BEFORE))).isFalse();
            assertThat(inverted.isValidAt(didKey(), Instant.parse(NOT_AFTER))).isFalse();
        }
    }

    // ------------------------------------------------------------------ unambiguous signing input

    @Nested
    @DisplayName("unambiguous signing input")
    class UnambiguousSigningInput {

        @Test
        @DisplayName("a moved field boundary changes the signing bytes")
        void fieldBoundariesChangeTheSigningBytes() {
            // "alice" + "bob" and "ali" + "cebob" concatenate to the same characters. Without length
            // prefixes they would produce identical signing input — see the record's own Javadoc.
            byte[] left =
                    TransportKeyAttestation.signingBytes("alice", "bob", encodedTransportKey(), NOT_BEFORE, NOT_AFTER);
            byte[] right =
                    TransportKeyAttestation.signingBytes("ali", "cebob", encodedTransportKey(), NOT_BEFORE, NOT_AFTER);

            assertThat(left).isNotEqualTo(right);
        }

        @Test
        @DisplayName("a signature does not transfer across a moved field boundary")
        void signatureDoesNotTransferAcrossAMovedBoundary() {
            // Defends against a real identity forgery: if the boundary were invisible to the
            // signature, holding a signed attestation for one DID would hand you a valid one for
            // another whose DID and key id happen to concatenate the same way.
            TransportKeyAttestation alice = TransportKeyAttestation.sign(
                    "alice", "bob", transportKeys.getPublic(), NOT_BEFORE, NOT_AFTER, didKeys.getPrivate());
            TransportKeyAttestation shifted = new TransportKeyAttestation(
                    "ali", "cebob", encodedTransportKey(), NOT_BEFORE, NOT_AFTER, alice.signature());

            assertThat(alice.isValidAt(didKey(), NOW)).isTrue();
            assertThat(shifted.isValidAt(didKey(), NOW)).isFalse();
        }

        @Test
        @DisplayName("the timestamp boundary is unambiguous too")
        void timestampBoundariesAreUnambiguous() {
            // The same argument applies to every adjacent pair of fields, not just the first two.
            assertThat(TransportKeyAttestation.signingBytes(DID, KEY_ID, encodedTransportKey(), "2026", "-07"))
                    .isNotEqualTo(
                            TransportKeyAttestation.signingBytes(DID, KEY_ID, encodedTransportKey(), "2026-", "07"));
        }
    }

    // ------------------------------------------------------------------ defensive copies

    @Nested
    @DisplayName("defensive copies")
    class DefensiveCopies {

        @Test
        @DisplayName("mutating the arrays passed in does not change the record")
        void constructorCopiesItsArrays() {
            // Defends against a time-of-check/time-of-use hole: a caller (or a shared buffer) that
            // could edit the key bytes after verification would make "verified" meaningless.
            byte[] key = encodedTransportKey();
            byte[] signature = signed().signature();
            TransportKeyAttestation attestation =
                    new TransportKeyAttestation(DID, KEY_ID, key, NOT_BEFORE, NOT_AFTER, signature);

            Arrays.fill(key, (byte) 0);
            Arrays.fill(signature, (byte) 0);

            assertThat(attestation.transportPublicKey()).isEqualTo(encodedTransportKey());
            assertThat(attestation.isValidAt(didKey(), NOW)).isTrue();
        }

        @Test
        @DisplayName("mutating the arrays handed out does not change the record")
        void accessorsCopyTheirArrays() {
            TransportKeyAttestation attestation = signed();
            byte[] handedOutKey = attestation.transportPublicKey();
            byte[] handedOutSignature = attestation.signature();

            Arrays.fill(handedOutKey, (byte) 0);
            Arrays.fill(handedOutSignature, (byte) 0);

            assertThat(attestation.transportPublicKey()).isEqualTo(encodedTransportKey());
            assertThat(attestation.signature()).isNotEqualTo(handedOutSignature);
            assertThat(attestation.isValidAt(didKey(), NOW)).isTrue();
        }

        @Test
        @DisplayName("each accessor call hands out a distinct array")
        void accessorsDoNotShareState() {
            TransportKeyAttestation attestation = signed();
            assertThat(attestation.signature()).isNotSameAs(attestation.signature());
            assertThat(attestation.transportPublicKey()).isNotSameAs(attestation.transportPublicKey());
        }
    }

    // ------------------------------------------------------------------ wire encoding

    @Nested
    @DisplayName("wire encoding")
    class WireEncoding {

        @Test
        @DisplayName("an attestation survives a round trip through the wire, still verifiable")
        void encodeDecodeRoundTrip() {
            TransportKeyAttestation original = signed();
            TransportKeyAttestation decoded = TransportKeyAttestation.decode(original.encode());

            assertThat(decoded.did()).isEqualTo(original.did());
            assertThat(decoded.keyId()).isEqualTo(original.keyId());
            assertThat(decoded.transportPublicKey()).isEqualTo(original.transportPublicKey());
            assertThat(decoded.notBefore()).isEqualTo(original.notBefore());
            assertThat(decoded.notAfter()).isEqualTo(original.notAfter());
            assertThat(decoded.signature()).isEqualTo(original.signature());
            // The only assertion that really matters: it still proves what it proved before.
            assertThat(decoded.isValidAt(didKey(), NOW)).isTrue();
        }

        @Test
        @DisplayName("re-encoding a decoded attestation reproduces the same bytes")
        void encodingIsStable() {
            // The handshake hashes these bytes into its transcript, so two peers must agree on them
            // exactly (docs/architecture/07-transport-security.md §4.2).
            byte[] encoded = signed().encode();
            assertThat(TransportKeyAttestation.decode(encoded).encode()).isEqualTo(encoded);
        }

        @Test
        @DisplayName("a truncated encoding is rejected")
        void truncatedEncodingIsRejected() {
            // Defends against: a short read being parsed as a valid attestation with a short signature.
            byte[] encoded = signed().encode();
            byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);

            assertThatThrownBy(() -> TransportKeyAttestation.decode(truncated))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("trailing bytes after an otherwise valid encoding are rejected")
        void trailingBytesAreRejected() {
            // Defends against transcript confusion: appended bytes that parse away but change the
            // hash would let one message mean two things to the two peers.
            byte[] encoded = signed().encode();
            byte[] withTail = Arrays.copyOf(encoded, encoded.length + 1);

            assertThatThrownBy(() -> TransportKeyAttestation.decode(withTail))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an encoding with too few fields is rejected")
        void tooFewFieldsIsRejected() {
            // Defends against: a short message leaving later fields empty instead of failing.
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeString(DID);
            writer.writeString(KEY_ID);
            writer.writeBytes(encodedTransportKey());

            assertThatThrownBy(() -> TransportKeyAttestation.decode(writer.toByteArray()))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("empty and null encodings are rejected")
        void emptyAndNullEncodingsAreRejected() {
            // Defends against: an empty or absent buffer producing an all-defaults attestation.
            assertThatThrownBy(() -> TransportKeyAttestation.decode(new byte[0]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> TransportKeyAttestation.decode(null))
                    .isInstanceOf(SecureChannelException.class)
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("garbage is rejected")
        void garbageIsRejected() {
            assertThatThrownBy(
                            () -> TransportKeyAttestation.decode("not an attestation".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a decoded attestation with an unusable transport key fails when the key is used")
        void malformedTransportKeyIsRejectedOnUse() {
            // Decoding does not parse the key — it is opaque bytes until someone needs it. That is
            // fine, but it means the failure surfaces at transportKey(), so pin that it is a clean
            // protocol error and not an IllegalArgumentException from the JCA.
            TransportKeyAttestation attestation =
                    new TransportKeyAttestation(DID, KEY_ID, new byte[8], NOT_BEFORE, NOT_AFTER, new byte[64]);

            assertThatThrownBy(attestation::transportKey).isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ nulls

    @Nested
    @DisplayName("null rejection")
    class NullRejection {

        @Test
        @DisplayName("every component is non-null")
        void everyComponentIsRequired() {
            byte[] key = encodedTransportKey();
            byte[] signature = signed().signature();

            assertThatThrownBy(() -> new TransportKeyAttestation(null, KEY_ID, key, NOT_BEFORE, NOT_AFTER, signature))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("did");
            assertThatThrownBy(() -> new TransportKeyAttestation(DID, null, key, NOT_BEFORE, NOT_AFTER, signature))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("keyId");
            assertThatThrownBy(() -> new TransportKeyAttestation(DID, KEY_ID, null, NOT_BEFORE, NOT_AFTER, signature))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("transportPublicKey");
            assertThatThrownBy(() -> new TransportKeyAttestation(DID, KEY_ID, key, null, NOT_AFTER, signature))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("notBefore");
            assertThatThrownBy(() -> new TransportKeyAttestation(DID, KEY_ID, key, NOT_BEFORE, null, signature))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("notAfter");
            assertThatThrownBy(() -> new TransportKeyAttestation(DID, KEY_ID, key, NOT_BEFORE, NOT_AFTER, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("signature");
        }
    }

    // ------------------------------------------------------------------ documented behaviour

    @Nested
    @DisplayName("documented behaviour (not defences)")
    class DocumentedBehaviour {

        @Test
        @DisplayName("record equality compares array identity, not contents")
        void recordEqualityIsReferenceBasedForArrays() {
            TransportKeyAttestation original = signed();
            TransportKeyAttestation decoded = TransportKeyAttestation.decode(original.encode());

            // NOT a defence, and a trap worth pinning: records use Object.equals for array
            // components, and both the constructor and the accessors clone, so two attestations
            // with byte-identical contents are never equal and never share a hashCode. Compare
            // encode() output, or the DID and encoded key — never the records themselves, and never
            // put one in a HashSet expecting deduplication.
            assertThat(decoded).isNotEqualTo(original);
            assertThat(decoded.encode()).isEqualTo(original.encode());
        }

        @Test
        @DisplayName("a non-UTC offset timestamp is accepted and normalised to UTC")
        void offsetTimestampsAreNormalised() {
            // NOT a defence, and mildly surprising given the field is documented as "ISO-8601 UTC":
            // since JDK 12 Instant.parse accepts an offset and converts it. Harmless here — the
            // instant is still unambiguous and the signature covers the exact characters, so two
            // verifiers always agree — but it means the same window has more than one spelling, and
            // anything that ever compares attestations by string must normalise first.
            TransportKeyAttestation offset = signed("2026-07-01T02:00:00+02:00", NOT_AFTER);

            assertThat(offset.isValidAt(didKey(), Instant.parse("2026-07-01T00:00:00Z")))
                    .isTrue();
            assertThat(offset.isValidAt(didKey(), Instant.parse("2026-06-30T23:59:59Z")))
                    .isFalse();
        }

        @Test
        @DisplayName("nothing validates the key id against the DID")
        void keyIdIsNotCheckedAgainstTheDid() {
            // NOT a defence. The key id is a DID fragment by convention only; a self-consistent
            // attestation can name any fragment. Nothing keys off it today — if anything ever does,
            // it needs its own check, because this one does not exist.
            TransportKeyAttestation odd = TransportKeyAttestation.sign(
                    DID,
                    "did:plc:someone-else#transport-9",
                    transportKeys.getPublic(),
                    NOT_BEFORE,
                    NOT_AFTER,
                    didKeys.getPrivate());

            assertThat(odd.isValidAt(didKey(), NOW)).isTrue();
        }
    }
}
