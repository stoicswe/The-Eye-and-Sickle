package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Duration;
import java.util.Objects;

/**
 * One row in the rig's process table — the manual audit, made a thing a player can actually do.
 *
 * <h2>⚠ THERE IS NO {@code rogue} FIELD, AND ADDING ONE DELETES THE FEATURE</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 makes manual investigation the game's second-strongest
 * tutorial vector: "the discrepancy is always present in the data". A parasite hides here by
 * <b>looking like the other rows</b>, and the only thing that gives it away is the data itself — a
 * name one character off a real daemon, a user nothing else on the machine runs as, a CPU figure that
 * does not match its accumulated CPU time, two rows claiming to be the same tool.
 *
 * <p>So this record carries what a real process table carries and nothing more. A boolean the client
 * could paint red would turn an investigation into a highlight, and every tell below into decoration.
 * The same rule that keeps {@link BreachSnapshot} from carrying the Logic code applies here, and it
 * is the reason {@link #killable} and {@link #restartable} are phrased as <em>capabilities</em>
 * rather than as kinds: they answer "what can I do to this row", which the player is allowed to know,
 * instead of "what is this row really", which they are not.
 *
 * <h2>The stats are diegetic instrumentation, and they are stable</h2>
 *
 * {@link #cpuPercent}, {@link #memoryBytes} and the four traffic counters are the machine's own
 * readouts. They are derived from real state where real state exists — a scan holding 35 cycles
 * genuinely shows as heavy CPU — and are otherwise a deterministic function of the process's identity
 * and a slow time bucket, so a row drifts the way a real one does and never jitters. A table whose
 * numbers danced would be unreadable at exactly the moment a player was trying to compare two rows,
 * which is the only moment it matters.
 *
 * @param processId what an intent names. Stable for the life of the process; never the pid, which is
 *     a display figure a player reads and a save could legitimately renumber
 * @param pid the number on screen. Small and stable for system processes, larger for anything started
 *     since boot — the ordering a real table has, and a tell in its own right
 * @param name the command, as it would appear in {@code ps}
 * @param user who it runs as. {@code root}, the player's handle, or an underscore-prefixed service
 *     account — and occasionally something that belongs to none of those families
 * @param parent what started it, for the tree a player can read down. {@code ""} for pid 1
 * @param cpuPercent share of the machine, 0–100 as a percentage figure rather than a fraction, so it
 *     reads the way Activity Monitor's column does
 * @param cpuTime accumulated processor time. ⚠ A process claiming heavy CPU with no accumulated time
 *     has not been working as long as it says it has
 * @param threads how many threads it holds
 * @param idleWakeups wakeups from idle — the column that catches a busy-loop
 * @param memoryBytes resident memory
 * @param ports mach ports held; decoration on most rows and a tell on a few
 * @param bytesRead disk read since it started
 * @param bytesWritten disk written since it started
 * @param sentBytes network out. ⚠ A local-only tool with traffic here is not local-only
 * @param rcvdBytes network in
 * @param sentPackets packets out
 * @param rcvdPackets packets in
 * @param cycles what it is costing the compute ledger, so this table and the grid can be reconciled
 *     against each other — which is the cross-check §3.1 is built on
 * @param killable whether {@code kill} is offered. False for anything the rig needs to keep running
 * @param restartable whether {@code restart} is offered instead — see the class note on system
 *     processes
 * @param detail one short line for the row's tooltip and its accessible text; never a verdict
 */
public record RigProcess(
        String processId,
        int pid,
        String name,
        String user,
        String parent,
        double cpuPercent,
        Duration cpuTime,
        int threads,
        int idleWakeups,
        long memoryBytes,
        int ports,
        long bytesRead,
        long bytesWritten,
        long sentBytes,
        long rcvdBytes,
        long sentPackets,
        long rcvdPackets,
        long cycles,
        boolean killable,
        boolean restartable,
        String detail) {

    public RigProcess {
        Objects.requireNonNull(processId, "processId");
        name = name == null ? "" : name;
        user = user == null ? "" : user;
        parent = parent == null ? "" : parent;
        detail = detail == null ? "" : detail;
        cpuTime = cpuTime == null ? Duration.ZERO : cpuTime;

        // Clamped rather than rejected. This record is built from a save the player can edit, and a
        // process table that refused to render is a strictly worse outcome than one showing a
        // nonsense figure — the figure is visible and arguable, the exception is neither.
        cpuPercent = Math.max(0.0d, Math.min(100.0d, cpuPercent));

        // ⚠ Killable and restartable are mutually exclusive, and the check is here rather than in the
        // builder because this is the type both the view and the rules agree through. A row offering
        // both would let a player kill a process the rig cannot run without by picking the other
        // menu item — the guard would be in one place and the bug in the other.
        if (killable && restartable) {
            throw new IllegalArgumentException(
                    "a process is killable or restartable, never both: " + name);
        }
    }

    /** Memory as the table prints it: MB with one decimal, or GB past a thousand. */
    public String memoryText() {
        double megabytes = memoryBytes / (1024.0d * 1024.0d);
        if (megabytes >= 1024.0d) {
            return String.format(java.util.Locale.ROOT, "%.2f GB", megabytes / 1024.0d);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", megabytes);
    }

    /** {@code 3:32.28} — minutes and hundredths, exactly the shape Activity Monitor's column has. */
    public String cpuTimeText() {
        long totalSeconds = cpuTime.getSeconds();
        long hundredths = cpuTime.toMillisPart() / 10;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d.%02d", minutes, seconds, hundredths);
    }

    /** Bytes as the disk and network columns print them. */
    public static String bytesText(long bytes) {
        if (bytes <= 0) {
            return "0 bytes";
        }
        double kilobytes = bytes / 1024.0d;
        if (kilobytes < 1024.0d) {
            return String.format(java.util.Locale.ROOT, "%.0f KB", Math.max(1.0d, kilobytes));
        }
        double megabytes = kilobytes / 1024.0d;
        if (megabytes < 1024.0d) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", megabytes);
        }
        return String.format(java.util.Locale.ROOT, "%.2f GB", megabytes / 1024.0d);
    }
}
