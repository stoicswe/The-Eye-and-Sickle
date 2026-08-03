package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.AllocationState;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.RigState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
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

    // ================================================================== theft

    /**
     * Cycles being taken by processes that are not the player's.
     *
     * <p>⚠ Read off {@link RigState#foreignMiners} rather than off the {@code DEPLOYED_MINER}
     * allocations, and the difference is not cosmetic. A parasite that arrives on a rig with no room
     * gets planted <em>without</em> an allocation — {@code NetRules.counterHack} and
     * {@code Targets.plantTutorialMiner} both take that fallback deliberately, because "a parasite
     * that declined to install because the machine was busy would be the wrong lesson entirely". Its
     * appetite is real whether or not the ledger found room to record it, and it is the appetite that
     * should slow the machine down.
     *
     * <p>⚠ It counts a miner whether or not the player has <em>found</em> it. Every consequence of
     * theft — slower tools, slower recovery, work that will not start — is felt before the audit, and
     * has to be: those consequences are the only evidence a player has that an audit is worth
     * running.
     */
    public static long stolenCycles(RigState rig) {
        if (rig == null || rig.foreignMiners == null) {
            return 0L;
        }
        long sum = 0L;
        for (MinerState miner : rig.foreignMiners) {
            sum += Math.max(0L, miner.hostCycles);
        }
        return sum;
    }

    /**
     * {@link #stolenCycles} as a fraction of the rig, {@code [0, 1]}.
     *
     * <p>Takes the rig rather than the save because every caller — the recovery curve, the task
     * duration, the readout — is asking about one machine, and a signature that took the whole save
     * would invite somebody to sum a second rig's parasites into the first one's answer.
     */
    public static double stolenShare(RigState rig) {
        if (rig == null || rig.totalCycles <= 0) {
            return 0.0d;
        }
        return Math.min(1.0d, stolenCycles(rig) / (double) rig.totalCycles);
    }

    /**
     * How long a piece of work actually takes on this rig, given what is stealing from it.
     *
     * <p>{@code Balance.THEFT_SLOWDOWN} of 1.0 means a rig with half its capacity stolen runs
     * everything half again as slowly. Proportional and honest — the machine has less of itself to
     * give, so everything it does takes longer.
     *
     * <p>⚠ <b>Applied when the work is commissioned, so it is baked into the deadline.</b> That is
     * what makes it true offline as well as online: a scan started on an infested rig finishes late
     * whether or not the client was open while it ran, which is the same reasoning that freezes a
     * sweep's whole result at {@code beginSweep}. Re-deriving it at settlement would let a player
     * dodge the penalty by cracking the parasite while the task was in flight — and would make the
     * duration depend on whether anyone was watching.
     */
    public static long slowedSeconds(RigState rig, long baseSeconds) {
        if (baseSeconds <= 0) {
            return baseSeconds;
        }
        double factor = 1.0d + stolenShare(rig) * Balance.THEFT_SLOWDOWN;
        return Math.max(baseSeconds, Math.round(baseSeconds * factor));
    }

    // ================================================================== the ledger

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
     * Load factor as it will be once {@code allocationId} lets go — the rig the returning cycles are
     * actually coming home to.
     *
     * <p>Exists for {@link #beginRecovery}, and the exclusion is the whole point. A held allocation
     * is part of the load right up until it releases, so measuring load with it still counted would
     * charge a scan a recovery penalty <em>for its own cycles</em> — the rig would be slow to give
     * back exactly the capacity that was making it slow. That is a compounding cost nothing in
     * {@code docs/design/01-core-resources.md} §1.3 asks for, and it would make hold-then-recover
     * quietly more expensive than the doubling {@code UI-6} was decided on.
     */
    public static double loadFactorExcluding(RigState rig, String allocationId) {
        if (rig.totalCycles <= 0) {
            return 1.0d;
        }
        long sum = rig.selfMiningCycles;
        for (AllocationState a : rig.allocations) {
            if ("ACTIVE".equals(a.state) && !a.allocationId.equals(allocationId)) {
                sum += a.cycles;
            }
        }
        return (double) sum / (double) rig.totalCycles;
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
        Duration recovery =
                ThermalRules.recoveryTime(cycles, rig.totalCycles, load, rig.thermalBudget, stolenShare(rig));

        AllocationState a = new AllocationState();
        a.consumer = consumer.name();
        a.label = label;
        a.cycles = cycles;
        a.state = "RECOVERING";
        // The engine's clock, never Instant.now(). A rules engine that reads the wall clock behind
        // its caller's back cannot be tested deterministically and — worse — disagrees with itself
        // about what time it is, so a scan started "now" can outlive a tick that happens "later".
        a.recoversAt = now.plus(recovery);
        a.startedAt = now;
        rig.allocations.add(a);
        return a;
    }

    /** Releases a held reservation — a bot stopped, a defence disarmed, a tool unequipped. */
    public static boolean release(RigState rig, String allocationId) {
        return rig.allocations.removeIf(a -> a.allocationId.equals(allocationId));
    }

    /**
     * Turns a held allocation loose onto the Thermal Budget curve: {@code ACTIVE} → {@code
     * RECOVERING}, with the wait measured from {@code releasedAt}.
     *
     * <h2>Why this exists (UI-6)</h2>
     *
     * This is the second half of <b>hold-then-recover</b>, decided on 2026-07-26 and recorded in
     * {@code docs/design/04-mining.md} §3.2. Work with a real duration — a scan — now <em>holds</em>
     * its cycles while it runs and only then starts giving them back, instead of
     * {@link #spend}'s spend-and-recover-immediately. §3.2's own sentence is what forced it: a
     * Thorough Scan is meant to leave the player "effectively down 35 cycles for far longer than the
     * scan runs", which under spend-immediately was true only on an already-loaded rig — on a lean
     * one the cycles were back before the six-minute scan finished, which is the opposite of the
     * published asymmetry.
     *
     * <p><b>{@code releasedAt} is the task's end, not the caller's "now".</b> A scan that finished
     * while the game was closed must begin recovering when it <em>ended</em>, or a player away for a
     * week comes back to a rig still nursing a scan that completed on Tuesday. Same argument as
     * {@link #settleRecovered} settling on load.
     *
     * @return how long the recovery will take, or {@code null} if no such allocation is held
     */
    public static Duration beginRecovery(RigState rig, String allocationId, Instant releasedAt) {
        for (AllocationState a : rig.allocations) {
            if (!a.allocationId.equals(allocationId) || !"ACTIVE".equals(a.state)) {
                continue;
            }
            Duration recovery = ThermalRules.recoveryTime(
                    a.cycles,
                    rig.totalCycles,
                    loadFactorExcluding(rig, allocationId),
                    rig.thermalBudget,
                    stolenShare(rig));
            a.state = "RECOVERING";
            // Re-stamped, so the readout draws the recovery's own progress rather than counting from
            // when the scan started — the wait the player is now looking at began here.
            a.startedAt = releasedAt;
            a.recoversAt = releasedAt.plus(recovery);
            return recovery;
        }
        return null;
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
    public static ComputeBudget snapshot(GameSave save) {
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

        List<String> hidden = undiscoveredAllocationIds(rig);
        for (AllocationState a : rig.allocations) {
            // ⚠ An undiscovered parasite is OMITTED, not relabelled. See undiscoveredAllocationIds.
            if (hidden.contains(a.allocationId)) {
                continue;
            }
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

    /**
     * The allocation ids belonging to parasites no audit has named yet.
     *
     * <h2>Why they are dropped from the snapshot rather than anonymised</h2>
     *
     * A row that said {@code UNKNOWN 6C} would be the readout telling the player they are being
     * robbed, which is exactly the product {@code docs/design/04-mining.md} §3.2 sells audits for. So
     * the row is not published at all, and {@code available} is still computed from the real rig — the
     * cycles are gone, they are simply not attributed.
     *
     * <p>The consequence is deliberate and is the whole mechanic: {@link ComputeBudget#unaccountedFor}
     * becomes non-zero, so <b>claimed + recovering + free comes to less than the rig's ceiling</b>.
     * §3.1 calls noticing that "the game's second-strongest tutorial vector", and it only works if the
     * numbers normally reconcile — which they do, because this is the one thing in the engine that
     * makes them not.
     *
     * <p>⚠ {@code ComputeBudget}'s constructor permits under-reconciliation and rejects
     * over-reconciliation, so dropping rows here is safe by construction and adding phantom ones would
     * not be. That asymmetry is documented there and this is the caller it was written for.
     */
    private static List<String> undiscoveredAllocationIds(RigState rig) {
        List<String> out = new ArrayList<>();
        if (rig.foreignMiners == null) {
            return out;
        }
        for (MinerState miner : rig.foreignMiners) {
            if (!miner.discovered && miner.allocationId != null && !miner.allocationId.isBlank()) {
                out.add(miner.allocationId);
            }
        }
        return out;
    }
}
