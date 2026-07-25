package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes a full migration on a single server, in the order the invariants require — the reference flow,
 * and the path a self-hosted or same-operator move actually takes
 * ({@code docs/architecture/09-player-state-portability.md} §6, §6.1).
 *
 * <h2>Why an orchestrator at all</h2>
 *
 * A cross-server migration is three independent calls: the source exports a bundle, the destination imports
 * it, and the source retires the character. When source and destination are the <em>same</em> server — a
 * self-hosted player restarting a character while keeping their verified gear, or a same-operator move —
 * those three steps run in one process, and their order is load-bearing: <strong>the source is retired
 * before the destination character goes live</strong> (§6.1, no double-play). This service pins that
 * order in one transactional method so it cannot be got wrong, and so the whole flow is testable end to
 * end. A genuine cross-server move drives the three REST endpoints separately; the directory's monotonic
 * sequence is what keeps <em>those</em> honest.
 */
@Service
public class CharacterMigrationService {

    private final CharacterExportService exportService;
    private final CharacterImportService importService;

    /**
     * @param exportService the source-side export and commit
     * @param importService the destination-side import
     */
    public CharacterMigrationService(CharacterExportService exportService, CharacterImportService importService) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.importService = Objects.requireNonNull(importService, "importService");
    }

    /**
     * Migrates a character on this server via the untrusted, verifiable path (Option C, §6), in the
     * invariant-safe order: bundle it, retire the source (no double-play), then create the fresh
     * economy-reset character and recognize its verified items.
     *
     * <p>Transactional so the whole move is atomic: if the destination import fails — a stale sequence, a
     * cap refusal — the source retire rolls back with it and the character is left exactly where it was.
     *
     * @param accountDid the authenticated account
     * @param characterId the character to migrate
     * @return the import outcome (the fresh character and what was recognized)
     * @throws io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException if the character does
     *     not exist or belongs to another account
     * @throws io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException if it is already
     *     migrated or retired
     */
    @Transactional
    public MigrationImportResult migrateVerifiableWithinServer(Did accountDid, UUID characterId) {
        CharacterMigrationBundle bundle = exportService.exportForMigration(accountDid, characterId);
        // No double-play: retire the source here BEFORE the destination character becomes live (§6.1).
        exportService.commitMigration(accountDid, characterId);
        return importService.importVerified(bundle);
    }
}
