package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The character lifecycle for one account: list, create (cap-checked), select, and the one-way status
 * transitions ({@code docs/architecture/09-player-state-portability.md} §1-§2, §6.1).
 *
 * <h2>What this owns that the repository does not</h2>
 *
 * {@link PlayerRepository} is primitives; the policy lives here:
 *
 * <ul>
 *   <li><strong>The cap.</strong> Before creating a DID-bound character it consults
 *       {@link RecognizedCharacterCount} — how many characters the federation already recognizes for the
 *       account — and refuses the {@code (max+1)}-th (09 §2). The count seam is what a directory-aware
 *       deployment widens from "this server" to "the whole federation"; the default counts local active
 *       rows.
 *   <li><strong>Slot assignment.</strong> It picks the lowest free slot in {@code 1..}{@link
 *       Player#MAX_SLOT}, skipping any slot still held by a migrated or retired shell (those keep their
 *       slot number — 09 §6.1 — and {@code uq_players_did_slot} would reject a reuse).
 *   <li><strong>The one-way rule.</strong> A status transition only ever goes {@code active ->
 *       migrated/retired} and never back (09 §6.1, no double-play). It reads the character, checks it is
 *       active, then applies the change under the version it read.
 *   <li><strong>Local exemption.</strong> A local, DID-less character is created with no cap and no slot
 *       (09 §1). It is outside this whole system.
 * </ul>
 *
 * <h2>The cap is soft (09 §2, Invariant I15)</h2>
 *
 * There is no global account table and no single arbiter, so two servers each seeing only two characters
 * could race a third-and-fourth into existence (open question Q-cap-race). {@code uq_players_did_slot} is
 * the structural backstop <em>within</em> a server; across the federation the directory converges to at
 * most {@code maxCharacters} recognized, and the excess is not recognized. This service enforces the
 * strongest guarantee a no-central-authority federation can make, which is exactly the guarantee the rest
 * of the trust model relies on — not a hard, central one, which I15 forbids.
 */
@Service
public class CharacterService {

    private final PlayerRepository players;
    private final CharacterProperties characterProperties;
    private final RecognizedCharacterCount recognizedCharacterCount;
    private final PlayerSessionStore sessions;
    private final IdentityProperties identityProperties;
    private final Clock clock;

