package io.github.stoicswe.eyeandsickle.protocol.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import java.security.KeyPair;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end proof that the provenance crypto stack works as {@code
 * docs/architecture/04-item-provenance.md} describes: serialize → canonicalize (JCS/RFC 8785) → sign
 * (Ed25519) → verify, and that tampering breaks it.
 *
 * <p>This is the scaffold's load-bearing test. If it passes, the architecture's central claim — that
 * a client can independently re-verify an item's history offline, without trusting the server's UI —
 * is mechanically true rather than merely intended.
 */
class ProvenanceSigningTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static ProvenancePayload genesisPayload() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("power", 42);
        attrs.put("durability", 0.87);
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777"),
                "hacking_tool_tier2",
                attrs,
                ProvenanceEventType.INITIAL_MINT,
                "did:plc:holder00000000000000",
                "did:plc:issuer00000000000000",
                null,
                0,
                "2026-07-23T18:04:00Z",
                "Yk9mQjNwWmhLdE5xVXhBZw");
    }

    @Test
    @DisplayName("canonicalize -> sign -> verify round-trips")
    void signAndVerifyRoundTrip() {
        KeyPair issuer = Ed25519Signatures.generateKeyPair();
        byte[] canonical = JsonCanonicalization.canonicalize(MAPPER.writeValueAsString(genesisPayload()));

        byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), canonical);

        assertThat(signature).hasSize(64);
        assertThat(Ed25519Signatures.verify(issuer.getPublic(), canonical, signature))
                .as("a signature made over these exact canonical bytes must verify")
                .isTrue();
    }

    @Test
    @DisplayName("a tampered payload no longer verifies")
    void tamperedPayloadFailsVerification() {
        KeyPair issuer = Ed25519Signatures.generateKeyPair();
        ProvenancePayload original = genesisPayload();
        byte[] canonical = JsonCanonicalization.canonicalize(MAPPER.writeValueAsString(original));
        byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), canonical);

        // The classic attack: a dishonest server rewrites the item's stats but keeps the signature.
        Map<String, Object> buffedAttrs = new LinkedHashMap<>(original.itemAttrs());
        buffedAttrs.put("power", 9001);
        ProvenancePayload tampered = new ProvenancePayload(
                original.recordVersion(),
                original.itemId(),
                original.itemType(),
                buffedAttrs,
                original.eventType(),
                original.holderDid(),
                original.issuerDid(),
                original.prevRecordHash(),
                original.chainDepth(),
                original.timestamp(),
                original.nonce());

        byte[] tamperedCanonical = JsonCanonicalization.canonicalize(MAPPER.writeValueAsString(tampered));

        assertThat(Ed25519Signatures.verify(issuer.getPublic(), tamperedCanonical, signature))
                .as("rewriting itemAttrs must invalidate the issuer's signature")
                .isFalse();
    }

    @Test
    @DisplayName("another server's key cannot vouch for this record")
    void wrongKeyFailsVerification() {
        KeyPair issuer = Ed25519Signatures.generateKeyPair();
        KeyPair impostor = Ed25519Signatures.generateKeyPair();
        byte[] canonical = JsonCanonicalization.canonicalize(MAPPER.writeValueAsString(genesisPayload()));
        byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), canonical);

        assertThat(Ed25519Signatures.verify(impostor.getPublic(), canonical, signature))
                .isFalse();
    }

    @Test
    @DisplayName("canonicalization is key-order independent — the property signatures depend on")
    void canonicalizationIsOrderIndependent() {
        // Two JSON documents that differ only in key order and whitespace are the same document.
        // If this were not true, a record signed by a Java server would fail to verify on a server
        // whose JSON library happened to order keys differently, and cross-server play would break
        // in a way that looked like cheating.
        String a = "{\"b\":2,\"a\":1,\"c\":{\"z\":true,\"y\":null}}";
        String b = "{  \"c\" : { \"y\" : null, \"z\" : true } ,\n \"a\":1, \"b\":2 }";

        assertThat(JsonCanonicalization.canonicalizeToString(a))
                .isEqualTo(JsonCanonicalization.canonicalizeToString(b));
    }

    @Test
    @DisplayName("RFC 8785 escaping and number formatting match the spec")
    void matchesRfc8785Examples() {
        // Straight from RFC 8785 §3.2.3: literals keep their shortest round-trip form and control
        // characters use the short escapes.
        assertThat(JsonCanonicalization.canonicalizeToString("{\"numbers\":[1E30,1.0,0.1]}"))
                .isEqualTo("{\"numbers\":[1e+30,1,0.1]}");
        assertThat(JsonCanonicalization.canonicalizeToString("{\"s\":\"\\u000b\\t\\n\"}"))
                .isEqualTo("{\"s\":\"\\u000b\\t\\n\"}");
    }

    @Test
    @DisplayName("a genesis record must not chain to a predecessor")
    void genesisInvariantIsEnforced() {
        assertThatThrownBy(() -> new ProvenancePayload(
                        1,
                        UUID.randomUUID(),
                        "hacking_tool_tier2",
                        Map.of(),
                        ProvenanceEventType.INITIAL_MINT,
                        "did:plc:holder",
                        "did:plc:issuer",
                        "sha256-of-something", // depth 0 but claims a predecessor
                        0,
                        "2026-07-23T18:04:00Z",
                        "nonce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chainDepth");

        assertThatThrownBy(() -> new ProvenancePayload(
                        1,
                        UUID.randomUUID(),
                        "hacking_tool_tier2",
                        Map.of(),
                        ProvenanceEventType.TRADE,
                        "did:plc:holder",
                        "did:plc:issuer",
                        null, // depth 3 but nothing to chain to
                        3,
                        "2026-07-23T18:04:00Z",
                        "nonce"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duel outcomes carry a validator committee's signatures, not one server's")
    void duelOutcomesAreMultiSignature() {
        ProvenancePayload payload = new ProvenancePayload(
                1,
                UUID.randomUUID(),
                "hacking_tool_tier2",
                Map.of(),
                ProvenanceEventType.DUEL_GRANT,
                "did:plc:winner",
                "duel:0a9f-4c2e", // synthetic quorum identifier, not a server DID
                "sha256-of-previous",
                7,
                "2026-07-23T18:04:00Z",
                "nonce");

        byte[] canonical = JsonCanonicalization.canonicalize(MAPPER.writeValueAsString(payload));
        var signatures = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> {
                    KeyPair validator = Ed25519Signatures.generateKeyPair();
                    byte[] sig = Ed25519Signatures.sign(validator.getPrivate(), canonical);
                    return SignatureBlock.eddsa(
                            "did:plc:validator" + i + "#key1",
                            Base64.getUrlEncoder().withoutPadding().encodeToString(sig));
                })
                .toList();

        // N = 7 committee, f = 2, so 5 of 7 must agree (architecture/05 §1).
        ProvenanceEnvelope envelope = ProvenanceEnvelope.quorum(payload, signatures);

        assertThat(envelope.isMultiSignature()).isTrue();
        assertThat(envelope.signatures()).hasSize(5);
        assertThat(envelope.payloadCanonicalization()).isEqualTo(ProvenanceEnvelope.JCS_RFC8785);
        assertThat(envelope.signatures().getFirst().signerDid()).isEqualTo("did:plc:validator1");
    }

    @Test
    @DisplayName("an envelope without a signature is not an envelope")
    void unsignedEnvelopeIsRejected() {
        assertThatThrownBy(() ->
                        new ProvenanceEnvelope(genesisPayload(), ProvenanceEnvelope.JCS_RFC8785, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
