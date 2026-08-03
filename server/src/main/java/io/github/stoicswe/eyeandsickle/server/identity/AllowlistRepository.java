package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes {@code allowlist_entries} — the durable, runtime-editable form of the operator's
 * join allowlist ({@code docs/architecture/03-server-and-federation.md} §1).
 *
 * <h2>The one hot query</h2>
 *
 * {@link #isAllowed(Did)} answers "may this DID join right now", asked once per sign-in, and is served
 * by the partial index {@code ix_allowlist_entries_active}. It tests for an <em>active</em> (not
 * soft-revoked) entry, so a revoked DID is refused without the row being destroyed — the moderation
 * history the operator may later need survives.
 *
 * <h2>Attribution is required for revocation, optional for addition</h2>
 *
 * The schema pairs {@code revoked_at} with {@code revoked_by_did} and forbids one without the other, so
 * {@link #revoke(Did, Did, Instant)} demands a revoking actor; addition allows a null adder because the
 * initial configuration seed has no in-game actor. Passing a null revoker would be a constraint
 * violation, which the caller ({@link OperatorAllowlistService}) prevents by refusing to revoke without
 * a configured operator DID.
 */
@Repository
public class AllowlistRepository {

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient Spring's JdbcClient over the server's Postgres
     */
    public AllowlistRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * Whether a DID has an active (unrevoked) allowlist entry.
     *
     * @param did the identity to check
     * @return {@code true} if it may currently join
     */
    public boolean isAllowed(Did did) {
        Objects.requireNonNull(did, "did");
        return Boolean.TRUE.equals(jdbcClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM allowlist_entries
                             WHERE did = :did AND revoked_at IS NULL)
                        """)
                .param("did", did.value())
                .query(Boolean.class)
                .single());
    }

    /**
     * Adds an entry if the DID has none, and reports whether it was added.
     *
     * <p>Idempotent by DID: a {@code MERGE} with only a {@code WHEN NOT MATCHED} branch means seeding
     * the same configuration twice, or an operator re-adding an existing DID, is a harmless no-op
     * rather than a duplicate-key failure. The boolean lets the seeder log "added" versus "already
     * present" honestly. Note a consequence worth stating: because the match is on the unique DID,
     * re-adding a <em>revoked</em> DID does nothing — un-revoking is a separate, deliberate action,
     * not a silent side effect of an add.
     *
     * <p>⚠ This was {@code ON CONFLICT (did) DO NOTHING}. The H2 spelling with a {@code WHEN MATCHED}
     * branch added — a plain upsert — would <strong>overwrite</strong> the existing row, which here
     * means re-stamping {@code added_at} and {@code added_by_did} for an operator who merely re-ran
     * the seed. Omitting that branch is what keeps this an add-if-absent.
     *
     * @param did the identity to allow
     * @param addedBy the DID performing the addition, or {@code null} for a configuration seed
     * @param note free-text context, or {@code null}
     * @param now the instant to record
     * @return {@code true} if a row was inserted, {@code false} if one already existed for this DID
     */
    public boolean insertIfAbsent(Did did, Did addedBy, String note, Instant now) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(now, "now");
        int inserted = jdbcClient
                .sql("""
                        MERGE INTO allowlist_entries AS t
                        USING (VALUES (CAST(:entryId AS uuid), CAST(:did AS varchar),
                                       CAST(:now AS timestamp with time zone),
                                       CAST(:addedBy AS varchar), CAST(:note AS varchar)))
                              AS s(entry_id, did, added_at, added_by_did, note)
                           ON t.did = s.did
                         WHEN NOT MATCHED THEN INSERT (entry_id, did, added_at, added_by_did, note)
                              VALUES (s.entry_id, s.did, s.added_at, s.added_by_did, s.note)
                        """)
                .param("entryId", UUID.randomUUID())
                .param("did", did.value())
                .param("now", at(now))
                .param("addedBy", addedBy == null ? null : addedBy.value())
                .param("note", note)
                .update();
        return inserted == 1;
    }

    /**
     * Soft-revokes the active entry for a DID, and reports whether one was revoked.
     *
     * <p>Targets only an active entry ({@code revoked_at IS NULL}), so revoking a DID that is absent or
     * already revoked returns {@code false} rather than failing — the operator's intent ("this DID is
     * not welcome") is already satisfied in both cases.
     *
     * @param did the identity to revoke
     * @param revokedBy the operator's attribution DID; required by the schema and never {@code null}
     * @param now the instant to record; must be at or after the entry's {@code added_at}
     * @return {@code true} if an active entry was revoked, {@code false} if none was active
     */
    public boolean revoke(Did did, Did revokedBy, Instant now) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(revokedBy, "revokedBy");
        Objects.requireNonNull(now, "now");
        int revoked = jdbcClient
                .sql("""
                        UPDATE allowlist_entries
                           SET revoked_at     = :now,
                               revoked_by_did = :revokedBy
                         WHERE did = :did
                           AND revoked_at IS NULL
                        """)
                .param("now", at(now))
                .param("revokedBy", revokedBy.value())
                .param("did", did.value())
                .update();
        return revoked == 1;
    }

    /**
     * @param did the identity to look up
     * @return the entry for a DID regardless of its revocation state, or empty if none exists
     */
    public Optional<AllowlistEntry> findByDid(Did did) {
        Objects.requireNonNull(did, "did");
        return jdbcClient
                .sql("SELECT " + AllowlistRows.COLUMNS + " FROM allowlist_entries WHERE did = :did")
                .param("did", did.value())
                .query(AllowlistRows.MAPPER)
                .optional();
    }

    /**
     * Lists every entry, active and revoked, newest first — the operator's audit view.
     *
     * @return all allowlist entries
     */
    public List<AllowlistEntry> findAll() {
        return jdbcClient
                .sql("SELECT " + AllowlistRows.COLUMNS + " FROM allowlist_entries ORDER BY added_at DESC")
                .query(AllowlistRows.MAPPER)
                .list();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
