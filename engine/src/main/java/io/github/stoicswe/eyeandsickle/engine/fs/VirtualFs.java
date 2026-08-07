package io.github.stoicswe.eyeandsickle.engine.fs;

import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * The filesystem every machine in the game appears to have.
 *
 * <h2>⚠ Nothing here touches a real filesystem, and that is a safety boundary</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.1 rule 3 forbids concatenating anything a
 * player types into a host filesystem call. Every path in this class is a key into a tree computed on
 * demand; no {@code java.io} or {@code java.nio} type appears anywhere in the package. That mattered
 * when the surface was a shell printing {@code /rig/storage/vault}; it matters more now that it is a
 * file manager, because a file manager is precisely the widget that makes a path look openable.
 *
 * <h2>Generated, never stored</h2>
 *
 * A host's tree is derived from its address and its state each time it is asked for. Two reasons, and
 * the second is the load-bearing one:
 *
 * <ul>
 *   <li>Fifty hosts × a hundred entries is five thousand records in a save file that is meant to be
 *       small enough to read by hand.
 *   <li><b>A generated tree cannot drift from the rules.</b> A miner appears in {@code /etc/systemd}
 *       because {@code deployedMiners} is non-empty, and it stops appearing the moment the miner is
 *       killed — there is no second copy of that fact to forget to update. A stored tree would be a
 *       cache of game state and would eventually disagree with it, and the surface it disagreed on
 *       would be the one the player uses to decide whether a machine has been tampered with.
 * </ul>
 *
 * <p>Determinism comes from seeding on the address, so the same machine has the same filesystem
 * across a reload — a host whose directory listing reshuffled every visit would make "was this here
 * before?" unanswerable, which is the question {@code docs/design/04-mining.md} §3.1 is built on.
 *
 * <h2>Ubuntu's layout, because it is the one worth learning</h2>
 *
 * The tree is the Filesystem Hierarchy Standard as Ubuntu ships it: {@code /etc} for configuration,
 * {@code /var/log} for logs, {@code /home/<user>} for people, {@code /usr/bin} for programs,
 * {@code /mnt} for things mounted by hand. Every one of those is real and transfers to any Linux
 * machine the player ever touches, which is the cheapest teaching in the client — nobody has to be
 * told what {@code /var/log} is if they have already gone looking in it for something.
 *
 * <p>⚠ It is <b>simplified</b>, and It is simplified and the simplifications are: no permissions
 * model that actually gates anything (the rules decide readability, not a mode string), no inodes, no
 * hard links, and a much smaller {@code /usr}. The layout is true; the depth is not.
 */
public final class VirtualFs {

    private VirtualFs() {}

    /** Where a machine's own operator lives. Hosts have one user; the player's rig uses the handle. */
    public static final String DEFAULT_USER = "operator";

    /** Mount point for another machine. Both macOS and FreeBSD keep {@code /Volumes}-style mounts;
     * {@code /mnt} is FreeBSD's own empty mount point and is the one this uses. */
    public static final String MNT = "/mnt";

    /** The four roots. macOS's names, and the whole reason the tree reads as a desktop. */
    public static final String APPLICATIONS = "/Applications";

    /** Shared support that is not the base system and not a user's. */
    public static final String LIBRARY = "/Library";

    /** The base operating system. See {@link SystemTree} — nothing in it opens. */
    public static final String SYSTEM = SystemTree.ROOT;

    public static final String USERS = "/Users";

    // ── The player's own rig ──────────────────────────────────────────────────────────────────

    /**
     * One owned item, as the filesystem needs to see it.
     *
     * <p>⚠ Carries the {@code tier}, and that field is the whole reason this record exists rather
     * than a bare name. An upgrade shown inside an app bundle is a <b>view</b> onto an item that
     * lives in a storage tier, and the tier is still what decides whether a remote actor can take it
     * ({@code docs/design/01-core-resources.md} §6). Drop the tier here and the Applications folder
     * becomes a fourth exposure surface that bypasses the vault.
     */
    public record Installed(String itemType, String displayName, String tier, boolean equipped) {}

