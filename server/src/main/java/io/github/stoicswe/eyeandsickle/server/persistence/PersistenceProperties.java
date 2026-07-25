package io.github.stoicswe.eyeandsickle.server.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one place a calibrated number reaches the persistence layer.
 *
 * <h2>Why a properties class and not a constant</h2>
 *
 * {@code CLAUDE.md}'s working agreements: the economy figures in {@code docs/design/03-economy.md}
 * and {@code docs/design/04-mining.md} are calibrated <em>as a set</em>, so changing one means
 * re-checking the tables that depend on it. A number scattered across three constants in three
 * packages cannot be re-checked; a number in one bound properties class can, and can also be tuned by
 * a self-hoster without a rebuild.
 *
 * <p>This class is deliberately tiny, and it should stay that way. It holds only what the
 * <em>schema</em> forces someone to supply. Yields, prices, sweep probabilities and gate thresholds
 * belong to the systems that own them, each with its own properties class — not here, and not in a
 * shared bag of game constants, which is the same scattering problem with a tidier name.
 *
 * <h2>Not a place for anything a client could gain from</h2>
 *
 * Everything here is server-side (Invariant I14). None of it is sent to a client as a rule the client
 * may apply; a client is told outcomes, never the arithmetic that produced them.
 *
 * @param yieldBufferCapHours hours of a deployed miner's yield that its on-host buffer holds before
 *     the miner runs and produces nothing ({@code docs/design/04-mining.md} §2.3). The schema makes
 *     {@code deployed_miners.buffer_cap_hours} NOT NULL with no database default precisely so this
 *     value has to come from configuration rather than being frozen into a migration.
 *     <p><strong>This is open question OQ-4</strong> ({@code docs/design/15-open-questions.md} §1):
 *     4 hours is a starting figure, to be resolved against session-length telemetry. It lives here
 *     only because the persistence layer is currently the first code that needs it — when the mining
 *     system lands with its own configuration, move it there and delete it from this class rather
 *     than leaving two homes for one number.
 */
@ConfigurationProperties(prefix = "eyeandsickle.persistence")
public record PersistenceProperties(Integer yieldBufferCapHours) {

    /** {@code docs/design/04-mining.md} §2.3; OQ-4 says this is a starting figure, not a decision. */
    public static final int DEFAULT_YIELD_BUFFER_CAP_HOURS = 4;

    public PersistenceProperties {
        // Boxed parameter with a null-default rather than a primitive, so "not configured" is
        // distinguishable from "configured to zero" — a zero cap would silently disable offline
        // income (Invariant I5's only source) and look like a game bug rather than a config error.
        yieldBufferCapHours = yieldBufferCapHours == null ? DEFAULT_YIELD_BUFFER_CAP_HOURS : yieldBufferCapHours;
        if (yieldBufferCapHours <= 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.persistence.yield-buffer-cap-hours must be positive, was " + yieldBufferCapHours
                            + "; a non-positive cap removes the only offline income source (Invariant I5)");
        }
    }
}
