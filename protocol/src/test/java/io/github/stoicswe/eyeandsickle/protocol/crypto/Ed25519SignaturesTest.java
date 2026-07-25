package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Standalone tests for {@link Ed25519Signatures}.
 *
 * <p>{@code ProvenanceSigningTest} already exercises signing as part of the end-to-end provenance
 * flow. This suite covers the primitive on its own, because the two questions are different: that one
 * asks "does a provenance record round-trip", this one asks "does the signature primitive fail in
 * exactly the ways the rest of the design assumes it fails".
 *
 * <p>Two of those assumptions are load-bearing and easy to lose in a refactor:
 *
 * <ul>
 *   <li><strong>{@code verify} returns {@code false}; it never throws.</strong> {@code
 *       docs/architecture/03-server-and-federation.md} §4 makes an unverifiable chain simply
 *       <em>unrecognized</em> rather than an error condition. A verifier that threw on a malformed
 *       signature would let any peer kill a verification pass — and therefore a player's item history
 *       — by sending 63 bytes instead of 64.
 *   <li><strong>Signing is deterministic.</strong> Ed25519 derives its per-signature nonce from a
 *       hash of the private key and the message (RFC 8032 §5.1.6), so nothing in the signing path
 *       reads an RNG. That is what lets two servers holding the same record produce byte-identical
 *       signatures, which in turn is what makes a stored signature comparable by value.
 * </ul>
 */
class Ed25519SignaturesTest {

    /** Stands in for canonicalized provenance payload bytes; the content is not what is under test. */
    private static final byte[] PAYLOAD =
            "{\"itemId\":\"2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777\",\"eventType\":\"INITIAL_MINT\"}"
                    .getBytes(StandardCharsets.UTF_8);

    /** An Ed25519 signature is fixed-width: 32 bytes of R plus 32 bytes of S. */
    private static final int SIGNATURE_LENGTH = 64;

    @Nested
    @DisplayName("signing and verification")
    class RoundTrip {

        @Test
        @DisplayName("a signature over the exact bytes verifies")
        void roundTrips() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();

            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);