    /**
     * The hidden folder every owned item actually lives in.
     *
     * <p>⚠ Hidden, and under the home rather than at {@code /mnt}. The three tiers are the player's
     * private store, not volumes anybody mounted, and a {@code /mnt/vault} sitting in the sidebar of
     * a machine an intruder is standing on is a signpost to the one place that is supposed to be
     * safe. A leading dot is only a convention and hides nothing from a determined reader — which is
     * exactly the right strength of protection, because {@code §6} already says what the real
     * protection is, and it is the tier, not the folder name.
     */
    public static final String VAULTSTORE = ".VaultStore";

    /** {@code ~/.VaultStore/vault} — never exposed (§6). */
    public static String vaultDir(String user) {
        return home(user) + "/" + VAULTSTORE + "/vault";
    }

    /** {@code ~/.VaultStore/standard} — exposed while the owner is online. */
    public static String standardDir(String user) {
        return home(user) + "/" + VAULTSTORE + "/standard";
    }

    /** {@code ~/.VaultStore/hot} — always exposed, raidable offline. */
    public static String hotDir(String user) {
        return home(user) + "/" + VAULTSTORE + "/hot";
    }

    /**
     * {@code /Users/<name>}.
     *
     * <p>⚠ macOS's spelling, not Unix's {@code /home}. uOS's root is macOS-shaped and its base
     * system is FreeBSD-shaped, which is not an arbitrary pairing — macOS's own userland descends
     * from FreeBSD, so this is the one real system the combination is closest to.
     */
    public static String home(String user) {
        return USERS + "/" + (user == null || user.isBlank() ? DEFAULT_USER : user);
    }

    /** {@code /Applications} — system-wide, as on macOS, rather than per-user. */
    public static String applications(String user) {
        return APPLICATIONS;
    }

    /**
     * Where an intrusion is recorded.
     *
     * <p>⚠ {@code /Library/Logs} and not {@code /System/var/log}, and the reason is structural: the
     * base system does not open. A record of an intrusion that nobody can read is not a record. So
     * it sits with the machine's other readable logs, which is also where macOS puts a log that
     * belongs to the computer rather than to the OS. See {@code solo/rules/AccessLog}.
     */
    public static final String ACCESS_LOG = "/Library/Logs/remote-access.log";

    /**
     * The home folder's own layout.
     *
     * <p>⚠ <b>The root is Ubuntu's and the home is macOS's, and that is a deliberate hybrid.</b> uOS
     * is the game's own system ({@code docs/client/03}), and it takes the Filesystem Hierarchy
     * Standard for its root — {@code /etc}, {@code /var/log}, {@code /usr} — because those are real
     * and transfer to any Linux machine. The <em>home</em> takes the arrangement a desktop user
     * actually recognises: Applications, Desktop, Documents, Downloads, Movies, Music, Pictures.
     * Both halves are real somewhere; neither is invented. What would be dishonest is claiming this
     * is Ubuntu, and nothing in the game does.
     */
    private static final List<String> HOME_FOLDERS =
            List.of("Desktop", "Documents", "Downloads", "Movies", "Music", "Pictures");

    public static List<String> homeFolders() {
        return HOME_FOLDERS;
    }

    /**
     * The root, and it is four entries plus a mount point.
     *
     * <h2>⚠ This replaced a Linux FHS root on 2026-07-28</h2>
     *
     * It was {@code /bin /boot /etc /home /usr /var …} — twenty directories, of which a desktop user
     * touches two. macOS's four say what each one is <em>for</em> at the top level: programs, shared
     * support, the operating system, people. The FHS did not disappear; it moved inside
     * {@code /System}, where it belongs — those directories <b>are</b> the operating system, and
     * putting them at the root was always the thing that made a Unix filesystem look forbidding.
     *
     * <p>{@code /mnt} stays at the root because a mounted machine is not any of the four.
     */
    public static List<String> rigSkeleton() {
        return List.of(APPLICATIONS, LIBRARY, SYSTEM, USERS, MNT);
    }

