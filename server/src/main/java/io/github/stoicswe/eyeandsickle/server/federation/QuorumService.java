package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ReputationRules;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ValidatorConduct;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.AResSampler;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledCommittee;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The complete validator-quorum loop of {@code docs/architecture/05-validator-quorum.md} §5, wired
 * over persistence.
 *
 * <p>This is the one place the pure pieces meet the database. The decisions live in the pure classes —
 * {@link AResSampler} (§2), {@link QuorumAdjudicator} (§1, §5), {@link ReputationRules} (§3–§4),
 * {@link EquivocationDetector} (§3.3) — and this service only sequences them and commits their
 * effects atomically:
 *
 * <ol>
 *   <li>{@link #openDuel} samples a committee, freezes it on the duel, and stamps the drawn validators
 *       (§5 steps 1–2);
 *   <li>{@link #adjudicate} takes the collected votes, decides consensus, resolves the duel into a
 *       {@code duel_grant} outcome (§5 steps 3–4), updates every sampled validator's reputation and
 *       uptime (§5 step 5), and flags any equivocator's server federation-wide (§3.3 → {@code
 *       docs/architecture/03} §4).
 * </ol>
 *
 * <h2>Why the whole of adjudication is one transaction</h2>
 *
 * Resolving the duel, moving reputations, and raising flags are one indivisible fact about the world.
 * If the outcome were committed but a reputation update then failed, the federation would carry a
 * resolved duel whose validators were never rewarded or slashed — a silent divergence between the
 * signed history and the trust scores that are supposed to track it. {@code @Transactional} makes them
 * all land or none.
 */
@Service
public class QuorumService {

    private final ValidatorRegistry validators;
    private final DuelRepository duels;
    private final FlaggedServerRegistry flaggedServers;
    private final QuorumProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    QuorumService(
            ValidatorRegistry validators,
            DuelRepository duels,
            FlaggedServerRegistry flaggedServers,
            QuorumProperties properties,
            RandomGenerator random,
            Clock clock) {
        this.validators = validators;
        this.duels = duels;
        this.flaggedServers = flaggedServers;
        this.properties = properties;
        this.random = random;
        this.clock = clock;
    }

    /**
     * Enrolls a validator at the cold-start floor (§2.5).
     *
     * @param validatorDid the opting-in server's DID
     * @return the enrolled validator
     */
    @Transactional
    public Validator enrollValidator(String validatorDid) {
        return validators.enroll(validatorDid, properties.newcomerReputation(), clock.instant());
    }

    /**
     * Opens a duel and samples its committee (§5 steps 1–2).
     *
     * <p>Draws up to {@code committeeSize} validators weighted by {@code reputation × uptime}, freezes
     * that draw on the duel row, and marks the drawn validators sampled. The committee is frozen here
     * precisely so a later reputation change cannot re-shape a duel that is already under way.
     *
     * @param duelId the adjudication id
     * @param participants the fighting servers' DIDs (at least two)
     * @return the sampled committee — the validators to ask for signatures
     * @throws IllegalStateException if no validator has a positive sampling weight, so no committee can
     *     be drawn; a duel cannot be adjudicated trustlessly without one
     */
    @Transactional
    public SampledCommittee openDuel(UUID duelId, List<String> participants) {
        Instant now = clock.instant();
        List<SampledValidator> candidates = validators.eligibleCandidates();
        List<SampledValidator> drawn = AResSampler.sample(candidates, properties.committeeSize(), random);
        if (drawn.isEmpty()) {
            throw new IllegalStateException(
                    "No eligible validators to sample for duel " + duelId + "; the federation cannot adjudicate a"
                            + " cross-server outcome without a committee (docs/architecture/05 §2)");
        }
        SampledCommittee committee = new SampledCommittee(duelId.toString(), drawn);
        duels.open(duelId, participants, committee, now);
        validators.markSampled(
                drawn.stream().map(SampledValidator::validatorDid).toList(), now);
        return committee;
    }

