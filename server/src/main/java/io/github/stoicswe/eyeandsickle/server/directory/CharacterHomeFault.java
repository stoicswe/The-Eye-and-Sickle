package io.github.stoicswe.eyeandsickle.server.directory;

/**
 * Every reason {@link CharacterHomeRecordVerifier} refuses a published character-home record.
 *
 * <p>A record arrives from an untrusted home server ({@code
 * docs/architecture/03-server-and-federation.md} §1, {@code 09} §4), so refusal is an expected outcome,
 * not an error — the classifications exist so an operator log and the test suite can say precisely
 * <em>why</em> a binding was not admitted. The verifier reports the first fault it hits; the ordering
 * below roughly follows the order they are checked, cheapest and most structural first.
 *
 * <p>Mirrors {@code DescriptorFault} in the discovery slice deliberately: the two verifiers face the same
 * threat model, so a reviewer reasons about one refusal vocabulary, not two. Every malformation is a
 * typed refusal and none is ever an exception thrown out of the verify path — the number-overflow and
 * bad-base64 cases that a naive parser would throw on are folded back into {@link #MALFORMED_SEQUENCE},
 * {@link #MALFORMED_SLOT}, {@link #MALFORMED_TRANSPORT_KEY} and {@link #INVALID_SIGNATURE} instead.
 */
public enum CharacterHomeFault {

    /** The raw record exceeds the configured byte cap. A DoS bound, checked before parsing. */
    TOO_LARGE,

    /** The bytes are not well-formed JSON, or not the expected object shape. */
    MALFORMED_JSON,

    /** A required field (accountDid, characterId, slot, homeServerDid, endpoint, key, sequence, signature) is missing. */
    MISSING_FIELD,

    /** {@code accountDid} is not DID-shaped ({@code is_did} in the schema). */
    MALFORMED_ACCOUNT_DID,

    /** {@code homeServerDid} is not DID-shaped. */
    MALFORMED_HOME_DID,

    /** {@code characterId} is not a well-formed UUID. */
    MALFORMED_CHARACTER_ID,

    /** {@code slot} is absent, non-integer, or outside the {@code 1..MAX_SLOT} structural range. */
    MALFORMED_SLOT,

    /** {@code homeEndpoint} does not match {@code ^https?://...} or is over the length cap. */
    MALFORMED_ENDPOINT,

    /** {@code sequence} is absent, negative, fractional, or larger than {@code long} can hold. */
    MALFORMED_SEQUENCE,

    /** The home transport public key is not a decodable X25519 key of the permitted length. */
    MALFORMED_TRANSPORT_KEY,

    /**
     * The signing {@code kid} does not belong to {@code homeServerDid}; a home server may only sign a
     * binding for itself, so the key's DID part must equal the home DID it names.
     */
    SIGNER_NOT_HOME,

    /** No public key resolves for the signing {@code kid} — the home server's DID could not be resolved to a key. */
    UNKNOWN_SIGNING_KEY,

    /** The signature does not cover the record's canonical bytes. Tampered, replayed under a new field, or forged. */
    INVALID_SIGNATURE
}
