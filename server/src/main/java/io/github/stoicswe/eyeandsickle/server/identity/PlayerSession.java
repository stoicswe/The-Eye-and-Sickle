package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A proof-of-identity session: the server's record that a specific player authenticated, and the
 * bearer token that stands in for that proof on subsequent requests.
 *
 * <h2>Authn, not authz ({@code docs/architecture/02-identity-and-auth.md} §5)</h2>
 *
 * The AT Proto sign-in is authentication-only, and this session inherits that scope exactly: it says
 * "this caller is this DID", nothing about what they may do on their social account. It carries the
 * player's server-local id and DID so an authenticated request can be attributed without another
 * lookup, and it is the object a request's principal resolves to once the bearer filter has validated
 * the token.
 *
 * <h2>Why a server-side session and not a self-contained token</h2>
 *
 * The token is an opaque handle looked up in a {@link PlayerSessionStore}, not a signed claim the
 * client carries. That makes a session <em>revocable</em>: sign-out and Ghost Protocol
 * ({@code docs/design/08}) must be able to end a session immediately, and a stateless JWT cannot be
 * un-issued. On an allowlist-bounded home server the lookup cost is trivial and the revocability is
 * worth far more.
 *
 * @param token the opaque bearer token; high-entropy and never derived from the player's identity, so
 *     it leaks nothing and guessing one is infeasible
 * @param playerId the authenticated player's server-local id
 * @param did the authenticated player's portable identity
 * @param handle the display handle at issue time, for convenience; not authoritative
 * @param issuedAt when the session began
 * @param expiresAt when it stops being valid; a bounded lifetime limits the damage of a leaked token
 */
public record PlayerSession(String token, UUID playerId, Did did, String handle, Instant issuedAt, Instant expiresAt) {

    public PlayerSession {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (expiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("session expiresAt " + expiresAt + " precedes issuedAt " + issuedAt);
        }
    }

    /**
     * @param now the reference instant
     * @return whether the session has expired as of {@code now}; expiry is inclusive of the boundary so
     *     a session is not valid <em>at</em> its expiry instant
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }
}
