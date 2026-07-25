package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import java.util.Objects;

/**
 * One validator as it enters — or leaves — a sampling draw: its DID and the two factors of its
 * sampling weight, captured at one instant.
 *
 * <p>{@code docs/architecture/05-validator-quorum.md} §2.2 defines the sampling weight as {@code
 * reputation × uptime}: a validator that is usually offline is useless even with a spotless
 * reputation, and a fresh one with perfect uptime still ranks below a proven one. Both factors are
 * carried, not just their product, because the frozen record a duel stores ({@code duels
 * .sampled_validators}) needs them: §7 step 1 lets a verifier re-derive the weight and confirm the
 * committee was sampled honestly, and it can only do that if the inputs are on record, not just the
 * result.
 *
 * <p>The weight is captured <em>at sampling time</em> and never recomputed. Reputation moves after
 * every duel; re-deriving an old duel's weights from today's reputations would silently
 * re-adjudicate it, which is the drift {@code QuorumCommittee}'s Javadoc warns against.
 *
 * @param validatorDid the validator's server DID
 * @param reputation its {@code validatorReputation} at sampling time, in {@code [0, 1]}
 * @param uptime its uptime at sampling time, in {@code [0, 1]}
 */
public record SampledValidator(String validatorDid, double reputation, double uptime) {

    public SampledValidator {
        Objects.requireNonNull(validatorDid, "validatorDid");
        requireUnit("reputation", reputation);
        requireUnit("uptime", uptime);
    }

    /**
     * Builds a candidate from its two factors.
     *
     * @param validatorDid the validator's server DID
     * @param reputation {@code validatorReputation}, in {@code [0, 1]}
     * @param uptime uptime, in {@code [0, 1]}
     * @return the candidate
     */
    public static SampledValidator of(String validatorDid, double reputation, double uptime) {
        return new SampledValidator(validatorDid, reputation, uptime);
    }

    /**
     * The sampling weight, {@code reputation × uptime} (§2.2).
     *
     * <p>Zero when either factor is zero, which is correct: a validator with no reputation or no
     * uptime is unsamplable, and {@link AResSampler} skips it. A newcomer avoids that only because
     * §2.5's cold-start floor starts its reputation above zero.
     *
     * @return the weight, in {@code [0, 1]}
     */
    public double weight() {
        return reputation * uptime;
    }

    private static void requireUnit(String name, double value) {
        // NaN fails the first comparison and is rejected with the rest: a NaN factor would make weight
        // NaN, and a NaN key sorts unpredictably, which is the last thing a sampling draw should do.
        if (!(value >= 0.0) || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1], was " + value);
        }
    }
}