    /**
     * A directory listing for the player's own rig.
     *
     * <p>The caller supplies what the rules know — the handle and the owned items — so this class
     * stays a layout and never becomes a second place that decides what a player owns.
     *
     * @param accessLogLines how many lines the remote-access log holds, so its size is honest. Zero
     *     is normal and is what solo always reports: there are no remote actors in a single-player
     *     game, so nothing ever writes to it
     */
    public static List<FsEntry> listRig(
            String path,
            String handle,
            List<Installed> items,
            int accessLogLines,
            List<io.github.stoicswe.eyeandsickle.engine.state.RecentEntry> recents,
            List<io.github.stoicswe.eyeandsickle.engine.state.StoredFileState> stored,
            Instant now) {
        String p = normalise(path);
        String user = handle == null || handle.isBlank() ? DEFAULT_USER : handle;
        String home = home(user);
        List<Installed> owned = items == null ? List.<Installed>of() : items;
        List<FsEntry> out = new ArrayList<>();

        if (p.equals("/")) {
            for (String dir : rigSkeleton()) {
                out.add(dir(dir, "root", now));
            }
        } else if (SystemTree.isSystem(p)) {
            // ⚠ Readable, because this is the player's OWN machine. The base system is read-ONLY
            // (every mode in it is r-xr-xr-x); it was never supposed to be unlookable. An earlier
            // version passed false here and the file manager told players to "breach it first" —
            // about their own rig.
            return SystemTree.list(p, true, now);
        } else if (p.equals(USERS)) {
            out.add(dir(home, user, now));
        } else if (p.equals(LIBRARY)) {
            for (String folder : List.of("Application Support", "Caches", "Logs", "Preferences")) {
                out.add(dir(LIBRARY + "/" + folder, user, now));
            }
        } else if (p.equals(LIBRARY + "/Logs")) {
            out.add(file(LIBRARY + "/Logs/system.log", 48_204, "root", now, FsKind.FILE));
            // ⚠ The one log that is the player's business, and it is here rather than in /System
            // because /System does not open. An intrusion record nobody can read is not a record.
            out.add(file(ACCESS_LOG, accessLogLines * 96L, user, now, FsKind.FILE));
        } else if (p.equals(MNT)) {
            return List.of();
        } else if (p.equals(APPLICATIONS)) {
            for (Apps.App app : Apps.catalogue()) {
                out.add(dir(APPLICATIONS + "/" + app.bundle(), user, now));
            }
        } else if (p.startsWith(APPLICATIONS + "/")) {
            bundle(out, p, APPLICATIONS, owned, user, now);
        } else if (p.equals(home)) {
            for (String folder : HOME_FOLDERS) {
                out.add(dir(home + "/" + folder, user, now));
            }
            out.add(dir(home + "/Library", user, now));
            out.add(dir(home + "/" + VAULTSTORE, user, now));
            out.add(dir(home + "/.Trash", user, now));
            out.add(dir(home + "/.config", user, now));
            out.add(dir(home + "/.local", user, now));
            // A shell history in the home is real, and it is one of the more interesting things an
            // intruder standing here can find — which is why it survived the restructure.
            out.add(file(home + "/.bash_history", 2_048, user, now, FsKind.FILE));
        } else if (p.equals(home + "/" + VAULTSTORE)) {
            out.add(dir(vaultDir(user), user, now));
            out.add(dir(standardDir(user), user, now));
            out.add(dir(hotDir(user), user, now));
        } else if (p.equals(vaultDir(user))) {
            items(out, vaultDir(user), owned, "VAULT", user, now);
        } else if (p.equals(standardDir(user))) {
            items(out, standardDir(user), owned, "STANDARD_STORAGE", user, now);
        } else if (p.equals(hotDir(user))) {
            items(out, hotDir(user), owned, "HIGH_HACKABLE_ZONE", user, now);
        } else if (p.equals(home + "/.local")) {
            out.add(dir(home + "/.local/share", user, now));
        } else if (p.equals(home + "/.local/share")) {
            out.add(dir(Recents.dirFor(user), user, now));
        } else if (p.equals(Recents.dirFor(user))) {
            for (var recent :
                    recents == null ? List.<io.github.stoicswe.eyeandsickle.engine.state.RecentEntry>of() : recents) {
                out.add(new FsEntry(
                        nameOf(recent.path),
                        recent.path,
                        recent.directory ? FsKind.DIRECTORY : FsKind.SYMLINK,
                        0,
                        "lrwxrwxrwx",
                        user,
                        user,
                        recent.at,
                        true));
            }
        }

        // Files the player actually downloaded, wherever they put them. The only stored part of
        // this tree — everything else is derived. See StoredFileState.
        for (var file :
                stored == null ? List.<io.github.stoicswe.eyeandsickle.engine.state.StoredFileState>of() : stored) {
            if (file.directory.equals(p)) {
                out.add(new FsEntry(
                        file.name,
                        file.path(),
                        // ⚠ Both installable suffixes mark as executable. `.frm` is firmware and
                        // `.upg` is software, and `ls -F` marks a thing you can run — a firmware
                        // image that listed as a plain file would read as inert data.
                        file.name.endsWith(".upg") || file.name.endsWith(".frm") ? FsKind.EXECUTABLE : FsKind.FILE,
                        file.bytes,
                        "-rw-r--r--",
                        user,
                        user,
                        file.at,
                        true));
            }
        }
        out.sort(ORDER);
        return List.copyOf(out);
    }

