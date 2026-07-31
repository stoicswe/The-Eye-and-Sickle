package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
import io.github.stoicswe.eyeandsickle.solo.state.FolderState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The player's filing of the machines they have found: create, rename, move, remove, file, unfile.
 *
 * <h2>Why this is a rules class and not a client feature</h2>
 *
 * It could have lived in the client — nothing here changes a number the game reasons about, and the
 * client already owns window positions and theme choices. It does not, for one reason: a folder names
 * a <b>discovered address</b>, and whether an address has been discovered is a rules question the
 * client is specifically not allowed to answer ({@code docs/client/04-terminology-and-education.md}
 * §3.4, Invariant <b>I14</b>). A client-side folder store would either duplicate {@code knownNodes}
 * or accept whatever address it was handed, and the second of those is a free oracle for the one
 * thing every sweep tier is sold on. Keeping it here also means it travels: the day a home server
 * backs a session, the player's filing arrives with everything else instead of being stranded on the
 * machine that made it.
 *
 * <h2>Refusals, never exceptions</h2>
 *
 * Every mutator returns a {@link Refusal} — empty on success, a sentence on failure. Same contract as
 * {@link NetRules#beginSweep}: the shell prints a refusal and a window shows one, and a rules engine
 * that threw would be deciding how the client reports it. The sentences are the player-facing wording,
 * because the alternative is two vocabularies for the same failure.
 *
 * <h2>Self-healing on read, so a hand-edited save opens</h2>
 *
 * {@link #tree} tolerates a cycle, a missing parent, and a node filed under a folder that is gone. It
 * repairs rather than refuses: an orphan re-roots, a cycle is broken at the deepest edge, a dangling
 * {@code folderId} is cleared. {@code SoloSave}'s class comment is explicit that this file belongs to
 * the player and that they may edit it, so every reader here has to survive one that has been edited
 * badly. Refusing to open a save because two folders point at each other would be the wrong trade by
 * a long way.
 */
public final class FolderRules {

    private FolderRules() {}

    /**
     * How deep the tree may go, counting a top-level folder as depth 0.
     *
     * <p>A limit rather than none because the readout indents, and an unbounded tree eventually
     * indents past the panel. Four levels is deeper than any real filing of a few hundred machines
     * needs and shallow enough that the deepest row still fits beside its address.
     */
    public static final int MAX_DEPTH = 4;

    /** Long enough for "eye infrastructure — do not touch", short enough to sit in a column. */
    public static final int MAX_NAME = 48;

    /**
     * The one character a name may not contain.
     *
     * <p>{@code /} separates path segments in every rendering of this tree, so a name containing one
     * would print a path the player could not type back. Everything else is allowed, including
     * spaces: this is a label the player reads, not an identifier anything resolves.
     */
    private static final char SEPARATOR = '/';

    // ================================================================== the read model

    /**
     * The whole tree, parents before children, siblings by name.
     *
     * <p>Ordered so a renderer can walk the list once and indent by {@link NetFolder#depth()} without
     * doing its own traversal — which is what keeps the window and the terminal from disagreeing
     * about the shape of something they both draw.
     *
     * @param save may be null; an absent or empty filing yields an empty list rather than a throw
     */
    public static List<NetFolder> tree(SoloSave save) {
        if (save == null || save.netFolders == null || save.netFolders.isEmpty()) {
            return List.of();
        }
        repair(save);

        Map<String, List<FolderState>> children = new HashMap<>();
        for (FolderState folder : save.netFolders) {
            children.computeIfAbsent(folder.parentId, key -> new ArrayList<>()).add(folder);
        }
        for (List<FolderState> siblings : children.values()) {
            siblings.sort(Comparator.comparing((FolderState f) -> f.name.toLowerCase(Locale.ROOT))
                    .thenComparing(f -> f.folderId));
        }

        Map<String, List<String>> filed = filedByFolder(save);
        List<NetFolder> out = new ArrayList<>();
        walk(children, filed, "", "", 0, out);
        return List.copyOf(out);
    }

    private static void walk(
            Map<String, List<FolderState>> children,
            Map<String, List<String>> filed,
            String parentId,
            String parentPath,
            int depth,
            List<NetFolder> out) {

        for (FolderState folder : children.getOrDefault(parentId, List.of())) {
            String path = parentPath + SEPARATOR + folder.name;
            List<String> addresses = filed.getOrDefault(folder.folderId, List.of());
            // The subtree count is computed after the recursion so a parent can report everything
            // underneath it — which is the number a collapsed row has to show, and the one a player
            // uses to decide whether opening it is worth it.
            int before = out.size();
            out.add(new NetFolder(folder.folderId, folder.parentId, folder.name, path, depth, addresses, 0));
            int at = out.size() - 1;
            walk(children, filed, folder.folderId, path, depth + 1, out);

            int subtree = addresses.size();
            for (int i = before + 1; i < out.size(); i++) {
                subtree += out.get(i).addresses().size();
            }
            out.set(at, out.get(at).withSubtreeCount(subtree));
        }
    }

    /** Discovered machines the player has not filed anywhere, by address. */
    public static List<String> unfiled(SoloSave save) {
        if (save == null || save.knownNodes == null) {
            return List.of();
        }
        repair(save);
        List<String> out = new ArrayList<>();
        for (NodeState node : save.knownNodes) {
            if (node.folderId == null || node.folderId.isBlank()) {
                out.add(node.address);
            }
        }
        out.sort(Comparator.naturalOrder());
        return List.copyOf(out);
    }

    /** The folder a machine is filed under, or empty. */
    public static Optional<NetFolder> folderOf(SoloSave save, String address) {
        NodeState node = node(save, address);
        if (node == null || node.folderId.isBlank()) {
            return Optional.empty();
        }
        return tree(save).stream()
                .filter(f -> f.folderId().equals(node.folderId))
                .findFirst();
    }

    // ================================================================== the intents

    /**
     * Creates a folder under {@code parentId} ({@code ""} for top level).
     *
     * @return the new folder, or a refusal naming what stopped it
     */
    public static Result create(SoloSave save, String parentId, String name, Instant now) {
        if (save == null) {
            return Result.refused("there is no character to file anything for");
        }
        String parent = parentId == null ? "" : parentId.trim();
        String wanted = name == null ? "" : name.trim();

        Refusal bad = checkName(wanted);
        if (bad != null) {
            return Result.refused(bad.why());
        }
        if (!parent.isEmpty() && find(save, parent) == null) {
            return Result.refused("no such folder");
        }
        if (depthOf(save, parent) + 1 > MAX_DEPTH) {
            return Result.refused("folders nest " + (MAX_DEPTH + 1) + " deep at most; this would be one level further");
        }
        if (siblingNamed(save, parent, wanted, "") != null) {
            return Result.refused("a folder called '" + wanted + "' is already there");
        }

        FolderState folder = new FolderState();
        folder.parentId = parent;
        folder.name = wanted;
        folder.createdAt = now == null ? Instant.EPOCH : now;
        save.netFolders.add(folder);
        return Result.created(folder.folderId);
    }

    /** Renames a folder in place. */
    public static Refusal rename(SoloSave save, String folderId, String name) {
        FolderState folder = find(save, folderId);
        if (folder == null) {
            return Refusal.of("no such folder");
        }
        String wanted = name == null ? "" : name.trim();
        Refusal bad = checkName(wanted);
        if (bad != null) {
            return bad;
        }
        if (siblingNamed(save, folder.parentId, wanted, folder.folderId) != null) {
            return Refusal.of("a folder called '" + wanted + "' is already there");
        }
        folder.name = wanted;
        return Refusal.none();
    }

    /**
     * Moves a folder under a new parent ({@code ""} for top level).
     *
     * <p>⚠ Refuses to move a folder inside itself or inside its own descendant. That is not a
     * defensive nicety: the resulting cycle is unreachable from the root, so every folder in it and
     * every machine filed under them would vanish from {@link #tree} at once, with the save on disk
     * still holding them.
     */
    public static Refusal move(SoloSave save, String folderId, String newParentId) {
        FolderState folder = find(save, folderId);
        if (folder == null) {
            return Refusal.of("no such folder");
        }
        String parent = newParentId == null ? "" : newParentId.trim();
        if (parent.equals(folder.folderId)) {
            return Refusal.of("a folder cannot contain itself");
        }
        if (!parent.isEmpty()) {
            if (find(save, parent) == null) {
                return Refusal.of("no such folder");
            }
            if (descendants(save, folder.folderId).contains(parent)) {
                return Refusal.of("a folder cannot be moved inside something it already contains");
            }
        }
        if (siblingNamed(save, parent, folder.name, folder.folderId) != null) {
            return Refusal.of("a folder called '" + folder.name + "' is already there");
        }
        if (depthOf(save, parent) + 1 + heightOf(save, folder.folderId) > MAX_DEPTH) {
            return Refusal.of("that would nest folders more than " + (MAX_DEPTH + 1) + " deep");
        }
        folder.parentId = parent;
        return Refusal.none();
    }

    /**
     * Removes a folder, lifting what was inside it up to where the folder was.
     *
     * <p>⚠ <b>Non-destructive on purpose, and the alternative was considered.</b> A recursive delete
     * would be shorter and would eventually cost somebody an evening's filing on a mistaken click —
     * and unlike everything else in this game there is no risk lesson in it, because filing is inert
     * ({@link FolderState}). Sub-folders and machines re-parent to the removed folder's parent, so
     * the worst outcome of a wrong {@code rmdir} is a flattened level, which is one {@code mkdir} and
     * a few {@code mv}s from repaired.
     */
    public static Refusal remove(SoloSave save, String folderId) {
        FolderState folder = find(save, folderId);
        if (folder == null) {
            return Refusal.of("no such folder");
        }
        String parent = folder.parentId;
        for (FolderState child : save.netFolders) {
            if (child.parentId.equals(folder.folderId)) {
                child.parentId = parent;
            }
        }
        for (NodeState node : nodes(save)) {
            if (folder.folderId.equals(node.folderId)) {
                node.folderId = parent;
            }
        }
        save.netFolders.remove(folder);
        return Refusal.none();
    }

    /**
     * Files a discovered machine under a folder, or unfiles it when {@code folderId} is blank.
     *
     * <p>⚠ The two refusals below say the same words on purpose. "You have not discovered that" and
     * "that address does not exist" are different facts, and a player who could tell them apart could
     * enumerate the world one guess at a time without ever running a sweep — which is the whole
     * product this feature sits next to ({@code docs/design/07-recon-tools.md} §1). One wording, one
     * answer, no oracle.
     */
    public static Refusal file(SoloSave save, String address, String folderId) {
        NodeState node = node(save, address);
        if (node == null) {
            return Refusal.of("no machine you have discovered at that address");
        }
        String wanted = folderId == null ? "" : folderId.trim();
        if (!wanted.isEmpty() && find(save, wanted) == null) {
            return Refusal.of("no such folder");
        }
        node.folderId = wanted;
        return Refusal.none();
    }

    // ================================================================== integrity

    /**
     * Re-roots orphans, breaks cycles and clears dangling filings, in place.
     *
     * <p>Called from every read rather than once at load because the save is the player's to edit at
     * any moment and because it is cheap — this list is tens of entries, not the topology's hundreds.
     * Idempotent: a healthy tree passes through untouched.
     */
    public static void repair(SoloSave save) {
        if (save == null || save.netFolders == null) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (FolderState folder : save.netFolders) {
            if (folder.name == null) {
                folder.name = "";
            }
            if (folder.parentId == null) {
                folder.parentId = "";
            }
            ids.add(folder.folderId);
        }
        for (FolderState folder : save.netFolders) {
            // An orphan re-roots. Dropping it instead would silently delete everything filed under
            // it, which is the one outcome a repair pass must never produce.
            if (!folder.parentId.isEmpty() && !ids.contains(folder.parentId)) {
                folder.parentId = "";
            }
        }
        for (FolderState folder : save.netFolders) {
            if (reachesRoot(save, folder)) {
                continue;
            }
            // In a cycle: cut this folder loose. Whichever member is visited first breaks it, and
            // the rest then reach the root through it.
            folder.parentId = "";
        }
        for (NodeState node : nodes(save)) {
            if (node.folderId == null) {
                node.folderId = "";
            } else if (!node.folderId.isEmpty() && !ids.contains(node.folderId)) {
                node.folderId = "";
            }
        }
    }

    private static boolean reachesRoot(SoloSave save, FolderState from) {
        Set<String> seen = new HashSet<>();
        FolderState at = from;
        while (at != null && !at.parentId.isEmpty()) {
            if (!seen.add(at.folderId)) {
                return false;
            }
            at = find(save, at.parentId);
        }
        return at != null;
    }

    // ================================================================== lookups

    private static Refusal checkName(String name) {
        if (name.isEmpty()) {
            return Refusal.of("a folder needs a name");
        }
        if (name.length() > MAX_NAME) {
            return Refusal.of("a folder name is at most " + MAX_NAME + " characters");
        }
        if (name.indexOf(SEPARATOR) >= 0) {
            return Refusal.of("'" + SEPARATOR + "' separates folders and cannot be part of a name");
        }
        return null;
    }

    private static FolderState siblingNamed(SoloSave save, String parentId, String name, String ignoreId) {
        for (FolderState folder : save.netFolders) {
            if (folder.folderId.equals(ignoreId)) {
                continue;
            }
            if (folder.parentId.equals(parentId) && folder.name.equalsIgnoreCase(name)) {
                return folder;
            }
        }
        return null;
    }

    /** Depth of a folder id, where {@code ""} (top level) is {@code -1} so its children are 0. */
    private static int depthOf(SoloSave save, String folderId) {
        if (folderId == null || folderId.isEmpty()) {
            return -1;
        }
        int depth = 0;
        Set<String> seen = new HashSet<>();
        FolderState at = find(save, folderId);
        while (at != null && !at.parentId.isEmpty() && seen.add(at.folderId)) {
            depth++;
            at = find(save, at.parentId);
        }
        return depth;
    }

    /** How many levels of folder sit below this one; 0 for a leaf. */
    private static int heightOf(SoloSave save, String folderId) {
        int tallest = 0;
        for (FolderState child : save.netFolders) {
            if (child.parentId.equals(folderId)) {
                tallest = Math.max(tallest, 1 + heightOf(save, child.folderId));
            }
        }
        return tallest;
    }

    private static Set<String> descendants(SoloSave save, String folderId) {
        Set<String> out = new HashSet<>();
        collect(save, folderId, out);
        return out;
    }

    private static void collect(SoloSave save, String folderId, Set<String> out) {
        for (FolderState child : save.netFolders) {
            if (child.parentId.equals(folderId) && out.add(child.folderId)) {
                collect(save, child.folderId, out);
            }
        }
    }

    /** A folder by id, or null. Public so the session layer can refuse before it calls a mutator. */
    public static FolderState find(SoloSave save, String folderId) {
        if (save == null || save.netFolders == null || folderId == null || folderId.isBlank()) {
            return null;
        }
        String wanted = folderId.trim();
        for (FolderState folder : save.netFolders) {
            if (folder.folderId.equals(wanted)) {
                return folder;
            }
        }
        return null;
    }

    /**
     * A folder by the {@code /a/b} path a player typed, or null.
     *
     * <p>Case-insensitive, because the tree is a label the player reads rather than an identifier —
     * and because refusing {@code /Eye} for a folder called {@code eye} would be the least helpful
     * possible reading of a name they chose themselves.
     */
    public static FolderState byPath(SoloSave save, String path) {
        if (save == null || path == null) {
            return null;
        }
        String trimmed = path.trim();
        while (trimmed.startsWith(String.valueOf(SEPARATOR))) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith(String.valueOf(SEPARATOR))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String parent = "";
        FolderState at = null;
        for (String segment : trimmed.split("/")) {
            at = siblingNamed(save, parent, segment, "");
            if (at == null) {
                return null;
            }
            parent = at.folderId;
        }
        return at;
    }

    private static Map<String, List<String>> filedByFolder(SoloSave save) {
        Map<String, List<String>> out = new HashMap<>();
        for (NodeState node : nodes(save)) {
            if (node.folderId != null && !node.folderId.isEmpty()) {
                out.computeIfAbsent(node.folderId, key -> new ArrayList<>()).add(node.address);
            }
        }
        for (List<String> addresses : out.values()) {
            addresses.sort(Comparator.naturalOrder());
        }
        return out;
    }

    private static List<NodeState> nodes(SoloSave save) {
        return save == null || save.knownNodes == null ? List.of() : save.knownNodes;
    }

    private static NodeState node(SoloSave save, String address) {
        if (address == null) {
            return null;
        }
        String wanted = address.trim();
        for (NodeState node : nodes(save)) {
            if (node.address.equalsIgnoreCase(wanted)) {
                return node;
            }
        }
        return null;
    }

    // ================================================================== results

    /**
     * Empty on success, or one sentence saying what stopped it.
     *
     * <p>A record rather than {@code Optional<String>} so the two readings cannot be confused at a
     * call site — {@code refused()} reads as a question and an empty Optional reads as "nothing
     * happened", which is the opposite of what an empty refusal means here.
     */
    public record Refusal(String why) {

        public static Refusal none() {
            return new Refusal("");
        }

        public static Refusal of(String why) {
            return new Refusal(why == null ? "" : why);
        }

        public boolean refused() {
            return !why.isEmpty();
        }
    }

    /** A {@link Refusal} that also carries the id of a folder that was created. */
    public record Result(String folderId, String why) {

        static Result created(String folderId) {
            return new Result(folderId, "");
        }

        static Result refused(String why) {
            return new Result("", why);
        }

        public boolean refused() {
            return !why.isEmpty();
        }
    }
}
