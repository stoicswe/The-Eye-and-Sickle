package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PeerBackoff} — pure, clock-injected exponential back-off.
 *
 * <p>Back-off is not optional politeness: probing a dead or abusive peer on every round is a
 * self-inflicted denial of service. The schedule must double geometrically, cap so a long-dead peer is
 * still retried eventually, and never overflow. Because the type is pure and takes {@code now} as a
 * parameter, the whole schedule is asserted against fixed instants.
 */
class PeerBackoffTest {

    private static final Instant T0 = Instant.parse("2026-07-24T00:00:00Z");

    @Nested
    @DisplayName("delayFor")
    class DelayFor {

        private final PeerBackoff backoff = new PeerBackoff(Duration.ofSeconds(30), Duration.ofHours(6));

        @Test
        @DisplayName("zero failures means no delay — a healthy peer is always eligible")
        void zeroFailuresIsZero() {
            assertThat(backoff.delayFor(0)).isEqualTo(Duration.ZERO);
            assertThat(backoff.delayFor(-1)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("the delay doubles from base with each consecutive failure")
        void doublesFromBase() {
            assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofSeconds(30));
            assertThat(backoff.delayFor(2)).isEqualTo(Duration.ofSeconds(60));
            assertThat(backoff.delayFor(3)).isEqualTo(Duration.ofSeconds(120));
            assertThat(backoff.delayFor(4)).isEqualTo(Duration.ofSeconds(240));
        }

        @Test
        @DisplayName("the delay is capped, so even a hundred failures is retried eventually")
        void capsOut() {
            // 30s doubling crosses 6h between the 10th failure (4.27h) and the 11th (pinned at the 6h
            // cap), so any large streak is held at the cap rather than growing without bound.
            assertThat(backoff.delayFor(100)).isEqualTo(Duration.ofHours(6));
            assertThat(backoff.delayFor(Integer.MAX_VALUE))
                    .as("no overflow at the extreme failure count")
                    .isEqualTo(Duration.ofHours(6));
        }

        @Test
        @DisplayName("the schedule is monotonic non-decreasing and never exceeds the cap")
        void monotonicAndBounded() {
            Duration previous = Duration.ZERO;
            for (int failures = 0; failures <= 50; failures++) {
                Duration delay = backoff.delayFor(failures);
                assertThat(delay).as("delayFor(%d) went backwards", failures).isGreaterThanOrEqualTo(previous);
                assertThat(delay)
                        .as("delayFor(%d) exceeded the cap", failures)
                        .isLessThanOrEqualTo(Duration.ofHours(6));
                previous = delay;
            }
        }

        @Test
        @DisplayName("when base equals cap, every non-zero streak is exactly that one value")
        void degenerateBaseEqualsCap() {
            PeerBackoff flat = new PeerBackoff(Duration.ofMinutes(2), Duration.ofMinutes(2));
            assertThat(flat.delayFor(1)).isEqualTo(Duration.ofMinutes(2));
            assertThat(flat.delayFor(9)).isEqualTo(Duration.ofMinutes(2));
        }
    }

    @Nested
    @DisplayName("eligibility")
    class Eligibility {

        private final PeerBackoff backoff = new PeerBackoff(Duration.ofSeconds(30), Duration.ofHours(6));

        @Test
        @DisplayName("a peer never contacted is immediately eligible")
        void neverContactedIsEligible() {
            assertThat(backoff.nextEligibleAt(5, null)).isEqualTo(Instant.MIN);
            assertThat(backoff.isDue(5, null, T0)).isTrue();
        }

        @Test
        @DisplayName("nextEligibleAt is lastAttempt plus the current delay")
        void nextEligibleIsLastPlusDelay() {
            assertThat(backoff.nextEligibleAt(2, T0)).isEqualTo(T0.plus(Duration.ofSeconds(60)));
        }

        @Test
        @DisplayName("isDue is true exactly at the eligible instant, false one tick before it")
        void isDueBoundary() {
            Instant eligible = T0.plus(Duration.ofSeconds(60)); // 2 failures -> 60s
            assertThat(backoff.isDue(2, T0, eligible.minusNanos(1)))
                    .as("still inside the back-off window")
                    .isFalse();
            assertThat(backoff.isDue(2, T0, eligible))
                    .as("the window is closed-open on the left: eligible at exactly the boundary")
                    .isTrue();
            assertThat(backoff.isDue(2, T0, eligible.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("a healthy peer (zero failures) is always due")
        void healthyIsAlwaysDue() {
            assertThat(backoff.isDue(0, T0, T0)).isTrue();
        }

        @Test
        @DisplayName("now is required")
        void nowRequired() {
            assertThatThrownBy(() -> backoff.isDue(1, T0, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("base must be positive")
        void baseMustBePositive() {
            assertThatThrownBy(() -> new PeerBackoff(Duration.ZERO, Duration.ofHours(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("base");
            assertThatThrownBy(() -> new PeerBackoff(Duration.ofSeconds(-1), Duration.ofHours(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cap below base is refused — the first retry would wait longer than every later one")
        void capBelowBaseRefused() {
            assertThatThrownBy(() -> new PeerBackoff(Duration.ofHours(1), Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cap");
        }

        @Test
        @DisplayName("null base or cap is refused")
        void nullsRefused() {
            assertThatThrownBy(() -> new PeerBackoff(null, Duration.ofHours(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new PeerBackoff(Duration.ofSeconds(1), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("from(properties) uses the configured base and cap")
        void fromProperties() {
            DiscoveryProperties properties = new DiscoveryProperties(
                    null, null, null, null, null, null, null, Duration.ofSeconds(15), Duration.ofHours(2), null);
            PeerBackoff backoff = PeerBackoff.from(properties);
            assertThat(backoff.base()).isEqualTo(Duration.ofSeconds(15));
            assertThat(backoff.cap()).isEqualTo(Duration.ofHours(2));
            assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofSeconds(15));
        }
    }
}
