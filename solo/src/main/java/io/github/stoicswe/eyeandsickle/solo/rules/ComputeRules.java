package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.RigState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The rig's capacity ledger — allocate, release, recover, reconcile.
 *
 * <h2>Why this class is fussy about arithmetic</h2>
 *
 * {@code docs/design/01-core-resources.md} §1.4 makes the compute readout mandatory and always
 * visible, and {@code docs/design/04-mining.md} §3.1 goes further: the player must be able to catch a
 * hidden miner by noticing that the numbers <em>do not add up</em>. Both of those depend on the
 * budget reconciling exactly — total = available + allocated + recovering — because a HUD that is
 * routinely off by one teaches the player to ignore discrepancies, which disables the game's central
 * investigation.
 *
 * <p>{@link ComputeBudget} enforces the ceiling itself and refuses over-subscription, so a bug here
 * fails loudly at construction rather than rendering a quietly wrong number. That is deliberate: this
 * is the one readout where being wrong silently is worse than crashing.
 */
public final class ComputeRules {

    private ComputeRules() {}

    /** Cycles currently held by an active allocation, plus whatever is committed to self-mining. */
    public static long activeCycles(RigState rig) {
        long sum = rig.selfMiningCycles;
        for (AllocationState a : rig.allocations) {
            if ("ACTIVE".equals(a.state)) {
                sum += a.cycles;
            }
        }
        return sum;
    }

    /** Cycles on their way back under the Thermal Budget curve — neither held nor available. */
    public static long recoveringCycles(RigState rig) {
        long sum = 0L;
        for (AllocationState a : rig.allocations) {
            if ("RECOVERING".equals(a.state)) {
                sum += a.cycles;
            }
        }
        return sum;
    }

    /** What is left to commit right now. Never negative. */
    public static long availableCycles(RigState rig) {
        return Math.max(0L, rig.totalCycles - activeCycles(rig) - recoveringCycles(rig));
    }

    /** allocated ÷ total, for the recovery curve. */
    public static double loadFactor(RigState rig) {
        if (rig.totalCycles <= 0) {
            return 1.0d;
        }
        return (double) activeCycles(rig) / (double) rig.totalCycles;
    }

    /**
     * Reserves cycles for as long as the consumer runs.
     *
     * @return the allocation, or {@code null} if the rig cannot afford it
     */
    public static AllocationState reserve(RigState rig, ComputeConsumer consumer, String label, long cycles) {
        if (cycles <= 0 || availableCycles(rig) < cycles) {
            return null;
        }
        AllocationState a = new AllocationState();
        a.consumer = consumer.name();
        a.label = label;
        a.cycles = cycles;
        a.state = "ACTIVE";
        rig.allocations.add(a);
        return a;
    }

    /**
     * Spends cycles on a discrete action; they return on the Thermal Budget curve rather than at once.
     *
     * <p>The load factor is read <em>before</em> the spend is recorded, which is the honest reading of
     * "recovery is slower the closer the rig sits to capacity": the cost of being busy is charged
     * against the state you were in when you chose to act.
     *
     * @return the recovering allocation, or {@code null} if the rig cannot afford it
     */
    public static AllocationState spend(
            RigState rig, ComputeConsumer consumer, String label, long cycles, Instant now) {
        if (cycles <= 0 || availableCycles(rig) < cycles) {
            return null;
        }
        double load = loadFactor(rig);
        Duration recovery = ThermalRules.recoveryTime(cycles, load, rig.thermalBudget);

        AllocationState a = new AllocationState();
        a.consumer = consumer.name();
        a.label = label;
        a.cycles = cycles;
        a.state = "RECOVERING";
        // The engine's clock, never Instant.now(). A rules engine that reads the wall clock behind
        // its caller's back cannot be tested deterministically and — worse — disagrees with itself
        // about what time it is, so a scan started "now" can outlive a tick that happens "later".
        a.recoversAt = now.plus(recovery);
        rig.allocations.add(a);
        return a;
    }

    /** Releases a held reservation — a bot stopped, a defence disarmed, a tool unequipped. */
    public static boolean release(RigState rig, String allocationId) {
        return rig.allocations.removeIf(a -> a.allocationId.equals(allocationId));
    }

    /**
     * Drops every recovering allocation whose time has come.
     *
     * <p>Called on tick and on load. Doing it on load is what makes a save resumed after a week come
     * back with a full rig instead of one still nursing last Tuesday's scan.
     *
     * @return cycles returned to the pool
     */
    public static long settleRecovered(RigState rig, Instant now) {
        long returned = 0L;
        for (Iterator<AllocationState> it = rig.allocations.iterator(); it.hasNext(); ) {
            AllocationState a = it.next();
            if ("RECOVERING".equals(a.state) && a.recoversAt != null && !a.recoversAt.isAfter(now)) {
                returned += a.cycles;
                it.remove();
            }
        }
        return returned;
    }

    /**
     * Builds the immutable snapshot the client renders.
     *
     * <p>This is the seam that makes the local and remote sessions indistinguishable: the rig monitor
     * binds to a {@link ComputeBudget} and never learns where it came from. Self-mining is emitted as
     * a synthetic allocation rather than tracked separately, so it appears in the per-consumer
     * breakdown alongside everything else — a player should be able to see, in one column, that
     * self-mining is where their rig went.
     */
    public static ComputeBudget snapshot(SoloSave save) {
        RigState rig = save.rig;
        UUID rigId = UUID.fromString(rig.rigId);
        List<ComputeAllocation> out = new ArrayList<>();

        if (rig.selfMiningCycles > 0) {
            out.add(new ComputeAllocation(
                    UUID.nameUUIDFromBytes(("self-mining:" + rig.rigId).getBytes()),
                    rigId,
                    null,
                    ComputeConsumer.SELF_MINING,
                    null,
                    Cycles.of(rig.selfMiningCycles),
                    ComputeAllocation.State.ACTIVE,
                    null));
        }

        for (AllocationState a : rig.allocations) {
            boolean recovering = "RECOVERING".equals(a.state);
            out.add(new ComputeAllocation(
                    UUID.fromString(a.allocationId),
                    rigId,
                    null,
                    ComputeConsumer.valueOf(a.consumer),
                    null,
                    Cycles.of(a.cycles),
                    recovering ? ComputeAllocation.State.RECOVERING : ComputeAllocation.State.ACTIVE,
                    recovering ? a.recoversAt : null));
        }

        return new ComputeBudget(rigId, Cycles.of(rig.totalCycles), Cycles.of(availableCycles(rig)), out);
    }
}
