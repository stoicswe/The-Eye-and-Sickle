package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.provenance.DuelCommitteeLookup;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver;
import io.github.stoicswe.eyeandsickle.server.items.DidSigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.items.ItemsProperties;
import io.github.stoicswe.eyeandsickle.server.items.JdbcItemRepository;
import io.github.stoicswe.eyeandsickle.server.items.JdbcProvenanceRepository;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceVerificationService;
import io.github.stoicswe.eyeandsickle.server.items.ServerIssuerAuthority;
import io.github.stoicswe.eyeandsickle.server.items.ServerRecognition;
import io.github.stoicswe.eyeandsickle.server.migration.MigrationItemImporter.ItemImportOutcome;
import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The migration slice against real SQL: the export read ({@link JdbcMigrationItemChains}) round-trips with
 * the real verify-and-store importer ({@link ProvenanceIngressItemImporter}) over a real database and the
 * real provenance verifier.
 *
 * <p>The point is twofold. First, a genuinely-signed item, recognized into the store, reads back out as an
 * exportable chain that <em>still verifies</em> — even though PostgreSQL normalizes the {@code jsonb}
 * envelope, the payload re-canonicalizes deterministically and the signature reproduces. Second — the part
 * that matters most — a tampered chain is recognized by nothing and stored nowhere, so it is not
 * exportable either: the anti-cheat model holds end to end, on the migration path exactly as on the
 * federation edge.
 */
class JdbcMigrationItemChainsIT extends DatabaseIntegrationTestBase {

    private final MigrationTestChains chains = new MigrationTestChains();

    private final ItemsProperties itemsProperties =
            new ItemsProperties(MigrationTestChains.MAX_FUTURE_SKEW, null, null, null);
    private final Clock clock = Clock.fixed(MigrationTestChains.NOW, ZoneOffset.UTC);
    private final ServerRecognition recognition = ServerRecognition.of(Set.of(MigrationTestChains.HOME_DID));

    private MigrationItemImporter importer() {
        return new ProvenanceIngressItemImporter(
                clock,
                itemsProperties,
                chains.signingIdentity(),
                DidPublicKeyResolver.unresolved(),
                recognition,
                jdbcClient(),
                new JdbcItemRepository(jdbcClient()),
                new JdbcProvenanceRepository(jdbcClient()));
    }

    private MigrationItemChains reader() {
        return new JdbcMigrationItemChains(jdbcClient());
    }

    private ProvenanceVerificationService verification() {
        return new ProvenanceVerificationService(
                clock,
                itemsProperties,
                new DidSigningKeyDirectory(chains.signingIdentity(), DidPublicKeyResolver.unresolved()),
                new ServerIssuerAuthority(recognition),
                DuelCommitteeLookup.none());
    }

    @Test
    @DisplayName("a recognized item reads back out as an exportable chain that still verifies")
    void recognizedItemIsExportable() {
        List<String> documents = chains.validDocuments();

        ItemImportOutcome outcome = importer().recognize(documents);
        assertThat(outcome.recognized()).isTrue();
        assertThat(outcome.itemId()).isEqualTo(MigrationTestChains.ITEM_ID);

        List<ItemChain> exported = reader().chainsForHolder(CharacterDid.from(MigrationTestChains.HOLDER_DID));

        assertThat(exported).hasSize(1);
        assertThat(exported.getFirst().itemId()).isEqualTo(MigrationTestChains.ITEM_ID);
        // The exported envelopes re-verify at a destination — the property that actually matters, and the
        // reason byte-for-byte identity with the input is not required (jsonb normalizes the stored text).
        List<ProvenanceEnvelope> reparsed = exported.getFirst().envelopes().stream()
                .map(ProvenanceJson::readEnvelope)
                .toList();
        assertThat(verification().verify(reparsed).recognized()).isTrue();
    }

    @Test
    @DisplayName("a tampered chain is recognized by nothing, stored nowhere, and therefore not exportable")
    void tamperedItemIsNotStoredNorExportable() {
        ItemImportOutcome outcome = importer().recognize(chains.tamperedDocuments());

        assertThat(outcome.recognized())
                .as("a rewritten stat fails its signature")
                .isFalse();
        assertThat(reader().chainsForHolder(CharacterDid.from(MigrationTestChains.HOLDER_DID)))
                .as("nothing was stored, so nothing exports")
                .isEmpty();
    }

    @Test
    @DisplayName("the export read is holder-scoped — another account's items are not returned")
    void exportIsHolderScoped() {
        importer().recognize(chains.validDocuments()); // held by HOLDER_DID

        assertThat(reader().chainsForHolder(CharacterDid.from(MigrationTestChains.OTHER_HOLDER_DID)))
                .isEmpty();
    }
}
