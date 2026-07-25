package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.Map;
import java.util.Objects;

/**
 * The sampling record for one duel: which validators were drawn, and what each one's vote weighs.
 *
 * <p>This is the evidence {@code docs/architecture/04-item-provenance.md} §7 steps 1–2 are checked
 * against — "each signature resolves to a validator that was actually sampled for that duel", and
 * "the summed reputation-weight of valid signatures clears the {@code 2f+1}-of-{@code 3f+1}
 * threshold". It is produced by the sampling step in {@code
 * docs/architecture/05-validator-quorum.md} §2 and supplied to the verifier by the caller, because
 * the verifier does no I/O (§6.2).
 *
 * <p>The weight of a validator is its sampling weight, {@code validatorReputation × uptime} ({@code
 * 05} §2.2), captured <em>at sampling time</em>. It must be the historical value, not today's: a
 * duel adjudicated last year was legitimate under the reputations that existed then, and
 * re-evaluating it against current reputations would let an item silently stop being recognized
 * because an unrelated validator has since gone bad.
 *
 * <p>Nothing here is a game balance value. The weights are data handed in by the caller, and the
 * threshold below is derived from the committee's own size — this type holds no tunable constant.
 *
 * @param duelId the duel this committee was sampled for; a record's {@code issuerDid} is {@code
 *     duel:<duelId>}
 * @param sampledWeights each sampled validator's DID mapped to its sampling weight; non-negative and
 *     finite
 */
public record QuorumCommittee(String duelId, Map<String, Double> sampledWeights) {

    public QuorumCommittee {
        Objects.requireNonNull(duelId, "duelId");
        Objects.requireNonNull(sampledWeights, "sampledWeights");
        if (sampledWeights.isEmpty()) {
            throw new IllegalArgumentException("A sampled committee has at least one validator");
        }
        for (Map.Entry<String, Double> entry : sampledWeights.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "validator DID");
            Double weight = Objects.requireNonNull(entry.getValue(), "validator weight");
            if (!Double.isFinite(weight) || weight < 0) {
                throw new IllegalArgumentException(
                        "Validator weight must be finite and non-negative; " + entry.getKey() + " has " + weight);
            }
        }
        sampledWeights = Map.copyOf(sampledWeights);
    }

    /** How many validators were drawn — {@code N}, which {@code 05} §1 recommends as 7. */
    public int size() {
        return sampledWeights.size();
    }

    /**
     * The number of Byzantine validators this committee size tolerates: the largest {@code f} with
     * {@code 3f+1 <= N}. For the recommended {@code N = 7}, {@code f = 2}.
     *
     * @return {@code f}
     */
    public int byzantineTolerance() {
        return (size() - 1) / 3;
    }

    /**
     * How many distinct sampled validators must have signed.
     *
     * <p>Computed as {@code floor(2N/3) + 1}, the standard BFT quorum size, which equals {@code
     * 2f+1} exactly when {@code N = 3f+1} — 5 for the recommended committee of 7, as {@code 05} §1
     * states. For a committee size that is not of the form {@code 3f+1} the two formulas diverge and
     * this one is the stricter; erring toward strictness is right, because the failure it prevents
     * (recognizing a forged duel outcome) is permanent while the failure it risks (refusing a
     * legitimate one) is visible and fixable.
     *
     * @return the minimum number of agreeing validators
     */
    public int agreeingValidatorsRequired() {
        return 2 * size() / 3 + 1;
    }

    /** The committee's total sampling weight. */
    public double totalWeight() {
        double total = 0;
        for (double weight : sampledWeights.values()) {
            total += weight;
        }
        return total;
    }

    /**
     * The minimum summed weight of agreeing validators — {@code totalWeight × (2f+1) / (3f+1)}.
     *
     * <p>For the recommended committee this is five sevenths of the sampled weight, which is exactly
     * what "{@code 2f+1} of {@code 3f+1} weighted validator power" means at {@code N = 7}. Expressed
     * as a fraction of the committee's own total rather than as a constant, so a differently-sized
     * committee scales rather than needing a second rule.
     *
     * @return the weight threshold
     */
    public double requiredWeight() {
        return totalWeight() * agreeingValidatorsRequired() / size();
    }

    /**
     * @param validatorDid a signer's DID
     * @return whether that validator was drawn for this duel
     */
    public boolean wasSampled(String validatorDid) {
        return sampledWeights.containsKey(validatorDid);
    }

    /**
     * @param validatorDid a signer's DID
     * @return its sampling weight, or {@code 0} if it was not sampled
     */
    public double weightOf(String validatorDid) {
        return sampledWeights.getOrDefault(validatorDid, 0.0);
    }
}
