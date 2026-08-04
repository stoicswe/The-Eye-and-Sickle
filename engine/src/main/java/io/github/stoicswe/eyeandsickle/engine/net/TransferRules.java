package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
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
    public static Started begin(GameSave save, String address, FsEntry entry, String destination, Instant now) {
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
        TaskState task = new TaskState(KIND, "downloading " + entry.name(), "", 0L, now, now.plus(duration));
        // Address, path, size and DESTINATION ride on the task — the destination especially,
        // because the player chose it and the tick that completes the transfer minutes later has no
        // other way to know where they wanted it.
        task.outcome = address + " " + entry.path() + " " + bytes + " "
                + io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs.normalise(destination);
        save.tasks.add(task);
        return new Started(task, null, bytes, duration);
    }

    /**
     * The stand-in address a bought package is fetched from.
     *
     * <p>Not a machine and never resolvable: a vendor is a shopfront, and giving it an address would
     * put something on the map that cannot be swept, breached or held. It reads as a source in the
     * activity list and the arrival log, which is all it has to be.
     */
    public static final String VENDOR = "market";

    /**
     * Commissions the download of a bought upgrade.
     *
     * <h2>Why buying goes over the same pipe as stealing</h2>
     *
     * Until 2026-07-29 a purchase materialised the item in the same call that took the money. It now
     * takes the money, broadcasts the transaction, and <b>downloads a package</b> — the same task, the
     * same activity readout, the same Downloads folder and the same {@code install} step a stolen
     * upgrade goes through. One pipeline rather than two means the fee market, the link speed and the
     * install step are facts about upgrades rather than facts about where you got one.
     *
     * <p>⚠ <b>No session is required and none is charged.</b> {@link #begin} refuses without a shell
     * on the far machine because you cannot pull a file off a host you do not hold; a vendor hands it
     * to you because you paid. That is the whole difference, and it is why this is a second entry
     * point rather than a flag on the first.
     *
     * @param entryId the ledger row for the payment. Rides on the task so the package that lands
     *     minutes later knows which transaction releases it — see {@code Repac.locked}.
     */
    public static Started beginPurchase(GameSave save, String itemType, String fileName, String entryId, Instant now) {
        return beginPurchase(save, itemType, fileName, entryId, now, true);
    }

    /**
     * Starts a purchase download, stating whether the vendor is somebody else's machine.
     *
     * <p>⚠ {@code foreign} decides the NOISE, and the default above is {@code true} — the loud
     * direction. Solo and LAN are both foreign in the fiction, a federated server you do not own is
     * foreign, and only your own home server is not. Defaulting to silent would make every unconverted
     * caller quietly free, which is the wrong way for that mistake to go: a purchase that should have
     * been observable and was not is a stealth bug nobody can see.
     *
     * @param foreign whether the vendor is a machine the player does not control
     */
    public static Started beginPurchase(
            GameSave save, String itemType, String fileName, String entryId, Instant now, boolean foreign) {
        if (save == null || itemType == null || itemType.isBlank()) {
            return new Started(null, Refusal.NOT_TRANSFERABLE, 0, Duration.ZERO);
        }
        String path = "/" + VENDOR + "/" + fileName;
        if (running(save, path).isPresent()) {
            return new Started(null, Refusal.ALREADY_RUNNING, 0, Duration.ZERO);
        }
        // The same size the identical upgrade has when it is sitting inside somebody's app bundle,
        // from the same function — a bought copy and a stolen copy of one tool are one file.
        long bytes = io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs.upgradeBytes(itemType);
        Duration duration = Balance.transferTime(bytes);
        TaskState task = new TaskState(KIND, "downloading " + fileName, "", 0L, now, now.plus(duration));
        // ⚠ On the TASK, so the noise is present-tense and ends by itself when the download does.
        // NoiseRules already counts a running task's declared loudness, so this needs no new
        // mechanism and cannot leave a rig permanently loud if something forgets to clear it.
        task.noiseCycles = foreign ? Balance.MARKET_FOREIGN_PURCHASE_NOISE_CYCLES : 0L;
        task.outcome = VENDOR + " " + path + " " + bytes + " "
                + io.github.stoicswe.eyeandsickle.engine.rules.Repac.defaultDestination(save.handle)
                + " " + itemType + " " + entryId;
        save.tasks.add(task);
        return new Started(task, null, bytes, duration);
    }

    /**
     * Commissions the download of a bought <b>bundle</b>, as one archive.
     *
     * <h2>⚠ ONE transfer, because it was ONE purchase</h2>
     *
     * A bundle is one price, one debit and one ledger row, so it arrives as one file. Three
     * concurrent {@code .pkg} downloads would turn it back into three purchases that happened to
     * share a discount, and the player would watch three bars for something they bought once.
     *
     * <p>⚠ The member list rides as a SEVENTH field and the item type is deliberately blank: an
     * archive installs as nothing, it <em>contains</em> things. Leaving the item type set would make
     * {@code Repac.arrive} file the archive as an upgrade of whatever happened to be first.
     *
     * @param members the catalogue ids inside it
     * @param bytes the archive's size, which the caller totals from the members
     */
    public static Started beginArchive(
            GameSave save,
            String fileName,
            String entryId,
            long bytes,
            java.util.List<String> members,
            Instant now,
            boolean foreign) {
        if (save == null || members == null || members.isEmpty()) {
            return new Started(null, Refusal.NOT_TRANSFERABLE, 0, Duration.ZERO);
        }
        String path = "/" + VENDOR + "/" + fileName;
        if (running(save, path).isPresent()) {
            return new Started(null, Refusal.ALREADY_RUNNING, 0, Duration.ZERO);
        }
        long size = Math.max(1L, bytes);
        Duration duration = Balance.transferTime(size);
        TaskState task = new TaskState(KIND, "downloading " + fileName, "", 0L, now, now.plus(duration));
        task.noiseCycles = foreign ? Balance.MARKET_FOREIGN_PURCHASE_NOISE_CYCLES : 0L;
        // ⚠ Field 4 (the item type) is EMPTY and field 6 carries the members. The accessors read by
        // index, so appending rather than repurposing is what keeps a task written by an older build
        // parsing — every missing index answers empty rather than throwing.
        // ⚠ Assembled by INDEX, with the empty item-type field spelled out. Written as a `+` chain
        // it is a double space in the middle of a string literal — invisible in review, and deleting
        // it silently shifts the entry id into the item-type slot, which files the archive as an
        // upgrade of nothing and loses the lock that holds a bundle until its payment is mined.
        task.outcome = String.join(
                " ",
                VENDOR,
                path,
                String.valueOf(size),
                io.github.stoicswe.eyeandsickle.engine.rules.Repac.defaultDestination(save.handle),
                "",
                entryId == null ? "" : entryId,
                String.join(",", members));
        save.tasks.add(task);
        return new Started(task, null, size, duration);
    }

    /**
     * What is inside a bundle's archive, or empty for anything else.
     *
     * <p>⚠ Comma-separated because {@link #field} splits on spaces. A list joined with spaces would
     * read back as one member and silently lose the rest of a bundle the player paid for.
     */
    public static java.util.List<String> membersOf(TaskState task) {
        String joined = field(task, 6);
        return joined.isBlank() ? java.util.List.of() : java.util.List.of(joined.split(","));
    }

    /** Whether this transfer is carrying a bundle archive rather than a single package. */
    public static boolean isArchive(TaskState task) {
        return !membersOf(task).isEmpty();
    }

    /**
     * The catalogue id a bought download will install as, or empty for an ordinary transfer.
     *
     * <p>⚠ Appended as a FIFTH field, so a task written before this existed still parses — every
     * accessor reads by index and a missing index answers empty rather than throwing. A task list is
     * serialised into the save and outlives the code that wrote it.
     */
    public static String itemTypeOf(TaskState task) {
        return field(task, 4);
    }

    /** The ledger row that releases a bought package, or empty when nothing holds it. */
    public static String entryIdOf(TaskState task) {
        return field(task, 5);
    }

    /** Whether this transfer is a purchase rather than a pull off a machine. */
    public static boolean isPurchase(TaskState task) {
        return VENDOR.equals(addressOf(task));
    }

    /** A transfer already running for this path, if there is one. */
    public static Optional<TaskState> running(GameSave save, String path) {
        if (save == null || path == null) {
            return Optional.empty();
        }
        return save.tasks.stream()
                .filter(task -> KIND.equals(task.kind))
                .filter(task -> path.equals(pathOf(task)))
                .findFirst();
    }

    /** Every transfer currently in flight. */
    public static java.util.List<TaskState> inFlight(GameSave save) {
        return save == null
                ? java.util.List.of()
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
