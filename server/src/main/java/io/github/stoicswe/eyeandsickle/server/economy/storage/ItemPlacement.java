package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.Objects;
import java.util.UUID;

/**
 * Where an item currently sits, and the version needed to move it safely.
 *
 * <p>Distinct from {@link StoredItem}, which is the read-model projection for listing a tier's
 * contents. This is the projection a <em>mutation</em> needs: the item's present location plus its
 * {@code row_version}, so a move can be applied with an optimistic-concurrency guard rather than
 * blindly overwriting a placement another request changed in between.
 *
 * <p>Exactly one of {@code tier} and {@code socketedIn} is set — the {@code ck_items_one_location}
 * constraint guarantees it in the database, and this record mirrors that: a tiered item has a tier
 * and no socket, a socketed item ({@code docs/design/10-botnets.md} — it has left the vault and
 * become mid-risk) has a socket and no tier. Both being null would mean an item that exists nowhere,
 * which the schema forbids.
 *
 * @param itemId the item's identifier
 * @param holderDid the DID that owns the item ({@code docs/architecture/04-item-provenance.md})
 * @param tier the tier the item occupies, or {@code null} if it is socketed into a bot
 * @param socketedIn the bot instance the item is socketed into, or {@code null} if it is in a tier
 * @param rowVersion the item row's version, for the optimistic-concurrency guard on a move
 */
public record ItemPlacement(UUID itemId, String holderDid, StorageTier tier, UUID socketedIn, long rowVersion) {

    public ItemPlacement {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(holderDid, "holderDid");
        if ((tier == null) == (socketedIn == null)) {
            throw new IllegalArgumentException(
                    "Exactly one of tier / socketedIn must be set (ck_items_one_location); got tier=" + tier
                            + ", socketedIn=" + socketedIn);
        }
    }

    /** Whether the item is socketed into a bot rather than resting in a storage tier. */
    public boolean isSocketed() {
        return socketedIn != null;
    }
}
