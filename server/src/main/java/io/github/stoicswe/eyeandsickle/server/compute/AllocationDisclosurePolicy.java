package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import java.util.List;
import java.util.UUID;

/**
 * Decides which of a rig's allocations its owner is shown — the seam that makes a rootkit-wrapped
 * miner invisible on the HUD while still charging the rig.
 *
 * <h2>Why disclosure is a policy, not a column</h2>
 *
 * A rootkit-wrapped deployed miner ({@code docs/design/09-defense-and-hardening.md}) hides by being
 * <em>absent</em> from the host's readout — but its allocation row still exists and is still charged,
 * because Invariant I6 says it consumes the host's compute whether or not the host can see it. So
 * concealment cannot live on {@code compute_allocations}; the row is always there. It is derived at
 * read time from {@code deployed_miners.rootkit_wrapped}, which is state this compute slice does not
 * own.
 *
 * <p>This interface is therefore the boundary: the compute ledger asks "of these allocations charged
 * to this rig, which may its owner see?" and the defensive/deployed-mining slice answers. Hiding a row
 * here is exactly what turns {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget#unaccountedFor()} from zero into the
 * manual-audit signal ({@code docs/design/04-mining.md} §3.1): the server still reports the true
 * available cycles (computed over <em>all</em> rows), so the cycles a hidden miner steals show up as a
 * gap the numbers cannot account for.
 *
 * <p>The default binding is {@link DiscloseAllAllocations}. A server with no concealment mechanism
 * discloses everything and reconciles exactly, which is correct — the gap only opens once something is
 * deliberately hidden.
 */
public interface AllocationDisclosurePolicy {

    /**
     * The subset of {@code chargedAllocations} the owner of {@code rigId} may be shown.
     *
     * <p>Every element of the returned list must be one of the inputs (so the compute ledger never
     * shows an allocation charged to a different rig) and each must remain charged to {@code rigId} —
     * the implementation filters, it does not rewrite. Filtering only ever <em>removes</em> rows;
     * adding or altering one would corrupt the ledger the audit reads against.
     *
     * @param rigId the rig whose owner is viewing the ledger
     * @param chargedAllocations every allocation charged to that rig, disclosed or not, as read from
     *     the database
     * @return the allocations to place on the owner's HUD; a subset, possibly all, of the input
     */
    List<ComputeAllocation> disclosedTo(UUID rigId, List<ComputeAllocation> chargedAllocations);
}
