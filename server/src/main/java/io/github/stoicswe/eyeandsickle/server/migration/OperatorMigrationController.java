package io.github.stoicswe.eyeandsickle.server.migration;

import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST for the trusted, cooperative full-state migration (Option B,
 * {@code docs/architecture/09-player-state-portability.md} §5) — <strong>operator-authenticated</strong>.
 *
 * <h2>Every endpoint proves operator authority first</h2>
 *
 * Option B moves a character's whole state, economy included, and is legitimate only between operators who
 * trust each other (§5). So each method here calls {@link OperatorAuthorization#requireOperator} with the
 * {@code X-Operator-Token} header <em>before</em> touching any character — a player who cannot present the
 * operator credential is refused with {@code 403}, and by default (no token configured) <em>everyone</em>
 * is, so full-state transfer is off until an operator turns it on. This is what stops a full-state bundle
 * from being player-triggered, which would violate the portable/non-portable split (§3) and Invariant I14.
 *
 * <p>These paths live under {@code /api/operator/...} rather than the player-facing {@code /api/accounts/...}
 * tree precisely so the two are never confused at the routing layer.
 */
@RestController
@RequestMapping("/api/operator/migration")
/**
 * ⚠ ABSENT IN LAN MODE, not merely refused.
 *
 * <p>{@code docs/architecture/12-lan-mode.md} §3: LAN state may never cross a server boundary, and a
 * flag consulted at each call site is a flag somebody forgets at one of them. So in LAN mode this
 * component is not created at all — the endpoint does not exist, and a caller that should not be
 * calling fails to wire at startup rather than failing to check in production.
 *
 * <p>⚠ {@code matchIfMissing = true} on purpose: an unset or misspelled {@code eyeandsickle.mode}
 * must give the mode with the security machinery ON, never the one without it.
 */
@ConditionalOnProperty(name = "eyeandsickle.mode", havingValue = "FEDERATED", matchIfMissing = true)
public class OperatorMigrationController {

    /** The header a cooperating operator presents to prove authority. */
    static final String OPERATOR_TOKEN_HEADER = "X-Operator-Token";

    private final CharacterExportService exportService;
    private final CharacterImportService importService;
    private final OperatorAuthorization operatorAuthorization;

    OperatorMigrationController(
            CharacterExportService exportService,
            CharacterImportService importService,
            OperatorAuthorization operatorAuthorization) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.importService = Objects.requireNonNull(importService, "importService");
        this.operatorAuthorization = Objects.requireNonNull(operatorAuthorization, "operatorAuthorization");
    }

    /**
     * Exports a character's full state for a cooperative move (Option B). Operator-only.
     *
     * @param characterId the character to export in full
     * @param operatorToken the operator credential from the {@code X-Operator-Token} header, if present
     * @return 200 with the trusted, full-state export; 403 if not operator-authorized, 404 if the character
     *     does not exist, 409 if it is not active
     */
    @PostMapping("/export/{characterId}")
    public TrustedCharacterExport exportFullState(
            @PathVariable UUID characterId,
            @RequestHeader(value = OPERATOR_TOKEN_HEADER, required = false) String operatorToken) {
        operatorAuthorization.requireOperator(operatorToken);
        return exportService.exportFullState(characterId);
    }

    /**
     * Retires a character here as part of an operator-driven hand-off (Option B). Operator-only.
     *
     * @param characterId the character being handed off
     * @param operatorToken the operator credential
     * @return 204; 403 if not operator-authorized, 404 if it does not exist, 409 if already terminal
     */
    @PostMapping("/commit/{characterId}")
    public ResponseEntity<Void> commit(
            @PathVariable UUID characterId,
            @RequestHeader(value = OPERATOR_TOKEN_HEADER, required = false) String operatorToken) {
        operatorAuthorization.requireOperator(operatorToken);
        exportService.commitMigrationAsOperator(characterId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Imports a trusted, full-state export at the destination (Option B). Operator-only.
     *
     * @param export the trusted, full-state export
     * @param operatorToken the operator credential
     * @return 201 with the import outcome; 403 if not operator-authorized, 413 if oversized, 409 if the home
     *     sequence is stale or the account is at its cap, 400 if a document is malformed
     */
    @PostMapping("/import")
    public ResponseEntity<MigrationImportResult> importTrusted(
            @RequestBody TrustedCharacterExport export,
            @RequestHeader(value = OPERATOR_TOKEN_HEADER, required = false) String operatorToken) {
        operatorAuthorization.requireOperator(operatorToken);
        MigrationImportResult result = importService.importTrusted(export);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
