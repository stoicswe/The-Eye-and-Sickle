package io.github.stoicswe.eyeandsickle.solo.proc;

import io.github.stoicswe.eyeandsickle.solo.net.NetRules;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.EventLog;
import io.github.stoicswe.eyeandsickle.solo.rules.ScanRules;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Killing and restarting what is on the process table.
 *
 * <h2>The three verbs and the one price</h2>
 *
 * <ul>
 *   <li><b>Kill a tool of your own.</b> It stops where it is and <em>keeps what it had</em>: a scan
 *       names the parasites it had got to, a sweep reports the machines it had already reached. The
 *       cycles are <b>not</b> refunded and the thermal recovery is the full one for the full amount.
 *       Stopping early buys back your time, never your capacity — which is what stops "start
 *       everything, kill the losers" being free.
 *   <li><b>Kill a parasite.</b> The {@code kill} response from {@code docs/design/04-mining.md} §5:
 *       the miner goes, its cycles come back, and its buffer is <b>forfeit</b> — cracking is what
 *       takes the buffer, and a kill that also paid would make the crack pointless. This is the
 *       payoff for a manual audit and the reason the table exists.
 *   <li><b>Restart a daemon.</b> The rig needs it, so it cannot be killed. Restarting takes down
 *       every running tool that depends on it, and each of those is killed by the rule above —
 *       partial results, full recovery. That cost is what makes suspecting a system row a decision
 *       rather than a free click.
 * </ul>
 *
 * <h2>⚠ Partial results are a truncation of a frozen result, never a re-roll</h2>
 *
 * A scan's finding and a sweep's whole outcome are decided when the work is commissioned and stored
 * on the task, precisely so that quitting cannot change them. Killing early therefore <em>cuts the
 * stored answer down</em> in proportion to how far it got; it does not ask the rules for a new one. A
 * kill that re-rolled would be a reroll a player could force at will, which is the exploit every
 * frozen outcome in this engine exists to close.
 *
 * <h2>Refusals, never exceptions</h2>
 *
 * Same contract as {@code FolderRules}: an empty {@link Outcome} is success and a non-empty one is a
 * sentence the player reads. A rules engine that threw would be deciding how the client reports it.
 */
public final class ProcessRules {

    private ProcessRules() {}

    /** Empty on success; a sentence otherwise. {@code killed} counts what actually stopped. */
    public record Outcome(String why, int stopped) {

        static Outcome ok(int stopped) {
            return new Outcome("", stopped);
        }

        static Outcome refused(String why) {
            return new Outcome(why == null ? "" : why, 0);
        }

        public boolean refused() {
            return !why.isEmpty();
        }
    }

    // ================================================================== kill

    /**
     * Stops a process the player is allowed to stop.
     *
     * @param processId the id from {@code RigProcess}, which is prefixed by what it names —
     *     {@code task:}, {@code alloc:}, {@code miner:} or {@code sys:}
     */
    public static Outcome kill(SoloSave save, String processId, Instant now) {
        if (save == null || processId == null || processId.isBlank()) {
            return Outcome.refused("no such process");
        }
        String id = processId.trim();

        if (id.startsWith("sys:")) {
            // Named separately from "no such process" because the player did find it — they are
            // being told the rig needs it, which is a different and more useful fact.
            return Outcome.refused("the rig needs that process; it can be restarted, not killed");
        }
        if (id.startsWith("task:")) {
            return killTask(save, id.substring("task:".length()), now);
        }
        if (id.startsWith("miner:")) {
            return killMiner(save, id.substring("miner:".length()), now);
        }
        if (id.startsWith("alloc:")) {
            return killAllocation(save, id.substring("alloc:".length()), now);
        }
        return Outcome.refused("no such process");
    }

