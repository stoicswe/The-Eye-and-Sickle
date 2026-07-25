package io.github.stoicswe.eyeandsickle.protocol.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.JsonCanonicalization;
import java.security.KeyPair;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire format down to the byte.
 *
 * <p>These assertions look pedantic and are not. The field names, their spelling and the presence of
 * a null are all inputs to the signature, so a change that looks cosmetic here invalidates every
 * record any server has ever signed. If one of these tests fails after a dependency bump, the right
 * response is to stop, not to update the expected string.
 */
class ProvenanceJsonTest {

    private static final UUID ITEM = UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777");
    private static final String HOLDER = "did:plc:holder00000000000000";
    private static final String ISSUER = "did:plc:issuer00000000000000";
    private static final String TIMESTAMP = "2026-07-23T18:04:00Z";
    private static final String NONCE = "Yk9mQjNwWmhLdE5xVXhBZw";

    private static ProvenancePayload payload(Map<String, Object> attrs) {
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM,
                "hacking_tool_tier2",
                attrs,
                ProvenanceEventType.INITIAL_MINT,
                HOLDER,
                ISSUER,
                null,
                0,
                TIMESTAMP,
                NONCE);
    }

    private static Map<String, Object> attributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("power", 42);
        attrs.put("durability", 0.87);
        return attrs;
    }

    @Nested
    @DisplayName("the payload document")
    class PayloadDocument {

        @Test
        @DisplayName("uses exactly the field names and order of architecture/04 §2")
        void fieldNamesMatchTheSpecification() {
            String expected = "{\"recordVersion\":1,"
                    + "\"itemId\":\"2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777\","
                    + "\"itemType\":\"hacking_tool_tier2\","
                    + "\"itemAttrs\":{},"
                    + "\"eventType\":\"initial_mint\","
                    + "\"holderDid\":\"did:plc:holder00000000000000\","
                    + "\"issuerDid\":\"did:plc:issuer00000000000000\","
                    + "\"prevRecordHash\":null,"
                    + "\"chainDepth\":0,"
                    + "\"timestamp\":\"2026-07-23T18:04:00Z\","
                    + "\"nonce\":\"Yk9mQjNwWmhLdE5xVXhBZw\"}";

            assertThat(ProvenanceJson.writePayload(payload(Map.of()))).isEqualTo(expected);
        }

        @Test
        @DisplayName("writes prevRecordHash as an explicit null at genesis, never omits it")
        void genesisCarriesAnExplicitNull() {
            // Omitting the key would change the canonical bytes, so a serializer that started
            // skipping nulls would invalidate every genesis record in the federation at once.
            assertThat(ProvenanceJson.writePayload(payload(Map.of()))).contains("\"prevRecordHash\":null");
        }

        @Test
        @DisplayName("carries the predecessor's hash on a non-genesis record")
        void nonGenesisCarriesItsLink() {
            ProvenancePayload genesis = payload(attributes());
            String link = RecordHash.of(genesis);
            ProvenancePayload second = ChainFixture.Edit.of(genesis)
                    .eventType(ProvenanceEventType.TRADE)
                    .prevRecordHash(link)
                    .chainDepth(1)
                    .build();

            assertThat(ProvenanceJson.writePayload(second)).contains("\"prevRecordHash\":\"" + link + "\"");
        }

        @Test
        @DisplayName("round-trips losslessly, attributes and all")
        void roundTripsLosslessly() {
            ProvenancePayload original = payload(attributes());

            ProvenancePayload decoded = ProvenanceJson.readPayload(ProvenanceJson.writePayload(original));

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.itemAttrs()).containsExactlyInAnyOrderEntriesOf(attributes());
        }

        @Test
        @DisplayName("round-trips a record with no attributes")
        void roundTripsEmptyAttributes() {
            ProvenancePayload original = payload(Map.of());

            assertThat(ProvenanceJson.readPayload(ProvenanceJson.writePayload(original)))
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("refuses a document that is not well-formed JSON")
        void refusesMalformedJson() {
            assertThatThrownBy(() -> ProvenanceJson.readPayload("{\"recordVersion\":"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a document with a missing or wrongly-typed field")
        void refusesStructurallyWrongDocuments() {
            String good = ProvenanceJson.writePayload(payload(Map.of()));

            assertThatThrownBy(() -> ProvenanceJson.readPayload(good.replace("\"nonce\":", "\"nnce\":")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonce");
            // A fractional chainDepth truncated to an int would let a forged record land on a
            // position it never claimed, so it is refused rather than coerced.
            assertThatThrownBy(() -> ProvenanceJson.readPayload(good.replace("\"chainDepth\":0", "\"chainDepth\":1.5")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chainDepth");
            assertThatThrownBy(() -> ProvenanceJson.readPayload(good.replace(ITEM.toString(), "not-a-uuid")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("itemId");
        }
    }

    @Nested
    @DisplayName("event type spelling")
    class EventTypeNames {

        @Test
        @DisplayName("is the lowercase snake_case of architecture/04 §2, not the Java constant name")
        void wireNamesMatchTheSpecification() {
            assertThat(ProvenanceJson.wireName(ProvenanceEventType.INITIAL_MINT))
                    .isEqualTo("initial_mint");
            assertThat(ProvenanceJson.wireName(ProvenanceEventType.SERVER_GRANT))
                    .isEqualTo("server_grant");
            assertThat(ProvenanceJson.wireName(ProvenanceEventType.TRADE)).isEqualTo("trade");
            assertThat(ProvenanceJson.wireName(ProvenanceEventType.DUEL_GRANT)).isEqualTo("duel_grant");
        }

        @Test
        @DisplayName("round-trips for every declared event type")
        void everyEventTypeRoundTrips() {
            // Guards the case where a fifth event type is added and only half-wired.
            for (ProvenanceEventType eventType : ProvenanceEventType.values()) {
                assertThat(ProvenanceJson.eventType(ProvenanceJson.wireName(eventType)))
                        .isEqualTo(eventType);
            }
        }

        @Test
        @DisplayName("refuses an event type the verifier could not authorize")
        void refusesUnknownEventTypes() {
            assertThatThrownBy(() -> ProvenanceJson.eventType("INITIAL_MINT"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProvenanceJson.eventType("admin_grant"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("the canonical signing bytes")
    class CanonicalBytes {

        @Test
        @DisplayName("are stable across calls and across equal payloads")
        void areStable() {
            ProvenancePayload one = payload(attributes());
            ProvenancePayload two = payload(attributes());

            assertThat(ProvenanceJson.canonicalBytes(one)).isEqualTo(ProvenanceJson.canonicalBytes(one));
            assertThat(ProvenanceJson.canonicalBytes(one)).isEqualTo(ProvenanceJson.canonicalBytes(two));
        }

        @Test
        @DisplayName("are the JCS form of the written payload, and nothing else")
        void areTheJcsFormOfTheWrittenPayload() {
            ProvenancePayload record = payload(attributes());

            assertThat(ProvenanceJson.canonicalBytes(record))
                    .isEqualTo(JsonCanonicalization.canonicalize(ProvenanceJson.writePayload(record)));
        }

        @Test
        @DisplayName("sort every key, including inside itemAttrs")
        void sortKeys() {
            String canonical = ProvenanceJson.canonicalJson(payload(attributes()));

            assertThat(canonical).startsWith("{\"chainDepth\":0,\"eventType\":\"initial_mint\"");
            assertThat(canonical).contains("\"itemAttrs\":{\"durability\":0.87,\"power\":42}");
            assertThat(canonical).endsWith("\"timestamp\":\"2026-07-23T18:04:00Z\"}");
        }

        @Test
        @DisplayName("are what a signature actually covers")
        void areWhatSignaturesCover() {
            KeyPair issuer = Ed25519Signatures.generateKeyPair();
            ProvenancePayload record = payload(attributes());
            byte[] signature = Ed25519Signatures.sign(issuer.getPrivate(), ProvenanceJson.canonicalBytes(record));

            // Re-deriving the bytes from a decoded copy must reproduce them, or an item could not be
            // verified on the server it was sent to.
            ProvenancePayload decoded = ProvenanceJson.readPayload(ProvenanceJson.writePayload(record));
            assertThat(Ed25519Signatures.verify(issuer.getPublic(), ProvenanceJson.canonicalBytes(decoded), signature))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("the envelope document")
    class EnvelopeDocument {

        private final ChainFixture fixture = new ChainFixture();

        @Test
        @DisplayName("uses the singular 'signature' object for a single-issuer record (§3)")
        void singleIssuerUsesTheSingularField() {
            ProvenanceEnvelope envelope = fixture.singleIssuer(fixture.genesis());

            String json = ProvenanceJson.writeEnvelope(envelope);

            assertThat(json).contains("\"payload\":{");
            assertThat(json).contains("\"payloadCanonicalization\":\"JCS-RFC8785\"");
            assertThat(json)
                    .contains("\"signature\":{\"alg\":\"EdDSA\",\"kid\":\""
                            + ChainFixture.kidOf(ChainFixture.HOME_SERVER) + "\",\"sig\":\"");
            assertThat(json).doesNotContain("\"signatures\"");
        }

        @Test
        @DisplayName("uses the 'signatures' array for a duel outcome (§3.1)")
        void duelOutcomeUsesTheArrayField() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload duel = ChainFixture.Edit.of(
                            fixture.following(genesis, ProvenanceEventType.DUEL_GRANT, ChainFixture.OTHER_HOLDER, "x"))
                    .issuerDid("duel:" + ChainFixture.DUEL_ID)
                    .build();
            ProvenanceEnvelope envelope = fixture.quorum(duel, ChainFixture.validators(5));

            String json = ProvenanceJson.writeEnvelope(envelope);

            assertThat(json).contains("\"signatures\":[{\"alg\":\"EdDSA\"");
            assertThat(json).doesNotContain("\"signature\":");
            assertThat(ProvenanceJson.readEnvelope(json).signatures()).hasSize(5);
        }

        @Test
        @DisplayName("round-trips both shapes losslessly")
        void roundTripsBothShapes() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope single = fixture.singleIssuer(genesis);
            ProvenancePayload duel = ChainFixture.Edit.of(
                            fixture.following(genesis, ProvenanceEventType.DUEL_GRANT, ChainFixture.OTHER_HOLDER, "x"))
                    .issuerDid("duel:" + ChainFixture.DUEL_ID)
                    .build();
            ProvenanceEnvelope quorum = fixture.quorum(duel, ChainFixture.validators(3));

            assertThat(ProvenanceJson.readEnvelope(ProvenanceJson.writeEnvelope(single)))
                    .isEqualTo(single);
            assertThat(ProvenanceJson.readEnvelope(ProvenanceJson.writeEnvelope(quorum)))
                    .isEqualTo(quorum);
        }

        @Test
        @DisplayName("never drops a signature to fit the singular field")
        void neverDropsASignature() {
            // A trade should not carry two signatures, but if one arrives, encoding it must not
            // quietly discard evidence.
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope odd = new ProvenanceEnvelope(
                    genesis,
                    ProvenanceEnvelope.JCS_RFC8785,
                    List.of(
                            fixture.sign(genesis, ChainFixture.HOME_SERVER),
                            fixture.sign(genesis, ChainFixture.ROGUE_SERVER)));

            assertThat(ProvenanceJson.readEnvelope(ProvenanceJson.writeEnvelope(odd)))
                    .isEqualTo(odd);
        }

        @Test
        @DisplayName("reads §3.1 array elements that omit 'alg' as EdDSA")
        void defaultsTheAlgorithmOnRead() {
            // The doc's own §3.1 example omits alg. Defaulting is safe because the verifier still
            // checks the value, and EdDSA is the only one it accepts.
            ProvenancePayload genesis = fixture.genesis();
            String sig = fixture.sign(genesis, ChainFixture.HOME_SERVER).sig();
            String json = "{\"payload\":" + ProvenanceJson.writePayload(genesis)
                    + ",\"payloadCanonicalization\":\"JCS-RFC8785\""
                    + ",\"signatures\":[{\"kid\":\"" + ChainFixture.kidOf(ChainFixture.HOME_SERVER)
                    + "\",\"sig\":\"" + sig + "\"}]}";

            ProvenanceEnvelope envelope = ProvenanceJson.readEnvelope(json);

            assertThat(envelope.signatures()).singleElement().satisfies(block -> {
                assertThat(block.alg()).isEqualTo("EdDSA");
                assertThat(block.signerDid()).isEqualTo(ChainFixture.HOME_SERVER);
            });
        }

        @Test
        @DisplayName("refuses an envelope carrying both 'signature' and 'signatures'")
        void refusesBothSignatureFields() {
            // Two readings of one document is how a signature-stripping trick gets in.
            ProvenancePayload genesis = fixture.genesis();
            String block =
                    "{\"alg\":\"EdDSA\",\"kid\":\"" + ChainFixture.kidOf(ChainFixture.HOME_SERVER) + "\",\"sig\":\""
                            + fixture.sign(genesis, ChainFixture.HOME_SERVER).sig() + "\"}";
            String json = "{\"payload\":" + ProvenanceJson.writePayload(genesis)
                    + ",\"payloadCanonicalization\":\"JCS-RFC8785\""
                    + ",\"signature\":" + block
                    + ",\"signatures\":[" + block + "]}";

            assertThatThrownBy(() -> ProvenanceJson.readEnvelope(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never both");
        }

        @Test
        @DisplayName("refuses an envelope carrying no signature at all")
        void refusesAnUnsignedEnvelope() {
            String json = "{\"payload\":" + ProvenanceJson.writePayload(fixture.genesis())
                    + ",\"payloadCanonicalization\":\"JCS-RFC8785\"}";

            assertThatThrownBy(() -> ProvenanceJson.readEnvelope(json)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a signature block that is not an object")
        void refusesMalformedSignatureBlocks() {
            String json = "{\"payload\":" + ProvenanceJson.writePayload(fixture.genesis())
                    + ",\"payloadCanonicalization\":\"JCS-RFC8785\",\"signature\":\"just-a-string\"}";

            assertThatThrownBy(() -> ProvenanceJson.readEnvelope(json)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a decoded envelope still verifies against the original signature")
        void decodedEnvelopesStillVerify() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenanceEnvelope envelope = fixture.singleIssuer(genesis);

            ProvenanceEnvelope decoded = ProvenanceJson.readEnvelope(ProvenanceJson.writeEnvelope(envelope));

            byte[] signature = Base64.getUrlDecoder()
                    .decode(decoded.signatures().getFirst().sig());
            assertThat(Ed25519Signatures.verify(
                            fixture.keysOf(ChainFixture.HOME_SERVER).getPublic(),
                            ProvenanceJson.canonicalBytes(decoded.payload()),
                            signature))
                    .as("a record that survives a wire round trip must still verify, or federation breaks")
                    .isTrue();
        }
    }
}
