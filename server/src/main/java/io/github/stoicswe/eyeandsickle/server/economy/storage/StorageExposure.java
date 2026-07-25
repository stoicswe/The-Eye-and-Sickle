package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;

/**
 * How exposed a storage tier is — the risk half of the capacity/exposure trade ({@code
 * docs/design/01-core-resources.md} §6).
 *
 * <p>Each tier buys capacity with exposure: the vault is small and safe, the High-Hackable Zone is
 * large and always raidable. This enum is the server-side reading of that trade, derived purely from
 * the tier — the raid-targeting slice ({@code docs/design/07-recon-tools.md}, {@code
 * docs/design/09-defense-and-hardening.md}) consumes it to decide what is reachable and when, but the
 * economy slice owns the tier→exposure mapping because it is a property of the tier, stated in §6.
 *
 * <p>It is derived, never stored: a second column echoing the tier would eventually disagree with it.
 */
public enum StorageExposure {

    /** Encrypted Vault: never exposed. Safe until an item is socketed into a bot and leaves the vault. */
    NEVER_EXPOSED,

    /** Standard Storage: exposed while the owner is online, safe while they are offline. */
    EXPOSED_WHILE_ONLINE,

    /** High-Hackable Zone: always exposed, raidable even while the owner is offline. */
    ALWAYS_EXPOSED;

    /**
     * The exposure of a tier.
     *
     * @param tier the tier
     * @return its exposure semantics, per {@code docs/design/01-core-resources.md} §6
     */
    public static StorageExposure of(StorageTier tier) {
        return switch (tier) {
            case VAULT -> NEVER_EXPOSED;
            case STANDARD_STORAGE -> EXPOSED_WHILE_ONLINE;
            case HIGH_HACKABLE_ZONE -> ALWAYS_EXPOSED;
        };
    }
}
