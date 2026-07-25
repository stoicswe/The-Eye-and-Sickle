package io.github.stoicswe.eyeandsickle.server.migration;

/**
 * A migration bundle exceeded a configured size bound before verification began
 * ({@code docs/architecture/09-player-state-portability.md} — REST guidance "bound bundle size").
 *
 * <h2>A denial-of-service bound, not a game rule</h2>
 *
 * Verifying provenance chains is real work, and an untrusted courier could hand a destination an
 * arbitrarily large bundle — thousands of items, or one item with a pathologically long chain — purely
 * to burn CPU. Rejecting an oversized bundle <em>before</em> the first signature check is the cheap
 * defence. The limits ({@link MigrationProperties}) are operational knobs a self-hoster tunes, never
 * values a player can gain from (Invariant I14).
 *
 * <p>Maps to {@code 413 Payload Too Large}.
 */
public class MigrationBundleTooLargeException extends RuntimeException {

    /**
     * @param detail which bound was exceeded and by how much
     */
    public MigrationBundleTooLargeException(String detail) {
        super(detail);
    }
}
