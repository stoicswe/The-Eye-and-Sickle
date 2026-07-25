package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An item held on this server, as the {@code items} table stores it.
 *
 * <p>The row is the queryable projection of the item's provenance chain tip ({@code
 * docs/architecture/04-item-provenance.md} §2): {@code holderDid} and {@code itemAttrs} always mirror
 * the most recent verified record, and are only ever written from one — never from a client request.
 * The provenance chain is the authoritative definition; this record is what makes "who holds it" and
 * "what are its stats" answerable without walking the chain every time.
 *
 * <p>Location is exactly one of {@code storageTier} or {@code socketedIn}, the {@code
 * ck_items_one_location} rule: an item is in a storage tier ({@code docs/design/01} §6) or socketed
 * into a bot, never both and never neither.
 *
 * @param itemId the item's identity, matching provenance {@code itemId}
 * @param itemType e.g. {@code hacking_tool_tier2}
 * @param itemAttrs authoritative stats, mirroring the chain tip's {@code itemAttrs}
 * @param holderDid the current owner's DID
 * @param storageTier the storage tier, or {@code null} when the item is socketed into a bot
 * @param socketedIn the bot instance this item is socketed into, or {@code null} when it is in storage
 * @param acquiredAt when this server first recorded the item
 * @param rowVersion optimistic-concurrency version
 */
public record Item(
        UUID itemId,
        String itemType,
        Map<String, Object> itemAttrs,
        String holderDid,
        StorageTier storageTier,
        UUID socketedIn,
        Instant acquiredAt,
        long rowVersion) {

    public Item {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemType, "itemType");
        Objects.requireNonNull(holderDid, "holderDid");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        itemAttrs = itemAttrs == null ? Map.of() : Map.copyOf(itemAttrs);
        // The database enforces this too (ck_items_one_location); catching it here gives a caller a
        // message about the item rather than a constraint name from a failed insert.
        if ((storageTier == null) == (socketedIn == null)) {
            throw new IllegalArgumentException(
                    "An item is in a storage tier or socketed into a bot, never both and never neither: "
                            + "storageTier=" + storageTier + ", socketedIn=" + socketedIn);
        }
    }
}
