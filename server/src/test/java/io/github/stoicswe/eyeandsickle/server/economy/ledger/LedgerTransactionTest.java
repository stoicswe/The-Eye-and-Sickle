package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A ledger row as a value. The faucet rule is enforced in the constructor, mirroring the schema's
 * {@code ck_ledger_faucet}: a record that could not have been stored also cannot be constructed. Only a
 * mining reward may have no payer.
 */
class LedgerTransactionTest {

    private static final String FROM = "did:plc:payer0000000000000000";
    private static final String TO = "did:plc:payee0000000000000000";
    private static final Instant WHEN = Instant.parse("2026-07-24T12:00:00Z");

    private static LedgerTransaction tx(String from, String to, LedgerEntryType type) {
        return new LedgerTransaction(UUID.randomUUID(), from, to, Ethecoin.ofWholeEthecoin(10), type, true, null, WHEN);
    }

    @Nested
    @DisplayName("the faucet rule: only a mining reward may have no payer")
    class FaucetRule {

        @Test
        @DisplayName("a payerless mining reward is valid")
        void payerlessMiningRewardIsValid() {
            assertThatCode(() -> tx(null, TO, LedgerEntryType.MINING_REWARD)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a payerless transfer is rejected — a crack seizure with no payer would mint currency")
        void payerlessTransferRejected() {
            assertThatThrownBy(() -> tx(null, TO, LedgerEntryType.CRACK_SEIZURE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a fromDid");

            assertThatThrownBy(() -> tx(null, TO, LedgerEntryType.TRADE)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a mining reward WITH a payer is still valid (the rule is about the absence, not presence)")
        void miningRewardWithPayerIsValid() {
            assertThatCode(() -> tx(FROM, TO, LedgerEntryType.MINING_REWARD)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("required fields")
    class RequiredFields {

        @Test
        @DisplayName("toDid, amount, type, txId and createdAt are all required")
        void nullsRejected() {
            assertThatThrownBy(() -> new LedgerTransaction(
                            null, FROM, TO, Ethecoin.ZERO, LedgerEntryType.TRADE, true, null, WHEN))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LedgerTransaction(
                            UUID.randomUUID(), FROM, null, Ethecoin.ZERO, LedgerEntryType.TRADE, true, null, WHEN))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LedgerTransaction(
                            UUID.randomUUID(), FROM, TO, null, LedgerEntryType.TRADE, true, null, WHEN))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LedgerTransaction(
                            UUID.randomUUID(), FROM, TO, Ethecoin.ofWholeEthecoin(1), null, true, null, WHEN))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LedgerTransaction(
                            UUID.randomUUID(),
                            FROM,
                            TO,
                            Ethecoin.ofWholeEthecoin(1),
                            LedgerEntryType.TRADE,
                            true,
                            null,
                            null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("the memo")
    class Memo {

        @Test
        @DisplayName("a null memo becomes an empty map, not a NullPointerException later")
        void nullMemoBecomesEmpty() {
            LedgerTransaction transaction = tx(FROM, TO, LedgerEntryType.TRADE);
            assertThat(transaction.memo()).isEmpty();
        }

        @Test
        @DisplayName("the memo is defensively copied — mutating the caller's map does not change the row")
        void memoIsCopied() {
            Map<String, Object> source = new HashMap<>();
            source.put("block", 42);
            LedgerTransaction transaction = new LedgerTransaction(
                    UUID.randomUUID(),
                    FROM,
                    TO,
                    Ethecoin.ofWholeEthecoin(1),
                    LedgerEntryType.TRADE,
                    true,
                    source,
                    WHEN);

            source.put("block", 999);
            source.put("injected", "later");

            assertThat(transaction.memo()).containsEntry("block", 42).doesNotContainKey("injected");
        }

        @Test
        @DisplayName("the stored memo is unmodifiable")
        void memoIsUnmodifiable() {
            LedgerTransaction transaction = new LedgerTransaction(
                    UUID.randomUUID(),
                    FROM,
                    TO,
                    Ethecoin.ofWholeEthecoin(1),
                    LedgerEntryType.TRADE,
                    true,
                    Map.of("k", "v"),
                    WHEN);

            assertThatThrownBy(() -> transaction.memo().put("x", "y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("a Dead Drop is a normal row with traceable = false")
    void deadDropIsJustAFlag() {
        LedgerTransaction deadDrop = new LedgerTransaction(
                UUID.randomUUID(), FROM, TO, Ethecoin.ofWholeEthecoin(50), LedgerEntryType.TRADE, false, null, WHEN);

        // The row still exists; laundering leaves something for an investigator to eventually find.
        assertThat(deadDrop.traceable()).isFalse();
        assertThat(deadDrop.amount()).isEqualTo(Ethecoin.ofWholeEthecoin(50));
    }
}
