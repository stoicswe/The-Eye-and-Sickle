package io.github.stoicswe.eyeandsickle.server.economy.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The money-and-heat projection of {@code players}, against a real PostgreSQL. The reads are simple; the
 * behaviour worth proving on a real database is the version-checked write (the lost-update guard) and
 * that a multi-account lock returns only the DIDs that resolve to a local player — the case the ledger's
 * sink/source handling turns on.
 */
class AccountRepositoryIT extends PostgresIntegrationTestBase {

    private static final String ALICE = "did:plc:alice000000000000000000";
    private static final String BOB = "did:plc:bob00000000000000000000";
    private static final String NOT_LOCAL = "did:plc:remote00000000000000000";

    private final AccountRepository repository = new AccountRepository(jdbcClient());

    @Test
    @DisplayName("findByDid reads back the balance and personal heat, and misses an unknown DID")
    void findByDid() {
        insertPlayer(ALICE, 4_200L, "37.5000");

        Optional<Account> found = repository.findByDid(ALICE);
        assertThat(found).isPresent();
        assertThat(found.get().balance()).isEqualTo(Ethecoin.ofMinorUnits(4_200));
        assertThat(found.get().personalHeat()).isEqualByComparingTo(new java.math.BigDecimal("37.5000"));
        assertThat(found.get().did()).isEqualTo(ALICE);
        assertThat(found.get().rowVersion()).isZero();

        assertThat(repository.findByDid("did:plc:nobody00000000000000000")).isEmpty();
    }

    @Test
    @DisplayName("lockForUpdate returns only the DIDs that resolve to a local player")
    void lockForUpdateSkipsNonLocalDids() {
        insertPlayer(ALICE, 100L, "0");
        insertPlayer(BOB, 200L, "0");

        // FOR UPDATE holds the lock to commit, so it must run inside a transaction.
        List<Account> locked =
                transactions().execute(status -> repository.lockForUpdate(List.of(ALICE, NOT_LOCAL, BOB)));

        // The remote DID has no row here and simply is not returned — an NPC or off-server counterparty
        // has no balance to lock.
        assertThat(locked).extracting(Account::did).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    @DisplayName("an empty DID set locks nothing without touching the database")
    void lockForUpdateEmpty() {
        assertThat(repository.lockForUpdate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a version-checked write applies against the current version and bumps it")
    void writeBalanceApplies() {
        insertPlayer(ALICE, 1_000L, "0");
        Account before = repository.findByDid(ALICE).orElseThrow();

        transactions()
                .executeWithoutResult(status ->
                        repository.writeBalance(before.playerId(), Ethecoin.ofMinorUnits(1_500), before.rowVersion()));

        Account after = repository.findByDid(ALICE).orElseThrow();
        assertThat(after.balance()).isEqualTo(Ethecoin.ofMinorUnits(1_500));
        assertThat(after.rowVersion()).isEqualTo(before.rowVersion() + 1);
    }

    @Test
    @DisplayName("a write against a stale version matches nothing and is reported as a conflict")
    void writeBalanceStaleVersionConflicts() {
        insertPlayer(ALICE, 1_000L, "0");
        UUID playerId = repository.findByDid(ALICE).orElseThrow().playerId();

        // First writer moves the version from 0 to 1.
        transactions()
                .executeWithoutResult(status -> repository.writeBalance(playerId, Ethecoin.ofMinorUnits(1_500), 0L));

        // Second writer still believes it holds version 0 — the classic lost update, which here would be
        // a player spending the same ethecoin twice. It must be refused, not silently dropped.
        assertThatThrownBy(() -> transactions()
                        .executeWithoutResult(
                                status -> repository.writeBalance(playerId, Ethecoin.ofMinorUnits(9_000), 0L)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findByDid(ALICE).orElseThrow().balance()).isEqualTo(Ethecoin.ofMinorUnits(1_500));
    }

    private void insertPlayer(String did, long balanceMinor, String heat) {
        jdbcClient()
                .sql("""
                        INSERT INTO players (player_id, did, handle, ethecoin_balance_ec_minor, personal_heat)
                        VALUES (:id, :did, 'operator', :balance, CAST(:heat AS numeric))
                        """)
                .param("id", UUID.randomUUID())
                .param("did", did)
                .param("balance", balanceMinor)
                .param("heat", heat)
                .update();
    }
}
