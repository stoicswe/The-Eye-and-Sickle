package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Hand-written SQL over {@code character_directory}, the character-home directory table.
 *
 * <h2>Thin primitives; the decision lives in the service</h2>
 *
 * These methods are individual, legible statements. The multi-step convergence decision — insert vs.
 * advance vs. refresh vs. ignore-as-stale-or-conflict — is composed in {@link CharacterDirectoryService}
 * inside one {@code @Transactional} method, following the house rule that a decision spanning several
 * statements gets one transaction boundary ({@code Mutations}).
 *
 * <h2>Monotonic advance is expressed in the WHERE clause</h2>
 *
 * {@link #updateIfNewer} advances a binding only {@code WHERE sequence_number < :seq}. That single
 * predicate is the whole monotonic rule (09 §4): it is race-safe (only the highest offered sequence
 * wins, whichever connection runs first) and it can never regress a binding, which the database's
 * {@code character_directory_no_sequence_rollback} trigger then guarantees a second, independent time. A
 * wall clock never enters into it.
 *
 * <h2>Left non-{@code final} on purpose, mirroring {@code FederationPeerRepository}</h2>
 *
 * So {@link CharacterDirectoryService} can be unit-tested against an in-memory {@code
 * FakeCharacterDirectoryRepository} that overrides these methods with {@code super(null)} — the same
 * Docker-free pattern the discovery slice uses. {@code JdbcClient} surfaces a driver {@code SQLException}
 * — including the anti-rollback trigger's {@code restrict_violation} — as a Spring {@code
 * DataAccessException} on its own, so {@code @Repository}'s exception translation is not what carries it.
 */
@Repository
public class CharacterDirectoryRepository {

    private final JdbcClient jdbcClient;

    CharacterDirectoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * The current binding for one account slot, if this server knows it.
     *
     * @param accountDid the account
     * @param slot the save slot within the account
     * @return the stored binding, or empty
     */
    public Optional<CharacterHomeEntry> findByAccountAndSlot(String accountDid, int slot) {
        return jdbcClient
                .sql("SELECT " + CharacterDirectoryRows.ALL_COLUMNS + " FROM " + CharacterDirectoryRows.TABLE
                        + " WHERE " + CharacterDirectoryRows.ACCOUNT_DID + " = :accountDid AND "
                        + CharacterDirectoryRows.SLOT + " = :slot")
                .param("accountDid", accountDid)
                .param("slot", slot)
                .query(CharacterDirectoryRows.MAPPER)
                .optional();
    }

    /**
     * Inserts a previously-unknown binding, doing nothing if a concurrent writer inserted the same
     * {@code (account, slot)} first.
     *
     * @param record the verified home record
     * @param now the observation instant
     * @return 1 if inserted, 0 if a row for this {@code (account, slot)} already existed
     */
    public int insertNew(CharacterHomeRecord record, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO character_directory
                            (entry_id, account_did, character_id, slot, home_server_did, home_endpoint,
                             home_transport_public_key, signing_key_id, sequence_number, signature,
                             first_seen_at, last_seen_at)
                        VALUES
                            (:entryId, :accountDid, :characterId, :slot, :homeServerDid, :homeEndpoint,
                             :key, :signingKeyId, :seq, :signature, :now, :now)
                        ON CONFLICT (account_did, slot) DO NOTHING
                        """)
                .param("entryId", UUID.randomUUID())
                .param("accountDid", record.accountDid())
                .param("characterId", record.characterId())
                .param("slot", record.slot())
                .param("homeServerDid", record.homeServerDid())
                .param("homeEndpoint", record.homeEndpoint())
                .param("key", record.homeTransportPublicKey())
                .param("signingKeyId", record.signingKeyId())
                .param("seq", record.sequenceNumber())
                .param("signature", record.signature())
                .param("now", Timestamps.at(now))
                .update();
    }

    /**
     * Advances a known binding to a strictly-higher sequence, replacing the home, character id, endpoint,
     * key and signature.
     *
     * @param record the verified, newer record
     * @param now the observation instant
     * @return 1 if advanced, 0 if the stored sequence was already at or above this one
     */
    public int updateIfNewer(CharacterHomeRecord record, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE character_directory
                           SET character_id = :characterId,
                               home_server_did = :homeServerDid,
                               home_endpoint = :homeEndpoint,
                               home_transport_public_key = :key,
                               signing_key_id = :signingKeyId,
                               sequence_number = :seq,
                               signature = :signature,
                               last_seen_at = :now,
                               row_version = row_version + 1
                         WHERE account_did = :accountDid
                           AND slot = :slot
                           AND sequence_number < :seq
                        """)
                .param("characterId", record.characterId())
                .param("homeServerDid", record.homeServerDid())
                .param("homeEndpoint", record.homeEndpoint())
                .param("key", record.homeTransportPublicKey())
                .param("signingKeyId", record.signingKeyId())
                .param("seq", record.sequenceNumber())
                .param("signature", record.signature())
                .param("accountDid", record.accountDid())
                .param("slot", record.slot())
                .param("now", Timestamps.at(now))
                .update();
    }

    /**
     * Refreshes a binding's last-observed time without changing it — a same-sequence, same-signature
     * re-announcement.
     *
     * @param accountDid the account
     * @param slot the save slot
     * @param now the observation instant
     * @return rows touched (0 if the binding is unknown)
     */
    public int touchLastSeen(String accountDid, int slot, Instant now) {
        return jdbcClient
                .sql("UPDATE " + CharacterDirectoryRows.TABLE + " SET " + CharacterDirectoryRows.LAST_SEEN_AT
                        + " = :now WHERE " + CharacterDirectoryRows.ACCOUNT_DID + " = :accountDid AND "
                        + CharacterDirectoryRows.SLOT + " = :slot")
                .param("now", Timestamps.at(now))
                .param("accountDid", accountDid)
                .param("slot", slot)
                .update();
    }

    /** @return how many home bindings are in the directory */
    public long count() {
        return jdbcClient
                .sql("SELECT count(*) FROM " + CharacterDirectoryRows.TABLE)
                .query(Long.class)
                .single();
    }

    /**
     * How many characters the directory recognizes for an account — the number the soft slot cap is
     * checked against (09 §2). One row per occupied slot, so a plain count of the account's rows is the
     * count of its recognized characters.
     *
     * @param accountDid the account
     * @return the number of recognized characters
     */
    public long countByAccount(String accountDid) {
        return jdbcClient
                .sql("SELECT count(*) FROM " + CharacterDirectoryRows.TABLE + " WHERE "
                        + CharacterDirectoryRows.ACCOUNT_DID + " = :accountDid")
                .param("accountDid", accountDid)
                .query(Long.class)
                .single();
    }

    /**
     * An account's home bindings, ordered by slot, bounded for anti-amplification.
     *
     * @param accountDid the account to resolve
     * @param limit the maximum bindings to return
     * @return the account's recognized characters and their homes, by slot
     */
    public List<CharacterHomeEntry> findByAccount(String accountDid, int limit) {
        return jdbcClient
                .sql("SELECT " + CharacterDirectoryRows.ALL_COLUMNS + " FROM " + CharacterDirectoryRows.TABLE
                        + " WHERE " + CharacterDirectoryRows.ACCOUNT_DID + " = :accountDid"
                        + " ORDER BY " + CharacterDirectoryRows.SLOT + " LIMIT :limit")
                .param("accountDid", accountDid)
                .param("limit", limit)
                .query(CharacterDirectoryRows.MAPPER)
                .list();
    }
}
