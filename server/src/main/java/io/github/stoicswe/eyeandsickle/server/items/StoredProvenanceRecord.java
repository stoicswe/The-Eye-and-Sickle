package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.RecordHash;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the {@code provenance_records} table — a stored, signed provenance record.
 *
 * <h2>The envelope is authoritative; payload and signatures are projections of it</h2>
 *
 * A record arrives (or is minted) as a detached-JWS {@code envelope}, and its signature covers exact
 * canonical bytes ({@code docs/architecture/04-item-provenance.md} §1). {@link #envelopeJson} holds
 * that envelope <strong>verbatim</strong> — for a received record, the literal bytes a peer sent; for a
 * record this server signed, the bytes it produced. {@link #payloadJson} and {@link #signaturesJson}
 * are extracted from the <em>same single parse</em> for querying, never re-derived independently, which
 * is what stops the three columns drifting into a state that reads as cheating (the schema comment on
 * the table spells this out).
 *
 * <p>{@link #recordHash} is the SHA-256 over the canonical <em>payload</em> bytes ({@code
 * RecordHash} — [PROPOSAL] P-1), stored so a chain link is checkable with an index lookup rather than a
 * re-hash, and so it is exactly the value a successor's {@code prevRecordHash} must equal.
 *
 * @param recordId this row's identity
 * @param itemId the item whose chain this record belongs to
 * @param chainDepth position in the chain, genesis = 0
 * @param recordHash {@code sha256-<hex>} over the canonical payload bytes
 * @param prevRecordHash the predecessor's {@link #recordHash}, or {@code null} at genesis
 * @param eventType what happened
 * @param holderDid who owns the item after this event
 * @param issuerDid the signing server's DID, or {@code duel:<duelId>} for a quorum outcome
 * @param recordVersion the payload schema version
 * @param payloadJson the payload as JSON (a projection; the canonical form is derived, not this)
 * @param envelopeJson the full detached-JWS envelope, verbatim and authoritative
 * @param signaturesJson the signature blocks as a JSON array, one entry per signer
 * @param payloadTimestamp the ISO-8601 timestamp exactly as signed
 * @param recordedAt when this server stored the row
 */
public record StoredProvenanceRecord(
        UUID recordId,
        UUID itemId,
        int chainDepth,
        String recordHash,
        String prevRecordHash,
        ProvenanceEventType eventType,
        String holderDid,
        String issuerDid,
        int recordVersion,
        String payloadJson,
        String envelopeJson,
        String signaturesJson,
        String payloadTimestamp,
        Instant recordedAt) {

    public StoredProvenanceRecord {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(recordHash, "recordHash");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(holderDid, "holderDid");
        Objects.requireNonNull(issuerDid, "issuerDid");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(envelopeJson, "envelopeJson");
        Objects.requireNonNull(signaturesJson, "signaturesJson");
        Objects.requireNonNull(payloadTimestamp, "payloadTimestamp");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }

    /**
     * Builds a storable row from an envelope and the verbatim JSON it should be stored as.
     *
     * <p>One parse in, three consistent columns out — the discipline the table demands. The caller
     * supplies {@code envelopeJsonVerbatim} rather than letting this method re-serialize the envelope,
     * because for a received record the exact received bytes must be kept (re-serializing could change
     * the bytes a signature covers), while for a minted record the caller passes what {@link
     * ProvenanceJson#writeEnvelope} just produced.
     *
     * @param recordId this row's identity
     * @param envelope the parsed envelope (source of the payload/signature projections and the hash)
     * @param envelopeJsonVerbatim the envelope's authoritative JSON, stored untouched
     * @param recordedAt when this server is storing the row
     * @return the row
     */
    public static StoredProvenanceRecord from(
            UUID recordId, ProvenanceEnvelope envelope, String envelopeJsonVerbatim, Instant recordedAt) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(envelopeJsonVerbatim, "envelopeJsonVerbatim");
        ProvenancePayload payload = envelope.payload();
        byte[] canonical = ProvenanceJson.canonicalBytes(payload);
        return new StoredProvenanceRecord(
                recordId,
                payload.itemId(),
                payload.chainDepth(),
                RecordHash.ofCanonicalBytes(canonical),
                payload.prevRecordHash(),
                payload.eventType(),
                payload.holderDid(),
                payload.issuerDid(),
                payload.recordVersion(),
                ProvenanceJson.writePayload(payload),
                envelopeJsonVerbatim,
                signaturesArrayJson(envelope.signatures()),
                payload.timestamp(),
                recordedAt);
    }

    /**
     * Re-parses the stored envelope into a {@link ProvenanceEnvelope} for verification.
     *
     * <p>Because {@link #envelopeJson} was stored verbatim, this round-trips to exactly the envelope
     * that was received or signed — which is what lets a verifier (or the client, {@code 04} §6.2)
     * re-check the record against its canonical bytes.
     *
     * @return the parsed envelope
     */
    public ProvenanceEnvelope toEnvelope() {
        return ProvenanceJson.readEnvelope(envelopeJson);
    }

    /**
     * The signature blocks as a JSON array, the shape {@code provenance_records.signatures} requires
     * ({@code jsonb_typeof = 'array'}, length {@code >= 1}) — even for a single-issuer record, whose
     * envelope uses the singular {@code "signature"} object. The column is a query projection, not a
     * signature input, so writing it in array form for every record is lossless and uniform.
     */
    private static String signaturesArrayJson(List<SignatureBlock> blocks) {
        List<Map<String, String>> entries = new ArrayList<>(blocks.size());
        for (SignatureBlock block : blocks) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("alg", block.alg());
            entry.put("kid", block.kid());
            entry.put("sig", block.sig());
            entries.add(entry);
        }
        return Jsonb.writeArray(entries);
    }
}
