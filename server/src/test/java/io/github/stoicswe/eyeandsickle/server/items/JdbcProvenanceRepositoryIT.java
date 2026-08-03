package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * {@link JdbcProvenanceRepository} against a real PostgreSQL — per-item chain storage indexed by
 * {@code item_id} with {@code chainDepth} range access ({@code docs/architecture/04-item-provenance.md}
 * §6.1).
 *
 * <p>The database constraints themselves are proved in {@code SchemaIT}; this test proves the
 * repository code and the properties the rest of the slice depends on: a stored chain reads back and
 * <em>re-verifies</em> (§6.2), the range read serves the "records N..N+20" window, and the
 * position-uniqueness and foreign-key guards surface as {@link DataAccessException}s through the
 * repository rather than as silent no-ops.
 */
class JdbcProvenanceRepositoryIT extends DatabaseIntegrationTestBase {

    private static final Instant RECORDED_AT = Instant.parse("2026-08-01T12:00:00Z");

    private final TestChains chains = new TestChains();
    private JdbcProvenanceRepository provenance;
    private JdbcItemRepository items;

    @BeforeEach
    void setUp() {
        provenance = new JdbcProvenanceRepository(jdbcClient());
        items = new JdbcItemRepository(jdbcClient());
    }

    /** The item row a chain hangs off — provenance rows reference it by foreign key. */
    private void insertHostItem() {
        items.insert(new Item(
                TestChains.ITEM_ID,
                TestChains.ITEM_TYPE,
                Map.of("power", 42),
                TestChains.HOLDER,
                StorageTier.VAULT,
                null,
                RECORDED_AT,
                0L));
    }

    private StoredProvenanceRecord row(ProvenanceEnvelope envelope) {
        return StoredProvenanceRecord.from(
                UUID.randomUUID(), envelope, ProvenanceJson.writeEnvelope(envelope), RECORDED_AT);
    }

    private List<StoredProvenanceRecord> store(int length) {
        insertHostItem();
        List<ProvenanceEnvelope> chain = chains.validChain(length);
        List<StoredProvenanceRecord> stored = chain.stream().map(this::row).toList();
        stored.forEach(provenance::append);
        return stored;
    }

    // ------------------------------------------------------------------ round-trip + re-verification

    @Nested
    @DisplayName("a stored chain")
    class StoredChain {

        @Test
        @DisplayName("reads back genesis-first and re-verifies after the database round-trip")
        void readsBackAndReVerifies() {
            store(4);

            List<ProvenanceEnvelope> readBack = provenance.findChain(TestChains.ITEM_ID).stream()
                    .map(StoredProvenanceRecord::toEnvelope)
                    .toList();

            assertThat(readBack).extracting(e -> e.payload().chainDepth()).containsExactly(0, 1, 2, 3);
            // §6.2: jsonb storage normalizes whitespace and key order, but because the client re-derives
            // the canonical bytes from the parsed fields, the chain it is shown still verifies.
            assertThat(chains.verification(TestChains.HOME_DID).verify(readBack).recognized())
                    .isTrue();
        }

        @Test
        @DisplayName("preserves every structured column of each record")
        void preservesColumns() {
            List<StoredProvenanceRecord> written = store(2);

            List<StoredProvenanceRecord> read = provenance.findChain(TestChains.ITEM_ID);
            assertThat(read).hasSize(2);
            for (int depth = 0; depth < written.size(); depth++) {
                StoredProvenanceRecord expected = written.get(depth);
                StoredProvenanceRecord actual = read.get(depth);
                assertThat(actual.recordId()).isEqualTo(expected.recordId());
                assertThat(actual.itemId()).isEqualTo(expected.itemId());
                assertThat(actual.chainDepth()).isEqualTo(expected.chainDepth());
                assertThat(actual.recordHash()).isEqualTo(expected.recordHash());
                assertThat(actual.prevRecordHash()).isEqualTo(expected.prevRecordHash());
                assertThat(actual.eventType()).isEqualTo(expected.eventType());
                assertThat(actual.holderDid()).isEqualTo(expected.holderDid());
                assertThat(actual.issuerDid()).isEqualTo(expected.issuerDid());
                assertThat(actual.recordVersion()).isEqualTo(expected.recordVersion());
                assertThat(actual.payloadTimestamp()).isEqualTo(expected.payloadTimestamp());
                assertThat(actual.recordedAt()).isEqualTo(RECORDED_AT);
            }
        }

