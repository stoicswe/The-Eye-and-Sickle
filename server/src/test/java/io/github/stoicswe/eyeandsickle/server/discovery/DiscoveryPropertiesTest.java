package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DiscoveryProperties} — the anti-abuse and liveness knobs.
 *
 * <p>Every bound here exists because a peer list, a descriptor, and a probe response all arrive from a
 * server this one does not control. An unbounded any of them is a denial-of-service vector, so the
 * tests confirm each field defaults to a sane value, rejects a non-positive one, and that the one
 * cross-field rule (cap must not sit below base) is enforced at construction rather than surfacing later
 * as a nonsensical schedule.
 */
class DiscoveryPropertiesTest {

    private static DiscoveryProperties allDefaults() {
        return new DiscoveryProperties(null, null, null, null, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("a purely-local server that sets nothing still gets a coherent config")
        void nullsCoalesceToDefaults() {
            DiscoveryProperties properties = allDefaults();

            assertThat(properties.seeds()).isEmpty();
            assertThat(properties.maxDirectorySize()).isEqualTo(DiscoveryProperties.DEFAULT_MAX_DIRECTORY_SIZE);
            assertThat(properties.maxPeersPerExchange()).isEqualTo(DiscoveryProperties.DEFAULT_MAX_PEERS_PER_EXCHANGE);
            assertThat(properties.gossipFanout()).isEqualTo(DiscoveryProperties.DEFAULT_GOSSIP_FANOUT);
            assertThat(properties.maxDescriptorBytes()).isEqualTo(DiscoveryProperties.DEFAULT_MAX_DESCRIPTOR_BYTES);
            assertThat(properties.gossipInterval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.probeInterval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.backoffBase()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.backoffCap()).isEqualTo(Duration.ofHours(6));
            assertThat(properties.clockSkewTolerance()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("one knob can be set without re-declaring the rest")
        void oneKnobLeavesOthersDefault() {
            DiscoveryProperties properties =
                    new DiscoveryProperties(null, 99, null, null, null, null, null, null, null, null);

            assertThat(properties.maxDirectorySize()).isEqualTo(99);
            assertThat(properties.gossipFanout()).isEqualTo(DiscoveryProperties.DEFAULT_GOSSIP_FANOUT);
        }

        @Test
        @DisplayName("seeds are copied defensively, so a later mutation of the source list does not leak in")
        void seedsAreCopied() {
            List<String> source = new ArrayList<>(List.of("https://seed.example.test"));
            DiscoveryProperties properties =
                    new DiscoveryProperties(source, null, null, null, null, null, null, null, null, null);

            source.add("https://injected.example.evil");

            assertThat(properties.seeds()).containsExactly("https://seed.example.test");
            assertThatThrownBy(() -> properties.seeds().add("x")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("validation — every bound must be positive")
    class Validation {

        @Test
        @DisplayName("a non-positive integer bound is refused")
        void nonPositiveIntegersRefused() {
            assertRejected(new IntBounds(0, null, null, null), "max-directory-size");
            assertRejected(new IntBounds(null, 0, null, null), "max-peers-per-exchange");
            assertRejected(new IntBounds(null, null, 0, null), "gossip-fanout");
            assertRejected(new IntBounds(null, null, null, 0), "max-descriptor-bytes");
            assertRejected(new IntBounds(-1, null, null, null), "max-directory-size");
        }

        @Test
        @DisplayName("a zero or negative duration is refused")
        void nonPositiveDurationsRefused() {
            assertThatThrownBy(() -> withDurations(Duration.ZERO, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gossip-interval");
            assertThatThrownBy(() -> withDurations(null, Duration.ofSeconds(-1), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("probe-interval");
            assertThatThrownBy(() -> withDurations(null, null, Duration.ZERO, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backoff-base");
        }

        @Test
        @DisplayName("a backoff cap below the base is refused")
        void capBelowBaseRefused() {
            assertThatThrownBy(() -> withDurations(null, null, Duration.ofHours(1), Duration.ofMinutes(1), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backoff-cap");
        }

        @Test
        @DisplayName("a negative clock-skew tolerance is refused (a positive zero is fine)")
        void negativeClockSkewRefused() {
            assertThatThrownBy(() -> withDurations(null, null, null, null, Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("clock-skew-tolerance");

            // Zero skew is a legitimate strict setting, not an error.
            assertThat(withDurations(null, null, null, null, Duration.ZERO).clockSkewTolerance())
                    .isEqualTo(Duration.ZERO);
        }
    }

    // ------------------------------------------------------------------ helpers

    private record IntBounds(Integer maxDir, Integer maxPeers, Integer fanout, Integer maxBytes) {}

    private static void assertRejected(IntBounds bounds, String expectedName) {
        assertThatThrownBy(() -> new DiscoveryProperties(
                        null,
                        bounds.maxDir(),
                        bounds.maxPeers(),
                        bounds.fanout(),
                        bounds.maxBytes(),
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedName);
    }

    private static DiscoveryProperties withDurations(
            Duration gossip, Duration probe, Duration backoffBase, Duration backoffCap, Duration clockSkew) {
        return new DiscoveryProperties(null, null, null, null, null, gossip, probe, backoffBase, backoffCap, clockSkew);
    }
}
