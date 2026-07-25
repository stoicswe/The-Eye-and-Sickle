package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Duration;
import java.util.Objects;

/**
 * The first-pass Thermal Budget curve: {@code rate = base * tier * (1 - load_factor)^k}, spent cycles
 * taking {@code spent / rate} seconds to return.
 *
 * <p><strong>[PROPOSAL].</strong> This implements the shape {@code docs/design/01-core-resources.md}
 * §1.4 sketches, with the constants in {@link ComputeProperties}. The source design fixes the shape —
 * a superlinear penalty as load approaches capacity — and explicitly leaves the numbers to playtest,
 * so this class exists to be replaced, not defended. It is registered behind {@link
 * ThermalRecoveryStrategy} precisely so a playtested curve can drop in without any caller changing.
 *
 * <h2>How the shape delivers "overextension is punished by the physics of the rig"</h2>
 *
 * The recovery <em>rate</em> falls as the load factor {@code (remainingLoad / total)} rises, and the
 * exponent {@code k} makes that fall steepen near capacity. Because the time to recover a fixed number
 * of cycles is the reciprocal of the rate, the <em>duration</em> blows up as load approaches 1: a lean
 * rig gets its cycles back quickly, an overextended one is down them for a long stretch — "exactly
 * when it can least afford to be" (§1.3). A Thorough Scan's 35 cycles ({@code docs/design/04-mining.md}
 * §3.2) recover cheaply on an idle rig and punishingly on a loaded one, which is the design intent that
 * gives scanning a real rather than nominal opportunity cost.
 *
 * <h2>The clamp, and why it is not optional</h2>
 *
 * At a load factor of 1 the term {@code (1 - load)^k} is 0 and the duration is infinite. Real rigs can
 * sit at or above full load — a parasite can push them past it (Invariant I6) — so the load factor is
 * clamped to {@link ComputeProperties#recoveryMaxLoadFactor} strictly below 1. A fully pinned rig then
 * recovers very slowly rather than never, which is punishing (the point) without being a soft lock.
 */
public final class LoadFactorThermalRecovery implements ThermalRecoveryStrategy {

    private final double baseCyclesPerSecond;
    private final double loadExponent;
    private final double maxLoadFactor;

    /**
     * @param properties the calibrated constants; the three {@code recovery*} fields are read here
     */
    public LoadFactorThermalRecovery(ComputeProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.baseCyclesPerSecond = properties.recoveryBaseCyclesPerSecond();
        this.loadExponent = properties.recoveryLoadExponent();
        this.maxLoadFactor = properties.recoveryMaxLoadFactor();
    }

    @Override
    public Duration recoveryDuration(Cycles spent, Cycles remainingLoad, Cycles total, int thermalBudgetTier) {
        Objects.requireNonNull(spent, "spent");
        Objects.requireNonNull(remainingLoad, "remainingLoad");
        Objects.requireNonNull(total, "total");
        if (total.isZero()) {
            // A zero-ceiling rig cannot exist (Rig enforces it); guarding anyway keeps the division
            // below honest rather than trusting an invariant maintained elsewhere.
            throw new IllegalArgumentException("recovery is undefined for a rig with no capacity");
        }
        if (thermalBudgetTier < 1) {
            throw new IllegalArgumentException("thermalBudgetTier is at least 1, was " + thermalBudgetTier);
        }
        if (spent.isZero()) {
            return Duration.ZERO;
        }

        double loadFactor = (double) remainingLoad.cycles() / (double) total.cycles();
        // A parasite (Invariant I6) can push the load past capacity, so clamp both ends: never below 0
        // (a negative would speed recovery up, which is nonsense), never at or above the configured
        // ceiling (which would make the reciprocal blow up to infinity).
        loadFactor = Math.clamp(loadFactor, 0.0, maxLoadFactor);

        double ratePerSecond = baseCyclesPerSecond * thermalBudgetTier * Math.pow(1.0 - loadFactor, loadExponent);
        // ratePerSecond is strictly positive: base > 0, tier >= 1, and (1 - loadFactor) > 0 because
        // maxLoadFactor < 1. So the division below cannot produce infinity or a negative.
        double seconds = spent.cycles() / ratePerSecond;

        // Round up: a rig is not "recovered" until the last cycle is back, so truncating would hand the
        // final cycle back a moment early. Cap at a very large but finite duration so an extreme
        // configuration cannot overflow Duration.ofSeconds.
        long wholeSeconds = (long) Math.min(Math.ceil(seconds), (double) MAX_RECOVERY_SECONDS);
        return Duration.ofSeconds(Math.max(1L, wholeSeconds));
    }

    /**
     * A finite ceiling on recovery time (100 days). Nothing in the design recovers this slowly; the cap
     * only stops a pathological configuration from overflowing {@link Duration}. It is not a balance
     * value — it is a numeric guard.
     */
    private static final long MAX_RECOVERY_SECONDS = 100L * 24L * 60L * 60L;
}
