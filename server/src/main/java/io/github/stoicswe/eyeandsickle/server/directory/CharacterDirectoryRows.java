package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for {@code character_directory}, following the house pattern in {@code
 * RowMappers}: one class per table, column names as constants, the mapper as a static field, and never a
 * {@code SELECT *}.
 *
 * <p>Listing columns explicitly is what keeps a query and its mapper reviewable against each other, and
 * what makes a later migration that widens the table unable to silently change a hot read's shape.
 */
final class CharacterDirectoryRows {

    static final String TABLE = "character_directory";

    static final String ENTRY_ID = "entry_id";
    static final String ACCOUNT_DID = "account_did";
    static final String CHARACTER_ID = "character_id";
    static final String SLOT = "slot";
    static final String HOME_SERVER_DID = "home_server_did";
    static final String HOME_ENDPOINT = "home_endpoint";
    static final String HOME_TRANSPORT_PUBLIC_KEY = "home_transport_public_key";
    static final String SIGNING_KEY_ID = "signing_key_id";
    static final String SEQUENCE_NUMBER = "sequence_number";
    static final String SIGNATURE = "signature";
    static final String FIRST_SEEN_AT = "first_seen_at";
    static final String LAST_SEEN_AT = "last_seen_at";
    static final String ROW_VERSION = Mutations.ROW_VERSION;

    /** The full column list for a {@link CharacterHomeEntry}, in mapper order. */
    static final String ALL_COLUMNS = String.join(
            ", ",
            ENTRY_ID,
            ACCOUNT_DID,
            CHARACTER_ID,
            SLOT,
            HOME_SERVER_DID,
            HOME_ENDPOINT,
            HOME_TRANSPORT_PUBLIC_KEY,
            SIGNING_KEY_ID,
            SEQUENCE_NUMBER,
            SIGNATURE,
            FIRST_SEEN_AT,
            LAST_SEEN_AT,
            ROW_VERSION);

    static final RowMapper<CharacterHomeEntry> MAPPER = RowMappers.of(
            CharacterHomeEntry.class,
            row -> new CharacterHomeEntry(
                    row.uuid(ENTRY_ID),
                    row.text(ACCOUNT_DID),
                    row.uuid(CHARACTER_ID),
                    row.int32(SLOT),
                    row.text(HOME_SERVER_DID),
                    row.text(HOME_ENDPOINT),
                    row.bytes(HOME_TRANSPORT_PUBLIC_KEY),
                    row.text(SIGNING_KEY_ID),
                    row.int64(SEQUENCE_NUMBER),
                    row.bytes(SIGNATURE),
                    row.instant(FIRST_SEEN_AT),
                    row.instant(LAST_SEEN_AT),
                    row.int64(ROW_VERSION)));

    private CharacterDirectoryRows() {}
}