    /**
     * @param players the character table
     * @param characterProperties the slot cap ({@code maxCharacters})
     * @param recognizedCharacterCount the federation-wide recognized-character count seam
     * @param sessions the session store a selected character's play session is opened in
     * @param identityProperties supplies the session lifetime
     * @param clock the source of creation and selection instants
     */
    public CharacterService(
            PlayerRepository players,
            CharacterProperties characterProperties,
            RecognizedCharacterCount recognizedCharacterCount,
            PlayerSessionStore sessions,
            IdentityProperties identityProperties,
            Clock clock) {
        this.players = Objects.requireNonNull(players, "players");
        this.characterProperties = Objects.requireNonNull(characterProperties, "characterProperties");
        this.recognizedCharacterCount = Objects.requireNonNull(recognizedCharacterCount, "recognizedCharacterCount");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.identityProperties = Objects.requireNonNull(identityProperties, "identityProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------ read

    /**
     * The account's playable characters — the character-select roster.
     *
     * <p>Only {@code active} characters: migrated and retired shells are history, not choices. An account
     * with none yet gets an empty list, which is the signal to offer character creation.
     *
     * @param accountDid the authenticated account
     * @return the account's active characters, ordered by slot
     */
    public List<Player> listCharacters(Did accountDid) {
        Objects.requireNonNull(accountDid, "accountDid");
        return players.findCharactersByDid(accountDid).stream()
                .filter(c -> c.status().isPlayable())
                .toList();
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates a new DID-bound character for the account, refusing to exceed the cap.
     *
     * <p>Transactional so the cap check and the insert are one unit. The cap is checked against the
     * federation-recognized count, then a free slot is assigned and the character inserted. Local play
     * does not come through here — see {@link #createLocalCharacter(String)}.
     *
     * @param accountDid the authenticated account
     * @param handle the account's current display handle, or {@code null}
     * @return the created character
     * @throws CharacterSlotExceededException if the account already holds {@code maxCharacters} recognized
     *     characters
     * @throws IllegalStateException if every slot in {@code 1..}{@link Player#MAX_SLOT} is already held (a
     *     retired/migrated-shell exhaustion; open question Q-retire-window governs reaping them)
     */
    @Transactional
    public Player createCharacter(Did accountDid, String handle) {
        Objects.requireNonNull(accountDid, "accountDid");
        int recognized = recognizedCharacterCount.countRecognized(accountDid);
        int max = characterProperties.maxCharacters();
        if (recognized >= max) {
            throw new CharacterSlotExceededException(accountDid, recognized, max);
        }
        int slot = nextFreeSlot(players.findCharactersByDid(accountDid));
        return players.createCharacter(accountDid, handle, slot, clock.instant());
    }

    /**
     * Creates a local, DID-less character — offline single-player, exempt from the cap and the directory
     * (09 §1).
     *
     * @param handle the display handle, or {@code null}
     * @return the created local character
     */
    public Player createLocalCharacter(String handle) {
        return players.createLocalCharacter(handle, clock.instant());
    }

    // ------------------------------------------------------------------ select / session

    /**
     * Opens a play session bound to one of the account's characters.
     *
     * <p>The character must belong to the account and be {@code active}: selecting a migrated or retired
     * character is refused, which is the no-double-play rule at the point of entry (09 §6.1). The returned
     * {@link PlayerSession} carries the bearer token that authorizes play as that character.
     *
     * @param accountDid the authenticated account
     * @param characterId the character to play
     * @return a session bound to the selected character
     * @throws PlayerNotFoundException if the character does not exist or belongs to another account
     * @throws CharacterNotActiveException if the character is migrated or retired
     */
    public PlayerSession selectCharacter(Did accountDid, UUID characterId) {
        Player character = requireOwned(accountDid, characterId);
        if (!character.status().isPlayable()) {
            throw new CharacterNotActiveException(characterId, character.status());
        }
        return sessions.create(character, identityProperties.sessionTtl());
    }

    /**
     * Ends a play session immediately.
     *
     * @param token the bearer token to invalidate; unknown tokens are a harmless no-op
     */
    public void endSession(String token) {
        sessions.invalidate(token);
    }

    // ------------------------------------------------------------------ status transitions (one-way)

    /**
     * Retires one of the account's characters — a one-way transition to {@code retired} (09 §6.1).
     *
     * @param accountDid the authenticated account
     * @param characterId the character to retire
     * @throws PlayerNotFoundException if the character does not exist or belongs to another account
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    public void retireCharacter(Did accountDid, UUID characterId) {
        transition(requireOwned(accountDid, characterId), CharacterStatus.RETIRED);
    }

    /**
     * Marks a character migrated — a one-way transition to {@code migrated} (09 §5, §6).
     *
     * <p>This is the hook the migration flow calls when a character's authoritative home moves away: the
     * old home retires the character to {@code migrated} <em>before</em> it becomes live at the new home,
     * so it is never live in two places (09 §6.1). It is keyed by character id (server-internal), not by
     * account, because migration is a server operation, not a player-facing account action.
     *
     * @param characterId the character whose home is moving away
     * @throws PlayerNotFoundException if the character does not exist
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    public void markMigrated(UUID characterId) {
        transition(players.requireCharacter(characterId), CharacterStatus.MIGRATED);
    }

    /**
     * Marks a character retired by id — the server-internal counterpart to {@link #retireCharacter}.
     *
     * @param characterId the character to retire
     * @throws PlayerNotFoundException if the character does not exist
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    public void markRetired(UUID characterId) {
        transition(players.requireCharacter(characterId), CharacterStatus.RETIRED);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Applies a one-way status transition. The source must be {@code active}: a transition out of a
     * terminal state is refused, which is what makes the lifecycle one-way (09 §6.1). The target must
     * itself be terminal — {@code active} is a start state, never a destination.
     */
    private void transition(Player character, CharacterStatus target) {
        if (!target.isTerminal()) {
            throw new IllegalArgumentException(
                    "A status transition targets a terminal state (migrated or retired), not " + target);
        }
        if (!character.status().isPlayable()) {
            throw new CharacterNotActiveException(character.playerId(), character.status());
        }
        players.updateStatus(character.playerId(), target, character.rowVersion());
    }

    /**
     * Fetches a character and checks it belongs to the account. An account may only act on its own
     * characters; a mismatch is reported as not-found rather than forbidden, so the endpoint discloses
     * nothing about characters on other accounts.
     */
    private Player requireOwned(Did accountDid, UUID characterId) {
        Objects.requireNonNull(accountDid, "accountDid");
        Objects.requireNonNull(characterId, "characterId");
        Player character = players.requireCharacter(characterId);
        if (!accountDid.equals(character.did())) {
            throw new PlayerNotFoundException(characterId);
        }
        return character;
    }

    /**
     * The lowest slot number in {@code 1..}{@link Player#MAX_SLOT} not already held by any of the
     * account's characters — active or terminal. Terminal shells keep their slot number (09 §6.1) and
     * {@code uq_players_did_slot} still holds it, so a free slot must dodge them too.
     */
    private static int nextFreeSlot(List<Player> existing) {
        Set<Integer> used =
                existing.stream().map(Player::slot).filter(Objects::nonNull).collect(Collectors.toSet());
        for (int slot = Player.MIN_SLOT; slot <= Player.MAX_SLOT; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        // The cap (maxCharacters <= MAX_SLOT) keeps active characters well under this, but migrated and
        // retired shells accumulate and are not yet reaped (open question Q-retire-window). When they
        // fill every slot, creation must fail loudly rather than pick an out-of-range slot the schema
        // would reject anyway.
        throw new IllegalStateException("No free character slot in " + Player.MIN_SLOT + ".." + Player.MAX_SLOT
                + "; every slot is held by a live or retained-shell character. Reaping migrated/retired shells is "
                + "open question Q-retire-window (docs/architecture/09-player-state-portability.md §9).");
    }
}
