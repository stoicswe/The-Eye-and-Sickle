package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One completed audit, as it is stored.
 *
 * <p>⚠ Written when a scan <b>settles</b>, never when it starts. A scan in flight is already visible
 * as a running task with its own progress; a row here is a result, and a list that mixed the two
 * would make "what has this rig found" a question you cannot answer by reading it.
 *
 * <p>Capped at {@link #LIMIT} — see {@code GameSave.scanReports}.
 */
public final class ScanReportState {

    /**
     * How many audits are kept.
     *
     * <p>A hundred is roughly a long campaign's worth at the rate a player actually scans, and the
     * value of the list is comparison against the recent past rather than a permanent archive. The
     * cap is also what stops a save growing without bound on the one action a player can repeat
     * indefinitely for cycles they get back.
     */
    public static final int LIMIT = 100;

    public String tier = "";
    public Instant startedAt = Instant.EPOCH;
    public Instant finishedAt = Instant.EPOCH;
    public long seconds;
    public long cycles;
    public String summary = "";
    public int found;
}