    /**
     * Ends a running tool early, keeping what it managed.
     *
     * <p>⚠ The recovery is dated from <b>now</b> and charged for the <b>full</b> cycles. The task's
     * own {@code endsAt} is irrelevant to it — the player is getting their session time back, not
     * their capacity, and a kill that shortened the recovery too would make starting work you intend
     * to abandon strictly better than not starting it.
     */
    private static Outcome killTask(SoloSave save, String taskId, Instant now) {
        for (TaskState task : List.copyOf(tasks(save))) {
            if (!task.taskId.equals(taskId)) {
                continue;
            }
            double progress = task.progressAt(now);
            String label = task.label;
            settlePartial(save, task, progress, now);
            EventLog.warning(
                    save,
                    "rig",
                    label + " killed at " + Math.round(progress * 100) + "%. "
                            + task.cycles + " cycles are recovering in full — stopping early returns "
                            + "your time, never your capacity.",
                    now);
            return Outcome.ok(1);
        }
        return Outcome.refused("that tool is no longer running");
    }

    /**
     * Applies a killed task's partial result and puts its cycles on the curve.
     *
     * <p>Dispatches on kind for the same reason {@code SoloGame.settleTasks} does: a task list with
     * more than one kind in it needs a switch, and the moment it grew a second kind it stopped
     * having one.
     */
    private static void settlePartial(SoloSave save, TaskState task, double progress, Instant now) {
        if ("sweep".equals(task.kind)) {
            NetRules.truncate(task, progress);
            NetRules.settleSweep(save, task, now);
        } else {
            ScanRules.truncate(task, progress);
            revealPartial(save, task);
            EventLog.notice(save, "scan", task.label + " (partial). " + ScanRules.finding(task), now);
        }
        save.tasks.remove(task);
        ComputeRules.beginRecovery(save.rig, task.allocationId, now);
    }

    /** Marks whatever a truncated audit still managed to name. Same rule as a completed one. */
    private static void revealPartial(SoloSave save, TaskState task) {
        if (task.foundMinerIds == null) {
            return;
        }
        for (MinerState miner : miners(save)) {
            if (task.foundMinerIds.contains(miner.minerId)) {
                miner.discovered = true;
            }
        }
    }

    /**
     * Kills a parasite — {@code docs/design/04-mining.md} §5's {@code kill} response.
     *
     * <p>⚠ Its buffer is <b>forfeit</b>, and that is the whole distinction from a crack. §5 gives the
     * player four responses to a discovered miner and prices them against each other; a kill that
     * also swept the buffer would collapse three of those four into one. What a kill buys is
     * <em>immediacy</em>: no breach, no attention, no puzzle, and it works the moment you spot the
     * row.
     *
     * <p>Killable whether or not an audit named it. That is the point — {@code 04} §3.1's "manual
     * audit still sees things a scan does not" has been a sentence with no mechanic behind it, and
     * this is the mechanic.
     */
    private static Outcome killMiner(SoloSave save, String minerId, Instant now) {
        for (MinerState miner : List.copyOf(miners(save))) {
            if (!miner.minerId.equals(minerId)) {
                continue;
            }
            ComputeRules.release(save.rig, miner.allocationId);
            save.rig.foreignMiners.remove(miner);
            EventLog.notice(
                    save,
                    "rig",
                    "killed " + processName(miner) + ": " + miner.hostCycles
                            + " cycles are yours again. Its buffer went with it — a crack takes the "
                            + "buffer, a kill just takes the process.",
                    now);
            return Outcome.ok(1);
        }
        return Outcome.refused("that process is no longer running");
    }

    /** What the log calls a parasite, which is what the player saw on the row. */
    private static String processName(MinerState miner) {
        if (!miner.discovered && !miner.disguiseName.isBlank()) {
            return miner.disguiseName;
        }
        return miner.label.isBlank() ? "an unregistered process" : miner.label;
    }

