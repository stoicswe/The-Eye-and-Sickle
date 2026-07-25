package io.github.stoicswe.eyeandsickle.server.discovery;

/**
 * What {@link PeerDirectoryService#accept} did with a verified self-descriptor.
 *
 * <p>Convergence of self-asserted directory data is last-writer-wins <em>on the signed sequence
 * number</em>, never on a clock ({@code docs/architecture/08-discovery-and-sync.md} §3). These
 * outcomes are that rule made observable: a strictly-higher sequence supersedes, an equal one is a
 * harmless refresh, a lower one is a stale replay — possibly a rollback attempt — and is ignored.
 */
public enum AcceptOutcome {

    /** The peer was unknown and has been added to the directory. */
    ACCEPTED_NEW,

    /** The peer was known and its descriptor advanced to a strictly higher sequence. */
    ACCEPTED_UPDATED,

    /**
     * The incoming sequence equals the stored one — the same descriptor re-announced. Liveness is
     * refreshed, but nothing about the descriptor changed.
     */
    IGNORED_DUPLICATE,

    /**
     * The incoming sequence is lower than the stored one. An old descriptor replayed as if new — at
     * best staleness, at worst a downgrade attack trying to roll the peer back to a retired transport
     * key. Refused; the stored, newer descriptor stands.
     */
    IGNORED_STALE,

    /**
     * The directory is at its configured size cap and this is a new peer. Refused rather than allowed
     * to grow storage without bound from gossip. Existing peers still update.
     */
    IGNORED_AT_CAPACITY
}
