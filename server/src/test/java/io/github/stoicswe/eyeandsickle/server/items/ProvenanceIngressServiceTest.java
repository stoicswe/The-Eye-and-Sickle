package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainFault.Reason;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.RecordHash;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceIngressService.IngressResult;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceIngressService.IngressStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ProvenanceIngressService} — verify-before-recognize on the federation edge ({@code
 * docs/architecture/04-item-provenance.md} §7). This is the security core of the slice: a chain that
 * fails <em>any</em> check is stored nowhere, which is how a cheating server's fabricated items become
 * worthless across the federation ({@code 03} §4).
 *
 * <p>The bulk of the file is rejection. Each tamper — a broken hash link, a rewritten stat, an
 * unauthorized issuer, a forged signature, a genesis that is not a mint — is fed in as verbatim
 * envelope JSON and asserted to leave both stores empty. A green ingest of a sound chain proves almost
 * nothing on its own; that the forged ones are refused is the point.
 */
class ProvenanceIngressServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(TestChains.NOW, ZoneOffset.UTC);

    private final TestChains chains = new TestChains();
    private final FakeItemStore items = new FakeItemStore();
    private final FakeProvenanceStore provenance = new FakeProvenanceStore();
    private final ItemsProperties properties = new ItemsProperties(TestChains.MAX_FUTURE_SKEW, null, null, null);

    /** Ingress wired to recognize only the home server, exactly as a non-federating node would be. */
    private ProvenanceIngressService ingress() {
        return new ProvenanceIngressService(
                chains.verification(TestChains.HOME_DID), items, provenance, properties, FIXED_CLOCK);
    }

    private void assertStoredNothing() {
        assertThat(items.size()).as("a rejected chain writes no item").isZero();
        assertThat(provenance.size()).as("a rejected chain writes no record").isZero();
    }

    // ------------------------------------------------------------------ the happy path

    @Nested
    @DisplayName("a sound chain")
    class Recognized {

        @Test
        @DisplayName("is recognized, and its item and every record are stored")
        void recognizedAndStored() {
            List<String> documents = TestChains.documentsOf(chains.validChain(3));

            IngressResult result = ingress().ingest(documents);

            assertThat(result.status()).isEqualTo(IngressStatus.RECOGNIZED_STORED);
            assertThat(result.stored()).isTrue();
            assertThat(result.itemId()).isEqualTo(TestChains.ITEM_ID);
            assertThat(result.verification().recognized()).isTrue();
            assertThat(items.size()).isEqualTo(1);
            assertThat(provenance.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("lands the ingested item in the configured storage tier, never socketed")
        void ingestedItemLandsInConfiguredTier() {
            ingress().ingest(TestChains.documentsOf(chains.validChain(2)));

            Item item = items.find(TestChains.ITEM_ID).orElseThrow();
            // [PROPOSAL] ItemsProperties default: a transferred-in item is exposed only while online.
            assertThat(item.storageTier()).isEqualTo(StorageTier.STANDARD_STORAGE);
            assertThat(item.socketedIn()).isNull();
            assertThat(item.holderDid())
                    .as("the item mirrors the verified chain tip")
                    .isEqualTo(TestChains.HOLDER);
        }

        @Test
        @DisplayName("records the incoming character-DID holder verbatim and lists it per-character (09 §9)")
        void ingestedHolderIsACharacterDid() {
            // A foreign server minted this item to a character DID; ingress verifies the issuer signature
            // (indifferent to the holder string) and records the holder unchanged.
            var chain = List.of(chains.singleIssuer(
                    chains.genesisForHolder(TestChains.HOME_DID, TestChains.CHARACTER_SLOT_1.value())));

            ingress().ingest(TestChains.documentsOf(chain));

            Item item = items.find(TestChains.ITEM_ID).orElseThrow();
            assertThat(item.holderDid())
                    .as("the holder is the character DID the issuing server stamped, stored verbatim")
                    .isEqualTo(TestChains.CHARACTER_SLOT_1.value());
            // The character-scoped read finds it under the minting character, and not under another
            // character of the same account.
            assertThat(items.findByHolder(TestChains.CHARACTER_SLOT_1))
                    .extracting(Item::itemId)
                    .containsExactly(TestChains.ITEM_ID);
            assertThat(items.findByHolder(TestChains.CHARACTER_SLOT_2)).isEmpty();
        }

        @Test
        @DisplayName("stores each envelope verbatim, so a client can re-verify offline (§6.2)")
        void envelopesAreStoredVerbatim() {
            List<String> documents = TestChains.documentsOf(chains.validChain(2));
            ingress().ingest(documents);

            List<String> stored = provenance.findChain(TestChains.ITEM_ID).stream()
                    .map(StoredProvenanceRecord::envelopeJson)
                    .toList();
            // Re-serializing could change the bytes a signature covers; the received bytes are kept as-is.
            assertThat(stored).containsExactlyElementsOf(documents);
        }

        @Test
        @DisplayName("re-ingesting an item this server already holds does not merge or double-store it")
        void alreadyPresentIsNotMerged() {
            List<String> documents = TestChains.documentsOf(chains.validChain(2));
            ingress().ingest(documents);

            IngressResult second = ingress().ingest(documents);

            assertThat(second.status()).isEqualTo(IngressStatus.ALREADY_PRESENT);
            assertThat(second.itemId()).isEqualTo(TestChains.ITEM_ID);
            assertThat(second.verification().recognized())
                    .as("the chain is still valid; it is simply already held")
                    .isTrue();
            assertThat(provenance.size())
                    .as("no records were appended a second time")
                    .isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ rejection — the point of the slice

    @Nested
    @DisplayName("a chain that fails verification is stored nowhere")
    class Rejected {

        @Test
        @DisplayName("a broken hash link is not recognized")
        void brokenHashLink() {
            List<ProvenanceEnvelope> chain = chains.validChain(2);
            ProvenancePayload wrongLink =
                    TestChains.withPrevRecordHash(chain.get(1).payload(), "sha256-" + "0".repeat(64));
            List<String> documents = List.of(
                    ProvenanceJson.writeEnvelope(chain.get(0)),
                    ProvenanceJson.writeEnvelope(chains.singleIssuer(wrongLink)));

            IngressResult result = ingress().ingest(documents);

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.BROKEN_HASH_LINK))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a rewritten stat with a kept signature is not recognized")
        void tamperedPayloadFailsSignature() {
            ProvenancePayload original = chains.genesis();
            var honestBlock = chains.sign(original, TestChains.HOME_DID);
            // Buff the item but keep the signature the server made over the original bytes.
            ProvenancePayload buffed = TestChains.withAttrs(original, Map.of("power", 9001));
            String forged = ProvenanceJson.writeEnvelope(ProvenanceEnvelope.singleIssuer(buffed, honestBlock));

            IngressResult result = ingress().ingest(List.of(forged));

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.INVALID_SIGNATURE))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("an unrecognized issuer's mint is not recognized")
        void unauthorizedIssuer() {
            // A well-formed, correctly self-signed chain — but issued by a server this node does not
            // recognize. Its key resolves, so the only fault is authority, not a missing key.
            List<String> documents =
                    List.of(ProvenanceJson.writeEnvelope(chains.singleIssuer(chains.genesis(TestChains.ROGUE_DID))));

            IngressResult result = ingress().ingest(documents);

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.UNAUTHORIZED_ISSUER))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a record signed by a key that is not its issuer is not recognized")
        void signerIsNotIssuer() {
            ProvenancePayload genesis = chains.genesis();
            // issuerDid says home, but the block is signed by (and labelled with) the rogue's key.
            String document = ProvenanceJson.writeEnvelope(
                    ProvenanceEnvelope.singleIssuer(genesis, chains.sign(genesis, TestChains.ROGUE_DID)));

            IngressResult result = ingress().ingest(List.of(document));

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.SIGNER_NOT_ISSUER))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a signature under the issuer's kid but the wrong private key is not recognized")
        void forgedSignatureUnderIssuerKid() {
            // Materialize the home key so the directory resolves the kid — as it would in production,
            // where the server's own key is always in its verification directory. Without this the kid
            // resolves to nothing and the (also-correct) fault is UNKNOWN_SIGNING_KEY; with it, the key
            // resolves and the forged signature fails against it, which is the INVALID_SIGNATURE path
            // this test means to exercise.
            chains.keysOf(TestChains.HOME_DID);
            ProvenancePayload genesis = chains.genesis();
            // The kid names the home key, but the bytes were signed with the rogue's private key.
            String document = ProvenanceJson.writeEnvelope(ProvenanceEnvelope.singleIssuer(
                    genesis, chains.signWithKid(genesis, TestChains.ROGUE_DID, TestChains.kidOf(TestChains.HOME_DID))));

            IngressResult result = ingress().ingest(List.of(document));

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.INVALID_SIGNATURE))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a chain whose genesis is not an initial_mint is not recognized")
        void genesisNotInitialMint() {
            String document = ProvenanceJson.writeEnvelope(
                    chains.singleIssuer(chains.genesisWithEvent(ProvenanceEventType.TRADE)));

            IngressResult result = ingress().ingest(List.of(document));

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.GENESIS_NOT_INITIAL_MINT))
                    .isTrue();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a chain presented from the middle (no genesis) is not recognized")
        void chainWithoutGenesis() {
            // Records from the middle of a chain prove nothing about what came before them.
            ProvenancePayload genesis = chains.genesis();
            ProvenancePayload second =
                    chains.following(genesis, ProvenanceEventType.TRADE, TestChains.HOLDER, TestChains.HOME_DID);
            String document = ProvenanceJson.writeEnvelope(chains.singleIssuer(second));

            IngressResult result = ingress().ingest(List.of(document));

            assertThat(result.status()).isEqualTo(IngressStatus.REJECTED);
            assertThat(result.verification().verdict().hasFault(Reason.MISSING_GENESIS))
                    .isTrue();
            assertStoredNothing();
        }
    }

    // ------------------------------------------------------------------ malformed vs. rejected

    @Nested
    @DisplayName("shape errors are distinct from verification failures")
    class Shape {

        @Test
        @DisplayName("no documents at all is EMPTY, not a false rejection")
        void emptyIngest() {
            IngressResult result = ingress().ingest(List.of());

            assertThat(result.status()).isEqualTo(IngressStatus.EMPTY);
            assertThat(result.itemId()).isNull();
            assertThat(result.verification().recognized()).isFalse();
            assertStoredNothing();
        }

        @Test
        @DisplayName("a document that is not a well-formed envelope is a client error, not a rejection")
        void malformedDocument() {
            // Distinct from a well-formed chain that fails verification: this never reaches the verifier.
            assertThatThrownBy(() -> ingress().ingest(List.of("{ this is not an envelope")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertStoredNothing();
        }
    }

    // ------------------------------------------------------------------ the verdict travels with its basis

    @Test
    @DisplayName("the ingress result carries the instant and skew the chain was judged against")
    void verdictCarriesItsBasis() {
        IngressResult result = ingress().ingest(TestChains.documentsOf(chains.validChain(1)));

        // "If you cache a verdict, cache what it was verified against." A bare boolean would have
        // silently expired.
        assertThat(result.verification().verifiedAt()).isEqualTo(TestChains.NOW);
        assertThat(result.verification().maxFutureSkew()).isEqualTo(TestChains.MAX_FUTURE_SKEW);
    }

    @Test
    @DisplayName("the sound-chain hash links actually chain (a control for the tamper tests)")
    void soundChainLinksAreGenuine() {
        List<ProvenanceEnvelope> chain = chains.validChain(2);
        // Confirms the tamper tests are perturbing a chain that was genuinely linked to begin with.
        assertThat(chain.get(1).payload().prevRecordHash())
                .isEqualTo(RecordHash.of(chain.get(0).payload()));
    }
}
