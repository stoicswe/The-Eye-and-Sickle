package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * One claim on the rig's capacity.
 *
 * <p>Mirrors {@link io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation} deliberately:
 * the engine converts one to the other, so the client's compute readout is driven by the same shape
 * whether the session is local or remote. A player must not be able to tell, from the rig monitor,
 * which mode they are in.
 */
public final class AllocationState {

    public String allocationId = UUID.randomUUID().toString();
    public String consumer = "ACTIVE_TOOL";

    /** What the allocation is for, in words, for the rig monitor's per-consumer breakdown. */
    public String label = "";

    public long cycles = 0L;

    /** {@code ACTIVE} while held; {@code RECOVERING} while returning under the Thermal Budget curve. */
    public String state = "ACTIVE";

    /** Set only while recovering. Null otherwise — the two must agree, and the engine checks. */
    public Instant recoversAt;

    /**
     * When this allocation entered its current state.
     *
     * <p>Needed so a recovering allocation can be drawn as <em>progress</em> rather than as a bare
     * deadline: without a start there is no denominator, and the rig readout can say when cycles
     * come back but not how far through the wait it is. Null on saves written before this field
     * existed — the readout treats that as unknown-progress rather than as zero, because a bar that
     * reads 0% on a recovery that is nearly done is worse than a bar that admits it does not know.
     */
    public Instant startedAt;
}
