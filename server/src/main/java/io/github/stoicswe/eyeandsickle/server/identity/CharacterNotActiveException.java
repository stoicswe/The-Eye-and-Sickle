package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.UUID;

/**
 * An operation required a live, playable character, but the character is in a terminal state
 * ({@code docs/architecture/09-player-state-portability.md} §6.1).
 *
 * <h2>The no-double-play rule, refused</h2>
 *
 * Selecting a character to play it, retiring it, or migrating it all require it to be {@code active}. A
 * migrated or retired character is a shell the old home keeps for audit — "a retired character cannot be
 * played or migrated again" (09 §6.1). This is what that refusal looks like when a caller tries anyway:
 * it prevents the same character being live in two places at once.
 *
 * <p>It maps to {@code 409 Conflict}: the character exists, but its lifecycle state forbids the request.
 */
public class CharacterNotActiveException extends RuntimeException {

    /**
     * @param characterId the character that is not active
     * @param status the terminal state it is actually in
     */
    public CharacterNotActiveException(UUID characterId, CharacterStatus status) {
        super("Character " + characterId + " is " + status.dbValue() + ", not active; it cannot be played, retired or "
                + "migrated again (no double-play, docs/architecture/09-player-state-portability.md §6.1).");
    }
}
