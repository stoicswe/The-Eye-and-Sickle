package io.github.stoicswe.eyeandsickle.server.federation.reputation;

/**
 * The AIMD reputation update and the separate uptime decay — {@code
 * docs/architecture/05-validator-quorum.md} §3 and §4, as pure arithmetic.
 *
 * <h2>Why three cases, not one formula</h2>
 *
 * §3 is emphatic that correct, divergent and equivocating votes need <em>different</em> treatment.
 * The shape is AIMD — additive-increase, multiplicative-decrease — the "reward slowly, punish fast"
 * pattern proven in TCP congestion control and in Tendermint-style BFT: a correct vote nudges
 * reputation up by a small fraction of the remaining distance to 1, a divergent vote cuts it by a
 * fixed proportion, and a proven equivocation slams it to the floor. Increase is deliberately far
 * slower than decrease so a validator cannot build trust cheaply and spend it in one betrayal.
 *
 * <h2>Why liveness is a fourth method on a different field</h2>
 *
 * §4: a validator that was sampled and stayed silent is <em>unavailable</em>, not dishonest. Its
 * penalty is a lighter multiplicative decay applied to {@code uptime}, never to {@code reputation}.
 * Keeping {@link #afterNoShow(double, double)} apart from the reputation methods, on a different
 * value, is the code-level expression of that separation — the two failure modes must never feed one
 * score.
 *
 * <h2>Pure and total</h2>
 *
 * No state, no I/O, no clock. Every method clamps its result into {@code [0, 1]} so a caller cannot
 * push a stored reputation or uptime out of the range the schema's CHECK constraint enforces, and so
 * rounding never escapes the bound. The coefficients are validated once, in {@link
 * io.github.stoicswe.eyeandsickle.server.federation.QuorumProperties}; these methods assume the range
 * but do not re-police it, beyond clamping the output.
 */
public final class ReputationRules {

    private ReputationRules() {}

    /**
     * A correct vote: {@code r ← r + α(1 − r)} (§3.1).
     *
     * <p>An exponential-moving-average step toward 1.0. At {@code r = 1} it is a fixed point (nothing
     * to gain); at {@code r = 0} it yields {@code α}. The gap {@code (1 − r)} shrinking as reputation
     * rises is what makes the climb asymptotic — the last stretch to full trust is the slowest, which
     * is the intended cost of a long clean record.
     *
     * @param reputation current reputation, in {@code [0, 1]}
     * @param alpha the increase coefficient {@code α}, in {@code (0, 1)}
     * @return the new reputation, clamped to {@code [0, 1]}
     */
    public static double afterCorrectVote(double reputation, double alpha) {
        return clampUnit(reputation + alpha * (1.0 - reputation));
    }

    /**
     * A divergent vote: {@code r ← r(1 − β)} (§3.2).
     *
     * <p>Multiplicative decrease. Proportional rather than absolute so it bites a high-reputation
     * validator harder in absolute terms while never driving a low one negative. Not catastrophic on
     * its own — divergence can be an honest race, and §3.2 says so — which is why it is not the
     * equivocation slash.
     *
     * @param reputation current reputation, in {@code [0, 1]}
     * @param beta the decrease coefficient {@code β}, in {@code (0, 1)}
     * @return the new reputation, clamped to {@code [0, 1]}
     */
    public static double afterDivergentVote(double reputation, double beta) {
        return clampUnit(reputation * (1.0 - beta));
    }

    /**
     * A proven equivocation: reputation is slashed to the floor (§3.3).
     *
     * <p>Implemented as {@code min(reputation, floor)}, not a bare assignment to {@code floor}. The
     * doc phrases it as "reputation = floor_value", but a slash must never <em>raise</em> a validator
     * that was already below the floor: getting caught double-signing cannot be a way to gain trust.
     * The {@code min} keeps it a punishment in every case, and never rises above what a plain
     * assignment would give.
     *
     * <p>This is the reputation half only. Equivocation also auto-flags the validator's server for
     * federation-wide non-recognition ({@code docs/architecture/03} §4); that flag is raised by the
     * adjudication service, not here.
     *
     * @param reputation current reputation, in {@code [0, 1]}
     * @param floor the equivocation floor, in {@code [0, 1)}
     * @return the slashed reputation, clamped to {@code [0, 1]}
     */
    public static double afterEquivocation(double reputation, double floor) {
        return clampUnit(Math.min(reputation, floor));
    }

    /**
     * A no-show: {@code uptime ← uptime(1 − γ)} (§4).
     *
     * <p>Applied to <strong>uptime</strong>, and returned as a new uptime — the type of the argument
     * is the reminder that this never touches reputation. Lighter than {@code β} because being
     * offline is a weaker signal than signing something wrong.
     *
     * @param uptime current uptime, in {@code [0, 1]}
     * @param gamma the decay coefficient {@code γ}, in {@code (0, 1)}
     * @return the new uptime, clamped to {@code [0, 1]}
     */
    public static double afterNoShow(double uptime, double gamma) {
        return clampUnit(uptime * (1.0 - gamma));
    }

    /**
     * Applies one conduct's reputation effect, or returns the reputation unchanged when the conduct
     * only affects uptime.
     *
     * <p>{@link ValidatorConduct#NO_SHOW} returns the input untouched, because §4 is explicit that a
     * no-show leaves reputation alone; its effect lives in {@link #afterNoShow(double, double)}
     * against uptime instead. This method exists so a caller iterating sampled validators can ask "what
     * does this conduct do to reputation" without re-deriving the case split, and get {@code NO_SHOW}
     * right by construction rather than by remembering to skip it.
     *
     * @param conduct what the validator did
     * @param reputation current reputation, in {@code [0, 1]}
     * @param alpha increase coefficient (§3.1)
     * @param beta divergence coefficient (§3.2)
     * @param floor equivocation floor (§3.3)
     * @return the new reputation
     */
    public static double applyToReputation(
            ValidatorConduct conduct, double reputation, double alpha, double beta, double floor) {
        return switch (conduct) {
            case CORRECT -> afterCorrectVote(reputation, alpha);
            case DIVERGENT -> afterDivergentVote(reputation, beta);
            case EQUIVOCATED -> afterEquivocation(reputation, floor);
            case NO_SHOW -> clampUnit(reputation);
        };
    }

    /**
     * Clamps a value into {@code [0, 1]}.
     *
     * <p>The schema constrains reputation and uptime to {@code [0, 1]}; a value that drifted out by a
     * rounding ulp would be refused by the CHECK constraint at write time and surface as a lost
     * update. Clamping here keeps the arithmetic inside the bound the database will accept. A NaN — a
     * coefficient that slipped past validation, say — collapses to 0 rather than propagating, because
     * a NaN reputation would make every later comparison false and quietly exclude the validator from
     * sampling forever.
     */
    private static double clampUnit(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        return value > 1.0 ? 1.0 : value;
    }
}
