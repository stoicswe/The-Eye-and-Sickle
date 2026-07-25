package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes and decodes the JSON envelope a {@link CharacterHomeRecord} is published over.
 *
 * <h2>JSON transports the fields; the signature covers binary, not JSON</h2>
 *
 * Unlike a server self-descriptor — whose signature covers canonical JSON — a {@code CharacterHomeRecord}
 * signs its own length-prefixed binary form ({@link CharacterHomeRecord#signingBytes}). So this JSON is a
 * pure transport envelope for the record's fields, and there is no canonicalization to agree on: a
 * verifier reconstructs the binary signing bytes from the parsed fields and checks the signature against
 * those. That keeps the trust decision in one place ({@link CharacterHomeRecordVerifier}) and off the
 * shape of the JSON entirely.
 *
 * <pre>{@code
 * {
 *   "recordVersion": 1,
 *   "accountDid": "did:plc:account",
 *   "characterId": "11111111-1111-1111-1111-111111111111",
 *   "slot": 2,
 *   "homeServerDid": "did:plc:home",
 *   "homeEndpoint": "https://home.example.org",
 *   "homeTransportPublicKey": "<base64url X.509 X25519>",
 *   "signingKeyId": "did:plc:home#key1",
 *   "sequence": 7,
 *   "signature": "<base64url Ed25519>"
 * }
 * }</pre>
 *
 * <h2>Structural failures are typed; semantic ones are the verifier's</h2>
 *
 * {@link #parse(String, int)} throws {@link CharacterHomeCodecException} carrying a {@link
 * CharacterHomeFault} for anything structurally wrong before a field can even be read — an oversized
 * body or bytes that are not a JSON object. Field-level checks — DID shape, endpoint shape, slot range,
 * signature validity — belong to {@link CharacterHomeRecordVerifier}, which owns policy; this class owns
 * format. The split mirrors {@code ServerDescriptorCodec}/{@code ServerDescriptorVerifier}.
 */
public final class CharacterHomeCodec {

    // Envelope field keys.
    static final String RECORD_VERSION = "recordVersion";
    static final String ACCOUNT_DID = "accountDid";
    static final String CHARACTER_ID = "characterId";
    static final String SLOT = "slot";
    static final String HOME_SERVER_DID = "homeServerDid";
    static final String HOME_ENDPOINT = "homeEndpoint";
    static final String HOME_TRANSPORT_PUBLIC_KEY = "homeTransportPublicKey";
    static final String SIGNING_KEY_ID = "signingKeyId";
    static final String SEQUENCE = "sequence";
    static final String SIGNATURE = "signature";

    /** The current record schema version. Bumped only if the envelope shape changes. */
    public static final int CURRENT_RECORD_VERSION = 1;

    private CharacterHomeCodec() {}

    // ------------------------------------------------------------------ produce

    /**
     * Serializes a signed record to its publish envelope.
     *
     * <p>Used by a home server to publish its binding, and by tests. Byte fields (the transport key and
     * the signature) are base64url without padding, the same encoding {@link #decodeBase64Url(String)}
     * reads back.
     *
     * @param record the signed record to serialize
     * @return the envelope JSON, ready to publish
     */
    public static String writeEnvelope(CharacterHomeRecord record) {
        Objects.requireNonNull(record, "record");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(RECORD_VERSION, CURRENT_RECORD_VERSION);
        envelope.put(ACCOUNT_DID, record.accountDid());
        envelope.put(CHARACTER_ID, record.characterId().toString());
        envelope.put(SLOT, record.slot());
        envelope.put(HOME_SERVER_DID, record.homeServerDid());
        envelope.put(HOME_ENDPOINT, record.homeEndpoint());
        envelope.put(HOME_TRANSPORT_PUBLIC_KEY, base64Url(record.homeTransportPublicKey()));
        envelope.put(SIGNING_KEY_ID, record.signingKeyId());
        envelope.put(SEQUENCE, record.sequenceNumber());
        envelope.put(SIGNATURE, base64Url(record.signature()));
        return Jsonb.writeObject(envelope);
    }

    // ------------------------------------------------------------------ consume

    /**
     * Parses a publish envelope's structure.
     *
     * @param rawEnvelope the received JSON, verbatim
     * @param maxBytes the configured byte cap; checked before any parsing
     * @return the parsed field map, for the verifier to validate
     * @throws CharacterHomeCodecException with a {@link CharacterHomeFault} if the envelope is oversized or
     *     not a JSON object
     */
    public static Map<String, Object> parse(String rawEnvelope, int maxBytes) {
        Objects.requireNonNull(rawEnvelope, "rawEnvelope");
        int size = rawEnvelope.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxBytes) {
            throw new CharacterHomeCodecException(
                    CharacterHomeFault.TOO_LARGE, "Record is " + size + " bytes, over the " + maxBytes + "-byte cap");
        }
        try {
            return Jsonb.readObject(rawEnvelope);
        } catch (RuntimeException e) {
            throw new CharacterHomeCodecException(CharacterHomeFault.MALFORMED_JSON, "Not a well-formed JSON object");
        }
    }

    /**
     * Decodes a base64url key or signature from a record.
     *
     * @param value the base64url text
     * @return the decoded bytes
     * @throws IllegalArgumentException if the text is not valid base64url
     */
    public static byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A structural parse failure carrying the {@link CharacterHomeFault} the verifier should report. */
    public static final class CharacterHomeCodecException extends RuntimeException {

        private final transient CharacterHomeFault fault;

        CharacterHomeCodecException(CharacterHomeFault fault, String message) {
            super(message);
            this.fault = fault;
        }

        /** @return the fault classification */
        public CharacterHomeFault fault() {
            return fault;
        }
    }
}
