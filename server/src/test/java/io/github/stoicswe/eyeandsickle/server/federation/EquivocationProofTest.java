package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The self-contained equivocation proof — {@code docs/architecture/05-validator-quorum.md} §3.3. The
 * evidence it renders into {@code flagged_servers.evidence} must let any peer re-run the two
 * verifications for itself, which means carrying each conflicting outcome's exact canonical bytes plus
 * the signature block that covers them.
 */
class EquivocationProofTest {

    private static final String DUEL = "duel-1";
    private static final String VALIDATOR = "did:plc:validator1";

    private final FederationFixture fx = new FederationFixture();

    private EquivocationProof proof() {
        ValidatorSignature first = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
        ValidatorSignature second = fx.vote(DUEL, FederationFixture.HOLDER_B, VALIDATOR);
        return new EquivocationProof(VALIDATOR, DUEL, first, second);
    }

    @Nested
    @DisplayName("the evidence document")
    class Evidence {

        @Test
        @DisplayName("names the kind, subject validator and duel")
        void topLevelFields() {
            Map<String, Object> evidence = proof().evidence();
            assertThat(evidence.get("kind")).isEqualTo("equivocation");
            assertThat(evidence.get("validatorDid")).isEqualTo(VALIDATOR);
            assertThat(evidence.get("duelId")).isEqualTo(DUEL);
        }

        @Test
        @DisplayName("carries both conflicting outcomes as canonical JSON plus their signature blocks")
        void conflictingOutcomes() {
            EquivocationProof proof = proof();
            Map<String, Object> evidence = proof.evidence();

            Object conflicting = evidence.get("conflictingOutcomes");
            assertThat(conflicting).isInstanceOf(List.class);
            List<?> outcomes = (List<?>) conflicting;
            assertThat(outcomes).hasSize(2);

            Map<?, ?> firstEntry = (Map<?, ?>) outcomes.getFirst();
            // The canonical form is stored verbatim — exactly the bytes the signature covers — so a
            // verifier re-canonicalizes nothing and cannot be tricked by a re-encoding.
            assertThat(firstEntry.get("outcomeCanonical"))
                    .isEqualTo(ProvenanceJson.canonicalJson(proof.first().outcome()));

            Map<?, ?> firstSig = (Map<?, ?>) firstEntry.get("signature");
            assertThat(firstSig.get("alg")).isEqualTo(proof.first().signature().alg());
            assertThat(firstSig.get("kid")).isEqualTo(proof.first().signature().kid());
            assertThat(firstSig.get("sig")).isEqualTo(proof.first().signature().sig());
        }
    }

    @Test
    @DisplayName("rejects null constructor arguments")
    void rejectsNulls() {
        ValidatorSignature a = fx.vote(DUEL, FederationFixture.HOLDER_A, VALIDATOR);
        ValidatorSignature b = fx.vote(DUEL, FederationFixture.HOLDER_B, VALIDATOR);
        assertThatThrownBy(() -> new EquivocationProof(null, DUEL, a, b)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquivocationProof(VALIDATOR, null, a, b)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquivocationProof(VALIDATOR, DUEL, null, b))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EquivocationProof(VALIDATOR, DUEL, a, null))
                .isInstanceOf(NullPointerException.class);
    }
}
