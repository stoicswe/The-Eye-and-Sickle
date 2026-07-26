package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;

/**
 * One line in the rig's log.
 *
 * <h2>Real severities, because the numbering teaches something</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.10 maps the {@code log} command to
 * {@code journalctl -f} and {@code tail -f}, and specifies that severity glyphs follow <b>RFC 5424's
 * eight levels</b>. Those are the real levels every syslog daemon on earth uses, with the real
 * numbering — {@code 0} is most severe and {@code 7} least, which is backwards from what most people
 * guess and is exactly the sort of small true thing this game exists to teach.
 *
 * <p>A player who learns here that {@code warning} is 4 and {@code debug} is 7 has learned the
 * numbering they will meet in {@code journalctl -p 4}, in a syslog config, and in every logging
 * library's severity enum.
 *
 * <h2>The facility is the subsystem, and it is how a log becomes filterable</h2>
 *
 * Real syslog has facilities ({@code auth}, {@code cron}, {@code daemon}…). This borrows the idea
 * rather than the list: the facility here is which part of the rig spoke — {@code compute},
 * {@code mining}, {@code defense}, {@code storage}, {@code market}. That is what makes
 * {@code log | grep mining} useful, which is the pipeline the whole terminal exists to support.
 */
public final class RigEvent {

    /** RFC 5424 severity. Lower is more severe — 0 is Emergency, 7 is Debug. */
    public int severity = 6;

    /** Which part of the rig produced this. Borrowed from syslog's facility idea. */
    public String facility = "rig";

    public Instant at = Instant.now();

    public String message = "";

    public RigEvent() {}

    public RigEvent(int severity, String facility, Instant at, String message) {
        this.severity = severity;
        this.facility = facility;
        this.at = at;
        this.message = message;
    }

    /** RFC 5424 §6.2.1's numerical codes and their keywords, verbatim. */
    public static final int EMERGENCY = 0;

    public static final int ALERT = 1;
    public static final int CRITICAL = 2;
    public static final int ERROR = 3;
    public static final int WARNING = 4;
    public static final int NOTICE = 5;
    public static final int INFORMATIONAL = 6;
    public static final int DEBUG = 7;

    /** The keyword RFC 5424 gives this level. */
    public static String keyword(int severity) {
        return switch (severity) {
            case EMERGENCY -> "emerg";
            case ALERT -> "alert";
            case CRITICAL -> "crit";
            case ERROR -> "err";
            case WARNING -> "warning";
            case NOTICE -> "notice";
            case INFORMATIONAL -> "info";
            default -> "debug";
        };
    }

    /**
     * A single-character glyph for the log gutter.
     *
     * <p>Paired with the keyword rather than replacing it. {@code docs/client/07} §5.2's rule is
     * never colour alone, and a glyph alone has the same problem: it needs a word beside it or it is
     * a private code.
     */
    public static String glyph(int severity) {
        return switch (severity) {
            case EMERGENCY, ALERT, CRITICAL -> "‼";
            case ERROR -> "✖";
            case WARNING -> "▲";
            case NOTICE -> "●";
            case INFORMATIONAL -> "·";
            default -> "˙";
        };
    }

    public String keyword() {
        return keyword(severity);
    }

    public String glyph() {
        return glyph(severity);
    }
}
