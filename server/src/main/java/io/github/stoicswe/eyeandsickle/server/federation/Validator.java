package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An opted-in federated server eligible to adjudicate cross-server duels — one row of {@code
 * validators} ({@code docs/architecture/05-validator-quorum.md} §2.1).
 *
 * <h2>The two scores are separate on purpose</h2>
 *
 * {@code validatorReputation} is correctness (§3) and {@code uptime} is availability (§4), and they
 * are distinct fields because §4 is explicit that a validator sampled-but-silent must not be penalised
 * like one that signed something wrong. They are multiplied only at sampling time, to form the weight
 * (§2.2). Neither has anything to do with a player's {@code factionReputation}: this is a server's
 * trust score, keyed by a server DID, with no join to any player ({@code docs/architecture/06} §1
 * constraint 5).
 *
 * <p>Reputation and uptime are {@link BigDecimal}, matching the schema's {@code numeric(9,8)}: the
 * database is authoritative for the stored value, and a threshold comparison must not depend on which
 * server rounded a {@code double} which way. Arithmetic converts to {@code double} for the AIMD step
 * and rounds back (see {@code QuorumService}).
 *
 * @param validatorDid the server's DID and primary key
 * @param validatorReputation correctness score, in {@code [0, 1]} (§3)
 * @param uptime availability score, in {@code [0, 1]} (§4)
 * @param isNew the cold-start flag: still carrying the newcomer floor, not yet proven (§2.5)
 * @param enrolledAt when it opted in
 * @param lastSampledAt when it was last drawn for a committee, or {@code null} if never
 * @param lastVoteAt when its reputation was last moved by a vote, or {@code null} if never
 * @param votesCorrect lifetime count of correct votes (§3.1)
 * @param votesDivergent lifetime count of divergent votes (§3.2)
 * @param noShows lifetime count of times it was sampled and did not respond (§4)
 * @param rowVersion optimistic-concurrency version
 */
public record Validator(
        String validatorDid,
        BigDecimal validatorReputation,
        BigDecimal uptime,
        boolean isNew,
        Instant enrolledAt,
        Instant lastSampledAt,
        Instant lastVoteAt,
        long votesCorrect,
        long votesDivergent,
        long noShows,
        long rowVersion) {

    public Validator {
        Objects.requireNonNull(validatorDid, "validatorDid");
        Objects.requireNonNull(validatorReputation, "validatorReputation");
        Objects.requireNonNull(uptime, "uptime");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    /**
     * Projects this validator to a sampling candidate carrying its two weight factors.
     *
     * @return the candidate for {@code AResSampler}
     */
    public SampledValidator toSamplingCandidate() {
        return SampledValidator.of(validatorDid, validatorReputation.doubleValue(), uptime.doubleValue());
    }
}
