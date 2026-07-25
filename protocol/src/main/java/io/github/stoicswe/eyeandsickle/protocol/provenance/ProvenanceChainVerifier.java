package io.github.stoicswe.eyeandsickle.protocol.provenance;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainFault.Reason;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Walks an item's provenance chain and decides whether it is recognized.
 *
 * <p>This is the verification algorithm of {@code docs/architecture/04-item-provenance.md} §7, and
 * the thing the whole module exists for. A chain that fails any check is <strong>not
 * recognized</strong> — federation-wide, that is how a cheating server's fabricated items become
 * worthless ({@code 03} §4). Nothing here decides what an item <em>does</em>; it only decides whether
 * the record claiming it exists is believable.
 *
 * <h2>What it checks, per record</h2>
 *
 * <ol>
 *   <li><strong>Shape</strong> — a schema version and canonicalization this build understands, and
 *       the same {@code itemId} throughout, because chains are per-item ({@code 04} §6).
 *   <li><strong>Position</strong> — {@code chainDepth} contiguous from 0 and matching the record's
 *       place in the walk, so a record cannot be dropped, duplicated or reordered unnoticed.
 *   <li><strong>Linkage</strong> — {@code prevRecordHash} names the record before it, all the way
 *       back to a genesis {@code initial_mint} (§7 step 3).
 *   <li><strong>Replay</strong> — nonces unique within the chain and timestamps non-decreasing and
 *       not implausibly future-dated (§2's stated purpose for those two fields).
 *   <li><strong>Authority</strong> — for a single-issuer record the one signature must resolve to the
 *       authorized issuer DID for that event type; for a {@code duel_grant}, every signature must
 *       belong to a validator actually sampled for that duel and the agreeing weight must clear the
 *       {@code 2f+1}-of-{@code 3f+1} threshold ({@code 05} §1).
 * </ol>
 *
 * <h2>Offline by construction</h2>
 *
 * No I/O, no clock, no randomness. Key resolution, duel sampling records, the current instant and the
 * tolerated skew all arrive in a {@link ChainVerificationContext}. That is what lets a player's
 * client re-verify the history it is being shown without trusting the server that showed it ({@code
 * 04} §6.2), and it means the client and the server run byte-identical logic rather than two
 * implementations that agree until they do not.
 *
 * <h2>Records are supplied genesis-first</h2>
 *
 * The chain is a {@link List} ordered from depth 0 upward. §6.1 stores {@code chainDepth} exactly so
 * a client can fetch records N..N+20 instead of walking from the tip, and this verifier takes them in
 * that order. A caller holding records from the middle of a chain gets {@link Reason#MISSING_GENESIS}
 * — correctly, since a partial walk proves nothing about what came before it.
 */
public final class ProvenanceChainVerifier {

    /** §3.1: a duel outcome's issuer is the synthetic identifier {@code duel:<duelId>}. */
    private static final String QUORUM_ISSUER_PREFIX = "duel:";

    /**
     * Slack on the weighted-quorum comparison. Sampling weights are IEEE-754 doubles summed in
     * arbitrary map order, so an outcome that sits exactly on the threshold can land a few ulps under
     * it depending on which server does the addition. Rejecting a legitimate duel because of
     * floating-point ordering would be a federation-splitting bug, and a relative tolerance this
     * small cannot admit a quorum that is genuinely short.
     */
    private static final double WEIGHT_TOLERANCE = 1e-9;

    private ProvenanceChainVerifier() {}

    /**
     * Verifies an item's chain from genesis to tip.
     *
     * @param chain the item's records, ordered genesis-first
     * @param context key resolution, issuer authority, duel sampling records, and the instant and
     *     skew to judge timestamps against
     * @return a verdict naming every failed check, empty if the chain is recognized
     */
    public static ChainVerdict verify(List<ProvenanceEnvelope> chain, ChainVerificationContext context) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(context, "context");

        List<ChainFault> faults = new ArrayList<>();
        if (chain.isEmpty()) {
            faults.add(ChainFault.forChain(
                    Reason.EMPTY_CHAIN, "No records supplied; an item with no provenance has no provenance"));
            return new ChainVerdict(faults);
        }

        UUID itemId = chain.getFirst().payload().itemId();
        Map<String, Integer> nonceFirstSeenAt = new HashMap<>();
        Instant latestAcceptable = context.latestAcceptableTimestamp();
        Instant previousTimestamp = null;
        String expectedPrevRecordHash = null;

        for (int position = 0; position < chain.size(); position++) {
            ProvenanceEnvelope envelope = Objects.requireNonNull(chain.get(position), "chain element");
            ProvenancePayload payload = envelope.payload();

            // Canonicalized once and reused: it is both the signature input and the input to the
            // hash the next record chains to. Deriving them from the same bytes is what guarantees
            // that "the signature covers what the chain links" is true by construction.
            byte[] canonical = ProvenanceJson.canonicalBytes(payload);

            checkShape(position, envelope, itemId, faults);
            checkPosition(position, payload, faults);
            checkLinkage(position, payload, expectedPrevRecordHash, faults);
            checkNonce(position, payload, nonceFirstSeenAt, faults);
            previousTimestamp = checkTimestamp(position, payload, previousTimestamp, latestAcceptable, faults);

            if (payload.eventType() == ProvenanceEventType.DUEL_GRANT) {
                checkQuorumSignatures(position, envelope, canonical, context, faults);
            } else {
                checkSingleIssuerSignature(position, envelope, canonical, context, faults);
            }

            expectedPrevRecordHash = RecordHash.ofCanonicalBytes(canonical);
        }
        return new ChainVerdict(faults);
    }

    // ------------------------------------------------------------------ per-record checks

    private static void checkShape(int position, ProvenanceEnvelope envelope, UUID itemId, List<ChainFault> faults) {
        ProvenancePayload payload = envelope.payload();
        if (payload.recordVersion() != ProvenancePayload.CURRENT_RECORD_VERSION) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNSUPPORTED_RECORD_VERSION,
                    "Record announces schema version " + payload.recordVersion() + "; this build verifies version "
                            + ProvenancePayload.CURRENT_RECORD_VERSION
                            + ", and a payload it cannot read is a payload it cannot vouch for"));
        }
        if (!ProvenanceEnvelope.JCS_RFC8785.equals(envelope.payloadCanonicalization())) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNSUPPORTED_CANONICALIZATION,
                    "Envelope declares canonicalization '" + envelope.payloadCanonicalization()
                            + "'; this build signs and verifies " + ProvenanceEnvelope.JCS_RFC8785));
        }
        if (!itemId.equals(payload.itemId())) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.ITEM_ID_MISMATCH,
                    "Record belongs to item " + payload.itemId() + " but this chain is for " + itemId
                            + "; chains are per-item"));
        }
    }

    private static void checkPosition(int position, ProvenancePayload payload, List<ChainFault> faults) {
        if (payload.chainDepth() > position) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.CHAIN_DEPTH_GAP,
                    "Record claims depth " + payload.chainDepth() + " at walk position " + position + "; "
                            + (payload.chainDepth() - position) + " record(s) are missing before it"));
        } else if (payload.chainDepth() < position) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.CHAIN_DEPTH_OUT_OF_ORDER,
                    "Record claims depth " + payload.chainDepth() + " at walk position " + position
                            + "; depth must be contiguous from 0 and must match position"));
        }

        if (position == 0) {
            if (!payload.isGenesis()) {
                faults.add(ChainFault.at(
                        position,
                        payload,
                        Reason.MISSING_GENESIS,
                        "The chain does not start at genesis, so it cannot be walked back to one"));
            }
            if (payload.eventType() != ProvenanceEventType.INITIAL_MINT) {
                faults.add(ChainFault.at(
                        position,
                        payload,
                        Reason.GENESIS_NOT_INITIAL_MINT,
                        "A chain begins with an initial_mint; this one begins with "
                                + ProvenanceJson.wireName(payload.eventType())));
            }
        } else if (payload.eventType() == ProvenanceEventType.INITIAL_MINT) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.NON_GENESIS_MINT,
                    "An initial_mint appears at position " + position + "; an item is minted exactly once"));
        }
    }

    private static void checkLinkage(
            int position, ProvenancePayload payload, String expectedPrevRecordHash, List<ChainFault> faults) {
        if (position == 0) {
            return;
        }
        if (payload.prevRecordHash() == null) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.BROKEN_HASH_LINK,
                    "Record carries no prevRecordHash at position " + position + ", so nothing ties it to "
                            + "the record before it"));
        } else if (!payload.prevRecordHash().equals(expectedPrevRecordHash)) {
            String shape = RecordHash.isWellFormed(payload.prevRecordHash())
                    ? "names " + payload.prevRecordHash()
                    : "names an unreadable digest '" + payload.prevRecordHash() + "'";
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.BROKEN_HASH_LINK,
                    "Record " + shape + " but the record before it hashes to " + expectedPrevRecordHash));
        }
    }

    private static void checkNonce(
            int position, ProvenancePayload payload, Map<String, Integer> nonceFirstSeenAt, List<ChainFault> faults) {
        Integer firstSeen = nonceFirstSeenAt.putIfAbsent(payload.nonce(), position);
        if (firstSeen != null) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.REPLAYED_NONCE,
                    "Nonce already used by the record at position " + firstSeen
                            + "; a repeated nonce is what an old record replayed as a new event looks like"));
        }
    }

    /**
     * @return the timestamp to compare the next record against — this record's if it parsed,
     *     otherwise the one carried in, so a single unreadable timestamp does not cascade into a
     *     false ordering fault on every record after it
     */
    private static Instant checkTimestamp(
            int position,
            ProvenancePayload payload,
            Instant previousTimestamp,
            Instant latestAcceptable,
            List<ChainFault> faults) {
        Instant timestamp;
        try {
            timestamp = Instant.parse(payload.timestamp());
        } catch (DateTimeParseException e) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.MALFORMED_TIMESTAMP,
                    "Timestamp '" + payload.timestamp() + "' is not an ISO-8601 instant"));
            return previousTimestamp;
        }
        if (timestamp.isAfter(latestAcceptable)) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.TIMESTAMP_IN_FUTURE,
                    "Timestamp " + timestamp + " is later than the tolerated horizon " + latestAcceptable));
        }
        if (previousTimestamp != null && timestamp.isBefore(previousTimestamp)) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.TIMESTAMP_NOT_MONOTONIC,
                    "Timestamp " + timestamp + " precedes the previous record's " + previousTimestamp
                            + "; an item's history cannot run backwards"));
        }
        return timestamp;
    }

    // ------------------------------------------------------------------ signatures

    private static void checkSingleIssuerSignature(
            int position,
            ProvenanceEnvelope envelope,
            byte[] canonical,
            ChainVerificationContext context,
            List<ChainFault> faults) {
        ProvenancePayload payload = envelope.payload();
        List<SignatureBlock> blocks = envelope.signatures();
        if (blocks.size() != 1) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNEXPECTED_MULTI_SIGNATURE,
                    "A " + ProvenanceJson.wireName(payload.eventType()) + " has exactly one issuer, but this "
                            + "envelope carries " + blocks.size() + " signatures"));
            return;
        }
        SignatureBlock block = blocks.getFirst();
        if (!block.signerDid().equals(payload.issuerDid())) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.SIGNER_NOT_ISSUER,
                    "Signed by " + block.signerDid() + " but the record names " + payload.issuerDid()
                            + " as its issuer"));
        }
        if (!context.issuerAuthority().isAuthorizedIssuer(payload)) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNAUTHORIZED_ISSUER,
                    payload.issuerDid() + " is not authorized to issue a "
                            + ProvenanceJson.wireName(payload.eventType()) + " for this item"));
        }
        checkSignature(position, payload, block, canonical, context, faults);
    }

    /**
     * §7 steps 1–2 for a duel outcome.
     *
     * <p><strong>[PROPOSAL] — two deliberate strictnesses beyond the doc's wording.</strong>
     *
     * <p>First, §7.2 talks about "the summed reputation-weight of <em>valid</em> signatures", which
     * could be read as quietly discarding a signature that fails to verify and passing the record
     * anyway if the rest still clear the threshold. This implementation instead reports every
     * unverifiable signature as a fault. The trade is deliberate: tolerating them would make an item
     * survive a rotated or revoked validator key, but it also means a verifier silently accepts
     * records carrying garbage it cannot explain. Given that the doc's own framing is
     * "not recognized" rather than "best effort", refusing is the safer default — and the failure it
     * causes is loud and fixable, while the failure it prevents is a permanently laundered item.
     *
     * <p>Second, the threshold is enforced on the <em>count</em> of agreeing validators as well as on
     * their weight. §7.2 and {@code 05} §1 speak only of weight, but weight alone lets a single
     * validator holding most of a committee's reputation decide an outcome by itself, which is
     * precisely what Invariant I15 forbids. Requiring both never rejects the doc's worked example
     * (5 of 7 signing is 5 signatures and five sevenths of the weight), so the extra condition costs
     * nothing legitimate. Both of these need confirming in {@code docs/design/15-open-questions.md}.
     */
    private static void checkQuorumSignatures(
            int position,
            ProvenanceEnvelope envelope,
            byte[] canonical,
            ChainVerificationContext context,
            List<ChainFault> faults) {
        ProvenancePayload payload = envelope.payload();
        String issuer = payload.issuerDid();
        if (!issuer.startsWith(QUORUM_ISSUER_PREFIX) || issuer.length() == QUORUM_ISSUER_PREFIX.length()) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.MALFORMED_QUORUM_ISSUER,
                    "A duel_grant is issued by a committee, so issuerDid must be '" + QUORUM_ISSUER_PREFIX
                            + "<duelId>'; this record names '" + issuer + "'"));
            return;
        }
        String duelId = issuer.substring(QUORUM_ISSUER_PREFIX.length());
        QuorumCommittee committee = context.duelCommittees().committeeFor(duelId);
        if (committee == null) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNKNOWN_DUEL_COMMITTEE,
                    "No sampling record for duel '" + duelId + "'; without it, a real quorum and a handful "
                            + "of freshly generated keys are indistinguishable"));
            return;
        }

        Set<String> counted = new HashSet<>();
        double agreeingWeight = 0;
        int agreeingValidators = 0;
        for (SignatureBlock block : envelope.signatures()) {
            String validatorDid = block.signerDid();
            if (!counted.add(validatorDid)) {
                faults.add(ChainFault.at(
                        position,
                        payload,
                        Reason.DUPLICATE_VALIDATOR_SIGNATURE,
                        validatorDid + " signed this outcome more than once, which would double-count its weight"));
                continue;
            }
            if (!committee.wasSampled(validatorDid)) {
                faults.add(ChainFault.at(
                        position,
                        payload,
                        Reason.VALIDATOR_NOT_SAMPLED,
                        validatorDid + " was not sampled for duel '" + duelId + "', so its signature carries "
                                + "no authority over the outcome"));
                continue;
            }
            if (checkSignature(position, payload, block, canonical, context, faults)) {
                agreeingWeight += committee.weightOf(validatorDid);
                agreeingValidators++;
            }
        }

        double requiredWeight = committee.requiredWeight();
        double tolerance = WEIGHT_TOLERANCE * Math.max(1.0, committee.totalWeight());
        boolean weightMet = agreeingWeight >= requiredWeight - tolerance;
        boolean countMet = agreeingValidators >= committee.agreeingValidatorsRequired();
        if (!weightMet || !countMet) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.QUORUM_NOT_REACHED,
                    "Duel '" + duelId + "': " + agreeingValidators + " of " + committee.size()
                            + " sampled validators agreed, carrying weight " + agreeingWeight + " of "
                            + committee.totalWeight() + "; the 2f+1-of-3f+1 threshold needs "
                            + committee.agreeingValidatorsRequired() + " validators and weight " + requiredWeight));
        }
    }

    /**
     * @return whether this signature verifies; a fault has already been recorded if not
     */
    private static boolean checkSignature(
            int position,
            ProvenancePayload payload,
            SignatureBlock block,
            byte[] canonical,
            ChainVerificationContext context,
            List<ChainFault> faults) {
        if (!Ed25519Signatures.JOSE_ALG.equals(block.alg())) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.WRONG_SIGNATURE_ALGORITHM,
                    "Signature by " + block.kid() + " declares alg '" + block.alg() + "'; provenance is signed "
                            + "with " + Ed25519Signatures.JOSE_ALG + " and nothing else"));
            return false;
        }
        PublicKey key = context.signingKeys().publicKeyFor(block.kid());
        if (key == null) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.UNKNOWN_SIGNING_KEY,
                    "No public key resolves for kid '" + block.kid() + "'"));
            return false;
        }
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(block.sig());
        } catch (IllegalArgumentException e) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.MALFORMED_SIGNATURE,
                    "Signature by " + block.kid() + " is not decodable base64url"));
            return false;
        }
        if (!Ed25519Signatures.verify(key, canonical, signature)) {
            faults.add(ChainFault.at(
                    position,
                    payload,
                    Reason.INVALID_SIGNATURE,
                    "Signature by " + block.kid() + " does not cover this record's canonical bytes"));
            return false;
        }
        return true;
    }
}
