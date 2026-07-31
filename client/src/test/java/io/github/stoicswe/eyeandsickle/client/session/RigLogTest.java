package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.RigEvent;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the rig log.
 *
 * <p>The two that matter most are {@link Coverage#offlineIncomeIsReported()} — silent income is
 * indistinguishable from a bug — and {@link Discipline#ticksAreNotLogged()}, because a log that
 * narrates every second buries the one line that mattered, which is the failure
 * {@code alert-fatigue(7)} describes in this game's own manual.
 */
class RigLogTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");

    private static LocalGameSession session(Path dir, Instant at) {
        return new LocalGameSession(
                SoloGame.open(new SaveStore(dir.resolve("s.json")), "op", Clock.fixed(at, ZoneOffset.UTC)));
    }

    @Nested
    @DisplayName("severities follow RFC 5424")
    class Severities {

        @Test
        @DisplayName("the numbering runs backwards, as the real one does")
        void numberingIsReal() {
            // Lower is MORE severe. This is the thing most people guess wrong, and it is exactly the
            // sort of small true fact the game exists to teach — `journalctl -p 4` means warnings
            // and worse, not warnings and better.
            assertThat(RigEvent.EMERGENCY).isZero();
            assertThat(RigEvent.ERROR).isEqualTo(3);
            assertThat(RigEvent.WARNING).isEqualTo(4);
            assertThat(RigEvent.INFORMATIONAL).isEqualTo(6);
            assertThat(RigEvent.DEBUG).isEqualTo(7);
        }

        @Test
        @DisplayName("every level has the keyword RFC 5424 gives it")
        void keywordsAreReal() {
            assertThat(RigEvent.keyword(0)).isEqualTo("emerg");
            assertThat(RigEvent.keyword(3)).isEqualTo("err");
            assertThat(RigEvent.keyword(4)).isEqualTo("warning");
            assertThat(RigEvent.keyword(5)).isEqualTo("notice");
            assertThat(RigEvent.keyword(6)).isEqualTo("info");
            assertThat(RigEvent.keyword(7)).isEqualTo("debug");
        }

        @Test
        @DisplayName("every level has a glyph AND a keyword — never a glyph alone")
        void glyphsAreNeverAlone() {
            // docs/client/07 §5.2: meaning must not rest on appearance. A glyph with no word beside
            // it is a private code.
            for (int level = 0; level <= 7; level++) {
                assertThat(RigEvent.glyph(level)).as("glyph for %d", level).isNotBlank();
                assertThat(RigEvent.keyword(level)).as("keyword for %d", level).isNotBlank();
            }
        }

        @Test
        @DisplayName("filtering by severity means 'this level or worse', like journalctl -p")
        void filterSemantics(@TempDir Path dir) {
            LocalGameSession s = session(dir, T0);
            s.allocateSelfMining(40); // info, 6
            s.arm("firewall", 1); // notice, 5

            assertThat(s.log(7, 100)).hasSizeGreaterThanOrEqualTo(2);
            // -p 5 keeps the notice and drops the info.
            assertThat(s.log(5, 100)).allMatch(l -> l.severity() <= 5);
            assertThat(s.log(5, 100)).anyMatch(l -> l.facility().equals("defense"));
            assertThat(s.log(5, 100)).noneMatch(l -> l.severity() == 6);
        }
    }

    @Nested
    @DisplayName("what gets logged")
    class Coverage {

        @Test
        @DisplayName("state changes are logged with the subsystem that made them")
        void stateChangesAreLogged(@TempDir Path dir) {
            LocalGameSession s = session(dir, T0);
            s.allocateSelfMining(40);
            s.arm("canary", 1);
            s.scan("quick");

            assertThat(s.log(7, 100)).extracting(GameSession.LogLine::facility).contains("mining", "defense", "scan");
        }

        /**
         * ⚠ This used to assert the log said "online-only", and the wording changed with I5.
         *
         * <p>I5 was amended on 2026-07-29 ({@code docs/design/15-open-questions.md} §3): the rig
         * keeps hashing for {@code Balance.OFFLINE_MINING_HOURS} after the client closes and then
         * stops dead. The log's job is unchanged and is the reason this test exists at all —
         * <b>silent behaviour and broken behaviour look identical from outside</b> — so what it must
         * now say is where the rig stopped, rather than that it never started.
         */
        @Test
        @DisplayName("INVARIANT I5 — offline income is reported, and so is where it stopped")
        void offlineIncomeIsReported(@TempDir Path dir) {
            // Deployed miners accrue for the whole window and self-mining accrues until the rig
            // spins down. Both facts are invisible without the log, and invisible income — or
            // invisibly *absent* income — is indistinguishable from a bug.
            Path file = dir.resolve("s.json");
            SoloGame first = SoloGame.open(new SaveStore(file), "op", Clock.fixed(T0, ZoneOffset.UTC));
            first.allocateSelfMining(50);
            NodeState node = new NodeState();
            MinerState miner = new MinerState();
            miner.hostCycles = 10;
            miner.deployedAt = T0;
            miner.lastAccruedAt = T0;
            node.deployedMiners.add(miner);
            first.state().knownNodes.add(node);
            first.persist();

            LocalGameSession later = new LocalGameSession(SoloGame.open(
                    new SaveStore(file), "op", Clock.fixed(T0.plus(Duration.ofHours(6)), ZoneOffset.UTC)));

            String text = String.join(
                    " ",
                    later.log(7, 100).stream().map(GameSession.LogLine::message).toList());
            assertThat(text).contains("Resumed after");
            assertThat(text).contains("buffered");
            // The chain ran without the client, and says so — a height that moved 26 blocks with no
            // explanation is indistinguishable from a tampered save.
            assertThat(text).contains("blocks synchronised");
            // The important half: it names where the rig stopped and how many blocks were mined
            // after that, rather than staying silent and leaving a player who was away six hours
            // and paid for four to wonder whether it broke.
            assertThat(text).contains("spun down");
            assertThat(text).contains("(I5)");
        }

        @Test
        @DisplayName("a riskier storage move is a warning, a safer one is not")
        void riskChangesAreGraded(@TempDir Path dir) {
            LocalGameSession s = session(dir, T0);
            var item = new io.github.stoicswe.eyeandsickle.solo.state.ItemState();
            item.displayName = "Overflow Kit";
            item.tier = "VAULT";
            s.game().state().items.add(item);

            s.moveItem(item.itemId, StorageTier.HIGH_HACKABLE_ZONE);
            assertThat(s.log(4, 100))
                    .anyMatch(l -> l.facility().equals("storage") && l.message().contains("more exposed"));

            s.moveItem(item.itemId, StorageTier.VAULT);
            // Moving back to safety is ordinary, not a warning.
            assertThat(s.log(7, 100))
                    .anyMatch(l -> l.severity() == 6 && l.message().contains("vault"));
        }
    }

    @Nested
    @DisplayName("log discipline")
    class Discipline {

        @Test
        @DisplayName("per-second accrual is NOT logged")
        void ticksAreNotLogged(@TempDir Path dir) {
            // A line every second saying "earned 0.011 EC" would bury the one line that mattered.
            // That is alert-fatigue(7), which is a page in this game's own manual.
            var clock = new MutableClock(T0);
            LocalGameSession s = new LocalGameSession(SoloGame.open(new SaveStore(dir.resolve("s.json")), "op", clock));
            s.allocateSelfMining(100);
            int afterAllocate = s.log(7, 500).size();

            for (int i = 0; i < 30; i++) {
                clock.advance(Duration.ofSeconds(1));
                s.tick();
            }
            assertThat(s.log(7, 500)).hasSize(afterAllocate);
        }

        @Test
        @DisplayName("the log is capped, so a long session cannot grow the save without bound")
        void logIsCapped(@TempDir Path dir) {
            LocalGameSession s = session(dir, T0);
            SoloSave save = s.game().state();
            for (int i = 0; i < SoloSave.LOG_CAPACITY + 250; i++) {
                io.github.stoicswe.eyeandsickle.solo.rules.EventLog.info(save, "test", "line " + i, T0);
            }
            assertThat(save.log).hasSize(SoloSave.LOG_CAPACITY);
            // Oldest dropped, newest kept — a log that dropped the NEW ones would be useless.
            assertThat(save.log.getLast().message).isEqualTo("line " + (SoloSave.LOG_CAPACITY + 249));
        }

        @Test
        @DisplayName("the log survives a restart, like a real journal")
        void logPersists(@TempDir Path dir) {
            Path file = dir.resolve("s.json");
            SoloGame first = SoloGame.open(new SaveStore(file), "op", Clock.fixed(T0, ZoneOffset.UTC));
            first.allocateSelfMining(20);
            first.persist();

            SoloGame reopened = SoloGame.open(new SaveStore(file), "op", Clock.fixed(T0, ZoneOffset.UTC));
            assertThat(reopened.log()).anyMatch(e -> e.facility.equals("mining"));
        }
    }

    /** A clock a test can wind forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
