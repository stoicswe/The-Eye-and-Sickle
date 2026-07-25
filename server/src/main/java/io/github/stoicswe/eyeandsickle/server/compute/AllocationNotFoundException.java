package io.github.stoicswe.eyeandsickle.server.compute;

import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when an allocation id does not name an allocation charged to the rig it was looked up through.
 *
 * <h2>Absence and mis-ownership are deliberately the same answer</h2>
 *
 * A release or spend targets an allocation <em>through</em> a rig ({@code
 * /rigs/{rigId}/compute/allocations/{allocationId}}). Both "no such allocation" and "that allocation
 * belongs to a different rig" resolve to this exception, and both surface as 404 — because telling a
 * caller "that allocation exists but is not yours" leaks the existence of another rig's allocation,
 * which is a reconnaissance gift on a server where knowing what another player is running is something
 * the Provenance Tracer is supposed to have to earn ({@code docs/design/07-recon-tools.md}).
 *
 * @param rigId the rig the allocation was looked up through
 * @param allocationId the allocation id that did not resolve under that rig
 */
public final class AllocationNotFoundException extends RuntimeException {

    private final UUID rigId;
    private final UUID allocationId;

    public AllocationNotFoundException(UUID rigId, UUID allocationId) {
        super("No allocation " + allocationId + " charged to rig " + rigId);
        this.rigId = Objects.requireNonNull(rigId, "rigId");
        this.allocationId = Objects.requireNonNull(allocationId, "allocationId");
    }

    /** @return the rig the allocation was looked up through */
    public UUID rigId() {
        return rigId;
    }

    /** @return the allocation id that did not resolve */
    public UUID allocationId() {
        return allocationId;
    }
}
