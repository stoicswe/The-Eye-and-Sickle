package io.github.stoicswe.eyeandsickle.protocol.provenance;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The wire codec for provenance records, and the single place that decides which bytes get signed.
 *
 * <p>Implements the JSON shapes in {@code docs/architecture/04-item-provenance.md} §2 (payload), §3
 * (single-issuer envelope) and §3.1 (multi-signature duel envelope), plus the {@code chainDepth}
 * field §6.1 adds to the payload.
 *
 * <h2>Why the payload document is assembled by hand</h2>
 *
 * A serializer's defaults are not a stable contract. Whether nulls are emitted, whether an absent
 * value is skipped, how an enum is spelled, what order fields appear in — all of that is
 * configuration, and all of it changes the bytes. Those bytes are the signature input. A library
 * upgrade that starts omitting {@code "prevRecordHash": null} would silently invalidate every
 * genesis record ever signed, on every server, at once — and it would look exactly like a federation
 * full of cheaters rather than like a build change.
 *
 * <p>So the scalar fields of the payload are written directly, in the §2 order, with Jackson used
 * only to quote strings and to serialize the opaque {@code itemAttrs} sub-document. That is the same
 * reasoning {@link ProvenancePayload} gives for typing {@code timestamp} as a {@code String}: what
 * gets signed must be exactly the bytes on the wire, chosen deliberately.
 *
 * <h2>The event-type spelling is explicit, not derived</h2>
 *
 * {@code initial_mint}, {@code server_grant}, {@code trade}, {@code duel_grant} are the wire values
 * fixed by §2. They are mapped to and from the Java constants by an exhaustive switch rather than by
 * a naming convention, so renaming a Java constant is a compile error instead of a silent wire-format
 * break that only shows up as unverifiable records on somebody else's server.
 *
 * <h2>Numbers inside {@code itemAttrs}</h2>
 *
 * RFC 8785 serializes every number through the IEEE-754 double path, so an attribute value beyond
 * 2^53 will not survive canonicalization intact. Item stats are small; a counter that is not should
 * be carried as a string.
 */
public final class ProvenanceJson {

    /**
     * Thread-safe once built, so one instance serves the whole process. Used only for string quoting
     * and for the {@code itemAttrs} sub-document — never to decide the payload's shape.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String RECORD_VERSION = "recordVersion";
    private static final String ITEM_ID = "itemId";
    private static final String ITEM_TYPE = "itemType";
    private static final String ITEM_ATTRS = "itemAttrs";
    private static final String EVENT_TYPE = "eventType";
    private static final String HOLDER_DID = "holderDid";
    private static final String ISSUER_DID = "issuerDid";
    private static final String PREV_RECORD_HASH = "prevRecordHash";
    private static final String CHAIN_DEPTH = "chainDepth";
    private static final String TIMESTAMP = "timestamp";
    private static final String NONCE = "nonce";

    private static final String PAYLOAD = "payload";
    private static final String PAYLOAD_CANONICALIZATION = "payloadCanonicalization";
    private static final String SIGNATURE = "signature";
    private static final String SIGNATURES = "signatures";
    private static final String ALG = "alg";
    private static final String KID = "kid";
    private static final String SIG = "sig";

    private ProvenanceJson() {}

    // ------------------------------------------------------------------ event type

    /**
     * The wire spelling of an event type, as fixed by {@code
     * docs/architecture/04-item-provenance.md} §2.
     *
     * @param eventType the Java constant
     * @return the lowercase snake_case value that appears in JSON
     */
    public static String wireName(ProvenanceEventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        return switch (eventType) {
            case INITIAL_MINT -> "initial_mint";
            case SERVER_GRANT -> "server_grant";
            case TRADE -> "trade";
            case DUEL_GRANT -> "duel_grant";
        };
    }

