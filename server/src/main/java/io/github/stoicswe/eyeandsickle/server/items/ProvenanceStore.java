package io.github.stoicswe.eyeandsickle.server.items;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-item provenance chain storage, behind a narrow interface.
 *
 * <p>Chains are per-item ({@code docs/architecture/04-item-provenance.md} §6), stored indexed by {@code
 * itemId} with {@code chainDepth} so a client can fetch "records N through N+20" instead of walking
 * from the tip every time (§6.1). This interface exposes exactly those access patterns; {@link
 * JdbcProvenanceRepository} implements them and an in-memory fake serves the unit tests.
 *
 * <p>The table is append-only at the database level — a trigger refuses UPDATE and DELETE — so there is
 * deliberately no update or delete method here. A correction to history is a new record, never an edit.
 */
public interface ProvenanceStore {

    /**
     * Appends a record. The caller guarantees it is the next link — the database's {@code UNIQUE
     * (item_id, chain_depth)} refuses a second record at the same depth, which is how a forked chain is
     * rejected.
     *
     * @param record the row to append
     * @throws org.springframework.dao.DataAccessException on a duplicate position or a constraint
     *     violation
     */
    void append(StoredProvenanceRecord record);

    /**
     * The tip of an item's chain: the record at the greatest {@code chainDepth}.
     *
     * @param itemId the item
     * @return the tip, or empty if the item has no records yet
     */
    Optional<StoredProvenanceRecord> findTip(UUID itemId);

    /**
     * A contiguous range of an item's chain, ordered genesis-first — the §6.1 range query.
     *
     * @param itemId the item
     * @param fromDepth the first {@code chainDepth} to return (inclusive)
     * @param limit the maximum number of records
     * @return the records from {@code fromDepth} upward, at most {@code limit} of them, in ascending
     *     depth order
     */
    List<StoredProvenanceRecord> findRange(UUID itemId, int fromDepth, int limit);

    /**
     * An item's entire chain, ordered genesis-first — what a verifier walks.
     *
     * @param itemId the item
     * @return every record for the item, in ascending depth order
     */
    List<StoredProvenanceRecord> findChain(UUID itemId);
}
