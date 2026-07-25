package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Provable equivocation detection — {@code docs/architecture/05-validator-quorum.md} §3.3.
 *
 * <p>Equivocation is the one anti-cheat verdict that needs no trust in whoever reports it, because the
 * evidence carries its own refutation: two signatures by the same key, over two different canonical
 * outcomes, both of which verify. Every clause of that definition rules out a specific false positive,
 * and the tests below are almost entirely about the false positives — a detector that slashes on a
 * forged second "vote" would let anyone frame any validator.
 */
class EquivocationDetectorTest {

    private static final String DUEL = "duel-1";
    private static final String VALIDATOR = "did:plc:validator1";

    private final FederationFixture fx = new FederationFixture();
    private final SigningKeyDirectory keys = fx.directory();

    @Nested
    @DisplayName("detect(a, b) — the pairwise rule")
    class Pairwise {

        @Test
        @DisplayName("reports a proof when one validator signs two different verified outcomes for one duel")
        void detectsGenuineEquivocation() {
            ValidatorSignature forA = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            ValidatorSignature forB = fx.vote(DUEL, FederationFixture.HOLDER_B, VALIDATOR);

            EquivocationProof proof = EquivocationDetector.detect(forA, forB, keys);

            assertThat(proof).isNotNull();
            assertThat(proof.validatorDid()).isEqualTo(VALIDATOR);
            assertThat(proof.duelId()).isEqualTo(DUEL);
        }

        @Test
        @DisplayName("does not flag two DIFFERENT validators disagreeing — that is divergence, not equivocation")
        void differentValidatorsAreNotEquivocation() {
            ValidatorSignature a = fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator1");
            ValidatorSignature b = fx.vote(DUEL, FederationFixture.HOLDER_B, "did:plc:validator2");

            // Two parties disagreeing is §3.2 divergence; only one party contradicting ITSELF is §3.3.
            assertThat(EquivocationDetector.detect(a, b, keys)).isNull();
        }

        @Test
        @DisplayName("does not flag one validator signing outcomes for two DIFFERENT duels — that is its job")
        void differentDuelsAreNotEquivocation() {
            ValidatorSignature d1 = fx.vote("duel-1", FederationFixture.HOLDER_A, VALIDATOR);
            ValidatorSignature d2 = fx.vote("duel-2", FederationFixture.HOLDER_B, VALIDATOR);

            assertThat(EquivocationDetector.detect(d1, d2, keys)).isNull();
        }

        @Test
        @DisplayName("does not flag the SAME outcome re-sent — a retransmit is not a contradiction")
        void identicalResendIsNotEquivocation() {
            ValidatorSignature once = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            ValidatorSignature again = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);

            // Same validator, same duel, same canonical bytes: harmless duplicate, not a conflict.
            assertThat(EquivocationDetector.detect(once, again, keys)).isNull();
        }

        @Test
        @DisplayName("does not slash on a forged second vote whose signature does not verify (anti-framing)")
        void unverifiableSecondSignatureIsNotProof() {
            ValidatorSignature real = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            ValidatorSignature forged = fx.voteWithBadSignature(DUEL, FederationFixture.HOLDER_B, VALIDATOR);

            // This is the crux of §3.3: an unverifiable signature is NOT the validator's admission, so
            // anyone could otherwise fabricate a "conflicting" vote and frame an honest validator.
            assertThat(EquivocationDetector.detect(real, forged, keys)).isNull();
            assertThat(EquivocationDetector.detect(forged, real, keys)).isNull();
        }

