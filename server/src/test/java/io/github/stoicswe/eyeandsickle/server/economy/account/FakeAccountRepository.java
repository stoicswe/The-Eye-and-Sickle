package io.github.stoicswe.eyeandsickle.server.economy.account;

import static org.mockito.Mockito.mock;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A hand-written, in-memory stand-in for {@link AccountRepository}, so the pure-logic services
 * ({@code GateEvaluator}, {@code LedgerService}) can be exercised with no database and no container.
 *
 * <p>It lives in the same package as {@link AccountRepository} on purpose: that repository's
 * constructor is package-private, so a fake can only extend it from here. The {@code JdbcClient} the
 * superclass demands is a Mockito stub that is never touched — every method that would reach SQL is
 * overridden to read and write this fake's own maps instead.
 *
 * <p>It keys on the <strong>character DID</strong>, exactly as the real repository now does: two
 * characters of one account are two distinct entries with two distinct balances. Only
 * {@link #findByCharacter(CharacterDid)}, {@link #lockForUpdate(Collection)} and
 * {@link #writeBalance(UUID, Ethecoin, long)} are overridden; the inherited
 * {@link #findByCharacterDid(String)} parses and then calls the overridden {@code findByCharacter}, so it
 * needs no override of its own.
 *
 * <p>It deliberately mirrors two behaviours the real repository has that the services depend on: a
 * balance write is guarded by the {@code row_version} it was read at (a stale write throws
 * {@link OptimisticLockingFailureException}, exactly as {@code Mutations.requireUpdated} would), and
 * {@link #lockForUpdate(Collection)} returns only the characters that resolve to a local account — an NPC
 * or remote counterparty simply is not there, which is the case the ledger's sink/source logic turns on.
 */
public final class FakeAccountRepository extends AccountRepository {

    private final Map<UUID, Account> byId = new LinkedHashMap<>();
    private final Map<String, UUID> characterDidToId = new LinkedHashMap<>();

    /** Every {@code (playerId, newBalance, expectedVersion)} write, in order — for "reads move nothing". */
    public final List<BalanceWrite> writes = new ArrayList<>();

    /** How many times {@link #findByCharacter(CharacterDid)} was called — for "the account is read once". */
    public int findByCharacterCalls;

    /** How many times {@link #lockForUpdate(Collection)} was called. */
    public int lockCalls;

    public FakeAccountRepository() {
        super(mock(JdbcClient.class));
    }

    /** A recorded balance write. */
    public record BalanceWrite(UUID playerId, Ethecoin newBalance, long expectedRowVersion) {}

    /** Adds or replaces a character. */
    public FakeAccountRepository with(Account account) {
        Objects.requireNonNull(account, "account");
        byId.put(account.playerId(), account);
        if (account.characterDid() != null) {
            characterDidToId.put(account.characterDid().value(), account.playerId());
        }
        return this;
    }

    @Override
    public Optional<Account> findByCharacter(CharacterDid character) {
        Objects.requireNonNull(character, "character");
        findByCharacterCalls++;
        return currentByCharacter(character.value());
    }

    @Override
    public List<Account> lockForUpdate(Collection<CharacterDid> characters) {
        Objects.requireNonNull(characters, "characters");
        lockCalls++;
        // Mirror the real repository: only characters that resolve to a local account come back, ordered by
        // player_id, and each at most once even if the caller named it twice.
        return characters.stream()
                .map(CharacterDid::value)
                .map(characterDidToId::get)
                .filter(Objects::nonNull)
                .distinct()
                .map(byId::get)
                .sorted(Comparator.comparing(a -> a.playerId().toString()))
                .toList();
    }

    @Override
    public void writeBalance(UUID playerId, Ethecoin newBalance, long expectedRowVersion) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(newBalance, "newBalance");
        writes.add(new BalanceWrite(playerId, newBalance, expectedRowVersion));
        Account current = byId.get(playerId);
        if (current == null) {
            throw new IllegalStateException("no fake account " + playerId);
        }
        if (current.rowVersion() != expectedRowVersion) {
            // The lost-update guard: a write against a version another writer already moved past matches
            // no row, which the real repository turns into this exception via Mutations.requireUpdated.
            throw new OptimisticLockingFailureException("stale row_version for " + playerId);
        }
        byId.put(
                playerId,
                new Account(
                        current.playerId(),
                        current.accountDid(),
                        current.slot(),
                        newBalance,
                        current.personalHeat(),
                        current.rowVersion() + 1));
    }

    /** The account currently stored for a character DID string, for a post-condition assertion. */
    public Optional<Account> currentByCharacter(String characterDid) {
        UUID id = characterDidToId.get(characterDid);
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** The current balance stored for a character DID string. */
    public Ethecoin balanceOf(String characterDid) {
        return currentByCharacter(characterDid).orElseThrow().balance();
    }
}
