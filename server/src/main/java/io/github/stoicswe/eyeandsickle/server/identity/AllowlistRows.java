package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for {@code allowlist_entries}.
 *
 * <p>House pattern (see {@code RowMappers}): one class per table, explicit column list, a single
 * mapper. The DID columns are wrapped as {@link Did} on the way out so a malformed value stored by
 * something that bypassed the application surfaces as a mapping failure naming the column, rather than
 * flowing on as a plausible string.
 */
final class AllowlistRows {

    static final String ENTRY_ID = "entry_id";
    static final String DID = "did";
    static final String ADDED_AT = "added_at";
    static final String ADDED_BY_DID = "added_by_did";
    static final String NOTE = "note";
    static final String REVOKED_AT = "revoked_at";
    static final String REVOKED_BY_DID = "revoked_by_did";

    /** The explicit projection for every {@code allowlist_entries} read in this slice. */
    static final String COLUMNS =
            String.join(", ", ENTRY_ID, DID, ADDED_AT, ADDED_BY_DID, NOTE, REVOKED_AT, REVOKED_BY_DID);

    static final RowMapper<AllowlistEntry> MAPPER = RowMappers.of(
            AllowlistEntry.class,
            row -> new AllowlistEntry(
                    row.uuid(ENTRY_ID),
                    Did.of(row.text(DID)),
                    row.instant(ADDED_AT),
                    Did.ofNullable(row.textOrNull(ADDED_BY_DID)),
                    row.textOrNull(NOTE),
                    row.instantOrNull(REVOKED_AT),
                    Did.ofNullable(row.textOrNull(REVOKED_BY_DID))));

    private AllowlistRows() {}
}
