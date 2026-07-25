package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import java.util.List;
import java.util.UUID;

/**
 * A scripted {@link MigrationItemImporter} for the import unit tests — the verify-and-recognize verdict
 * without real crypto.
 *
 * <h2>The verdict is encoded in the envelope string</h2>
 *
 * A migration unit test does not need genuine signatures to exercise the import <em>logic</em> — what an
 * item does when it verifies versus when it does not. So each seeded chain's single envelope is a small
 * directive this fake reads:
 *
 * <ul>
 *   <li>{@code "<itemId>:ok"} — verifies; recognized.
 *   <li>{@code "<itemId>:bad"} — a tampered/unverifiable chain; not recognized (dropped), stored nowhere.
 *   <li>{@code "<itemId>:mismatch:<otherId>"} — verifies, but the records name a <em>different</em> item
 *       than the manifest claimed; the import must not trust the manifest over the records.
 *   <li>{@code "malformed"} — not a well-formed envelope at all; an {@link IllegalArgumentException}, the
 *       bad-request path distinct from a verification failure.
 * </ul>
 *
 * The genuine crypto path is exercised separately by {@code ProvenanceIngressItemImporter} against a real
 * database in the integration tests; here the point is the surrounding decision.
 */
final class FakeMigrationItemImporter implements MigrationItemImporter {

    /** Builds an {@code ok} chain for an item — the shape a test hands the bundle. */
    static ItemChain ok(UUID itemId) {
        return new ItemChain(itemId, List.of(itemId + ":ok"));
    }

    /** Builds a tampered/unverifiable chain for an item. */
    static ItemChain bad(UUID itemId) {
        return new ItemChain(itemId, List.of(itemId + ":bad"));
    }

    /** Builds a chain whose records name {@code actualId} though the manifest says {@code itemId}. */
    static ItemChain mismatch(UUID itemId, UUID actualId) {
        return new ItemChain(itemId, List.of(itemId + ":mismatch:" + actualId));
    }

    /** Builds a malformed chain (its document is not a well-formed envelope). */
    static ItemChain malformed(UUID itemId) {
        return new ItemChain(itemId, List.of("malformed"));
    }

    @Override
    public ItemImportOutcome recognize(List<String> envelopeDocuments) {
        String document = envelopeDocuments.getFirst();
        if (document.equals("malformed")) {
            throw new IllegalArgumentException("not a well-formed provenance envelope: " + document);
        }
        String[] parts = document.split(":");
        UUID declared = UUID.fromString(parts[0]);
        return switch (parts[1]) {
            case "ok" -> new ItemImportOutcome(declared, true);
            case "bad" -> new ItemImportOutcome(declared, false);
            case "mismatch" -> new ItemImportOutcome(UUID.fromString(parts[2]), true);
            default -> throw new IllegalArgumentException("unknown verdict directive: " + document);
        };
    }
}
