package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.Objects;
import java.util.UUID;

/**
 * The authoritative per-rig compute arithmetic, read from the {@code rig_compute_reconciliation} view.
 *
 * <h2>Why this is separate from {@link io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget}</h2>
 *
 * The wire-facing {@code ComputeBudget} is what the player is <em>shown</em>: it carries only the
 * allocations the server chose to disclose, and its {@code available} is a non-negative {@link Cycles}.
 * This record is what the server <em>knows</em>: it sums <b>every</b> allocation charged to the rig —
 * including rows a rootkit-wrapped miner ({@code docs/design/09-defense-and-hardening.md}) keeps out
 * of the player's view — and its {@link #availableCycles} is a signed {@code long} that goes
 * <em>negative</em> on an over-subscribed rig.
 *
 * <p>That signed figure is the manual-audit signal in its rawest form ({@code
 * docs/design/04-mining.md} §3.1, {@code docs/architecture/06-data-model.md} §1 constraint 4). {@link
 * Cycles} refuses negatives on purpose — a rig cannot have less than no capacity — so an
 * over-subscription cannot be expressed as one; a raw {@code long} keeps it observable instead of
 * throwing during a read, which is exactly what an audit needs.
 *
 * <p>The view sums all rows, so over the whole rig the arithmetic always closes: {@code total =
 * active + recovering + available}. The gap a player is meant to notice appears one layer up, when
 * this authoritative {@code available} is compared against the smaller set of allocations the server
 * discloses — that comparison is {@code ComputeBudget#unaccountedFor()}.
 *
 * @param rigId the rig
 * @param playerId the owning player
 * @param totalCycles the rig's ceiling
 * @param activeCycles cycles currently held by active allocations (all of them, disclosed or not)
 * @param recoveringCycles cycles spent and returning under the Thermal Budget curve (all of them)
 * @param availableCycles {@code total - active - recovering}; <b>signed</b>, negative when
 *     over-subscribed
 */
public record RigComputeReconciliation(
        UUID rigId,
        UUID playerId,
        Cycles totalCycles,
        Cycles activeCycles,
        Cycles recoveringCycles,
        long availableCycles) {

    public RigComputeReconciliation {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(totalCycles, "totalCycles");
        Objects.requireNonNull(activeCycles, "activeCycles");
        Objects.requireNonNull(recoveringCycles, "recoveringCycles");
    }

    /**
     * Whether more cycles are committed than the rig has — {@link #availableCycles} is negative.
     *
     * <p>The only thing that produces this is a parasite charged to the rig beyond its ceiling
     * (Invariant I6; {@link ComputeLedgerService#chargeHostForParasite}). It is a state, not an error:
     * a host whose numbers no longer add up is a host with something to find.
     *
     * @return {@code true} if the rig is over its ceiling
     */
    public boolean isOverSubscribed() {
        return availableCycles < 0L;
    }

    /**
     * Cycles free for a new allocation right now, clamped at zero for callers that cannot represent an
     * over-subscription.
     *
     * <p>Use {@link #availableCycles} when the sign matters (an audit); use this when feeding the
     * non-negative {@code available} of a {@link io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget}.
     *
     * @return {@code max(0, availableCycles)} as a {@link Cycles}
     */
    public Cycles availableForAllocation() {
        return Cycles.of(Math.max(0L, availableCycles));
    }
}
