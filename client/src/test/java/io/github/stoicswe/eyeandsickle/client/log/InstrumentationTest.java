package io.github.stoicswe.eyeandsickle.client.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.TestSaves;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * That the client's instrumentation actually reaches the buffer.
 *
 * <h2>⚠ Why this exists separately from {@code ClientLogTest}</h2>
 *
 * That one proves the capture layer works when something logs. This one proves that the client
 * <em>does</em> log — which is a different claim and the one that rots. Adding a logger field and a
 * call compiles whether or not the line is on a path anything takes, so a suite that only tested the
 * buffer would report full marks over a client that had gone quiet.
 */
class InstrumentationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void reset() {
        ClientLog.install();
        ClientLog.shared().clear();
    }

    @Test
    @DisplayName("⚠ a refused action is logged at INFO, with the reason")
    void refusalsAreLogged(@TempDir Path dir) {
        LocalGameSession session = new LocalGameSession(
                GameEngine.open(TestSaves.at(dir.resolve("s.json")), "operator", CLOCK));

        // Far more cycles than any rig has. The engine refuses, and `announce` is the chokepoint that
        // records it.
        session.allocateSelfMining(999_999);

        assertThat(messagesAt(LogLevel.INFO))
                .as("a refusal is the line somebody reading a log is looking for — \"why did nothing "
                        + "happen when I pressed that\" is answered here or nowhere")
                .anySatisfy(message -> assertThat(message).contains("refused").contains("cycles"));
    }

    @Test
    @DisplayName("a successful action is logged at DEBUG, not INFO")
    void successesAreQuieter(@TempDir Path dir) {
        LocalGameSession session = new LocalGameSession(
                GameEngine.open(TestSaves.at(dir.resolve("s.json")), "operator", CLOCK));

        session.allocateSelfMining(1);

        // ⚠ The LEVEL is the assertion. A busy player produces several of these a second; at INFO
        // they would bury the handful of lines describing the client's own lifecycle, which is the
        // alert-fatigue failure this game has a manual page about.
        assertThat(messagesAt(LogLevel.DEBUG))
                .anySatisfy(message -> assertThat(message).contains("intent").contains("allocateSelfMining"));
        assertThat(messagesAt(LogLevel.INFO))
                .as("a success must not be logged at the level refusals use")
                .noneSatisfy(message -> assertThat(message).contains("allocateSelfMining"));
    }

    @Test
    @DisplayName("opening a character database says which schema version it reached")
    void theDatabaseAnnouncesItself(@TempDir Path dir) {
        io.github.stoicswe.eyeandsickle.engine.save.LocalDatabase.openAt(dir.resolve("characters"));

        // The difference between "first launch" and "already there" is the single most useful fact
        // when a character does not appear, and it is only knowable from the migration count.
        assertThat(messagesAt(LogLevel.INFO))
                .anySatisfy(message -> assertThat(message).contains("character database ready"))
                .anySatisfy(message -> assertThat(message).contains("opening character database"));
    }

    private static List<String> messagesAt(LogLevel level) {
        return ClientLog.shared().entries().stream()
                .filter(entry -> entry.level() == level)
                .map(LogEntry::message)
                .toList();
    }
}
