package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The default disclosure policy: every allocation charged to a rig is shown to its owner.
 *
 * <h2>Why "disclose all" is the correct default, not a stub</h2>
 *
 * A server with no concealment mechanism has nothing to hide, and on such a server the compute ledger
 * should reconcile exactly — the true available cycles equal what the disclosed allocations account
 * for, so {@link io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget#unaccountedFor()} is
 * zero. That is the honest readout for the single-player and friends case, which is the majority of
 * deployments ({@code docs/architecture/03-server-and-federation.md}).
 *
 * <p>The manual-audit gap ({@code docs/design/04-mining.md} §3.1) only exists once a rootkit-wrapped
 * miner is deliberately concealed, and concealment is the defensive/deployed-mining slice's decision.
 * That slice supplies a replacement bean and {@link ComputeConfiguration} steps this one aside via
 * {@code @ConditionalOnMissingBean}. Until then, everything is visible, which is exactly right rather
 * than a placeholder.
 */
public final class DiscloseAllAllocations implements AllocationDisclosurePolicy {

    @Override
    public List<ComputeAllocation> disclosedTo(UUID rigId, List<ComputeAllocation> chargedAllocations) {
        Objects.requireNonNull(rigId, "rigId");
        // Defensive copy: the ledger reads this straight into a ComputeBudget, and the budget must not
        // share a mutable list with whatever the caller passed in.
        return List.copyOf(chargedAllocations);
    }
}
