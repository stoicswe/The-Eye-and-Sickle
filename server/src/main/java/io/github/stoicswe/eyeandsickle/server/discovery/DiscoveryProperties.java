package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operational knobs for peer discovery — bounds, intervals, and seed peers.
 *
 * <h2>Why these live in one bound properties class</h2>
 *
 * {@code CLAUDE.md} asks that any tuning value needed by a slice live in one clearly-named properties
 * class in that slice's own package, rather than scattered as constants. None of these are economy
 * numbers ({@code docs/design/03-economy.md}); they are anti-abuse and liveness knobs a self-hoster
 * may reasonably tune. Keeping them together is what lets an operator reason about the discovery
 * layer's resource footprint in one place, and what lets a hostile-federation test dial every bound
 * down to a stress value.
 *
 * <h2>Every bound here exists because the input is untrusted</h2>
 *
 * A peer list, a descriptor, and a probe response all arrive from a server this one does not control
 * ({@code docs/architecture/03-server-and-federation.md} §1). An unbounded any of them is a
 * denial-of-service vector: a peer that returns a million descriptors, a descriptor that is a
 * gigabyte of JSON, a directory that grows without limit from gossip. Each field below caps one such
 * vector; see {@code docs/architecture/08-discovery-and-sync.md} §5.
 *
 * @param seeds endpoint URLs of servers to bootstrap from, before any gossip has happened. Empty is
 *     legitimate — a private/friends server never federates, and a first federating server has no one
 *     to seed from until someone seeds from it.
 * @param maxDirectorySize the largest number of peers this server will retain. A hard cap on how far
 *     gossip can grow local storage; new peers beyond it are refused, existing ones still update.
 * @param maxPeersPerExchange the most descriptors accepted from, or served in, a single peer-exchange.
 *     Anti-amplification: bounds one round's work regardless of how many a peer offers.
 * @param gossipFanout how many peers to pull from in one gossip round. Small on purpose — discovery is
 *     eventually-consistent and does not need to fan out widely.
 * @param maxDescriptorBytes the largest self-descriptor this server will parse, in UTF-8 bytes. A
 *     descriptor has no natural length, so it needs an imposed one.
 * @param gossipInterval how often a gossip round runs.
 * @param probeInterval how often a liveness round runs.
 * @param backoffBase the first back-off delay after a peer's first failed contact; doubles per
 *     consecutive failure up to {@link #backoffCap}.
 * @param backoffCap the ceiling on back-off delay, so a long-dead peer is still retried occasionally
 *     rather than never.
 * @param clockSkewTolerance how far a descriptor's transport-key {@code notBefore} may sit in the
 *     future and still be accepted, absorbing honest clock disagreement between self-hosted servers.
 */
@ConfigurationProperties(prefix = "eyeandsickle.discovery")
public record DiscoveryProperties(
        List<String> seeds,
        Integer maxDirectorySize,
        Integer maxPeersPerExchange,
        Integer gossipFanout,
        Integer maxDescriptorBytes,
        Duration gossipInterval,
        Duration probeInterval,
        Duration backoffBase,
        Duration backoffCap,
        Duration clockSkewTolerance) {

    /** Default directory ceiling. A home server's federation is allowlist-bounded and small ({@code 06} §3). */
    public static final int DEFAULT_MAX_DIRECTORY_SIZE = 512;

    /** Default per-exchange descriptor cap. */
    public static final int DEFAULT_MAX_PEERS_PER_EXCHANGE = 64;

    /** Default gossip fan-out. */
    public static final int DEFAULT_GOSSIP_FANOUT = 4;

    /**
     * Default descriptor-size ceiling, matching {@code Jsonb.MAX_BYTES} (1 MiB). A descriptor is far
     * smaller than this in practice; the cap only has to stop an abusive one.
     */
    public static final int DEFAULT_MAX_DESCRIPTOR_BYTES = 1 << 20;

    private static final Duration DEFAULT_GOSSIP_INTERVAL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_BACKOFF_BASE = Duration.ofSeconds(30);
    private static final Duration DEFAULT_BACKOFF_CAP = Duration.ofHours(6);
    private static final Duration DEFAULT_CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(5);

    public DiscoveryProperties {
        // Null-coalesce every field so an operator can set one knob without re-declaring the rest, and
        // so a purely local server (which sets none of this) still gets a coherent config.
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        maxDirectorySize = orDefault(maxDirectorySize, DEFAULT_MAX_DIRECTORY_SIZE);
        maxPeersPerExchange = orDefault(maxPeersPerExchange, DEFAULT_MAX_PEERS_PER_EXCHANGE);
        gossipFanout = orDefault(gossipFanout, DEFAULT_GOSSIP_FANOUT);
        maxDescriptorBytes = orDefault(maxDescriptorBytes, DEFAULT_MAX_DESCRIPTOR_BYTES);
        gossipInterval = gossipInterval == null ? DEFAULT_GOSSIP_INTERVAL : gossipInterval;
        probeInterval = probeInterval == null ? DEFAULT_PROBE_INTERVAL : probeInterval;
        backoffBase = backoffBase == null ? DEFAULT_BACKOFF_BASE : backoffBase;
        backoffCap = backoffCap == null ? DEFAULT_BACKOFF_CAP : backoffCap;
        clockSkewTolerance = clockSkewTolerance == null ? DEFAULT_CLOCK_SKEW_TOLERANCE : clockSkewTolerance;

        requirePositive("max-directory-size", maxDirectorySize);
        requirePositive("max-peers-per-exchange", maxPeersPerExchange);
        requirePositive("gossip-fanout", gossipFanout);
        requirePositive("max-descriptor-bytes", maxDescriptorBytes);
        requirePositive("gossip-interval", gossipInterval);
        requirePositive("probe-interval", probeInterval);
        requirePositive("backoff-base", backoffBase);
        requirePositive("backoff-cap", backoffCap);
        if (backoffCap.compareTo(backoffBase) < 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.discovery.backoff-cap (" + backoffCap + ") must be >= backoff-base (" + backoffBase
                            + "); a cap below the base would make the first retry wait longer than every later one");
        }
        if (clockSkewTolerance.isNegative()) {
            throw new IllegalArgumentException(
                    "eyeandsickle.discovery.clock-skew-tolerance must not be negative, was " + clockSkewTolerance);
        }
    }

    private static Integer orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("eyeandsickle.discovery." + name + " must be positive, was " + value);
        }
    }

    private static void requirePositive(String name, Duration value) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("eyeandsickle.discovery." + name + " must be positive, was " + value);
        }
    }
}