    /**
     * Inside an application bundle.
     *
     * <p>The real macOS layout: {@code Contents/Info.plist}, {@code Contents/MacOS/<binary>},
     * {@code Contents/Resources}. ⚠ {@code Contents/Upgrades} is ours and is not part of a real bundle.
     */
    private static void bundle(
            List<FsEntry> out, String path, String applications, List<Installed> owned, String user, Instant now) {
        String rest = path.substring(applications.length() + 1);
        String[] parts = rest.split("/");
        Optional<Apps.App> app = Apps.byBundle(parts[0]);
        if (app.isEmpty()) {
            return;
        }
        String base = applications + "/" + parts[0];

        if (parts.length == 1) {
            out.add(dir(base + "/Contents", user, now));
            return;
        }
        if (parts.length == 2 && parts[1].equals("Contents")) {
            out.add(file(base + "/Contents/Info.plist", 1_120, user, now, FsKind.FILE));
            out.add(dir(base + "/Contents/uOS", user, now));
            out.add(dir(base + "/Contents/Resources", user, now));
            out.add(dir(base + "/Contents/" + Apps.UPGRADES, user, now));
            return;
        }
        if (parts.length == 3 && parts[1].equals("Contents") && parts[2].equals(Apps.BINARIES)) {
            out.add(file(
                    base + "/Contents/" + Apps.BINARIES + "/" + app.get().binary(),
                    420_000,
                    user,
                    now,
                    FsKind.EXECUTABLE));
            return;
        }
        if (parts.length == 3 && parts[1].equals("Contents") && parts[2].equals("Resources")) {
            out.add(file(base + "/Contents/Resources/icon.png", 24_600, user, now, FsKind.FILE));
            out.add(file(base + "/Contents/Resources/manual.txt", 3_800, user, now, FsKind.FILE));
            return;
        }
        if (parts.length == 3 && parts[1].equals("Contents") && parts[2].equals(Apps.UPGRADES)) {
            String dir = base + "/Contents/" + Apps.UPGRADES;
            for (Installed item : owned) {
                if (Apps.forItem(item.itemType())
                        .map(a -> a.bundle().equals(parts[0]))
                        .orElse(false)) {
                    // ⚠ Readable to the owner, always. Whether a REMOTE actor may take it is the
                    // tier's answer and is decided in AccessLog, not here — this is a view onto an
                    // item, not a second place it lives.
                    String suffix =
                            io.github.stoicswe.eyeandsickle.engine.rules.Repac.installableSuffix(item.itemType());
                    out.add(new FsEntry(
                            slug(item.displayName()) + suffix,
                            dir + "/" + slug(item.displayName()) + suffix,
                            FsKind.FILE,
                            upgradeBytes(item.itemType()),
                            "-rw-r--r--",
                            user,
                            user,
                            now,
                            true));
                }
            }
        }
    }

