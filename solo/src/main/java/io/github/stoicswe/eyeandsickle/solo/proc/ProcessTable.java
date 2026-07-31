package io.github.stoicswe.eyeandsickle.solo.proc;

import io.github.stoicswe.eyeandsickle.protocol.game.RigProcess;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything running on the rig, as rows — the manual audit {@code docs/design/04-mining.md} §3.1
 * has always described and nothing ever implemented.
 *
 * <h2>Four sources, one shape</h2>
 *
 * <ol>
 *   <li><b>System processes</b> ({@link SystemProcesses}) — FreeBSD's kernel threads and daemons plus
 *       the fiction's own. The haystack, and what a mimic hides among.
 *   <li><b>The player's own work</b> — running tasks, self-mining, armed defences, held tool
 *       allocations. Everything here is something they started and can therefore stop.
 *   <li><b>Parasites</b> — in whatever costume {@link Disguise} put them in. ⚠ They come out of this
 *       method looking exactly like the rows above them; nothing in {@link RigProcess} says which is
 *       which.
 *   <li><b>Nothing else.</b> There is no filler. Every row corresponds to something the engine
 *       genuinely models, which is what lets the table be cross-checked against the compute grid.
 * </ol>
 *
 * <h2>⚠ An undiscovered parasite is listed here and nowhere else</h2>
 *
 * It is absent from the compute ledger by design ({@code ComputeRules.snapshot}), so the grid comes
 * up short and says nothing. This table is the one place it is visible <em>at all</em> — wearing a
 * costume, among three dozen honest rows. The grid tells you something is wrong; this is where you go
 * to find out what.
 *
 * <h2>The figures move like a machine's, on a five-second tick</h2>
 *
 * {@link Vitals} does the arithmetic and its class note has the reasoning. In short: <b>gauges
 * wander</b> around a resting level rather than teleporting, <b>counters only ever increase</b>, and
 * everything is a pure function of {@code (identity, interval)} — so two reads inside the same tick
 * are identical, a player can compare two rows without racing them, and nothing here touches the
 * persisted {@code Rng}.
 *
 * <p>That movement is not decoration. Two of the five disguises are caught by comparing figures — one
 * row against another, or one row against itself a few ticks later — and both require the numbers to
 * be honest enough to compare.
 */
public final class ProcessTable {

    private ProcessTable() {}

    /** Where pids for things started since boot begin. Above every daemon's, as on a real machine. */
    private static final int USER_PID_BASE = 4_000;

    /** A kilobyte, for the per-interval I/O rates below. */
    private static final long KB = 1024L;

    private static final long MB = KB * 1024L;

    /**
     * The whole table.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — uptime, CPU time and the interval
     *     index are all measured against it, and a table that read the wall clock would report a test
     *     clock's processes as having run for months
     */
    public static List<RigProcess> of(SoloSave save, Instant now) {
        if (save == null || save.rig == null || now == null) {
            return List.of();
        }
        long interval = Vitals.intervalAt(now.getEpochSecond());
        // Uptime is measured from the character's creation, which is this machine's boot. A daemon's
        // counters have therefore been climbing since the save was made, which is why an hour-old
        // character shows a kernel with gigabytes of I/O behind it and a freshly planted parasite
        // does not.
        long upIntervals = intervalsSince(save.createdAt, now);
        long capacity = Math.max(1L, save.rig.totalCycles);

        List<RigProcess> out = new ArrayList<>();
        for (SystemProcesses.Daemon daemon : SystemProcesses.all()) {
            out.add(daemon(daemon, save, interval, upIntervals));
        }
        for (TaskState task : tasks(save)) {
            out.add(task(task, save, now, interval, capacity));
        }
        out.addAll(allocations(save, now, interval, capacity));
        for (MinerState miner : miners(save)) {
            out.add(parasite(miner, save, now, interval, capacity));
        }
        return List.copyOf(out);
    }

    // ================================================================== the haystack

