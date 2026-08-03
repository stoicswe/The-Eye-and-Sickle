package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.engine.persistence.Timestamps;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ItemStore} over {@code JdbcClient} and hand-written SQL — the decided data-access approach
 * (open question A-4, resolved; {@code server/pom.xml}).
 *
 * <p>Reads select the explicit {@link ItemRows#COLUMNS} projection, never {@code SELECT *}; the {@code
 * item_attrs} jsonb parameter is bound with the {@code  FORMAT JSON} cast the driver requires ({@code
 * io.github.stoicswe.eyeandsickle.server.persistence.Jsonb}); and the holder update is version-checked,
 * turning a concurrent transfer into a retryable {@code OptimisticLockingFailureException} rather than a
 * silently lost write.
 */
// @Component, not @Repository: @Repository adds a persistence-exception-translation proxy that
// cannot subclass this final class, and it is redundant here — JdbcClient already throws Spring's
// DataAccessException hierarchy natively, which is all @Repository's translation would provide.
@org.springframework.stereotype.Component
public final class JdbcItemRepository implements ItemStore {

    private final JdbcClient jdbcClient;

    public JdbcItemRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public Optional<Item> find(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return jdbcClient
                .sql("SELECT " + ItemRows.COLUMNS + " FROM items WHERE " + ItemRows.ITEM_ID + " = :itemId")
                .param("itemId", itemId)
                .query(ItemRows.MAPPER)
                .optional();
    }

    @Override
    public List<Item> findByHolder(CharacterDid holder) {
        Objects.requireNonNull(holder, "holder");
        // Reads by holder_did — the character DID string — served by ix_items_holder. Because holder_did
        // now stores the character DID (09 §9), this returns exactly one character's items, so an account's
        // characters never see a shared inventory.
        return jdbcClient
                .sql("SELECT " + ItemRows.COLUMNS + " FROM items WHERE " + ItemRows.HOLDER_DID
                        + " = :holderDid ORDER BY " + ItemRows.ITEM_ID)
                .param("holderDid", holder.value())
                .query(ItemRows.MAPPER)
                .list();
    }

    @Override
    public boolean exists(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return jdbcClient
                .sql("SELECT 1 FROM items WHERE " + ItemRows.ITEM_ID + " = :itemId")
                .param("itemId", itemId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public void insert(Item item) {
        Objects.requireNonNull(item, "item");
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO items
                            (item_id, item_type, item_attrs, holder_did, storage_tier, socketed_in,
                             acquired_at, row_version)
                        VALUES
                            (:itemId, :itemType, :attrs FORMAT JSON, :holderDid, :storageTier, :socketedIn,
                             :acquiredAt, :rowVersion)
                        """)
                .param("itemId", item.itemId())
                .param("itemType", item.itemType())
                .param("attrs", Jsonb.writeObject(item.itemAttrs()))
                .param("holderDid", item.holderDid())
                .param("storageTier", item.storageTier() == null ? null : EnumColumns.storageTier(item.storageTier()))
                .param("socketedIn", item.socketedIn())
                .param("acquiredAt", Timestamps.at(item.acquiredAt()))
                .param("rowVersion", item.rowVersion())
                .update();
        Mutations.requireInserted(inserted, "items");
    }

    @Override
    public long updateHolder(UUID itemId, String newHolderDid, long expectedRowVersion) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(newHolderDid, "newHolderDid");
        int updated = jdbcClient
                .sql("""
                        UPDATE items
                           SET holder_did = :holderDid,
                               row_version = row_version + 1
                         WHERE item_id = :itemId
                           AND row_version = :expectedVersion
                        """)
                .param("holderDid", newHolderDid)
                .param("itemId", itemId)
                .param("expectedVersion", expectedRowVersion)
                .update();
        Mutations.requireUpdated(updated, "items", itemId);
        return Mutations.nextRowVersion(expectedRowVersion);
    }
}