    private static void items(
            List<FsEntry> out, String base, List<Installed> owned, String tier, String user, Instant now) {
        for (Installed item : owned) {
            if (!tier.equals(item.tier())) {
                continue;
            }
            out.add(new FsEntry(
                    slug(item.displayName()),
                    base + "/" + slug(item.displayName()),
                    FsKind.FILE,
                    4_096,
                    "-rw-------",
                    user,
                    user,
                    now,
                    true));
        }
    }

    // ── A machine somebody else owns ──────────────────────────────────────────────────────────

    /**
     * A directory listing for a host.
     *
     * <p>⚠ <b>{@code readable} is the rules' answer and this method is where it is decided once.</b>
     * Without a foothold the tree is visible in outline and nothing in it opens — which is the honest
     * shape, because knowing a machine has a {@code /home/dana} is exactly what a port scan tells you
     * in reality, and reading what is in it is what breaking in buys. A viewer must not re-derive
     * this; it renders what it is given.
     */
    public static List<FsEntry> listHost(HostState host, String path, Instant now) {
        return listHost(host, path, List.of(), now);
    }

    /**
     * The same, with the miners the player has deployed on this host.
     *
     * <p>⚠ They are passed in rather than read off {@link HostState}, because they are not on it:
     * {@code HostState} is the world and {@code NodeState} is what the <em>player</em> knows, and a
     * deployed miner is the player's own record. Reaching across that split inside a layout class
     * would put a knowledge question in the one place that must only answer shape questions.
     */
    public static List<FsEntry> listHost(HostState host, String path, List<String> minerIds, Instant now) {
        String p = normalise(path);
        boolean readable = host != null && host.foothold;
        String user = hostUser(host);
        String home = home(user);
        Random random = seeded(host);
        List<FsEntry> out = new ArrayList<>();

        if (p.equals("/")) {
            for (String dir : rigSkeleton()) {
                out.add(dir(dir, "root", now, readable));
            }
        } else if (SystemTree.isSystem(p)) {
            // ⚠ Somebody else's base system follows the same rule as the rest of their machine:
            // visible in outline always, readable once you hold a foothold. Which is realistic and
            // is a genuine recon reward — their rc.conf tells you what that box actually runs.
            return SystemTree.list(p, readable, now);
        } else if (p.equals(USERS)) {
            out.add(dir(home, user, now, readable));
        } else if (p.equals(LIBRARY)) {
            out.add(dir(LIBRARY + "/Logs", "root", now, readable));
            out.add(dir(LIBRARY + "/Preferences", "root", now, readable));
        } else if (p.equals(LIBRARY + "/Logs")) {
            out.add(file(
                    LIBRARY + "/Logs/system.log", 20_000 + random.nextInt(60_000), "root", now, FsKind.FILE, readable));
            out.add(file(
                    LIBRARY + "/Logs/auth.log", 4_000 + random.nextInt(20_000), "root", now, FsKind.FILE, readable));
        } else if (p.equals(APPLICATIONS) || p.startsWith(APPLICATIONS + "/")) {
            hostApplications(out, host, p, APPLICATIONS, user, now, readable);
        } else if (p.equals(home)) {
            homeOf(out, host, user, now, readable, random);
        } else if (p.equals(home + "/Library")) {
            out.add(dir(home + "/Library/Preferences", user, now, readable));
            // Where a deployed miner hides on a desktop system: a launch agent nobody installed.
            // The desktop equivalent of the unit file it used to be, and the same tell.
            out.add(dir(home + "/Library/LaunchAgents", user, now, readable));
        } else if (p.equals(home + "/Library/LaunchAgents")) {
            for (String minerId : minerIds == null ? List.<String>of() : minerIds) {
                String name = "com.uos.agent." + shortId(minerId) + ".plist";
                out.add(file(home + "/Library/LaunchAgents/" + name, 386, user, now, FsKind.FILE, readable));
            }
        }
        out.sort(ORDER);
        return List.copyOf(out);
    }

