package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver;
import io.github.stoicswe.eyeandsickle.server.items.DidSigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.items.DuelCommitteeLookupJdbc;
import io.github.stoicswe.eyeandsickle.server.items.ItemStore;
import io.github.stoicswe.eyeandsickle.server.items.ItemsProperties;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceIngressService;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceIngressService.IngressResult;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceStore;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceVerificationService;
import io.github.stoicswe.eyeandsickle.server.items.ServerIssuerAuthority;
import io.github.stoicswe.eyeandsickle.server.items.ServerRecognition;
import io.github.stoicswe.eyeandsickle.server.items.ServerSigningIdentity;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The default {@link MigrationItemImporter}: re-verifies and recognizes a migrated item by leaning on the
 * items slice's {@code ProvenanceIngressService} — the one place the verify-before-recognize anti-cheat
 * model lives ({@code docs/architecture/04-item-provenance.md} §7).
 *
 * <h2>Why the ingress graph is assembled here from concrete beans</h2>
 *
 * The items slice's verification and ingress services are plain classes, not yet contributed to the
 * application context (their integration step had not run). Rather than wait on that, this adapter builds
 * the exact same graph from beans that <em>do</em> exist — the server's signing identity, its DID-key
 * resolver, its issuer recognition, the clock, the item and provenance stores — so migration import runs
 * the identical logic a federation transfer would. It constructs the collaborators as local objects rather
 * than declaring them as container beans, so it introduces no broadly-typed bean (a {@code
 * SigningKeyDirectory}, say) that could collide with another slice's. When the items slice publishes its
 * own {@link MigrationItemImporter}-equivalent, {@code @ConditionalOnMissingBean} on the migration
 * configuration lets it supersede this without a change here.
 *
 * <p>The ingest runs inside the caller's transaction ({@link CharacterImportService} is
 * {@code @Transactional}), so a recognized item's row and its chain are committed atomically with the
 * fresh character — and a rejected chain, as ever, is stored nowhere.
 */
@Component
class ProvenanceIngressItemImporter implements MigrationItemImporter {

    private final ProvenanceIngressService ingress;

    ProvenanceIngressItemImporter(
            Clock clock,
            ItemsProperties itemsProperties,
            ServerSigningIdentity signingIdentity,
            DidPublicKeyResolver didPublicKeyResolver,
            ServerRecognition serverRecognition,
            org.springframework.jdbc.core.simple.JdbcClient jdbcClient,
            ItemStore itemStore,
            ProvenanceStore provenanceStore) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(itemsProperties, "itemsProperties");
        var keys = new DidSigningKeyDirectory(
                Objects.requireNonNull(signingIdentity, "signingIdentity"),
                Objects.requireNonNull(didPublicKeyResolver, "didPublicKeyResolver"));
        var authority = new ServerIssuerAuthority(Objects.requireNonNull(serverRecognition, "serverRecognition"));
        var committees = new DuelCommitteeLookupJdbc(Objects.requireNonNull(jdbcClient, "jdbcClient"));
        var verification = new ProvenanceVerificationService(clock, itemsProperties, keys, authority, committees);
        this.ingress = new ProvenanceIngressService(
                verification,
                Objects.requireNonNull(itemStore, "itemStore"),
                Objects.requireNonNull(provenanceStore, "provenanceStore"),
                itemsProperties,
                clock);
    }

    @Override
    public ItemImportOutcome recognize(List<String> envelopeDocuments) {
        Objects.requireNonNull(envelopeDocuments, "envelopeDocuments");
        IngressResult result = ingress.ingest(envelopeDocuments);
        // Recognized-and-stored and already-present both mean the item is legitimately held here; only a
        // failed verification (REJECTED) or an empty submission (EMPTY) is "not recognized". A REJECTED
        // result still carries the itemId the chain claimed, which the caller records as dropped.
        boolean recognized =
                switch (result.status()) {
                    case RECOGNIZED_STORED, ALREADY_PRESENT -> true;
                    case REJECTED, EMPTY -> false;
                };
        return new ItemImportOutcome(result.itemId(), recognized);
    }
}
