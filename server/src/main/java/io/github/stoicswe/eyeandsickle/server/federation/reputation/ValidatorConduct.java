package io.github.stoicswe.eyeandsickle.server.federation.reputation;

/**
 * How a sampled validator behaved in one duel — the four cases {@code
 * docs/architecture/05-validator-quorum.md} §3 and §4 treat <em>differently</em>, deliberately not
 * collapsed into one score.
 *
 * <p>The whole point of §3–§4 is that these are not degrees of one thing. Correctness (§3) moves
 * {@code reputation}; liveness (§4) moves {@code uptime}; and equivocation (§3.3) is not a worse
 * divergence but a categorically different, cryptographically proven, event. {@link ReputationRules}
 * applies each with its own arithmetic, and {@link
 * io.github.stoicswe.eyeandsickle.server.federation.QuorumAdjudicator} decides which one a validator
 * earned.
 */
public enum ValidatorConduct {

    /**
     * Signed the outcome the quorum ultimately agreed on. Reputation increases slowly and
     * asymptotically (§3.1, {@code α}). Touches reputation only.
     */
    CORRECT,

    /**
     * Signed exactly one outcome, but not the one the threshold reached. Honest disagreement or
     * staleness — a race or lag can cause it — so the penalty is a single multiplicative decrease
     * (§3.2, {@code β}), not catastrophic. Touches reputation only.
     */
    DIVERGENT,

    /**
     * Signed two conflicting outcomes for the same duel. Both signatures exist, so this is proof, not
     * suspicion: the hard slash to the floor (§3.3), and it auto-flags the validator's server for
     * federation-wide non-recognition ({@code docs/architecture/03} §4). Touches reputation only; the
     * flag is raised separately.
     */
    EQUIVOCATED,

    /**
     * Was sampled but did not respond. Unavailability, not dishonesty — so a lighter decay on {@code
     * uptime} (§4, {@code γ}) and <strong>reputation is left untouched</strong>. Conflating this with
     * {@link #DIVERGENT} is exactly the mistake §4 forbids.
     */
    NO_SHOW
}
