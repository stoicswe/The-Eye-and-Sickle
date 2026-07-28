package io.github.stoicswe.eyeandsickle.solo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The storage capacities the STORAGE grid draws its empty slots against.
 *
 * <p>These were a single unused constant until 2026-07-27 — {@code STARTING_VAULT_CAPACITY} was
 * declared the day storage was written and read by nothing for as long as it existed. The grid makes
 * them visible, so they are worth pinning to the document that chose them.
 */
class StorageCapacityTest {

    @Test
    @DisplayName("every tier has a capacity, and they are design/01 §6's first-pass numbers")
    void capacitiesMatchTheDesignDoc() {
        assertThat(Balance.storageCapacity(StorageTier.VAULT)).isEqualTo(6);
        assertThat(Balance.storageCapacity(StorageTier.STANDARD_STORAGE)).isEqualTo(20);
        assertThat(Balance.storageCapacity(StorageTier.HIGH_HACKABLE_ZONE)).isEqualTo(60);
    }

    @Test
    @DisplayName("⚠ capacity rises strictly with exposure — that trade IS the storage mechanic")
    void safetyCostsRoom() {
        // design/01 §6: "a strict capacity/exposure trade". If the vault ever held as much as the
        // high-hackable zone there would be no decision left in the window at all — every item
        // would go in the vault and the other two mounts would be dead UI.
        assertThat(Balance.storageCapacity(StorageTier.VAULT))
                .isLessThan(Balance.storageCapacity(StorageTier.STANDARD_STORAGE));
        assertThat(Balance.storageCapacity(StorageTier.STANDARD_STORAGE))
                .isLessThan(Balance.storageCapacity(StorageTier.HIGH_HACKABLE_ZONE));
    }

    @Test
    @DisplayName("the vault is the scarce one, by a wide margin")
    void theVaultIsSmall() {
        // I12 makes vault capacity the thing that can never be bought. A vault at half the
        // high-hackable zone would make that invariant guard something that barely mattered.
        assertThat(Balance.storageCapacity(StorageTier.VAULT))
                .isLessThan(Balance.storageCapacity(StorageTier.HIGH_HACKABLE_ZONE) / 4);
    }

    @Test
    @DisplayName("every tier is covered — a new tier cannot be added without a capacity")
    void everyTierIsCovered() {
        for (StorageTier tier : StorageTier.values()) {
            assertThat(Balance.storageCapacity(tier)).as("%s", tier).isPositive();
        }
    }
}
