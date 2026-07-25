package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The part of a provenance record that gets canonicalized and signed.
 *
 * <p>Transcribed from {@code docs/architecture/04-item-provenance.md} §2, plus {@code chainDepth}
 * from §6.1 (marked {@code [PROPOSAL]} there as an addition to the original schema — because it is
 * part of the payload, it is part of what gets signed).
 *
 * <p>Note that {@code itemAttrs} carries the item's game-relevant stats. That is deliberate: the
 * provenance record <em>is</em> the authoritative item definition, not merely a receipt of one.
 *
 * <h2>Why the timestamp is a String</h2>
 *
 * What gets signed must be exactly the bytes on the wire. Typing this field as {@code Instant} would
 * hand the rendering decision to whichever JSON library each implementation happens to use — epoch
 * seconds, epoch millis, fractional-second precision — and a signature that does not reproduce across
 * implementations is worse than no signature at all. The field is an ISO-8601 UTC instant as a
 * string, chosen once, by the author of the record.
 *
 * @param recordVersion schema version of this payload; currently {@value #CURRENT_RECORD_VERSION}
 * @param itemId the item this chain describes
 * @param itemType e.g. {@code hacking_tool_tier2}
 * @param itemAttrs authoritative item stats, e.g. {@code {"power": 42, "durability": 0.87}}
 * @param eventType what happened
 * @param holderDid who owns the item after this event
 * @param issuerDid the home server's DID, or {@code duel:<duelId>} for a quorum-issued duel outcome
 * @param prevRecordHash SHA-256 of the previous record in this item's chain; {@code null} at genesis
 * @param chainDepth position in the chain, genesis = 0; lets a client fetch records N..N+20 instead
 *     of walking from the tip every time
 * @param timestamp ISO-8601 UTC instant, e.g. {@code 2026-07-23T18:04:00Z}
 * @param nonce random 128-bit value; with {@code timestamp}, prevents replaying an old valid record
 *     as though it were a new event
 */
public record ProvenancePayload(
        int recordVersion,
        UUID itemId,
        String itemType,
        Map<String, Object> itemAttrs,
        ProvenanceEventType eventType,
        String holderDid,
        String issuerDid,
        String prevRecordHash,
        int chainDepth,
        String timestamp,
        String nonce) {

    /** The payload schema version this build writes. */
    public static final int CURRENT_RECORD_VERSION = 1;

    public ProvenancePayload {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemType, "itemType");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(holderDid, "holderDid");
        Objects.requireNonNull(issuerDid, "issuerDid");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(nonce, "nonce");

        if (chainDepth < 0) {
            throw new IllegalArgumentException("chainDepth must be >= 0, was " + chainDepth);
        }
        // Genesis is defined by BOTH markers agreeing. A record claiming depth 0 while chaining to a
        // predecessor — or claiming depth > 0 with nothing to chain to — is a broken chain, and
        // catching it at construction is cheaper than catching it during a verification walk.
        boolean genesis = chainDepth == 0;
        if (genesis != (prevRecordHash == null)) {
            throw new IllegalArgumentException(
                    "Genesis records have chainDepth 0 and no prevRecordHash; got chainDepth=" + chainDepth
                            + ", prevRecordHash=" + prevRecordHash);
        }
        itemAttrs = itemAttrs == null ? Map.of() : Map.copyOf(itemAttrs);
    }

    /** Whether this is the first record in the item's chain. */
    public boolean isGenesis() {
        return chainDepth == 0;
    }
}