    /**
     * A system process.
     *
     * <p>⚠ A kernel thread is modelled as one: almost no resident memory, and <b>no disk or network
     * counters at all</b>. That is true of the real thing and it is also load-bearing here — a
     * parasite claiming to be a bracketed kernel thread would have to show zero I/O to fit in, and it
     * cannot, because it is talking to whoever planted it.
     */
    private static RigProcess daemon(SystemProcesses.Daemon daemon, SoloSave save, long interval, long upIntervals) {

        long seed = Vitals.mix(save.characterId.hashCode(), daemon.name().hashCode());
        // A resting share in roughly [0.05, 1.4]% — daemons mostly sleep. The kernel's own threads
        // sit higher, which is what a real table looks like and why sorting by %CPU does not simply
        // list every daemon in name order.
        double resting = (daemon.kernel() ? 0.35d : 0.12d) + Math.floorMod(seed, 90L) / 100.0d;
        double cpu = Vitals.gauge(seed, interval, resting, 0.55d);

        long ioIntervals = daemon.kernel() ? 0 : upIntervals;
        boolean networked = FACILITY_NET.equals(daemon.provides());

        return new RigProcess(
                "sys:" + daemon.name(),
                daemon.pid(),
                daemon.name(),
                daemon.user(),
                daemon.pid() <= 1 ? "" : (daemon.kernel() ? "[kernel]" : "init"),
                cpu,
                // ⚠ Banked at the RESTING share, not the wandering one — see Vitals.cpuTime. The
                // figure still agrees with the %CPU column beside it, which is exactly what
                // STOPPED_CLOCK fails to do, and it cannot tick backwards when the gauge dips.
                Vitals.cpuTime(seed, upIntervals, resting),
                Vitals.steps(seed, interval, daemon.threads(), daemon.kernel() ? 2 : 1, 12),
                (int) Vitals.gauge(seed >> 3, interval, daemon.kernel() ? 90 : 14, 0.9d),
                (long) Vitals.gauge(
                        seed >> 6,
                        interval,
                        daemon.kernel() ? 6L * MB : (10L + Math.floorMod(seed >> 9, 180L)) * MB,
                        0.06d),
                Vitals.steps(seed >> 12, interval, daemon.kernel() ? 8 : 60, 40, 24),
                Vitals.counter(seed >> 15, ioIntervals, daemon.kernel() ? 0 : 90L * KB),
                Vitals.counter(seed >> 18, ioIntervals, daemon.kernel() ? 0 : 34L * KB),
                Vitals.counter(seed >> 21, networked ? upIntervals : 0, 22L * KB),
                Vitals.counter(seed >> 24, networked ? upIntervals : 0, 61L * KB),
                Vitals.counter(seed >> 27, networked ? upIntervals : 0, 37L),
                Vitals.counter(seed >> 30, networked ? upIntervals : 0, 84L),
                0L,
                false,
                true,
                daemon.blurb());
    }

    private static final String FACILITY_NET = SystemProcesses.FACILITY_NET;

    // ================================================================== the player's own work

    /**
     * A running tool.
     *
     * <p>Killable, and the consequence is real: {@code ProcessRules.kill} settles it early with the
     * results it had managed, and the cycles still take their full recovery. Its resting CPU share is
     * derived from the cycles it actually holds, so this row and the compute grid agree.
     */
    private static RigProcess task(TaskState task, SoloSave save, Instant now, long interval, long capacity) {

        long seed = Vitals.mix(save.characterId.hashCode(), task.taskId.hashCode());
        double resting = Math.max(0.8d, 100.0d * task.cycles / capacity);
        double cpu = Vitals.gauge(seed, interval, resting, 0.35d);
        long ran = Math.max(0L, intervalsSince(task.startedAt, now));
        boolean networked = task.noiseCycles > 0;

        return new RigProcess(
                "task:" + task.taskId,
                USER_PID_BASE + Math.floorMod(task.taskId.hashCode(), 60_000),
                task.label,
                save.handle,
                "init",
                cpu,
                // Wall-clock, because a tool genuinely occupies the machine for its whole duration.
                // This is the honest baseline a STOPPED_CLOCK parasite is measured against.
                maxZero(Duration.between(task.startedAt, now)),
                Vitals.steps(seed >> 3, interval, 2, 4, 6),
                (int) Vitals.gauge(seed >> 6, interval, 22, 1.1d),
                (long) Vitals.gauge(seed >> 9, interval, (14L + task.cycles * 3L) * MB, 0.08d),
                Vitals.steps(seed >> 12, interval, 24, 40, 12),
                Vitals.counter(seed >> 15, ran, 140L * KB),
                Vitals.counter(seed >> 18, ran, 26L * KB),
                // ⚠ Only work that reaches other machines has traffic, and it is the same rule
                // NoiseRules uses. A local audit with packets on it is not a local audit.
                Vitals.counter(seed >> 21, networked ? ran : 0, 210L * KB),
                Vitals.counter(seed >> 24, networked ? ran : 0, 95L * KB),
                Vitals.counter(seed >> 27, networked ? ran : 0, 160L),
                Vitals.counter(seed >> 30, networked ? ran : 0, 70L),
                task.cycles,
                true,
                false,
                "your tool, running; killing it keeps what it has found so far");
    }