    /**
     * The event type a wire value names.
     *
     * <p>An unrecognized value is rejected rather than mapped to some "unknown" constant. A verifier
     * decides which issuer is authorized per event type, so a record whose event type it does not
     * understand is a record it cannot authorize — see {@link ProvenanceEventType}.
     *
     * @param wireName the JSON value
     * @return the Java constant
     * @throws IllegalArgumentException if the value is not one of the four defined events
     */
    public static ProvenanceEventType eventType(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return switch (wireName) {
            case "initial_mint" -> ProvenanceEventType.INITIAL_MINT;
            case "server_grant" -> ProvenanceEventType.SERVER_GRANT;
            case "trade" -> ProvenanceEventType.TRADE;
            case "duel_grant" -> ProvenanceEventType.DUEL_GRANT;
            default -> throw new IllegalArgumentException("Unknown provenance eventType '" + wireName + "'");
        };
    }

    // ------------------------------------------------------------------ payload

    /**
     * Serializes a payload in the field order {@code docs/architecture/04-item-provenance.md} §2
     * uses, for logging, storage and the player-facing item-history view (§6.1).
     *
     * <p>This is <em>not</em> the signature input — {@link #canonicalBytes(ProvenancePayload)} is.
     * The two agree on content; only key order and spacing differ.
     *
     * @param payload the record content
     * @return a JSON object
     */
    public static String writePayload(ProvenancePayload payload) {
        Objects.requireNonNull(payload, "payload");
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        json.append(quote(RECORD_VERSION)).append(':').append(payload.recordVersion());
        json.append(',')
                .append(quote(ITEM_ID))
                .append(':')
                .append(quote(payload.itemId().toString()));
        json.append(',').append(quote(ITEM_TYPE)).append(':').append(quote(payload.itemType()));
        json.append(',').append(quote(ITEM_ATTRS)).append(':').append(MAPPER.writeValueAsString(payload.itemAttrs()));
        json.append(',').append(quote(EVENT_TYPE)).append(':').append(quote(wireName(payload.eventType())));
        json.append(',').append(quote(HOLDER_DID)).append(':').append(quote(payload.holderDid()));
        json.append(',').append(quote(ISSUER_DID)).append(':').append(quote(payload.issuerDid()));
        json.append(',').append(quote(PREV_RECORD_HASH)).append(':');
        // Written explicitly rather than omitted: §2 shows the field present on every record, and a
        // genesis record whose canonical form silently lost a key would verify differently on a
        // server whose serializer kept it.
        json.append(payload.prevRecordHash() == null ? "null" : quote(payload.prevRecordHash()));
        json.append(',').append(quote(CHAIN_DEPTH)).append(':').append(payload.chainDepth());
        json.append(',').append(quote(TIMESTAMP)).append(':').append(quote(payload.timestamp()));
        json.append(',').append(quote(NONCE)).append(':').append(quote(payload.nonce()));
        json.append('}');
        return json.toString();
    }

    /**
     * Parses a payload document.
     *
     * @param json a JSON object as produced by {@link #writePayload}
     * @return the payload
     * @throws IllegalArgumentException if the document is malformed, or a field is missing or of the
     *     wrong JSON type
     */
    public static ProvenancePayload readPayload(String json) {
        return payloadFrom(parseObject(json, "provenance payload"));
    }

    /**
     * The exact bytes a provenance signature covers: the payload, canonicalized per RFC 8785.
     *
     * <p>Everything that signs or verifies a provenance record goes through here. Two independent
     * implementations that both call this produce identical bytes for the same logical record, which
     * is the property that lets a client re-verify an item's history offline without trusting the
     * server that showed it (§6.2).
     *
     * @param payload the record content
     * @return the canonical UTF-8 bytes to sign or verify
     */
    public static byte[] canonicalBytes(ProvenancePayload payload) {
        return JsonCanonicalization.canonicalize(writePayload(payload));
    }

