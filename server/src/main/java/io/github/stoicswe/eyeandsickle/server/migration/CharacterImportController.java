package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST for the <strong>destination</strong> side of an untrusted, verifiable migration (Option C,
 * {@code docs/architecture/09-player-state-portability.md} §6): the endpoint a home server exposes to
 * accept an incoming character.
 *
 * <h2>Why accepting from an untrusted caller is safe here</h2>
 *
 * The safety is not in who calls this — it is in what the service does. Every item chain is re-verified
 * with the full provenance verifier before anything is recognized, the character is minted fresh with a
 * reset economy, and the home binding only advances (§6.1). A caller cannot smuggle economy or forge an
 * item; the worst they can do is present a bundle whose items all fail verification, and they get a
 * gear-less fresh character for their trouble — one that still counts against the account's cap.
 *
 * <p><strong>Deferred:</strong> authenticating that the caller controls the bundle's account DID — the
 * immigrating account — is the same identity-filter deferral the other character endpoints carry, and the
 * cap is the only backstop until it is wired.
 */
@RestController
@RequestMapping("/api/migration/import")
public class CharacterImportController {

    private final CharacterImportService importService;

    CharacterImportController(CharacterImportService importService) {
        this.importService = Objects.requireNonNull(importService, "importService");
    }

    /**
     * Accepts an untrusted migration bundle, re-verifies it, and mints the fresh character.
     *
     * @param bundle the untrusted, verifiable bundle
     * @return 201 with the import outcome; 413 if the bundle is oversized, 409 if the home sequence is stale
     *     or the account is at its cap, 400 if a document is malformed or the account DID is invalid
     */
    @PostMapping
    public ResponseEntity<MigrationImportResult> importVerified(@RequestBody CharacterMigrationBundle bundle) {
        MigrationImportResult result = importService.importVerified(bundle);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(result);
    }
}
