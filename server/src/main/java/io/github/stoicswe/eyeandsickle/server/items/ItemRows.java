package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Row;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the {@code items} table, the house one-class-per-table pattern
 * ({@code io.github.stoicswe.eyeandsickle.server.persistence.RowMappers}).
 *
 * <p>{@code storage_tier} is read with the nullable accessor because a socketed item legitimately has
 * none ({@code ck_items_one_location}); {@link Item}'s own constructor then enforces the exactly-one
 * rule, so a row that somehow had neither is caught with a message about the item rather than a bare
 * mapping failure.
 */
final class ItemRows {

    static final String ITEM_ID = "item_id";
    static final String ITEM_TYPE = "item_type";
    static final String ITEM_ATTRS = "item_attrs";
    static final String HOLDER_DID = "holder_did";
    static final String STORAGE_TIER = "storage_tier";
    static final String SOCKETED_IN = "socketed_in";
    static final String ACQUIRED_AT = "acquired_at";
    static final String ROW_VERSION = "row_version";

    /** The projection every item read selects; there is no {@code SELECT *}. */
    static final String COLUMNS = String.join(
            ", ", ITEM_ID, ITEM_TYPE, ITEM_ATTRS, HOLDER_DID, STORAGE_TIER, SOCKETED_IN, ACQUIRED_AT, ROW_VERSION);

    static final RowMapper<Item> MAPPER = RowMappers.of(Item.class, ItemRows::read);

    private ItemRows() {}

    private static Item read(Row row) {
        String tier = row.textOrNull(STORAGE_TIER);
        return new Item(
                row.uuid(ITEM_ID),
                row.text(ITEM_TYPE),
                Jsonb.objectColumn(row, ITEM_ATTRS),
                row.text(HOLDER_DID),
                tier == null ? null : EnumColumns.storageTier(tier),
                row.uuidOrNull(SOCKETED_IN),
                row.instant(ACQUIRED_AT),
                row.int64(ROW_VERSION));
    }
}
