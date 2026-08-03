package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.economy.ledger.LedgerQuery.Direction;
import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The public ledger repository against a real PostgreSQL. Two things genuinely need a database: that a
 * transaction is queryable from BOTH counterparties ({@code docs/design/01-core-resources.md} §2.2),
 * and that the Dead Drop visibility rule lives in the query — a traceable row is public, an untraceable
 * one visible only to its own counterparties, decided by the viewer argument and never by a
 * client-supplied filter (Invariant I14).
 */
class LedgerRepositoryIT extends DatabaseIntegrationTestBase {

    private static final String A = "did:plc:aaaa000000000000000000";
    private static final String B = "did:plc:bbbb000000000000000000";
    private static final String C = "did:plc:cccc000000000000000000";
    private static final Instant T0 = Instant.parse("2026-07-24T12:00:00Z");

    private final LedgerRepository repository = new LedgerRepository(jdbcClient());

    // ------------------------------------------------------------------ round-trip

    @Test
    @DisplayName("a row round-trips: amount, type, traceability, memo and timestamp all survive")
    void roundTrip() {
        UUID id = append(A, B, Ethecoin.ofWholeEthecoin(4), LedgerEntryType.TRADE, true, Map.of("note", "deal"), T0);

        LedgerTransaction row = onlyRow(repository.query(LedgerQuery.recent(10), null));
        assertThat(row.txId()).isEqualTo(id);
        assertThat(row.fromDid()).isEqualTo(A);
        assertThat(row.toDid()).isEqualTo(B);
        assertThat(row.amount()).isEqualTo(Ethecoin.ofWholeEthecoin(4));
        assertThat(row.type()).isEqualTo(LedgerEntryType.TRADE);
        assertThat(row.traceable()).isTrue();
        assertThat(row.memo()).containsEntry("note", "deal");
        assertThat(row.createdAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("a mining reward round-trips with no payer")
    void miningRewardHasNoPayer() {
        append(null, A, Ethecoin.ofWholeEthecoin(3), LedgerEntryType.MINING_REWARD, true, null, T0);

        LedgerTransaction row = onlyRow(repository.query(LedgerQuery.recent(10), null));
        assertThat(row.fromDid()).isNull();
        assertThat(row.type()).isEqualTo(LedgerEntryType.MINING_REWARD);
    }

    // ------------------------------------------------------------------ both counterparties

    @Nested
    @DisplayName("queryable from both counterparties")
    class BothSides {

        @Test
        @DisplayName("a transfer is found as sent by the payer and received by the payee")
        void sentAndReceived() {
            append(A, B, Ethecoin.ofWholeEthecoin(5), LedgerEntryType.TRADE, true, null, T0);

            assertThat(repository.query(LedgerQuery.forParticipant(A, Direction.SENT, 10), null))
                    .hasSize(1);
            assertThat(repository.query(LedgerQuery.forParticipant(A, Direction.RECEIVED, 10), null))
                    .isEmpty();
            assertThat(repository.query(LedgerQuery.forParticipant(B, Direction.RECEIVED, 10), null))
                    .hasSize(1);
            assertThat(repository.query(LedgerQuery.forParticipant(B, Direction.SENT, 10), null))
                    .isEmpty();
        }

        @Test
        @DisplayName("between() pins the flow to a specific pair, in either direction")
        void betweenAPair() {
            append(A, B, Ethecoin.ofWholeEthecoin(5), LedgerEntryType.TRADE, true, null, T0);
            append(A, C, Ethecoin.ofWholeEthecoin(2), LedgerEntryType.PURCHASE, true, null, T0.plusSeconds(1));

            assertThat(repository.query(LedgerQuery.between(A, B, 10), null)).hasSize(1);
            assertThat(repository.query(LedgerQuery.between(A, C, 10), null)).hasSize(1);
            assertThat(repository.query(LedgerQuery.between(B, C, 10), null)).isEmpty();
        }

        @Test
        @DisplayName("a type filter restricts to one transaction type")
        void typeFilter() {
            append(A, B, Ethecoin.ofWholeEthecoin(5), LedgerEntryType.TRADE, true, null, T0);
            append(A, C, Ethecoin.ofWholeEthecoin(2), LedgerEntryType.PURCHASE, true, null, T0.plusSeconds(1));

            List<LedgerTransaction> purchases =
                    repository.query(LedgerQuery.recent(10).ofType(LedgerEntryType.PURCHASE), null);
            assertThat(purchases).hasSize(1);
            assertThat(purchases.get(0).type()).isEqualTo(LedgerEntryType.PURCHASE);
        }
    }

    // ------------------------------------------------------------------ Dead Drop visibility

    @Nested
    @DisplayName("Dead Drop visibility")
    class DeadDrops {

        @Test
        @DisplayName("an untraceable row is hidden from an anonymous investigator but a traceable one is not")
        void anonymousSeesOnlyTraceable() {
            append(A, B, Ethecoin.ofWholeEthecoin(5), LedgerEntryType.TRADE, true, null, T0);
            UUID deadDrop =
                    append(A, B, Ethecoin.ofWholeEthecoin(50), LedgerEntryType.TRADE, false, null, T0.plusSeconds(1));

            List<LedgerTransaction> anonymous = repository.query(LedgerQuery.recent(10), null);
            assertThat(anonymous).extracting(LedgerTransaction::txId).doesNotContain(deadDrop);
            assertThat(anonymous).hasSize(1); // the traceable one only
        }

        @Test
        @DisplayName("an untraceable row is visible to its OWN counterparties, and to nobody else")
        void counterpartiesSeeTheirOwnDeadDrop() {
            UUID deadDrop = append(A, B, Ethecoin.ofWholeEthecoin(50), LedgerEntryType.TRADE, false, null, T0);

            // Both ends of the drop can see it...
            assertThat(repository.query(LedgerQuery.recent(10), A))
                    .extracting(LedgerTransaction::txId)
                    .contains(deadDrop);
            assertThat(repository.query(LedgerQuery.recent(10), B))
                    .extracting(LedgerTransaction::txId)
                    .contains(deadDrop);
            // ...an unrelated third party cannot, even naming themselves as viewer.
            assertThat(repository.query(LedgerQuery.recent(10), C))
                    .extracting(LedgerTransaction::txId)
                    .doesNotContain(deadDrop);
        }
    }

    // ------------------------------------------------------------------ ordering and clamping

    @Nested
    @DisplayName("ordering and the scan bound")
    class OrderingAndLimit {

        @Test
        @DisplayName("rows come back newest first")
        void newestFirst() {
            UUID oldest = append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0);
            UUID middle =
                    append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0.plusSeconds(10));
            UUID newest =
                    append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0.plusSeconds(20));

            assertThat(repository.query(LedgerQuery.recent(10), null))
                    .extracting(LedgerTransaction::txId)
                    .containsExactly(newest, middle, oldest);
        }