    /**
     * Adjudicates a duel's collected votes and applies every consequence (§5 steps 3–5).
     *
     * @param duelId the adjudication id
     * @param votes the collected validator votes (signed {@code duel_grant} outcomes)
     * @param keys resolves each signature's {@code kid} to a public key
     * @return the consensus decision, the per-validator conduct, and any equivocation proofs
     * @throws DuelNotFoundException if this server never opened the duel
     * @throws IllegalStateException if the duel is already resolved
     */
    @Transactional
    public AdjudicationResult adjudicate(UUID duelId, List<ValidatorSignature> votes, SigningKeyDirectory keys) {
        Objects.requireNonNull(votes, "votes");
        Objects.requireNonNull(keys, "keys");
        DuelRecord duel = duels.find(duelId).orElseThrow(() -> new DuelNotFoundException(duelId));
        if (duel.isResolved()) {
            // Re-resolving would overwrite a signed outcome. Detecting equivocation that only surfaces
            // in a LATER submission would need the earlier votes staged, which this schema does not
            // provide (duels stores only the resolved signatures); that cross-submission case is a seam
            // left for the vote-transport layer. Within a single submission, equivocation is caught below.
            throw new IllegalStateException("Duel " + duelId + " is already resolved");
        }
        Instant now = clock.instant();

        AdjudicationResult result = QuorumAdjudicator.adjudicate(duel.committee(), votes, keys);

        applyConduct(result.conduct(), now);
        raiseEquivocationFlags(result.equivocations(), now);

        if (result.resolved()) {
            AdjudicationResult.AgreedOutcome outcome = result.agreedOutcome().orElseThrow();
            duels.resolve(
                    duelId,
                    ProvenanceJson.writePayload(outcome.payload()),
                    signaturesJson(outcome.signatures()),
                    now,
                    duel.rowVersion());
        }
        return result;
    }

    /**
     * Applies §3 (reputation) and §4 (uptime) to each sampled validator, under a per-row lock taken in
     * a consistent DID order so two concurrent adjudications touching the same validator cannot
     * deadlock.
     */
    private void applyConduct(Map<String, ValidatorConduct> conduct, Instant now) {
        // TreeSet gives a deterministic lock order across the whole method — the discipline Mutations
        // documents for any operation that locks more than one row.
        for (String validatorDid : new TreeSet<>(conduct.keySet())) {
            ValidatorConduct behaviour = conduct.get(validatorDid);
            Validator current = validators.lockForUpdate(validatorDid).orElse(null);
            if (current == null) {
                // Sampled from our own registry, so this should not happen; if the row was removed out
                // from under us, there is nothing to update and nothing to lose by skipping it.
                continue;
            }
            validators.save(updated(current, behaviour, now));
        }
    }

    /** Builds the post-conduct state of one validator: the §3/§4 arithmetic plus the bookkeeping counters. */
    private Validator updated(Validator current, ValidatorConduct conduct, Instant now) {
        double reputation = ReputationRules.applyToReputation(
                conduct,
                current.validatorReputation().doubleValue(),
                properties.reputationIncreaseAlpha(),
                properties.reputationDecreaseBeta(),
                properties.equivocationFloor());
        double uptime = conduct == ValidatorConduct.NO_SHOW
                ? ReputationRules.afterNoShow(current.uptime().doubleValue(), properties.uptimeDecayGamma())
                : current.uptime().doubleValue();

        long votesCorrect = current.votesCorrect() + (conduct == ValidatorConduct.CORRECT ? 1 : 0);
        long votesDivergent = current.votesDivergent() + (conduct == ValidatorConduct.DIVERGENT ? 1 : 0);
        long noShows = current.noShows() + (conduct == ValidatorConduct.NO_SHOW ? 1 : 0);
        // A no-show did not vote, so it does not move last_vote_at; every other conduct signed something.
        Instant lastVoteAt = conduct == ValidatorConduct.NO_SHOW ? current.lastVoteAt() : now;

        return new Validator(
                current.validatorDid(),
                unit(reputation),
                unit(uptime),
                false, // any conduct means it has now been sampled and acted: no longer a newcomer (§2.5)
                current.enrolledAt(),
                current.lastSampledAt(),
                lastVoteAt,
                votesCorrect,
                votesDivergent,
                noShows,
                current.rowVersion());
    }

    /** Raises one federation-wide flag per equivocating server, carrying the proof as evidence (§3.3 → §4). */
    private void raiseEquivocationFlags(List<EquivocationProof> equivocations, Instant now) {
        for (EquivocationProof proof : equivocations) {
            // raisedByDid is null: this flag was raised locally and automatically by our own
            // adjudication, not relayed from a peer. The evidence is self-verifying regardless.
            flaggedServers.flag(proof.validatorDid(), FlaggedServer.REASON_EQUIVOCATION, proof.evidence(), null, now);
        }
    }

    /** Serialises the agreeing validators' signature blocks into the {@code duels.signatures} array form. */
    private static String signaturesJson(List<SignatureBlock> signatures) {
        List<Map<String, Object>> array = new ArrayList<>(signatures.size());
        for (SignatureBlock block : signatures) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("alg", block.alg());
            entry.put("kid", block.kid());
            entry.put("sig", block.sig());
            array.add(entry);
        }
        return Jsonb.writeArray(array);
    }

    /** The {@code numeric(9,8)} form of a reputation or uptime, clamped into the range the schema accepts. */
    private static BigDecimal unit(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(clamped).setScale(8, RoundingMode.HALF_UP);
    }
}
