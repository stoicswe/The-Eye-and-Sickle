package io.github.stoicswe.eyeandsickle.server.economy.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The money-and-heat projection of {@code players}, against a real PostgreSQL, keyed on the character.
 *
 * <p>The behaviour worth proving on a real database: a character DID resolves to the single {@code
 * (did, slot)} row — so an account holding more than one character no longer makes the lookup throw (09
 * §9's bug), and two characters of one account keep separate balances — plus the version-checked write
 * (the lost-update guard) and a multi-character lock returning only the characters that resolve to a
 * local player.
 */
class AccountRepositoryIT extends PostgresIntegrationTestBase {

    private static final String ALICE_ACCOUNT = "did:plc:alice000000000000000000";
    private static final String BOB_ACCOUNT = "did:plc:bob00000000000000000000";

    private static final CharacterDid ALICE = new CharacterDid(ALICE_ACCOUNT, 1);
    private static final CharacterDid ALICE_SLOT_2 = new CharacterDid(ALICE_ACCOUNT, 2);
    private static final CharacterDid BOB = new CharacterDid(BOB_ACCOUNT, 1);
    private static final CharacterDid NOT_LOCAL = new CharacterDid("did:plc:remote00000000000000000", 1);

    private final AccountRepository repository = new AccountRepository(jdbcClient());

    @Test
    @DisplayName("findByCharacter reads back the balance and personal heat, and misses an unknown character")
    void findByCharacter() {
        insertPlayer(ALICE_ACCOUNT, 1, 4_200L, "37.5000");

        Optional<Account> found = repository.findByCharacter(ALICE);
        assertThat(found).isPresent();
        assertThat(found.get().balance()).isEqualTo(Ethecoin.ofMinorUnits(4_200));
        assertThat(found.get().personalHeat()).isEqualByComparingTo(new java.math.BigDecimal("37.5000"));
        assertThat(found.get().accountDid()).isEqualTo(ALICE_ACCOUNT);
        assertThat(found.get().slot()).isEqualTo(1);
        assertThat(found.get().characterDid()).isEqualTo(ALICE);
        assertThat(found.get().rowVersion()).isZero();

        // Same account, a slot with no character yet: not found, and — crucially — not an error.
        assertThat(repository.findByCharacter(ALICE_SLOT_2)).isEmpty();
        assertThat(repository.findByCharacter(new CharacterDid("did:plc:nobody00000000000000000", 1)))
                .isEmpty();
    }

    @Test
    @DisplayName("two characters of ONE account resolve to SEPARATE balances — the >1-character lookup never throws")
    void twoCharactersOfOneAccountAreSeparate() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L, "10");
        insertPlayer(ALICE_ACCOUNT, 2, 250L, "90");

        // The old findByDid(account) matched BOTH rows and threw on .optional(); naming the slot resolves
        // exactly one character, so each of the account's two characters has its own balance and heat.
        Account first = repository.findByCharacter(ALICE).orElseThrow();
        Account second = repository.findByCharacter(ALICE_SLOT_2).orElseThrow();

        assertThat(first.balance()).isEqualTo(Ethecoin.ofMinorUnits(1_000));
        assertThat(second.balance()).isEqualTo(Ethecoin.ofMinorUnits(250));
        assertThat(first.personalHeat()).isEqualByComparingTo(new java.math.BigDecimal("10"));
        assertThat(second.personalHeat()).isEqualByComparingTo(new java.math.BigDecimal("90"));
        assertThat(first.playerId()).isNotEqualTo(second.playerId());
    }

    @Test
    @DisplayName("lockForUpdate returns only the characters that resolve to a local player")
    void lockForUpdateSkipsNonLocalCharacters() {
        insertPlayer(ALICE_ACCOUNT, 1, 100L, "0");
        insertPlayer(BOB_ACCOUNT, 1, 200L, "0");

        // FOR UPDATE holds the lock to commit, so it must run inside a transaction.
        List<Account> locked =
                transactions().execute(status -> repository.lockForUpdate(List.of(ALICE, NOT_LOCAL, BOB)));

        // The remote character has no row here and simply is not returned — an NPC or off-server
        // counterparty has no balance to lock.
        assertThat(locked).extracting(Account::characterDid).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    @DisplayName("lockForUpdate locks the named character, not every character of its account")
    void lockForUpdateIsPerCharacter() {
        insertPlayer(ALICE_ACCOUNT, 1, 100L, "0");
        insertPlayer(ALICE_ACCOUNT, 2, 999L, "0");

        List<Account> locked = transactions().execute(status -> repository.lockForUpdate(List.of(ALICE)));

        // Only slot 1 is locked; slot 2 of the same account is a separate character and is untouched.
        assertThat(locked).extracting(Account::characterDid).containsExactly(ALICE);
        assertThat(locked)
                .singleElement()
                .satisfies(a -> assertThat(a.balance()).isEqualTo(Ethecoin.ofMinorUnits(100)));
    }

    @Test
    @DisplayName("an empty character set locks nothing without touching the database")
    void lockForUpdateEmpty() {
        assertThat(repository.lockForUpdate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a version-checked write applies against the current version and bumps it")
    void writeBalanceApplies() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L, "0");
        Account before = repository.findByCharacter(ALICE).orElseThrow();

        transactions()
                .executeWithoutResult(status ->
                        repository.writeBalance(before.playerId(), Ethecoin.ofMinorUnits(1_500), before.rowVersion()));

        Account after = repository.findByCharacter(ALICE).orElseThrow();
        assertThat(after.balance()).isEqualTo(Ethecoin.ofMinorUnits(1_500));
        assertThat(after.rowVersion()).isEqualTo(before.rowVersion() + 1);
    }

    @Test
    @DisplayName("a write against a stale version matches nothing and is reported as a conflict")
    void writeBalanceStaleVersionConflicts() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L, "0");
        UUID playerId = repository.findByCharacter(ALICE).orElseThrow().playerId();

        // First writer moves the version from 0 to 1.
        transactions()
                .executeWithoutResult(status -> repository.writeBalance(playerId, Ethecoin.ofMinorUnits(1_500), 0L));

        // Second writer still believes it holds version 0 — the classic lost update, which here would be
        // a player spending the same ethecoin twice. It must be refused, not silently dropped.
        assertThatThrownBy(() -> transactions()
                        .executeWithoutResult(
                                status -> repository.writeBalance(playerId, Ethecoin.ofMinorUnits(9_000), 0L)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findByCharacter(ALICE).orElseThrow().balance()).isEqualTo(Ethecoin.ofMinorUnits(1_500));
    }

    private void insertPlayer(String accountDid, int slot, long balanceMinor, String heat) {
        jdbcClient()
                .sql("""
                        INSERT INTO players (player_id, did, slot, handle, ethecoin_balance_ec_minor, personal_heat)
                        VALUES (:id, :did, :slot, 'operator', :balance, CAST(:heat AS numeric))
                        """)
                .param("id", UUID.randomUUID())
                .param("did", accountDid)
                .param("slot", slot)
                .param("balance", balanceMinor)
                .param("heat", heat)
                .update();
    }
}
