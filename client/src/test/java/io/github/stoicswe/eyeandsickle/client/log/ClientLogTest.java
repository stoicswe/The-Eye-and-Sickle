package io.github.stoicswe.eyeandsickle.client.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The capture layer behind CLIENT LOGS. */
class ClientLogTest {

    private static final Logger LOG = Logger.getLogger(ClientLogTest.class.getName());

    @BeforeEach
    void reset() {
        ClientLog.install();
        ClientLog.shared().clear();
    }

    @Nested
    @DisplayName("capture")
    class Capture {

        @Test
        @DisplayName("⚠ TRACE is captured even though the panel starts with it filtered out")
        void traceIsCaptured() {
            // The whole point of the capture/filter split. If the buffer only took what the panel
            // showed, a player asked to turn trace on would see what happens NEXT rather than what
            // led up to the problem — and every trace investigation would start with "reproduce it
            // again", which for an intermittent fault is the same as "we cannot help you".
            LOG.finest("the finest detail");
            LOG.finer("finer detail");

            assertThat(levelsHeld()).contains(LogLevel.TRACE);
            assertThat(messagesHeld()).contains("the finest detail", "finer detail");
        }

        @Test
        @DisplayName("every level lands in exactly one band, and none is dropped")
        void everyLevelIsHeld() {
            LOG.severe("severe");
            LOG.warning("warning");
            LOG.info("info");
            LOG.config("config");
            LOG.fine("fine");
            LOG.finer("finer");

            assertThat(levelsHeld())
                    .as("all five bands must be reachable, or a filter toggle is dead")
                    .contains(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO, LogLevel.DEBUG, LogLevel.TRACE);
            assertThat(ClientLog.shared().size()).isEqualTo(6);
        }

        @Test
        @DisplayName("⚠ a message's parameters are substituted, not left as {0}")
        void parametersAreResolved() {
            // A naive capture keeps LogRecord's raw format string, so the panel shows the game's own
            // lines as literal "{0}" — which reads as broken logging rather than a broken capture.
            LOG.log(Level.INFO, "opened slot {0} for {1}", new Object[] {2, "ghost"});

            assertThat(messagesHeld()).anySatisfy(message -> assertThat(message)
                    .isEqualTo("opened slot 2 for ghost"));
        }

        @Test
        @DisplayName("a throwable is kept whole, for the tooltip")
        void stackTracesAreKept() {
            LOG.log(Level.SEVERE, "could not open the character", new IllegalStateException("boom"));

            assertThat(ClientLog.shared().entries())
                    .filteredOn(entry -> entry.level() == LogLevel.ERROR)
                    .anySatisfy(entry -> {
                        assertThat(entry.throwable()).contains("IllegalStateException").contains("boom");
                        assertThat(entry.message()).isEqualTo("could not open the character");
                    });
        }

        @Test
        @DisplayName("⚠ a library's own custom Level still lands in a band")
        void customLevelsAreBanded() {
            // Flyway and Jackson both define their own Levels. LogLevel.of compares intValue rather
            // than identity precisely so those are not dropped on the floor with nothing saying so.
            Level betweenWarnAndError = new Level("FLYWAY", Level.WARNING.intValue() + 1) {};
            Level belowTrace = new Level("VERYFINE", Level.FINEST.intValue() - 1) {};
            LOG.log(betweenWarnAndError, "a library speaking its own dialect");
            LOG.log(belowTrace, "quieter than FINEST");

            // Banded by threshold: a level lands in the band it is at or above, so 901 is WARN (not
            // ERROR — SEVERE is 1000) and anything under FINE is TRACE however low it goes.
            assertThat(levelsHeld()).contains(LogLevel.WARN, LogLevel.TRACE);
            assertThat(messagesHeld())
                    .as("a custom level must never be silently dropped")
                    .contains("a library speaking its own dialect", "quieter than FINEST");
        }
    }

    @Nested
    @DisplayName("the bound")
    class Bound {

        @Test
        @DisplayName("⚠ the buffer is bounded and REPORTS what it dropped")
        void oldestAreDroppedAndCounted() {
            for (int i = 0; i < ClientLog.CAPACITY + 50; i++) {
                LOG.info("line " + i);
            }

            assertThat(ClientLog.shared().size()).isEqualTo(ClientLog.CAPACITY);
            // A bounded log that silently discards its oldest lines will eventually be read as a
            // complete record of a session it is not. The count is on the panel for that reason.
            assertThat(ClientLog.shared().dropped()).isGreaterThanOrEqualTo(50);
            assertThat(messagesHeld()).doesNotContain("line 0").contains("line 2049");
        }
    }

    @Nested
    @DisplayName("the handler itself")
    class HandlerBehaviour {

        @Test
        @DisplayName("⚠ installing twice does not double every line")
        void installIsIdempotent() {
            ClientLog.install();
            ClientLog.install();
            LOG.info("said once");

            assertThat(messagesHeld()).filteredOn("said once"::equals).hasSize(1);
        }

        @Test
        @DisplayName("⚠ THE ROOT LOGGER IS NOT OPENED — JavaFX's own FINEST would drown the panel")
        void theRootIsLeftAlone() {
            // Measured, not theorised: with the root at ALL, one render of this tab dropped 11,905
            // records and filled the buffer with `javafx.scene.layout` reporting every node it moved.
            // Every line the client itself had logged was evicted before a human could read one.
            //
            // ⚠ This assertion is the guard on that. Someone will eventually reach for
            // `root.setLevel(ALL)` as the obvious way to "capture everything" — it compiles, the
            // capture tests still pass, and the panel becomes useless.
            assertThat(Logger.getLogger("").getLevel())
                    .as("opening the root captures the toolkit's per-node layout logging")
                    .isNotEqualTo(Level.ALL);
            assertThat(Logger.getLogger("io.github.stoicswe.eyeandsickle").getLevel())
                    .as("this project's own loggers must still reach trace")
                    .isEqualTo(Level.ALL);
        }

        @Test
        @DisplayName("⚠ the console handler is pinned to INFO, so the terminal is not flooded")
        void theTerminalIsNotFlooded() {
            for (Handler handler : Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    assertThat(handler.getLevel())
                            .as("the console must not inherit an opened-up level")
                            .isEqualTo(Level.INFO);
                }
            }
        }

        @Test
        @DisplayName("⚠ a record that cannot be formatted is held, not thrown from")
        void aBadRecordDoesNotEscape() {
            // This runs inside a logging handler. An exception escaping here propagates into whatever
            // the application was doing when it logged, which turns a diagnostic into the fault.
            LogRecord broken = new LogRecord(Level.INFO, "unmatched brace {0");
            broken.setParameters(new Object[] {"x"});
            broken.setLoggerName("deliberately.broken");

            for (Handler handler : Logger.getLogger("").getHandlers()) {
                handler.publish(broken);
            }

            assertThat(ClientLog.shared().entries())
                    .as("the line is kept with its raw message rather than lost")
                    .anySatisfy(entry -> assertThat(entry.message()).contains("unmatched brace"));
        }
    }

    private static List<LogLevel> levelsHeld() {
        return ClientLog.shared().entries().stream().map(LogEntry::level).toList();
    }

    private static List<String> messagesHeld() {
        return ClientLog.shared().entries().stream().map(LogEntry::message).toList();
    }
}
