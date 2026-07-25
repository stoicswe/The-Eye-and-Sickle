package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ValidatorConduct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The consensus decision at the centre of the §5 loop — {@code
 * docs/architecture/05-validator-quorum.md} §1 and §5, and the direct enforcement of Invariant I15
 * (no single arbiter decides a cross-server outcome).
 *
 * <p>The interesting cases are all the ways an outcome must FAIL to certify: below the count
 * threshold, below the weighted threshold, from a validator that was never sampled, or with an
 * equivocator's forfeited vote counted. A green happy path proves almost nothing here; the security is
 * in the rejections.
 */
class QuorumAdjudicatorTest {

    private static final String DUEL = "duel-1";
    private static final String A = FederationFixture.HOLDER_A;
    private static final String B = FederationFixture.HOLDER_B;

    private final FederationFixture fx = new FederationFixture();
    private final SigningKeyDirectory keys = fx.directory();

    private ValidatorSignature vote(String winner, int validatorIndex) {
        return fx.vote(DUEL, winner, "did:plc:validator" + validatorIndex);
    }

    @Nested
    @DisplayName("reaching quorum")
    class Quorum {

        @Test
        @DisplayName("resolves the doc's worked example: 5 of 7 equal-weight validators agree")
        void resolvesFiveOfSeven() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            List<ValidatorSignature> votes = List.of(
                    vote(A, 1),
                    vote(A, 2),
                    vote(A, 3),
                    vote(A, 4),
                    vote(A, 5), // five agree on A
                    vote(B, 6)); // one diverges; validator7 is a no-show

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isTrue();
            // The five agreeing validators are CORRECT, the diverger DIVERGENT, the silent one NO_SHOW.
            assertThat(result.conduct())
                    .containsEntry("did:plc:validator1", ValidatorConduct.CORRECT)
                    .containsEntry("did:plc:validator5", ValidatorConduct.CORRECT)
                    .containsEntry("did:plc:validator6", ValidatorConduct.DIVERGENT)
                    .containsEntry("did:plc:validator7", ValidatorConduct.NO_SHOW);
            // The resolved envelope carries exactly the five agreeing signatures, no more.
            assertThat(result.agreedOutcome().orElseThrow().signatures()).hasSize(5);
            assertThat(result.agreedOutcome().orElseThrow().payload().holderDid())
                    .isEqualTo(A);
        }

        @Test
        @DisplayName("certifies only when BOTH the count and the weighted thresholds are met")
        void requiresBothThresholds() {
            // Weight is lopsided but every validator still counts as one head.
            QuorumCommittee committee = FederationFixture.weightedCommittee(DUEL, 1, 1, 1, 1, 1, 1, 10);
            List<ValidatorSignature> allSeven = new ArrayList<>();
            for (int i = 1; i <= 7; i++) {
                allSeven.add(vote(A, i));
            }

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, allSeven, keys);

