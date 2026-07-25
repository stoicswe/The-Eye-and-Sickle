package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import java.util.List;
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
     * Lists the items a <em>character</em> holds.
     *
     * <p>Keyed on the {@link CharacterDid}, not the account DID, so two characters of one account see
     * <em>different</em> inventories ({@code docs/architecture/09-player-state-portability.md} §9,
     * Q-item-keying option 3). The parameter is a {@link CharacterDid} rather than a bare string precisely
     * so a caller cannot re-introduce the account-shared bug by keying this read on an account DID — the
     * only inventory you can ask for is a single character's.
     *
     * @param holder the character whose items to list
     * @return that character's items, in no guaranteed order; empty if it holds none
     */
    List<Item> findByHolder(CharacterDid holder);

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
     * <p>{@code newHolderDid} is a holder string — for a local move, the character DID
     * ({@code did:eyeandsickle:<slot>:<accountDid>}) that {@link ItemProvenanceService} derives from the
     * new owning character (09 §9); for an ingested item, the holder string the verified chain tip carried.
     * A plain {@code String} keeps this persistence primitive honest to the {@code holder_did} text column
     * and able to store a foreign holder it does not parse.
     *
     * @param itemId the item's identity
     * @param newHolderDid the (character) DID string the item now belongs to
     * @param expectedRowVersion the version the caller read the item at
     * @return the item's new {@code rowVersion}
     * @throws org.springframework.dao.OptimisticLockingFailureException if no row matched the version
     */
    long updateHolder(UUID itemId, String newHolderDid, long expectedRowVersion);
}
