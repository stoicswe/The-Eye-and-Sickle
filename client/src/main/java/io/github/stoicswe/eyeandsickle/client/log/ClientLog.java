package io.github.stoicswe.eyeandsickle.client.log;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures everything the client logs, so the CLIENT LOGS tab can show it.
 *
 * <h2>⚠ java.util.logging, and no new dependency</h2>
 *
 * The client has no SLF4J and no Logback, and adding one would mean widening the enforcer ban for a
 * diagnostic. It does not need to: JUL is in the JDK, and — more usefully — it is what the libraries
 * already here log through. Flyway logs to JUL directly; commons-logging falls back to it. So one
 * handler on the root logger captures <strong>the client's own lines and its libraries' together, in
 * one ordered stream</strong>, which is the version of this panel actually worth opening: "the
 * migration ran, then the save loaded, then the deck failed" is a sequence no per-subsystem log would
 * show.
 *
 * <h2>⚠ Install EARLY — {@code Launcher.main}, before the toolkit</h2>
 *
 * Records logged before {@link #install} are gone; there is no backfill. Start-up is exactly when the
 * interesting failures happen — a database that would not open, a font that would not load, a
 * migration that refused — so installing this after the UI exists would miss the ones a player most
 * needs to send in.
 *
 * <h2>⚠ OUR LOGGERS GO TO {@code ALL}. THE ROOT DOES NOT, AND THAT IS MEASURED.</h2>
 *
 * A JUL record is dropped at the <em>logger</em> before any handler sees it, so capturing trace means
 * opening a logger up. The obvious move is to open the root — and it makes this panel useless inside
 * a single frame.
 *
 * <p><strong>JavaFX logs its own layout at {@code FINEST}</strong>: {@code javafx.scene.layout} emits
 * a record for every node resized and every node moved, on every layout pass. Measured on the first
 * render of the CLIENT LOGS tab with the root opened: <strong>11,905 records dropped</strong>, the
 * buffer entirely full of {@code Region@2b0d7119[styleClass=increment-arrow] moved to (10.0,0.0)},
 * and every line the client itself had logged evicted before a human could read one. The panel
 * compiled, its tests passed, and it was unusable — which is precisely the class of defect only a
 * render finds.
 *
 * <p>So: {@code io.github.stoicswe.eyeandsickle} goes to {@code ALL} — this project's own code, every
 * level — and the root keeps its default, so libraries still contribute {@code INFO} and above. The
 * stream is still one ordered sequence of the client and its libraries; what it leaves out is toolkit
 * internals nobody is here to read.
 *
 * <p>⚠ {@code -Deyeandsickle.log.verbose=true} opens the root anyway, for the rare case of chasing a
 * library's own debug output. It is a deliberate act with a documented cost, not a default.
 *
 * <p>The console handler is pinned to {@code INFO} either way, so the terminal keeps saying what it
 * always said.
 *
 * <h2>⚠ A logging handler must never log, and must never throw</h2>
 *
 * Logging from inside {@link #publish} is a loop: the record it emits is published to this handler,
 * which logs again. Throwing is worse — the exception propagates into whatever the application was
 * doing when it logged, so a diagnostic becomes the fault. Everything here is caught and dropped.
 */
public final class ClientLog {

    /** The root of this project's own loggers — the only ones opened up to trace by default. */
    private static final String OUR_PACKAGE = "io.github.stoicswe.eyeandsickle";

    /**
     * ⚠ Opens the ROOT logger to every level, including the toolkit's. Off by default, and see the
     * class note for what it costs. For chasing a library, not for ordinary use.
     */
    public static final String VERBOSE_PROPERTY = "eyeandsickle.log.verbose";

    /**
     * ⚠ A bound, matching the event recorder's. Two thousand lines is several minutes of ordinary
     * play and comfortably more than any single failure needs, and the entries are flattened strings
     * so the cost is bounded by what is written rather than by what was alive when it was written.
     */
    public static final int CAPACITY = 2000;

    private static final ClientLog INSTANCE = new ClientLog();

    /** ⚠ Guarded by itself. JUL publishes from whatever thread logged; the UI reads on the FX one. */
    private final ArrayDeque<LogEntry> entries = new ArrayDeque<>(CAPACITY);

    private final AtomicLong dropped = new AtomicLong();

    private ClientLog() {}

    /** @return the one buffer. */
    public static ClientLog shared() {
        return INSTANCE;
    }

    /**
     * Attaches the buffer to the root logger and opens this project's own loggers to every level.
     *
     * <p>⚠ Idempotent, because a second install would double every record — every line would appear
     * twice and read as the client doing everything twice.
     */
    public static void install() {
        Logger root = Logger.getLogger("");
        for (Handler existing : root.getHandlers()) {
            if (existing instanceof BufferHandler) {
                return;
            }
            // ⚠ Pin the console DOWN before opening anything UP, not after. Between the two calls
            // every record in flight would otherwise reach the terminal.
            if (existing instanceof ConsoleHandler) {
                existing.setLevel(Level.INFO);
            }
        }
        BufferHandler handler = new BufferHandler();
        handler.setLevel(Level.ALL);
        root.addHandler(handler);

        // ⚠ OUR loggers open to ALL; the ROOT is left alone. See the class note — opening the root
        // makes this panel unusable within one frame.
        Logger.getLogger(OUR_PACKAGE).setLevel(Level.ALL);
        if (Boolean.getBoolean(VERBOSE_PROPERTY)) {
            root.setLevel(Level.ALL);
        }
    }

    /**
     * @return every held entry, oldest first — a snapshot, safe to iterate off-thread
     */
    public List<LogEntry> entries() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    /** @return how many are held. */
    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    /**
     * @return how many were discarded to stay inside {@link #CAPACITY}. ⚠ Reported on the panel: a
     *     bounded log that silently drops its oldest lines will eventually be read as a complete
     *     record of a session that it is not.
     */
    public long dropped() {
        return dropped.get();
    }

    /** Forgets everything held, so the next reproduction stands alone. */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
        dropped.set(0);
    }

    private void add(LogEntry entry) {
        synchronized (entries) {
            if (entries.size() >= CAPACITY) {
                entries.removeFirst();
                dropped.incrementAndGet();
            }
            entries.addLast(entry);
        }
    }

    /** The JUL end of it. Package-private: nothing outside should be able to attach a second one. */
    private static final class BufferHandler extends Handler {

        @Override
        public void publish(LogRecord record) {
            if (record == null || !isLoggable(record)) {
                return;
            }
            try {
                INSTANCE.add(LogEntry.of(record));
            } catch (RuntimeException | StackOverflowError ignored) {
                // ⚠ Swallowed deliberately, and this is the one place in this codebase where that is
                // right. There is nowhere to report it: logging the failure would publish a record to
                // this same handler. A lost diagnostic line is a far smaller harm than a logging
                // handler that can take down the thing it was watching.
            }
        }

        @Override
        public void flush() {
            // Nothing buffered downstream — an entry is in the deque the moment it is published.
        }

        @Override
        public void close() {
            // Nothing to release. Deliberately does NOT clear the buffer: JUL closes handlers on
            // shutdown, and discarding the log at exit would empty it exactly when a player is being
            // asked what happened.
        }
    }
}
