package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when a rig is asked to reserve more cycles than it has free — the refusal that keeps compute
 * the master scarcity.
 *
 * <h2>Refused, never clamped</h2>
 *
 * A client that asks for 60 cycles on a rig with 40 free is told no; the server does not quietly hand
 * back 40 and pretend the request succeeded. Clamping would let a client discover a rig's exact free
 * capacity by watching what it got, and — worse — would let an over-ask silently under-power the thing
 * it was for. The whole point of an authoritative server (Invariant I14) is that the answer to an
 * impossible request is an error, not a best effort.
 *
 * <p>The requested and available amounts are carried so the REST layer can report both without
 * re-deriving them, and so a caller can decide whether to retry with a smaller request.
 *
 * @param rigId the rig that could not satisfy the request
 * @param requested what was asked for
 * @param available what was actually free (the authoritative figure, over all allocations)
 */
public final class InsufficientComputeException extends RuntimeException {

    private final UUID rigId;
    private final transient Cycles requested;
    private final transient Cycles available;

    public InsufficientComputeException(UUID rigId, Cycles requested, Cycles available) {
        super("Rig " + rigId + " cannot reserve " + requested + " cycles; only " + available
                + " are free. Compute is refused, not clamped (Invariant I14).");
        this.rigId = Objects.requireNonNull(rigId, "rigId");
        this.requested = Objects.requireNonNull(requested, "requested");
        this.available = Objects.requireNonNull(available, "available");
    }

    /** @return the rig that could not satisfy the request */
    public UUID rigId() {
        return rigId;
    }

    /** @return the cycles that were requested */
    public Cycles requested() {
        return requested;
    }

    /** @return the cycles that were actually free */
    public Cycles available() {
        return available;
    }
}
