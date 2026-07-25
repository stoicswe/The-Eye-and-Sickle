package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes and decodes the signed self-descriptor envelope.
 *
 * <h2>One trust path, reused — not a second one</h2>
 *
 * A self-descriptor is signed and verified exactly the way an item provenance record is ({@code
 * docs/architecture/04-item-provenance.md} §1, §4): a JSON payload is put into RFC&nbsp;8785 canonical
 * byte form and an Ed25519 <em>detached</em> signature is made over those bytes. This class does not
 * invent a signing scheme; it composes {@code JsonCanonicalization} and {@code Ed25519Signatures} from
 * the protocol module — the same crypto stack the whole game runs on, so a reviewer reasons about one
 * construction, not two.
 *
 * <h2>The envelope shape</h2>
 *
 * <pre>{@code
 * {
 *   "descriptor": {
 *     "descriptorVersion": 1,
 *     "peerDid": "did:plc:xxx",
 *     "endpoint": "https://home.example.org",
 *     "sequence": 7,
 *     "capabilities": ["federation", "validator"],
 *     "transportPublicKey": "<base64url X.509 X25519>",
 *     "transportKeyId": "did:plc:xxx#transport-1",
 *     "transportKeyNotBefore": "2026-07-24T00:00:00Z",
 *     "transportKeyNotAfter": "2026-07-31T00:00:00Z",
 *     "issuedAt": "2026-07-24T00:00:00Z"
 *   },
 *   "payloadCanonicalization": "JCS-RFC8785",
 *   "signature": { "alg": "EdDSA", "kid": "did:plc:xxx#key1", "sig": "<base64url>" }
 * }
 * }</pre>
 *
 * The signature covers the canonical bytes of the {@code descriptor} object alone. The whole envelope
 * is what gets stored verbatim in {@code federation_peers.self_descriptor}; verification re-derives the
 * canonical bytes from the parsed {@code descriptor} object, so storage order and whitespace are
 * irrelevant to whether the signature checks.
 *
 * <h2>Structural failures are typed</h2>
 *
 * {@link #parse(String, int)} throws {@link DescriptorCodecException} carrying a {@link DescriptorFault}
 * for anything structurally wrong (not JSON, wrong shape, missing top-level part, unsupported
 * canonicalization). Field-level <em>semantic</em> checks — endpoint shape, DID shape, key window
 * sanity — belong to {@link ServerDescriptorVerifier}, which owns policy; this class owns format.
 */
public final class ServerDescriptorCodec {

    // Envelope keys.
    static final String ENVELOPE_DESCRIPTOR = "descriptor";
    static final String ENVELOPE_CANONICALIZATION = "payloadCanonicalization";
    static final String ENVELOPE_SIGNATURE = "signature";
    static final String SIGNATURE_ALG = "alg";
    static final String SIGNATURE_KID = "kid";
    static final String SIGNATURE_SIG = "sig";

    // Descriptor payload keys.
    static final String DESCRIPTOR_VERSION = "descriptorVersion";
    static final String PEER_DID = "peerDid";
    static final String ENDPOINT = "endpoint";
    static final String SEQUENCE = "sequence";
    static final String CAPABILITIES = "capabilities";
    static final String TRANSPORT_PUBLIC_KEY = "transportPublicKey";
    static final String TRANSPORT_KEY_ID = "transportKeyId";
    static final String TRANSPORT_KEY_NOT_BEFORE = "transportKeyNotBefore";
    static final String TRANSPORT_KEY_NOT_AFTER = "transportKeyNotAfter";
    static final String ISSUED_AT = "issuedAt";

    /** The current descriptor schema version. Bumped only if the payload shape changes. */
    public static final int CURRENT_DESCRIPTOR_VERSION = 1;

    private ServerDescriptorCodec() {}

    // ------------------------------------------------------------------ produce

    /**
     * Builds and signs a self-descriptor envelope.
     *
     * <p>Used by tests and by a {@link LocalDescriptorSource} implementation. The real signing key is a
     * server's DID Ed25519 key, which the identity slice holds; this method only needs the private key
     * handed to it, and never persists or logs it.
     *
     * @param peerDid the signing server's DID
     * @param endpoint its reachable endpoint
     * @param sequence the monotonic version counter; must be strictly greater than any previously
     *     published value for this DID, or peers will (correctly) ignore it as stale
     * @param capabilities self-declared capabilities, may be empty
     * @param transportPublicKey the X25519 transport key to attest
     * @param transportKeyId the DID fragment naming the transport key, or {@code null}
     * @param transportKeyNotBefore validity start, or {@code null} for unbounded
     * @param transportKeyNotAfter validity end, or {@code null} for unbounded
     * @param issuedAt when this descriptor was produced, or {@code null}
     * @param signingKid the DID fragment identifying the DID signing key, e.g. {@code did:plc:xxx#key1}
     * @param didSigningKey the Ed25519 private key belonging to {@code peerDid}
     * @return the envelope JSON, ready to publish and to store verbatim
     */
    public static String sign(
            String peerDid,
            String endpoint,
            long sequence,
            List<String> capabilities,
            PublicKey transportPublicKey,
            String transportKeyId,
            Instant transportKeyNotBefore,
            Instant transportKeyNotAfter,
            Instant issuedAt,
            String signingKid,
            PrivateKey didSigningKey) {
        Objects.requireNonNull(peerDid, "peerDid");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(transportPublicKey, "transportPublicKey");
        Objects.requireNonNull(signingKid, "signingKid");
        Objects.requireNonNull(didSigningKey, "didSigningKey");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative, was " + sequence);
        }

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(DESCRIPTOR_VERSION, CURRENT_DESCRIPTOR_VERSION);
        descriptor.put(PEER_DID, peerDid);
        descriptor.put(ENDPOINT, endpoint);
        descriptor.put(SEQUENCE, sequence);
        descriptor.put(CAPABILITIES, capabilities == null ? List.of() : List.copyOf(capabilities));
        descriptor.put(TRANSPORT_PUBLIC_KEY, base64Url(X25519KeyExchange.encodePublicKey(transportPublicKey)));
        if (transportKeyId != null) {
            descriptor.put(TRANSPORT_KEY_ID, transportKeyId);
        }
        if (transportKeyNotBefore != null) {
            descriptor.put(TRANSPORT_KEY_NOT_BEFORE, transportKeyNotBefore.toString());
        }
        if (transportKeyNotAfter != null) {
            descriptor.put(TRANSPORT_KEY_NOT_AFTER, transportKeyNotAfter.toString());
        }
        if (issuedAt != null) {
            descriptor.put(ISSUED_AT, issuedAt.toString());
        }

        String descriptorJson = Jsonb.writeObject(descriptor);
        byte[] canonical = JsonCanonicalization.canonicalize(descriptorJson);
        byte[] signature = Ed25519Signatures.sign(didSigningKey, canonical);

        Map<String, Object> signatureBlock = new LinkedHashMap<>();
        signatureBlock.put(SIGNATURE_ALG, Ed25519Signatures.JOSE_ALG);
        signatureBlock.put(SIGNATURE_KID, signingKid);
        signatureBlock.put(SIGNATURE_SIG, base64Url(signature));

        Map<String, Object> envelope = new LinkedHashMap<>();
        // Store the parsed descriptor object, not the serialized string, so the stored envelope is a
        // proper nested object rather than a JSON string within JSON.
        envelope.put(ENVELOPE_DESCRIPTOR, descriptor);
        envelope.put(ENVELOPE_CANONICALIZATION, ProvenanceEnvelope.JCS_RFC8785);
        envelope.put(ENVELOPE_SIGNATURE, signatureBlock);
        return Jsonb.writeObject(envelope);
    }

    // ------------------------------------------------------------------ consume

    /**
     * Parses an envelope's structure and computes the canonical bytes the signature must cover.
     *
     * @param rawEnvelope the received JSON, verbatim
     * @param maxBytes the configured byte cap; checked before any parsing
     * @return the parsed structural pieces
     * @throws DescriptorCodecException with a {@link DescriptorFault} if the envelope is oversized, not
     *     JSON, not the expected object shape, missing a top-level part, or declares an unsupported
     *     canonicalization
     */
    public static ParsedEnvelope parse(String rawEnvelope, int maxBytes) {
        Objects.requireNonNull(rawEnvelope, "rawEnvelope");
        int size = rawEnvelope.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (size > maxBytes) {
            throw new DescriptorCodecException(
                    DescriptorFault.TOO_LARGE, "Descriptor is " + size + " bytes, over the " + maxBytes + "-byte cap");
        }

        Map<String, Object> envelope;
        try {
            envelope = Jsonb.readObject(rawEnvelope);
        } catch (RuntimeException e) {
            throw new DescriptorCodecException(DescriptorFault.MALFORMED_JSON, "Not a well-formed JSON object");
        }

        Object descriptorObj = envelope.get(ENVELOPE_DESCRIPTOR);
        if (!(descriptorObj instanceof Map<?, ?> descriptorMap)) {
            throw new DescriptorCodecException(
                    DescriptorFault.MISSING_FIELD, "Envelope has no '" + ENVELOPE_DESCRIPTOR + "' object");
        }

        Object canonicalization = envelope.get(ENVELOPE_CANONICALIZATION);
        if (!ProvenanceEnvelope.JCS_RFC8785.equals(canonicalization)) {
            throw new DescriptorCodecException(
                    DescriptorFault.UNSUPPORTED_CANONICALIZATION,
                    "Envelope declares canonicalization '" + canonicalization + "'; this build verifies "
                            + ProvenanceEnvelope.JCS_RFC8785);
        }

        Object signatureObj = envelope.get(ENVELOPE_SIGNATURE);
        if (!(signatureObj instanceof Map<?, ?> signatureMap)) {
            throw new DescriptorCodecException(
                    DescriptorFault.MISSING_FIELD, "Envelope has no '" + ENVELOPE_SIGNATURE + "' object");
        }
        String alg = asString(signatureMap.get(SIGNATURE_ALG));
        String kid = asString(signatureMap.get(SIGNATURE_KID));
        String sig = asString(signatureMap.get(SIGNATURE_SIG));
        if (kid == null || sig == null) {
            throw new DescriptorCodecException(
                    DescriptorFault.MISSING_FIELD, "Signature block is missing 'kid' or 'sig'");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> descriptor = (Map<String, Object>) descriptorMap;
        byte[] canonicalBytes;
        try {
            // Re-serialize the parsed descriptor object and canonicalize it. JCS is deterministic, so
            // this reproduces the exact bytes the signer canonicalized regardless of how the received
            // JSON was ordered or spaced.
            canonicalBytes = JsonCanonicalization.canonicalize(Jsonb.writeObject(descriptor));
        } catch (RuntimeException e) {
            throw new DescriptorCodecException(
                    DescriptorFault.MALFORMED_JSON, "Descriptor object cannot be canonicalized");
        }
        return new ParsedEnvelope(descriptor, canonicalBytes, alg, kid, sig);
    }

    /**
     * Decodes a base64url signature or key from a descriptor.
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

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /**
     * The structural pieces of a parsed envelope: the descriptor payload, the canonical bytes its
     * signature must cover, and the signature block's fields.
     *
     * @param descriptor the descriptor payload object, parsed
     * @param canonicalBytes the RFC 8785 canonical bytes of {@code descriptor}
     * @param alg the declared signature algorithm, possibly {@code null}
     * @param kid the signing key's DID fragment
     * @param sig the base64url signature
     */
    public record ParsedEnvelope(
            Map<String, Object> descriptor, byte[] canonicalBytes, String alg, String kid, String sig) {

        public ParsedEnvelope {
            descriptor = Map.copyOf(descriptor);
            canonicalBytes = canonicalBytes.clone();
        }

        @Override
        public byte[] canonicalBytes() {
            return canonicalBytes.clone();
        }
    }

    /** A structural parse failure carrying the {@link DescriptorFault} the verifier should report. */
    public static final class DescriptorCodecException extends RuntimeException {

        private final transient DescriptorFault fault;

        DescriptorCodecException(DescriptorFault fault, String message) {
            super(message);
            this.fault = fault;
        }

        /** @return the fault classification */
        public DescriptorFault fault() {
            return fault;
        }
    }
}
