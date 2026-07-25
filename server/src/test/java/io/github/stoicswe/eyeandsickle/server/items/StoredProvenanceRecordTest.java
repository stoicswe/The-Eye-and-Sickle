package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.RecordHash;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link StoredProvenanceRecord} — the row projection of a signed envelope. Its contract is that the
 * envelope is authoritative and everything else is a projection of the <em>same single parse</em>: the
 * {@code recordHash} is the digest of the canonical payload, and the stored envelope is kept verbatim
 * so it round-trips to exactly what was signed ({@code docs/architecture/04-item-provenance.md} §6.2).
 */
class StoredProvenanceRecordTest {

    private final TestChains chains = new TestChains();

    private ProvenanceEnvelope genesisEnvelope() {
        return chains.singleIssuer(chains.genesis());
    }

    // ------------------------------------------------------------------ projections

    @Nested
    @DisplayName("projecting an envelope into a row")
    class Projection {

        private final ProvenanceEnvelope envelope = genesisEnvelope();
        private final String verbatim = ProvenanceJson.writeEnvelope(envelope);
        private final UUID recordId = UUID.randomUUID();
        private final StoredProvenanceRecord record =
                StoredProvenanceRecord.from(recordId, envelope, verbatim, TestChains.NOW);

        @Test
        @DisplayName("copies the identifying fields straight from the payload")
        void copiesPayloadFields() {
            ProvenancePayload payload = envelope.payload();
            assertThat(record.recordId()).isEqualTo(recordId);
            assertThat(record.itemId()).isEqualTo(payload.itemId());
            assertThat(record.chainDepth()).isEqualTo(0);
            assertThat(record.prevRecordHash()).isNull();
            assertThat(record.eventType()).isEqualTo(ProvenanceEventType.INITIAL_MINT);
            assertThat(record.holderDid()).isEqualTo(payload.holderDid());
            assertThat(record.issuerDid()).isEqualTo(payload.issuerDid());
            assertThat(record.recordVersion()).isEqualTo(payload.recordVersion());
            assertThat(record.payloadTimestamp()).isEqualTo(payload.timestamp());
            assertThat(record.recordedAt()).isEqualTo(TestChains.NOW);
        }

        @Test
        @DisplayName("the record hash is the SHA-256 of the canonical payload bytes")
        void recordHashIsOverCanonicalPayload() {
            assertThat(record.recordHash()).isEqualTo(RecordHash.of(envelope.payload()));
        }

        @Test
        @DisplayName("keeps the envelope verbatim and derives the payload projection")
        void keepsEnvelopeVerbatim() {
            assertThat(record.envelopeJson()).isEqualTo(verbatim);
            assertThat(record.payloadJson()).isEqualTo(ProvenanceJson.writePayload(envelope.payload()));
        }

        @Test
        @DisplayName("writes signatures as a JSON array even for a single-issuer record")
        void signaturesAreAlwaysArrayShaped() {
            // The column is a query projection, not a signature input, so the uniform array shape is
            // lossless — even though a single-issuer envelope writes the singular "signature" object.
            List<Object> blocks = Jsonb.readArray(record.signaturesJson());
            assertThat(blocks).hasSize(1);
            assertThat(blocks.getFirst()).isInstanceOf(Map.class);
            Map<?, ?> block = (Map<?, ?>) blocks.getFirst();
            assertThat(block.get("alg")).isEqualTo(Ed25519Signatures.JOSE_ALG);
            assertThat(block.get("kid")).isEqualTo(TestChains.kidOf(TestChains.HOME_DID));
        }
    }

    // ------------------------------------------------------------------ round-trip

    @Test
    @DisplayName("the stored envelope round-trips and its signature still verifies")
    void envelopeRoundTripsAndVerifies() {
        ProvenanceEnvelope envelope = genesisEnvelope();
        StoredProvenanceRecord record = StoredProvenanceRecord.from(
                UUID.randomUUID(), envelope, ProvenanceJson.writeEnvelope(envelope), TestChains.NOW);

        ProvenanceEnvelope reparsed = record.toEnvelope();

        assertThat(reparsed.payload()).isEqualTo(envelope.payload());
        assertThat(reparsed.signatures()).isEqualTo(envelope.signatures());
        // The whole point of storing verbatim: the signature still covers the canonical bytes.
        assertThat(Ed25519Signatures.verify(
                        chains.publicKeyOf(TestChains.HOME_DID),
                        ProvenanceJson.canonicalBytes(reparsed.payload()),
                        Base64.getUrlDecoder()
                                .decode(reparsed.signatures().getFirst().sig())))
                .isTrue();
    }

    @Test
    @DisplayName("a non-genesis record projects its predecessor hash")
    void nonGenesisCarriesPrevHash() {
        ProvenancePayload genesis = chains.genesis();
        ProvenancePayload second =
                chains.following(genesis, ProvenanceEventType.TRADE, TestChains.OTHER_HOLDER, TestChains.HOME_DID);
        ProvenanceEnvelope envelope = chains.singleIssuer(second);

        StoredProvenanceRecord record = StoredProvenanceRecord.from(
                UUID.randomUUID(), envelope, ProvenanceJson.writeEnvelope(envelope), TestChains.NOW);

        assertThat(record.chainDepth()).isEqualTo(1);
        assertThat(record.prevRecordHash()).isEqualTo(RecordHash.of(genesis));
    }

    // ------------------------------------------------------------------ null guards

    @Nested
    @DisplayName("null arguments are rejected")
    class NullGuards {

        private final ProvenanceEnvelope envelope = genesisEnvelope();
        private final String verbatim = ProvenanceJson.writeEnvelope(envelope);

        @Test
        @DisplayName("from() requires the envelope and the verbatim JSON")
        void fromRequiresEnvelopeAndJson() {
            assertThatThrownBy(() -> StoredProvenanceRecord.from(UUID.randomUUID(), null, verbatim, TestChains.NOW))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> StoredProvenanceRecord.from(UUID.randomUUID(), envelope, null, TestChains.NOW))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the row requires a record id and a recorded-at instant")
        void rowRequiresIdAndInstant() {
            assertThatThrownBy(() -> StoredProvenanceRecord.from(null, envelope, verbatim, TestChains.NOW))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> StoredProvenanceRecord.from(UUID.randomUUID(), envelope, verbatim, (Instant) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
