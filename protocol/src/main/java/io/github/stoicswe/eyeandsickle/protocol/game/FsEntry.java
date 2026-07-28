package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * One entry in a machine's filesystem, as the owning side chose to describe it.
 *
 * <h2>⚠ This is a DESCRIPTION, never a path the client may act on</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.1 rule 3: nothing a player types is ever
 * concatenated into a host filesystem call. That rule does not relax because the thing on screen now
 * looks like a file manager — if anything it matters more, because a file manager is the surface
 * where a path most looks like it ought to be openable. {@link #path} is a key into an in-memory tree
 * the rules built; it names nothing on the machine the client is running on, and no code anywhere may
 * hand it to {@code java.nio}.
 *
 * <h2>Why the metadata is here and not derived</h2>
 *
 * {@link #mode}, {@link #owner} and {@link #sizeBytes} are supplied by whoever owns the machine
 * rather than computed by the viewer, for the same reason every other readout in this client works
 * that way: a client that invented a permission bit would be inventing the answer to "may I read
 * this", which is a rules question (I14). A viewer that cannot read something is told so; it does not
 * work it out.
 *
 * @param name the entry's own name, with no path in it
 * @param path the absolute path within its machine — a key, see above
 * @param kind what it is; drives the icon and what a double-click does
 * @param sizeBytes apparent size. Zero for a directory, which is what the owning side reports rather
 *     than a claim that a directory is empty
 * @param mode the permission string as {@code ls -l} writes it, e.g. {@code drwxr-xr-x}
 * @param owner user name
 * @param group group name
 * @param modifiedAt last modification
 * @param readable whether THIS viewer may read it — the rules' verdict, rendered as received
 */
public record FsEntry(
        String name,
        String path,
        FsKind kind,
        long sizeBytes,
        String mode,
        String owner,
        String group,
        Instant modifiedAt,
        boolean readable) {

    public boolean directory() {
        return kind == FsKind.DIRECTORY;
    }
}
