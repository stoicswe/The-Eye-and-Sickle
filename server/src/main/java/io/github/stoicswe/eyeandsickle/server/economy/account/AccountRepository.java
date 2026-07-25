package io.github.stoicswe.eyeandsickle.server.economy.account;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes the money-and-heat state on {@code players}.
 *
 * <h2>Authority, not convenience</h2>
 *
 * On an authoritative server (Invariant I14) the balance a client shows is a rumour; the balance in
 * this table is the fact. Every write here is version-checked so a concurrent one cannot be lost — two
 * requests each reading a balance of 100 and each writing 40 would hand out an item for free, and the
 * {@code row_version} guard is what turns that race into a retryable conflict ({@code
 * persistence/Mutations}).
 *
 * <h2>Reads that decide, and reads that only display</h2>
 *
 * {@link #findByDid(String)} is a display/decision snapshot. {@link #lockForUpdate(Collection)} is for
 * a decision that must hold across a transfer: it takes {@code SELECT ... FOR UPDATE} on the player
 * rows, <strong>ordered by {@code player_id}</strong>, so two transfers touching the same two accounts
 * in opposite directions serialise instead of deadlocking. Consistent lock order is the whole reason
 * this method takes a collection and sorts it rather than locking one row at a time.
 */
@Repository
public class AccountRepository {

    private final JdbcClient jdbcClient;

    AccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    private static final String SELECT = """
            SELECT player_id, did, ethecoin_balance_ec_minor, personal_heat, row_version
              FROM players
            """;

    /**
     * A snapshot of an account by DID.
     *
     * @param did the player's DID
     * @return the account, or empty if no local player has that DID
     */
    public Optional<Account> findByDid(String did) {
        Objects.requireNonNull(did, "did");
        return jdbcClient
                .sql(SELECT + " WHERE did = :did")
                .param("did", did)
                .query(AccountRows.MAPPER)
                .optional();
    }

    /**
     * Locks the accounts for the given DIDs and returns them, for a decision that spans more than one.
     *
     * <p>Must be called inside a transaction — {@code FOR UPDATE} holds the lock until commit. DIDs
     * that name no local player are simply absent from the result (an NPC or remote counterparty has no
     * row here and no balance to lock); the caller decides what that means for its operation.
     *
     * @param dids the DIDs to lock; ordering of the argument does not matter, the SQL orders by
     *     {@code player_id}
     * @return the locked accounts, one per DID that resolved to a local player
     */
    public List<Account> lockForUpdate(Collection<String> dids) {
        Objects.requireNonNull(dids, "dids");
        if (dids.isEmpty()) {
            return List.of();
        }
        // ORDER BY player_id, then FOR UPDATE: a global, consistent lock order across every transfer,
        // so cross-account operations (Invariant I6 makes those routine elsewhere; transfers make them
        // routine here) cannot deadlock by grabbing the two rows in opposite orders.
        return jdbcClient
                .sql(SELECT + " WHERE did IN (:dids) ORDER BY player_id FOR UPDATE")
                .param("dids", List.copyOf(dids))
                .query(AccountRows.MAPPER)
                .list();
    }

    /**
     * Writes a new balance, conditional on the version the caller read.
     *
     * <p>The caller has already decided the new balance (a credit, a checked debit) against a snapshot;
     * this applies it only if that snapshot is still current. A zero affected-row count means another
     * writer moved first, and {@link Mutations#requireUpdated(int, String, Object)} turns that into an
     * {@code OptimisticLockingFailureException} rather than a silently lost write.
     *
     * <p>The database's own {@code ck_players_balance_non_negative} is the backstop: a caller that
     * computed a negative balance (an unchecked overdraft) is refused by the schema even if it reached
     * here, because on an authoritative server the database is the last line of defence.
     *
     * @param playerId the account to write
     * @param newBalance the balance to store
     * @param expectedRowVersion the version the new balance was computed against
     * @throws org.springframework.dao.OptimisticLockingFailureException if the version has moved on
     */
    public void writeBalance(UUID playerId, Ethecoin newBalance, long expectedRowVersion) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(newBalance, "newBalance");
        int affected = jdbcClient
                .sql("""
                        UPDATE players
                           SET ethecoin_balance_ec_minor = :balance,
                               row_version = row_version + 1
                         WHERE player_id = :playerId
                           AND row_version = :expectedVersion
                        """)
                .param("balance", EconomyColumns.ethecoinValue(AccountRows.BALANCE, newBalance))
                .param("playerId", playerId)
                .param("expectedVersion", expectedRowVersion)
                .update();
        Mutations.requireUpdated(affected, "players", playerId);
    }
}
