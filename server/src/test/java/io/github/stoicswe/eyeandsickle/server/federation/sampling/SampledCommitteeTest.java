package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The frozen per-duel sampling record — the storable evidence a peer re-verifies a committee from
 * ({@code docs/architecture/04-item-provenance.md} §7 step 1). Its projections to the protocol {@link
 * QuorumCommittee} and to the {@code duels.sampled_validators} jsonb array must both derive the weight
 * from one source.
 */
class SampledCommitteeTest {

    private static final String DUEL_ID = "0a9f-4c2e";

    private static SampledValidator v(String suffix, double reputation, double uptime) {
        return SampledValidator.of("did:plc:validator" + suffix, reputation, uptime);
    }

    @Test
    @DisplayName("size reports how many validators were drawn")
    void size() {
        SampledCommittee committee =
                new SampledCommittee(DUEL_ID, List.of(v("1", 0.8, 1.0), v("2", 0.5, 1.0), v("3", 0.3, 1.0)));
        assertThat(committee.size()).isEqualTo(3);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("refuses an empty committee, which could have adjudicated nothing")
        void refusesEmpty() {
            assertThatThrownBy(() -> new SampledCommittee(DUEL_ID, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses null duel id or null members")
        void refusesNulls() {
            assertThatThrownBy(() -> new SampledCommittee(null, List.of(v("1", 0.5, 1.0))))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new SampledCommittee(DUEL_ID, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("defensively copies its members, so the caller cannot re-shape a frozen draw")
        void defensivelyCopiesMembers() {
            List<SampledValidator> members = new ArrayList<>(List.of(v("1", 0.5, 1.0)));
            SampledCommittee committee = new SampledCommittee(DUEL_ID, members);

            members.add(v("2", 0.9, 1.0));

            // Freezing the committee is the whole point: a later reputation change (or list mutation)
            // must not silently re-adjudicate a duel already under way.
            assertThat(committee.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("projection to the protocol QuorumCommittee")
    class ToQuorumCommittee {

        @Test
        @DisplayName("maps each member DID to its frozen reputation × uptime weight")
        void mapsDidToWeight() {
            SampledCommittee committee =
                    new SampledCommittee(DUEL_ID, List.of(v("1", 0.8, 0.5), v("2", 1.0, 1.0), v("3", 0.4, 0.5)));

            QuorumCommittee projected = committee.toQuorumCommittee();

            assertThat(projected.duelId()).isEqualTo(DUEL_ID);
            assertThat(projected.size()).isEqualTo(3);
            assertThat(projected.weightOf("did:plc:validator1")).isCloseTo(0.40, within(1e-12));
            assertThat(projected.weightOf("did:plc:validator2")).isCloseTo(1.00, within(1e-12));
            assertThat(projected.weightOf("did:plc:validator3")).isCloseTo(0.20, within(1e-12));
            // A QuorumCommittee is a DID-keyed set accessed via weightOf(); its map does not promise an
            // iteration order (the protocol type copies into an unordered map, whose order is randomized
            // per-JVM-run since Java 9). So assert membership, not order — every sampled validator is
            // present exactly once.
            assertThat(projected.sampledWeights().keySet())
                    .containsExactlyInAnyOrder("did:plc:validator1", "did:plc:validator2", "did:plc:validator3");
        }
    }

    @Nested
    @DisplayName("projection to the jsonb array form")
    class ToJsonArray {

        @Test
        @DisplayName("records did, reputation, uptime and the derived weight per member")
        void recordsAllFactors() {
            SampledCommittee committee = new SampledCommittee(DUEL_ID, List.of(v("1", 0.8, 0.5)));

            List<Map<String, Object>> array = committee.toJsonArray();

            assertThat(array).hasSize(1);
            Map<String, Object> entry = array.getFirst();
            assertThat(entry.get("did")).isEqualTo("did:plc:validator1");
            assertThat(entry.get("reputation")).isEqualTo(0.8);
            assertThat(entry.get("uptime")).isEqualTo(0.5);
            // The weight is stored even though it is recomputable, so an auditing peer sees the exact
            // number the committee was judged by rather than trusting a re-multiplication.
            assertThat((double) entry.get("weight")).isCloseTo(0.40, within(1e-12));
        }

        @Test
        @DisplayName("keeps members in the order the sampler returned them")
        void preservesOrder() {
            SampledCommittee committee =
                    new SampledCommittee(DUEL_ID, List.of(v("3", 0.3, 1.0), v("1", 0.9, 1.0), v("2", 0.6, 1.0)));

            assertThat(committee.toJsonArray())
                    .extracting(entry -> entry.get("did"))
                    .containsExactly("did:plc:validator3", "did:plc:validator1", "did:plc:validator2");
        }
    }
}
