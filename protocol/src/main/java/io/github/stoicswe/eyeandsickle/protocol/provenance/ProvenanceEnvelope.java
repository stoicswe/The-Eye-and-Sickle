package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.List;
import java.util.Objects;

/**
 * What actually travels over the wire: a payload plus the detached signature(s) over its canonical
 * form.
 *
 * <p>From {@code docs/architecture/04-item-provenance.md} §3 and §3.1. "Detached" means the signature
 * does not embed the payload — they travel together but separately — which keeps the payload
 * human-readable and loggable while staying tamper-evident.
 *
 * <h2>⚠ Open wire-format decision</h2>
 *
 * The architecture doc shows two shapes and does not reconcile them: §3 has a single {@code
 * "signature"} <em>object</em> with {@code alg}/{@code kid}/{@code sig}, while §3.1 has a {@code
 * "signatures"} <em>array</em> whose elements carry only {@code kid}/{@code sig}. This type models
 * the general case — always a list, {@code alg} always present per block — because one shape is far
 * easier to verify than two, and a single-issuer record is just a list of length one.
 *
 * <p>That is a scaffolding decision, not a design ruling. It needs confirming before anything signs
 * for real, and logging in {@code docs/design/15-open-questions.md} §3 once it is.
 *
 * @param payload the signed content
 * @param payloadCanonicalization the canonicalization applied before signing; always {@link
 *     #JCS_RFC8785} today, recorded explicitly so a future migration stays verifiable
 * @param signatures one block for a single-issuer record; one per signing validator for a duel
 *     outcome
 */
public record ProvenanceEnvelope(
        ProvenancePayload payload, String payloadCanonicalization, List<SignatureBlock> signatures) {

    /** The only canonicalization currently used. */
    public static final String JCS_RFC8785 = "JCS-RFC8785";

    public ProvenanceEnvelope {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadCanonicalization, "payloadCanonicalization");
        Objects.requireNonNull(signatures, "signatures");
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("A provenance envelope must carry at least one signature");
        }
        signatures = List.copyOf(signatures);
    }

    /**
     * Wraps a payload with a single issuer's signature — a mint, server grant, or trade.
     *
     * @param payload the signed content
     * @param signature the issuer's signature block
     * @return the envelope
     */
    public static ProvenanceEnvelope singleIssuer(ProvenancePayload payload, SignatureBlock signature) {
        return new ProvenanceEnvelope(payload, JCS_RFC8785, List.of(signature));
    }

    /**
     * Wraps a duel outcome with the sampled validator committee's signatures.
     *
     * <p>Whether enough of them signed is not this type's business — the threshold is
     * reputation-weighted {@code 2f+1} of {@code 3f+1} ({@code
     * docs/architecture/05-validator-quorum.md} §1), which needs the sampling record and each
     * validator's weight. This type carries the evidence; the verifier judges it.
     *
     * @param payload the signed content, whose {@code eventType} should be {@link
     *     ProvenanceEventType#DUEL_GRANT}
     * @param signatures the validators' signature blocks
     * @return the envelope
     */
    public static ProvenanceEnvelope quorum(ProvenancePayload payload, List<SignatureBlock> signatures) {
        return new ProvenanceEnvelope(payload, JCS_RFC8785, signatures);
    }

    /** Whether this envelope was signed by a validator committee rather than a single issuer. */
    public boolean isMultiSignature() {
        return signatures.size() > 1;
    }
}
