package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies an item's provenance chain arriving from another server, and stores it only if it is
 * recognized.
 *
 * <h2>Verify before recognize — this is the whole anti-cheat model</h2>
 *
 * {@code docs/architecture/04-item-provenance.md} §7: a chain that fails any check is <strong>not
 * recognized</strong>, and federation-wide that is how a cheating server's fabricated items become
 * worthless ({@code 03} §4). This service enforces exactly that ordering: it runs the full chain walk
 * first, and a chain that does not clear it is stored <em>nowhere</em>. Nothing here ever writes an
 * unverified chain as though it were verified — the store step is unreachable unless the verdict is
 * recognized.
 *
 * <h2>The verdict travels with what it was checked against</h2>
 *
 * The {@link IngressResult} carries the {@link ProvenanceVerificationService.Result}, which records the
 * instant and skew the chain was judged against. A caller that logs or forwards the outcome is thus
 * recording what it was verified against, not a bare "valid" that has silently expired — the guidance
 * "if you cache a verdict, cache what it was verified against".
 *
 * <h2>The incoming holder is a character, recorded verbatim</h2>
 *
 * Since {@code docs/architecture/09-player-state-portability.md} §9 (Q-item-keying option 3), an item's
 * holder is a <em>character</em> DID ({@code did:eyeandsickle:<slot>:<accountDid>}), produced by the
 * issuing server. Ingress does not change mechanically: the verifier checks the <em>issuer's</em>
 * signature and is indifferent to what the holder string is, so a character-DID holder is verified and
 * recorded exactly as any holder string was before. This service neither parses nor validates the holder
 * beyond the chain checks — it stores the tip's holder verbatim onto the {@code items} row, and a
 * character-scoped inventory read ({@code ItemStore.findByHolder}) then keys on that same character DID.
 *
 * <h2>Scope: a new item, not a chain merge</h2>
 *
 * This ingests an item this server does not already hold. Reconciling an incoming chain against a
 * partial local copy — a trade that extends a chain we already have a prefix of — is a distinct
 * problem: it needs a rule for what to do when the incoming and local tips disagree, which the docs do
 * not give. An already-present item is therefore reported as {@link IngressStatus#ALREADY_PRESENT} and
 * left untouched, and that merge rule is recorded as undecided.
 */
public class ProvenanceIngressService {

    private final ProvenanceVerificationService verification;
    private final ItemStore items;
    private final ProvenanceStore provenance;
    private final ItemsProperties properties;
    private final Clock clock;

    /**
     * @param verification the chain verifier wired with this server's key resolution and authority
     * @param items item persistence
     * @param provenance provenance-chain persistence
     * @param properties supplies the landing storage tier for an ingested item
     * @param clock source of the {@code acquiredAt}/{@code recordedAt} instants
     */
    public ProvenanceIngressService(
            ProvenanceVerificationService verification,
            ItemStore items,
            ProvenanceStore provenance,
            ItemsProperties properties,
            Clock clock) {
        this.verification = Objects.requireNonNull(verification, "verification");
        this.items = Objects.requireNonNull(items, "items");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ingests a received chain.
     *
     * @param envelopeDocuments the item's records as the federation layer received them, each the
     *     verbatim JSON of one detached-JWS envelope, ordered genesis-first
     * @return what happened, including the verdict and the basis it was reached on
     * @throws IllegalArgumentException if a document is not a well-formed envelope (a client/peer error,
     *     distinct from a well-formed chain that fails verification)
     */
    @Transactional
    public IngressResult ingest(List<String> envelopeDocuments) {
        Objects.requireNonNull(envelopeDocuments, "envelopeDocuments");
        if (envelopeDocuments.isEmpty()) {
            return new IngressResult(IngressStatus.EMPTY, null, verification.verify(List.of()));
        }

        // Parse each document once. The verbatim string is kept beside the parse so the stored envelope
        // is the exact bytes received — re-serializing could change what a signature covers.
        List<ProvenanceEnvelope> envelopes = new ArrayList<>(envelopeDocuments.size());
        for (String document : envelopeDocuments) {
            envelopes.add(ProvenanceJson.readEnvelope(document));
        }

        ProvenanceVerificationService.Result result = verification.verify(envelopes);
        UUID itemId = envelopes.getFirst().payload().itemId();
        if (!result.recognized()) {
            // Not recognized -> stored nowhere. This is the point of the whole exercise.
            return new IngressResult(IngressStatus.REJECTED, itemId, result);
        }
        if (items.exists(itemId)) {
            return new IngressResult(IngressStatus.ALREADY_PRESENT, itemId, result);
        }

        store(envelopes, envelopeDocuments);
        return new IngressResult(IngressStatus.RECOGNIZED_STORED, itemId, result);
    }

    private void store(List<ProvenanceEnvelope> envelopes, List<String> documents) {
        Instant now = clock.instant();
        ProvenancePayload tip = envelopes.getLast().payload();

        // The item projection mirrors the verified chain tip (04 §2). A transferred-in item lands in the
        // configured tier ([PROPOSAL] — see ItemsProperties), never socketed. tip.holderDid() is the
        // character DID the issuing server stamped (09 §9); it is stored verbatim, not reinterpreted.
        items.insert(new Item(
                tip.itemId(),
                tip.itemType(),
                tip.itemAttrs(),
                tip.holderDid(),
                properties.ingressLandingTier(),
                null,
                now,
                0L));

        for (int i = 0; i < envelopes.size(); i++) {
            StoredProvenanceRecord record =
                    StoredProvenanceRecord.from(UUID.randomUUID(), envelopes.get(i), documents.get(i), now);
            provenance.append(record);
        }
    }

    /** The outcome of an ingest attempt. */
    public enum IngressStatus {

        /** The chain was recognized and stored. */
        RECOGNIZED_STORED,

        /** The chain failed verification and was stored nowhere. */
        REJECTED,

        /** The item is already held here; the incoming chain was not merged (see class note). */
        ALREADY_PRESENT,

        /** No records were supplied. */
        EMPTY
    }

    /**
     * The result of an ingest.
     *
     * @param status what happened
     * @param itemId the item the chain describes, or {@code null} if none was supplied
     * @param verification the verdict and the instant/skew it was reached against
     */
    public record IngressResult(IngressStatus status, UUID itemId, ProvenanceVerificationService.Result verification) {

        public IngressResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(verification, "verification");
        }

        /** Whether the chain was recognized and stored. */
        public boolean stored() {
            return status == IngressStatus.RECOGNIZED_STORED;
        }
    }
}
