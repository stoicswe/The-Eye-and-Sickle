package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@link JdbcItemRepository} against a real PostgreSQL — the item projection of a verified chain tip,
 * only ever written from a verified record (Invariant I14).
 *
 * <p>The raw {@code items} constraints are proved in {@code SchemaIT}; this test proves the repository
 * <em>code</em>: the {@code ::jsonb}-cast insert, the explicit-projection read that round-trips an item
 * intact, and — the load-bearing one — the version-checked holder update that turns two concurrent
 * transfers into a retryable conflict rather than a lost write.
 */
class JdbcItemRepositoryIT extends PostgresIntegrationTestBase {

    private static final String HOLDER = "did:plc:holder00000000000000";
    private static final String OTHER_HOLDER = "did:plc:holder11111111111111";
    private static final Instant ACQUIRED = Instant.parse("2026-08-01T12:00:00Z");

    private JdbcItemRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcItemRepository(jdbcClient());
    }

    private Item stored(UUID id, StorageTier tier, UUID socketedIn) {
        return new Item(
                id, "hacking_tool_tier2", Map.of("power", 42, "grade", "t2"), HOLDER, tier, socketedIn, ACQUIRED, 0L);
    }

    // ------------------------------------------------------------------ round-trip

    @Test
    @DisplayName("an inserted item reads back intact, attrs and all")
    void insertAndFindRoundTrips() {
        UUID id = UUID.randomUUID();
        repository.insert(stored(id, StorageTier.VAULT, null));

        Item found = repository.find(id).orElseThrow();
        assertThat(found.itemId()).isEqualTo(id);
        assertThat(found.itemType()).isEqualTo("hacking_tool_tier2");
        assertThat(found.holderDid()).isEqualTo(HOLDER);
        assertThat(found.storageTier()).isEqualTo(StorageTier.VAULT);
        assertThat(found.socketedIn()).isNull();
        assertThat(found.acquiredAt()).isEqualTo(ACQUIRED);
        assertThat(found.rowVersion()).isZero();
        assertThat(found.itemAttrs()).containsEntry("power", 42).containsEntry("grade", "t2");
    }

    @Test
    @DisplayName("a socketed item round-trips with a null tier and a bot reference")
    void socketedItemRoundTrips() {
        UUID id = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        repository.insert(stored(id, null, bot));

        Item found = repository.find(id).orElseThrow();
        // Location is exactly one of tier or socket; a socketed item legitimately has no tier.
        assertThat(found.storageTier()).isNull();
        assertThat(found.socketedIn()).isEqualTo(bot);
    }

    @Test
    @DisplayName("exists reflects presence, and a missing item reads back empty")
    void existsAndMissing() {
        UUID id = UUID.randomUUID();
        assertThat(repository.exists(id)).isFalse();
        assertThat(repository.find(id)).isEmpty();

        repository.insert(stored(id, StorageTier.STANDARD_STORAGE, null));
        assertThat(repository.exists(id)).isTrue();
    }

    @Test
    @DisplayName("a second insert of the same id is refused by the primary key")
    void duplicateInsertRefused() {
        UUID id = UUID.randomUUID();
        repository.insert(stored(id, StorageTier.VAULT, null));

        assertThatThrownBy(() -> repository.insert(stored(id, StorageTier.VAULT, null)))
                .isInstanceOf(DataAccessException.class);
    }

    // ------------------------------------------------------------------ version-checked holder update

    @Test
    @DisplayName("a holder update on the expected version moves the item and bumps the version")
    void updateHolderOnCurrentVersion() {
        UUID id = UUID.randomUUID();
        repository.insert(stored(id, StorageTier.VAULT, null));

        long next = repository.updateHolder(id, OTHER_HOLDER, 0L);

        assertThat(next).isEqualTo(1L);
        Item moved = repository.find(id).orElseThrow();
        assertThat(moved.holderDid()).isEqualTo(OTHER_HOLDER);
        assertThat(moved.rowVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a holder update on a stale version is a retryable conflict, not a lost write")
    void updateHolderOnStaleVersionConflicts() {
        UUID id = UUID.randomUUID();
        repository.insert(stored(id, StorageTier.VAULT, null));

        // The first transfer wins and advances the version.
        repository.updateHolder(id, OTHER_HOLDER, 0L);

        // The second still believes it holds version 0 — the classic lost update, which here would be an
        // item transferred to two owners.
        assertThatThrownBy(() -> repository.updateHolder(id, HOLDER, 0L))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.find(id).orElseThrow().holderDid())
                .as("the losing transfer did not take effect")
                .isEqualTo(OTHER_HOLDER);
    }

    @Test
    @DisplayName("a holder update on an item that does not exist is a conflict")
    void updateHolderOnMissingItemConflicts() {
        assertThatThrownBy(() -> repository.updateHolder(UUID.randomUUID(), OTHER_HOLDER, 0L))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
