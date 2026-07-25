package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.Objects;
import java.util.UUID;

/**
 * One item as it sits in a storage tier — the projection the storage views need, and no more.
 *
 * <p>Deliberately thin: an id, a type, and the tier it occupies. The authoritative item definition
 * lives in the provenance chain and {@code items.item_attrs} ({@code docs/architecture/04}), which is
 * another slice's concern; storage only needs to know what is where, to count capacity and list a
 * tier's contents. A socketed item ({@code items.socketed_in} set) is not in a tier and is not one of
 * these — it has left the vault and become mid-risk, which the bot slice owns.
 *
 * @param itemId the item's identifier
 * @param itemType the item's type tag, for display
 * @param tier the tier the item currently occupies
 */
public record StoredItem(UUID itemId, String itemType, StorageTier tier) {

    public StoredItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemType, "itemType");
        Objects.requireNonNull(tier, "tier");
    }
}
