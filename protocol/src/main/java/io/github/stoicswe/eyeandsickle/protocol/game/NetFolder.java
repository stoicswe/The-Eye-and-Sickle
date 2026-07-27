package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;

/**
 * One folder in the player's filing of the machines they have found.
 *
 * <h2>A read model of an annotation, not of the world</h2>
 *
 * Every other network type in this package describes something the game decided: {@link Sighting} is
 * what a sweep established, {@link NetLink} is an edge that exists, {@link ServerRef} is a place. This
 * one describes something the <em>player</em> decided, and nothing in the rules reads it back. That
 * makes it the one record here a client could not forge anything with — there is no gate on a folder,
 * no cost to one, and no quantity of them that unlocks anything — which is why it is allowed to be as
 * free-form as it is.
 *
 * <h2>Flat, with a depth, because the renderers are character-cell surfaces</h2>
 *
 * A nested {@code children} list would be the obvious shape and is the wrong one for the two things
 * that draw this. The map window indents rows by hand and the terminal prints a tree with box glyphs;
 * both want a pre-ordered walk, and if each did its own traversal the two would eventually order
 * siblings differently. So the producer walks once, parents before children, and hands over a list
 * whose order <em>is</em> the drawing order. {@link #depth} is how far to indent and nothing else.
 *
 * @param folderId stable id — what an intent names, never the path, because a path changes on rename
 * @param parentId the folder above, or {@code ""} at top level
 * @param name what the player called it
 * @param path {@code /a/b/c} — for display and for the terminal's argument form, never for identity
 * @param depth 0 at top level; how far a renderer indents this row
 * @param addresses the machines filed directly in this folder, ascending; never those in sub-folders
 * @param subtreeCount machines filed here <em>or anywhere below</em> — the count a collapsed row shows
 */
public record NetFolder(
        String folderId,
        String parentId,
        String name,
        String path,
        int depth,
        List<String> addresses,
        int subtreeCount) {

    public NetFolder {
        folderId = folderId == null ? "" : folderId;
        parentId = parentId == null ? "" : parentId;
        name = name == null ? "" : name;
        path = path == null ? "" : path;
        depth = Math.max(0, depth);
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
        subtreeCount = Math.max(0, subtreeCount);
    }

    /** Whether this folder holds nothing at all, at any depth — what an empty-state row keys on. */
    public boolean isEmpty() {
        return subtreeCount == 0;
    }

    /**
     * The same folder with its subtree total filled in.
     *
     * <p>Exists because the total is only knowable after the children have been walked, and the
     * alternative — a mutable builder, or a second pass that rebuilds every record — costs more than
     * one wither on a record with seven components.
     */
    public NetFolder withSubtreeCount(int total) {
        return new NetFolder(folderId, parentId, name, path, depth, addresses, total);
    }
}
