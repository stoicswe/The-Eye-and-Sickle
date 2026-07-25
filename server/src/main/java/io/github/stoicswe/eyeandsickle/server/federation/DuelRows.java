package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Row;
import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the {@code duels} table.
 *
 * <p>The interesting part is {@link #committeeOf(Row, UUID)}: it turns the stored {@code
 * sampled_validators} jsonb array back into a protocol {@link QuorumCommittee}, mapping each member's
 * DID to the {@code weight} that was frozen at sampling time. It reads the stored weight verbatim
 * rather than recomputing {@code reputation × uptime}, so an audit sees the exact number the committee
 * was judged by ({@code docs/architecture/04} §7).
 */
final class DuelRows {

    static final String DUEL_ID = "duel_id";
    static final String PARTICIPANTS = "participants";
    static final String SAMPLED_VALIDATORS = "sampled_validators";
    static final String COMMITTEE_SIZE = "committee_size";
    static final String OUTCOME = "outcome";
    static final String SIGNATURES = "signatures";
    static final String OPENED_AT = "opened_at";
    static final String RESOLVED_AT = "resolved_at";
    static final String ROW_VERSION = "row_version";

    static final String COLUMNS = String.join(
            ", ",
            DUEL_ID,
            PARTICIPANTS,
            SAMPLED_VALIDATORS,
            COMMITTEE_SIZE,
            OUTCOME,
            SIGNATURES,
            OPENED_AT,
            RESOLVED_AT,
            ROW_VERSION);

    static final RowMapper<DuelRecord> MAPPER = RowMappers.of(DuelRecord.class, DuelRows::map);

    private DuelRows() {}

    private static DuelRecord map(Row row) {
        UUID duelId = row.uuid(DUEL_ID);
        return new DuelRecord(
                duelId,
                participantsOf(row),
                committeeOf(row, duelId),
                row.jsonOrNull(OUTCOME),
                row.json(SIGNATURES),
                row.instant(OPENED_AT),
                row.instantOrNull(RESOLVED_AT),
                row.int64(ROW_VERSION));
    }

    private static List<String> participantsOf(Row row) {
        List<Object> raw = Jsonb.arrayColumn(row, PARTICIPANTS);
        return raw.stream().map(String::valueOf).toList();
    }

    /** Rebuilds the frozen committee: each element's {@code did} mapped to its stored {@code weight}. */
    private static QuorumCommittee committeeOf(Row row, UUID duelId) {
        List<Object> members = Jsonb.arrayColumn(row, SAMPLED_VALIDATORS);
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Object member : members) {
            if (!(member instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("duels.sampled_validators element is not an object: " + member);
            }
            Object did = entry.get("did");
            Object weight = entry.get("weight");
            if (!(did instanceof String didString) || !(weight instanceof Number weightNumber)) {
                throw new IllegalArgumentException(
                        "duels.sampled_validators element needs a string 'did' and numeric 'weight': " + entry);
            }
            weights.put(didString, weightNumber.doubleValue());
        }
        return new QuorumCommittee(duelId.toString(), weights);
    }
}