    /**
     * A host's Applications folder and the upgrades in it.
     *
     * <p>Which programs a host carries is derived from its address, so the same machine always holds
     * the same things and "I saw a deep sweep on that box" stays true between visits.
     */
    private static void hostApplications(
            List<FsEntry> out, HostState host, String path, String base, String user, Instant now, boolean readable) {
        Random random = seeded(host);
        List<Apps.App> present = Apps.catalogue().stream()
                .filter(app -> !app.itemPrefixes().isEmpty())
                .filter(app -> random.nextInt(100) < 45)
                .toList();

        if (path.equals(base)) {
            for (Apps.App app : present) {
                out.add(dir(base + "/" + app.bundle(), user, now, readable));
            }
            return;
        }
        String[] parts = path.substring(base.length() + 1).split("/");
        Optional<Apps.App> app = Apps.byBundle(parts[0]);
        if (app.isEmpty() || !present.contains(app.get())) {
            return;
        }
        String bundle = base + "/" + parts[0];
        if (parts.length == 1) {
            out.add(dir(bundle + "/Contents", user, now, readable));
        } else if (parts.length == 2) {
            out.add(file(bundle + "/Contents/Info.plist", 1_120, user, now, FsKind.FILE, readable));
            out.add(dir(bundle + "/Contents/" + Apps.BINARIES, user, now, readable));
            out.add(dir(bundle + "/Contents/" + Apps.UPGRADES, user, now, readable));
        } else if (parts.length == 3 && parts[2].equals(Apps.BINARIES)) {
            out.add(file(
                    bundle + "/Contents/" + Apps.BINARIES + "/" + app.get().binary(),
                    420_000,
                    user,
                    now,
                    FsKind.EXECUTABLE,
                    readable));
        } else if (parts.length == 3 && parts[2].equals(Apps.UPGRADES)) {
            // ⚠ `.pkg` on somebody else's machine, `.upg` on yours. A vendor package is not the
            // same object as an installable one, and Repac is the step between — see solo/rules/Repac.
            //
            // ⚠ `-firmware` rather than `-upgrade` when it is one, so the distinction is visible in
            // `ls` before anything is spent. Same reasoning as the `.pkg`/`.upg` rename: a fact the
            // player can see in the listing is a fact they do not have to be told twice, and firmware
            // is the class with conditions attached to installing it.
            String name = app.get().binary() + (Apps.isFirmwareApp(app.get()) ? "-firmware" : "-upgrade");
            out.add(new FsEntry(
                    name + ".pkg",
                    bundle + "/Contents/" + Apps.UPGRADES + "/" + name + ".pkg",
                    FsKind.FILE,
                    upgradeBytes(app.get().id() + host.address),
                    "-rw-r--r--",
                    user,
                    user,
                    now,
                    readable));
        }
    }

