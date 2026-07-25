package io.github.stoicswe.eyeandsickle.protocol.provenance;

/**
 * The kinds of event that can appear in an item's provenance chain.
 *
 * <p>Transcribed from {@code docs/architecture/04-item-provenance.md} §2. The set is closed on
 * purpose: a verifier decides which issuer is authorized for a given event type, so an unrecognized
 * event type is an unverifiable record, not an extension point.
 */
public enum ProvenanceEventType {

    /** The item's genesis record. Chain depth 0, no previous record hash. */
    INITIAL_MINT,

    /** A home server granting an item to one of its own players. Single-issuer. */
    SERVER_GRANT,

    /** An item changing hands. Single-issuer. */
    TRADE,

    /**
     * An item changing hands as the outcome of a cross-server duel.
     *
     * <p>The only <em>multi-signature</em> event type. Its issuer is not a single server but a
     * synthetic quorum identifier ({@code duel:<duelId>}), and the envelope carries the signatures of
     * the sampled validator committee instead of one server's signature — because no single arbiter
     * decides a cross-server adversarial outcome (Invariant I15). See {@code
     * docs/architecture/05-validator-quorum.md}.
     */
    DUEL_GRANT
}