    /**
     * The canonical form as a string, for logs and dispute transcripts.
     *
     * @param payload the record content
     * @return the RFC 8785 canonical JSON
     */
    public static String canonicalJson(ProvenancePayload payload) {
        return JsonCanonicalization.canonicalizeToString(writePayload(payload));
    }

    // ------------------------------------------------------------------ envelope

    /**
     * Serializes an envelope in the §3 single-signature shape or the §3.1 multi-signature shape.
     *
     * <p>The shape follows the <em>event</em>, not the signature count: a {@code duel_grant} always
     * writes the {@code "signatures"} array because its authority is a committee even when only one
     * member's signature is being carried, and every other event writes the {@code "signature"}
     * object. The one exception is a non-duel envelope that somehow holds more than one block — it
     * takes the array shape too, because dropping a signature to fit the singular field would make
     * encoding lossy, and lossy is worse than unusual.
     *
     * @param envelope the record and its signatures
     * @return a JSON object
     */
    public static String writeEnvelope(ProvenanceEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        boolean arrayShape = envelope.payload().eventType() == ProvenanceEventType.DUEL_GRANT
                || envelope.signatures().size() != 1;

        StringBuilder json = new StringBuilder(768);
        json.append('{');
        json.append(quote(PAYLOAD)).append(':').append(writePayload(envelope.payload()));
        json.append(',')
                .append(quote(PAYLOAD_CANONICALIZATION))
                .append(':')
                .append(quote(envelope.payloadCanonicalization()));
        if (arrayShape) {
            json.append(',').append(quote(SIGNATURES)).append(":[");
            for (int i = 0; i < envelope.signatures().size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(signatureJson(envelope.signatures().get(i)));
            }
            json.append(']');
        } else {
            json.append(',')
                    .append(quote(SIGNATURE))
                    .append(':')
                    .append(signatureJson(envelope.signatures().getFirst()));
        }
        json.append('}');
        return json.toString();
    }

    /**
     * Parses an envelope in either the single-signature or the multi-signature shape.
     *
     * <p>Reading accepts whichever of {@code signature} / {@code signatures} is present, because a
     * peer's choice of shape is not something to reject a record over — the verifier judges the
     * record's semantics. Carrying <em>both</em> is refused: that document has two possible readings
     * and picking one silently is how a signature-stripping trick gets in.
     *
     * @param json a JSON object as produced by {@link #writeEnvelope}
     * @return the envelope
     * @throws IllegalArgumentException if the document is malformed or carries no usable signature
     */
    public static ProvenanceEnvelope readEnvelope(String json) {
        Map<String, Object> root = parseObject(json, "provenance envelope");

        Object payloadNode = root.get(PAYLOAD);
        if (!(payloadNode instanceof Map<?, ?> payloadObject)) {
            throw new IllegalArgumentException("Envelope field 'payload' must be a JSON object");
        }
        ProvenancePayload payload = payloadFrom(asObject(payloadObject));
        String canonicalization = requiredString(root, PAYLOAD_CANONICALIZATION);

        Object single = root.get(SIGNATURE);
        Object multiple = root.get(SIGNATURES);
        if (single != null && multiple != null) {
            throw new IllegalArgumentException("An envelope carries either 'signature' or 'signatures', never both");
        }

        List<SignatureBlock> blocks = new ArrayList<>();
        if (multiple != null) {
            if (!(multiple instanceof List<?> elements)) {
                throw new IllegalArgumentException("Envelope field 'signatures' must be a JSON array");
            }
            for (Object element : elements) {
                blocks.add(signatureFrom(element));
            }
        } else if (single != null) {
            blocks.add(signatureFrom(single));
        } else {
            throw new IllegalArgumentException("Envelope carries neither 'signature' nor 'signatures'");
        }
        return new ProvenanceEnvelope(payload, canonicalization, blocks);
    }

    // ------------------------------------------------------------------ internals