    private static void homeOf(
            List<FsEntry> out, HostState host, String user, Instant now, boolean readable, Random random) {
        String home = home(user);
        out.add(dir(home + "/Library", user, now, readable));
        out.add(dir(home + "/.ssh", user, now, readable));
        out.add(dir(home + "/Desktop", user, now, readable));
        out.add(dir(home + "/Documents", user, now, readable));
        out.add(dir(home + "/Downloads", user, now, readable));
        out.add(file(home + "/.bash_history", 400 + random.nextInt(3_000), user, now, FsKind.FILE, readable));
        if (host == null) {
            return;
        }
        if (!host.documentId.isBlank() && !host.documentTaken) {
            out.add(new FsEntry(
                    documentFileName(host.documentId),
                    home + "/Documents/" + documentFileName(host.documentId),
                    FsKind.DOCUMENT,
                    2_400 + random.nextInt(6_000),
                    "-rw-r--r--",
                    user,
                    user,
                    now,
                    readable));
        }
        if (host.lootWei.signum() > 0 && !host.looted) {
            out.add(new FsEntry(
                    "wallet.dat",
                    home + "/wallet.dat",
                    FsKind.LOOT,
                    // ⚠ A file SIZE, so bytes rather than money — the loot amount doubles as the
                    // wallet's size on disk, which is the joke and is why it is not reformatted.
                    // Clamped into a long because a size is one; a wallet over nine exabytes is not
                    // a case this filesystem has to render.
                    host.lootWei.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(),
                    "-rw-------",
                    user,
                    user,
                    now,
                    readable));
        }
    }

    /** The file a recovered fragment appears as. Deterministic, so it can be found again. */
    public static String documentFileName(String documentId) {
        String id = documentId == null ? "" : documentId;
        String tail = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
        return (tail.isBlank() ? "fragment" : tail) + ".txt";
    }

    /**
     * The account name a host's own operator uses. Derived from the address, so it is stable.
     *
     * <p>⚠ The pool and the hash both live in {@link io.github.stoicswe.eyeandsickle.engine.net.NpcNames}
     * rather than here. This method used to hold an eight-name array indexed by
     * {@code address.hashCode()}, which walked the pool in lockstep with the host index — every
     * server's operators arrived in the same rotation, so the name was the index in disguise. That is
     * the trap {@code DocumentPool} documents, hit a second time; the fix belongs in one place.
     */
    public static String hostUser(HostState host) {
        if (host == null) {
            return DEFAULT_USER;
        }
        return io.github.stoicswe.eyeandsickle.engine.net.NpcNames.operator(host.address);
    }

    /** Whether a path is one this tree has anything at. Used to refuse {@code cd} honestly. */
    public static boolean isDirectory(HostState host, String path, Instant now) {
        String p = normalise(path);
        if (p.equals("/")) {
            return true;
        }
        List<FsEntry> parent = host == null ? List.of() : listHost(host, parentOf(p), now);
        return parent.stream().anyMatch(e -> e.path().equals(p) && e.directory());
    }

    /** Finds one entry by absolute path, or empty. */
    public static Optional<FsEntry> find(List<FsEntry> siblings, String path) {
        String p = normalise(path);
        return siblings.stream().filter(e -> e.path().equals(p)).findFirst();
    }

