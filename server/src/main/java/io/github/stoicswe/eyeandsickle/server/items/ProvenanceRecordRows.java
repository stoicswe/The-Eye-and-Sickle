package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Row;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the {@code provenance_records} table.
 *
 * <p>{@code envelope}, {@code payload} and {@code signatures} are read as their raw JSON text with
 * {@link Row#json(String)} rather than parsed here — the envelope in particular must reach a verifier
 * (or the player's client) as the exact bytes it was stored as, because re-serializing it could change
 * what a signature covers ({@code docs/architecture/04-item-provenance.md} §6.2). Parsing happens
 * downstream, once, where it is needed.
 */
final class ProvenanceRecordRows {

    static final String RECORD_ID = "record_id";
    static final String ITEM_ID = "item_id";
    static final String CHAIN_DEPTH = "chain_depth";
    static final String RECORD_HASH = "record_hash";
    static final String PREV_RECORD_HASH = "prev_record_hash";
    static final String EVENT_TYPE = "event_type";
    static final String HOLDER_DID = "holder_did";
    static final String ISSUER_DID = "issuer_did";
    static final String RECORD_VERSION = "record_version";
    static final String PAYLOAD = "payload";
    static final String ENVELOPE = "envelope";
    static final String SIGNATURES = "signatures";
    static final String PAYLOAD_TIMESTAMP = "payload_timestamp";
    static final String RECORDED_AT = "recorded_at";

    /** The projection every record read selects; there is no {@code SELECT *}. */
    static final String COLUMNS = String.join(
            ", ",
            RECORD_ID,
            ITEM_ID,
            CHAIN_DEPTH,
            RECORD_HASH,
            PREV_RECORD_HASH,
            EVENT_TYPE,
            HOLDER_DID,
            ISSUER_DID,
            RECORD_VERSION,
            PAYLOAD,
            ENVELOPE,
            SIGNATURES,
            PAYLOAD_TIMESTAMP,
            RECORDED_AT);

    static final RowMapper<StoredProvenanceRecord> MAPPER =
            RowMappers.of(StoredProvenanceRecord.class, ProvenanceRecordRows::read);

    private ProvenanceRecordRows() {}

    private static StoredProvenanceRecord read(Row row) {
        return new StoredProvenanceRecord(
                row.uuid(RECORD_ID),
                row.uuid(ITEM_ID),
                row.int32(CHAIN_DEPTH),
                row.text(RECORD_HASH),
                row.textOrNull(PREV_RECORD_HASH),
                EnumColumns.provenanceEventType(row.text(EVENT_TYPE)),
                row.text(HOLDER_DID),
                row.text(ISSUER_DID),
                row.int32(RECORD_VERSION),
                row.json(PAYLOAD),
                row.json(ENVELOPE),
                row.json(SIGNATURES),
                row.text(PAYLOAD_TIMESTAMP),
                row.instant(RECORDED_AT));
    }
}
