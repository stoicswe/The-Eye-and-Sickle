package io.github.stoicswe.eyeandsickle.protocol.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The {@code 2f+1}-of-{@code 3f+1} arithmetic from {@code docs/architecture/05-validator-quorum.md}
 * §1, checked against the doc's own worked example.
 */
class QuorumCommitteeTest {

    private static QuorumCommittee committee(double... weights) {
        Map<String, Double> sampled = new LinkedHashMap<>();
        for (int i = 0; i < weights.length; i++) {
            sampled.put("did:plc:validator" + (i + 1), weights[i]);
        }
        return new QuorumCommittee("0a9f-4c2e", sampled);
    }

    @Nested
    @DisplayName("the BFT threshold")
    class Threshold {

        @Test
        @DisplayName("is 5 of 7 for the recommended committee, tolerating f = 2")
        void matchesTheDocumentedExample() {
            QuorumCommittee seven = committee(1, 1, 1, 1, 1, 1, 1);

            assertThat(seven.size()).isEqualTo(7);
            assertThat(seven.byzantineTolerance()).isEqualTo(2);
            assertThat(seven.agreeingValidatorsRequired()).isEqualTo(5);
        }

        @Test
        @DisplayName("agrees with 2f+1 at every committee size of the form 3f+1")
        void agreesWithTwoFPlusOneAtExactSizes() {
            for (int f = 0; f <= 6; f++) {
                int n = 3 * f + 1;
                double[] weights = new double[n];
                java.util.Arrays.fill(weights, 1.0);
                QuorumCommittee exact = committee(weights);

                assertThat(exact.byzantineTolerance()).as("f for N=%d", n).isEqualTo(f);
                assertThat(exact.agreeingValidatorsRequired())
                        .as("quorum for N=%d", n)
                        .isEqualTo(2 * f + 1);
            }
        }

        @Test
        @DisplayName("is the stricter floor(2N/3)+1 for sizes that are not 3f+1")
        void generalisesStrictlyForOtherSizes() {
            // For N = 5 the largest tolerated f is 1, so 2f+1 would say 3; the standard BFT quorum
            // says 4. Erring strict is right: recognizing a forged outcome is permanent, refusing a
            // legitimate one is visible and fixable.
            assertThat(committee(1, 1, 1, 1, 1).agreeingValidatorsRequired()).isEqualTo(4);
            assertThat(committee(1, 1, 1).agreeingValidatorsRequired()).isEqualTo(3);
            assertThat(committee(1).agreeingValidatorsRequired()).isEqualTo(1);
        }

        @Test
        @DisplayName("weighted, is five sevenths of the sampled power at N = 7")
        void weightedThresholdIsAFractionOfSampledPower() {
            QuorumCommittee seven = committee(1, 1, 1, 1, 1, 1, 1);
            assertThat(seven.totalWeight()).isEqualTo(7.0);
            assertThat(seven.requiredWeight()).isCloseTo(5.0, within(1e-12));

            // Weight is by reputation, not one-server-one-vote: the same committee with lopsided
            // reputations needs the same fraction of a larger total.
            QuorumCommittee lopsided = committee(10, 1, 1, 1, 1, 1, 1);
            assertThat(lopsided.totalWeight()).isEqualTo(16.0);
            assertThat(lopsided.requiredWeight()).isCloseTo(16.0 * 5 / 7, within(1e-12));
        }
    }

    @Nested
    @DisplayName("the sampling record")
    class SamplingRecord {

        @Test
        @DisplayName("answers membership and weight for sampled validators only")
        void answersMembership() {
            QuorumCommittee seven = committee(1, 2, 3, 4, 5, 6, 7);

            assertThat(seven.wasSampled("did:plc:validator3")).isTrue();
            assertThat(seven.weightOf("did:plc:validator3")).isEqualTo(3.0);
            assertThat(seven.wasSampled("did:plc:validator8")).isFalse();
            assertThat(seven.weightOf("did:plc:validator8")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("refuses a committee that could not have adjudicated anything")
        void refusesDegenerateCommittees() {
            assertThatThrownBy(() -> new QuorumCommittee("0a9f", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QuorumCommittee("0a9f", Map.of("did:plc:v1", -0.5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QuorumCommittee("0a9f", Map.of("did:plc:v1", Double.NaN)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new QuorumCommittee("0a9f", Map.of("did:plc:v1", Double.POSITIVE_INFINITY)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("is defensively copied, so a caller cannot re-weight a duel after the fact")
        void isDefensivelyCopied() {
            Map<String, Double> sampled = new LinkedHashMap<>();
            sampled.put("did:plc:validator1", 1.0);
            QuorumCommittee snapshot = new QuorumCommittee("0a9f", sampled);

            sampled.put("did:plc:validator2", 99.0);

            assertThat(snapshot.size()).isEqualTo(1);
            assertThat(snapshot.wasSampled("did:plc:validator2")).isFalse();
        }
    }
}
