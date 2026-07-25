package io.github.stoicswe.eyeandsickle.server.items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * In-memory {@link ItemStore} for the Docker-free unit tests.
 *
 * <p>Hand-written rather than mocked because these tests assert on what the minting and ingress logic
 * <em>did</em> to the store — that a rejected chain wrote nothing, that a holder change is
 * version-checked — and a fake reads far better than a pile of {@code verify(...)} calls. It mirrors
 * the two behaviours the real {@link JdbcItemRepository} is relied on for: an insert that refuses a
 * duplicate, and a holder update that fails on a stale version.
 */
final class FakeItemStore implements ItemStore {

    private final Map<UUID, Item> byId = new LinkedHashMap<>();

    @Override
    public Optional<Item> find(UUID itemId) {
        return Optional.ofNullable(byId.get(itemId));
    }

    @Override
    public boolean exists(UUID itemId) {
        return byId.containsKey(itemId);
    }

    @Override
    public void insert(Item item) {
        // The real table's primary key refuses a second row; a fake that silently overwrote would let a
        // double-mint test pass for the wrong reason.
        if (byId.putIfAbsent(item.itemId(), item) != null) {
            throw new IllegalStateException("duplicate item " + item.itemId());
        }
    }

    @Override
    public long updateHolder(UUID itemId, String newHolderDid, long expectedRowVersion) {
        Item current = byId.get(itemId);
        if (current == null || current.rowVersion() != expectedRowVersion) {
            throw new OptimisticLockingFailureException("no item " + itemId + " at version " + expectedRowVersion);
        }
        long nextVersion = expectedRowVersion + 1;
        byId.put(
                itemId,
                new Item(
                        current.itemId(),
                        current.itemType(),
                        current.itemAttrs(),
                        newHolderDid,
                        current.storageTier(),
                        current.socketedIn(),
                        current.acquiredAt(),
                        nextVersion));
        return nextVersion;
    }

    /** How many items are held — used to assert a rejected chain wrote nothing. */
    int size() {
        return byId.size();
    }
}
