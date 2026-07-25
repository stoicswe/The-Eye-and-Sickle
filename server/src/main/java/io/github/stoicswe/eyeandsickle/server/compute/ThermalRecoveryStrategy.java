package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Duration;

/**
 * How long spent cycles take to come back — the Thermal Budget curve ({@code
 * docs/design/01-core-resources.md} §1.3), behind a replaceable seam.
 *
 * <h2>Why an interface</h2>
 *
 * The recovery curve is still {@code [PROPOSAL]} (§1.4): the source design fixes only its <em>shape</em>
 * — "recovery is slower the closer the rig sits to capacity", a superlinear penalty as load approaches
 * 100% — and leaves the exact function to playtest. Pinning a formula behind this interface means the
 * formula can be swapped for a playtested one without any caller of {@link ComputeLedgerService#spend}
 * changing. The default implementation is {@link LoadFactorThermalRecovery}; a self-hoster or a later
 * balance pass can supply another bean.
 *
 * <h2>Time is an input, never read here</h2>
 *
 * This computes a <em>duration</em>, not a deadline. The service adds it to a clock it was given, so
 * that "when do these cycles return" is testable without waiting and without any balance logic reading
 * a wall clock — the same discipline the protocol enforces on {@code ComputeAllocation.recoversAt}.
 */
public interface ThermalRecoveryStrategy {

    /**
     * How long {@code spent} cycles take to fully return, given how loaded the rig remains and how good
     * its Thermal Budget is.
     *
     * <p>The contract the shape must honour ({@code docs/design/01-core-resources.md} §1.3):
     *
     * <ul>
     *   <li>monotonic in load — a heavier {@code remainingLoad} never recovers faster;
     *   <li>superlinear near capacity — the duration grows without bound as load approaches {@code
     *       total}, so an overextended rig is "effectively down those cycles for a long stretch";
     *   <li>monotonic in thermal tier — a higher {@code thermalBudgetTier} never recovers slower;
     *   <li>zero for zero — spending nothing takes no time.
     * </ul>
     *
     * @param spent the cycles that were just spent and are now recovering
     * @param remainingLoad the cycles still held by active allocations after this spend — the load the
     *     recovery curve is read against
     * @param total the rig's ceiling; the load factor is {@code remainingLoad / total}
     * @param thermalBudgetTier the rig's Thermal Budget tier (at least 1)
     * @return the time until the spent cycles are back in the pool; never negative
     */
    Duration recoveryDuration(Cycles spent, Cycles remainingLoad, Cycles total, int thermalBudgetTier);
}
