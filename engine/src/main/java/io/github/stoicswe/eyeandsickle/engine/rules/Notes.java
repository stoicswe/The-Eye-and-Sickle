package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.NoteState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The notebook: create, rename, move, write, delete.
 *
 * <h2>⚠ NOTHING HERE IS READ BY ANY RULE, and that is a constraint rather than a description</h2>
 *
 * A note is text the player wrote for themselves. No gate, price, threshold or outcome may ever
 * depend on one — the moment one does, the notebook becomes a save-editable input to the rules and
 * every note is a cheat. That is why this class has no {@code tick}, no clock beyond a timestamp,
 * and returns nothing any caller could branch a game decision on.
 */
public final class Notes {

    private Notes() {}

    /**
     * The most notes and folders one character may keep.
     *
     * <p>Bounded for the same reason every other growing list here is: the save is written every
     * thirty seconds and this is one the player fills by hand. Generous enough that nobody using it
     * as a notebook will meet it.
     */
    public static final int LIMIT = 500;

    /** The longest a note or folder name may be. Long enough for a sentence, short enough for a tree. */
    public static final int NAME_LIMIT = 80;

    /** The most a single note may hold, in characters. */
    public static final int BODY_LIMIT = 200_000;

    /** How deep folders may nest. */
    public static final int DEPTH_LIMIT = 8;

    /** What an unnamed note is called, so the tree never shows a blank row. */
    public static final String UNTITLED = "untitled";

    public static Optional<NoteState> byId(GameSave save, String noteId) {
        if (save == null || noteId == null || noteId.isBlank()) {
            return Optional.empty();
        }
        return save.notes.stream().filter(n -> n.noteId.equals(noteId)).findFirst();
    }