            assertThat(signature).hasSize(SIGNATURE_LENGTH);
            assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, signature))
                    .isTrue();
        }

        @Test
        @DisplayName("an empty message signs and verifies")
        void emptyMessage() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), new byte[0]);

            // Worth pinning rather than assuming: if the empty message were a special case, an
            // attacker who could make canonicalization yield nothing would get a free valid
            // signature over "no claim at all".
            assertThat(signature).hasSize(SIGNATURE_LENGTH);
            assertThat(Ed25519Signatures.verify(issuer.getPublic(), new byte[0], signature))
                    .isTrue();
        }

        @Test
        @DisplayName("a large message signs and verifies")
        void largeMessage() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            // A megabyte: far larger than any provenance payload, but a chain export or a bulk
            // federation sync is signed as one blob, and Ed25519ph/Ed25519 differ on prehashing.
            byte[] large = new byte[1 << 20];
            for (int i = 0; i < large.length; i++) {
                large[i] = (byte) (i * 31);
            }

            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), large);

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), large, signature))
                    .isTrue();
        }

        @Test
        @DisplayName("two key pairs sign the same message differently")
        void distinctKeysProduceDistinctSignatures() {
            KeyPair one = Ed25519Signatures.generateKeyPair();
            KeyPair two = Ed25519Signatures.generateKeyPair();

            // If this ever collided, generateKeyPair() would be returning a fixed key — which would
            // mean every home server in the federation shared an issuing identity.
            assertThat(Ed25519Signatures.sign(one.getPrivate(), PAYLOAD))
                    .isNotEqualTo(Ed25519Signatures.sign(two.getPrivate(), PAYLOAD));
        }
    }

    @Nested
    @DisplayName("rejection")
    class Rejection {

        @Test
        @DisplayName("another party's key does not verify this signature")
        void wrongKeyIsRejected() {
            // Attack: a dishonest server claims one of its own mints was issued by a trusted server.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            KeyPair impostor = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);

            assertThat(Ed25519Signatures.verify(impostor.getPublic(), PAYLOAD, signature))
                    .isFalse();
        }

        @Test
        @DisplayName("altering the signed bytes invalidates the signature")
        void alteredDataIsRejected() {
            // Attack: keep a genuine signature, rewrite the item it vouches for.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);

            byte[] altered = PAYLOAD.clone();
            altered[altered.length - 2] ^= 0x01;

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), altered, signature))
                    .isFalse();
        }

        @Test
        @DisplayName("flipping any single bit of the signature invalidates it")
        void mutatedSignatureIsRejected() {
            // Attack: grind toward a valid signature by mutating a captured one. Every byte of both
            // halves must matter, or the search space is smaller than 2^64.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);

            for (int i = 0; i < signature.length; i++) {
                byte[] mutated = signature.clone();
                mutated[i] ^= 0x01;
                assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, mutated))
                        .as("byte %d of the signature was mutable without detection", i)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("a truncated signature is refused, not thrown on")
        void truncatedSignatureIsRejected() {
            // Attack: send 63 bytes. The provider raises SignatureException for a wrong-length
            // signature; verify() must absorb that and answer the question it was asked.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);
            byte[] truncated = Arrays.copyOf(signature, SIGNATURE_LENGTH - 1);

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, truncated))
                    .isFalse();
        }

        @Test
        @DisplayName("an oversized signature is refused, not thrown on")
        void oversizedSignatureIsRejected() {
            // Attack: append trailing bytes, hoping a length-tolerant verifier ignores them.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);
            byte[] padded = Arrays.copyOf(signature, SIGNATURE_LENGTH + 1);

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, padded))
                    .isFalse();
        }

        @Test
        @DisplayName("an empty signature is refused, not thrown on")
        void emptySignatureIsRejected() {
            // Attack: omit the signature entirely and hope "nothing to check" reads as "nothing
            // wrong". This is the shape of the classic `alg: none` JOSE bug.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, new byte[0]))
                    .isFalse();
        }

        @Test
        @DisplayName("an all-zero signature is refused")
        void allZeroSignatureIsRejected() {
            // Attack: the cheapest forgery anyone tries first — a correctly sized block of zeros.
            KeyPair issuer = Ed25519Signatures.generateKeyPair();

            assertThat(Ed25519Signatures.verify(issuer.getPublic(), PAYLOAD, new byte[SIGNATURE_LENGTH]))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("the same key over the same message always gives identical bytes")
        void signingIsDeterministic() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] first = Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD);

            // This is a property of Ed25519 itself, not of this class — but the design leans on it
            // (a provenance record has one canonical byte form end to end), so it is pinned here.
            // An ECDSA-style scheme with a random nonce would fail this test, and swapping the
            // algorithm for one would silently break record deduplication across servers.
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThat(Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD))
                        .as("signature attempt %d diverged", attempt)
                        .isEqualTo(first);
            }
        }

        @Test
        @DisplayName("different messages under one key give different signatures")
        void distinctMessagesProduceDistinctSignatures() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            byte[] other = "{\"itemId\":\"2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777\",\"eventType\":\"TRADE\"}"
                    .getBytes(StandardCharsets.UTF_8);

            assertThat(Ed25519Signatures.sign(issuer.getPrivate(), PAYLOAD))
                    .isNotEqualTo(Ed25519Signatures.sign(issuer.getPrivate(), other));
        }
    }

    @Nested
    @DisplayName("algorithm identifiers")
    class AlgorithmIdentifiers {

        @Test
        @DisplayName("the JCA name and the JOSE name are different strings on purpose")
        void constantsAreTheOnesTheWireExpects() {
            // ALGORITHM names the JCA provider algorithm; JOSE_ALG is the value that travels in a
            // SignatureBlock's `alg` field (docs/architecture/04-item-provenance.md §3). They are
            // easy to conflate, and conflating them produces envelopes no JOSE library will read.
            assertThat(Ed25519Signatures.ALGORITHM).isEqualTo("Ed25519");
            assertThat(Ed25519Signatures.JOSE_ALG).isEqualTo("EdDSA");
        }

        @Test
        @DisplayName("a generated key reports its family name, not the curve name")
        void generatedKeysReportTheFamilyName() {
            KeyPair pair = Ed25519Signatures.generateKeyPair();

            // A trap worth pinning. KeyPairGenerator.getInstance("Ed25519") returns keys whose
            // getAlgorithm() is "EdDSA" — the family — not "Ed25519". So a check of the form
            // `key.getAlgorithm().equals(ALGORITHM)` looks obviously correct, compiles, and is
            // always false. Anything that has to identify a key by name should compare against
            // JOSE_ALG, which happens to be the same string for a different reason.
            assertThat(pair.getPublic().getAlgorithm()).isEqualTo("EdDSA");
            assertThat(pair.getPrivate().getAlgorithm()).isEqualTo("EdDSA");
            assertThat(pair.getPublic().getAlgorithm()).isNotEqualTo(Ed25519Signatures.ALGORITHM);
        }

        @Test
        @DisplayName("two generated pairs are different pairs")
        void generationIsNotFixed() {
            assertThat(Ed25519Signatures.generateKeyPair().getPublic().getEncoded())
                    .isNotEqualTo(
                            Ed25519Signatures.generateKeyPair().getPublic().getEncoded());
        }
    }
}
