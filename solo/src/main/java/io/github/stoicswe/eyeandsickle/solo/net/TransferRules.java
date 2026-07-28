package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pulling a file off another machine, at the speed the link actually allows.
 *
 * <h2>⚠ The remote end's UPLOAD is the bottleneck, not your download</h2>
 *
 * {@link Balance#downloadBytesPerSecond()} is {@code min(your down, their up)}, and a Gigabit line
 * against a 150 Mbit uplink gives <b>18.75 MB/s no matter how good your connection is</b>. That is
 * the whole reason the two constants are separate numbers rather than one: it is the most useful
 * true thing about file transfers that nearly everyone has experienced and almost nobody has had
 * named for them. A player who works out that upgrading their own line would change nothing has
 * learned it — which is why <b>TR-1</b> ({@code docs/design/15}) leaves the upgrade path open rather
 * than quietly offering one.
 *
 * <h2>A transfer is a task, not a modal</h2>
 *
 * It goes into {@code save.tasks} beside scans and sweeps, which buys three things for nothing: it
 * appears in the rig monitor's activity list with a real countdown, it persists, and it completes on
 * the first tick after a reload rather than being lost. A progress bar that lived only in the file
 * manager would vanish when the window closed, and a player would reasonably conclude the download
 * had been cancelled.
 *
 * <h2>It costs no compute, deliberately</h2>
 *
 * Moving bytes is I/O, not arithmetic. Charging cycles would make the rig's compute readout answer a
 * question it is not measuring. What a transfer <em>does</em> cost is the session it runs over —
 * already held, already outward, already loud.
 */
public final class TransferRules {

    private TransferRules() {}

    /** The task kind, so the activity readout and the tick can recognise one. */
    public static final String KIND = "transfer";

    /** Why a transfer could not start. */
    public enum Refusal {
        /** There is no session on that machine — nothing to pull over. */
        NOT_CONNECTED,

        /** Not a file the rules model, so there would be nothing to bring back. */
        NOT_TRANSFERABLE,

        /** Not readable from here. */
        NOT_READABLE,

        /** Already on its way. */
        ALREADY_RUNNING
    }

    /** The task, or the reason there is none. */
    public record Started(TaskState task, Refusal refusal, long bytes, Duration duration) {

        public boolean succeeded() {
            return task != null;
        }
    }

    /**
     * Whether a file is worth bringing back.
     *
     * <p>⚠ Deliberately narrow. Somebody else's {@code /var/log/syslog} is scenery — copying it
     * would produce a file with nothing behind it, and a download that yields nothing teaches a
     * player that downloads yield nothing. These are the kinds the rules actually model; everything
     * else is refused in words.
     */
    public static boolean transferable(FsEntry entry) {
        if (entry == null || entry.directory()) {
            return false;
        }
        return entry.kind() == FsKind.DOCUMENT
                || entry.kind() == FsKind.LOOT
                || entry.name().endsWith(".pkg")
                || entry.name().endsWith(".schematic");
    }

    /**
     * Commissions a transfer.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — a task whose deadline is measured
     *     against a different clock from the one that completes it reports 100% the moment it starts
     */
    public static Started begin(
            SoloSave save, String address, FsEntry entry, String destination, Instant now) {
        if (save == null || entry == null) {
            return new Started(null, Refusal.NOT_TRANSFERABLE, 0, Duration.ZERO);
        }
        if (SessionRules.find(save, address).isEmpty()) {
            return new Started(null, Refusal.NOT_CONNECTED, 0, Duration.ZERO);
        }
        if (!entry.readable()) {
            return new Started(null, Refusal.NOT_READABLE, 0, Duration.ZERO);
        }
        if (!transferable(entry)) {
            return new Started(null, Refusal.NOT_TRANSFERABLE, 0, Duration.ZERO);
        }
        if (running(save, entry.path()).isPresent()) {
            return new Started(null, Refusal.ALREADY_RUNNING, 0, Duration.ZERO);
        }

        long bytes = Math.max(1L, entry.sizeBytes());
        Duration duration = Balance.transferTime(bytes);
        TaskState task = new TaskState(
                KIND, "downloading " + entry.name(), "", 0L, now, now.plus(duration));
        // Address, path, size and DESTINATION ride on the task — the destination especially,
        // because the player chose it and the tick that completes the transfer minutes later has no
        // other way to know where they wanted it.
        task.outcome = address + " " + entry.path() + " " + bytes + " "
                + io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.normalise(destination);
        save.tasks.add(task);
        return new Started(task, null, bytes, duration);
    }

    /** A transfer already running for this path, if there is one. */
    public static Optional<TaskState> running(SoloSave save, String path) {
        if (save == null || path == null) {
            return Optional.empty();
        }
        return save.tasks.stream()
                .filter(task -> KIND.equals(task.kind))
                .filter(task -> path.equals(pathOf(task)))
                .findFirst();
    }

    /** Every transfer currently in flight. */
    public static java.util.List<TaskState> inFlight(SoloSave save) {
        return save == null ? java.util.List.of()
                : save.tasks.stream().filter(task -> KIND.equals(task.kind)).toList();
    }

    public static String addressOf(TaskState task) {
        return field(task, 0);
    }

    public static String pathOf(TaskState task) {
        return field(task, 1);
    }

    /** Where the player chose to put it. */
    public static String destinationOf(TaskState task) {
        return field(task, 3);
    }

    public static long bytesOf(TaskState task) {
        try {
            return Long.parseLong(field(task, 2));
        } catch (NumberFormatException malformed) {
            return 0L;
        }
    }

    private static String field(TaskState task, int index) {
        String[] parts = String.valueOf(task == null ? "" : task.outcome).split(" ");
        return parts.length > index ? parts[index] : "";
    }
}
