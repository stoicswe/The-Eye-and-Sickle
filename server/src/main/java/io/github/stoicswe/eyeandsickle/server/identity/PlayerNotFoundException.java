package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.UUID;

/**
 * No player exists for the given identity.
 *
 * <p>Distinct from an authorization failure on purpose: "there is no such player" is a {@code 404},
 * not a {@code 403}. It is thrown by lookups that a caller expects to succeed — reading the profile of
 * a session whose player row has since been deleted, or a faction transition against a missing player —
 * so the caller gets a specific, actionable failure instead of a {@link NullPointerException} with no
 * identity in it.
 */
public class PlayerNotFoundException extends RuntimeException {

    /**
     * @param playerId the server-local id that resolved to no row
     */
    public PlayerNotFoundException(UUID playerId) {
        super("No player with id " + playerId);
    }

    /**
     * @param did the DID that resolved to no row
     */
    public PlayerNotFoundException(Did did) {
        super("No player with DID " + did);
    }
}
