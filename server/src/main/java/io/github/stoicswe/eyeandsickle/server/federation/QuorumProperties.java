package io.github.stoicswe.eyeandsickle.server.federation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable number the validator quorum needs, in one bound properties class.
 *
 * <h2>Why one class and not scattered constants</h2>
 *
 * {@code docs/architecture/05-validator-quorum.md} §6 is explicit that {@code α}, {@code β}, {@code
 * γ}, the floor, the newcomer initialisation and {@code N} are <em>recommended starting points</em>
 * to be tuned against real federation behaviour — not invariants. A value that is meant to be tuned
 * must have exactly one home, or tuning it means hunting through the code for the copies that did not
 * get changed. These bind to {@code eyeandsickle.federation.quorum.*} in {@code application.yml},
 * whose {@code federation} profile already lists the same defaults.
 *
 * <h2>These are not client-facing rules</h2>
 *
 * A client is told duel outcomes, never the coefficients that produced a reputation (Invariant I14).
 * Nothing here is sent as a rule a client may re-apply.
 *
 * @param committeeSize {@code N}, how many validators are sampled per duel. §1 recommends 7, giving
 *     {@code f = 2} and a 5-of-7 threshold. Kept configurable because §6 says so, but a committee of
 *     the form {@code 3f+1} is what makes the BFT tolerance clean.
 * @param reputationIncreaseAlpha {@code α} in {@code r ← r + α(1−r)} for a correct vote (§3.1). Small
 *     on purpose (recommended 0.05): trust builds over dozens of duels so a validator cannot rocket
 *     to high trust right before defecting. Must be in {@code (0, 1)} — at {@code 1} a single correct
 *     vote jumps straight to full trust, defeating the slow-build intent; at {@code 0} reputation
 *     never recovers.
 * @param reputationDecreaseBeta {@code β} in {@code r ← r(1−β)} for a divergent vote (§3.2).
 *     Recommended 0.2–0.3. Divergence is honest disagreement or staleness, not proven malice, so it
 *     must not be catastrophic on its own. Must be in {@code (0, 1)}.
 * @param uptimeDecayGamma {@code γ} in {@code uptime ← uptime(1−γ)} for a no-show (§4). Lighter than
 *     {@code β} (recommended 0.1) and applied to <em>uptime</em>, never reputation: being offline is
 *     unavailability, not dishonesty, and §4 exists precisely to keep the two from being conflated.
 *     Must be in {@code (0, 1)}.
 * @param equivocationFloor the reputation an equivocating validator is slashed to (§3.3). The hard
 *     slash: equivocation is cryptographically provable (two conflicting signatures exist), so it
 *     gets no benefit of the doubt. Recommended 0.1. Must be in {@code [0, 1)} and below {@link
 *     #newcomerReputation} — a slash that landed a validator above a newcomer would reward getting
 *     caught.
 * @param newcomerReputation the reputation a freshly enrolled validator starts at — the cold-start
 *     floor (§2.5). Must be strictly positive, or a new validator has sampling weight zero, is never
 *     sampled, can never earn reputation, and the pool deadlocks. Recommended 0.3–0.5.
 */
@ConfigurationProperties(prefix = "eyeandsickle.federation.quorum")
public record QuorumProperties(
        Integer committeeSize,
        Double reputationIncreaseAlpha,
        Double reputationDecreaseBeta,
        Double uptimeDecayGamma,
        Double equivocationFloor,
        Double newcomerReputation) {

    /** §1: {@code N = 7 → f = 2 → 5 of 7 must agree}. */
    public static final int DEFAULT_COMMITTEE_SIZE = 7;

    /** §3.1: slow, asymptotic trust-building. */
    public static final double DEFAULT_ALPHA = 0.05;

    /** §3.2: honest disagreement, not proven malice. */
    public static final double DEFAULT_BETA = 0.25;

    /** §4: lighter decay on liveness, not correctness. */
    public static final double DEFAULT_GAMMA = 0.10;

    /** §3.3: the hard slash for provable equivocation. */
    public static final double DEFAULT_EQUIVOCATION_FLOOR = 0.10;

    /** §2.5: the cold-start floor that keeps the pool from deadlocking. */
    public static final double DEFAULT_NEWCOMER_REPUTATION = 0.40;

    public QuorumProperties {
        // Boxed parameters with null-defaults so "unset" is distinguishable from "set to zero": a
        // zeroed coefficient is a silent behaviour change (no trust-building, or a permanent slash to
        // nothing), and it should read as a config error, not a game rule.
        committeeSize = committeeSize == null ? DEFAULT_COMMITTEE_SIZE : committeeSize;
        reputationIncreaseAlpha = reputationIncreaseAlpha == null ? DEFAULT_ALPHA : reputationIncreaseAlpha;
        reputationDecreaseBeta = reputationDecreaseBeta == null ? DEFAULT_BETA : reputationDecreaseBeta;
        uptimeDecayGamma = uptimeDecayGamma == null ? DEFAULT_GAMMA : uptimeDecayGamma;
        equivocationFloor = equivocationFloor == null ? DEFAULT_EQUIVOCATION_FLOOR : equivocationFloor;
        newcomerReputation = newcomerReputation == null ? DEFAULT_NEWCOMER_REPUTATION : newcomerReputation;

        if (committeeSize < 1) {
            throw new IllegalArgumentException(
                    "committee-size must be >= 1, was " + committeeSize + "; meaningful BFT needs a committee of the"
                            + " form 3f+1 (7 is recommended), but a single validator is the degenerate lower bound");
        }
        requireOpenUnitInterval("reputation-increase-alpha", reputationIncreaseAlpha);
        requireOpenUnitInterval("reputation-decrease-beta", reputationDecreaseBeta);
        requireOpenUnitInterval("uptime-decay-gamma", uptimeDecayGamma);
        if (!(equivocationFloor >= 0) || equivocationFloor >= 1) {
            throw new IllegalArgumentException("equivocation-floor must be in [0, 1), was " + equivocationFloor);
        }
        if (!(newcomerReputation > 0) || newcomerReputation > 1) {
            throw new IllegalArgumentException("newcomer-reputation must be in (0, 1], was " + newcomerReputation
                    + "; a non-positive floor removes the cold-start protection and deadlocks the pool (§2.5)");
        }
        if (newcomerReputation < equivocationFloor) {
            throw new IllegalArgumentException("newcomer-reputation (" + newcomerReputation
                    + ") must not be below equivocation-floor (" + equivocationFloor
                    + "); a fresh validator starting below the slash floor would rank a caught equivocator's equal");
        }
    }

    private static void requireOpenUnitInterval(String name, double value) {
        // NaN fails the first comparison, so this rejects it too rather than letting it poison later
        // arithmetic.
        if (!(value > 0) || value >= 1) {
            throw new IllegalArgumentException(name + " must be in (0, 1), was " + value);
        }
    }
}
