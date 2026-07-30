package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.economy.account.Account;
import io.github.stoicswe.eyeandsickle.server.economy.account.FakeAccountRepository;
import io.github.stoicswe.eyeandsickle.server.economy.account.UnknownPlayerException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The money layer, and the wall between its two operations ({@code docs/design/03-economy.md} §5 rule
 * 3): {@code mint} is the one narrow path that creates ethecoin, and {@code transfer} moves ethecoin
 * that already exists and physically refuses the faucet type. A crack seizure that could mint would
 * inflate the economy every time a miner was cracked, so the interesting tests are the refusals and the
 * conservation of supply.
 *
 * <p>Every local party is a <strong>character</strong> — the counterparty strings here are character DIDs
 * ({@code did:eyeandsickle:<slot>:<accountDid>}), not raw account DIDs, and NPC counterparties are plain
 * DIDs that are not characters at all. The separation this buys — two characters of one account with two
 * balances — is proved directly in {@link CharacterSeparation}.
 */
class LedgerServiceTest {

    private static final String ALICE_ACCOUNT = "did:plc:alice000000000000000000";
    private static final String BOB_ACCOUNT = "did:plc:bob00000000000000000000";

    // Character DIDs, not account DIDs: this is what the ledger stores and what a balance keys on.
    private static final String ALICE = CharacterDid.of(ALICE_ACCOUNT, 1);
    // A second character of Alice's SAME account — a distinct save game, a distinct balance.
    private static final String ALICE_SLOT_2 = CharacterDid.of(ALICE_ACCOUNT, 2);
    private static final String BOB = CharacterDid.of(BOB_ACCOUNT, 1);
    private static final String NPC_HOST = "did:npc:crackedhost";
    private static final String NPC_VENDOR = "did:npc:blackmarket";
    private static final String GHOST = CharacterDid.of("did:plc:ghost000000000000000000", 1);

    private final FakeAccountRepository accounts = new FakeAccountRepository();
    private final FakeLedgerRepository ledger = new FakeLedgerRepository();
    private final LedgerService service = new LedgerService(accounts, ledger);

    private static Account account(String characterDid, long balanceMinor) {
        CharacterDid character = CharacterDid.from(characterDid);
        return new Account(
                UUID.randomUUID(),
                character.accountDid(),
                character.slot(),
                Ethecoin.ofWei(java.math.BigInteger.valueOf(balanceMinor)
                        .multiply(Ethecoin.WEI_PER_ETHECOIN).divide(java.math.BigInteger.valueOf(100))),
                java.math.BigDecimal.ZERO,
                0L);
    }

    private java.math.BigInteger localSupply() {
        return accounts.balanceOf(ALICE).wei()
                .add(accounts.currentByCharacter(BOB)
                        .map(a -> a.balance().wei())
                        .orElse(java.math.BigInteger.ZERO));
    }

    // ------------------------------------------------------------------ mint (the faucet)

    @Nested
    @DisplayName("mint — the one path that creates ethecoin")
    class Mint {

        @Test
        @DisplayName("credits the recipient and writes a payerless MINING_REWARD row")
        void mintsIntoBalance() {
            accounts.with(account(ALICE, 1_000));

            LedgerTransaction row = service.mint(ALICE, Ethecoin.ofDecimal("2.5"), Map.of("block", 7));

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("12.5"));
            assertThat(ledger.appended).hasSize(1);
            assertThat(row.fromDid()).as("the faucet has no payer").isNull();
            assertThat(row.toDid()).isEqualTo(ALICE);
            assertThat(row.type()).isEqualTo(LedgerEntryType.MINING_REWARD);
            assertThat(row.type().isFaucet()).isTrue();
            assertThat(row.traceable()).isTrue();
        }

