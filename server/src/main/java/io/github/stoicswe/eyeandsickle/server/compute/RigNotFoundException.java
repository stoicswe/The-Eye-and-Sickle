package io.github.stoicswe.eyeandsickle.server.compute;

import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when a rig id does not name a rig on this server.
 *
 * <p>Distinct from a generic data-access miss so the REST layer can answer 404 rather than 500: asking
 * about a rig that is not here is a client mistake, not a server fault.
 *
 * @param rigId the id that resolved to nothing
 */
public final class RigNotFoundException extends RuntimeException {

    private final UUID rigId;

    public RigNotFoundException(UUID rigId) {
        super("No rig " + rigId + " on this server");
        this.rigId = Objects.requireNonNull(rigId, "rigId");
    }

    /** @return the id that resolved to nothing */
    public UUID rigId() {
        return rigId;
    }
}
