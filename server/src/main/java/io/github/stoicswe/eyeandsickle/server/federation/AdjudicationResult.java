package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ValidatorConduct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of adjudicating one duel's collected votes: whether a quorum was reached, what each
 * sampled validator earned, and any equivocation proofs found along the way.
 *
 * <p>This is the pure result of {@link QuorumAdjudicator} — the decision, with no persistence in it.
 * {@code QuorumService} turns it into writes: resolving the duel if {@link #agreedOutcome()} is
 * present, applying each {@link ValidatorConduct} to the validator registry (§3–§4), and raising a
 * flag per {@link #equivocations() equivocation} (§3.3, {@code docs/architecture/03} §4). Splitting
 * the decision from its side effects is what lets the whole §5 loop be tested without a database.
 *
 * @param agreedOutcome the resolved outcome and the validator signatures that carried it past the
 *     {@code 2f+1}-of-{@code 3f+1} threshold, or empty if no outcome reached quorum
 * @param conduct one entry per sampled validator whose reputation or uptime changes; a sampled
 *     validator absent from the map is deliberately untouched — see {@link QuorumAdjudicator} for the
 *     one case that produces no change (a lone signer of a losing outcome when no quorum formed)
 * @param equivocations proofs of validators that signed two conflicting outcomes for this duel; each
 *     drives both the {@link ValidatorConduct#EQUIVOCATED} slash in {@code conduct} and a
 *     federation-wide flag
 */
public record AdjudicationResult(
        Optional<AgreedOutcome> agreedOutcome,
        Map<String, ValidatorConduct> conduct,
        List<EquivocationProof> equivocations) {

    public AdjudicationResult {
        Objects.requireNonNull(agreedOutcome, "agreedOutcome");
        conduct = Map.copyOf(Objects.requireNonNull(conduct, "conduct"));
        equivocations = List.copyOf(Objects.requireNonNull(equivocations, "equivocations"));
    }

    /** Whether the duel reached quorum and can be resolved. */
    public boolean resolved() {
        return agreedOutcome.isPresent();
    }

    /**
     * The agreed duel outcome and the committee signatures that authorize it.
     *
     * <p>Assembled straight into the {@code duel_grant} form {@code
     * docs/architecture/04-item-provenance.md} §3.1 describes: the payload plus one signature block
     * per agreeing validator. The signatures are the very blocks the validators submitted — nothing is
     * re-signed — so the resolved envelope verifies under the same keys the votes did.
     *
     * @param payload the outcome the quorum agreed on
     * @param signatures the agreeing validators' signature blocks, one per validator, in the order
     *     they were counted
     */
    public record AgreedOutcome(ProvenancePayload payload, List<SignatureBlock> signatures) {

        public AgreedOutcome {
            Objects.requireNonNull(payload, "payload");
            signatures = List.copyOf(Objects.requireNonNull(signatures, "signatures"));
            if (signatures.isEmpty()) {
                throw new IllegalArgumentException("An agreed outcome carries the signatures that agreed to it");
            }
        }

        /**
         * The outcome as a multi-signature provenance envelope, ready to persist and to hand to the
         * protocol chain verifier.
         *
         * @return the {@code duel_grant} envelope
         */
        public ProvenanceEnvelope toEnvelope() {
            return ProvenanceEnvelope.quorum(payload, signatures);
        }
    }
}
