package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Duration;
import java.util.Optional;

/**
 * Issues, resolves and revokes {@link PlayerSession}s.
 *
 * <h2>Why an interface</h2>
 *
 * The default implementation keeps sessions in memory, which is correct for the single-process,
 * allowlist-bounded home server this game is built around. A deployment that ran more than one instance
 * behind a load balancer would need a shared store (e.g. Redis or a database table) so a session issued
 * by one instance is honoured by another; that is a swap of this interface, not a rewrite of the
 * sign-in flow. The seam is here so that future is a configuration choice rather than a refactor.
 *
 * <p>Tokens are opaque handles resolved through this store rather than self-contained signed claims, so
 * revocation is immediate — {@link #invalidate(String)} ends a session now, which sign-out and Ghost
 * Protocol both require and a stateless token cannot offer.
 */
public interface PlayerSessionStore {

    /**
     * Opens a new session for a freshly authenticated player and returns it, including its token.
     *
     * @param player the authenticated player
     * @param ttl how long the session stays valid from now
     * @return the created session
     */
    PlayerSession create(Player player, Duration ttl);

    /**
     * Resolves a bearer token to its live session.
     *
     * <p>An expired session must resolve to {@link Optional#empty()} exactly as a missing one does — a
     * token that has aged out is no more valid than a token that was never issued, and the caller must
     * not have to re-check expiry the implementation already knows about. An implementation may evict
     * the expired entry as a side effect.
     *
     * @param token the presented bearer token
     * @return the session if the token is known and unexpired, otherwise empty
     */
    Optional<PlayerSession> resolve(String token);

    /**
     * Ends a session immediately, if the token names one.
     *
     * <p>Idempotent: invalidating an unknown or already-invalidated token is a no-op, because sign-out
     * must not fail just because the session already ended.
     *
     * @param token the token to revoke
     */
    void invalidate(String token);
}
