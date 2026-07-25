package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only builder for signed — and deliberately malformed — self-descriptor envelopes.
 *
 * <p>The valid path goes through the real {@link ServerDescriptorCodec#sign}, so an accepted descriptor
 * was signed exactly the way the production signer signs. The malformed paths assemble the envelope map
 * directly, so a single field can be omitted, mistyped, or oversized without the codec "helping" by
 * producing a well-formed one. A test keypair (Ed25519 for the DID signature, X25519 for the transport
 * key) means the signature is real, not stubbed.
 */
final class DescriptorFixture {

    static final String PEER_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    static final String OTHER_DID = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";
    static final String KID = PEER_DID + "#key1";
    static final String ENDPOINT = "https://home.example.org";

    /** A fixed instant; verification is clock-injected so nothing here reads a wall clock. */
    static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    /** A well-formed base64url signature that is structurally valid but verifies against no key. */
    static final String DUMMY_SIG = base64Url(new byte[64]);

    final KeyPair signing = Ed25519Signatures.generateKeyPair();
    final PublicKey transportKey = X25519KeyExchange.generateKeyPair().getPublic();

    /** Resolves {@link #KID} to this fixture's signing key; every other kid resolves to nothing. */
    PeerKeyResolver resolver() {
        return PeerKeyResolver.ofMap(Map.of(KID, signing.getPublic()));
    }

    // ------------------------------------------------------------------ valid, signed by the codec

    /** A valid, signed envelope for {@link #PEER_DID} at {@code sequence}, declaring "federation". */
    String signed(long sequence) {
        return signed(sequence, List.of(ServerDescriptor.CAPABILITY_FEDERATION), null);
    }

    /** A valid, signed envelope with explicit capabilities and issuedAt. */
    String signed(long sequence, List<String> capabilities, Instant issuedAt) {
        return ServerDescriptorCodec.sign(
                PEER_DID,
                ENDPOINT,
                sequence,
                capabilities,
                transportKey,
                PEER_DID + "#transport-1",
                null,
                null,
                issuedAt,
                KID,
                signing.getPrivate());
    }

    // ------------------------------------------------------------------ hand-assembled descriptors

    /** A fully-valid descriptor payload map that a test then mutates to exercise one failure. */
    Map<String, Object> validDescriptorMap(long sequence) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(ServerDescriptorCodec.DESCRIPTOR_VERSION, ServerDescriptorCodec.CURRENT_DESCRIPTOR_VERSION);
        descriptor.put(ServerDescriptorCodec.PEER_DID, PEER_DID);
        descriptor.put(ServerDescriptorCodec.ENDPOINT, ENDPOINT);
        descriptor.put(ServerDescriptorCodec.SEQUENCE, sequence);
        descriptor.put(ServerDescriptorCodec.CAPABILITIES, List.of(ServerDescriptor.CAPABILITY_FEDERATION));
        descriptor.put(
                ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY, base64Url(X25519KeyExchange.encodePublicKey(transportKey)));
        return descriptor;
    }

    /** Wraps a descriptor map in an envelope with an explicit (possibly bogus) signature block. */
    String envelope(Map<String, Object> descriptor, String alg, String kid, String sigBase64) {
        Map<String, Object> signature = new LinkedHashMap<>();
        if (alg != null) {
            signature.put(ServerDescriptorCodec.SIGNATURE_ALG, alg);
        }
        if (kid != null) {
            signature.put(ServerDescriptorCodec.SIGNATURE_KID, kid);
        }
        if (sigBase64 != null) {
            signature.put(ServerDescriptorCodec.SIGNATURE_SIG, sigBase64);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(ServerDescriptorCodec.ENVELOPE_DESCRIPTOR, descriptor);
        envelope.put(ServerDescriptorCodec.ENVELOPE_CANONICALIZATION, ProvenanceEnvelope.JCS_RFC8785);
        envelope.put(ServerDescriptorCodec.ENVELOPE_SIGNATURE, signature);
        return Jsonb.writeObject(envelope);
    }

    /** An envelope whose signature is a real Ed25519 signature over {@code descriptor}, made by {@code key}. */
    String envelopeSignedBy(Map<String, Object> descriptor, String kid, PrivateKey key) {
        byte[] canonical = JsonCanonicalization.canonicalize(Jsonb.writeObject(descriptor));
        byte[] signature = Ed25519Signatures.sign(key, canonical);
        return envelope(descriptor, Ed25519Signatures.JOSE_ALG, kid, base64Url(signature));
    }

    /** A structurally valid envelope carrying a dummy signature — for faults checked before verification. */
    String envelopeWithDummySignature(Map<String, Object> descriptor) {
        return envelope(descriptor, Ed25519Signatures.JOSE_ALG, KID, DUMMY_SIG);
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
