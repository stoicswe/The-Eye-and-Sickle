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
}
