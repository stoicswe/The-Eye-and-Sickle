package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * A miner the player has deployed onto someone else's machine.
 *
 * <p>Two invariants live in these fields. The miner's work is charged to the <em>host</em>, never the
 * deployer (I6) — so {@link #hostCycles} is not subtracted from the player's rig, while the control
 * channel is. And it is the only source of offline income (I5), which is why {@link #bufferedMinorUnits}
 * accrues while the player is away and why it stops dead at the cap.
 */
public final class MinerState {

    public String minerId = UUID.randomUUID().toString();

    /** Cycles consumed on the host machine. Invariant I6: this is not the deployer's cost. */
    public long hostCycles = 8L;

    public Instant deployedAt = Instant.now();

    /** When yield was last swept into the buffer. Drives offline accrual on load. */
    public Instant lastAccruedAt = Instant.now();

    /** Yield sitting on the host, waiting to be collected. Capped — see {@code Balance}. */
    public long bufferedMinorUnits = 0L;

    /** Hidden from routine listings but not from a manual audit ({@code docs/design/09}). */
    public boolean rootkitWrapped = false;
}
