package io.github.stoicswe.eyeandsickle.server.compute;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one home for the calibrated numbers this slice needs.
 *
 * <h2>Why a properties class and not scattered constants</h2>
 *
 * {@code CLAUDE.md}'s working agreement: the economy figures are calibrated <em>as a set</em>, so a
 * number frozen into three constants in three places cannot be re-checked or tuned. Everything the
 * compute ledger needs a number for lives here, once, and a self-hoster can override it without a
 * rebuild. None of it is ever sent to a client (Invariant I14): a client is told outcomes, never the
 * arithmetic.
 *
 * <h2>What is established, and what is [PROPOSAL]</h2>
 *
 * <ul>
 *   <li>{@code controlChannelCycles} — <b>established</b>. {@code docs/design/04-mining.md} §2 fixes
 *       the deployer's per-miner control channel at 3 cycles. It is the mechanism behind the
 *       self-correcting network cap (§2.2), so it is a balance value, not a magic number to inline.
 *   <li>{@code startingRigCycles} — <b>established</b>. {@code docs/design/01-core-resources.md} §1: a
 *       starting rig is 100 cycles.
 *   <li>the three {@code recovery*} fields — <b>[PROPOSAL]</b>. §1.4 gives the shape
 *       ({@code base_rate * (1 - load_factor)^k}) but states the numbers "exist to be playtested".
 *       These are starting figures chosen to honour the shape (superlinear pain near capacity), not
 *       decisions; see {@link LoadFactorThermalRecovery}. Recorded for the integrator to log against
 *       {@code docs/design/15-open-questions.md}.
 * </ul>
 *
 * @param controlChannelCycles cycles a deployer reserves per live deployed miner ({@code
 *     docs/design/04-mining.md} §2); defaults to {@value #DEFAULT_CONTROL_CHANNEL_CYCLES}
 * @param startingRigCycles the compute ceiling of a freshly provisioned rig ({@code
 *     docs/design/01-core-resources.md} §1); defaults to {@value #DEFAULT_STARTING_RIG_CYCLES}
 * @param recoveryBaseCyclesPerSecond [PROPOSAL] cycles returned per second, per thermal tier, at zero
 *     load — the {@code base_rate} of §1.4; defaults to {@value #DEFAULT_RECOVERY_BASE_CYCLES_PER_SECOND}
 * @param recoveryLoadExponent [PROPOSAL] the {@code k} that makes the penalty superlinear near
 *     capacity; defaults to {@value #DEFAULT_RECOVERY_LOAD_EXPONENT}
 * @param recoveryMaxLoadFactor [PROPOSAL] the load factor the curve is clamped to, so a fully loaded
 *     rig still recovers eventually rather than dividing by zero; defaults to {@value
 *     #DEFAULT_RECOVERY_MAX_LOAD_FACTOR}
 */
@ConfigurationProperties(prefix = "eyeandsickle.compute")
public record ComputeProperties(
        Integer controlChannelCycles,
        Integer startingRigCycles,
        Double recoveryBaseCyclesPerSecond,
        Double recoveryLoadExponent,
        Double recoveryMaxLoadFactor) {

    /** {@code docs/design/04-mining.md} §2. */
    public static final int DEFAULT_CONTROL_CHANNEL_CYCLES = 3;

    /** {@code docs/design/01-core-resources.md} §1. */
    public static final int DEFAULT_STARTING_RIG_CYCLES = 100;

    /** [PROPOSAL] starting figure — see the class Javadoc. */
    public static final double DEFAULT_RECOVERY_BASE_CYCLES_PER_SECOND = 0.2;

    /** [PROPOSAL] starting figure — {@code k} in {@code (1 - load)^k}. */
    public static final double DEFAULT_RECOVERY_LOAD_EXPONENT = 2.0;

    /** [PROPOSAL] starting figure — the clamp that keeps recovery finite at full load. */
    public static final double DEFAULT_RECOVERY_MAX_LOAD_FACTOR = 0.95;

    public ComputeProperties {
        // Boxed parameters with null-defaults so "not configured" is distinguishable from "configured
        // to zero" — a zero control channel would make deployed mining free and quietly delete the
        // network cap (docs/design/04 §2.2), which would read as a game balance bug, not a config error.
        controlChannelCycles = controlChannelCycles == null ? DEFAULT_CONTROL_CHANNEL_CYCLES : controlChannelCycles;
        startingRigCycles = startingRigCycles == null ? DEFAULT_STARTING_RIG_CYCLES : startingRigCycles;
        recoveryBaseCyclesPerSecond = recoveryBaseCyclesPerSecond == null
                ? DEFAULT_RECOVERY_BASE_CYCLES_PER_SECOND
                : recoveryBaseCyclesPerSecond;
        recoveryLoadExponent = recoveryLoadExponent == null ? DEFAULT_RECOVERY_LOAD_EXPONENT : recoveryLoadExponent;
        recoveryMaxLoadFactor =
                recoveryMaxLoadFactor == null ? DEFAULT_RECOVERY_MAX_LOAD_FACTOR : recoveryMaxLoadFactor;

        if (controlChannelCycles <= 0) {
            throw new IllegalArgumentException("eyeandsickle.compute.control-channel-cycles must be positive, was "
                    + controlChannelCycles + "; a free control channel removes the deployed-mining network cap"
                    + " (docs/design/04-mining.md §2.2)");
        }
        if (startingRigCycles <= 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.compute.starting-rig-cycles must be positive, was " + startingRigCycles);
        }
        if (recoveryBaseCyclesPerSecond <= 0.0 || !Double.isFinite(recoveryBaseCyclesPerSecond)) {
            throw new IllegalArgumentException("eyeandsickle.compute.recovery-base-cycles-per-second must be a"
                    + " positive, finite number, was " + recoveryBaseCyclesPerSecond
                    + "; a non-positive base rate means spent cycles never come back");
        }
        if (recoveryLoadExponent < 0.0 || !Double.isFinite(recoveryLoadExponent)) {
            throw new IllegalArgumentException("eyeandsickle.compute.recovery-load-exponent must be a"
                    + " non-negative, finite number, was " + recoveryLoadExponent);
        }
        // Strictly below 1: at load factor 1 the term (1 - load)^k is zero and recovery would take
        // forever. The clamp is the guard, so it must itself leave headroom.
        if (!(recoveryMaxLoadFactor > 0.0) || !(recoveryMaxLoadFactor < 1.0)) {
            throw new IllegalArgumentException("eyeandsickle.compute.recovery-max-load-factor must be strictly"
                    + " between 0 and 1, was " + recoveryMaxLoadFactor
                    + "; at 1 the recovery curve divides by zero");
        }
    }
}
