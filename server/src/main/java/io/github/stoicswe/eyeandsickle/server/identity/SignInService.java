package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The authentication-only sign-in flow: authenticate, gate, and return the account with its characters.
 *
 * <h2>The order is the security property</h2>
 *
 * <ol>
 *   <li><strong>Authenticate</strong> via {@link AtProtoIdentityProvider} — obtain a DID the caller has
 *       proven control of. A failure here never reveals anything about the allowlist.
 *   <li><strong>Gate</strong> the authenticated DID against {@link AllowlistPolicy} — closed by default
 *       ({@code docs/architecture/03-server-and-federation.md} §1). A DID that is real but not allowed is
 *       refused with {@link SignInDeniedException}.
 *   <li><strong>Load the account</strong>: the authenticated DID is an account
 *       ({@code docs/architecture/09-player-state-portability.md} §1), and its characters are read and
 *       returned so the caller can select one or create a new one. Sign-in no longer creates or refreshes
 *       a single player — that assumption is what one-character-per-DID used to encode, and it is gone.
 * </ol>
 *
 * Authenticating before gating is deliberate: the server must not disclose whether an unauthenticated DID
 * is on its allowlist, and it cannot admit an identity it has not verified. Both fall out of doing the
 * steps in this order.
 *
 * <h2>No auto-created character, no play token (09 §1-§2)</h2>
 *
 * This step is authentication and enumeration only. It does not mint a character (creation is a separate,
 * cap-checked step — {@link CharacterService#createCharacter}) and it does not open a play session (that
 * is minted per selected character — {@link CharacterService#selectCharacter}). What it returns is the
 * character-select payload: who you are, and which characters you may play.
 *
 * <h2>Authentication only (Invariant I14)</h2>
 *
 * Nothing here reads or writes the player's PDS, and nothing trusts a client-supplied value past the
 * point the provider verified it. The DID becomes a key in this server's Postgres and the source of truth
 * for the account is here — never the client, never the PDS.
 */
@Service
public class SignInService {

    private final AtProtoIdentityProvider identityProvider;
    private final AllowlistPolicy allowlist;
    private final PlayerRepository players;

    /**
     * @param identityProvider the AT Proto authentication seam
     * @param allowlist the closed-by-default join gate
     * @param players the character table
     */
    public SignInService(
            AtProtoIdentityProvider identityProvider, AllowlistPolicy allowlist, PlayerRepository players) {
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider");
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.players = Objects.requireNonNull(players, "players");
    }

    /**
     * Runs the full sign-in flow.
     *
     * <p>No surrounding transaction is declared, and none is needed: the flow only authenticates, gates,
     * and reads the account's characters. Keeping the provider call — the only step that may reach the
     * network — outside any database transaction also means it never holds a connection, which is how a
     * connection pool starves under a slow PDS.
     *
     * @param credentials the untrusted client inputs
     * @return the authenticated account and its playable characters
     * @throws SignInException if authentication fails or no provider is available
     * @throws SignInDeniedException if the authenticated DID is not on the allowlist
     */
    public AccountSession signIn(SignInCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        // Step 1: authenticate — obtain a DID the caller has proven control of.
        ResolvedIdentity identity = identityProvider.authenticate(credentials);

        // Step 2: gate the authenticated DID. Closed by default.
        if (!allowlist.permits(identity.did())) {
            throw new SignInDeniedException(identity.did());
        }

        // Step 3: enumerate the account's playable characters. Only active ones are selectable; migrated
        // and retired shells are history, not choices (09 §6.1).
        List<Player> characters = players.findCharactersByDid(identity.did()).stream()
                .filter(character -> character.status().isPlayable())
                .toList();
        return new AccountSession(identity.did(), identity.handle(), characters);
    }
}
