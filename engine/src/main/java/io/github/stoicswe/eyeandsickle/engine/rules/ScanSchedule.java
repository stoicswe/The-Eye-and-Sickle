package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ScanScheduleState;
import java.time.Duration;
import java.time.Instant;

/**
 * Auditing this rig on a timer.
 *
 * <h2>⚠ AT MOST ONE CATCH-UP SCAN, however long the player was away</h2>
 *
 * A six-hourly schedule and a four-day absence is sixteen missed scans. Running them all on the first
 * tick back would spend a day's compute in one second, fill the history with sixteen identical
 * reports, and — because a scan is a {@code TaskState} with a duration — leave the rig unusable for
 * as long as they took. Running <em>none</em> is the other extreme and is worse, because the player
 * asked for regular audits and would come back to a rig nobody had looked at.
 *
 * <p>So an absence produces exactly <b>one</b> scan: the one that was due. This is the same shape as
 * {@code Balance.OFFLINE_MINING_HOURS} — all offline yield is capped and never proportional to how
 * long somebody was gone — and the reasoning is identical. ⚠ It also means a schedule cannot be
 * farmed by quitting: sixteen missed scans and one missed scan produce the same result.
 *
 * <h2>⚠ A scheduled scan that cannot be paid for is SKIPPED, not queued</h2>
 *
 * The rig may be fully allocated when the timer comes round. Queueing it would make a scan land at an
 * unpredictable later moment — possibly mid-breach, where it would take cycles the player is
 * counting on. The schedule slips to the next interval and says so in the log, which is the
 * behaviour of every real scheduler and the only one that cannot surprise anybody.
 */
public final class ScanSchedule {

    private ScanSchedule() {}

    /**
     * How long an audit's verdict stays worth anything.
     *
     * <h2>⚠ A clean scan is a statement about a MOMENT, not a property of the rig</h2>
     *
     * Nothing stops a parasite landing the second after an audit finishes, so "clear" has a shelf
     * life — and a security panel that kept saying clear on the strength of a scan from last week
     * would be lying by omission in exactly the way a real one must not. Past this, the rig is not
     * <em>compromised</em>; it is <em>unknown</em>, which is a different and quieter thing.
     */
    public static final Duration STALE_AFTER = Duration.ofHours(24);

    /** The shortest interval a player may set. */
    public static final int MIN_HOURS = 1;

    /** The longest. Past a week a "schedule" is a thing you have forgotten you turned on. */
    public static final int MAX_HOURS = 168;

    /** Whether a scan is due at this instant. */
    public static boolean due(GameSave save, Instant now) {
        ScanScheduleState schedule = save.scanSchedule;
        if (schedule == null || !schedule.enabled) {
            return false;
        }
        // ⚠ A scan already running means this one is not due yet. Without the check, a schedule whose
        // interval is shorter than its own scan's duration would commission a new scan every tick and
        // the rig would be permanently mid-audit.
        boolean scanning = save.tasks.stream().anyMatch(task -> "scan".equals(task.kind));
        if (scanning) {
            return false;
        }
        return !now.isBefore(nextDue(save));
    }

    /** When the next scheduled scan is due. */
    public static Instant nextDue(GameSave save) {
        ScanScheduleState schedule = save.scanSchedule;
        if (schedule == null) {
            return Instant.EPOCH;
        }
        return schedule.lastRunAt.plus(Duration.ofHours(Math.max(MIN_HOURS, schedule.everyHours)));
    }

    /**
     * Stamps the schedule as having fired.
     *
     * <p>⚠ Set to {@code now}, <b>not</b> advanced by one interval from {@code lastRunAt}. Advancing
     * would let a long absence leave several intervals still in the past, so the next few ticks would
     * each fire another scan — the catch-up storm this class exists to prevent, arriving one tick
     * later than expected.
     */
    public static void stamp(GameSave save, Instant now) {
        if (save.scanSchedule != null) {
            save.scanSchedule.lastRunAt = now;
        }
    }

    /** Turns it on or off, and validates the interval. */
    public static void configure(GameSave save, boolean enabled, String tier, int everyHours, Instant now) {
        if (save.scanSchedule == null) {
            save.scanSchedule = new ScanScheduleState();
        }
        ScanScheduleState schedule = save.scanSchedule;
        boolean wasOff = !schedule.enabled;
        schedule.enabled = enabled;
        schedule.tier = tier == null || tier.isBlank() ? "quick" : tier;
        schedule.everyHours = Math.max(MIN_HOURS, Math.min(MAX_HOURS, everyHours));
        // ⚠ Turning it ON starts the clock rather than firing immediately. A scan the instant a
        // player flicks a switch reads as the switch having done something violent, and it takes
        // cycles they were about to use for whatever they opened the panel to do.
        if (enabled && wasOff) {
            schedule.lastRunAt = now;
        }
    }

    /** How long until the next one, never negative. Zero when it is due or disabled. */
    public static Duration until(GameSave save, Instant now) {
        if (save.scanSchedule == null || !save.scanSchedule.enabled) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(now, nextDue(save));
        return left.isNegative() ? Duration.ZERO : left;
    }
}
