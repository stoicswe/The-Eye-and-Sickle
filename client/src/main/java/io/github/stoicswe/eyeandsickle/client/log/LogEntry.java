package io.github.stoicswe.eyeandsickle.client.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.LogRecord;

/**
 * One captured log record, flattened at capture time.
 *
 * <h2>⚠ Flattened immediately, and that is not an optimisation</h2>
 *
 * A {@link LogRecord} is <strong>mutable and reusable</strong>: its message is a format string whose
 * parameters are resolved later, and a caller may legitimately hold one and change it. Keeping the
 * record and formatting it when the panel repaints would render whatever it says <em>then</em>, which
 * for a reused record is somebody else's message. Worse, the parameters may be live game objects, so
 * holding two thousand records would pin two thousand object graphs that the game has otherwise
 * finished with.
 *
 * <p>So everything is resolved here, once, on the thread that logged it: the message with its
 * parameters substituted, the logger name, the thread, the stack trace if there is one. What is left
 * is a record of {@link String}s that cannot change and holds nothing alive.
 *
 * @param at when it was logged
 * @param level the band it falls into
 * @param julLevel the real JUL level name, for the tooltip — {@code FINEST} and {@code FINER} both
 *     show as {@code TRACE} in the row, and somebody chasing a specific library's output wants the
 *     distinction back
 * @param logger the logger's name, usually a class
 * @param thread the thread that logged it
 * @param message the resolved message
 * @param throwable the stack trace, or empty
 */
public record LogEntry(
        Instant at,
        LogLevel level,
        String julLevel,
        String logger,
        String thread,
        String message,
        String throwable) {

    /**
     * Flattens a JUL record.
     *
     * <p>⚠ Never throws. This runs inside a logging handler, and an exception escaping here would
     * propagate into whatever the application was doing when it logged — turning a diagnostic into
     * the fault. A record that cannot be formatted is captured with its raw message instead.
     *
     * @param record the record, as JUL handed it over
     * @return the flattened entry
     */
    public static LogEntry of(LogRecord record) {
        String message;
        try {
            message = format(record);
        } catch (RuntimeException formattingFailed) {
            message = String.valueOf(record.getMessage());
        }
        String trace = "";
        if (record.getThrown() != null) {
            try {
                StringWriter out = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(out));
                trace = out.toString();
            } catch (RuntimeException ignored) {
                trace = String.valueOf(record.getThrown());
            }
        }
        return new LogEntry(
                record.getInstant(),
                LogLevel.of(record.getLevel()),
                record.getLevel() == null ? "?" : record.getLevel().getName(),
                shorten(record.getLoggerName()),
                // ⚠ The thread NAME, resolved now. LogRecord carries a thread id, and by the time a
                // panel repaints that id may belong to a different thread entirely — pooled threads
                // are recycled and JavaFX's are not stable across a session.
                Thread.currentThread().getName(),
                message,
                trace);
    }

    /**
     * ⚠ Substitutes parameters here rather than leaving {@code {0}} on screen. JUL's own
     * {@code SimpleFormatter} does this and a naive capture does not, so a panel that skipped it
     * would show the game's own log lines as literal format strings — which reads as a bug in the
     * logging rather than in the capture.
     */
    private static String format(LogRecord record) {
        String raw = record.getMessage();
        if (raw == null) {
            return "";
        }
        Object[] parameters = record.getParameters();
        if (parameters == null || parameters.length == 0) {
            return raw;
        }
        return java.text.MessageFormat.format(raw, parameters);
    }

    /**
     * ⚠ Drops this project's own package prefix, and nothing else's.
     *
     * <p>{@code io.github.stoicswe.eyeandsickle.client.view.LogView} is 58 characters of which 44 are
     * the same on every row, and a column that is identical on every row is a column that has stopped
     * carrying information. A third-party logger keeps its full name, because there the package IS
     * the information — knowing a line came from Flyway rather than Jackson is the whole point.
     */
    private static String shorten(String loggerName) {
        if (loggerName == null) {
            return "?";
        }
        String ours = "io.github.stoicswe.eyeandsickle.";
        return loggerName.startsWith(ours) ? loggerName.substring(ours.length()) : loggerName;
    }
}
