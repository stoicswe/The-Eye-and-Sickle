package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the liveness measurement that discovery hands the validator quorum:
 * {@link PeerRecord#successRatio(double)}, {@link PeerLiveness}, and {@link RepositoryPeerUptimeSource}.
 *
 * <p>This is the payoff of the whole probing apparatus — the {@code uptime} term the quorum weights
 * sampling by ({@code docs/architecture/05-validator-quorum.md} §2.2). The measurement, not a finished
 * score, is what crosses the slice boundary, and a peer with no contact history must report an honest
 * "unknown" (a neutral midpoint) rather than a 0 that buries it or a 1 that flatters it.
 */
class PeerLivenessAndUptimeTest {

    private static final String DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final Instant CONTACT = Instant.parse("2026-07-24T00:00:00Z");

    private static PeerRecord record(long successes, long failures, int consecutive, Instant lastSuccess) {
        return record(successes, failures, consecutive, lastSuccess, "{\"descriptor\":{}}");
    }

    private static PeerRecord record(
            long successes, long failures, int consecutive, Instant lastSuccess, String selfDescriptor) {
        return new PeerRecord(
                UUID.randomUUID(),
                DID,
                "https://home.example.org",
                new byte[44],
                null,
                null,
                null,
                selfDescriptor,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                lastSuccess,
                successes,
                failures,
                consecutive,
                0);
    }

    @Nested
    @DisplayName("successRatio")
    class SuccessRatio {

        @Test
        @DisplayName("a peer with no contact history reports the caller's 'no data' value, not 0 or 1")
        void noDataUsesDefault() {
            // Inventing 0 would bury a freshly-announced peer; inventing 1 would flatter one that never
            // answered. Neither is honest about "unknown".
            assertThat(record(0, 0, 0, null).successRatio(0.5)).isEqualTo(0.5);
            assertThat(record(0, 0, 0, null).successRatio(0.0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("the ratio is successes / (successes + failures)")
        void ratioIsComputed() {
            assertThat(record(3, 1, 0, CONTACT).successRatio(0.5)).isCloseTo(0.75, within(1e-9));
            assertThat(record(1, 0, 0, CONTACT).successRatio(0.5)).isEqualTo(1.0);
            assertThat(record(0, 4, 4, null).successRatio(0.5)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("declaredCapabilities reads the stored descriptor without re-verifying")
    class DeclaredCapabilities {

        @Test
        @DisplayName("returns the capabilities from a well-formed stored descriptor")
        void readsCapabilities() {
            String descriptor = new DescriptorFixture().signed(1, List.of("federation", "validator"), null);
            assertThat(record(1, 0, 0, CONTACT, descriptor).declaredCapabilities())
                    .containsExactly("federation", "validator");
        }

        @Test
        @DisplayName("a malformed stored descriptor yields an empty list, never an exception")
        void malformedDescriptorIsEmpty() {
            // A display path must not blow up on one odd row.
            assertThat(record(1, 0, 0, CONTACT, "{ not json").declaredCapabilities())
                    .isEmpty();
        }

        @Test
        @DisplayName("a descriptor with no capabilities field yields an empty list")
        void noCapabilitiesFieldIsEmpty() {
            assertThat(record(1, 0, 0, CONTACT, "{\"descriptor\":{}}").declaredCapabilities())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("PeerLiveness.of projects the measurement")
    class Projection {

        @Test
        @DisplayName("carries the raw availability numbers, not a finished score")
        void projectsFields() {
            PeerRecord record = record(3, 1, 0, CONTACT);
            PeerLiveness liveness = PeerLiveness.of(record, 0.5);

            assertThat(liveness.peerDid()).isEqualTo(DID);
            assertThat(liveness.successes()).isEqualTo(3);
            assertThat(liveness.failures()).isEqualTo(1);
            assertThat(liveness.consecutiveFailures()).isZero();
            assertThat(liveness.lastSuccessfulContactAt()).isEqualTo(CONTACT);
            assertThat(liveness.successRatio()).isCloseTo(0.75, within(1e-9));
        }

        @Test
        @DisplayName("a peer with no data projects the no-data ratio")
        void projectsNoData() {
            assertThat(PeerLiveness.of(record(0, 0, 0, null), 0.5).successRatio())
                    .isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("RepositoryPeerUptimeSource exposes reachability to the quorum")
    class UptimeSource {

        private final FakeFederationPeerRepository repository = new FakeFederationPeerRepository();
        private final RepositoryPeerUptimeSource source = new RepositoryPeerUptimeSource(repository);

        @Test
        @DisplayName("a known peer's reachability signal is exposed")
        void knownPeerHasLiveness() {
            repository.seed(DID, 8, 2, 0, CONTACT);

            Optional<PeerLiveness> liveness = source.livenessOf(DID);

            assertThat(liveness).isPresent();
            assertThat(liveness.get().successRatio()).isCloseTo(0.8, within(1e-9));
            assertThat(liveness.get().successes()).isEqualTo(8);
            assertThat(liveness.get().failures()).isEqualTo(2);
        }

        @Test
        @DisplayName("a peer with no contact history reports the neutral midpoint, not 0 or 1")
        void noDataPeerIsNeutral() {
            repository.seed(DID, 0, 0, 0, null);

            assertThat(source.livenessOf(DID).orElseThrow().successRatio())
                    .isEqualTo(RepositoryPeerUptimeSource.RATIO_WHEN_NO_DATA)
                    .isEqualTo(0.5);
        }

        @Test
        @DisplayName("an unknown peer has no measurement — empty, not a fabricated zero")
        void unknownPeerIsEmpty() {
            assertThat(source.livenessOf("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb")).isEmpty();
        }
    }
}
