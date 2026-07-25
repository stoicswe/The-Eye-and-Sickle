package io.github.stoicswe.eyeandsickle.server.migration;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Re-verifies one item's provenance chain at the destination and recognizes it only if it verifies — the
 * import side of a migration ({@code docs/architecture/09-player-state-portability.md} §6, §6.1
 * "provenance is re-verified at the destination, never trusted from the bundle").
 *
 * <h2>This is provenance ingress with a character attached</h2>
 *
 * Importing a migrated item is exactly what the items slice already does at the federation edge: run the
 * full {@code ProvenanceChainVerifier} walk, and store the item and its chain <em>only</em> if the verdict
 * is recognized; a chain that fails any check is stored nowhere. The default {@code
 * ProvenanceIngressItemImporter} leans on {@code ProvenanceIngressService} for precisely this, so the
 * anti-cheat model is the one place it already lives, not a second copy. Migration adds only the decision
 * of what to do with the outcome: a recognized item joins the fresh character's inventory, an unrecognized
 * one is simply dropped (the character is still created — you kept your verified gear, §6).
 */
public interface MigrationItemImporter {

    /**
     * Verifies a single item's chain and recognizes (stores) it if and only if it passes.
     *
     * @param envelopeDocuments the item's records as verbatim detached-JWS envelope documents, ordered
     *     genesis-first
     * @return the outcome — the item's id and whether it was recognized
     * @throws IllegalArgumentException if a document is not a well-formed envelope (a malformed bundle, a
     *     client/peer error distinct from a well-formed chain that fails verification)
     */
    ItemImportOutcome recognize(List<String> envelopeDocuments);

    /**
     * What became of one item on import.
     *
     * @param itemId the item the chain described, or {@code null} if none could be read
     * @param recognized whether the chain verified and the item is now held here (either freshly stored,
     *     or already present from a prior transfer); a rejected chain is {@code false} and stored nowhere
     */
    record ItemImportOutcome(UUID itemId, boolean recognized) {

        /**
         * @param itemId the item id, or {@code null}
         * @param recognized whether it was recognized
         */
        public ItemImportOutcome {
            // itemId may be null only when nothing could be parsed; recognized then must be false.
            if (recognized) {
                Objects.requireNonNull(itemId, "itemId of a recognized item");
            }
        }
    }
}
