package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.UUID;

/**
 * The storage slice's failure signals, kept together because they are small and share one concern.
 *
 * <p>Each is its own type so the REST edge can map it precisely — a full tier is {@code 409 Conflict},
 * a missing item is {@code 404} — rather than collapsing them into one opaque error the caller cannot
 * act on differently.
 */
public final class StorageExceptions {

    private StorageExceptions() {}

    /**
     * Thrown when an item cannot enter a tier because the tier is at capacity.
     *
     * <p>Capacity is the whole point of the tier system ({@code docs/design/01-core-resources.md} §6):
     * the vault is deliberately small, and refusing the overflow is what forces the risk decision —
     * leave the item in an exposed tier, or socket it into a bot and accept mid-risk. Silently dropping
     * the item, or silently exceeding the cap, would erase that decision.
     */
    public static final class TierAtCapacity extends RuntimeException {

        private final transient StorageTier tier;
        private final int capacity;

        /**
         * @param holderDid the holder whose tier is full
         * @param tier the full tier
         * @param capacity its capacity
         */
        public TierAtCapacity(String holderDid, StorageTier tier, int capacity) {
            super("Holder " + holderDid + "'s " + tier + " is at capacity (" + capacity
                    + " slots); free a slot or choose another tier");
            this.tier = tier;
            this.capacity = capacity;
        }

        /** @return the full tier */
        public StorageTier tier() {
            return tier;
        }

        /** @return the tier's capacity */
        public int capacity() {
            return capacity;
        }
    }

    /**
     * Thrown when an operation names an item id that does not exist on this server.
     */
    public static final class ItemNotFound extends RuntimeException {

        private final transient UUID itemId;

        /**
         * @param itemId the missing item id
         */
        public ItemNotFound(UUID itemId) {
            super("No item on this server has id " + itemId);
            this.itemId = itemId;
        }

        /** @return the missing item id */
        public UUID itemId() {
            return itemId;
        }
    }
}
