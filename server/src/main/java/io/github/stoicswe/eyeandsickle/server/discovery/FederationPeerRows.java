package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and row mappers for {@code federation_peers}, following the house pattern in {@code
 * RowMappers}: one class per table, column names as constants, mappers as static fields, and never a
 * {@code SELECT *}.
 *
 * <p>Listing columns explicitly is what keeps a query and its mapper reviewable against each other, and
 * what makes a later migration that widens the table unable to silently change a hot query's shape.
 */
final class FederationPeerRows {

    static final String TABLE = "federation_peers";

    static final String PEER_ID = "peer_id";
    static final String PEER_DID = "peer_did";
    static final String ENDPOINT_URL = "endpoint_url";
    static final String TRANSPORT_PUBLIC_KEY = "transport_public_key";
    static final String TRANSPORT_KEY_ID = "transport_key_id";
    static final String TRANSPORT_KEY_NOT_BEFORE = "transport_key_not_before";
    static final String TRANSPORT_KEY_NOT_AFTER = "transport_key_not_after";
    static final String SELF_DESCRIPTOR = "self_descriptor";
    static final String SEQUENCE_NUMBER = "sequence_number";
    static final String FIRST_SEEN_AT = "first_seen_at";
    static final String LAST_SEEN_AT = "last_seen_at";
    static final String LAST_SUCCESSFUL_CONTACT_AT = "last_successful_contact_at";
    static final String CONTACT_SUCCESSES = "contact_successes";
    static final String CONTACT_FAILURES = "contact_failures";
    static final String CONSECUTIVE_FAILURES = "consecutive_failures";
    static final String ROW_VERSION = "row_version";

    /** The full column list for a {@code PeerRecord}, in mapper order. */
    static final String ALL_COLUMNS = String.join(
            ", ",
            PEER_ID,
            PEER_DID,
            ENDPOINT_URL,
            TRANSPORT_PUBLIC_KEY,
            TRANSPORT_KEY_ID,
            TRANSPORT_KEY_NOT_BEFORE,
            TRANSPORT_KEY_NOT_AFTER,
            SELF_DESCRIPTOR,
            SEQUENCE_NUMBER,
            FIRST_SEEN_AT,
            LAST_SEEN_AT,
            LAST_SUCCESSFUL_CONTACT_AT,
            CONTACT_SUCCESSES,
            CONTACT_FAILURES,
            CONSECUTIVE_FAILURES,
            ROW_VERSION);

    static final RowMapper<PeerRecord> MAPPER = RowMappers.of(
            PeerRecord.class,
            row -> new PeerRecord(
                    row.uuid(PEER_ID),
                    row.text(PEER_DID),
                    row.text(ENDPOINT_URL),
                    row.bytes(TRANSPORT_PUBLIC_KEY),
                    row.textOrNull(TRANSPORT_KEY_ID),
                    row.instantOrNull(TRANSPORT_KEY_NOT_BEFORE),
                    row.instantOrNull(TRANSPORT_KEY_NOT_AFTER),
                    row.json(SELF_DESCRIPTOR),
                    row.int64(SEQUENCE_NUMBER),
                    row.instant(FIRST_SEEN_AT),
                    row.instant(LAST_SEEN_AT),
                    row.instantOrNull(LAST_SUCCESSFUL_CONTACT_AT),
                    row.int64(CONTACT_SUCCESSES),
                    row.int64(CONTACT_FAILURES),
                    row.int32(CONSECUTIVE_FAILURES),
                    row.int64(ROW_VERSION)));

    private FederationPeerRows() {}
}
