package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Duration;
import java.time.Instant;

/**
 * The standing instruction to audit this rig, as the panel sees it.
 *
 * @param enabled whether it runs at all
 * @param tier {@code quick}, {@code full} or {@code thorough}
 * @param everyHours the interval
 * @param nextDueAt when the next one fires
 * @param asOf the session's clock — ⚠ the standing rule for anything with a deadline; a countdown
 *     built on the wall clock reports a different number from the one the engine will act on
 * @param cycles what a scan at this tier costs
 * @param affordable whether the rig could pay for it right now
 */
public record ScanScheduleView(
        boolean enabled,
        String tier,
        int everyHours,
        Instant nextDueAt,
        Instant asOf,
        long cycles,
        boolean affordable) {

    /** How long until the next one, never negative. */
    public Duration untilNext() {
        Duration left = Duration.between(asOf, nextDueAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Nothing scheduled. */
    public static ScanScheduleView off() {
        return new ScanScheduleView(false, "quick", 6, Instant.EPOCH, Instant.EPOCH, 0, true);
    }
}