    // ── Paths ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Absolute, no trailing slash, {@code .} and {@code ..} resolved.
     *
     * <p>⚠ {@code ..} is resolved <b>here</b>, textually, and can never climb above {@code /}. That
     * is not a convenience: it is the guard that makes a path from a player a key into this tree and
     * nothing else. A {@code ..} left unresolved and passed to something that later did touch a
     * filesystem is the classic traversal bug, and the cheapest place to make it impossible is the
     * one function every path goes through.
     */
    public static String normalise(String path) {
        String raw = path == null ? "" : path.trim();
        if (raw.isEmpty()) {
            return "/";
        }
        List<String> parts = new ArrayList<>();
        for (String segment : raw.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
                continue;
            }
            parts.add(segment);
        }
        return parts.isEmpty() ? "/" : "/" + String.join("/", parts);
    }

    /** Resolves {@code path} against {@code cwd} — absolute wins, relative appends. */
    public static String resolve(String cwd, String path) {
        String target = path == null ? "" : path.trim();
        if (target.isEmpty()) {
            return normalise(cwd);
        }
        if (target.startsWith("/")) {
            return normalise(target);
        }
        return normalise(normalise(cwd) + "/" + target);
    }

    public static String parentOf(String path) {
        String p = normalise(path);
        int slash = p.lastIndexOf('/');
        return slash <= 0 ? "/" : p.substring(0, slash);
    }

    public static String nameOf(String path) {
        String p = normalise(path);
        return p.equals("/") ? "/" : p.substring(p.lastIndexOf('/') + 1);
    }

    // ── Construction helpers ──────────────────────────────────────────────────────────────────

    /**
     * Directories first, then by name.
     *
     * <p>Nautilus's own default, and it is the right one for a reason beyond familiarity: a listing
     * where directories are interleaved with files makes "what can I go into" a per-row decision, and
     * this window's whole job is going somewhere.
     */
    private static final Comparator<FsEntry> ORDER = Comparator.comparing((FsEntry e) -> !e.directory())
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));

    private static FsEntry dir(String path, String owner, Instant now) {
        return dir(path, owner, now, true);
    }

    private static FsEntry dir(String path, String owner, Instant now, boolean readable) {
        return new FsEntry(nameOf(path), path, FsKind.DIRECTORY, 0, "drwxr-xr-x", owner, owner, now, readable);
    }

    private static FsEntry mount(String path, String note, Instant now) {
        return new FsEntry(nameOf(path), path, FsKind.MOUNT, 0, "drwx------", DEFAULT_USER, DEFAULT_USER, now, true);
    }

    private static FsEntry file(String path, long size, String owner, Instant now, FsKind kind) {
        return file(path, size, owner, now, kind, true);
    }

    private static FsEntry file(String path, long size, String owner, Instant now, FsKind kind, boolean readable) {
        String mode = kind == FsKind.EXECUTABLE ? "-rwxr-xr-x" : "-rw-r--r--";
        return new FsEntry(nameOf(path), path, kind, size, mode, owner, owner, now, readable);
    }

    /**
     * ⚠ Seeded on the address, never on a clock or a counter.
     *
     * <p>A host whose listing changed between visits would make "was this file here last time?"
     * unanswerable, and that question is the entire mechanic {@code docs/design/04-mining.md} §3.1
     * describes. The same rule the topology generator already follows.
     */
    private static Random seeded(HostState host) {
        return new Random(seedOf(host == null ? "" : host.address));
    }

    private static int seedOf(String address) {
        return address == null ? 0 : address.hashCode();
    }

    private static String shortId(String id) {
        String s = id == null ? "" : id.replace("-", "");
        return s.length() <= 8 ? s : s.substring(0, 8);
    }

    /**
     * How big an upgrade package is, in bytes.
     *
     * <h2>⚠ These numbers are load-bearing now, because transfer time is derived from them</h2>
     *
     * A software package is <b>tens to hundreds of megabytes</b>. That was irrelevant while sizes
     * were decoration; the moment a download takes {@code Balance.transferTime(bytes)} it decides
     * whether stealing an upgrade is a ten-second commitment or a click. Forty to three hundred and
     * twenty megabytes puts a theft at roughly 2–17 seconds on a 150 Mbit uplink, which is long
     * enough to be a decision and short enough not to be a wait.
     *
     * <p>Deterministic from the item type, so the same upgrade is the same size every time — a
     * player who learns that the deep sweep is the big one should keep being right.
     */
    public static long upgradeBytes(String itemType) {
        long seed = Math.abs((long) String.valueOf(itemType).hashCode());
        return 40_000_000L + (seed % 281L) * 1_000_000L;
    }

    /** A blueprint is a specification, not a binary — megabytes, not hundreds of them. */
    public static long schematicBytes(String itemType) {
        long seed = Math.abs((long) String.valueOf(itemType).hashCode());
        return 2_000_000L + (seed % 19L) * 1_000_000L;
    }

    /** A file name from an item's display name — lowercase, no spaces, the way a file is named. */
    public static String slug(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) ? c : '-');
        }
        String slug = out.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "item" : slug;
    }
}
