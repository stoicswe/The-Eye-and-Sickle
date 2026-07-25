package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Exponential back-off for contacting an unreliable peer.
 *
 * <h2>Why back-off is not optional here</h2>
 *
 * Every peer is untrusted and may be unreachable, flapping, or hostile ({@code
 * docs/architecture/03-server-and-federation.md} §1). Probing a dead or abusive peer on every round
 * spends this server's connections and time on a peer that is not answering — a self-inflicted denial
 * of service. Back-off spaces retries out geometrically with the consecutive-failure count, so a
 * briefly-flapping peer is retried soon while a long-dead one is retried rarely, and neither is
 * abandoned: the delay is capped so even a peer that has failed a hundred times is still tried again
 * eventually, in case it comes back.
 *
 * <p>Pure and clock-injected — no wall clock, no I/O — so the schedule is exactly reproducible in a
 * test. The delay after {@code n} consecutive failures is {@code base * 2^(n-1)}, capped at
 * {@link #cap}; zero failures means no delay (a healthy peer is always eligible).
 *
 * @param base the delay after the first failure
 * @param cap the ceiling on the delay
 */
public record PeerBackoff(Duration base, Duration cap) {

    public PeerBackoff {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(cap, "cap");
        if (base.isZero() || base.isNegative()) {
            throw new IllegalArgumentException("base must be positive, was " + base);
        }
        if (cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("cap (" + cap + ") must be >= base (" + base + ")");
        }
    }

    /**
     * @param properties the discovery configuration
     * @return a back-off using its configured base and cap
     */
    public static PeerBackoff from(DiscoveryProperties properties) {
        return new PeerBackoff(properties.backoffBase(), properties.backoffCap());
    }

    /**
     * The delay required before the next attempt, given how many times contact has failed in a row.
     *
     * @param consecutiveFailures the failure streak; 0 means the peer is healthy
     * @return the delay, never longer than {@link #cap}
     */
    public Duration delayFor(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return Duration.ZERO;
        }
        // Double from base, stopping as soon as the cap is reached. Doubling in a loop (rather than
        // base * (1 << exp)) cannot overflow: it never produces a value larger than twice the cap.
        Duration delay = base;
        for (int i = 1; i < consecutiveFailures; i++) {
            if (delay.compareTo(cap) >= 0) {
                return cap;
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(cap) > 0 ? cap : delay;
    }

    /**
     * The earliest instant a peer becomes eligible for another attempt.
     *
     * @param consecutiveFailures the failure streak
     * @param lastAttemptAt when the peer was last tried (or last observed); {@code null} means never,
     *     which is immediately eligible
     * @return the next eligible instant
     */
    public Instant nextEligibleAt(int consecutiveFailures, Instant lastAttemptAt) {
        if (lastAttemptAt == null) {
            return Instant.MIN;
        }
        return lastAttemptAt.plus(delayFor(consecutiveFailures));
    }

    /**
     * Whether a peer may be contacted now.
     *
     * @param consecutiveFailures the failure streak
     * @param lastAttemptAt when the peer was last tried (or last observed); {@code null} means never
     * @param now the current instant
     * @return whether enough time has elapsed since the last attempt
     */
    public boolean isDue(int consecutiveFailures, Instant lastAttemptAt, Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(nextEligibleAt(consecutiveFailures, lastAttemptAt));
    }
}
