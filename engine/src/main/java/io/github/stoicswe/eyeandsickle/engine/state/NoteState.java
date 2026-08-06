package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;
import java.util.UUID;

/**
 * One note, or one folder of them.
 *
 * <h2>⚠ ONE type for both, and the alternative was worse</h2>
 *
 * A separate {@code FolderState} would need its own id space, its own rename, its own delete and its
 * own parent pointer — four things to keep in step with the four here, and a tree walk that has to
 * merge two lists in the right order. One record with a {@link #folder} flag makes "what is inside
 * this" a single filter on {@link #parentId}, and makes moving a note into a folder the same
 * operation as moving a folder into a folder. The cost is that {@link #body} is meaningless on a
 * folder, which is one dead field rather than a second hierarchy.
 *
 * <h2>Why notes live in the SAVE and not the profile</h2>
 *
 * Appearance is per character and accessibility is machine-wide ({@code CLAUDE.md}); notes are
 * neither. They are what <em>this character</em> found out — lore off a machine they breached, an
 * address worth remembering, a theory about who is informing on whom. Putting them in the profile
 * would pool one character's discoveries into another's, which spoils the thing the window exists
 * for. The consequence is honest and worth knowing: deleting a character deletes their notes.
 *
 * <p>⚠ <b>Nothing in here is read by any rule.</b> A note is text the player wrote for themselves;
 * no gate, price, threshold or outcome may ever depend on one. The moment something does, this stops
 * being a notebook and becomes a save-editable input to the rules.
 */
public final class NoteState {

    public String noteId = UUID.randomUUID().toString();

    /**
     * The folder this sits in, or {@code ""} for the root.
     *
     * <p>⚠ A parent POINTER rather than a path string. A path has to be rewritten on every
     * descendant when a folder is renamed — an operation that is O(tree) and silently wrong if it
     * misses one — where a pointer makes rename a single field write and makes the name purely a
     * display concern.
     */
    public String parentId = "";

    public String name = "";

    /** Markdown source. Empty and unused on a folder. */
    public String body = "";

    /** Whether this is a folder rather than a note. See the class note on why there is one type. */
    public boolean folder = false;

    public Instant createdAt = Instant.EPOCH;

    /** Last edit. Drives the list's ordering and the "saved" line in the editor. */
    public Instant updatedAt = Instant.EPOCH;
}
