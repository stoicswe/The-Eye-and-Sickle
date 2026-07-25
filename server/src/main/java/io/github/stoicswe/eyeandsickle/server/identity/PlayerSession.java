package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
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
 * <h2>The selected character, carried as the actor (09 §9)</h2>
 *
 * A session is opened for one <em>selected</em> character (one authenticated account, one selected
 * character, one session), so it carries that character's {@link CharacterDid} — the per-character
 * identity that items, the ledger and (future) deployed miners stamp as the actor, rather than the raw
 * account {@link #did()} which all of an account's characters share. Both are held: {@code did} is the
 * account identity for auth/allowlist decisions, {@code characterDid} is the game-state owner. The
 * constructor checks they agree ({@code characterDid.accountDid()} equals {@code did}), so a session can
 * never stamp a character that belongs to a different account.
 *
 * @param token the opaque bearer token; high-entropy and never derived from the player's identity, so
 *     it leaks nothing and guessing one is infeasible
 * @param playerId the authenticated player's server-local id (the selected character's id)
 * @param did the authenticated player's portable <em>account</em> identity
 * @param characterDid the selected character's derived per-character identity — the actor game state keys
 *     on (09 §9); its {@code accountDid()} always equals {@code did}
 * @param handle the display handle at issue time, for convenience; not authoritative
 * @param issuedAt when the session began
 * @param expiresAt when it stops being valid; a bounded lifetime limits the damage of a leaked token
 */
public record PlayerSession(
        String token,
        UUID playerId,
        Did did,
        CharacterDid characterDid,
        String handle,
        Instant issuedAt,
        Instant expiresAt) {

    public PlayerSession {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(characterDid, "characterDid");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        // A session's character must belong to its account — otherwise it would stamp game state for a
        // character on a different DID. The character DID is derived from (account DID, slot), so its
        // account component must match the session's account identity.
        if (!did.value().equals(characterDid.accountDid())) {
            throw new IllegalArgumentException("session account DID " + did + " does not match the selected "
                    + "character's account " + characterDid.accountDid());
        }
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
