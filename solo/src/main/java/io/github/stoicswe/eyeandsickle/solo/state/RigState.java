package io.github.stoicswe.eyeandsickle.solo.state;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The player's machine: its capacity, and every claim currently made against it. */
public final class RigState {

    public String rigId = UUID.randomUUID().toString();

    /**
     * Total capacity in cycles. Expands only through schematics and story milestones — never by
     * purchase (Invariant I1, I12), which is why nothing in the market catalogue writes to it.
     */
    public long totalCycles = Balance.STARTING_CYCLES;

    /** Rig stats from {@code docs/design/11-rig-infrastructure.md} §2. */
    public int thermalBudget = 1;

    public int memoryBuffer = 1;
    public int bandwidth = 1;

    /** Cycles the player has voluntarily committed to self-mining. Safe, silent, zero-heat (I4). */
    public long selfMiningCycles = 0L;

    public List<AllocationState> allocations = new ArrayList<>();

    /**
     * Miners somebody else planted on <em>this</em> rig, stealing its cycles.
     *
     * <p>The other side of Invariant I6. {@link #allocations} already holds the deployer's half —
     * a {@code CONTROL_CHANNEL} reservation on the deployer's rig — and this list is the host's
     * half: each miner here also holds a {@code DEPLOYED_MINER} allocation against
     * {@link #totalCycles}, which is why a foreign miner shows up in the compute readout as cycles
     * the player is not getting back.
     *
     * <p>That visibility is the point, not an oversight. {@code docs/design/04-mining.md} §3.1 makes
     * manual investigation work by requiring that "the discrepancy is always present in the data —
     * cycle totals that don't add up", and a parasite charged to nobody would leave no discrepancy
     * to find. The client already renders {@code DEPLOYED_MINER} as "Foreign miner / on your rig";
     * it had simply never had one to render.
     *
     * <p>A rootkit-wrapped miner ({@code docs/design/09-defense-and-hardening.md}) is hidden by
     * being absent from the disclosed <em>allocation list</em> while still consuming the cycles —
     * which is what {@code ComputeBudget.unaccountedFor()} exists to expose. This module does not
     * hide them yet; when it does, that is the mechanism.
     *
     * <p>These are what {@code breachTargets()} offers as crack targets ({@code 04} §5.1).
     */
    public List<MinerState> foreignMiners = new ArrayList<>();
}
