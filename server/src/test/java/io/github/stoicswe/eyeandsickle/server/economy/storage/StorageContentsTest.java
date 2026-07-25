package io.github.stoicswe.eyeandsickle.server.economy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.server.economy.storage.StorageContents.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The storage read-model the client renders: three tiers, each with capacity, usage, exposure and its
 * items. The free-slot arithmetic is clamped so an over-full tier reads as zero free rather than a
 * negative, and the item lists are defensively copied.
 */
class StorageContentsTest {

    private static StoredItem item(StorageTier tier) {
        return new StoredItem(UUID.randomUUID(), "hacking_tool_tier2", tier);
    }

    @Nested
    @DisplayName("free-slot arithmetic")
    class Free {

        @Test
        @DisplayName("free slots are capacity minus usage")
        void freeIsRemainder() {
            Tier tier = new Tier(StorageTier.VAULT, 6, 2, StorageExposure.NEVER_EXPOSED, List.of());
            assertThat(tier.free()).isEqualTo(4);
        }

        @Test
        @DisplayName("a full tier has zero free slots")
        void fullTierHasNoFree() {
            Tier tier = new Tier(StorageTier.VAULT, 6, 6, StorageExposure.NEVER_EXPOSED, List.of());
            assertThat(tier.free()).isZero();
        }

        @Test
        @DisplayName("an over-full tier reads as zero free, never negative")
        void overFullClampsToZero() {
            // Usage above capacity should not produce a negative "free" that some caller treats as space.
            Tier tier = new Tier(StorageTier.VAULT, 6, 9, StorageExposure.NEVER_EXPOSED, List.of());
            assertThat(tier.free()).isZero();
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("a tier copies its item list — later mutation of the caller's list does not leak in")
        void tierCopiesItems() {
            List<StoredItem> source = new ArrayList<>();
            source.add(item(StorageTier.VAULT));
            Tier tier = new Tier(StorageTier.VAULT, 6, 1, StorageExposure.NEVER_EXPOSED, source);

            source.add(item(StorageTier.VAULT));

            assertThat(tier.items()).hasSize(1);
        }

        @Test
        @DisplayName("StorageContents copies its tier list")
        void contentsCopiesTiers() {
            List<Tier> source = new ArrayList<>();
            source.add(new Tier(StorageTier.VAULT, 6, 0, StorageExposure.NEVER_EXPOSED, List.of()));
            StorageContents contents = new StorageContents("did:plc:holder00000000000000000", source);

            source.clear();

            assertThat(contents.tiers()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("required fields")
    class RequiredFields {

        @Test
        @DisplayName("StorageContents rejects a null holder or tier list")
        void contentsNullsRejected() {
            assertThatThrownBy(() -> new StorageContents(null, List.of())).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new StorageContents("did:plc:x00000000000000000000000", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a Tier rejects a null tier, exposure or item list")
        void tierNullsRejected() {
            assertThatThrownBy(() -> new Tier(null, 6, 0, StorageExposure.NEVER_EXPOSED, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Tier(StorageTier.VAULT, 6, 0, null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Tier(StorageTier.VAULT, 6, 0, StorageExposure.NEVER_EXPOSED, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a StoredItem rejects null fields")
        void storedItemNullsRejected() {
            assertThatThrownBy(() -> new StoredItem(null, "t", StorageTier.VAULT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new StoredItem(UUID.randomUUID(), null, StorageTier.VAULT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new StoredItem(UUID.randomUUID(), "t", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
