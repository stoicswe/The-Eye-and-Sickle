package io.github.stoicswe.eyeandsickle.server.discovery;

/**
 * Every reason {@link ServerDescriptorVerifier} refuses a self-descriptor.
 *
 * <p>A descriptor arrives from an untrusted server ({@code
 * docs/architecture/03-server-and-federation.md} §1), so refusal is an expected outcome, not an error
 * — the classifications exist so an operator log and the test suite can say precisely <em>why</em> a
 * peer was not admitted. The verifier reports the first fault it hits; ordering below roughly follows
 * the order they are checked, cheapest and most structural first.
 */
public enum DescriptorFault {

    /** The raw descriptor exceeds the configured byte cap. A DoS bound, checked before parsing. */
    TOO_LARGE,

    /** The bytes are not well-formed JSON, or not the expected envelope object shape. */
    MALFORMED_JSON,

    /** A required field (peerDid, endpoint, sequence, transport key, signature) is missing. */
    MISSING_FIELD,

    /** The declared canonicalization is not one this build signs and verifies. */
    UNSUPPORTED_CANONICALIZATION,

    /** {@code peerDid} is not DID-shaped ({@code is_did} in the schema). */
    MALFORMED_DID,

    /** {@code endpoint} does not match {@code ^https?://...} or is over the length cap. */
    MALFORMED_ENDPOINT,

    /** {@code sequence} is absent, negative, or not an integer. */
    MALFORMED_SEQUENCE,

    /** The transport public key is not a decodable X25519 key of the permitted length. */
    MALFORMED_TRANSPORT_KEY,

    /** The transport-key validity window is inverted ({@code notAfter <= notBefore}) or unparseable. */
    MALFORMED_KEY_WINDOW,

    /**
     * The transport-key {@code notBefore} is further in the future than the tolerated clock skew — a
     * descriptor that is not yet valid anywhere is not worth storing as current.
     */
    NOT_YET_VALID,

    /** The signature's {@code kid} does not belong to {@code peerDid}; a server may only sign for itself. */
    SIGNER_NOT_OWNER,

    /** The signature algorithm is not {@code EdDSA}; provenance and identity sign with Ed25519 and nothing else. */
    WRONG_SIGNATURE_ALGORITHM,

    /** No public key resolves for the signing {@code kid} — the DID could not be resolved to a key. */
    UNKNOWN_SIGNING_KEY,

    /** The signature does not cover the canonical descriptor bytes. Tampered, or signed by the wrong key. */
    INVALID_SIGNATURE
}
