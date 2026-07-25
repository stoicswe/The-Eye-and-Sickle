package io.github.stoicswe.eyeandsickle.server.economy.account;

import static org.mockito.Mockito.mock;

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
 * <p>It deliberately mirrors two behaviours the real repository has that the services depend on: a
 * balance write is guarded by the {@code row_version} it was read at (a stale write throws
 * {@link OptimisticLockingFailureException}, exactly as {@code Mutations.requireUpdated} would), and
 * {@link #lockForUpdate(Collection)} returns only the DIDs that resolve to a local account — an NPC or
 * remote counterparty simply is not there, which is the case the ledger's sink/source logic turns on.
 */
public final class FakeAccountRepository extends AccountRepository {

    private final Map<UUID, Account> byId = new LinkedHashMap<>();
    private final Map<String, UUID> didToId = new LinkedHashMap<>();

    /** Every {@code (playerId, newBalance, expectedVersion)} write, in order — for "reads move nothing". */
    public final List<BalanceWrite> writes = new ArrayList<>();

    /** How many times {@link #findByDid(String)} was called — for "the account is read once". */
    public int findByDidCalls;

    /** How many times {@link #lockForUpdate(Collection)} was called. */
    public int lockCalls;

    public FakeAccountRepository() {
        super(mock(JdbcClient.class));
    }

    /** A recorded balance write. */
    public record BalanceWrite(UUID playerId, Ethecoin newBalance, long expectedRowVersion) {}

    /** Adds or replaces an account. */
    public FakeAccountRepository with(Account account) {
        Objects.requireNonNull(account, "account");
        byId.put(account.playerId(), account);
        if (account.did() != null) {
            didToId.put(account.did(), account.playerId());
        }
        return this;
    }

    @Override
    public Optional<Account> findByDid(String did) {
        Objects.requireNonNull(did, "did");
        findByDidCalls++;
        return currentByDid(did);
    }

    @Override
    public List<Account> lockForUpdate(Collection<String> dids) {
        Objects.requireNonNull(dids, "dids");
        lockCalls++;
        // Mirror the real repository: only DIDs that resolve to a local account come back, ordered by
        // player_id, and each at most once even if the caller named it twice.
        return dids.stream()
                .map(didToId::get)
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
                        current.did(),
                        newBalance,
                        current.personalHeat(),
                        current.rowVersion() + 1));
    }

    /** The account currently stored for a DID, for a post-condition assertion. */
    public Optional<Account> currentByDid(String did) {
        UUID id = didToId.get(did);
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** The current balance stored for a DID. */
    public Ethecoin balanceOf(String did) {
        return currentByDid(did).orElseThrow().balance();
    }
}
