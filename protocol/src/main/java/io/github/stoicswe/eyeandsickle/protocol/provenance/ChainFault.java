package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.Objects;

/**
 * One reason a provenance chain is not recognized, pinned to the record that caused it.
 *
 * <p>A verifier that answers only "false" is useless twice over: the player-facing item-history view
 * ({@code docs/architecture/04-item-provenance.md} §6.1) has nothing to show beyond a red cross, and
 * an operator arguing a federation dispute has nothing to argue with. So every check that can fail
 * produces one of these, naming the record and the reason.
 *
 * <h2>Why this is chatty when {@code SecureChannelException} is deliberately mute</h2>
 *
 * The transport layer refuses to say <em>why</em> a frame was rejected, because an attacker who can
 * distinguish failure modes learns how far their forgery got and can grind toward a valid one. That
 * reasoning does not carry over here. A provenance chain is public, offline-verifiable data: the
 * attacker already holds every input and can run this verifier themselves as often as they like.
 * Withholding the reason would cost the honest player their history view and buy nothing.
 *
 * @param position the record's index in the chain as supplied, or {@code -1} for a fault about the
 *     chain as a whole
 * @param chainDepth the depth the record <em>claims</em>, or {@code -1} for a whole-chain fault.
 *     Reported alongside {@code position} rather than instead of it, because a record lying about
 *     its depth is itself one of the failures being reported
 * @param reason the machine-readable classification
 * @param detail a human-readable explanation, for logs and the history view
 */
public record ChainFault(int position, int chainDepth, Reason reason, String detail) {

    public ChainFault {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * A fault attributable to one record.
     *
     * @param position the record's index in the supplied chain
     * @param payload the offending record
     * @param reason the classification
     * @param detail the explanation
     * @return the fault
     */
    public static ChainFault at(int position, ProvenancePayload payload, Reason reason, String detail) {
        return new ChainFault(position, payload.chainDepth(), reason, detail);
    }

    /**
     * A fault about the chain rather than about any one record.
     *
     * @param reason the classification
     * @param detail the explanation
     * @return the fault
     */
    public static ChainFault forChain(Reason reason, String detail) {
        return new ChainFault(-1, -1, reason, detail);
    }

    @Override
    public String toString() {
        String where = position < 0 ? "chain" : "record #" + position + " (claims chainDepth " + chainDepth + ")";
        return where + ": " + reason + " - " + detail;
    }

    /** Why a chain was not recognized. */
    public enum Reason {

        /** Nothing was supplied. An item with no provenance has no provenance. */
        EMPTY_CHAIN,

        /** The payload announces a schema version this build does not know how to read. */
        UNSUPPORTED_RECORD_VERSION,

        /** The envelope was canonicalized by some scheme other than JCS (RFC 8785). */
        UNSUPPORTED_CANONICALIZATION,

        /** A record belongs to a different item. Chains are per-item ({@code 04} §6). */
        ITEM_ID_MISMATCH,

        /** The chain does not start at genesis, so it cannot be walked back to one. */
        MISSING_GENESIS,

        /** The genesis record is not an {@code initial_mint}. */
        GENESIS_NOT_INITIAL_MINT,

        /** An {@code initial_mint} appears somewhere other than genesis — an item minted twice. */
        NON_GENESIS_MINT,

        /** A record's {@code chainDepth} skips ahead: records are missing from the walk. */
        CHAIN_DEPTH_GAP,

        /** A record's {@code chainDepth} repeats or goes backwards. */
        CHAIN_DEPTH_OUT_OF_ORDER,

        /** A record's {@code prevRecordHash} does not name the record before it. */
        BROKEN_HASH_LINK,

        /** A nonce appears twice in the chain — the signature of a replayed record. */
        REPLAYED_NONCE,

        /** A record's {@code timestamp} is not a parseable ISO-8601 instant. */
        MALFORMED_TIMESTAMP,

        /** A record is dated before the record it follows. */
        TIMESTAMP_NOT_MONOTONIC,

        /** A record is dated further ahead than the tolerated clock skew allows. */
        TIMESTAMP_IN_FUTURE,

        /** A signature block names an algorithm other than EdDSA. */
        WRONG_SIGNATURE_ALGORITHM,

        /** A signature's {@code sig} is not decodable base64url. */
        MALFORMED_SIGNATURE,

        /** No public key could be resolved for a signature's {@code kid}. */
        UNKNOWN_SIGNING_KEY,

        /** A signature does not verify over the record's canonical bytes. */
        INVALID_SIGNATURE,

        /** A single-issuer event carries more than one signature. */
        UNEXPECTED_MULTI_SIGNATURE,

        /** The signing key's DID is not the DID the record names as issuer. */
        SIGNER_NOT_ISSUER,

        /** The named issuer may not issue this event type ({@code 04} §7 step 3). */
        UNAUTHORIZED_ISSUER,

        /** A {@code duel_grant}'s {@code issuerDid} is not of the form {@code duel:<duelId>}. */
        MALFORMED_QUORUM_ISSUER,

        /** No sampling record is available for the duel, so its committee cannot be checked. */
        UNKNOWN_DUEL_COMMITTEE,

        /** A signature comes from a validator that was not sampled for this duel ({@code 04} §7.1). */
        VALIDATOR_NOT_SAMPLED,

        /** One validator signed twice, which would double-count its weight. */
        DUPLICATE_VALIDATOR_SIGNATURE,

        /** The agreeing validators do not clear the {@code 2f+1}-of-{@code 3f+1} threshold. */
        QUORUM_NOT_REACHED
    }
}