        @Test
        @DisplayName("minting to an unknown player is refused and writes nothing")
        void unknownRecipientRejected() {
            assertThatThrownBy(() -> service.mint(GHOST, Ethecoin.ofDecimal("0.1"), null))
                    .isInstanceOf(UnknownPlayerException.class);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("minting zero is refused — a zero-value row is noise on an evidence surface")
        void zeroMintRejected() {
            accounts.with(account(ALICE, 0));
            assertThatThrownBy(() -> service.mint(ALICE, Ethecoin.ZERO, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ledger.appended).isEmpty();
        }
    }

    // ------------------------------------------------------------------ transfer moves, never mints

    @Nested
    @DisplayName("transfer — moves existing ethecoin, and refuses to mint")
    class Transfer {

        @Test
        @DisplayName("the faucet type is refused outright: minting has exactly one door, and it is not transfer")
        void faucetTypeRefused() {
            accounts.with(account(ALICE, 1_000)).with(account(BOB, 0));

            assertThatThrownBy(() -> service.transfer(
                            ALICE, BOB, Ethecoin.ofDecimal("1"), LedgerEntryType.MINING_REWARD, true, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("faucet");
            assertThat(ledger.appended).isEmpty();
            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("10"));
        }

        @Test
        @DisplayName("between two local players, total supply is conserved — a transfer moves, it does not mint")
        void supplyIsConserved() {
            accounts.with(account(ALICE, 1_000)).with(account(BOB, 400));
            java.math.BigInteger before = localSupply();

            LedgerTransaction row =
                    service.transfer(ALICE, BOB, Ethecoin.ofDecimal("3"), LedgerEntryType.TRADE, true, null);

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("7"));
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ofDecimal("7"));
            assertThat(localSupply()).as("no ethecoin was created or destroyed").isEqualTo(before);
            assertThat(row.type()).isEqualTo(LedgerEntryType.TRADE);
            assertThat(row.fromDid()).isEqualTo(ALICE);
            assertThat(row.toDid()).isEqualTo(BOB);
        }

        @Test
        @DisplayName("a crack seizure from a non-local host is a transfer, not a mint")
        void crackSeizureIsATransfer() {
            accounts.with(account(BOB, 100));

            LedgerTransaction row = service.transfer(
                    NPC_HOST, BOB, Ethecoin.ofDecimal("0.8"), LedgerEntryType.CRACK_SEIZURE, true, null);

            // The buffer already existed on the host and the mining slice debited it; here only the local
            // payee is credited, and the row records a CRACK_SEIZURE — never a MINING_REWARD.
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ofDecimal("1.8"));
            assertThat(row.type()).isEqualTo(LedgerEntryType.CRACK_SEIZURE);
            assertThat(row.type().isFaucet()).isFalse();
            assertThat(row.fromDid()).isEqualTo(NPC_HOST);
            // Only one balance was written — the local payee's; the non-local host has no balance here.
            assertThat(accounts.writes).hasSize(1);
        }

