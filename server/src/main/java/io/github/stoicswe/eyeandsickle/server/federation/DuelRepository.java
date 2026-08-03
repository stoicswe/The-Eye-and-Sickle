package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledCommittee;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the {@code duels} table — cross-server adjudications and their frozen sampling
 * records ({@code docs/architecture/05-validator-quorum.md} §5).
 *
 * <p>A duel is opened unresolved, with its committee sampled and stored; it is resolved once a quorum
 * signs an outcome. The resolution write is version-checked, because two adjudications of the same
 * duel racing to resolve it must not both win — the loser re-reads and sees it is already resolved.
 */
@Repository
public class DuelRepository {

    private final JdbcClient jdbcClient;

    DuelRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Opens a duel and freezes its sampled committee.
     *
     * <p>The committee is stored as the {@code sampled_validators} jsonb array — DID, reputation,
     * uptime and derived weight per member — and {@code committee_size} is the array length the schema
     * cross-checks. Signatures start as the empty array and the outcome as SQL NULL: an unresolved
     * duel with both markers absent, which the {@code ck_duels_resolved_pair} constraint requires.
     *
     * @param duelId the adjudication id
     * @param participants the fighting servers' DIDs (at least two, per the schema)
     * @param committee the frozen sampling record
     * @param openedAt the sampling time
     */
    public void open(UUID duelId, List<String> participants, SampledCommittee committee, Instant openedAt) {
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO duels (duel_id, participants, sampled_validators, committee_size,
                                           outcome, signatures, opened_at, resolved_at, row_version)
                        VALUES (:duelId, :participants FORMAT JSON, :sampled FORMAT JSON, :committeeSize,
                                NULL, '[]' FORMAT JSON, :openedAt, NULL, 0)
                        """)
                .param("duelId", duelId)
                .param("participants", Jsonb.writeArray(participants))
                .param("sampled", Jsonb.writeArray(committee.toJsonArray()))
                .param("committeeSize", committee.size())
                .param("openedAt", Timestamps.at(openedAt))
                .update();
        Mutations.requireInserted(inserted, "duels");
    }

    /**
     * @param duelId the adjudication id
     * @return the duel, or empty if unknown
     */
    public Optional<DuelRecord> find(UUID duelId) {
        return jdbcClient
                .sql("SELECT " + DuelRows.COLUMNS + " FROM duels WHERE duel_id = :duelId")
                .param("duelId", duelId)
                .query(DuelRows.MAPPER)
                .optional();
    }

    /** @return the still-open duels, oldest first — the work queue of adjudications awaiting quorum */
    public List<DuelRecord> findUnresolved() {
        return jdbcClient
                .sql("SELECT " + DuelRows.COLUMNS + " FROM duels WHERE resolved_at IS NULL ORDER BY opened_at")
                .query(DuelRows.MAPPER)
                .list();
    }

    /**
     * Records the agreed outcome and the signatures that carried it, closing the duel.
     *
     * <p>Version-checked against the value the caller read, so a concurrent resolution of the same
     * duel is turned into an {@link org.springframework.dao.OptimisticLockingFailureException} rather
     * than silently overwriting the first outcome with a second. Setting {@code outcome} and {@code
     * resolved_at} together satisfies {@code ck_duels_resolved_pair}.
     *
     * @param duelId the adjudication id
     * @param outcomeJson the agreed outcome, a JSON object (the {@code duel_grant} payload)
     * @param signaturesJson the agreeing validators' signature blocks, a JSON array
     * @param resolvedAt the resolution time
     * @param expectedVersion the {@code row_version} the duel was read with
     */
    public void resolve(
            UUID duelId, String outcomeJson, String signaturesJson, Instant resolvedAt, long expectedVersion) {
        int affected = jdbcClient
                .sql("""
                        UPDATE duels
                           SET outcome = :outcome FORMAT JSON,
                               signatures = :signatures FORMAT JSON,
                               resolved_at = :resolvedAt,
                               row_version = row_version + 1
                         WHERE duel_id = :duelId
                           AND row_version = :expectedVersion
                        """)
                .param("outcome", outcomeJson)
                .param("signatures", signaturesJson)
                .param("resolvedAt", Timestamps.atOrNull(resolvedAt))
                .param("duelId", duelId)
                .param("expectedVersion", expectedVersion)
                .update();
        Mutations.requireUpdated(affected, "duels", duelId);
    }
}
