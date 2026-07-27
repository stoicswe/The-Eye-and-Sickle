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

    // ------------------------------------------------------------------ the crack (design/04 §5.1)

    /**
     * How hard this miner is to crack, on the shared 1–5 scale.
     *
     * <p>{@code docs/design/04-mining.md} §5.1: "Difficulty scales with miner tier, raised further
     * by Rootkit Wrapper (which gives that item a defensive-denial role)." Both halves are read by
     * {@code Targets.available}: the wrapper adds a tier, capped at the top of the scale.
     */
    public int tier = 1;

    /** What the readout calls it. Already in the operator's vocabulary before the crack starts. */
    public String label = "";

    /**
     * The {@code DEPLOYED_MINER} allocation this miner steals through, when it is running on the
     * player's own rig.
     *
     * <p>Empty for a miner the player deployed elsewhere — that one costs the <em>host</em>, and
     * the deployer pays a separate {@code CONTROL_CHANNEL} reservation instead (Invariant I6, and
     * the two must never be summed into one number).
     *
     * <p>Cracking or killing a foreign miner releases this allocation, which is what
     * {@code docs/design/04-mining.md} §5's "compute reclaimed" column means in the ledger.
     */
    public String allocationId = "";

    /**
     * Who planted it.
     *
     * <p>Load-bearing on a <em>failed</em> crack: {@code docs/design/04-mining.md} §5.1's dead-man
     * switch "flushes the buffer to the deployer immediately and the miner self-destructs", and
     * "the deployer is alerted with the host's handle attached — feeding bounty/retaliation
     * options". Without a deployer there is nobody for the buffer to go to and nobody to learn your
     * handle, and §5.1 is explicit that without that consequence "cracking would strictly dominate
     * killing".
     */
    public String deployerHandle = "";
}
