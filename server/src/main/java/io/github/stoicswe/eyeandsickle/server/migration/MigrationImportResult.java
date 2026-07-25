package io.github.stoicswe.eyeandsickle.server.migration;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a destination did with a migration bundle
 * ({@code docs/architecture/09-player-state-portability.md} §6).
 *
 * <p>A migration does not fail because one item's chain does not verify: the fresh character is created
 * regardless, and each item is recognized only if its own chain passes ("you kept your verified gear",
 * §6). This result is the honest ledger of that — the new character's id, the advanced home-binding
 * sequence, and which items were recognized versus dropped — so the outcome is auditable rather than a
 * bare success flag.
 *
 * @param newCharacterId the fresh character minted at this destination home (a new id — §6)
 * @param newHomeSequence the home-binding sequence the directory now recognizes for it, strictly advanced
 *     past the bundle's (§4, §6.1)
 * @param recognizedItemIds the items whose chains re-verified and are now held here
 * @param rejectedItemIds the items whose chains failed verification and were stored nowhere
 * @param economyReset whether the economy was reset to base on arrival — {@code true} on the untrusted
 *     Option-C path (§6), {@code false} on the trusted Option-B path, which carries standing (§5)
 */
public record MigrationImportResult(
        UUID newCharacterId,
        long newHomeSequence,
        List<UUID> recognizedItemIds,
        List<UUID> rejectedItemIds,
        boolean economyReset) {

    public MigrationImportResult {
        Objects.requireNonNull(newCharacterId, "newCharacterId");
        recognizedItemIds = List.copyOf(Objects.requireNonNull(recognizedItemIds, "recognizedItemIds"));
        rejectedItemIds = List.copyOf(Objects.requireNonNull(rejectedItemIds, "rejectedItemIds"));
    }

    /** @return how many items were recognized onto the fresh character */
    public int recognizedCount() {
        return recognizedItemIds.size();
    }

    /** @return how many items were dropped as unverifiable */
    public int rejectedCount() {
        return rejectedItemIds.size();
    }
}
