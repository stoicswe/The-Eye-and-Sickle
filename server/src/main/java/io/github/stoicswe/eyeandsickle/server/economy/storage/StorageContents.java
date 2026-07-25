package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.List;
import java.util.Objects;

/**
 * A holder's storage across all three tiers — what the storage view renders.
 *
 * <p>One {@link Tier} entry per {@link StorageTier}, always all three (an empty tier is
 * {@code used == 0}, not an absent entry), so the client can lay out the fixed three-tier UI without
 * inferring which tiers exist. Each entry carries its capacity, its current usage, its exposure, and
 * its items, so the risk/capacity trade of {@code docs/design/01-core-resources.md} §6 is legible at a
 * glance — the vault small and safe, the High-Hackable Zone large and always exposed.
 *
 * @param holderDid whose storage this is
 * @param tiers the three tiers, in {@link StorageTier} order
 */
public record StorageContents(String holderDid, List<Tier> tiers) {

    public StorageContents {
        Objects.requireNonNull(holderDid, "holderDid");
        Objects.requireNonNull(tiers, "tiers");
        tiers = List.copyOf(tiers);
    }

    /**
     * One tier's slice of a holder's storage.
     *
     * @param tier which tier
     * @param capacity the slot cap — for the vault, sub-linear in the holder's Cold Storage Expansion
     *     level and never purchasable (Invariant I12)
     * @param used how many slots are occupied
     * @param exposure the tier's exposure semantics
     * @param items the items in this tier
     */
    public record Tier(StorageTier tier, int capacity, int used, StorageExposure exposure, List<StoredItem> items) {

        public Tier {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(exposure, "exposure");
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);
        }

        /** @return remaining free slots, never negative */
        public int free() {
            return Math.max(0, capacity - used);
        }
    }
}