    /**
     * Everything else the player is spending on: self-mining, defences, held tool reservations.
     *
     * <p>⚠ Skips the allocation a running task already owns — otherwise a Thorough Scan appears twice,
     * once as itself and once as the cycles paying for it. ⚠ Also skips an <b>undiscovered
     * parasite's</b> allocation, because that one is emitted below in costume; emitting it here as
     * well would put the same theft on the table twice, once honestly labelled.
     */
    private static List<RigProcess> allocations(SoloSave save, Instant now, long interval, long capacity) {

        List<RigProcess> out = new ArrayList<>();
        List<String> hidden = parasiteAllocationIds(save);

        if (save.rig.selfMiningCycles > 0) {
            long seed = Vitals.mix(save.characterId.hashCode(), "self-mining".hashCode());
            long ran = Math.max(0L, intervalsSince(save.createdAt, now));
            double resting = Math.max(0.8d, 100.0d * save.rig.selfMiningCycles / capacity);
            double cpu = Vitals.gauge(seed, interval, resting, 0.18d);
            out.add(new RigProcess(
                    "alloc:self-mining",
                    USER_PID_BASE + 11,
                    "minerd",
                    save.handle,
                    "init",
                    cpu,
                    Vitals.cpuTime(seed >> 2, ran, resting),
                    Vitals.steps(seed >> 3, interval, 3, 2, 24),
                    (int) Vitals.gauge(seed >> 6, interval, 9, 1.0d),
                    (long) Vitals.gauge(seed >> 9, interval, (40L + save.rig.selfMiningCycles) * MB, 0.05d),
                    Vitals.steps(seed >> 12, interval, 70, 60, 24),
                    Vitals.counter(seed >> 15, ran, 180L * KB),
                    Vitals.counter(seed >> 18, ran, 340L * KB),
                    // I4: self-mining reaches nothing. No traffic, ever — and a "minerd" with packets
                    // on it is therefore not the player's.
                    0L,
                    0L,
                    0L,
                    0L,
                    save.rig.selfMiningCycles,
                    true,
                    false,
                    "self-mining; killing it releases the cycles and forfeits the block in progress"));
        }

        for (AllocationState allocation : save.rig.allocations) {
            if (hidden.contains(allocation.allocationId) || ownedByTask(save, allocation.allocationId)) {
                continue;
            }
            long seed = Vitals.mix(save.characterId.hashCode(), allocation.allocationId.hashCode());
            boolean recovering = "RECOVERING".equals(allocation.state);
            long ran = allocation.startedAt == null ? 0L : Math.max(0L, intervalsSince(allocation.startedAt, now));
            // Cooling, not working: a recovering row keeps a low resting figure that still moves,
            // because the cycles are genuinely doing something — coming back.
            double resting = recovering ? 0.3d : Math.max(0.5d, 100.0d * allocation.cycles / capacity);
            double cpu = Vitals.gauge(seed, interval, resting, recovering ? 0.8d : 0.22d);

            out.add(new RigProcess(
                    "alloc:" + allocation.allocationId,
                    USER_PID_BASE + Math.floorMod(allocation.allocationId.hashCode(), 60_000),
                    allocation.label.isBlank()
                            ? allocation.consumer.toLowerCase(java.util.Locale.ROOT)
                            : allocation.label,
                    save.handle,
                    "init",
                    cpu,
                    Vitals.cpuTime(seed >> 2, ran, resting),
                    Vitals.steps(seed >> 3, interval, 1, 3, 12),
                    (int) Vitals.gauge(seed >> 6, interval, 7, 1.2d),
                    (long) Vitals.gauge(seed >> 9, interval, (12L + allocation.cycles * 2L) * MB, 0.07d),
                    Vitals.steps(seed >> 12, interval, 32, 30, 24),
                    Vitals.counter(seed >> 15, ran, 40L * KB),
                    Vitals.counter(seed >> 18, ran, 12L * KB),
                    0L,
                    0L,
                    0L,
                    0L,
                    allocation.cycles,
                    // A recovering allocation is not doing anything to stop. Offering `kill` on
                    // cycles already on their way back would be a control with no verb.
                    !recovering,
                    false,
                    recovering ? "cycles returning under the thermal curve" : "held while it runs"));
        }
        return out;
    }

    // ================================================================== the needle

