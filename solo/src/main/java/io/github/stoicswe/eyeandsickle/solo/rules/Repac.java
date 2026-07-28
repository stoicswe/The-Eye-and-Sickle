package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.solo.Catalogue;
import io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.StoredFileState;
import java.time.Instant;
import java.util.Optional;

/**
 * What happens to a file after it arrives: repacking, installing, and reselling.
 *
 * <h2>The three-step life of a stolen upgrade</h2>
 *
 * <pre>
 *   somebody's machine    ~/Downloads          ~/Downloads      Applications/…/Upgrades
 *   sweep-wide.pkg   ──▶  sweep-wide.pkg  ──▶  sweep-wide.upg ──▶  owned, installed
 *                 download            Repac              double-click
 *                                                             │
 *                                                             └──▶ or SOLD, and never installed
 * </pre>
 *
 * <b>Repac</b> is the rig's own packaging tool. It is not a window and the player never runs it —
 * it fires the moment an upgrade payload lands and turns a vendor package into something this rig
 * can install. It exists as a named step rather than as silent magic for one reason: a player who
 * watches {@code sweep-wide.pkg} become {@code sweep-wide.upg} and reads the log line saying which
 * tool did it has learned that a downloaded package is not the same object as an installed program.
 * That is a real and frequently-missing distinction, and it costs one log line to teach.
 *
 * <h2>⚠ Installing is optional, and that is the whole economy</h2>
 *
 * A {@code .upg} is an <b>asset</b>. Installing consumes it; selling it does not require ever having
 * wanted it. That is what makes stealing an upgrade you already own worth doing, and it is the
 * incentive the secondary market exists to create.
 *
 * <h2>⚠ [PROPOSAL] Only ETHECOIN-gated upgrades may be resold, and this is Invariant I2</h2>
 *
 * If a schematic-gated tool could be stolen and sold for ethecoin, then <b>anyone with enough
 * ethecoin could buy a ceiling</b> — which is exactly what <b>I2</b> forbids, and <b>I8</b> forbids
 * for zero-days. Resale is therefore restricted to items whose gate is <em>already</em>
 * {@link UnlockGate#ETHECOIN}: money reselling a money-gated item opens no route that was not open,
 * and the player economy still gets the large class of items that band covers.
 *
 * <p>Everything else can still be <b>stolen and used</b> — raiding is an established acquisition
 * route ({@code docs/design/01-core-resources.md} §6) and nothing here changes it. What is refused is
 * turning a gated item into currency. See {@code docs/design/15} for the alternative that was
 * rejected and why.
 */
public final class Repac {

    private Repac() {}

    /** What Repac produces. Installable, sellable, and gone once either happens. */
    public static final String PACKAGE_SUFFIX = ".upg";

    /** What arrives off somebody else's machine — a vendor package, not yet ours. */
    public static final String PAYLOAD_SUFFIX = ".pkg";

    /**
     * What a resold upgrade fetches, as a fraction of its catalogue price, in percent.
     *
     * <p>⚠ Below retail on purpose and by a wide margin. At parity, stealing and reselling would
     * dominate every other income source in the game — it has no compute cost, no thermal recovery
     * and no cap — and {@code docs/design/00} §4's meta-rule is that compute is the master scarcity.
     * Sixty percent leaves theft clearly worth doing and clearly not a replacement for mining.
     */
    public static final long RESALE_PERCENT = 60L;

    // ── arrival ───────────────────────────────────────────────────────────────────────────────

    /**
     * Files a completed transfer leaves behind.
     *
     * <p>⚠ Everything except an upgrade arrives <b>as itself</b> — a {@code .txt} stays a
     * {@code .txt}. Converting a recovered fragment into some game-specific artefact would make the
     * filesystem a metaphor again, and the whole point of it being a filesystem is that it is not.
     */
    public static StoredFileState arrive(
            SoloSave save, String directory, String name, String sourceAddress,
            long bytes, String itemType, Instant now) {
        StoredFileState file = new StoredFileState();
        file.directory = VirtualFs.normalise(directory);
        file.name = name;
        file.sourceAddress = sourceAddress;
        file.bytes = bytes;
        file.itemType = itemType == null ? "" : itemType;
        file.kind = name.endsWith(PAYLOAD_SUFFIX) ? "payload" : "document";
        file.at = now;
        save.files.add(file);
        return file;
    }

    /**
     * Runs Repac over a just-arrived payload, in place.
     *
     * <p>Instant rather than a second timed task. A download already has a progress bar; a second
     * one for a local repack would be two bars for one act, and the interesting wait — the one
     * bounded by somebody else's uplink — has already happened.
     *
     * @return the resulting package, or empty when the file was not a payload
     */
    public static Optional<StoredFileState> repack(SoloSave save, StoredFileState file, Instant now) {
        if (file == null || !"payload".equals(file.kind)) {
            return Optional.empty();
        }
        file.name = file.name.substring(0, file.name.length() - PAYLOAD_SUFFIX.length())
                + PACKAGE_SUFFIX;
        file.kind = "package";
        file.at = now;
        return Optional.of(file);
    }

    // ── installing ────────────────────────────────────────────────────────────────────────────

    /** Why an install or a sale was refused. */
    public enum Refusal {
        /** No such file on this rig. */
        NO_SUCH_FILE,

