package io.github.stoicswe.eyeandsickle.server.economy.account;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes the money-and-heat state on {@code players}, keyed on the <strong>character</strong>.
 *
 * <h2>The character is the money holder, not the account</h2>
 *
 * A DID is now an <em>account</em> that may hold several <em>characters</em>
 * ({@code docs/architecture/09-player-state-portability.md} §1), each its own {@code players} row with its
 * own balance. The old {@code findByDid(did)} keyed on the account DID: correct while one DID meant one
 * character, but once an account holds two it matched two rows and its {@code .optional()} threw
 * ({@code IncorrectResultSizeDataAccessException}) — 09 §9's core bug. Every lookup here now resolves a
 * {@link CharacterDid} to the single {@code (did, slot)} row, so it returns exactly one character and the
 * two characters of one account keep separate balances.
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
 * {@link #findByCharacter(CharacterDid)} is a display/decision snapshot. {@link #lockForUpdate(Collection)}
 * is for a decision that must hold across a transfer: it takes {@code SELECT ... FOR UPDATE} on the
 * character rows, <strong>ordered by {@code player_id}</strong>, so two transfers touching the same two
 * characters in opposite directions serialise instead of deadlocking. Consistent lock order is the whole
 * reason that method takes a collection and sorts it rather than locking one row at a time.
 */
@Repository
public class AccountRepository {

    private final JdbcClient jdbcClient;

    AccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    private static final String SELECT = """
            SELECT player_id, did, slot, ethecoin_balance_ec_minor, personal_heat, row_version
              FROM players
            """;

    /**
     * A snapshot of one character by its character DID.
     *
     * <p>Resolves the derived character identity to its underlying {@code (did, slot)} row and reads the
     * single <em>active</em> character there. {@code (did, slot)} is unique in the schema, so this returns
     * at most one row — the {@code .optional()} that once threw for an account with more than one character
     * cannot throw here, because a slot names exactly one character.
     *
     * @param character the character to read
     * @return the character's money-and-heat snapshot, or empty if no active local character occupies that
     *     account-and-slot
     */
    public Optional<Account> findByCharacter(CharacterDid character) {
        Objects.requireNonNull(character, "character");
        return jdbcClient
                .sql(SELECT + " WHERE did = :accountDid AND slot = :slot AND status = 'active'")
                .param("accountDid", character.accountDid())
                .param("slot", character.slot())
                .query(AccountRows.MAPPER)
                .optional();
    }

    /**
     * A snapshot of one character by its character-DID <em>string</em> — the spelling stored in the
     * ledger and carried on a session.
     *
     * <p>A string that is not a well-formed character DID (an NPC or system counterparty, or a raw account
     * DID) resolves to no local character and yields empty, exactly as an unknown character does: the
     * caller decides whether "not a local character" is a not-found error or simply a non-local
     * counterparty.
     *
     * @param characterDid the {@code did:eyeandsickle:<slot>:<accountDid>} string
     * @return the character's snapshot, or empty if the string is not a character DID or names no active
     *     local character
     */
    public Optional<Account> findByCharacterDid(String characterDid) {
        Objects.requireNonNull(characterDid, "characterDid");
        return CharacterDid.parse(characterDid).flatMap(this::findByCharacter);
    }

    /**
     * Locks the given characters and returns them, for a decision that spans more than one.
     *
     * <p>Must be called inside a transaction — {@code FOR UPDATE} holds the lock until commit. Characters
     * that name no active local player are simply absent from the result (an NPC or remote counterparty has
     * no row here and no balance to lock); the caller decides what that means for its operation.
     *
     * @param characters the characters to lock; the argument's ordering does not matter, the SQL orders by
     *     {@code player_id}
     * @return the locked characters, one per character that resolved to an active local player
     */
    public List<Account> lockForUpdate(Collection<CharacterDid> characters) {
        Objects.requireNonNull(characters, "characters");
        List<CharacterDid> distinct = characters.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        // A composite (did, slot) match, spelled out as an OR of pairs so the two smallint/text columns
        // bind through plain named params — no composite-IN tuple binding to depend on. ORDER BY player_id,
        // then FOR UPDATE: a global, consistent lock order across every transfer, so cross-character
        // operations cannot deadlock by grabbing the two rows in opposite orders.
        StringBuilder predicate = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            if (i > 0) {
                predicate.append(" OR ");
            }
            predicate
                    .append("(did = :did")
                    .append(i)
                    .append(" AND slot = :slot")
                    .append(i)
                    .append(")");
            params.put("did" + i, distinct.get(i).accountDid());
            params.put("slot" + i, distinct.get(i).slot());
        }
        return jdbcClient
                .sql(SELECT + " WHERE (" + predicate + ") AND status = 'active' ORDER BY player_id FOR UPDATE")
                .params(params)
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
     * <p>The write targets {@code player_id} — the character's local row key — not the character DID, so a
     * character DID never needs to be spelled into an UPDATE. The database's own
     * {@code ck_players_balance_non_negative} is the backstop: a caller that computed a negative balance
     * (an unchecked overdraft) is refused by the schema even if it reached here, because on an
     * authoritative server the database is the last line of defence.
     *
     * @param playerId the character row to write
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
