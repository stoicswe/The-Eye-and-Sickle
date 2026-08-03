package io.github.stoicswe.eyeandsickle.engine.fs;

import io.github.stoicswe.eyeandsickle.engine.state.RecentEntry;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Instant;
import java.util.List;

/**
 * What the operator has looked at lately.
 *
 * <h2>⚠ It is a real place, not a widget</h2>
 *
 * GNOME really does keep this — {@code ~/.local/share/recently-used.xbel} — and so does every other
 * desktop. That is why Recents here is a <b>directory you can navigate into</b> rather than a list
 * bolted to the side of one window: a player who learns that "recent files" is a file on disk has
 * learned something true and slightly uncomfortable, which is the bar {@code docs/education/00}
 * §1.2 sets. {@code ls ~/.local/share/recently-used} works in the shell for the same reason.
 *
 * <h2>⚠ Persisted, and that is a REVERSAL worth reading</h2>
 *
 * The sidebar version of this was deliberately session-local, on the grounds that a list of where
 * the player has been should not sit in a file on a machine the fiction says is watched. That
 * argument was backwards. A real machine keeps this list whether or not anyone wants it to, and the
 * uncomfortable part is the point — it lives in the <b>save</b> (the machine's own state) rather
 * than in the client profile, so it is exactly as exposed as the machine is. An intruder standing in
 * this directory can read what the owner has been doing, and that is a feature of the fiction rather
 * than a leak in it.
 *
 * <h2>Entries are references, never copies</h2>
 *
 * Each one is rendered as a {@link io.github.stoicswe.eyeandsickle.protocol.game.FsKind#SYMLINK}
 * carrying its <em>real</em> path, which is what a recents entry actually is. Two consequences fall
 * out for free and both are correct: opening one goes to the real thing, and an entry whose target
 * has gone simply points at nothing rather than pretending to still hold it.
 */
public final class Recents {

    private Recents() {}

    /** Where the list lives, relative to a home. Real: GNOME's own location. */
    public static final String DIR = ".local/share/recently-used";

    /**
     * How many are kept.
     *
     * <p>Thirty because the list has two readers with opposite interests — the owner, retracing what
     * they did, and an intruder, learning what the owner does. A longer list serves the second
     * better than the first: past a screenful the owner stops reading and the intruder does not.
     */
    public static final int CAP = 30;

    /** {@code /home/<user>/.local/share/recently-used} */
    public static String dirFor(String user) {
        return VirtualFs.home(user) + "/" + DIR;
    }

    /**
     * Records a look.
     *
     * <p>Most-recent-first, and re-looking at something <b>moves</b> it rather than adding a second
     * row — a recents list with the same path in it four times is a list that has pushed out three
     * other things to say one thing repeatedly.
     */
    public static void record(GameSave save, String path, boolean directory, Instant now) {
        String p = VirtualFs.normalise(path);
        if (save == null || p.equals("/")) {
            return;
        }
        // ⚠ The recents directory itself is never recorded. Without this, opening Recents puts
        // Recents at the top of Recents, and it never leaves.
        if (p.endsWith("/" + DIR) || p.contains("/" + DIR + "/")) {
            return;
        }
        save.recents.removeIf(entry -> p.equals(entry.path));
        RecentEntry entry = new RecentEntry();
        entry.path = p;
        entry.directory = directory;
        entry.at = now;
        save.recents.addFirst(entry);
        while (save.recents.size() > CAP) {
            save.recents.removeLast();
        }
    }

    /** Most recent first. */
    public static List<RecentEntry> entries(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.recents);
    }
}
