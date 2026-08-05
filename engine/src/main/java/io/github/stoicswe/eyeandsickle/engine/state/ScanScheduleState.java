package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/** A standing instruction to audit this rig on a timer. */
public final class ScanScheduleState {

    /** Off by default. A scan costs cycles, and nothing should start spending them unasked. */
    public boolean enabled = false;

    /** {@code quick}, {@code full} or {@code thorough} — the tier vocabulary the rules already use. */
    public String tier = "quick";

    /** How often, in hours. */
    public int everyHours = 6;

    /**
     * When the last scheduled scan was commissioned.
     *
     * <p>⚠ The instant it <b>started</b>, not when it finished. A scan takes real time and a schedule
     * measured from completion would drift by the scan's own duration every cycle — a six-hourly
     * Thorough would slip six minutes a day and the player would never work out why.
     *
     * <p>⚠ EPOCH means "never run", which makes the first scan due immediately once enabled. That is
     * the honest reading: a player who turns on a six-hourly scan is asking for one now and then
     * every six hours, not for the first one in six hours' time.
     */
    public Instant lastRunAt = Instant.EPOCH;

    public ScanScheduleState() {}
}