        @Test
        @DisplayName("a purchase from a non-local vendor debits the buyer and lets the ethecoin leave as a sink")
        void purchaseToNpcVendorIsASink() {
            accounts.with(account(ALICE, 500));

            service.transfer(ALICE, NPC_VENDOR, Ethecoin.ofDecimal("1.2"), LedgerEntryType.PURCHASE, true, null);

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("3.8"));
            assertThat(ledger.appended).hasSize(1);
            assertThat(ledger.appended.get(0).type()).isEqualTo(LedgerEntryType.PURCHASE);
        }

        @Test
        @DisplayName("a self-directed transfer is refused")
        void selfTransferRejected() {
            accounts.with(account(ALICE, 1_000));
            assertThatThrownBy(() ->
                            service.transfer(ALICE, ALICE, Ethecoin.ofDecimal("0.01"), LedgerEntryType.TRADE, true, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("two distinct parties");
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a zero-value transfer is refused")
        void zeroTransferRejected() {
            accounts.with(account(ALICE, 1_000)).with(account(BOB, 0));
            assertThatThrownBy(() -> service.transfer(ALICE, BOB, Ethecoin.ZERO, LedgerEntryType.TRADE, true, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a local payer who cannot cover it is refused, nothing is debited, and no row is written")
        void insufficientFundsRejected() {
            accounts.with(account(ALICE, 100)).with(account(BOB, 0));

            assertThatThrownBy(() ->
                            service.transfer(ALICE, BOB, Ethecoin.ofDecimal("1.01"), LedgerEntryType.TRADE, true, null))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, insufficient -> {
                        assertThat(insufficient.did()).isEqualTo(ALICE);
                        assertThat(insufficient.balance()).isEqualTo(Ethecoin.ofDecimal("1"));
                        assertThat(insufficient.required()).isEqualTo(Ethecoin.ofDecimal("1.01"));
                    });

            // Affordability is asked BEFORE the subtraction: no half-transfer, no ledger row.
            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("1"));
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ZERO);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a transfer where neither party is local is refused — this server has no stake")
        void neitherPartyLocalRejected() {
            assertThatThrownBy(() -> service.transfer(
                            NPC_HOST, NPC_VENDOR, Ethecoin.ofDecimal("0.1"), LedgerEntryType.TRADE, true, null))
                    .isInstanceOf(UnknownPlayerException.class);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a Dead Drop is still recorded, just flagged untraceable")
        void deadDropIsRecorded() {
            accounts.with(account(ALICE, 500)).with(account(BOB, 0));

            LedgerTransaction row =
                    service.transfer(ALICE, BOB, Ethecoin.ofDecimal("2"), LedgerEntryType.TRADE, false, null);

            assertThat(row.traceable()).isFalse();
            assertThat(ledger.appended).hasSize(1);
            assertThat(ledger.appended.get(0).traceable()).isFalse();
        }
    }

    // ------------------------------------------------------------------ two characters, one account

    @Nested
    @DisplayName("two characters of one account are separate money holders (09 §9)")
    class CharacterSeparation {

        @Test
        @DisplayName("the two characters of one account have separate balances — the headline of the fix")
        void separateBalances() {
            accounts.with(account(ALICE, 1_000)).with(account(ALICE_SLOT_2, 50));

            // Same account DID, different slots: two save games, two balances. Keying on the account DID
            // (the old bug) would have made these one shared balance.
            assertThat(service.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("10"));
            assertThat(service.balanceOf(ALICE_SLOT_2)).isEqualTo(Ethecoin.ofDecimal("0.5"));
        }

        @Test
        @DisplayName("a transfer between two characters of ONE account moves ethecoin from one to the other")
        void transferBetweenOwnCharacters() {
            accounts.with(account(ALICE, 1_000)).with(account(ALICE_SLOT_2, 0));

            LedgerTransaction row = service.transfer(
                    ALICE, ALICE_SLOT_2, Ethecoin.ofDecimal("3"), LedgerEntryType.TRADE, true, null);

            // Only reachable because the two characters are distinct money holders: the debit lands on one,
            // the credit on the other, and the ledger records the character DIDs as the two parties.
            assertThat(service.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("7"));
            assertThat(service.balanceOf(ALICE_SLOT_2)).isEqualTo(Ethecoin.ofDecimal("3"));
            assertThat(row.fromDid()).isEqualTo(ALICE);
            assertThat(row.toDid()).isEqualTo(ALICE_SLOT_2);
        }
    }

    // ------------------------------------------------------------------ reads

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("balanceOf returns the materialised balance, and refuses an unknown player")
        void balanceOf() {
            accounts.with(account(ALICE, 4_200));
            assertThat(service.balanceOf(ALICE)).isEqualTo(Ethecoin.ofDecimal("42"));
            assertThatThrownBy(() -> service.balanceOf(GHOST)).isInstanceOf(UnknownPlayerException.class);
        }

        @Test
        @DisplayName("the ledger query forwards the viewer to the repository rather than folding it into filters")
        void ledgerQueryForwardsViewer() {
            LedgerQuery query = LedgerQuery.forParticipant(ALICE, LedgerQuery.Direction.EITHER, 10);

            service.ledger(query, ALICE);

            // Who is asking must arrive at the repository as its own argument, never as a client filter.
            assertThat(ledger.lastQuery).isSameAs(query);
            assertThat(ledger.lastViewer).isEqualTo(ALICE);
        }

        @Test
        @DisplayName("an anonymous investigator is a null viewer, passed through as-is")
        void anonymousViewer() {
            service.ledger(LedgerQuery.recent(5), null);
            assertThat(ledger.lastViewer).isNull();
        }
    }
}
