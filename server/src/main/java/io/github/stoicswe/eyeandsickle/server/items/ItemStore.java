package io.github.stoicswe.eyeandsickle.server.items;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of the {@code items} table, behind a narrow interface.
 *
 * <p>The interface exists so the minting and ingress logic can be unit-tested against an in-memory fake
 * with no database, while production wires {@link JdbcItemRepository}. Everything a cheating client
 * could assert — who holds an item, what its stats are — is written here only from a verified
 * provenance record, never from a request (Invariant I14); this interface is the boundary that keeps
 * that true.
 */
public interface ItemStore {

    /**
     * @param itemId the item's identity
     * @return the item, or empty if this server does not hold it
     */
    Optional<Item> find(UUID itemId);

    /**
     * @param itemId the item's identity
     * @return whether this server holds a row for it
     */
    boolean exists(UUID itemId);

    /**
     * Inserts a new item. Used when minting locally or ingesting a foreign item this server has not seen
     * before.
     *
     * @param item the row to insert; its {@code rowVersion} is the initial version
     * @throws org.springframework.dao.DataAccessException if a row for the item already exists, or a
     *     database constraint is violated
     */
    void insert(Item item);

    /**
     * Moves an item to a new holder, version-checked.
     *
     * <p>The holder change and the provenance record that authorizes it are written in one transaction
     * ({@link ItemProvenanceService}); this is the item half. The version check turns two concurrent
     * transfers of the same item into a retryable failure rather than a lost write.
     *
     * @param itemId the item's identity
     * @param newHolderDid the DID the item now belongs to
     * @param expectedRowVersion the version the caller read the item at
     * @return the item's new {@code rowVersion}
     * @throws org.springframework.dao.OptimisticLockingFailureException if no row matched the version
     */
    long updateHolder(UUID itemId, String newHolderDid, long expectedRowVersion);
}
