package io.github.stoicswe.eyeandsickle.client.log;

import java.util.logging.Level;

/**
 * The five severities the CLIENT LOGS tab offers, over {@link java.util.logging}'s nine.
 *
 * <h2>⚠ Why five and not nine</h2>
 *
 * JUL ships {@code SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST} plus {@code ALL} and
 * {@code OFF}. Three of those are gradations of trace that nobody filters between in practice, and
 * {@code CONFIG} is a level almost nothing outside the JDK emits. Offering all nine would be a filter
 * row with four checkboxes that never change what is on screen — which teaches a reader that the
 * filter does not work.
 *
 * <p>So the mapping collapses the trace band and folds {@code CONFIG} into {@code DEBUG}. Nothing is
 * hidden: every record still lands in exactly one of these, and the record's real JUL level is on the
 * row's tooltip for anyone who needs it.
 *
 * <h2>⚠ These names are the industry's, not JUL's, and that is deliberate</h2>
 *
 * {@code ERROR/WARN/INFO/DEBUG/TRACE} is what every other logging framework calls these, what the
 * game's own {@code alert-fatigue(7)} manual page uses, and what a player who reports a bug will have
 * seen elsewhere. {@code SEVERE} and {@code FINEST} are JUL's vocabulary alone.
 */
public enum LogLevel {

    /** Something failed. {@code SEVERE}. */
    ERROR("ERROR", Level.SEVERE, "es-log-error"),

    /** Something is wrong but the client carried on. {@code WARNING}. */
    WARN("WARN", Level.WARNING, "es-log-warn"),

    /** The ordinary record of what the client did. {@code INFO}. */
    INFO("INFO", Level.INFO, "es-log-info"),

    /** Detail for chasing a specific problem. {@code FINE} and {@code CONFIG}. */
    DEBUG("DEBUG", Level.FINE, "es-log-debug"),

    /**
     * Everything else — {@code FINER}, {@code FINEST}.
     *
     * <p>⚠ <strong>OFF by default in the tab</strong>, and captured anyway. Trace is worth having the
     * moment somebody needs it and worthless to scroll past the rest of the time; capturing it but
     * not showing it means a player asked to turn it on sees the records that led up to the problem
     * rather than only what happens after they flip the switch.
     */
    TRACE("TRACE", Level.FINER, "es-log-trace");

    private final String label;
    private final Level julLevel;
    private final String styleClass;

    LogLevel(String label, Level julLevel, String styleClass) {
        this.label = label;
        this.julLevel = julLevel;
        this.styleClass = styleClass;
    }

    /** @return the display name. */
    public String label() {
        return label;
    }

    /** @return the JUL level this band is named for. */
    public Level julLevel() {
        return julLevel;
    }

    /** @return the style class the row is painted with. Colours live in {@code theme.css}. */
    public String styleClass() {
        return styleClass;
    }

    /**
     * Which band a JUL level falls into.
     *
     * <p>⚠ Compares {@code intValue()} rather than identity. A library is free to log at a custom
     * {@link Level} — Flyway and Jackson both define their own — and an identity check would drop
     * those records on the floor with nothing anywhere saying so. Thresholds catch every level that
     * has ever been or will ever be defined.
     *
     * @param level any JUL level, including a custom one
     * @return the band it belongs to; {@code TRACE} for anything below {@code FINE}
     */
    public static LogLevel of(Level level) {
        if (level == null) {
            return INFO;
        }
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return ERROR;
        }
        if (value >= Level.WARNING.intValue()) {
            return WARN;
        }
        if (value >= Level.INFO.intValue()) {
            return INFO;
        }
        // ⚠ CONFIG (700) sits between INFO and FINE and folds into DEBUG rather than getting a band
        // of its own — see the class note.
        if (value >= Level.FINE.intValue()) {
            return DEBUG;
        }
        return TRACE;
    }
}