        @Test
        @DisplayName("does not flag when the key cannot be resolved at all")
        void unresolvableKeyIsNotProof() {
            ValidatorSignature forA = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            ValidatorSignature forB = fx.vote(DUEL, FederationFixture.HOLDER_B, VALIDATOR);

            // With no key to verify against, neither signature is proof of anything.
            assertThat(EquivocationDetector.detect(forA, forB, SigningKeyDirectory.empty()))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("detectAll — scanning a batch")
    class Batch {

        @Test
        @DisplayName("reports one proof per equivocating validator")
        void oneProofPerEquivocator() {
            List<ValidatorSignature> votes = List.of(
                    fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator1"),
                    fx.vote(DUEL, FederationFixture.HOLDER_B, "did:plc:validator1"), // v1 equivocates
                    fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator2"), // v2 honest
                    fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator3"),
                    fx.vote(DUEL, FederationFixture.HOLDER_B, "did:plc:validator3")); // v3 equivocates

            List<EquivocationProof> proofs = EquivocationDetector.detectAll(votes, keys);

            assertThat(proofs)
                    .extracting(EquivocationProof::validatorDid)
                    .containsExactly("did:plc:validator1", "did:plc:validator3");
        }

        @Test
        @DisplayName("reports nothing when every validator signed at most one outcome")
        void noConflictsFound() {
            List<ValidatorSignature> votes = List.of(
                    fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator1"),
                    fx.vote(DUEL, FederationFixture.HOLDER_A, "did:plc:validator2"),
                    fx.vote(DUEL, FederationFixture.HOLDER_B, "did:plc:validator3")); // lone diverger, honest

            assertThat(EquivocationDetector.detectAll(votes, keys)).isEmpty();
        }

        @Test
        @DisplayName("drops unverifiable votes before grouping, so a forgery cannot manufacture a conflict")
        void unverifiableVotesCannotFrame() {
            List<ValidatorSignature> votes = List.of(
                    fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR),
                    fx.voteWithBadSignature(DUEL, FederationFixture.HOLDER_B, VALIDATOR));

            // Only the real vote survives grouping; there is no verified second outcome to conflict.
            assertThat(EquivocationDetector.detectAll(votes, keys)).isEmpty();
        }

        @Test
        @DisplayName("returns one proof even when a validator signed three conflicting outcomes")
        void firstConflictIsEnough() {
            List<ValidatorSignature> votes = List.of(
                    fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR),
                    fx.vote(DUEL, FederationFixture.HOLDER_B, VALIDATOR),
                    fx.vote(DUEL, "did:plc:holderccccccccccccccc", VALIDATOR));

            // One proof suffices to slash and flag; a validator that signed three is no more guilty.
            assertThat(EquivocationDetector.detectAll(votes, keys)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("verifies — what counts as a validator's own signature")
    class Verifies {

        @Test
        @DisplayName("accepts a genuine EdDSA signature over the vote's own canonical bytes")
        void acceptsGenuineSignature() {
            ValidatorSignature vote = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            assertThat(EquivocationDetector.verifies(vote, keys)).isTrue();
        }

        @Test
        @DisplayName("rejects a signature whose algorithm is not EdDSA")
        void rejectsWrongAlgorithm() {
            ProvenancePayload payload = fx.outcome(DUEL, FederationFixture.HOLDER_A);
            SignatureBlock genuine = fx.sign(payload, VALIDATOR);
            SignatureBlock wrongAlg = new SignatureBlock("ES256", genuine.kid(), genuine.sig());

            // Only the one algorithm the game signs with is accepted; anything else carries no weight.
            assertThat(EquivocationDetector.verifies(new ValidatorSignature(payload, wrongAlg), keys))
                    .isFalse();
        }

        @Test
        @DisplayName("rejects a vote whose kid resolves to no key")
        void rejectsUnknownKey() {
            ValidatorSignature vote = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
            assertThat(EquivocationDetector.verifies(vote, SigningKeyDirectory.empty()))
                    .isFalse();
        }

        @Test
        @DisplayName("rejects an undecodable base64url signature rather than throwing")
        void rejectsUndecodableSignature() {
            ProvenancePayload payload = fx.outcome(DUEL, FederationFixture.HOLDER_A);
            SignatureBlock bad = SignatureBlock.eddsa(FederationFixture.kidOf(VALIDATOR), "!!!not base64!!!");

            // A malformed signature is an ordinary thing to receive from an adversarial federation; it
            // is "not verified", never an error that aborts adjudication.
            assertThat(EquivocationDetector.verifies(new ValidatorSignature(payload, bad), keys))
                    .isFalse();
        }

        @Test
        @DisplayName("rejects a well-formed signature made by the wrong key")
        void rejectsSignatureFromWrongKey() {
            // Payload signed by validator2's private key, but the block claims validator1's kid.
            ProvenancePayload payload = fx.outcome(DUEL, FederationFixture.HOLDER_A);
            SignatureBlock mislabeled = fx.signWith(payload, "did:plc:validator2", FederationFixture.kidOf(VALIDATOR));

            // The kid resolves to validator1's key, which will not verify validator2's signature.
            assertThat(EquivocationDetector.verifies(new ValidatorSignature(payload, mislabeled), keys))
                    .isFalse();
        }
    }
}
