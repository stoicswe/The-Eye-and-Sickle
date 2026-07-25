package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeCodec.CharacterHomeCodecException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns untrusted, published character-home bytes into a verified {@link CharacterHomeRecord}, or a typed
 * refusal — the trust boundary of the character directory ({@code
 * docs/architecture/09-player-state-portability.md} §4).
 *
 * <h2>Nothing is believed until the home server's signature is checked</h2>
 *
 * A home binding arrives from a server this one does not control ({@code
 * docs/architecture/03-server-and-federation.md} §1). A home server may only speak for itself, so the
 * signing {@code kid} must belong to the {@code homeServerDid} the record names, and the signature must
 * verify against the key that DID resolves to. Only then does the binding become storable directory
 * state. Everything downstream ({@link CharacterDirectoryService}) can assume a {@code
 * CharacterHomeRecord} was verified, because the only way to make one here is through an accepting
 * verdict. The structure mirrors the discovery slice's {@code ServerDescriptorVerifier} deliberately —
 * one refusal discipline for two verifiers facing the same threat model.
 *
 * <h2>An untrusted record never throws out of {@link #verify}</h2>
 *
 * Every malformation is a typed {@link CharacterHomeFault}, never an exception escaping into the caller.
 * This is the lesson the discovery verifier learned the hard way: an attacker-supplied integer beyond
 * {@code long}'s range must not let {@code BigInteger.longValueExact()} throw an {@link
 * ArithmeticException} out of the verify path, and an attacker-supplied base64 blob must not let a decode
 * throw. Both — and the UUID and X25519 decodes — are caught and folded into typed refusals ({@link
 * #integer}, and the {@code try/catch} around each decode below), exactly as {@code
 * Ed25519Signatures.verify} is contractually never-throws.
 *
 * <h2>Cheap and structural first</h2>
 *
 * size cap &rarr; JSON shape &rarr; required fields &rarr; account/home DID shape (mirroring {@code
 * is_did} and the endpoint CHECK so a bad value is refused before the database has to) &rarr; slot range
 * &rarr; sequence sanity &rarr; character-id and transport-key decode &rarr; signer owns the home DID
 * &rarr; key resolves &rarr; signature covers the record. The elliptic-curve check runs last.
 */
@Component
public class CharacterHomeRecordVerifier {

    /** Mirrors {@code is_did} in {@code V2__core_schema.sql} so a malformed DID is refused before the INSERT. */
    private static final Pattern DID_SHAPE = Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

    private static final int DID_MAX_LENGTH = 512;

    /** Mirrors {@code ck_character_directory_endpoint}. */
    private static final Pattern ENDPOINT_SHAPE = Pattern.compile("^https?://[^\\s]+$");

    private static final int ENDPOINT_MAX_LENGTH = 2048;

    /** Lowest slot; mirrors {@code CharacterHomeRecord.MIN_SLOT} and the low half of {@code ck_character_directory_slot}. */
    private static final int MIN_SLOT = CharacterHomeRecord.MIN_SLOT;

    /**
     * Highest slot. Mirrors {@code ck_character_directory_slot BETWEEN 1 AND 16} and the identity slice's
     * {@code Player.MAX_SLOT} — the generous structural bound a slot number may take, not the soft
     * product cap on how many characters an account may hold (that is service-enforced from
     * {@code CharacterProperties.maxCharacters}). Refusing an out-of-range slot here keeps a bad value out
     * of the database, and bounds the rows one account can ever occupy.
     */
    private static final int MAX_SLOT = 16;

    private final CharacterDirectoryProperties properties;
    private final CharacterHomeKeyResolver keyResolver;

    /**
     * @param properties the size cap and directory bounds
     * @param keyResolver resolves a home server's signing {@code kid} to its Ed25519 key; the identity
     *     slice provides the real implementation, and until it does an empty resolver simply refuses every
     *     record (the safe closed default)
     */
    public CharacterHomeRecordVerifier(CharacterDirectoryProperties properties, CharacterHomeKeyResolver keyResolver) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
    }

    /**
     * Verifies a raw published home record.
     *
     * @param rawEnvelope the received JSON, verbatim
     * @return an accepted {@link CharacterHomeRecord} or a typed refusal
     */
    public CharacterHomeVerification verify(String rawEnvelope) {
        Objects.requireNonNull(rawEnvelope, "rawEnvelope");

        Map<String, Object> fields;
        try {
            fields = CharacterHomeCodec.parse(rawEnvelope, properties.maxRecordBytes());
        } catch (CharacterHomeCodecException e) {
            return CharacterHomeVerification.rejected(e.fault(), e.getMessage());
        }

        String accountDid = string(fields, CharacterHomeCodec.ACCOUNT_DID);
        if (accountDid == null) {
            return reject(CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.ACCOUNT_DID + "'");
        }
        if (!isDid(accountDid)) {
            return reject(CharacterHomeFault.MALFORMED_ACCOUNT_DID, "'" + accountDid + "' is not DID-shaped");
        }

        String homeServerDid = string(fields, CharacterHomeCodec.HOME_SERVER_DID);
        if (homeServerDid == null) {
            return reject(
                    CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.HOME_SERVER_DID + "'");
        }
        if (!isDid(homeServerDid)) {
            return reject(CharacterHomeFault.MALFORMED_HOME_DID, "'" + homeServerDid + "' is not DID-shaped");
        }

        String characterIdText = string(fields, CharacterHomeCodec.CHARACTER_ID);
        if (characterIdText == null) {
            return reject(CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.CHARACTER_ID + "'");
        }
        UUID characterId;
        try {
            characterId = UUID.fromString(characterIdText);
        } catch (IllegalArgumentException e) {
            // UUID.fromString throws on anything not UUID-shaped; from untrusted input that must be a typed
            // refusal, not an exception out of verify().
            return reject(CharacterHomeFault.MALFORMED_CHARACTER_ID, "characterId is not a UUID");
        }

        Long slotValue = integer(fields, CharacterHomeCodec.SLOT);
        if (slotValue == null || slotValue < MIN_SLOT || slotValue > MAX_SLOT) {
            return reject(
                    CharacterHomeFault.MALFORMED_SLOT,
                    "slot is missing, non-integer, or outside " + MIN_SLOT + ".." + MAX_SLOT);
        }
        int slot = slotValue.intValue();

        String homeEndpoint = string(fields, CharacterHomeCodec.HOME_ENDPOINT);
        if (homeEndpoint == null) {
            return reject(CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.HOME_ENDPOINT + "'");
        }
        if (homeEndpoint.length() > ENDPOINT_MAX_LENGTH
                || !ENDPOINT_SHAPE.matcher(homeEndpoint).matches()) {
            return reject(
                    CharacterHomeFault.MALFORMED_ENDPOINT, "endpoint '" + homeEndpoint + "' is not an http(s) URL");
        }

        Long sequence = integer(fields, CharacterHomeCodec.SEQUENCE);
        if (sequence == null) {
            return reject(CharacterHomeFault.MALFORMED_SEQUENCE, "sequence is missing or not an integer");
        }
        if (sequence < 0) {
            return reject(CharacterHomeFault.MALFORMED_SEQUENCE, "sequence is negative: " + sequence);
        }

        String signingKeyId = string(fields, CharacterHomeCodec.SIGNING_KEY_ID);
        if (signingKeyId == null) {
            return reject(
                    CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.SIGNING_KEY_ID + "'");
        }

        String transportKeyB64 = string(fields, CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY);
        if (transportKeyB64 == null) {
            return reject(
                    CharacterHomeFault.MISSING_FIELD,
                    "record has no '" + CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY + "'");
        }
        byte[] transportKey;
        try {
            transportKey = CharacterHomeCodec.decodeBase64Url(transportKeyB64);
            // Decodes and validates as an X25519 key; the return value is discarded, we only needed the
            // check. Storing the X.509 bytes, the same form the column and the channel expect.
            X25519KeyExchange.decodePublicKey(transportKey);
        } catch (RuntimeException e) {
            return reject(CharacterHomeFault.MALFORMED_TRANSPORT_KEY, "home transport key is not a valid X25519 key");
        }

        String signatureB64 = string(fields, CharacterHomeCodec.SIGNATURE);
        if (signatureB64 == null) {
            return reject(CharacterHomeFault.MISSING_FIELD, "record has no '" + CharacterHomeCodec.SIGNATURE + "'");
        }
        byte[] signature;
        try {
            signature = CharacterHomeCodec.decodeBase64Url(signatureB64);
        } catch (RuntimeException e) {
            return reject(CharacterHomeFault.INVALID_SIGNATURE, "signature is not decodable base64url");
        }

        // A home server may only sign a binding for itself: the signing key must belong to the home DID.
        String signerDid = didOf(signingKeyId);
        if (!homeServerDid.equals(signerDid)) {
            return reject(
                    CharacterHomeFault.SIGNER_NOT_HOME,
                    "signed by " + signerDid + " but the record is homed at " + homeServerDid);
        }

        PublicKey key = keyResolver.resolve(signingKeyId);
        if (key == null) {
            return reject(CharacterHomeFault.UNKNOWN_SIGNING_KEY, "no key resolves for kid '" + signingKeyId + "'");
        }

        CharacterHomeRecord record;
        try {
            record = new CharacterHomeRecord(
                    accountDid,
                    characterId,
                    slot,
                    homeServerDid,
                    signingKeyId,
                    homeEndpoint,
                    transportKey,
                    sequence,
                    signature);
        } catch (RuntimeException e) {
            // Every field above was validated, so the record constructor should not object; if it somehow
            // does (e.g. an oversized signature the CHECK would also reject), treat it as an invalid record
            // rather than letting the exception escape.
            return reject(CharacterHomeFault.INVALID_SIGNATURE, "record fields do not form a valid record");
        }
        if (!record.verify(key)) {
            return reject(
                    CharacterHomeFault.INVALID_SIGNATURE, "signature does not cover the record's canonical bytes");
        }
        return CharacterHomeVerification.accepted(record);
    }

    private static CharacterHomeVerification reject(CharacterHomeFault fault, String detail) {
        return CharacterHomeVerification.rejected(fault, detail);
    }

    private static boolean isDid(String value) {
        return value.length() <= DID_MAX_LENGTH && DID_SHAPE.matcher(value).matches();
    }

    private static String didOf(String kid) {
        int fragment = kid.indexOf('#');
        return fragment < 0 ? kid : kid.substring(0, fragment);
    }

    private static String string(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value instanceof String s ? s : null;
    }

    private static Long integer(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        // Accept any integral JSON number; reject a fractional one, which is never a slot or a counter.
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof BigInteger b) {
            try {
                return b.longValueExact();
            } catch (ArithmeticException overflow) {
                // A number larger than Long can hold is not a usable slot or sequence — and it arrives from
                // an untrusted server. Return null so the caller refuses it as a typed MALFORMED_*, rather
                // than letting the exception escape verify() and blow up the whole ingest. This is the exact
                // defect the discovery verifier once had.
                return null;
            }
        }
        return null;
    }
}
