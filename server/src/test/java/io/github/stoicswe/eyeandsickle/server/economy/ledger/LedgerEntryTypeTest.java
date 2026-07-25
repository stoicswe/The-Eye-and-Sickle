package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ledger vocabulary, and the one distinction the economy leans on: exactly one type mints, the rest
 * move. Getting the faucet flag wrong is not a rounding error, it is inflation ({@code
 * docs/design/03-economy.md} §5 rule 3), so the faucet set is asserted to be exactly {@code
 * MINING_REWARD}.
 */
class LedgerEntryTypeTest {

    @Test
    @DisplayName("MINING_REWARD is the ONLY faucet; every other type moves existing ethecoin")
    void exactlyOneFaucet() {
        long faucets = Arrays.stream(LedgerEntryType.values())
                .filter(LedgerEntryType::isFaucet)
                .count();
        assertThat(faucets).isEqualTo(1);
        assertThat(LedgerEntryType.MINING_REWARD.isFaucet()).isTrue();

        // Crack seizure and raid loot in particular are transfers: the ethecoin already exists on the
        // host, so taking it moves it — it does not mint it.
        assertThat(LedgerEntryType.CRACK_SEIZURE.isFaucet()).isFalse();
        assertThat(LedgerEntryType.RAID_LOOT.isFaucet()).isFalse();
        assertThat(LedgerEntryType.TRADE.isFaucet()).isFalse();
        assertThat(LedgerEntryType.PAYOUT_SPLITTER.isFaucet()).isFalse();
        assertThat(LedgerEntryType.PURCHASE.isFaucet()).isFalse();
    }

    @Test
    @DisplayName("every type round-trips through its database spelling")
    void wireValueRoundTrips() {
        for (LedgerEntryType type : LedgerEntryType.values()) {
            assertThat(LedgerEntryType.fromWire(type.wireValue()))
                    .as(type.name())
                    .isEqualTo(type);
        }
    }

    @Test
    @DisplayName("the database spellings are the six the schema permits")
    void wireValuesAreTheSchemaVocabulary() {
        assertThat(Arrays.stream(LedgerEntryType.values()).map(LedgerEntryType::wireValue))
                .containsExactlyInAnyOrder(
                        "mining_reward", "trade", "crack_seizure", "raid_loot", "payout_splitter", "purchase");
    }

    @Test
    @DisplayName("an unknown stored value is rejected, not mapped to a fallback")
    void unknownWireValueRejected() {
        // A type this build does not understand is one it cannot apply a rule to; guessing is how an
        // unrecognised state becomes a permitted one.
        assertThatThrownBy(() -> LedgerEntryType.fromWire("wire_transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wire_transfer");
    }

    @Test
    @DisplayName("a null stored value is rejected")
    void nullWireValueRejected() {
        assertThatThrownBy(() -> LedgerEntryType.fromWire(null)).isInstanceOf(NullPointerException.class);
    }
}