            // count 7 ≥ 5 and weight 16 ≥ 16·5/7 — both clear, so it resolves.
            assertThat(result.resolved()).isTrue();
        }
    }

    @Nested
    @DisplayName("Invariant I15 — no single arbiter, no colluding minority")
    class NoSingleArbiter {

        @Test
        @DisplayName("a validator holding a supermajority of weight cannot decide alone")
        void heavyValidatorCannotDecideAlone() {
            // validator1 holds 10 of 16 total weight — more than two thirds.
            QuorumCommittee committee = FederationFixture.weightedCommittee(DUEL, 10, 1, 1, 1, 1, 1, 1);

            // Alone: weight 10 < 16·5/7 ≈ 11.43 and count 1 < 5.
            assertThat(QuorumAdjudicator.adjudicate(committee, List.of(vote(A, 1)), keys)
                            .resolved())
                    .isFalse();
        }

        @Test
        @DisplayName("a heavy minority that clears the WEIGHT bar is still refused for lacking the COUNT")
        void weightWithoutCountIsRefused() {
            QuorumCommittee committee = FederationFixture.weightedCommittee(DUEL, 10, 1, 1, 1, 1, 1, 1);
            // validator1 (10) + validator2 (1) + validator3 (1) = weight 12 ≥ 11.43, but only 3 heads.
            List<ValidatorSignature> votes = List.of(vote(A, 1), vote(A, 2), vote(A, 3));

            // The count threshold is exactly the I15 guard: raw reputation cannot buy an outcome with
            // too few distinct servers behind it.
            assertThat(QuorumAdjudicator.adjudicate(committee, votes, keys).resolved())
                    .isFalse();
        }

        @Test
        @DisplayName("a numerous low-weight majority that clears the COUNT is still refused for lacking the WEIGHT")
        void countWithoutWeightIsRefused() {
            // validator7 is the heavy one; the six light validators together weigh only 6 of 16.
            QuorumCommittee committee = FederationFixture.weightedCommittee(DUEL, 1, 1, 1, 1, 1, 1, 10);
            List<ValidatorSignature> votes =
                    List.of(vote(A, 1), vote(A, 2), vote(A, 3), vote(A, 4), vote(A, 5), vote(A, 6));

            // count 6 ≥ 5 but weight 6 < 11.43: sock-puppet numerousness cannot override proven weight.
            assertThat(QuorumAdjudicator.adjudicate(committee, votes, keys).resolved())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("below threshold")
    class BelowThreshold {

        @Test
        @DisplayName("does not resolve when only four of seven agree")
        void fourOfSevenDoesNotResolve() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            List<ValidatorSignature> votes = List.of(vote(A, 1), vote(A, 2), vote(A, 3), vote(A, 4));

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isFalse();
            assertThat(result.agreedOutcome()).isEmpty();
        }

        @Test
        @DisplayName("leaves a voter unclassified when no quorum forms, but marks the silent as no-shows")
        void noQuorumLeavesVotersUnclassified() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            // Four sign A, three are silent: no outcome to call the four right or wrong against (§3.2
            // divergence presumes a threshold-reached majority), so the four are absent from conduct.
            List<ValidatorSignature> votes = List.of(vote(A, 1), vote(A, 2), vote(A, 3), vote(A, 4));

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.conduct())
                    .doesNotContainKey("did:plc:validator1")
                    .containsEntry("did:plc:validator5", ValidatorConduct.NO_SHOW)
                    .containsEntry("did:plc:validator6", ValidatorConduct.NO_SHOW)
                    .containsEntry("did:plc:validator7", ValidatorConduct.NO_SHOW);
        }
    }

    @Nested
    @DisplayName("votes that carry no authority")
    class Unauthorized {

        @Test
        @DisplayName("ignores a vote from a validator that was never sampled")
        void ignoresNonSampledSigner() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            List<ValidatorSignature> votes =
                    new ArrayList<>(List.of(vote(A, 1), vote(A, 2), vote(A, 3), vote(A, 4), vote(A, 5)));
            // A stranger who was not drawn tries to vote; it must not count and must not be judged.
            votes.add(fx.vote(DUEL, A, "did:plc:validator99"));

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isTrue();
            assertThat(result.conduct()).doesNotContainKey("did:plc:validator99");
            // Still exactly the five sampled signers in the envelope — the stranger added nothing.
            assertThat(result.agreedOutcome().orElseThrow().signatures()).hasSize(5);
        }

        @Test
        @DisplayName("treats a sampled validator whose signature does not verify as a no-show")
        void unverifiableSampledVoteIsNoShow() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            List<ValidatorSignature> votes = List.of(
                    vote(A, 1),
                    vote(A, 2),
                    vote(A, 3),
                    vote(A, 4),
                    vote(A, 5), // quorum on A
                    fx.voteWithBadSignature(DUEL, A, "did:plc:validator6")); // validator7 also silent

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isTrue();
            // validator6 "voted" but its signature is bogus, so it earns no CORRECT credit — no-show.
            assertThat(result.conduct()).containsEntry("did:plc:validator6", ValidatorConduct.NO_SHOW);
        }
    }

    @Nested
    @DisplayName("equivocation")
    class Equivocation {

        @Test
        @DisplayName(
                "sets an equivocator's forfeited votes aside, slashes it, and still resolves on the honest majority")
        void equivocatorForfeitsItsVote() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(7));
            List<ValidatorSignature> votes = List.of(
                    vote(A, 1),
                    vote(A, 2),
                    vote(A, 3),
                    vote(A, 4),
                    vote(A, 5), // five honest on A
                    fx.vote(DUEL, A, "did:plc:validator6"), // validator6 signs BOTH A ...
                    fx.vote(DUEL, B, "did:plc:validator6")); // ... and B — equivocation

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isTrue();
            assertThat(result.conduct()).containsEntry("did:plc:validator6", ValidatorConduct.EQUIVOCATED);
            assertThat(result.equivocations())
                    .extracting(EquivocationProof::validatorDid)
                    .containsExactly("did:plc:validator6");
            // The equivocator's A-vote must NOT be counted, or it could influence the very outcome it
            // is being slashed for: the envelope holds only the five honest signatures.
            assertThat(result.agreedOutcome().orElseThrow().signatures()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("deduplication")
    class Deduplication {

        @Test
        @DisplayName("counts a validator once when it re-sends the identical vote")
        void identicalResendCountedOnce() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(3));
            // committee of 3 requires all 3; validator1 sends its A-vote twice.
            List<ValidatorSignature> votes = List.of(vote(A, 1), vote(A, 1), vote(A, 2), vote(A, 3));

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, votes, keys);

            assertThat(result.resolved()).isTrue();
            // Three distinct signers, three signatures — the duplicate did not double validator1's
            // weight and its block appears once, or a verifier would over-count it (§7).
            assertThat(result.agreedOutcome().orElseThrow().signatures()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("argument validation")
    class Validation {

        @Test
        @DisplayName("resolves nothing from an empty vote set and marks every sampled validator a no-show")
        void emptyVotes() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(3));

            AdjudicationResult result = QuorumAdjudicator.adjudicate(committee, List.of(), keys);

            assertThat(result.resolved()).isFalse();
            assertThat(result.conduct().values()).containsOnly(ValidatorConduct.NO_SHOW);
        }

        @Test
        @DisplayName("rejects null committee, votes, or key directory")
        void rejectsNulls() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(3));
            assertThatThrownBy(() -> QuorumAdjudicator.adjudicate(null, List.of(), keys))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> QuorumAdjudicator.adjudicate(committee, null, keys))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> QuorumAdjudicator.adjudicate(committee, List.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects a null vote element rather than silently skipping it")
        void rejectsNullVote() {
            QuorumCommittee committee = fx.committee(DUEL, FederationFixture.validatorDids(3));
            List<ValidatorSignature> votes = Arrays.asList(vote(A, 1), null);
            assertThatThrownBy(() -> QuorumAdjudicator.adjudicate(committee, votes, keys))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
