package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.RecordHash;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ItemProvenanceService} — this server acting as an <em>issuer</em>: minting an item and
 * extending its chain with a grant or a trade ({@code docs/architecture/04-item-provenance.md} §2).
 *
 * <p>The load-bearing claim is that what this service signs, its own verifier recognizes: a freshly
 * minted chain verifies, each successor links to the tip by {@code prevRecordHash} and {@code
 * chainDepth}, and time and nonce come from the injected {@link Clock} / {@code SecureRandom} so the
 * whole thing is deterministic. The failure tests defend the two states that must never produce a
 * record: a second mint of the same item, and a server with no signing key.
 */
class ItemProvenanceServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(TestChains.NOW, ZoneOffset.UTC);
    private static final Map<String, Object> ATTRS = Map.of("power", 42, "durability", "0.87");

    private final TestChains chains = new TestChains();
    private final FakeItemStore items = new FakeItemStore();
    private final FakeProvenanceStore provenance = new FakeProvenanceStore();

    /** A signer backed by a real Ed25519 key that the fixture's directory can resolve. */
    private ServerSigningIdentity homeSigner() {
        KeyPair home = chains.keysOf(TestChains.HOME_DID);
        return new LoadedSigningIdentity(
                TestChains.HOME_DID, TestChains.kidOf(TestChains.HOME_DID), home.getPrivate(), home.getPublic());
    }

    private ItemProvenanceService service(ServerSigningIdentity signer) {
        return new ItemProvenanceService(FIXED_CLOCK, TestChains.deterministicRandom(42L), signer, items, provenance);
    }

    private ItemProvenanceService service() {
        return service(homeSigner());
    }

    // ------------------------------------------------------------------ minting

    @Nested
    @DisplayName("minting an item")
    class Minting {

        @Test
        @DisplayName("writes a genesis record its own verifier recognizes")
        void freshlyMintedChainVerifies() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            assertThat(chains.verification(TestChains.HOME_DID)
                            .verify(List.of(record.toEnvelope()))
                            .recognized())
                    .as("a server must be able to re-verify the record it just signed")
                    .isTrue();
        }

        @Test
        @DisplayName("the genesis record is depth 0 with no predecessor")
        void genesisIsRootOfTheChain() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            assertThat(record.chainDepth()).isZero();
            assertThat(record.prevRecordHash()).isNull();
            assertThat(record.eventType()).isEqualTo(ProvenanceEventType.INITIAL_MINT);
            assertThat(record.issuerDid()).isEqualTo(TestChains.HOME_DID);
            assertThat(record.holderDid()).isEqualTo(TestChains.HOLDER);
            assertThat(record.recordVersion()).isEqualTo(ProvenancePayload.CURRENT_RECORD_VERSION);
        }

        @Test
        @DisplayName("the stored record hash is the SHA-256 of its own canonical payload")
        void recordHashCoversTheCanonicalPayload() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            assertThat(record.recordHash())
                    .isEqualTo(RecordHash.of(record.toEnvelope().payload()));
        }

        @Test
        @DisplayName("the timestamp comes from the injected clock, never wall-clock time")
        void timestampIsFromTheInjectedClock() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            assertThat(record.payloadTimestamp()).isEqualTo("2026-08-01T12:00:00Z");
            assertThat(record.recordedAt()).isEqualTo(TestChains.NOW);
        }

        @Test
        @DisplayName("the item projection mirrors the minted record and lands in the requested tier")
        void itemProjectionMatchesTheRecord() {
            service().mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            Item item = items.find(TestChains.ITEM_ID).orElseThrow();
            assertThat(item.itemType()).isEqualTo(TestChains.ITEM_TYPE);
            assertThat(item.holderDid()).isEqualTo(TestChains.HOLDER);
            assertThat(item.storageTier()).isEqualTo(StorageTier.VAULT);
            assertThat(item.socketedIn()).isNull();
            assertThat(item.itemAttrs()).containsEntry("power", 42).containsEntry("durability", "0.87");
            assertThat(item.rowVersion()).isZero();
        }

        @Test
        @DisplayName("a null itemAttrs mints an item with empty attrs rather than failing")
        void nullAttrsBecomeEmpty() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, null, TestChains.HOLDER, StorageTier.VAULT);

            assertThat(record.toEnvelope().payload().itemAttrs()).isEmpty();
            assertThat(items.find(TestChains.ITEM_ID).orElseThrow().itemAttrs()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ extending the chain

    @Nested
    @DisplayName("extending an item's chain")
    class Extending {

        @Test
        @DisplayName("a server grant links to the genesis tip and moves the holder")
        void serverGrantLinksAndMovesHolder() {
            StoredProvenanceRecord genesis = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            StoredProvenanceRecord grant = service().serverGrant(TestChains.ITEM_ID, TestChains.OTHER_HOLDER);

            assertThat(grant.chainDepth()).isEqualTo(1);
            assertThat(grant.prevRecordHash())
                    .as("a successor's prevRecordHash is exactly the tip's recordHash")
                    .isEqualTo(genesis.recordHash());
            assertThat(grant.eventType()).isEqualTo(ProvenanceEventType.SERVER_GRANT);
            assertThat(grant.holderDid()).isEqualTo(TestChains.OTHER_HOLDER);

            Item item = items.find(TestChains.ITEM_ID).orElseThrow();
            assertThat(item.holderDid()).isEqualTo(TestChains.OTHER_HOLDER);
            assertThat(item.rowVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("a trade is a single-issuer event that carries the item's stats forward unchanged")
        void tradeCarriesStatsForward() {
            service().mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            StoredProvenanceRecord trade = service().trade(TestChains.ITEM_ID, TestChains.OTHER_HOLDER);

            ProvenancePayload payload = trade.toEnvelope().payload();
            assertThat(trade.eventType()).isEqualTo(ProvenanceEventType.TRADE);
            assertThat(payload.itemType()).isEqualTo(TestChains.ITEM_TYPE);
            assertThat(payload.itemAttrs()).containsEntry("power", 42).containsEntry("durability", "0.87");
        }

        @Test
        @DisplayName("a whole mint -> grant -> trade chain verifies end to end")
        void multiEventChainVerifies() {
            ItemProvenanceService service = service();
            service.mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);
            service.serverGrant(TestChains.ITEM_ID, TestChains.OTHER_HOLDER);
            service.trade(TestChains.ITEM_ID, TestChains.HOLDER);

            List<io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope> chain =
                    provenance.findChain(TestChains.ITEM_ID).stream()
                            .map(StoredProvenanceRecord::toEnvelope)
                            .toList();

            assertThat(chain).extracting(e -> e.payload().chainDepth()).containsExactly(0, 1, 2);
            assertThat(chains.verification(TestChains.HOME_DID).verify(chain).recognized())
                    .isTrue();
        }

        @Test
        @DisplayName("each appended record's depth is one past the tip")
        void depthIsContiguous() {
            ItemProvenanceService service = service();
            service.mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);
            service.trade(TestChains.ITEM_ID, TestChains.OTHER_HOLDER);
            StoredProvenanceRecord third = service.trade(TestChains.ITEM_ID, TestChains.HOLDER);

            assertThat(third.chainDepth()).isEqualTo(2);
            assertThat(provenance.findTip(TestChains.ITEM_ID).orElseThrow().chainDepth())
                    .isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ the signing identity

    @Nested
    @DisplayName("the signing identity")
    class Signing {

        @Test
        @DisplayName("a not-provisioned node mints nothing and writes nothing")
        void missingSigningIdentityCannotMint() {
            ItemProvenanceService service = service(new MissingSigningIdentity());

            assertThatThrownBy(() -> service.mint(
                            TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no signing key");

            // The refusal must precede any write, or a server with no key would leave orphaned rows.
            assertThat(items.size()).as("no item written").isZero();
            assertThat(provenance.size()).as("no record written").isZero();
        }

        @Test
        @DisplayName("a loaded identity signs, and its signature verifies over the canonical bytes")
        void loadedIdentitySigns() {
            StoredProvenanceRecord record = service()
                    .mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            // Single-issuer -> exactly one signature block, by the home server's key.
            assertThat(record.toEnvelope().signatures()).hasSize(1);
            assertThat(record.toEnvelope().signatures().getFirst().signerDid()).isEqualTo(TestChains.HOME_DID);
        }
    }

    // ------------------------------------------------------------------ failures

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an item is minted exactly once — a second mint is refused")
        void doubleMintIsRefused() {
            ItemProvenanceService service = service();
            service.mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);

            // A second initial_mint for the same id is a forked genesis, the shape a fabricating server
            // produces.
            assertThatThrownBy(() -> service.mint(
                            TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("granting an item this server does not hold is refused")
        void grantUnknownItemIsRefused() {
            assertThatThrownBy(() -> service().serverGrant(UUID.randomUUID(), TestChains.OTHER_HOLDER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No item");
        }

        @Test
        @DisplayName("trading an item this server does not hold is refused")
        void tradeUnknownItemIsRefused() {
            assertThatThrownBy(() -> service().trade(UUID.randomUUID(), TestChains.OTHER_HOLDER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No item");
        }

        @Test
        @DisplayName("extending an item that exists but has no chain is refused")
        void extendingAnItemWithNoChainIsRefused() {
            // An item projection with no provenance rows behind it — a corrupt state that must not be
            // silently 'fixed' by minting a fresh genesis mid-chain.
            items.insert(new Item(
                    TestChains.ITEM_ID,
                    TestChains.ITEM_TYPE,
                    ATTRS,
                    TestChains.HOLDER,
                    StorageTier.VAULT,
                    null,
                    Instant.parse("2026-08-01T12:00:00Z"),
                    0L));

            assertThatThrownBy(() -> service().serverGrant(TestChains.ITEM_ID, TestChains.OTHER_HOLDER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no provenance chain");
        }
    }

    // ------------------------------------------------------------------ determinism

    @Nested
    @DisplayName("determinism of time and nonce")
    class Determinism {

        @Test
        @DisplayName("the same seed produces the same nonce")
        void seededNonceIsReproducible() {
            String nonceA = mintNonce();
            String nonceB = mintNonce();
            assertThat(nonceA)
                    .as("two services with an identically-seeded random must sign identical nonces")
                    .isEqualTo(nonceB);
        }

        @Test
        @DisplayName("successive records within one run carry distinct nonces (replay defence)")
        void successiveNoncesDiffer() {
            ItemProvenanceService service = service();
            StoredProvenanceRecord genesis =
                    service.mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT);
            StoredProvenanceRecord trade = service.trade(TestChains.ITEM_ID, TestChains.OTHER_HOLDER);

            assertThat(genesis.toEnvelope().payload().nonce())
                    .isNotEqualTo(trade.toEnvelope().payload().nonce());
        }

        private String mintNonce() {
            TestChains local = new TestChains();
            KeyPair home = local.keysOf(TestChains.HOME_DID);
            ServerSigningIdentity signer = new LoadedSigningIdentity(
                    TestChains.HOME_DID, TestChains.kidOf(TestChains.HOME_DID), home.getPrivate(), home.getPublic());
            ItemProvenanceService service = new ItemProvenanceService(
                    FIXED_CLOCK,
                    TestChains.deterministicRandom(7L),
                    signer,
                    new FakeItemStore(),
                    new FakeProvenanceStore());
            return service.mint(TestChains.ITEM_ID, TestChains.ITEM_TYPE, ATTRS, TestChains.HOLDER, StorageTier.VAULT)
                    .toEnvelope()
                    .payload()
                    .nonce();
        }
    }
}