        @Test
        @DisplayName("the tip is the record at the greatest depth")
        void tipIsGreatestDepth() {
            store(5);

            assertThat(provenance.findTip(TestChains.ITEM_ID).orElseThrow().chainDepth())
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("an item with no records has no tip")
        void noTipForUnknownItem() {
            assertThat(provenance.findTip(UUID.randomUUID())).isEmpty();
        }
    }

    // ------------------------------------------------------------------ the range read (§6.1)

    @Nested
    @DisplayName("range access")
    class RangeAccess {

        @Test
        @DisplayName("serves the records N..N+20 window the history view asks for")
        void servesTheWindow() {
            store(30);

            List<StoredProvenanceRecord> window = provenance.findRange(TestChains.ITEM_ID, 5, 20);

            assertThat(window)
                    .extracting(StoredProvenanceRecord::chainDepth)
                    .first()
                    .isEqualTo(5);
            assertThat(window).hasSize(20);
            assertThat(window)
                    .extracting(StoredProvenanceRecord::chainDepth)
                    .last()
                    .isEqualTo(24);
        }

        @Test
        @DisplayName("returns records in ascending depth order from the requested start")
        void ascendingFromStart() {
            store(6);

            assertThat(provenance.findRange(TestChains.ITEM_ID, 0, 100))
                    .extracting(StoredProvenanceRecord::chainDepth)
                    .containsExactly(0, 1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("a non-positive limit fetches nothing rather than the whole chain")
        void nonPositiveLimitIsEmpty() {
            store(3);

            assertThat(provenance.findRange(TestChains.ITEM_ID, 0, 0)).isEmpty();
            assertThat(provenance.findRange(TestChains.ITEM_ID, 0, -5)).isEmpty();
        }

        @Test
        @DisplayName("a start past the tip returns nothing")
        void startPastTipIsEmpty() {
            store(3);

            assertThat(provenance.findRange(TestChains.ITEM_ID, 99, 20)).isEmpty();
        }
    }

    // ------------------------------------------------------------------ the constraints bite through the repo

    @Nested
    @DisplayName("integrity guards surface as exceptions")
    class Guards {

        @Test
        @DisplayName("a second record at the same depth is refused — a forked chain cannot be stored")
        void forkedChainRefused() {
            insertHostItem();
            ProvenancePayload genesis = chains.genesis();
            provenance.append(row(chains.singleIssuer(genesis)));
            provenance.append(row(chains.singleIssuer(
                    chains.following(genesis, ProvenanceEventType.TRADE, TestChains.HOLDER, TestChains.HOME_DID))));

            // A different record claiming the already-taken depth 1 — what a fabricating server produces.
            ProvenanceEnvelope fork = chains.singleIssuer(chains.following(
                    genesis, ProvenanceEventType.SERVER_GRANT, TestChains.OTHER_HOLDER, TestChains.HOME_DID));
            assertThatThrownBy(() -> provenance.append(row(fork)))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("uq_provenance_records_position");
        }

        @Test
        @DisplayName("a record for an item that does not exist is refused by the foreign key")
        void foreignKeyBites() {
            // No host item inserted: the chain has nothing to hang off.
            assertThatThrownBy(() -> provenance.append(row(chains.singleIssuer(chains.genesis()))))
                    .isInstanceOf(DataAccessException.class);
        }
    }
}