    /**
     * A parasite, in costume.
     *
     * <p>⚠ Everything that distinguishes it is <b>in the data on the row</b> — see {@link Disguise}.
     * It is killable like any of the player's own rows, because the reward for finding it is being
     * able to act without paying for a scan first.
     */
    private static RigProcess parasite(MinerState miner, SoloSave save, Instant now, long interval, long capacity) {

        Disguise disguise = Disguise.of(miner.disguise);
        long seed = Vitals.mix(save.characterId.hashCode(), miner.minerId.hashCode());
        long ran = Math.max(0L, intervalsSince(miner.deployedAt, now));
        double honest = Math.max(0.7d, 100.0d * miner.hostCycles / capacity);

        boolean unmasked = miner.discovered;
        String name = unmasked || miner.disguiseName.isBlank()
                ? (miner.label.isBlank() ? "unregistered" : miner.label)
                : miner.disguiseName;
        String user = unmasked || miner.disguiseUser.isBlank() ? "?" : miner.disguiseUser;

        Disguise effective = unmasked ? Disguise.RESOURCE_HOG : disguise;
        boolean greedy = effective == Disguise.RESOURCE_HOG || effective == Disguise.STOPPED_CLOCK;
        double resting = greedy ? Math.max(honest, 21.0d) : honest;
        double cpu = Vitals.gauge(seed, interval, resting, 0.30d);

        Duration cpuTime = effective == Disguise.STOPPED_CLOCK
                // ⚠ The tell. Every other row on this table banks processor time at roughly its own
                // %CPU; this one does not, because it has only ever CLAIMED to be busy. It creeps,
                // so a player watching it can see that it creeps — a figure pinned at zero would be
                // a different and much louder giveaway.
                ? Duration.ofMillis(Vitals.counter(seed >> 2, ran, 6L))
                : Vitals.cpuTime(seed >> 2, ran, resting);

        long memory =
                (long) Vitals.gauge(seed >> 9, interval, (greedy ? 780L : 24L + miner.hostCycles * 4L) * MB, 0.05d);

        return new RigProcess(
                "miner:" + miner.minerId,
                // ⚠ A user-range pid even when it claims to be a daemon. Every system process in the
                // table has a pid under a thousand because it started at boot; a "daemon" with a
                // five-figure pid started long after, which is a tell that comes free.
                USER_PID_BASE + Math.floorMod(miner.minerId.hashCode(), 60_000),
                name,
                user,
                "init",
                cpu,
                cpuTime,
                Vitals.steps(seed >> 3, interval, 2, 5, 6),
                // Busy-looping on somebody else's machine: the idle-wakeup column is high and stays
                // high, which is the tell nobody looks for and everybody finds eventually.
                (int) Vitals.gauge(seed >> 6, interval, 210, 0.7d),
                memory,
                Vitals.steps(seed >> 12, interval, 12, 20, 12),
                Vitals.counter(seed >> 15, ran, 30L * KB),
                Vitals.counter(seed >> 18, ran, 11L * KB),
                // It is talking to whoever planted it. A row that should be local and is not.
                Vitals.counter(seed >> 21, ran, 120L * KB),
                Vitals.counter(seed >> 24, ran, 48L * KB),
                Vitals.counter(seed >> 27, ran, 90L),
                Vitals.counter(seed >> 30, ran, 41L),
                miner.hostCycles,
                true,
                false,
                unmasked ? "identified as somebody else's miner; killing it forfeits its buffer" : "");
    }

    // ================================================================== lookups

    /** How many five-second intervals have elapsed between two moments; never negative. */
    private static long intervalsSince(Instant from, Instant to) {
        if (from == null || to == null) {
            return 0L;
        }
        long seconds = Duration.between(from, to).getSeconds();
        return seconds <= 0 ? 0L : seconds / Vitals.INTERVAL_SECONDS;
    }

    /** Allocation ids belonging to parasites — emitted in costume instead. */
    private static List<String> parasiteAllocationIds(SoloSave save) {
        List<String> out = new ArrayList<>();
        for (MinerState miner : miners(save)) {
            if (miner.allocationId != null && !miner.allocationId.isBlank()) {
                out.add(miner.allocationId);
            }
        }
        return out;
    }

    private static boolean ownedByTask(SoloSave save, String allocationId) {
        for (TaskState task : tasks(save)) {
            if (allocationId.equals(task.allocationId)) {
                return true;
            }
        }
        return false;
    }

    private static List<TaskState> tasks(SoloSave save) {
        return save.tasks == null ? List.of() : save.tasks;
    }

    private static List<MinerState> miners(SoloSave save) {
        return save.rig.foreignMiners == null ? List.of() : save.rig.foreignMiners;
    }

    private static Duration maxZero(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