        /** Not something that installs — a document, or a payload Repac has not touched. */
        NOT_INSTALLABLE,

        /** Already owned; installing a second copy would do nothing. */
        ALREADY_OWNED,

        /** ⚠ Gated by something other than money, so it cannot be turned into money. See I2. */
        NOT_SELLABLE
    }

    /** The outcome of an install or a sale. */
    public record Result(boolean ok, Refusal refusal, String message, long minorUnits) {

        static Result refused(Refusal refusal, String message) {
            return new Result(false, refusal, message, 0L);
        }
    }

    /**
     * Installs a package: the item becomes owned, and the file is gone.
     *
     * <p>⚠ It lands in <b>{@link StorageTier#VAULT}</b>, which is the safe tier. A stolen upgrade
     * arriving in the hot zone would be immediately re-stealable, and a chain of players stealing one
     * upgrade back and forth is a loop with no decision in it. Moving it out is the player's choice
     * and is what {@code docs/design/01} §6's trade is for.
     */
    public static Result install(SoloSave save, String path, Instant now) {
        Optional<StoredFileState> found = find(save, path);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_FILE, "no such file: " + path);
        }
        StoredFileState file = found.get();
        if (!"package".equals(file.kind) || file.itemType.isBlank()) {
            return Result.refused(Refusal.NOT_INSTALLABLE,
                    file.name + " is not an installable upgrade.");
        }
        boolean owned = save.items.stream().anyMatch(i -> file.itemType.equals(i.itemType));
        if (owned) {
            // Refused rather than silently consumed. A player who installs a duplicate and watches
            // the file vanish for nothing has been robbed by their own interface — and the duplicate
            // is worth real ethecoin on the secondary market, which the refusal points at.
            return Result.refused(Refusal.ALREADY_OWNED,
                    "You already have " + displayName(file.itemType)
                            + ". This copy is worth more sold than installed.");
        }

        ItemState item = new ItemState();
        item.itemType = file.itemType;
        item.displayName = displayName(file.itemType);
        item.tier = StorageTier.VAULT.name();
        item.acquiredAt = now;
        item.origin = file.sourceAddress.isBlank() ? "recovered" : "taken from " + file.sourceAddress;
        save.items.add(item);
        save.files.remove(file);

        return new Result(true, null,
                "installed " + item.displayName + " — the package is consumed", 0L);
    }

    // ── selling ───────────────────────────────────────────────────────────────────────────────

    /**
     * Whether an upgrade may be turned into money.
     *
     * <p>⚠ <b>Invariant I2 lives here.</b> Only an item already gated on ethecoin may be resold; a
     * schematic-, reputation-, proof-of-skill- or zero-day-gated item may be stolen and used but not
     * sold, because selling it would let anybody with enough ethecoin buy a ceiling. An unknown item
     * type fails closed, because guessing "sellable" would turn a content gap into an exploit.
     */
    public static boolean sellable(String itemType) {
        return Catalogue.byId(itemType)
                .map(offering -> offering.gate() == UnlockGate.ETHECOIN)
                .orElse(false);
    }

    /** What a copy fetches. Below retail — see {@link #RESALE_PERCENT}. */
    public static long resaleValue(String itemType) {
        return Catalogue.byId(itemType)
                .map(offering -> offering.priceMinorUnits() * RESALE_PERCENT / 100L)
                .orElse(0L);
    }

    /**
     * Sells a package on the secondary market.
     *
     * <p>The file goes and the balance moves. The caller credits the ledger — this returns the
     * amount rather than touching money itself, so there is exactly one place in the engine that
     * writes a ledger entry.
     */
    public static Result sell(SoloSave save, String path) {
        Optional<StoredFileState> found = find(save, path);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_FILE, "no such file: " + path);
        }
        StoredFileState file = found.get();
        if (!"package".equals(file.kind) || file.itemType.isBlank()) {
            return Result.refused(Refusal.NOT_INSTALLABLE, file.name + " is not an upgrade.");
        }
        if (!sellable(file.itemType)) {
            // Named, not generic. A player told only "cannot sell" will try again; one told the
            // reason has learned something about how the gates work.
            return Result.refused(Refusal.NOT_SELLABLE,
                    displayName(file.itemType) + " is not gated on ethecoin, so it cannot be turned "
                            + "into ethecoin. Nobody sells a way past a schematic. You can still "
                            + "use it.");
        }
        long value = resaleValue(file.itemType);
        save.files.remove(file);
        return new Result(true, null,
                "sold " + displayName(file.itemType) + " on the secondary market", value);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    public static Optional<StoredFileState> find(SoloSave save, String path) {
        String p = VirtualFs.normalise(path);
        return save == null ? Optional.empty()
                : save.files.stream().filter(f -> f.path().equals(p)).findFirst();
    }

    /** Every stored file directly inside a folder. */
    public static java.util.List<StoredFileState> in(SoloSave save, String directory) {
        String d = VirtualFs.normalise(directory);
        return save == null ? java.util.List.of()
                : save.files.stream().filter(f -> f.directory.equals(d)).toList();
    }

    private static String displayName(String itemType) {
        return Catalogue.byId(itemType).map(Catalogue.Offering::name).orElse(itemType);
    }

    /** Where a download lands unless the player picks somewhere else. It is called Downloads. */
    public static String defaultDestination(String handle) {
        return VirtualFs.home(handle) + "/Downloads";
    }
}
