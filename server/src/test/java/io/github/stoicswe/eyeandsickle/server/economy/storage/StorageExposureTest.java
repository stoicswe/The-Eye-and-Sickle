package io.github.stoicswe.eyeandsickle.server.economy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tier→exposure mapping ({@code docs/design/01-core-resources.md} §6): each tier buys capacity with
 * exposure, and the mapping is derived from the tier, never stored, so a second column cannot drift
 * from it.
 */
class StorageExposureTest {

    @Test
    @DisplayName("the vault is never exposed, standard is exposed while online, the high-hackable zone always")
    void mappingMatchesSection6() {
        assertThat(StorageExposure.of(StorageTier.VAULT)).isEqualTo(StorageExposure.NEVER_EXPOSED);
        assertThat(StorageExposure.of(StorageTier.STANDARD_STORAGE)).isEqualTo(StorageExposure.EXPOSED_WHILE_ONLINE);
        assertThat(StorageExposure.of(StorageTier.HIGH_HACKABLE_ZONE)).isEqualTo(StorageExposure.ALWAYS_EXPOSED);
    }

    @Test
    @DisplayName("every tier has a mapping — no tier falls through")
    void everyTierMaps() {
        for (StorageTier tier : StorageTier.values()) {
            assertThatCode(() -> StorageExposure.of(tier)).as(tier.name()).doesNotThrowAnyException();
        }
    }
}
