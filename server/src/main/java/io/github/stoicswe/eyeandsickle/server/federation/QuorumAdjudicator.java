package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.federation.AdjudicationResult.AgreedOutcome;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ValidatorConduct;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The consensus decision at the centre of the §5 loop: given a frozen committee and the votes it
 * cast, decide whether an outcome reached quorum and classify how every sampled validator behaved.
 *
 * <p>This is {@code docs/architecture/05-validator-quorum.md} §5 steps 3 and 5, as a pure function —
 * no persistence, no clock, no key I/O beyond the {@link SigningKeyDirectory} it is handed. The BFT
 * threshold arithmetic (§1) is not re-implemented here; it is delegated to the protocol {@link
 * QuorumCommittee}, the one authority for {@code 2f+1}-of-{@code 3f+1}, so the value a verifier later
 * re-checks (§7) and the value used to resolve are computed by the same code.
 *
 * <h2>How a vote is counted</h2>
 *
 * A vote counts toward an outcome only if the validator was sampled for this duel, its signature
 * verifies, and it did not equivocate. Equivocators are set aside first — a validator that signed two
 * conflicting outcomes has forfeited its vote, and counting either half would let it influence the
 * result it is being slashed for. Among the rest, votes are bucketed by the exact canonical bytes
 * they signed; an outcome wins only if its agreeing validators clear <em>both</em> the count
 * threshold and the weighted-power threshold. Requiring both, rather than weight alone, stops a
 * single validator holding most of a committee's reputation from deciding an outcome by itself —
 * which is precisely the single-arbiter failure Invariant I15 forbids, and it never rejects the doc's
 * worked example (5 signatures and five-sevenths of the weight satisfy both at once). This mirrors the
 * strictness protocol {@code ProvenanceChainVerifier} applies when it re-checks the same outcome.
 *
 * <h2>The conduct verdict</h2>
 *
 * <ul>
 *   <li>Equivocated → {@link ValidatorConduct#EQUIVOCATED} (and an {@link EquivocationProof}).
 *   <li>Quorum reached, validator signed the winning outcome → {@link ValidatorConduct#CORRECT}.
 *   <li>Quorum reached, validator signed a different outcome → {@link ValidatorConduct#DIVERGENT}.
 *   <li>Sampled but contributed no verified vote → {@link ValidatorConduct#NO_SHOW}, whether or not a
 *       quorum formed. Not responding is unavailability regardless of the result.
 *   <li>No quorum formed and the validator did cast a verified vote → <strong>no verdict</strong>
 *       (absent from the conduct map). Without an agreed outcome there is nothing to call the vote
 *       right or wrong against, and §3.2's divergence penalty presumes a threshold-reached majority to
 *       diverge from. Leaving reputation untouched here is the conservative reading; it is noted as a
 *       {@code [PROPOSAL]} choice for {@code docs/design/15}.
 * </ul>
 *
 * <p>A vote from a validator that was <em>not</em> sampled is ignored entirely — it carries no
 * authority over this duel — and its signer is neither counted nor judged here.
 */
public final class QuorumAdjudicator {

    /**
     * Slack on the weighted-quorum comparison, matching protocol {@code ProvenanceChainVerifier}.
     * Sampling weights are doubles summed in map order, so an outcome exactly on the threshold can
     * land a few ulps under it depending on addition order; a relative tolerance this small admits
     * that rounding without admitting a quorum that is genuinely short.
     */
    private static final double WEIGHT_TOLERANCE = 1e-9;

    private QuorumAdjudicator() {}

    /**
     * Adjudicates a duel's collected votes.
     *
     * @param committee the frozen sampling record — who was drawn and what each weighs (§2)
     * @param votes every collected validator vote; may contain non-sampled signers and multiple votes
     *     per validator, both of which this method handles
     * @param keys resolves each signature's {@code kid} to a public key
     * @return the consensus decision, the per-validator conduct, and any equivocation proofs
     */
    public static AdjudicationResult adjudicate(
            QuorumCommittee committee, List<ValidatorSignature> votes, SigningKeyDirectory keys) {
        Objects.requireNonNull(committee, "committee");
        Objects.requireNonNull(votes, "votes");
        Objects.requireNonNull(keys, "keys");

        // Only sampled validators have authority over this duel; a vote from anyone else is noise.
        List<ValidatorSignature> sampledVotes = new ArrayList<>();
        for (ValidatorSignature vote : votes) {
            Objects.requireNonNull(vote, "vote");
            if (committee.wasSampled(vote.validatorDid())) {
                sampledVotes.add(vote);
            }
        }

        List<EquivocationProof> equivocations = EquivocationDetector.detectAll(sampledVotes, keys);
        Set<String> equivocators = new HashSet<>();
        for (EquivocationProof proof : equivocations) {
            equivocators.add(proof.validatorDid());
        }

        // Bucket each non-equivocating validator's single verified outcome by the exact bytes signed.
        Map<String, OutcomeBucket> buckets = new LinkedHashMap<>();
        Map<String, String> validatorChoice = new HashMap<>();
        for (ValidatorSignature vote : sampledVotes) {
            String validatorDid = vote.validatorDid();
            if (equivocators.contains(validatorDid)) {
                continue;
            }
            if (!EquivocationDetector.verifies(vote, keys)) {
                continue;
            }
            String bucketKey = Base64.getEncoder().encodeToString(vote.canonicalBytes());
            OutcomeBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new OutcomeBucket(vote.outcome()));
            // A non-equivocating validator has at most one distinct verified outcome, so it is counted
            // once. A duplicate identical vote (same bytes re-sent) does not double its weight, and its
            // signature is deduplicated for the resolved envelope.
            if (validatorChoice.putIfAbsent(validatorDid, bucketKey) == null) {
                bucket.count++;
                bucket.weight += committee.weightOf(validatorDid);
            }
            bucket.addSignature(validatorDid, vote.signature());
        }

        String winnerKey = pickWinner(committee, buckets);
        Optional<AgreedOutcome> agreed = winnerKey == null
                ? Optional.empty()
                : Optional.of(new AgreedOutcome(
                        buckets.get(winnerKey).payload, buckets.get(winnerKey).orderedSignatures()));

        Map<String, ValidatorConduct> conduct = classifyConduct(committee, equivocators, validatorChoice, winnerKey);
        return new AdjudicationResult(agreed, conduct, equivocations);
    }

    /**
     * @return the bucket key of the one outcome that clears both thresholds, or {@code null} if none
     *     does. At most one can: a bucket needs more than two-thirds of the committee's power to win,
     *     and two disjoint buckets cannot each hold that.
     */
    private static String pickWinner(QuorumCommittee committee, Map<String, OutcomeBucket> buckets) {
        int requiredCount = committee.agreeingValidatorsRequired();
        double requiredWeight = committee.requiredWeight();
        double tolerance = WEIGHT_TOLERANCE * Math.max(1.0, committee.totalWeight());
        for (Map.Entry<String, OutcomeBucket> entry : buckets.entrySet()) {
            OutcomeBucket bucket = entry.getValue();
            boolean countMet = bucket.count >= requiredCount;
            boolean weightMet = bucket.weight >= requiredWeight - tolerance;
            if (countMet && weightMet) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, ValidatorConduct> classifyConduct(
            QuorumCommittee committee,
            Set<String> equivocators,
            Map<String, String> validatorChoice,
            String winnerKey) {
        Map<String, ValidatorConduct> conduct = new LinkedHashMap<>();
        for (String validatorDid : committee.sampledWeights().keySet()) {
            if (equivocators.contains(validatorDid)) {
                conduct.put(validatorDid, ValidatorConduct.EQUIVOCATED);
                continue;
            }
            String choice = validatorChoice.get(validatorDid);
            if (choice == null) {
                // Sampled, but no verified vote reached us: a no-show, whether or not a quorum formed.
                conduct.put(validatorDid, ValidatorConduct.NO_SHOW);
            } else if (winnerKey != null) {
                conduct.put(
                        validatorDid, choice.equals(winnerKey) ? ValidatorConduct.CORRECT : ValidatorConduct.DIVERGENT);
            }
            // else: a verified vote but no quorum to judge it against — left unclassified on purpose.
        }
        return conduct;
    }

    /** One candidate outcome and the agreeing power behind it. */
    private static final class OutcomeBucket {

        private final ProvenancePayload payload;
        private final Set<String> signers = new HashSet<>();
        private final List<SignatureBlock> signatures = new ArrayList<>();
        private int count;
        private double weight;

        private OutcomeBucket(ProvenancePayload payload) {
            this.payload = payload;
        }

        private void addSignature(String validatorDid, SignatureBlock block) {
            // One signature per validator in the resolved envelope: a validator that re-sent the same
            // vote must not appear twice, or a verifier would double-count its weight (§7).
            if (signers.add(validatorDid)) {
                signatures.add(block);
            }
        }

        private List<SignatureBlock> orderedSignatures() {
            return List.copyOf(signatures);
        }
    }
}
