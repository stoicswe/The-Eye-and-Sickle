package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.server.federation.AdjudicationResult.AgreedOutcome;
import io.github.stoicswe.eyeandsickle.server.federation.reputation.ValidatorConduct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The pure adjudication result and its agreed-outcome envelope projection. */
class AdjudicationResultTest {

    private final FederationFixture fx = new FederationFixture();

    private ProvenancePayload outcome() {
        return fx.outcome("d1", FederationFixture.HOLDER_A);
    }

    private SignatureBlock sig(String validatorDid) {
        return fx.sign(outcome(), validatorDid);
    }

    @Nested
    @DisplayName("resolved()")
    class Resolved {

        @Test
        @DisplayName("is true exactly when an agreed outcome is present")
        void tracksPresence() {
            AdjudicationResult unresolved = new AdjudicationResult(Optional.empty(), Map.of(), List.of());
            assertThat(unresolved.resolved()).isFalse();

            AgreedOutcome agreed = new AgreedOutcome(outcome(), List.of(sig("did:plc:validator1")));
            AdjudicationResult resolved = new AdjudicationResult(Optional.of(agreed), Map.of(), List.of());
            assertThat(resolved.resolved()).isTrue();
        }
    }

    @Nested
    @DisplayName("AgreedOutcome")
    class Agreed {

        @Test
        @DisplayName("refuses to exist with no signatures — an outcome carries the signatures that agreed to it")
        void refusesEmptySignatures() {
            assertThatThrownBy(() -> new AgreedOutcome(outcome(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("projects to a multi-signature quorum envelope with the same signatures, none re-signed")
        void projectsToQuorumEnvelope() {
            List<SignatureBlock> blocks = List.of(sig("did:plc:validator1"), sig("did:plc:validator2"));
            AgreedOutcome agreed = new AgreedOutcome(outcome(), blocks);

            ProvenanceEnvelope envelope = agreed.toEnvelope();

            assertThat(envelope.payload().eventType()).isEqualTo(ProvenanceEventType.DUEL_GRANT);
            assertThat(envelope.isMultiSignature()).isTrue();
            assertThat(envelope.signatures()).isEqualTo(blocks);
        }

        @Test
        @DisplayName("defensively copies its signature list")
        void copiesSignatures() {
            List<SignatureBlock> blocks = new ArrayList<>(List.of(sig("did:plc:validator1")));
            AgreedOutcome agreed = new AgreedOutcome(outcome(), blocks);

            blocks.add(sig("did:plc:validator2"));

            assertThat(agreed.signatures()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("defensive copies of the maps and lists")
    class Copies {

        @Test
        @DisplayName("copies the conduct map and equivocation list, so a later mutation cannot re-open the result")
        void copiesCollections() {
            Map<String, ValidatorConduct> conduct = new HashMap<>();
            conduct.put("did:plc:validator1", ValidatorConduct.CORRECT);
            List<EquivocationProof> equivocations = new ArrayList<>();

            AdjudicationResult result = new AdjudicationResult(Optional.empty(), conduct, equivocations);
            conduct.put("did:plc:validator2", ValidatorConduct.DIVERGENT);

            assertThat(result.conduct()).hasSize(1).containsOnlyKeys("did:plc:validator1");
        }

        @Test
        @DisplayName("rejects null constructor arguments")
        void rejectsNulls() {
            assertThatThrownBy(() -> new AdjudicationResult(null, Map.of(), List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AdjudicationResult(Optional.empty(), null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new AdjudicationResult(Optional.empty(), Map.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
