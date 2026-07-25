package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST for the <strong>source</strong> side of an untrusted, verifiable migration (Option C,
 * {@code docs/architecture/09-player-state-portability.md} §6).
 *
 * <h2>Player-authenticated, and account ownership is deferred to a filter</h2>
 *
 * The account is named in the path, exactly as {@code CharacterController} does. Proving the caller
 * <em>is</em> that account belongs to the identity/security filter that is expected to sit in front of
 * this controller; until it is wired these endpoints trust the path DID and must not face untrusted
 * callers. That deferral is called out here so it is a known gap, not a silent one — a DID is a
 * gossip-safe identifier (§7), so naming it leaks nothing; the missing piece is authorization.
 *
 * <h2>Export reads; commit releases</h2>
 *
 * {@code GET /bundle} builds the bundle and changes nothing — it doubles as a backup. {@code POST /commit}
 * is the one-way retire that makes no double-play real (§6.1); it is deliberately separate so taking a
 * bundle does not, by itself, give up the character.
 */
@RestController
@RequestMapping("/api/accounts/{accountDid}/characters/{characterId}/migration")
public class CharacterMigrationController {

    private final CharacterExportService exportService;

    CharacterMigrationController(CharacterExportService exportService) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
    }

    /**
     * Builds the untrusted, verifiable migration bundle for the character (Option C). Read-only.
     *
     * @param accountDid the account (from the authenticated principal in a real deployment)
     * @param characterId the character to bundle
     * @return 200 with the bundle; 404 if the character does not exist or belongs to another account, 409 if
     *     it is not active, 400 if the path DID is malformed
     */
    @GetMapping("/bundle")
    public CharacterMigrationBundle bundle(@PathVariable String accountDid, @PathVariable UUID characterId) {
        return exportService.exportForMigration(Did.of(accountDid), characterId);
    }

    /**
     * Retires the character here because it is moving to another home — the no-double-play commit (§6.1).
     *
     * @param accountDid the account
     * @param characterId the character being handed off
     * @return 204; 404 if it does not exist or belongs to another account, 409 if it is already migrated or
     *     retired
     */
    @PostMapping("/commit")
    public ResponseEntity<Void> commit(@PathVariable String accountDid, @PathVariable UUID characterId) {
        exportService.commitMigration(Did.of(accountDid), characterId);
        return ResponseEntity.noContent().build();
    }
}
