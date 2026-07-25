package io.github.stoicswe.eyeandsickle.server.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ProvenanceStore} for the Docker-free unit tests.
 *
 * <p>Keeps one append-ordered list per item and answers the three read patterns the interface exposes.
 * It does <em>not</em> reproduce the database's {@code UNIQUE (item_id, chain_depth)} guard — that
 * constraint biting is what {@code JdbcProvenanceRepositoryIT} exists to prove against a real
 * PostgreSQL; here the point is the service's ordering and linkage logic, not the storage layer's.
 */
final class FakeProvenanceStore implements ProvenanceStore {

    private final Map<UUID, List<StoredProvenanceRecord>> byItem = new LinkedHashMap<>();

    @Override
    public void append(StoredProvenanceRecord record) {
        byItem.computeIfAbsent(record.itemId(), ignored -> new ArrayList<>()).add(record);
    }

    @Override
    public Optional<StoredProvenanceRecord> findTip(UUID itemId) {
        return records(itemId).stream().max(Comparator.comparingInt(StoredProvenanceRecord::chainDepth));
    }

    @Override
    public List<StoredProvenanceRecord> findRange(UUID itemId, int fromDepth, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return records(itemId).stream()
                .filter(record -> record.chainDepth() >= fromDepth)
                .sorted(Comparator.comparingInt(StoredProvenanceRecord::chainDepth))
                .limit(limit)
                .toList();
    }

    @Override
    public List<StoredProvenanceRecord> findChain(UUID itemId) {
        return records(itemId).stream()
                .sorted(Comparator.comparingInt(StoredProvenanceRecord::chainDepth))
                .toList();
    }

    private List<StoredProvenanceRecord> records(UUID itemId) {
        return byItem.getOrDefault(itemId, List.of());
    }

    /** Total records stored across all items — used to assert a rejected chain wrote nothing. */
    int size() {
        return byItem.values().stream().mapToInt(List::size).sum();
    }
}