    private static String signatureJson(SignatureBlock block) {
        return "{" + quote(ALG) + ':' + quote(block.alg())
                + ',' + quote(KID) + ':' + quote(block.kid())
                + ',' + quote(SIG) + ':' + quote(block.sig())
                + "}";
    }

    private static SignatureBlock signatureFrom(Object node) {
        if (!(node instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("A signature block must be a JSON object");
        }
        Map<String, Object> block = asObject(object);
        // §3.1's example array elements carry only kid/sig, so a missing alg means the one algorithm
        // this game signs with. That is not an algorithm-agility hole: the verifier still checks the
        // resulting value, and EdDSA is the only value it accepts.
        Object alg = block.get(ALG);
        String algorithm = alg == null ? Ed25519Signatures.JOSE_ALG : requiredString(block, ALG);
        return new SignatureBlock(algorithm, requiredString(block, KID), requiredString(block, SIG));
    }

    private static ProvenancePayload payloadFrom(Map<String, Object> object) {
        String itemId = requiredString(object, ITEM_ID);
        UUID parsedItemId;
        try {
            parsedItemId = UUID.fromString(itemId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field 'itemId' is not a UUID: '" + itemId + "'", e);
        }
        return new ProvenancePayload(
                requiredInt(object, RECORD_VERSION),
                parsedItemId,
                requiredString(object, ITEM_TYPE),
                attributesFrom(object),
                eventType(requiredString(object, EVENT_TYPE)),
                requiredString(object, HOLDER_DID),
                requiredString(object, ISSUER_DID),
                nullableString(object, PREV_RECORD_HASH),
                requiredInt(object, CHAIN_DEPTH),
                requiredString(object, TIMESTAMP),
                requiredString(object, NONCE));
    }

    private static Map<String, Object> attributesFrom(Map<String, Object> object) {
        Object attrs = object.get(ITEM_ATTRS);
        if (attrs == null) {
            return Map.of();
        }
        if (!(attrs instanceof Map<?, ?> nested)) {
            throw new IllegalArgumentException("Field 'itemAttrs' must be a JSON object");
        }
        Map<String, Object> attributes = asObject(nested);
        // A top-level null attribute is a value ProvenancePayload cannot hold, so it is refused here
        // with a message that says so rather than surfacing as a bare NPE from Map.copyOf.
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Attribute '" + entry.getKey() + "' is null; omit it rather than writing a null");
            }
        }
        return attributes;
    }

    private static String quote(String value) {
        return MAPPER.writeValueAsString(value);
    }

    private static Map<String, Object> parseObject(String json, String what) {
        Objects.requireNonNull(json, "json");
        Object parsed;
        try {
            parsed = MAPPER.readValue(json, Map.class);
        } catch (RuntimeException e) {
            // Deliberately broad. Jackson reports every parse and binding failure as an unchecked
            // exception, and the caller's only useful response to any of them is the same: this
            // document is not a record we can verify. The cause is kept for the operator's log.
            throw new IllegalArgumentException("Not a well-formed " + what + " document", e);
        }
        if (!(parsed instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("A " + what + " must be a JSON object");
        }
        return asObject(object);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Map<?, ?> parsed) {
        // JSON object keys are always strings, so this cast cannot fail for anything Jackson parsed.
        return (Map<String, Object>) parsed;
    }

    private static String requiredString(Map<String, Object> object, String field) {
        Object value = object.get(field);
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Field '" + field + "' is missing or is not a JSON string");
    }

    private static String nullableString(Map<String, Object> object, String field) {
        Object value = object.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Field '" + field + "' must be a JSON string or null");
    }

    private static int requiredInt(Map<String, Object> object, String field) {
        Object value = object.get(field);
        if (value instanceof Integer integer) {
            return integer;
        }
        // Not Number.intValue(): accepting 1.5 for chainDepth and silently truncating it to 1 would
        // let a forged record land on a position it does not claim.
        throw new IllegalArgumentException("Field '" + field + "' is missing or is not a JSON integer");
    }
}
