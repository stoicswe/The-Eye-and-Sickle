package io.github.stoicswe.eyeandsickle.server.economy.storage;

import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and mappers for the storage slice's reads of {@code items}.
 *
 * <p>Only the columns storage cares about — id, type, tier, socket, holder, version — never
 * {@code SELECT *} and never {@code item_attrs}, which is the provenance slice's authoritative surface,
 * not storage's. The tier column is {@code text} + CHECK, so it is spelled through {@link EnumColumns}
 * (the one place a protocol enum becomes a database value), and read as nullable because a socketed
 * item has no tier.
 */
final class StoredItemRows {

    static final String ITEM_ID = "item_id";
    static final String ITEM_TYPE = "item_type";
    static final String HOLDER_DID = "holder_did";
    static final String STORAGE_TIER = "storage_tier";
    static final String SOCKETED_IN = "socketed_in";
    static final String ACQUIRED_AT = "acquired_at";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    /** For a tier listing: id, type, and the (non-null) tier the item sits in. */
    static final RowMapper<StoredItem> IN_TIER = RowMappers.of(
            StoredItem.class,
            row -> new StoredItem(
                    row.uuid(ITEM_ID), row.text(ITEM_TYPE), EnumColumns.storageTier(row.text(STORAGE_TIER))));

    /** For a placement decision: where the item is now and its version, to move it safely. */
    static final RowMapper<ItemPlacement> PLACEMENT = RowMappers.of(
            ItemPlacement.class,
            row -> new ItemPlacement(
                    row.uuid(ITEM_ID),
                    row.text(HOLDER_DID),
                    // Nullable: a socketed item has no tier, and a tiered item has no socket
                    // (ck_items_one_location guarantees exactly one is set).
                    map(row.textOrNull(STORAGE_TIER)),
                    row.uuidOrNull(SOCKETED_IN),
                    row.int64(ROW_VERSION)));

    private static io.github.stoicswe.eyeandsickle.protocol.game.StorageTier map(String tier) {
        return tier == null ? null : EnumColumns.storageTier(tier);
    }

    private StoredItemRows() {}
}
