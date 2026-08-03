package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.engine.persistence.Timestamps;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProvenanceStore} over {@code JdbcClient} and hand-written SQL.
 *
 * <p>The range query (§6.1) leans on {@code UNIQUE (item_id, chain_depth)}, which already indexes
 * {@code item_id} as a leading column, so no separate {@code item_id} index is needed (the schema
 * comment says as much). {@code payload}, {@code envelope} and {@code signatures} are written with the
 * {@code  FORMAT JSON} cast, from the one {@link StoredProvenanceRecord} the caller built from a single
 * parse — the three columns cannot drift because they are never derived independently.
 */
// @Component, not @Repository — see JdbcItemRepository: @Repository's translation proxy cannot
// subclass a final class and is redundant over JdbcClient's native DataAccessExceptions.
@org.springframework.stereotype.Component
public final class JdbcProvenanceRepository implements ProvenanceStore {

    private final JdbcClient jdbcClient;

    public JdbcProvenanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public void append(StoredProvenanceRecord record) {
        Objects.requireNonNull(record, "record");
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO provenance_records
                            (record_id, item_id, chain_depth, record_hash, prev_record_hash, event_type,
                             holder_did, issuer_did, record_version, payload, envelope, signatures,
                             payload_timestamp, recorded_at)
                        VALUES
                            (:recordId, :itemId, :chainDepth, :recordHash, :prevRecordHash, :eventType,
                             :holderDid, :issuerDid, :recordVersion, :payload FORMAT JSON, :envelope FORMAT JSON,
                             :signatures FORMAT JSON, :payloadTimestamp, :recordedAt)
                        """)
                .param("recordId", record.recordId())
                .param("itemId", record.itemId())
                .param("chainDepth", record.chainDepth())
                .param("recordHash", record.recordHash())
                .param("prevRecordHash", record.prevRecordHash())
                .param("eventType", EnumColumns.provenanceEventType(record.eventType()))
                .param("holderDid", record.holderDid())
                .param("issuerDid", record.issuerDid())
                .param("recordVersion", record.recordVersion())
                .param("payload", record.payloadJson())
                .param("envelope", record.envelopeJson())
                .param("signatures", record.signaturesJson())
                .param("payloadTimestamp", record.payloadTimestamp())
                .param("recordedAt", Timestamps.at(record.recordedAt()))
                .update();
        Mutations.requireInserted(inserted, "provenance_records");
    }

    @Override
    public Optional<StoredProvenanceRecord> findTip(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return jdbcClient
                .sql("SELECT " + ProvenanceRecordRows.COLUMNS
                        + " FROM provenance_records WHERE item_id = :itemId ORDER BY chain_depth DESC LIMIT 1")
                .param("itemId", itemId)
                .query(ProvenanceRecordRows.MAPPER)
                .optional();
    }

    @Override
    public List<StoredProvenanceRecord> findRange(UUID itemId, int fromDepth, int limit) {
        Objects.requireNonNull(itemId, "itemId");
        if (limit <= 0) {
            return List.of();
        }
        return jdbcClient
                .sql("SELECT " + ProvenanceRecordRows.COLUMNS
                        + " FROM provenance_records"
                        + " WHERE item_id = :itemId AND chain_depth >= :fromDepth"
                        + " ORDER BY chain_depth"
                        + " LIMIT :limit")
                .param("itemId", itemId)
                .param("fromDepth", fromDepth)
                .param("limit", limit)
                .query(ProvenanceRecordRows.MAPPER)
                .list();
    }

    @Override
    public List<StoredProvenanceRecord> findChain(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return jdbcClient
                .sql("SELECT " + ProvenanceRecordRows.COLUMNS
                        + " FROM provenance_records WHERE item_id = :itemId ORDER BY chain_depth")
                .param("itemId", itemId)
                .query(ProvenanceRecordRows.MAPPER)
                .list();
    }
}
