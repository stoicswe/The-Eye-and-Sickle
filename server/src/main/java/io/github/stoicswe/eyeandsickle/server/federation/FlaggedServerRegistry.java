package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@code flagged_servers} — the registry driving federation-wide non-recognition
 * ({@code docs/architecture/03-server-and-federation.md} §4).
 *
 * <p>The schema's partial unique index {@code uq_flagged_servers_active} allows at most one <em>active</em>
 * flag per server. This registry leans on that rather than fighting it: {@link #flag} is idempotent,
 * so raising the same flag twice — two peers detecting the same equivocation, say — settles to one
 * active flag rather than an error.
 */
@Repository
public class FlaggedServerRegistry {

    private final JdbcClient jdbcClient;

    FlaggedServerRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Flags a server for non-recognition, or returns the existing active flag if it is already
     * flagged.
     *
     * <p>Idempotent by design. A duplicate active flag is caught from the partial unique index and
     * resolved to the flag already in force, because "this server is not recognised" is a state, not an
     * event to be recorded twice — a second row would let one be cleared while the other silently kept
     * the server non-recognised. Concurrency: two inserts racing both target the same partial-unique
     * key, so the loser gets {@link DuplicateKeyException} and falls through to reading the winner's
     * row.
     *
     * @param serverDid the server to flag; the database enforces DID shape
     * @param reason why — {@link FlaggedServer#REASON_EQUIVOCATION} for the automatic case, free text
     *     otherwise
     * @param evidence the proof as a JSON object (empty for a flag with no cryptographic proof)
     * @param raisedByDid the raising server's DID, or {@code null} if local/automatic
     * @param now the flag time
     * @return the active flag — freshly inserted, or the one already in force
     */
    public FlaggedServer flag(
            String serverDid, String reason, Map<String, Object> evidence, String raisedByDid, Instant now) {
        UUID flagId = UUID.randomUUID();
        try {
            int inserted = jdbcClient
                    .sql("""
                            INSERT INTO flagged_servers (flag_id, server_did, reason, evidence, raised_by_did, flagged_at)
                            VALUES (:flagId, :serverDid, :reason, :evidence::jsonb, :raisedByDid, :now)
                            """)
                    .param("flagId", flagId)
                    .param("serverDid", serverDid)
                    .param("reason", reason)
                    .param("evidence", Jsonb.writeObject(evidence))
                    .param("raisedByDid", raisedByDid)
                    .param("now", Timestamps.at(now))
                    .update();
            Mutations.requireInserted(inserted, "flagged_servers");
            return find(flagId).orElseThrow();
        } catch (DuplicateKeyException alreadyFlagged) {
            // Already non-recognised: return the flag in force rather than raising a second.
            return findActive(serverDid).orElseThrow(() -> alreadyFlagged);
        }
    }

    /**
     * @param flagId the flag's id
     * @return the flag, or empty if unknown
     */
    public Optional<FlaggedServer> find(UUID flagId) {
        return jdbcClient
                .sql("SELECT " + FlaggedServerRows.COLUMNS + " FROM flagged_servers WHERE flag_id = :flagId")
                .param("flagId", flagId)
                .query(FlaggedServerRows.MAPPER)
                .optional();
    }

    /**
     * @param serverDid the server's DID
     * @return its active (uncleared) flag, or empty if it is recognised
     */
    public Optional<FlaggedServer> findActive(String serverDid) {
        return jdbcClient
                .sql("SELECT " + FlaggedServerRows.COLUMNS
                        + " FROM flagged_servers WHERE server_did = :serverDid AND cleared_at IS NULL")
                .param("serverDid", serverDid)
                .query(FlaggedServerRows.MAPPER)
                .optional();
    }

    /**
     * @param serverDid the server's DID
     * @return whether the server is currently non-recognised
     */
    public boolean isFlagged(String serverDid) {
        return findActive(serverDid).isPresent();
    }

    /** @return every active flag, newest first — the non-recognition list an honest server enforces */
    public List<FlaggedServer> listActive() {
        return jdbcClient
                .sql("SELECT " + FlaggedServerRows.COLUMNS
                        + " FROM flagged_servers WHERE cleared_at IS NULL ORDER BY flagged_at DESC")
                .query(FlaggedServerRows.MAPPER)
                .list();
    }

    /**
     * Clears a server's active flag, restoring recognition.
     *
     * <p>An {@code UPDATE} scoped to the one active flag ({@code cleared_at IS NULL}), so it cannot
     * touch an already-cleared historical flag. {@link Mutations#requireUpdated} turns "no active flag
     * to clear" into a failure rather than a silent no-op the caller would read as success.
     *
     * @param serverDid the server to restore
     * @param note why it is being cleared (reversibility is {@code [PROPOSAL]}, §4; the note is the
     *     audit trail meanwhile)
     * @param now the clear time
     */
    public void clear(String serverDid, String note, Instant now) {
        int affected = jdbcClient
                .sql("""
                        UPDATE flagged_servers
                           SET cleared_at = :now,
                               cleared_note = :note
                         WHERE server_did = :serverDid
                           AND cleared_at IS NULL
                        """)
                .param("now", Timestamps.at(now))
                .param("note", note)
                .param("serverDid", serverDid)
                .update();
        Mutations.requireUpdated(affected, "flagged_servers", serverDid);
    }
}
