package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.net.TransferRules;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import java.math.BigInteger;
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

    /** What Repac produces for ordinary software. Installable, sellable, gone once either happens. */
    public static final String PACKAGE_SUFFIX = ".upg";

    /**
     * What Repac produces for <b>firmware</b>.
     *
     * <h2>⚠ It replaces {@code .upg}, and it does NOT replace {@code .pkg}</h2>
     *
     * The pipeline is {@code .pkg → .frm} for firmware and {@code .pkg → .upg} for software. The
     * first arrow is unchanged on purpose: <b>the {@code .pkg} rename IS the confirmation lock</b>
     * (`docs/design/04-mining.md` §1.3e) — a bought package stays a vendor package until its payment
     * is mined, and it is derived from the ledger row on every read so no flag can disagree with the
     * chain. Naming firmware {@code .frm} at both ends would leave firmware with no rename to make,
     * and a firmware image bought from the market would become installable before its money moved.
     *
     * <p>What {@code .frm} buys is that the class is visible in {@code ls}, the file manager and the
     * shell without any of them knowing what firmware is — the same argument that made
     * {@code .pkg}/{@code .upg} worth distinguishing in the first place.
     */
    public static final String FIRMWARE_SUFFIX = ".frm";

    /** The suffix Repac gives this item: {@code .frm} for firmware, {@code .upg} for everything else. */
    public static String installableSuffix(String itemType) {
        return Catalogue.byId(itemType).map(Catalogue.Offering::firmware).orElse(false)
                ? FIRMWARE_SUFFIX
                : PACKAGE_SUFFIX;
    }

    /** What arrives off somebody else's machine — a vendor package, not yet ours. */
    public static final String PAYLOAD_SUFFIX = ".pkg";

    /**
     * What a bought package is called on disk.
     *
     * <h2>⚠ A short id in the NAME, because two copies must be two files</h2>
     *
     * A player may now buy a second Tarpit while the first is still in Downloads, and a filesystem
     * where both are {@code tarpit.pkg} is broken in a way that shows up everywhere at once:
     * {@link #find} resolves a path to <em>the first</em> match, so Get Info describes one of them,
     * {@link #install} consumes one of them and {@code rm} deletes one of them, with nothing on
     * screen saying which. The id makes the path the identifier it is supposed to be.
     *
     * <p>⚠ Six characters, not the whole UUID. It is a disambiguator a player reads in {@code ls} and
     * types into {@code install}; a 36-character stem would push every other column off the line. The
     * item's own {@code itemId} stays the full identity — this only has to be unique among the
     * handful of packages one rig holds at once.
     *
     * @param itemType the catalogue id
     * @param orderId the purchase this copy came from
     */
    public static String boughtPackageName(String itemType, String orderId) {
        String tag = orderId == null || orderId.length() < 6 ? "000000" : orderId.substring(0, 6);
        return itemType + "-" + tag + PAYLOAD_SUFFIX;
    }

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
            GameSave save,
            String directory,
            String name,
            String sourceAddress,
            long bytes,
            String itemType,
            io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion version,
            Instant now) {
        StoredFileState file = new StoredFileState();
        file.directory = VirtualFs.normalise(directory);
        file.name = name;
        file.sourceAddress = sourceAddress;
        file.bytes = bytes;
        file.itemType = itemType == null ? "" : itemType;
        file.kind = name.endsWith(PAYLOAD_SUFFIX) ? "payload" : "document";
        file.at = now;
        file.version = version == null ? "" : version.toString();
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
    public static Optional<StoredFileState> repack(GameSave save, StoredFileState file, Instant now) {
        if (file == null || !"payload".equals(file.kind)) {
            return Optional.empty();
        }
        file.name =
                file.name.substring(0, file.name.length() - PAYLOAD_SUFFIX.length()) + installableSuffix(file.itemType);
        file.kind = "package";
        file.at = now;
        return Optional.of(file);
    }

    // ── the manifest ──────────────────────────────────────────────────────────────────────────

    /**
     * What a package declares about itself, and what it actually is.
     *
     * <p>Everything here is <b>derived</b> — the catalogue supplies the tool's name, summary, gate
     * and cycle cost, the file supplies its size and where it came from, and the ledger supplies
     * whether it is still waiting on a payment. Nothing about a package is stored twice, so a
     * manifest cannot drift from the thing it describes.
     *
     * @return empty when the path is not a package this rig holds
     */
    public static Optional<PackageManifest> manifest(GameSave save, String path) {
        Optional<StoredFileState> found = find(save, path);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StoredFileState file = found.get();
        if (file.itemType == null || file.itemType.isBlank()) {
            return Optional.empty();
        }
        Optional<Catalogue.Offering> offering = Catalogue.byId(file.itemType);
        boolean held = locked(save, file);
        boolean owned = save.items.stream().anyMatch(i -> file.itemType.equals(i.itemType));
        boolean market = TransferRules.VENDOR.equals(file.sourceAddress);

        return Optional.of(new PackageManifest(
                file.path(),
                file.name,
                file.itemType,
                displayName(file.itemType),
                offering.map(Catalogue.Offering::description)
                        .orElse(
                                // A package for something the catalogue no longer lists. Said plainly rather
                                // than rendered blank — a manifest with an empty contents field reads as a
                                // panel that failed to load.
                                "This rig has no catalogue entry for " + file.itemType
                                        + ". It will install, and nothing here can describe what it does."),
                publisherOf(file, market),
                market
                        ? TransferRules.VENDOR
                        : file.sourceAddress == null || file.sourceAddress.isBlank() ? "recovered" : file.sourceAddress,
                offering.map(Catalogue.Offering::gate).orElse(UnlockGate.ETHECOIN),
                file.bytes,
                offering.map(Catalogue.Offering::equippedCycles).orElse(0L),
                expectedSha(file.itemType),
                actualSha(file),
                held,
                held ? pendingNote(save, file) : "",
                owned,
                // ⚠ Exactly the conditions install() checks, in the same order, rather than a second
                // opinion about them. A panel whose button was enabled when the rule would refuse is
                // the interface claiming an authority it does not have (client pillar C4).
                !held && "package".equals(file.kind) && !owned));
    }

    /** Who signed it. A vendor for a bought package; the machine it was taken off for a stolen one. */
    private static String publisherOf(StoredFileState file, boolean market) {
        if (market) {
            return "EAS VENDOR NETWORK";
        }
        if (file.sourceAddress == null || file.sourceAddress.isBlank()) {
            return "unsigned — recovered fragment";
        }
        return "unsigned — taken from " + file.sourceAddress;
    }

    /** Which block the payment is waiting for, in words. */
    private static String pendingNote(GameSave save, StoredFileState file) {
        return heldBy(save, file)
                .map(entry -> "payment broadcast, not yet mined — it unlocks when a miner packs it "
                        + "into a block. A higher fee buys an earlier block.")
                .orElse("waiting on a payment.");
    }

    /**
     * The digest a package's manifest declares for its payload.
     *
     * <p>A function of <em>what it claims to be</em>. Two copies of the same upgrade, one bought and
     * one stolen off a stranger's machine, declare the same digest — which is the property that makes
     * comparing them worth anything.
     */
    public static String expectedSha(String itemType) {
        return sha256("eas.upgrade.payload:" + itemType);
    }

    /**
     * The digest of the payload actually on this disk.
     *
     * <p>⚠ Identical to {@link #expectedSha} unless {@code StoredFileState.payloadSalt} is set, which
     * nothing in single player does. That is the tamper seam, and it is why this is a separate
     * function over the <em>file</em> rather than the same call twice: the day a payload can be
     * substituted, this is the one that changes.
     */
    public static String actualSha(StoredFileState file) {
        String salt = file.payloadSalt == null ? "" : file.payloadSalt;
        return salt.isEmpty()
                ? expectedSha(file.itemType)
                : sha256("eas.upgrade.payload:" + file.itemType + "!" + salt);
    }

    private static String sha256(String of) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(of.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            // Required of every Java platform. If it is genuinely absent, the save layer and the
            // provenance verifier are already broken and a package panel is not the problem.
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    // ── installing ────────────────────────────────────────────────────────────────────────────

    /** Why an install or a sale was refused. */
    public enum Refusal {
        /** No such file on this rig. */
        NO_SUCH_FILE,

        /** Not something that installs — a document, or a payload Repac has not touched. */
        NOT_INSTALLABLE,

        /** Already owned; installing a second copy would do nothing. */

        /** ⚠ Gated by something other than money, so it cannot be turned into money. See I2. */
        NOT_SELLABLE,

        /**
         * Bought, downloaded, and still waiting for the payment to confirm on-chain.
         *
         * <p>Not a failure and not a gate in {@code docs/design/02}'s sense — the player has paid and
         * the bytes are on their disk. It is the vendor's escrow: a package released before its
         * transaction was mined would be a purchase with no settlement, which is the one thing a
         * chain is for.
         */
        UNCONFIRMED,

        /**
         * Firmware whose schematic the player does not hold.
         *
         * <p>⚠ The image is the payload and the schematic is the authorisation; neither alone does
         * anything. This is the half that keeps <b>I2</b> intact — the image is purchasable, so if
         * flashing it needed nothing else, money would have bought a permanent capability.
         */
        NO_SCHEMATIC,

        /**
         * Firmware for a tool that is currently running.
         *
         * <p>⚠ A refusal, never a warning, and never an offer to stop the tool automatically. Firmware
         * sits underneath the program using it, real flashing tools refuse for exactly this reason,
         * and a half-written firmware is how a device is bricked. Stopping the player's mining on
         * their behalf would also silently cost them income they did not agree to lose.
         */
        TOOL_RUNNING
    }

    /**
     * The ledger row a package is waiting on, or empty when nothing holds it.
     *
     * <p>⚠ <b>Fails OPEN.</b> A file naming an entry that is not in the ledger is released rather
     * than held: the only ways to reach that state are a hand-edited save and a bug, and of the two
     * possible errors — releasing a package whose payment cannot be found, and holding one forever
     * with no way for the player to discover why — the second is unrecoverable and the first costs
     * one item in a single-player game.
     */
    public static Optional<LedgerEntryState> heldBy(GameSave save, StoredFileState file) {
        if (save == null || file == null || file.lockedByEntryId == null || file.lockedByEntryId.isBlank()) {
            return Optional.empty();
        }
        return save.ledger.stream()
                .filter(entry -> file.lockedByEntryId.equals(entry.entryId))
                .findFirst();
    }

    /**
     * Whether this package is still waiting for its purchase to confirm.
     *
     * <p>Derived on every call rather than cached — see {@code StoredFileState.lockedByEntryId}.
     * Confirmation happens on a tick, and a flag would go stale the moment it happened with the file
     * manager closed.
     */
    public static boolean locked(GameSave save, StoredFileState file) {
        return heldBy(save, file).filter(entry -> entry.blockNumber < 0).isPresent();
    }

    /** The outcome of an install or a sale. */
    public record Result(boolean ok, Refusal refusal, String message, BigInteger wei) {

        static Result refused(Refusal refusal, String message) {
            return new Result(false, refusal, message, BigInteger.ZERO);
        }

        /** The same, for a caller outside this package that has its own refusal to report. */
        public static Result refusedPublic(Refusal refusal, String message) {
            return refused(refusal, message);
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
    public static Result install(GameSave save, String path, Instant now) {
        Optional<StoredFileState> found = find(save, path);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_FILE, "no such file: " + path);
        }
        StoredFileState file = found.get();
        // ⚠ CHECKED BEFORE the kind, and the order is the whole message. A bought package is a
        // `.pkg` until its payment is mined, so the kind check below would refuse it as "not an
        // installable upgrade" — true, useless, and indistinguishable from a corrupt download. The
        // player is owed the actual reason, which is that they are waiting for a block.
        //
        // This is also the one place a fee tier finally buys something mechanical: until 2026-07-29
        // a purchase handed over the goods in the same call that took the money, so the fee bought
        // only how soon a row stopped saying "—" in the ledger. See docs/design/04-mining.md §1.3e.
        if (locked(save, file)) {
            return Result.refused(
                    Refusal.UNCONFIRMED,
                    file.name + " is paid for and downloaded, but the payment has not been mined "
                            + "yet. It becomes installable when the block carrying it is confirmed "
                            + "— a higher fee buys a place in an earlier block, not a faster chain.");
        }
        if (!"package".equals(file.kind) || file.itemType.isBlank()) {
            return Result.refused(Refusal.NOT_INSTALLABLE, file.name + " is not an installable upgrade.");
        }
        // ⚠ A DUPLICATE INSTALLS (2026-08-04). This used to refuse, on the reasoning that a player
        // who installs a second copy and watches the file vanish for nothing has been robbed by
        // their own interface — which was right while a copy was worth more sold than installed and
        // wrong once items stopped being one-per-type. They do not stack: each has its own
        // `itemId`, its own tier and its own build, so a second copy is a second thing rather than a
        // number going up. The warning survives where it belongs — `manifest()` still reports
        // `owned`, so the package panel says you already have one BEFORE anything is consumed.

        // ── firmware: two conditions, in the order the player can act on them ─────────────────
        //
        // ⚠ The schematic check comes FIRST. A player who is missing both is told the thing they
        // cannot fix by stopping mining — telling them to stop mining, and then refusing again for a
        // schematic they were never going to have, costs them their hashrate for nothing.
        var offering = Catalogue.byId(file.itemType);
        if (offering.map(Catalogue.Offering::firmware).orElse(false)) {
            String schematic = offering.get().requiresSchematic();
            if (save.schematics == null || !save.schematics.contains(schematic)) {
                return Result.refused(
                        Refusal.NO_SCHEMATIC,
                        displayName(file.itemType) + " is firmware. Flashing it needs the "
                                + schematic + " schematic, which is recovered rather than bought -- "
                                + "the image on its own is inert. Keep it; it does not expire.");
            }
            String running = runningTool(save, offering.get().stopsTool());
            if (!running.isBlank()) {
                return Result.refused(
                        Refusal.TOOL_RUNNING,
                        displayName(file.itemType) + " is firmware, and firmware sits underneath the "
                                + "program using it. " + running + " Stop it and flash again.");
            }
        }

        // ⚠ Firmware does not install; it FLASHES, and flashing is a task. The file stays on disk
        // for the duration and is consumed on completion — a file removed at the start would leave a
        // player who quit mid-flash with neither the image nor the tool.
        if (offering.map(Catalogue.Offering::firmware).orElse(false)) {
            return beginFlash(save, file, now);
        }

        ItemState item = new ItemState();
        item.itemType = file.itemType;
        item.displayName = displayName(file.itemType);
        // ⚠ A BOUGHT item lands in the HIGH-RISK zone, not the vault, and the player files it
        // themselves. The vault is meant to be a decision — goods you have not put away are goods
        // anybody can take — and a purchase that filed itself safely would make `design/01` §6's
        // tiers a setting nobody ever touches. A STOLEN item keeps the vault: you already carried
        // the risk of taking it, and charging it again on arrival is the same tax twice.
        item.tier = TransferRules.VENDOR.equals(file.sourceAddress)
                ? StorageRules.ARRIVALS.name()
                : StorageTier.VAULT.name();
        item.acquiredAt = now;
        item.origin = file.sourceAddress.isBlank() ? "recovered" : "taken from " + file.sourceAddress;
        // The build carries over from the package. Without this an installed tool would have no
        // version at all and every later comparison against it would be against nothing.
        item.version = file.version;
        save.items.add(item);
        save.files.remove(file);

        return new Result(true, null, "installed " + item.displayName + " — the package is consumed", BigInteger.ZERO);
    }

    // ── flashing ──────────────────────────────────────────────────────────────────────────────

    /** The task kind a firmware flash runs under. */
    public static final String FLASH_KIND = "flash";

    /**
     * Starts the flash.
     *
     * <h2>⚠ A task, not a pause — it survives a quit and settles on the way back in</h2>
     *
     * Every other timed thing in this game is a {@code TaskState} in {@code save.tasks} and this is
     * no different: closing the client mid-flash must not lose the image, and a flash that only
     * advanced while a window was open would be a device that stops writing its own memory when
     * nobody is looking. {@code GameEngine.resume} settles it on load for the same reason a scan does.
     *
     * <p>⚠ <b>It holds no compute.</b> Flashing is the device writing itself, not this rig computing
     * — the same argument that makes a transfer free of cycles ({@code TransferRules}). What it costs
     * is the tool being down, which is a real cost precisely because mining is frozen.
     */
    private static Result beginFlash(GameSave save, StoredFileState file, Instant now) {
        if (flashing(save).isPresent()) {
            return Result.refused(
                    Refusal.TOOL_RUNNING,
                    "A firmware flash is already running. One at a time — two writes to the same "
                            + "device is how it is bricked.");
        }
        TaskState task = new TaskState(
                FLASH_KIND,
                "flashing " + displayName(file.itemType),
                "",
                0L,
                now,
                now.plusSeconds(Balance.FIRMWARE_FLASH_SECONDS));
        // The file's path rides on the task: the tick that completes this minutes later has no other
        // way to know which image it was writing.
        task.outcome = file.path();
        save.tasks.add(task);
        EventLog.notice(
                save,
                "storage",
                "flashing " + displayName(file.itemType) + " -- "
                        + Balance.FIRMWARE_FLASH_SECONDS + "s. The mining tool is frozen until it "
                        + "finishes. Do not expect it back before then.",
                now);
        return new Result(
                true,
                null,
                "flashing " + displayName(file.itemType) + " — the mining tool is frozen for "
                        + Balance.FIRMWARE_FLASH_SECONDS + "s",
                BigInteger.ZERO);
    }

    /** The flash in progress, if there is one. */
    public static Optional<TaskState> flashing(GameSave save) {
        return save == null || save.tasks == null
                ? Optional.empty()
                : save.tasks.stream()
                        .filter(task -> FLASH_KIND.equals(task.kind))
                        .findFirst();
    }

    /**
     * Which tool a running flash has frozen, or empty.
     *
     * <p>⚠ Read from the CATALOGUE via the task's file, not stored on the task. A tool id copied onto
     * the task is a second record of which tool this firmware affects, and the two would eventually
     * disagree with the offering that decides every other rule about it.
     */
    public static String frozenTool(GameSave save) {
        return flashing(save)
                .flatMap(task -> save.files.stream()
                        .filter(file -> file.path().equals(task.outcome))
                        .findFirst())
                .flatMap(file -> Catalogue.byId(file.itemType))
                .map(Catalogue.Offering::stopsTool)
                .orElse("");
    }

    /**
     * Completes a flash: the item is owned and the image is consumed.
     *
     * <p>⚠ Consumed HERE rather than at the start. A player who quits mid-flash comes back to the
     * image still on disk and the flash settling on load; had it been removed up front, an
     * interrupted flash would have cost them the image and given nothing back.
     */
    public static Optional<ItemState> completeFlash(GameSave save, TaskState task, Instant now) {
        Optional<StoredFileState> file = save.files.stream()
                .filter(entry -> entry.path().equals(task.outcome))
                .findFirst();
        if (file.isEmpty()) {
            // The image is gone — a hand-edited save, or a delete during the flash. Nothing to grant,
            // and nothing to repair: silently dropping the task is the only honest outcome.
            return Optional.empty();
        }
        StoredFileState image = file.get();
        ItemState item = new ItemState();
        item.itemType = image.itemType;
        item.displayName = displayName(image.itemType);
        item.tier = StorageTier.VAULT.name();
        item.acquiredAt = now;
        item.origin = image.sourceAddress.isBlank() ? "flashed" : "flashed from " + image.sourceAddress;
        item.version = image.version;
        save.items.add(item);
        save.files.remove(image);
        return Optional.of(item);
    }

    /**
     * Why the tool this firmware affects cannot be interrupted right now, or empty if it can.
     *
     * <h2>⚠ Deployed miners count, and they are the case worth getting right</h2>
     *
     * Self-mining is the obvious half and a player will think of it. A miner sitting on somebody
     * else's machine is the half they will not: it is <em>this rig's</em> mining tool driving it
     * (the host supplies the compute — <b>I6</b> — not the software), so flashing while one runs is
     * the same interrupted write. Naming the count in the refusal is what makes that discoverable
     * rather than mystifying.
     *
     * <p>Returns a sentence rather than a boolean because the caller has to say <em>what</em> is
     * running; "the tool is running" about a rig with five deployed miners and no self-mining sends
     * the player to look at the wrong readout.
     */
    /**
     * Why {@code offering}'s firmware cannot be flashed right now, or empty.
     *
     * <p>Public so Get Info can say it <b>before</b> the transfer, using the same call
     * {@code install} refuses with. Two derivations of "is the tool running" would eventually
     * disagree, and the shape of that bug is a panel promising an install that then refuses.
     */
    public static String blockedBy(GameSave save, Catalogue.Offering offering) {
        return offering == null || !offering.firmware() ? "" : runningTool(save, offering.stopsTool());
    }

    private static String runningTool(GameSave save, String tool) {
        if (!Catalogue.MINING_TOOL.equals(tool)) {
            // Only the mining tool has a running state modelled today. Anything else is refused
            // nothing — an unknown tool must not silently block an install forever.
            return "";
        }
        long deployed = save.knownNodes.stream()
                .mapToLong(node -> node.deployedMiners.size())
                .sum();
        boolean selfMining = save.rig != null && save.rig.selfMiningCycles > 0;
        if (selfMining && deployed > 0) {
            return "Mining is running: " + save.rig.selfMiningCycles + " cycles self-mining and " + deployed
                    + " deployed miner(s).";
        }
        if (selfMining) {
            return "Mining is running: " + save.rig.selfMiningCycles + " cycles self-mining.";
        }
        if (deployed > 0) {
            return deployed + " deployed miner(s) are still running this rig's mining tool -- they "
                    + "spend the host's compute, not yours, but the software being flashed is ours.";
        }
        return "";
    }

    // ── deleting ──────────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a file from this rig.
     *
     * <h2>Why this is allowed freely, and why it is not a menu item that just does it</h2>
     *
     * Downloads accumulate. A player who has raided a dozen machines has a dozen packages they will
     * never install and cannot sell (the schematic-gated ones — <b>I2</b>), and a filesystem you can
     * only add to is not a filesystem. Deleting your own files is the most ordinary thing a computer
     * does, and refusing it would be the fiction breaking in a way nothing else here does.
     *
     * <p>⚠ <b>Only files this rig actually stores.</b> Everything else in the tree is <em>generated</em>
     * — {@code VirtualFs} derives the system tree, the app bundles and the vault views from state and
     * stores none of it, deliberately ({@code VirtualFs}'s class comment: a stored tree is a cache of
     * game state that eventually disagrees with it). There is nothing to delete, so the refusal says
     * so rather than pretending to succeed and leaving the entry on screen.
     *
     * <h2>⚠ It is genuinely destructive, and the caller must have confirmed</h2>
     *
     * A {@code .frm} is worth 180 EC and a {@code .upg} rather less, and neither comes back. This rule
     * does not ask — asking is the interface's job and the shell's {@code rm} would be wrong to — but
     * it <b>logs what was lost</b>, including the resale value, because an item that vanishes with no
     * trace is indistinguishable from a bug.
     *
     * @return what happened, in the rules' own words
     */
    public static Result delete(GameSave save, String path, Instant now) {
        Optional<StoredFileState> found = at(save, path);
        if (found.isEmpty()) {
            return Result.refused(
                    Refusal.NO_SUCH_FILE,
                    "Nothing to delete at " + path + ". Only files this rig actually stores can be "
                            + "removed -- the system tree, application bundles and vault views are "
                            + "generated from state, not kept on disk.");
        }
        StoredFileState file = found.get();
        // ⚠ A file being written cannot be deleted. `completeFlash` handles a missing image by
        // dropping the task silently, so without this the player would delete mid-flash, wait out
        // the remaining minute, and get nothing — with the log saying a flash had run.
        if (flashing(save).filter(task -> file.path().equals(task.outcome)).isPresent()) {
            return Result.refused(
                    Refusal.TOOL_RUNNING,
                    file.name + " is being flashed right now. Deleting the image mid-write is how a "
                            + "device is bricked -- wait for it to finish.");
        }

        save.files.remove(file);
        // ⚠ The value is named on the way out. A player who deletes a 180 EC firmware image by
        // accident deserves to find out from the log rather than from the market three days later.
        BigInteger worth = "package".equals(file.kind) && sellable(file.itemType)
                ? resaleValue(
                        file.itemType, io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion.parse(file.version))
                : BigInteger.ZERO;
        EventLog.notice(
                save,
                "storage",
                "deleted " + file.name + " from " + file.directory
                        + (worth.signum() > 0 ? " -- it would have sold for " + Ethecoin.format(worth) + "." : "."),
                now);
        return new Result(true, null, "deleted " + file.name, BigInteger.ZERO);
    }

    /** The stored file at this path, if this rig has one. */
    private static Optional<StoredFileState> at(GameSave save, String path) {
        String normalised = VirtualFs.normalise(path);
        return save == null || save.files == null
                ? Optional.empty()
                : save.files.stream()
                        .filter(file -> file.path().equals(normalised))
                        .findFirst();
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

    /**
     * What a copy of this build fetches.
     *
     * <p>⚠ The version is the <b>only</b> thing about a package that changes a number anywhere in the
     * game — see {@code solo/rules/Versions}. It moves what a copy is worth and nothing else, which
     * is what keeps raiding harder machines a reward in value rather than a ladder to a capability
     * ceiling nobody sold (<b>I2</b>).
     */
    public static BigInteger resaleValue(
            String itemType, io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion version) {
        return Versions.resaleWei(
                resaleValue(itemType),
                Catalogue.byId(itemType).map(Catalogue.Offering::priceWei).orElse(BigInteger.ZERO),
                version == null ? io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion.UNKNOWN : version);
    }

    /** What a copy fetches before its build is taken into account. See {@link #RESALE_PERCENT}. */
    public static BigInteger resaleValue(String itemType) {
        return Catalogue.byId(itemType)
                .map(offering -> offering.priceWei()
                        .multiply(BigInteger.valueOf(RESALE_PERCENT))
                        .divide(BigInteger.valueOf(100L)))
                .orElse(BigInteger.ZERO);
    }

    /**
     * Sells a package on the secondary market.
     *
     * <p>The file goes and the balance moves. The caller credits the ledger — this returns the
     * amount rather than touching money itself, so there is exactly one place in the engine that
     * writes a ledger entry.
     */
    public static Result sell(GameSave save, String path) {
        Optional<StoredFileState> found = find(save, path);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_FILE, "no such file: " + path);
        }
        StoredFileState file = found.get();
        // ⚠ Before the kind check, same reasoning as install(), and NOT redundant with it: reselling
        // an unconfirmed package is strictly worse than installing one early, because it turns goods
        // the player has not finished paying for into ethecoin they can spend before the debit is
        // mined. Without this the escrow would have a hole shaped exactly like the secondary market.
        if (locked(save, file)) {
            return Result.refused(
                    Refusal.UNCONFIRMED,
                    file.name + " has not been paid for on-chain yet. It cannot be resold until the "
                            + "block carrying the purchase is confirmed.");
        }
        if (!"package".equals(file.kind) || file.itemType.isBlank()) {
            return Result.refused(Refusal.NOT_INSTALLABLE, file.name + " is not an upgrade.");
        }
        if (!sellable(file.itemType)) {
            // Named, not generic. A player told only "cannot sell" will try again; one told the
            // reason has learned something about how the gates work.
            return Result.refused(
                    Refusal.NOT_SELLABLE,
                    displayName(file.itemType) + " is not gated on ethecoin, so it cannot be turned "
                            + "into ethecoin. Nobody sells a way past a schematic. You can still "
                            + "use it.");
        }
        BigInteger value = resaleValue(file.itemType);
        save.files.remove(file);
        return new Result(true, null, "sold " + displayName(file.itemType) + " on the secondary market", value);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    public static Optional<StoredFileState> find(GameSave save, String path) {
        String p = VirtualFs.normalise(path);
        return save == null
                ? Optional.empty()
                : save.files.stream().filter(f -> f.path().equals(p)).findFirst();
    }

    /** Every stored file directly inside a folder. */
    public static java.util.List<StoredFileState> in(GameSave save, String directory) {
        String d = VirtualFs.normalise(directory);
        return save == null
                ? java.util.List.of()
                : save.files.stream().filter(f -> f.directory.equals(d)).toList();
    }

    /** The catalogue name for an item type. ⚠ Package-private, not private: {@code ShadowMarket}
     * names the same items and a second copy of this lookup would drift the moment one was renamed. */
    static String displayName(String itemType) {
        return Catalogue.byId(itemType).map(Catalogue.Offering::name).orElse(itemType);
    }

    /** Where a download lands unless the player picks somewhere else. It is called Downloads. */
    public static String defaultDestination(String handle) {
        return VirtualFs.home(handle) + "/Downloads";
    }
}
