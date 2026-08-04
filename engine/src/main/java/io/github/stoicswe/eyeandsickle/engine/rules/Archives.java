package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code .tar.xz} — several packages in one file, and the work of getting them out.
 *
 * <h2>Why a bundle arrives as an archive rather than as three downloads</h2>
 *
 * A bundle is <b>one</b> purchase: one price, one debit, one ledger row. Handing it over as three
 * separate {@code .pkg} downloads would make it three purchases that happened to be discounted
 * together, and the player would watch three progress bars for a thing they bought once. One
 * archive is what a shop actually ships, and it costs the rules nothing extra — the transfer
 * machinery does not care what is inside a file.
 *
 * <h2>⚠ Extraction is REAL WORK and takes time, unlike Repac</h2>
 *
 * {@code Repac.repack} is instant, deliberately: renaming a payload is bookkeeping and a second
 * progress bar for one act is noise. Extraction is not that. {@code xz} is genuinely slow to
 * decompress — that is the trade the format exists to make, small files for expensive
 * decompression — so a wait here is the true thing rather than an invented one, and it is the
 * fact worth teaching about the format.
 *
 * <p>⚠ Scaled by size, not fixed. A firmware flash is 90 seconds whatever the image, because it is
 * bounded by the device writing itself; decompression is bounded by how many bytes there are, so
 * this one goes as {@link Balance#EXTRACT_BYTES_PER_SECOND}. The two constants are different
 * numbers because they measure different things.
 *
 * <h2>⚠ The archive is consumed at COMPLETION, never at the start</h2>
 *
 * Same rule the firmware flash follows. An extraction interrupted by a quit must cost nothing
 * rather than everything — deleting the archive up front and crashing in between would destroy a
 * bundle the player paid for, and no amount of care elsewhere gets it back.
 *
 * <h2>⚠ What comes out is LOCKED, exactly as a single purchase is</h2>
 *
 * Every member carries the bundle's own {@code lockedByEntryId}, so it lands as a vendor
 * {@code .pkg} and becomes an installable {@code .upg} when that one payment is mined. Extraction
 * is local work on bytes the player already holds and settles nothing — if unpacking released the
 * contents, a bundle would be the way to skip the on-chain settlement that every other purchase
 * waits for.
 */
public final class Archives {

    private Archives() {}

    /** The suffix. Real, and real for the reason the game uses it: tar for the bundling, xz for the squeeze. */
    public static final String SUFFIX = ".tar.xz";

    /** The task kind, so the activity readout and the tick can recognise an extraction. */
    public static final String EXTRACT_KIND = "extract";

    /** Why an extraction could not start. */
    public enum Refusal {
        /** No such file on this rig. */
        NOT_FOUND,

        /** Not an archive — there is nothing in it to get out. */
        NOT_AN_ARCHIVE,

        /** Already being unpacked. */
        ALREADY_RUNNING
    }

    /** The task, or the reason there is none. */
    public record Started(TaskState task, Refusal refusal, Duration duration) {

        public boolean succeeded() {
            return task != null;
        }

        public static Started refused(Refusal refusal) {
            return new Started(null, refusal, Duration.ZERO);
        }
    }

    /** @return whether this file is one this rig can unpack. */
    public static boolean isArchive(StoredFileState file) {
        return file != null && file.name.endsWith(SUFFIX);
    }

    /** @return whether this name is an archive's. */
    public static boolean isArchiveName(String name) {
        return name != null && name.endsWith(SUFFIX);
    }

    /**
     * What a bundle's archive is called.
     *
     * <p>⚠ Named for the ORDER, not for its contents. A name built from the member ids would be
     * eighty characters wide in {@code ls} and would change if the bundle's makeup ever did; the
     * short id is stable and the contents are answered by {@code stat}, which is where a player
     * asks what is in a file.
     */
    public static String fileName(String orderId) {
        String suffix = orderId == null || orderId.length() < 8 ? "0000" : orderId.substring(0, 8);
        return "bundle-" + suffix + SUFFIX;
    }

    /**
     * How long unpacking one takes.
     *
     * <p>Floored at a second so a tiny archive still reads as work happening rather than as a
     * button that did nothing.
     */
    public static Duration extractTime(long bytes) {
        long seconds = Math.max(1L, bytes / Balance.EXTRACT_BYTES_PER_SECOND);
        return Duration.ofSeconds(seconds);
    }

    /** An extraction already running for this path, if there is one. */
    public static Optional<TaskState> extracting(GameSave save, String path) {
        if (save == null || path == null) {
            return Optional.empty();
        }
        return save.tasks.stream()
                .filter(task -> EXTRACT_KIND.equals(task.kind))
                .filter(task -> path.equals(task.outcome))
                .findFirst();
    }

    /** Every extraction in flight. */
    public static List<TaskState> inFlight(GameSave save) {
        return save == null
                ? List.of()
                : save.tasks.stream().filter(task -> EXTRACT_KIND.equals(task.kind)).toList();
    }

    /**
     * Begins unpacking.
     *
     * <p>⚠ Costs no compute, for the reason a transfer does not: this models a wait, and charging
     * cycles for it would make the rig's compute readout answer a question it is not measuring.
     * ⚠ And it makes no noise — the archive is already on the player's own disk, and nothing about
     * unpacking it touches anybody else's machine.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — a deadline measured against a
     *     different clock from the one that settles it reports 100% the moment it starts
     */
    public static Started begin(GameSave save, String path, Instant now) {
        Optional<StoredFileState> found = Repac.find(save, path);
        if (found.isEmpty()) {
            return Started.refused(Refusal.NOT_FOUND);
        }
        if (!isArchive(found.get())) {
            return Started.refused(Refusal.NOT_AN_ARCHIVE);
        }
        String normalised = VirtualFs.normalise(path);
        if (extracting(save, normalised).isPresent()) {
            return Started.refused(Refusal.ALREADY_RUNNING);
        }
        Duration duration = extractTime(found.get().bytes);
        TaskState task = new TaskState(
                EXTRACT_KIND, "extracting " + found.get().name, "", 0L, now, now.plus(duration));
        // ⚠ The PATH alone, so `extracting` can match on it. Everything else about the archive is on
        // the file, and copying any of it here would be a second copy that can disagree — the member
        // list especially, which decides what the player ends up owning.
        task.outcome = normalised;
        save.tasks.add(task);
        return new Started(task, null, duration);
    }

    /**
     * Finishes an extraction: the archive goes, its contents appear.
     *
     * <p>⚠ Returns empty when the archive is gone. An extraction whose file was deleted underneath
     * it must produce nothing rather than conjuring the contents from the task — the same guard the
     * firmware flash carries, and for the same reason: the task remembers what it was asked to do,
     * not what is still true.
     *
     * @return the files that came out, oldest-first, or empty if there was nothing to unpack
     */
    public static List<StoredFileState> complete(GameSave save, TaskState task, Instant now) {
        if (save == null || task == null) {
            return List.of();
        }
        Optional<StoredFileState> found = Repac.find(save, task.outcome);
        if (found.isEmpty() || !isArchive(found.get())) {
            return List.of();
        }
        StoredFileState archive = found.get();
        List<StoredFileState> out = new ArrayList<>();
        for (String itemType : archive.archiveItemTypes) {
            Optional<Catalogue.Offering> offering = Catalogue.byId(itemType);
            if (offering.isEmpty()) {
                continue;
            }
            StoredFileState member = Repac.arrive(
                    save,
                    archive.directory,
                    itemType + Repac.PAYLOAD_SUFFIX,
                    archive.sourceAddress,
                    VirtualFs.upgradeBytes(itemType),
                    itemType,
                    // ⚠ The SAME call a singly-bought package's version comes from. A bundle member
                    // and the identical item bought on its own must be the same build — a hand-built
                    // version here would drift from `Versions.on` the day either is re-tuned, and the
                    // only visible symptom would be two resale prices for one file.
                    Versions.on(itemType, io.github.stoicswe.eyeandsickle.engine.net.TransferRules.VENDOR,
                            Balance.MARKET_UPGRADE_VERSION_MAJOR),
                    now);
            // ⚠ THE BUNDLE'S OWN PAYMENT holds every member. Unpacking is local work on bytes the
            // player already has and settles nothing — releasing the contents here would make a
            // bundle the one purchase that skips confirmation.
            member.lockedByEntryId = archive.lockedByEntryId;
            out.add(member);
        }
        // ⚠ LAST. Removing the archive before its contents are built loses the member list on any
        // failure in between, and the list lives nowhere else.
        save.files.remove(archive);
        return out;
    }
}
