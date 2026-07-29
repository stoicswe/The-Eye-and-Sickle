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

    /**
     * The ledger entry whose confirmation releases this package, or empty when nothing holds it.
     *
     * <h2>⚠ The LOCK IS DERIVED from this id, never stored as a flag</h2>
     *
     * A boolean would be a second copy of a fact the chain already owns, and the two would part
     * company the first time a block landed while nothing was looking at the file — which, since
     * confirmation happens on a tick and the file manager may be closed, is most of the time. Asking
     * the ledger row for its {@code blockNumber} cannot go stale. See {@code Repac.locked}.
     *
     * <p>Set only on a bought package. A stolen one is not waiting on anybody's payment: you took it,
     * and the chain has no opinion about that.
     */
    public String lockedByEntryId = "";

    /**
     * ⚠ THE TAMPER SEAM. Empty means the payload is exactly what its manifest declares.
     *
     * <p>A package's expected digest is a function of what it claims to be; its actual digest is a
     * function of the bytes. Today those are the same computation and every package verifies, because
     * in single player there is exactly one party and nobody to tamper — a vendor sells what it says
     * it sells, and a package stolen off a machine is whatever was really sitting there.
     *
     * <p>Any non-empty value here perturbs the actual digest and the two stop agreeing. It exists so
     * that the <b>player-to-player market</b> in online play has somewhere to put a substituted
     * payload without introducing a verification step at the same moment as the threat — by then the
     * habit of reading the two digests is already formed. See {@code PackageManifest}.
     */
    public String payloadSalt = "";

    public String path() {
        return directory + "/" + name;
    }
}
