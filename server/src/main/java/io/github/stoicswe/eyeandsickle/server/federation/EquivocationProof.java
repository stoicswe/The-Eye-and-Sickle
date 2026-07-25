package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Self-contained proof that one validator signed two conflicting outcomes for the same duel — the
 * cryptographic basis for a hard slash and an automatic federation-wide flag ({@code
 * docs/architecture/05-validator-quorum.md} §3.3, {@code docs/architecture/03} §4).
 *
 * <p>Equivocation is the one anti-cheat verdict that needs <em>no</em> trust in whoever reports it,
 * because the evidence carries its own refutation: two signatures by the same key over two different
 * canonical byte-strings, both of which verify. Any honest peer handed this proof can re-check it and
 * reach the same conclusion. That is why {@link #evidence()} bundles both signed votes verbatim into
 * the object stored in {@code flagged_servers.evidence} — a flag a peer cannot independently verify
 * would be a rumour, and a federation with no central authority cannot act on rumours.
 *
 * @param validatorDid the equivocating validator's server DID — the subject of both the slash and the
 *     flag
 * @param duelId the duel both conflicting outcomes claim to resolve
 * @param first one of the two conflicting signed votes
 * @param second the other; its canonical outcome bytes differ from {@code first}'s and both
 *     signatures verify under {@code validatorDid}'s key
 */
public record EquivocationProof(
        String validatorDid, String duelId, ValidatorSignature first, ValidatorSignature second) {

    public EquivocationProof {
        Objects.requireNonNull(validatorDid, "validatorDid");
        Objects.requireNonNull(duelId, "duelId");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }

    /**
     * The proof as a JSON object, for {@code flagged_servers.evidence}.
     *
     * <p>Each conflicting vote is rendered as its canonical outcome JSON plus the signature block that
     * covers it — everything a peer needs to re-run the two verifications for itself. The canonical
     * form is used (not a re-serialisation) so the stored bytes are exactly the bytes the signatures
     * were checked against; storing a prettier rendering would make the evidence unverifiable against
     * its own signatures.
     *
     * @return an object suitable for {@code Jsonb.writeObject}
     */
    public Map<String, Object> evidence() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("kind", "equivocation");
        root.put("validatorDid", validatorDid);
        root.put("duelId", duelId);
        root.put("conflictingOutcomes", List.of(voteEvidence(first), voteEvidence(second)));
        return root;
    }

    private static Map<String, Object> voteEvidence(ValidatorSignature vote) {
        Map<String, Object> entry = new LinkedHashMap<>();
        // The canonical outcome JSON, verbatim: this is the exact string the signature covers, so a
        // verifier re-canonicalizes nothing and cannot be tricked by a re-encoding that changed a byte.
        entry.put("outcomeCanonical", ProvenanceJson.canonicalJson(vote.outcome()));
        Map<String, Object> sig = new LinkedHashMap<>();
        sig.put("alg", vote.signature().alg());
        sig.put("kid", vote.signature().kid());
        sig.put("sig", vote.signature().sig());
        entry.put("signature", sig);
        return entry;
    }
}
