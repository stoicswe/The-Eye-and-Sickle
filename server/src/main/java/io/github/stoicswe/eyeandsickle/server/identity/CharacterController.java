package io.github.stoicswe.eyeandsickle.server.identity;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST over an account's characters: list, create (cap-checked), select (open a play session), retire.
 *
 * <h2>The client renders this; it never decides it (Invariant I14)</h2>
 *
 * Every endpoint is a thin edge over {@link CharacterService}, which is authoritative. The controller's
 * only jobs are to turn a URL and body into a service call and a service result — or a typed error into a
 * status code (see {@link IdentityExceptionHandler}). No rule lives here: not the slot cap, not slot
 * assignment, not the one-way status transitions.
 *
 * <h2>What this controller does not do — account ownership</h2>
 *
 * The account is named in the path ({@code accountDid}). Proving the caller <em>is</em> that account —
 * that the bearer of the request authenticated as this DID — belongs to the identity/security filter
 * ({@code docs/architecture/02-identity-and-auth.md}) and is expected to sit in front of this controller,
 * exactly as rig-ownership authorization sits in front of the compute controller. Until that filter is
 * wired, these endpoints trust the path DID and must not face untrusted callers. That deferral is called
 * out here so it is a known gap, not a silent one. A DID is a public, gossip-safe identifier (09 §7), so
 * naming it in the path leaks nothing; the missing piece is authorization, not confidentiality.
 *
 * <h2>Select mints the play token</h2>
 *
 * Sign-in yields the account and its roster (09 §1); selecting a character is where a bearer
 * {@link PlayerSession} for <em>that</em> character is minted. One authenticated account, one selected
 * character, one session.
 */
@RestController
@RequestMapping("/api/accounts/{accountDid}/characters")
public class CharacterController {

    private final CharacterService characters;

    CharacterController(CharacterService characters) {
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    /**
     * The account's playable characters — the character-select roster.
     *
     * @param accountDid the account (from the authenticated principal in a real deployment)
     * @return the account's active characters as select-views (200); 400 if the path DID is malformed
     */
    @GetMapping
    public List<CharacterSummary> list(@PathVariable String accountDid) {
        return characters.listCharacters(Did.of(accountDid)).stream()
                .map(CharacterSummary::from)
                .toList();
    }

    /**
     * Creates a new character for the account, refusing to exceed the slot cap.
     *
     * @param accountDid the account
     * @param request the optional handle to give the character; may be absent
     * @return 201 with the created character's select-view and a {@code Location} pointing at it; 409 if
     *     the account is already at its cap, 400 if the path DID is malformed
     */
    @PostMapping
    public ResponseEntity<CharacterSummary> create(
            @PathVariable String accountDid, @RequestBody(required = false) CreateCharacterRequest request) {
        String handle = request == null ? null : request.handle();
        Player created = characters.createCharacter(Did.of(accountDid), handle);
        URI location = URI.create("/api/accounts/" + accountDid + "/characters/" + created.playerId());
        return ResponseEntity.created(location).body(CharacterSummary.from(created));
    }

    /**
     * Selects a character to play, opening a bearer session bound to it.
     *
     * @param accountDid the account
     * @param characterId the character to play
     * @return 200 with the opened {@link PlayerSession} (its token authorizes play); 404 if the character
     *     does not exist or belongs to another account, 409 if it is migrated or retired
     */
    @PostMapping("/{characterId}/session")
    public PlayerSession select(@PathVariable String accountDid, @PathVariable UUID characterId) {
        return characters.selectCharacter(Did.of(accountDid), characterId);
    }

    /**
     * Retires one of the account's characters — a one-way transition to {@code retired}.
     *
     * @param accountDid the account
     * @param characterId the character to retire
     * @return 204; 404 if the character does not exist or belongs to another account, 409 if it is already
     *     migrated or retired
     */
    @DeleteMapping("/{characterId}")
    public ResponseEntity<Void> retire(@PathVariable String accountDid, @PathVariable UUID characterId) {
        characters.retireCharacter(Did.of(accountDid), characterId);
        return ResponseEntity.noContent().build();
    }
}
