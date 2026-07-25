package io.github.stoicswe.eyeandsickle.server.economy.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.server.economy.storage.StorageExceptions.ItemNotFound;
import io.github.stoicswe.eyeandsickle.server.economy.storage.StorageExceptions.TierAtCapacity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The storage failure signals carry enough structure for the REST edge to map each to its own status —
 * a full tier is a conflict, a missing item a not-found — rather than one opaque error.
 */
class StorageExceptionsTest {

    @Test
    @DisplayName("TierAtCapacity carries the tier and its capacity, and names them in the message")
    void tierAtCapacity() {
        TierAtCapacity exception = new TierAtCapacity("did:plc:holder00000000000000000", StorageTier.VAULT, 6);

        assertThat(exception.tier()).isEqualTo(StorageTier.VAULT);
        assertThat(exception.capacity()).isEqualTo(6);
        assertThat(exception.getMessage()).contains("VAULT").contains("6");
    }

    @Test
    @DisplayName("ItemNotFound carries the missing id")
    void itemNotFound() {
        UUID id = UUID.randomUUID();
        ItemNotFound exception = new ItemNotFound(id);

        assertThat(exception.itemId()).isEqualTo(id);
        assertThat(exception.getMessage()).contains(id.toString());
    }
}
