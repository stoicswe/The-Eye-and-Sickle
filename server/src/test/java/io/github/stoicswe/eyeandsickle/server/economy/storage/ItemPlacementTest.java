package io.github.stoicswe.eyeandsickle.server.economy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An item's location, and the "exactly one place" rule ({@code ck_items_one_location}). A tiered item
 * has a tier and no socket; a socketed item has left the vault to work inside a bot — mid-risk — and so
 * has a socket and no tier ({@code docs/design/01-core-resources.md} §6, {@code
 * docs/design/10-botnets.md}). Both-null (an item nowhere) and both-set are refused, mirroring the
 * database constraint so an impossible placement cannot even be constructed.
 */
class ItemPlacementTest {

    private static final String HOLDER = "did:plc:holder00000000000000000";
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID BOT = UUID.randomUUID();

    @Test
    @DisplayName("a tiered item has a tier and no socket, and is not socketed")
    void tieredItem() {
        ItemPlacement placement = new ItemPlacement(ITEM, HOLDER, StorageTier.VAULT, null, 0L);
        assertThat(placement.isSocketed()).isFalse();
        assertThat(placement.tier()).isEqualTo(StorageTier.VAULT);
    }

    @Test
    @DisplayName("assigning an item to a bot: it has a socket and no tier — it has left the vault")
    void socketedItem() {
        // §6: anything assigned to a bot leaves the vault and becomes mid-risk. Structurally, that is a
        // socket with no tier — the item is no longer in any storage tier at all.
        ItemPlacement placement = new ItemPlacement(ITEM, HOLDER, null, BOT, 3L);
        assertThat(placement.isSocketed()).isTrue();
        assertThat(placement.tier()).isNull();
        assertThat(placement.socketedIn()).isEqualTo(BOT);
    }

    @Test
    @DisplayName("an item cannot be in a tier AND socketed — that is not one location, it is two")
    void bothSetRejected() {
        assertThatThrownBy(() -> new ItemPlacement(ITEM, HOLDER, StorageTier.VAULT, BOT, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ck_items_one_location");
    }

    @Test
    @DisplayName("an item cannot be nowhere — neither tier nor socket is an item that exists nowhere")
    void bothNullRejected() {
        assertThatThrownBy(() -> new ItemPlacement(ITEM, HOLDER, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ck_items_one_location");
    }

    @Test
    @DisplayName("itemId and holderDid are required")
    void nullsRejected() {
        assertThatThrownBy(() -> new ItemPlacement(null, HOLDER, StorageTier.VAULT, null, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ItemPlacement(ITEM, null, StorageTier.VAULT, null, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("all three tiers are valid resting places")
    void everyTierIsAValidLocation() {
        for (StorageTier tier : StorageTier.values()) {
            assertThatCode(() -> new ItemPlacement(UUID.randomUUID(), HOLDER, tier, null, 0L))
                    .as(tier.name())
                    .doesNotThrowAnyException();
        }
    }
}
