package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.List;
import java.util.Objects;

/**
 * The result of a successful sign-in: an authenticated account and the characters it may play
 * ({@code docs/architecture/09-player-state-portability.md} §1).
 *
 * <h2>Sign-in yields an account, not a player</h2>
 *
 * Before character slots, sign-in create-or-refreshed a single player row and returned it. It no longer
 * does: a DID is an <em>account</em> that may hold several characters, so authentication yields the
 * account and its roster, and the client then <em>selects</em> an existing character or <em>creates</em>
 * a new one (09 §1-§2). This record is that roster — the character-select payload — carrying the proven
 * account identity and the account's current playable characters.
 *
 * <h2>No play token here</h2>
 *
 * This is deliberately not itself a bearer session. The token that authorizes play is minted per
 * character by {@link CharacterService#selectCharacter(Did, java.util.UUID)} once a character is chosen —
 * one authenticated account, one selected character, one {@link PlayerSession}. Account-level operations
 * (list, create, select) are performed against the authenticated account identity, which a request
 * filter supplies from the AT Proto sign-in; they do not need a separate account token.
 *
 * @param did the proven account identity (never {@code null}) — the DID the caller authenticated as
 * @param handle the account's current display handle at sign-in, or {@code null} if the provider
 *     resolved none; display-only and never the thing the account is keyed on (§5)
 * @param characters the account's playable ({@code active}) characters, ready to select; empty for a
 *     brand-new account that has not created one yet
 */
public record AccountSession(Did did, String handle, List<Player> characters) {

    public AccountSession {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(characters, "characters");
        characters = List.copyOf(characters);
    }
}
