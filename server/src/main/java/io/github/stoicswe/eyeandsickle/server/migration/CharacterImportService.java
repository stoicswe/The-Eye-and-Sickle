package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.migration.MigrationItemImporter.ItemImportOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The <strong>destination</strong> side of a migration: accepting a bundle, re-verifying it, and minting a
 * fresh character to hold what verifies ({@code docs/architecture/09-player-state-portability.md} §6, §6.1).
 *
 * <h2>The order is the security model</h2>
 *
 * Each import is one transaction, and the steps run in an order chosen so the invariants hold even for the
 * default in-memory directory:
 *
 * <ol>
 *   <li><strong>Bound the bundle</strong> before doing any verification work — an oversized bundle from an
 *       untrusted courier is refused ({@link MigrationBundleTooLargeException}) rather than allowed to burn
 *       CPU.
 *   <li><strong>Pre-check the home sequence</strong> against the directory: a bundle presenting a stale
 *       sequence is a rollback/replay and is refused ({@link StaleHomeSequenceException}) with no side
 *       effects — nothing is created.
 *   <li><strong>Mint a fresh character</strong> through the cap-checked identity path, which is where the
 *       economy <em>resets</em> (base ethecoin, zero heat, no faction — §6) and where a migration that
 *       would exceed the account's slot cap is refused.
 *   <li><strong>Re-verify and recognize each item</strong>; a chain that fails is dropped, never stored as
 *       if verified (§6.1). Provenance is checked afresh here, never trusted from the bundle.
 *   <li><strong>Advance the home binding</strong> to this server as the last step. On success everything
 *       commits together; if the authoritative advance loses a concurrent race it throws, the transaction
 *       rolls back, and the fresh character and its items vanish with it.
 * </ol>
 *
 * <p>A malformed envelope (not even well-formed JSON) aborts the whole import as a bad request — distinct
 * from a well-formed chain that simply fails verification, which is dropped while the character still
 * lands. The two untrusted-vs-trusted entry points differ only in step 3's economy handling.
 */
@Service
public class CharacterImportService {

    private final MigrationCharacters characters;
    private final MigrationItemImporter itemImporter;
    private final CharacterHomeDirectory directory;
    private final MigrationProperties properties;

