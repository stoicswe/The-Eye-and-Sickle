package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What an {@link FsEntry} is.
 *
 * <h2>Why a kind and not a MIME type</h2>
 *
 * A viewer needs to answer three questions — what icon, what does opening it do, and is it a place
 * you can go into. A MIME type answers none of them without a lookup table, and would invite the
 * client to grow an opinion about file contents it has no business having. These are the categories
 * the game actually acts on differently.
 *
 * <p>⚠ {@link #DOCUMENT} and {@link #LOOT} are <b>game</b> kinds and the others are real ones. That
 * split is deliberate and is the honesty rule {@code docs/education/00} §1.2 asks for applied to an
 * enum: a recovered fragment and an EC cache are not file types anybody will meet outside this game,
 * and pretending they are would teach something false about filesystems.
 */
public enum FsKind {

    /** A directory. The only kind you can descend into. */
    DIRECTORY,

    /** An ordinary file. */
    FILE,

    /** A symbolic link. Shown with its target, because a link whose target is hidden is a riddle. */
    SYMLINK,

    /** An executable — the bit that matters most on a machine you have just broken into. */
    EXECUTABLE,

    /** A mount point: another machine, or one of the rig's own storage tiers. */
    MOUNT,

    /** <b>Game.</b> A recovered fragment — what {@code download} pulls. */
    DOCUMENT,

    /** <b>Game.</b> An ethecoin cache sitting on a host, which is what looting takes. */
    LOOT
}
