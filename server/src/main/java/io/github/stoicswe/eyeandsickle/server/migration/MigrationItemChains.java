package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import java.util.List;

/**
 * Reads out the provenanced items a migrating account carries, each with its full signed chain — the
 * export side of a migration ({@code docs/architecture/09-player-state-portability.md} §6, "your
 * provenanced items with their signed chains").
 *
 * <h2>Read-only, and holder-keyed by DID</h2>
 *
 * Export never mutates: the same read backs both a migration and a backup (§5). Each item's chain is
 * returned as the verbatim envelope documents, genesis-first, so the signatures reproduce byte-for-byte
 * at the destination (re-serializing them could change what a signature covers).
 *
 * <p><strong>Keying caveat.</strong> Items are held by <em>DID</em> ({@code items.holder_did}), and a DID
 * is an account that may hold several characters. This port therefore returns the <em>account's</em>
 * items. For a single-character account that is exact; attributing an item to one specific character of a
 * multi-character account needs an item-to-character link the items schema does not yet carry — a known
 * gap this slice reports rather than papers over. The default {@code JdbcMigrationItemChains} implements
 * the DID-scoped read directly over {@code items} and {@code provenance_records}.
 */
public interface MigrationItemChains {

    /**
     * Every provenanced item held by an account, each as its full chain of verbatim envelope documents.
     *
     * @param holder the account (holder DID) whose items to export
     * @return one {@link ItemChain} per held item, each ordered genesis-first; empty if the account holds
     *     no items
     */
    List<ItemChain> chainsForHolder(CharacterDid holder);
}
