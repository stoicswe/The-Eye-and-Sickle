package io.github.stoicswe.eyeandsickle.server.directory;

/**
 * What {@link CharacterDirectoryService#accept} did with a verified home binding.
 *
 * <p>Mirrors the discovery slice's {@code AcceptOutcome}, with one addition the character directory needs
 * that a self-descriptor does not: {@link #IGNORED_CONFLICT}. A server descriptor can only be signed by
 * its own owner, so an equal-sequence re-announcement is always a harmless refresh. A home binding can be
 * signed by <em>different</em> home servers (a character genuinely moves home), so two different bindings
 * at the same sequence for one {@code (account, slot)} are a fork — evidence something is wrong — and are
 * refused rather than overwritten.
 */
public enum HomeAcceptOutcome {

    /** A binding for a previously-unknown {@code (account, slot)} was inserted. */
    ACCEPTED_NEW,

    /** A known binding was advanced to a strictly-higher, signed sequence. */
    ACCEPTED_UPDATED,

    /** The offered binding was identical (same sequence, same signature) to the stored one; last-seen refreshed. */
    IGNORED_DUPLICATE,

    /**
     * The offered binding had the same sequence as the stored one but different content (a different
     * signature) — a fork. The stored binding stands; the offered one is refused, not merged.
     */
    IGNORED_CONFLICT,

    /** The offered binding had a lower sequence than the stored one — a stale record, possibly a rollback attempt. */
    IGNORED_STALE,

    /** The directory is already at its configured capacity; a new binding is refused (existing ones still advance). */
    IGNORED_AT_CAPACITY
}
