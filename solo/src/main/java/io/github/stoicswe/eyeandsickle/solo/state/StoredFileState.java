package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * A file the player actually has, sitting somewhere on their rig.
 *
 * <h2>Why these are stored and the rest of the filesystem is generated</h2>
 *
 * {@code VirtualFs} derives every directory on demand precisely so it cannot drift from the rules.
 * These cannot be derived: a file that arrived because the player chose to download it, to a folder
 * the player chose, is not a function of anything else. So it is the one part of the tree that is
 * real state — and it is a short list, because only four kinds of thing transfer at all.
 */
public final class StoredFileState {

    public String fileId = UUID.randomUUID().toString();

    /** The folder it is in. The name is {@link #name}; together they make the path. */
    public String directory = "";

    public String name = "";

    /** {@code payload} — as it arrived; {@code package} — after Repac; {@code document}. */
    public String kind = "payload";

    /** For an upgrade, the item type it installs. Empty for anything else. */
    public String itemType = "";

    /** The machine it came off, so the player can retrace where a thing came from. */
    public String sourceAddress = "";

    public long bytes = 0L;

    public Instant at = Instant.now();

    public String path() {
        return directory + "/" + name;
    }
}