    /**
     * What sits directly inside a folder, folders first and then notes, each A–Z.
     *
     * <p>⚠ Folders first is the convention every file manager uses, and this window is explicitly
     * shaped like one. Sorting by name rather than by edit time is deliberate: a tree that reorders
     * itself as you type moves the row you are working in out from under the pointer.
     */
    public static List<NoteState> childrenOf(GameSave save, String parentId) {
        if (save == null) {
            return List.of();
        }
        String parent = parentId == null ? "" : parentId;
        return save.notes.stream()
                .filter(n -> parent.equals(n.parentId))
                .sorted(Comparator.comparing((NoteState n) -> !n.folder)
                        .thenComparing(n -> n.name.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    /** How deep a folder is, counting the root as zero. Used to refuse a nest that is too deep. */
    public static int depthOf(GameSave save, String parentId) {
        int depth = 0;
        String at = parentId == null ? "" : parentId;
        // ⚠ Bounded by LIMIT rather than by "until root". A hand-edited save can point a folder at
        // its own descendant, and an unbounded walk on a cycle hangs the client on load.
        while (!at.isBlank() && depth <= LIMIT) {
            NoteState parent = byId(save, at).orElse(null);
            if (parent == null) {
                break;
            }
            depth++;
            at = parent.parentId;
        }
        return depth;
    }

    /**
     * Creates a note or a folder inside {@code parentId}.
     *
     * @return the new entry, or empty when the notebook is full, the nest is too deep, or the parent
     *     is not a folder
     */
    public static Optional<NoteState> create(
            GameSave save, String parentId, String name, boolean folder, Instant now) {
        if (save == null || save.notes.size() >= LIMIT) {
            return Optional.empty();
        }
        String parent = parentId == null ? "" : parentId;
        if (!parent.isBlank()) {
            NoteState into = byId(save, parent).orElse(null);
            // ⚠ Refused rather than silently re-parented to the root. A note that quietly appeared
            // somewhere other than where it was made is worse than one that was not made.
            if (into == null || !into.folder) {
                return Optional.empty();
            }
            if (depthOf(save, parent) >= DEPTH_LIMIT) {
                return Optional.empty();
            }
        }
        NoteState note = new NoteState();
        note.parentId = parent;
        note.name = clean(name);
        note.folder = folder;
        note.createdAt = now;
        note.updatedAt = now;
        save.notes.add(note);
        return Optional.of(note);
    }

    /** Renames one. Returns false if there is nothing by that id. */
    public static boolean rename(GameSave save, String noteId, String name, Instant now) {
        NoteState note = byId(save, noteId).orElse(null);
        if (note == null) {
            return false;
        }
        note.name = clean(name);
        note.updatedAt = now;
        return true;
    }

    /**
     * Replaces a note's text.
     *
     * <p>⚠ Refuses on a FOLDER rather than writing a body nothing will ever read — a folder with
     * text in it is a note in the tree that renders as a folder, which is not a state worth having.
     *
     * @return false if the id is unknown, is a folder, or the body is unchanged — the last of which
     *     is what lets the caller skip persisting the save on a keystroke that changed nothing
     */
    public static boolean write(GameSave save, String noteId, String body, Instant now) {
        NoteState note = byId(save, noteId).orElse(null);
        if (note == null || note.folder) {
            return false;
        }
        String text = body == null ? "" : body;
        if (text.length() > BODY_LIMIT) {
            text = text.substring(0, BODY_LIMIT);
        }
        if (text.equals(note.body)) {
            return false;
        }
        note.body = text;
        note.updatedAt = now;
        return true;
    }

    /**
     * Deletes a note, or a folder AND everything inside it.
     *
     * <h2>⚠ RECURSIVE, unlike {@code Repac.delete}, and the difference is what is being deleted</h2>
     *
     * {@code Repac} refuses ever to walk a tree because the filesystem it would walk is <em>generated
     * from game state</em> — there is nothing there to remove. This tree is stored, the player built
     * it, and a folder delete that orphaned its contents would leave notes alive with a parent that
     * no longer exists: invisible in the window, still in the save, still counting against
     * {@link #LIMIT}. The UI is what asks first.
     *
     * @return how many entries were removed
     */
    public static int delete(GameSave save, String noteId) {
        NoteState note = byId(save, noteId).orElse(null);
        if (note == null) {
            return 0;
        }
        List<String> doomed = new ArrayList<>();
        collect(save, note, doomed);
        save.notes.removeIf(n -> doomed.contains(n.noteId));
        return doomed.size();
    }

    private static void collect(GameSave save, NoteState note, List<String> into) {
        // ⚠ Guarded against a cycle for the same reason depthOf is: a hand-edited save can make a
        // folder its own ancestor, and a naive recursion on one never returns.
        if (into.contains(note.noteId) || into.size() > LIMIT) {
            return;
        }
        into.add(note.noteId);
        if (!note.folder) {
            return;
        }
        for (NoteState child : childrenOf(save, note.noteId)) {
            collect(save, child, into);
        }
    }

    /**
     * Moves an entry into another folder.
     *
     * <p>⚠ <b>Refuses to move a folder into its own descendant.</b> That is the one operation that
     * detaches a whole subtree from the root: the notes stay in the save, count against the limit,
     * and are unreachable from the tree — a leak with no error message. Checked by walking up from
     * the destination, which is cheap at this depth.
     */
    public static boolean move(GameSave save, String noteId, String newParentId, Instant now) {
        NoteState note = byId(save, noteId).orElse(null);
        if (note == null) {
            return false;
        }
        String parent = newParentId == null ? "" : newParentId;
        if (parent.equals(note.noteId)) {
            return false;
        }
        if (!parent.isBlank()) {
            NoteState into = byId(save, parent).orElse(null);
            if (into == null || !into.folder) {
                return false;
            }
            for (String at = parent; !at.isBlank(); ) {
                if (at.equals(note.noteId)) {
                    return false;
                }
                NoteState step = byId(save, at).orElse(null);
                if (step == null) {
                    break;
                }
                at = step.parentId;
            }
        }
        note.parentId = parent;
        note.updatedAt = now;
        return true;
    }

    /**
     * Trims a name and gives an empty one a placeholder.
     *
     * <p>⚠ A blank name renders as a blank row — a thing in the tree that cannot be pointed at or
     * described, and that a screen reader announces as nothing at all. {@link #UNTITLED} is what a
     * player gets for pressing Enter on an empty field, which is a normal thing to do.
     */
    static String clean(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return UNTITLED;
        }
        return trimmed.length() > NAME_LIMIT ? trimmed.substring(0, NAME_LIMIT) : trimmed;
    }
}