    /**
     * Releases a standing reservation the player made: self-mining, a defence, a held tool.
     *
     * <p>Self-mining is special-cased because it is not an allocation — it is a field — and because
     * {@code docs/design/04-mining.md} is explicit that the block in progress is forfeit when the
     * cycles come off. Everything else simply lets go.
     */
    private static Outcome killAllocation(SoloSave save, String allocationId, Instant now) {
        if ("self-mining".equals(allocationId)) {
            long was = save.rig.selfMiningCycles;
            if (was <= 0) {
                return Outcome.refused("nothing is self-mining");
            }
            save.rig.selfMiningCycles = 0L;
            EventLog.warning(
                    save,
                    "mining",
                    "self-mining stopped; " + was + " cycles released and the block in progress is forfeit.",
                    now);
            return Outcome.ok(1);
        }
        for (AllocationState allocation : List.copyOf(save.rig.allocations)) {
            if (!allocation.allocationId.equals(allocationId)) {
                continue;
            }
            if ("RECOVERING".equals(allocation.state)) {
                return Outcome.refused("those cycles are already on their way back");
            }
            String label = allocation.label.isBlank() ? "a reservation" : allocation.label;
            // Released onto the curve rather than returned outright: capacity that was committed is
            // capacity that has to cool, however the commitment ended. Same price as a killed task.
            ComputeRules.beginRecovery(save.rig, allocationId, now);
            EventLog.notice(save, "rig", label + " released; " + allocation.cycles + " cycles are recovering.", now);
            return Outcome.ok(1);
        }
        return Outcome.refused("that process is no longer running");
    }

    // ================================================================== restart

    /**
     * Restarts a daemon, taking down everything that depended on it.
     *
     * <p>⚠ The cascade is the cost, and it is charged in the same currency a direct kill is: each
     * dependent tool ends where it stands, keeps what it had, and its cycles recover in full. A
     * restart that were free would make "restart everything" the optimal opening move of every
     * audit, and the table would stop being a decision.
     *
     * <p>A daemon with no {@code provides} takes nothing down, which is most of them. The two that do
     * are the ones a tool genuinely needs — the interface a sweep sends through, and the thing an
     * audit asks about other processes.
     */
    public static Outcome restart(SoloSave save, String processId, Instant now) {
        if (save == null || processId == null || !processId.startsWith("sys:")) {
            return Outcome.refused("only a system process can be restarted");
        }
        String name = processId.substring("sys:".length());
        SystemProcesses.Daemon daemon = SystemProcesses.all().stream()
                .filter(d -> d.name().equals(name))
                .findFirst()
                .orElse(null);
        if (daemon == null) {
            return Outcome.refused("no such process");
        }

        List<TaskState> dependents = new ArrayList<>();
        if (!daemon.provides().isBlank()) {
            for (TaskState task : tasks(save)) {
                if (daemon.provides().equals(facilityOf(task))) {
                    dependents.add(task);
                }
            }
        }
        for (TaskState task : dependents) {
            double progress = task.progressAt(now);
            String label = task.label;
            settlePartial(save, task, progress, now);
            EventLog.warning(
                    save,
                    "rig",
                    label + " went down with " + daemon.name() + " at " + Math.round(progress * 100)
                            + "%. It kept what it had; its cycles recover in full.",
                    now);
        }
        EventLog.notice(
                save,
                "rig",
                daemon.name() + " restarted"
                        + (dependents.isEmpty() ? "." : ", taking " + dependents.size() + " with it."),
                now);
        return Outcome.ok(dependents.size());
    }

    /**
     * Which daemon a running tool is standing on.
     *
     * <p>Derived from the task's kind rather than stored, because it is a fact about the <em>sort</em>
     * of work rather than about the instance: every sweep needs the interface, every audit needs the
     * process table. Storing it per task would be a copy that could disagree with the kind beside it.
     */
    private static String facilityOf(TaskState task) {
        return "sweep".equals(task.kind) ? SystemProcesses.FACILITY_NET : SystemProcesses.FACILITY_AUDIT;
    }

    /** How long the daemon takes to come back. Cosmetic today; the seam if it ever is not. */
    public static Duration restartDelay() {
        return Duration.ZERO;
    }

    private static List<TaskState> tasks(SoloSave save) {
        return save.tasks == null ? List.of() : save.tasks;
    }

    private static List<MinerState> miners(SoloSave save) {
        return save.rig == null || save.rig.foreignMiners == null ? List.of() : save.rig.foreignMiners;
    }
}
