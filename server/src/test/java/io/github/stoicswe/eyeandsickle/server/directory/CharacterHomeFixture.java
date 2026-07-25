package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only builder for signed — and deliberately malformed — character-home publish envelopes.
 *
 * <p>The valid path goes through the real {@link CharacterHomeRecord#sign} and {@link
 * CharacterHomeCodec#writeEnvelope}, so an accepted record was signed exactly the way a home server signs.
 * The malformed paths take the field map of a genuinely-signed record and mutate one field, so a single
 * value can be omitted, mistyped, or made out-of-range without the codec "helping" by producing a
 * well-formed one. A real Ed25519 keypair (for the home signature) and X25519 keypair (for the transport
 * key) mean the signature is real, not stubbed.
 */
final class CharacterHomeFixture {

    static final String ACCOUNT_DID = "did:plc:account0000000000";
    static final String HOME_DID = "did:plc:home000000000000";
    static final String OTHER_HOME_DID = "did:plc:otherhome0000000";
    static final String KID = HOME_DID + "#key1";
    static final String ENDPOINT = "https://home.example.org";
    static final UUID CHARACTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final int SLOT = 2;

    /** A well-formed base64url signature that is structurally valid but verifies against no key. */
    static final String DUMMY_SIG = base64Url(new byte[64]);

    final KeyPair signing = Ed25519Signatures.generateKeyPair();
    final PublicKey transportKey = X25519KeyExchange.generateKeyPair().getPublic();

    /** Resolves {@link #KID} to this fixture's home signing key; every other kid resolves to nothing. */
    CharacterHomeKeyResolver resolver() {
        return CharacterHomeKeyResolver.ofMap(Map.of(KID, signing.getPublic()));
    }

    // ------------------------------------------------------------------ valid, signed records

    /** A genuinely-signed record for {@link #CHARACTER_ID}, slot {@link #SLOT}, at {@code sequence}. */
    CharacterHomeRecord record(long sequence) {
        return record(CHARACTER_ID, SLOT, sequence, signing.getPrivate());
    }

    /** A genuinely-signed record with a chosen character, slot, sequence and signing key. */
    CharacterHomeRecord record(UUID characterId, int slot, long sequence, PrivateKey signingKey) {
        return CharacterHomeRecord.sign(
                ACCOUNT_DID, characterId, slot, HOME_DID, KID, ENDPOINT, transportKey, sequence, signingKey);
    }

    /** The publish envelope of a signed record at {@code sequence}. */
    String signed(long sequence) {
        return CharacterHomeCodec.writeEnvelope(record(sequence));
    }

    // ------------------------------------------------------------------ hand-assembled envelopes

    /** A mutable field map of a genuinely-signed record — a test mutates one field to exercise one failure. */
    Map<String, Object> fieldMap(long sequence) {
        return fieldMap(record(sequence));
    }

    /** A mutable field map of an arbitrary signed record. */
    Map<String, Object> fieldMap(CharacterHomeRecord record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CharacterHomeCodec.RECORD_VERSION, CharacterHomeCodec.CURRENT_RECORD_VERSION);
        fields.put(CharacterHomeCodec.ACCOUNT_DID, record.accountDid());
        fields.put(CharacterHomeCodec.CHARACTER_ID, record.characterId().toString());
        fields.put(CharacterHomeCodec.SLOT, record.slot());
        fields.put(CharacterHomeCodec.HOME_SERVER_DID, record.homeServerDid());
        fields.put(CharacterHomeCodec.HOME_ENDPOINT, record.homeEndpoint());
        fields.put(CharacterHomeCodec.HOME_TRANSPORT_PUBLIC_KEY, base64Url(record.homeTransportPublicKey()));
        fields.put(CharacterHomeCodec.SIGNING_KEY_ID, record.signingKeyId());
        fields.put(CharacterHomeCodec.SEQUENCE, record.sequenceNumber());
        fields.put(CharacterHomeCodec.SIGNATURE, base64Url(record.signature()));
        return fields;
    }

    /** Serializes a (possibly mutated) field map to a publish envelope. */
    String envelope(Map<String, Object> fields) {
        return Jsonb.writeObject(fields);
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