        @Test
        @DisplayName("a small limit pages the newest rows")
        void limitPagesNewest() {
            append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0);
            UUID middle =
                    append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0.plusSeconds(10));
            UUID newest =
                    append(A, B, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0.plusSeconds(20));

            assertThat(repository.query(LedgerQuery.recent(2), null))
                    .extracting(LedgerTransaction::txId)
                    .containsExactly(newest, middle);
        }

        @Test
        @DisplayName("a caller cannot ask the server to scan the whole ledger — the page is clamped to MAX_LIMIT")
        void limitIsClampedToMax() {
            for (int i = 0; i < LedgerQuery.MAX_LIMIT + 5; i++) {
                append(
                        null,
                        A,
                        Ethecoin.ofDecimal("0.01"),
                        LedgerEntryType.MINING_REWARD,
                        true,
                        null,
                        T0.plusSeconds(i));
            }

            assertThat(repository.query(LedgerQuery.recent(10_000), null)).hasSize(LedgerQuery.MAX_LIMIT);
        }
    }

    // ------------------------------------------------------------------ the database's own guards

    @Test
    @DisplayName("the database refuses a self-directed row even if the repository is asked to write one")
    void selfDirectedRowRefusedByDatabase() {
        LedgerTransaction selfRow = new LedgerTransaction(
                UUID.randomUUID(), A, A, Ethecoin.ofWholeEthecoin(1), LedgerEntryType.TRADE, true, null, T0);

        // The service guards against this too, but the schema is the last line of defence (Invariant I14).
        assertThatThrownBy(() -> repository.append(selfRow))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_not_self");
    }

    // ------------------------------------------------------------------ helpers

    private UUID append(
            String from,
            String to,
            Ethecoin amount,
            LedgerEntryType type,
            boolean traceable,
            Map<String, Object> memo,
            Instant when) {
        UUID id = UUID.randomUUID();
        repository.append(new LedgerTransaction(id, from, to, amount, type, traceable, memo, when));
        return id;
    }

    private static LedgerTransaction onlyRow(List<LedgerTransaction> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