    /**
     * @param characters the identity-core seam (create fresh, restore standing)
     * @param itemImporter the verify-and-recognize seam over provenance ingress
     * @param directory the character-home directory seam (monotonic advance)
     * @param properties the bundle size bounds
     */
    public CharacterImportService(
            MigrationCharacters characters,
            MigrationItemImporter itemImporter,
            CharacterHomeDirectory directory,
            MigrationProperties properties) {
        this.characters = Objects.requireNonNull(characters, "characters");
        this.itemImporter = Objects.requireNonNull(itemImporter, "itemImporter");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    // ------------------------------------------------------------------ Option C — untrusted, verifiable

    /**
     * Imports an untrusted, verifiable bundle (Option C, §6): a fresh character with a reset economy, and
     * only the items whose chains re-verify here.
     *
     * @param bundle the untrusted migration bundle
     * @return the outcome — the new character, the advanced home sequence, and which items were recognized
     * @throws MigrationBundleTooLargeException if the bundle exceeds a configured size bound
     * @throws StaleHomeSequenceException if the bundle's home sequence does not advance the directory
     * @throws io.github.stoicswe.eyeandsickle.server.identity.CharacterSlotExceededException if the account
     *     is at its recognized-character cap
     * @throws IllegalArgumentException if a bundle document is not a well-formed provenance envelope, or the
     *     account DID is malformed
     */
    @Transactional
    public MigrationImportResult importVerified(CharacterMigrationBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        Did accountDid = Did.of(bundle.accountDid());
        CharacterRef source = bundle.sourceCharacter();

        enforceSize(bundle.itemChains());
        preCheckSequence(accountDid, source, bundle.homeSequence());

        Player fresh = characters.createFreshCharacter(accountDid, null);
        Recognition recognition = recognizeAll(bundle.itemChains());
        long newSequence = directory.advanceHomeToLocal(accountDid, source, fresh.playerId(), bundle.homeSequence());

        return new MigrationImportResult(
                fresh.playerId(), newSequence, recognition.recognized(), recognition.rejected(), true);
    }

    // ------------------------------------------------------------------ Option B — trusted, cooperative

    /**
     * Imports a trusted, full-state export (Option B, §5): a fresh character onto which the identity-owned
     * standing (committed faction and personal heat) is restored, plus the re-verified items. The REST layer
     * proves operator authority before this runs; it must never be reachable from a player-facing path.
     *
     * <p>The ethecoin <em>balance</em> the export carries is <strong>not</strong> written here — applying a
     * balance is a ledger transaction the economy slice owns (Invariant I1), and faction reputation is a
     * separate table; both are documented seams the trusted path leaves to their owning slices. The
     * lossless full-state path is the operational PostgreSQL restore ({@code deploy/BACKUP.md}). Items are
     * still re-verified even on the trusted path — provenance is cheap to check and there is no reason to
     * skip it.
     *
     * @param export the trusted, full-state export
     * @return the outcome — the new character, the advanced home sequence, and which items were recognized
     * @throws MigrationBundleTooLargeException if the export exceeds a configured size bound
     * @throws StaleHomeSequenceException if the export's home sequence does not advance the directory
     * @throws IllegalArgumentException if a document is not a well-formed envelope, or the account DID is
     *     malformed
     */
    @Transactional
    public MigrationImportResult importTrusted(TrustedCharacterExport export) {
        Objects.requireNonNull(export, "export");
        Did accountDid = Did.of(export.accountDid());
        CharacterRef source = export.sourceCharacter();

        enforceSize(export.itemChains());
        preCheckSequence(accountDid, source, export.homeSequence());

        Player fresh = characters.createFreshCharacter(accountDid, export.handle());
        // Trusted path only: the standing carries because both operators cooperate (§5).
        characters.restoreStanding(fresh.playerId(), export.faction(), export.personalHeat(), fresh.rowVersion());
        Recognition recognition = recognizeAll(export.itemChains());
        long newSequence = directory.advanceHomeToLocal(accountDid, source, fresh.playerId(), export.homeSequence());

        return new MigrationImportResult(
                fresh.playerId(), newSequence, recognition.recognized(), recognition.rejected(), false);
    }

    // ------------------------------------------------------------------ internals

    /** The running tally of what verified and what did not. */
    private record Recognition(List<UUID> recognized, List<UUID> rejected) {}

    /**
     * Re-verifies and recognizes every item chain. A recognized item joins the fresh character; an
     * unrecognized one is dropped. A chain whose records name a different item than the bundle's manifest
     * claimed is treated as unrecognized — the manifest is not trusted over the signed records.
     */
    private Recognition recognizeAll(List<ItemChain> chains) {
        List<UUID> recognized = new ArrayList<>();
        List<UUID> rejected = new ArrayList<>();
        for (ItemChain chain : chains) {
            ItemImportOutcome outcome = itemImporter.recognize(chain.envelopes());
            UUID resolved = outcome.itemId();
            if (outcome.recognized() && (resolved == null || resolved.equals(chain.itemId()))) {
                recognized.add(chain.itemId());
            } else {
                rejected.add(chain.itemId());
            }
        }
        return new Recognition(recognized, rejected);
    }

    /**
     * A cheap, side-effect-free stale check before any character is created, so a plainly stale bundle is
     * refused without work. The authoritative monotonic guard is {@link
     * CharacterHomeDirectory#advanceHomeToLocal}, run last; this only fails fast.
     */
    private void preCheckSequence(Did accountDid, CharacterRef source, long presentedSequence) {
        long recognized = directory.currentSequence(accountDid, source);
        if (presentedSequence < recognized) {
            throw new StaleHomeSequenceException(source, presentedSequence, recognized);
        }
    }

    /**
     * Refuses an oversized bundle before verification begins (a DoS bound). Counts are checked before the
     * byte tally so the tally itself is bounded. The size is measured as total envelope character length, a
     * close and cheap proxy for bytes for the ASCII/base64 JSON these documents are.
     */
    private void enforceSize(List<ItemChain> chains) {
        if (chains.size() > properties.maxItems()) {
            throw new MigrationBundleTooLargeException("Bundle carries " + chains.size() + " items, over the limit "
                    + properties.maxItems() + " (eyeandsickle.migration.max-items).");
        }
        long total = 0;
        for (ItemChain chain : chains) {
            int records = chain.envelopes().size();
            if (records > properties.maxRecordsPerItem()) {
                throw new MigrationBundleTooLargeException("Item " + chain.itemId() + " carries " + records
                        + " records, over the limit " + properties.maxRecordsPerItem()
                        + " (eyeandsickle.migration.max-records-per-item).");
            }
            for (String envelope : chain.envelopes()) {
                total += envelope.length();
                if (total > properties.maxBundleBytes()) {
                    throw new MigrationBundleTooLargeException("Bundle exceeds the size limit "
                            + properties.maxBundleBytes() + " (eyeandsickle.migration.max-bundle-bytes).");
                }
            }
        }
    }
}
