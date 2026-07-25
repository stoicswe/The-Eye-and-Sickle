package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import java.util.Objects;

/**
 * One validator's signed vote on a duel outcome: the outcome it is voting for, and its signature over
 * that outcome's canonical bytes.
 *
 * <p>{@code docs/architecture/05-validator-quorum.md} §5 step 3–4: a sampled validator evaluates the
 * duel and <em>signs the outcome</em>, and the agreed result becomes a {@code duel_grant} provenance
 * event whose {@code issuerDid} is {@code duel:<duelId>} and which carries one signature block per
 * signing validator. So a vote <em>is</em> a candidate {@code duel_grant} payload plus this
 * validator's {@link SignatureBlock} over its canonical bytes. Modelling it as exactly that — rather
 * than as some separate vote message — means the signatures collected here are the very blocks that
 * go into the resolved envelope, with nothing re-signed or re-encoded in between.
 *
 * <p>The signed bytes are {@link ProvenanceJson#canonicalBytes(ProvenancePayload)}, the same input
 * the protocol chain verifier checks. Because the {@code issuerDid} binds the duel id into those
 * bytes, a signature over duel A's outcome cannot be replayed as a vote on duel B — the canonical
 * bytes differ, so the signature would not verify.
 *
 * @param outcome the {@code duel_grant} payload this validator is voting for
 * @param signature this validator's signature over {@code outcome}'s canonical bytes
 */
public record ValidatorSignature(ProvenancePayload outcome, SignatureBlock signature) {

    /** §3.1: a duel outcome's issuer is the synthetic identifier {@code duel:<duelId>}. */
    static final String QUORUM_ISSUER_PREFIX = "duel:";

    public ValidatorSignature {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(signature, "signature");
        if (outcome.eventType() != ProvenanceEventType.DUEL_GRANT) {
            throw new IllegalArgumentException(
                    "A validator vote is a duel_grant outcome, not a " + ProvenanceJson.wireName(outcome.eventType()));
        }
        String issuer = outcome.issuerDid();
        if (!issuer.startsWith(QUORUM_ISSUER_PREFIX) || issuer.length() == QUORUM_ISSUER_PREFIX.length()) {
            throw new IllegalArgumentException("A duel_grant outcome is issued by a committee, so issuerDid must be '"
                    + QUORUM_ISSUER_PREFIX + "<duelId>'; got '" + issuer + "'");
        }
    }

    /**
     * The duel this vote is about — the identifier after the {@code duel:} prefix of the outcome's
     * issuer.
     *
     * @return the duel id
     */
    public String duelId() {
        return outcome.issuerDid().substring(QUORUM_ISSUER_PREFIX.length());
    }

    /**
     * The validator that cast this vote — the DID portion of the signature's {@code kid}.
     *
     * <p>This is the identity resolved to a public key and the identity whose reputation the §3 update
     * moves. It is <em>not</em> read from the outcome (the outcome names {@code duel:<id>} as issuer,
     * not any one validator); a committee-issued document has no single author, so the signer comes
     * from the signature.
     *
     * @return the signing validator's DID
     */
    public String validatorDid() {
        return signature.signerDid();
    }

    /**
     * The exact bytes this signature covers.
     *
     * @return the outcome's RFC 8785 canonical bytes
     */
    public byte[] canonicalBytes() {
        return ProvenanceJson.canonicalBytes(outcome);
    }
}
