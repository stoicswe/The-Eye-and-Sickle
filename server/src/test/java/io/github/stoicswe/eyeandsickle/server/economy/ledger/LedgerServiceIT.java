package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.economy.account.Account;
import io.github.stoicswe.eyeandsickle.server.economy.account.AccountRepository;
import io.github.stoicswe.eyeandsickle.server.economy.account.AccountRepositoryTestSupport;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The money layer end-to-end, on a real PostgreSQL, wiring the real {@link AccountRepository} and
 * {@link LedgerRepository} together. This is where the central economic invariant is proved against the
 * database rather than a fake: {@code mint} is the only operation that grows total supply, and a
 * {@code transfer} between two local characters conserves it exactly — a crack seizure or a raid moves
 * ethecoin that already exists, it never mints ({@code docs/design/03-economy.md} §5 rule 3).
 *
 * <p>Every local party is a <strong>character</strong>: the counterparty strings are character DIDs
 * ({@code did:eyeandsickle:<slot>:<accountDid>}), which the {@code from_did}/{@code to_did} columns accept
 * as-is (they pass the schema's {@code is_did} shape). {@link #transferBetweenTwoCharactersOfOneAccount()}
 * is the per-character headline: two save games of one account, two balances, a transfer between them.
 *
 * <p>Service calls run inside a {@link org.springframework.transaction.support.TransactionTemplate}, so
 * the {@code SELECT ... FOR UPDATE} row locks and the balance/ledger atomicity that {@code @Transactional}
 * would provide in production are present here too.
 */
class LedgerServiceIT extends PostgresIntegrationTestBase {

    private static final String ALICE_ACCOUNT = "did:plc:alice000000000000000000";
    private static final String BOB_ACCOUNT = "did:plc:bob00000000000000000000";

    private static final String ALICE = CharacterDid.of(ALICE_ACCOUNT, 1);
    private static final String ALICE_SLOT_2 = CharacterDid.of(ALICE_ACCOUNT, 2);
    private static final String BOB = CharacterDid.of(BOB_ACCOUNT, 1);
    private static final String NPC_HOST = "did:npc:crackedhost";
    private static final String NPC_VENDOR = "did:npc:blackmarket";

    private final AccountRepository accounts = AccountRepositoryTestSupport.real(jdbcClient());
    private final LedgerRepository ledger = new LedgerRepository(jdbcClient());
    private final LedgerService service = new LedgerService(accounts, ledger);

    @Test
    @DisplayName("mint credits the balance and writes the ledger row atomically")
    void mintCreditsAndRecords() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L);

        transactions().executeWithoutResult(status -> service.mint(ALICE, Ethecoin.ofMinorUnits(250), null));

        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(1_250));
        assertThat(ledger.query(LedgerQuery.forParticipant(ALICE, LedgerQuery.Direction.RECEIVED, 10), null))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.type()).isEqualTo(LedgerEntryType.MINING_REWARD);
                    assertThat(row.toDid()).isEqualTo(ALICE);
                    assertThat(row.fromDid()).isNull();
                });
    }

    @Test
    @DisplayName("a transfer between two local characters conserves total supply — it moves, it never mints")
    void transferConservesSupply() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L);
        insertPlayer(BOB_ACCOUNT, 1, 400L);
        long before = balanceOf(ALICE).minorUnits() + balanceOf(BOB).minorUnits();

        transactions()
                .executeWithoutResult(status ->
                        service.transfer(ALICE, BOB, Ethecoin.ofMinorUnits(300), LedgerEntryType.TRADE, true, null));

        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(700));
        assertThat(balanceOf(BOB)).isEqualTo(Ethecoin.ofMinorUnits(700));
        assertThat(balanceOf(ALICE).minorUnits() + balanceOf(BOB).minorUnits()).isEqualTo(before);

        // Queryable from both counterparties (docs/design/01 §2.2).
        assertThat(ledger.query(LedgerQuery.forParticipant(ALICE, LedgerQuery.Direction.SENT, 10), null))
                .hasSize(1);
        assertThat(ledger.query(LedgerQuery.forParticipant(BOB, LedgerQuery.Direction.RECEIVED, 10), null))
                .hasSize(1);
    }

    @Test
    @DisplayName("a transfer between two characters of ONE account moves ethecoin between their separate balances")
    void transferBetweenTwoCharactersOfOneAccount() {
        // Same account DID, two slots: two characters, two balances (09 §9).
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L);
        insertPlayer(ALICE_ACCOUNT, 2, 0L);

        transactions()
                .executeWithoutResult(status -> service.transfer(
                        ALICE, ALICE_SLOT_2, Ethecoin.ofMinorUnits(300), LedgerEntryType.TRADE, true, null));

        // The money left one character and arrived at the other — proof they are not one shared balance.
        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(700));
        assertThat(balanceOf(ALICE_SLOT_2)).isEqualTo(Ethecoin.ofMinorUnits(300));

        // The ledger recorded the two character DIDs as the parties — and accepted them into the
        // is_did-constrained from_did/to_did columns with no schema change.
        assertThat(ledger.query(LedgerQuery.forParticipant(ALICE_SLOT_2, LedgerQuery.Direction.RECEIVED, 10), null))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.fromDid()).isEqualTo(ALICE);
                    assertThat(row.toDid()).isEqualTo(ALICE_SLOT_2);
                });
    }

    @Test
    @DisplayName("a crack seizure from a non-local host credits the local payee as a transfer, not a mint")
    void crackSeizureIsATransferNotAMint() {
        insertPlayer(BOB_ACCOUNT, 1, 100L);

        transactions()
                .executeWithoutResult(status -> service.transfer(
                        NPC_HOST, BOB, Ethecoin.ofMinorUnits(80), LedgerEntryType.CRACK_SEIZURE, true, null));

        assertThat(balanceOf(BOB)).isEqualTo(Ethecoin.ofMinorUnits(180));
        assertThat(ledger.query(LedgerQuery.forParticipant(BOB, LedgerQuery.Direction.RECEIVED, 10), null))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.type()).isEqualTo(LedgerEntryType.CRACK_SEIZURE);
                    assertThat(row.type().isFaucet()).isFalse();
                    assertThat(row.fromDid()).isEqualTo(NPC_HOST);
                });
    }

    @Test
    @DisplayName("the faucet type is refused by transfer — minting has exactly one door")
    void transferRefusesFaucetType() {
        insertPlayer(ALICE_ACCOUNT, 1, 1_000L);
        insertPlayer(BOB_ACCOUNT, 1, 0L);

        assertThatThrownBy(() -> transactions()
                        .executeWithoutResult(status -> service.transfer(
                                ALICE, BOB, Ethecoin.ofMinorUnits(50), LedgerEntryType.MINING_REWARD, true, null)))
                .isInstanceOf(IllegalArgumentException.class);

        // Nothing moved, nothing recorded.
        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(1_000));
        assertThat(ledger.query(LedgerQuery.recent(10), null)).isEmpty();
    }

    @Test
    @DisplayName("an unaffordable transfer is refused and leaves no trace — no debit, no ledger row")
    void insufficientFundsRollsBack() {
        insertPlayer(ALICE_ACCOUNT, 1, 100L);
        insertPlayer(BOB_ACCOUNT, 1, 0L);

        assertThatThrownBy(() -> transactions()
                        .executeWithoutResult(status -> service.transfer(
                                ALICE, BOB, Ethecoin.ofMinorUnits(101), LedgerEntryType.TRADE, true, null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(100));
        assertThat(balanceOf(BOB)).isEqualTo(Ethecoin.ZERO);
        assertThat(ledger.query(LedgerQuery.recent(10), null)).isEmpty();
    }

    @Test
    @DisplayName("a purchase from a non-local vendor is a sink: the buyer is debited and the ethecoin leaves")
    void purchaseToNpcVendorIsASink() {
        insertPlayer(ALICE_ACCOUNT, 1, 500L);

        transactions()
                .executeWithoutResult(status -> service.transfer(
                        ALICE, NPC_VENDOR, Ethecoin.ofMinorUnits(120), LedgerEntryType.PURCHASE, true, null));

        assertThat(balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(380));
        assertThat(ledger.query(LedgerQuery.recent(10), null))
                .singleElement()
                .satisfies(row -> assertThat(row.type()).isEqualTo(LedgerEntryType.PURCHASE));
    }

    @Test
    @DisplayName("a Dead Drop is recorded, hidden from an anonymous investigator, visible to a counterparty")
    void deadDropVisibility() {
        insertPlayer(ALICE_ACCOUNT, 1, 500L);
        insertPlayer(BOB_ACCOUNT, 1, 0L);

        transactions()
                .executeWithoutResult(status ->
                        service.transfer(ALICE, BOB, Ethecoin.ofMinorUnits(200), LedgerEntryType.TRADE, false, null));

        // The row exists — laundering leaves something to find — but only its counterparties see it.
        assertThat(service.ledger(LedgerQuery.recent(10), null)).isEmpty();
        assertThat(service.ledger(LedgerQuery.recent(10), ALICE)).hasSize(1);
        assertThat(service.ledger(LedgerQuery.recent(10), BOB)).hasSize(1);
    }

    private Ethecoin balanceOf(String characterDid) {
        return accounts.findByCharacterDid(characterDid).map(Account::balance).orElseThrow();
    }

    private void insertPlayer(String accountDid, int slot, long balanceMinor) {
        jdbcClient()
                .sql("""
                        INSERT INTO players (player_id, did, slot, handle, ethecoin_balance_ec_minor)
                        VALUES (:id, :did, :slot, 'operator', :balance)
                        """)
                .param("id", UUID.randomUUID())
                .param("did", accountDid)
                .param("slot", slot)
                .param("balance", balanceMinor)
                .update();
    }
}
