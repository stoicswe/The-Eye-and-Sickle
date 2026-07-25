package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 */
class LedgerServiceTest {

    private static final String ALICE = "did:plc:alice000000000000000000";
    private static final String BOB = "did:plc:bob00000000000000000000";
    private static final String NPC_HOST = "did:npc:crackedhost";
    private static final String NPC_VENDOR = "did:npc:blackmarket";
    private static final String GHOST = "did:plc:ghost000000000000000000";

    private final FakeAccountRepository accounts = new FakeAccountRepository();
    private final FakeLedgerRepository ledger = new FakeLedgerRepository();
    private final LedgerService service = new LedgerService(accounts, ledger);

    private static Account account(String did, long balanceMinor) {
        return new Account(UUID.randomUUID(), did, Ethecoin.ofMinorUnits(balanceMinor), java.math.BigDecimal.ZERO, 0L);
    }

    private long localSupply() {
        return accounts.balanceOf(ALICE).minorUnits()
                + (accounts.currentByDid(BOB).map(a -> a.balance().minorUnits()).orElse(0L));
    }

    // ------------------------------------------------------------------ mint (the faucet)

    @Nested
    @DisplayName("mint — the one path that creates ethecoin")
    class Mint {

        @Test
        @DisplayName("credits the recipient and writes a payerless MINING_REWARD row")
        void mintsIntoBalance() {
            accounts.with(account(ALICE, 1_000));

            LedgerTransaction row = service.mint(ALICE, Ethecoin.ofMinorUnits(250), Map.of("block", 7));

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(1_250));
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
            assertThatThrownBy(() -> service.mint(GHOST, Ethecoin.ofMinorUnits(10), null))
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
                            ALICE, BOB, Ethecoin.ofMinorUnits(100), LedgerEntryType.MINING_REWARD, true, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("faucet");
            assertThat(ledger.appended).isEmpty();
            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(1_000));
        }

        @Test
        @DisplayName("between two local players, total supply is conserved — a transfer moves, it does not mint")
        void supplyIsConserved() {
            accounts.with(account(ALICE, 1_000)).with(account(BOB, 400));
            long before = localSupply();

            LedgerTransaction row =
                    service.transfer(ALICE, BOB, Ethecoin.ofMinorUnits(300), LedgerEntryType.TRADE, true, null);

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(700));
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ofMinorUnits(700));
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
                    NPC_HOST, BOB, Ethecoin.ofMinorUnits(80), LedgerEntryType.CRACK_SEIZURE, true, null);

            // The buffer already existed on the host and the mining slice debited it; here only the local
            // payee is credited, and the row records a CRACK_SEIZURE — never a MINING_REWARD.
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ofMinorUnits(180));
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

            service.transfer(ALICE, NPC_VENDOR, Ethecoin.ofMinorUnits(120), LedgerEntryType.PURCHASE, true, null);

            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(380));
            assertThat(ledger.appended).hasSize(1);
            assertThat(ledger.appended.get(0).type()).isEqualTo(LedgerEntryType.PURCHASE);
        }

        @Test
        @DisplayName("a self-directed transfer is refused")
        void selfTransferRejected() {
            accounts.with(account(ALICE, 1_000));
            assertThatThrownBy(() ->
                            service.transfer(ALICE, ALICE, Ethecoin.ofMinorUnits(1), LedgerEntryType.TRADE, true, null))
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
                            service.transfer(ALICE, BOB, Ethecoin.ofMinorUnits(101), LedgerEntryType.TRADE, true, null))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, insufficient -> {
                        assertThat(insufficient.did()).isEqualTo(ALICE);
                        assertThat(insufficient.balance()).isEqualTo(Ethecoin.ofMinorUnits(100));
                        assertThat(insufficient.required()).isEqualTo(Ethecoin.ofMinorUnits(101));
                    });

            // Affordability is asked BEFORE the subtraction: no half-transfer, no ledger row.
            assertThat(accounts.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(100));
            assertThat(accounts.balanceOf(BOB)).isEqualTo(Ethecoin.ZERO);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a transfer where neither party is local is refused — this server has no stake")
        void neitherPartyLocalRejected() {
            assertThatThrownBy(() -> service.transfer(
                            NPC_HOST, NPC_VENDOR, Ethecoin.ofMinorUnits(10), LedgerEntryType.TRADE, true, null))
                    .isInstanceOf(UnknownPlayerException.class);
            assertThat(ledger.appended).isEmpty();
        }

        @Test
        @DisplayName("a Dead Drop is still recorded, just flagged untraceable")
        void deadDropIsRecorded() {
            accounts.with(account(ALICE, 500)).with(account(BOB, 0));

            LedgerTransaction row =
                    service.transfer(ALICE, BOB, Ethecoin.ofMinorUnits(200), LedgerEntryType.TRADE, false, null);

            assertThat(row.traceable()).isFalse();
            assertThat(ledger.appended).hasSize(1);
            assertThat(ledger.appended.get(0).traceable()).isFalse();
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
            assertThat(service.balanceOf(ALICE)).isEqualTo(Ethecoin.ofMinorUnits(4_200));
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
