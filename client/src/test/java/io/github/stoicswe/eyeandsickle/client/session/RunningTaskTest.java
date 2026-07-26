package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rig's running-task readout.
 *
 * <h2>What this is really guarding</h2>
 *
 * {@code docs/design/04-mining.md} §3.2 publishes a Duration column beside every scan tier — ~30s,
 * ~2 min, ~6 min — and for most of this project's life nothing waited for it: {@code scan()} spent
 * the cycles and returned, so a Thorough Scan was instantaneous and the published figure was
 * decoration. These tests exist so it cannot quietly become decoration again.
 *
 * <p>{@link Progress#progressUsesTheEngineClock()} is the one that caught a real bug. It is worth
 * keeping even though it looks trivial.
 */
class RunningTaskTest {

    private static final Instant T0 = Instant.parse("2026-07-26T12:00:00Z");

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
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static LocalGameSession session(Path dir, MutableClock clock) {
        return new LocalGameSession(SoloGame.open(new SaveStore(dir.resolve("s.json")), "op", clock));
    }

    @Nested
    @DisplayName("a scan is work that takes time")
    class Progress {

        @Test
        @DisplayName("starting a scan puts a task on the rig with the published duration")
        void scanCreatesATask(@TempDir Path dir) {
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            assertThat(s.tasks()).isEmpty();

            s.scan("thorough");

            assertThat(s.tasks()).hasSize(1);
            GameSession.RunningTask task = s.tasks().getFirst();
            assertThat(task.label()).isEqualTo("scan --thorough");
            assertThat(task.facility()).isEqualTo("scan");
            // docs/design/04-mining.md §3.2: Thorough is 35 cycles and ~6 minutes.
            assertThat(task.cycles()).isEqualTo(35);
            assertThat(Duration.between(task.startedAt(), task.endsAt())).isEqualTo(Duration.ofMinutes(6));
        }

        @Test
        @DisplayName("INVARIANT — progress is measured against the ENGINE's clock, not the wall clock")
        void progressUsesTheEngineClock(@TempDir Path dir) {
            // This caught a real bug. RunningTask#progress originally called Instant.now(), so under
            // any clock but the real one every task reported 100% complete the moment it started —
            // the same "engine reads the wall clock behind its caller's back" failure that
            // ComputeRules.spend's own comment warns about. In production the two clocks agree and
            // nothing would ever have looked wrong.
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            s.scan("thorough");

            assertThat(s.tasks().getFirst().progress()).isCloseTo(0.0, within(0.01));
            assertThat(s.tasks().getFirst().remaining()).isEqualTo(Duration.ofMinutes(6));

            clock.advance(Duration.ofMinutes(3));
            s.tick();

            assertThat(s.tasks().getFirst().progress()).isCloseTo(0.5, within(0.01));
            assertThat(s.tasks().getFirst().remaining()).isEqualTo(Duration.ofMinutes(3));
        }

        @Test
        @DisplayName("the cost stays on the task after its cycles have finished recovering")
        void costSurvivesRecovery(@TempDir Path dir) {
            // A Thorough Scan costs 35 cycles that come back in under three minutes on a lean rig,
            // but the scan runs for six. Reading the live allocation for the cycle figure made the
            // readout show "0C" halfway through a scan the player had just paid 35 cycles for.
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            s.scan("thorough");

            clock.advance(Duration.ofMinutes(5));
            s.tick();

            assertThat(s.computeBudget().recovering().cycles())
                    .as("the cycles have come back by now")
                    .isZero();
            assertThat(s.tasks()).hasSize(1);
            assertThat(s.tasks().getFirst().cycles())
                    .as("but the task still reports what it cost")
                    .isEqualTo(35);
        }

        @Test
        @DisplayName("the task completes on the tick after its end, and says so in the log")
        void completionIsReported(@TempDir Path dir) {
            // A six-minute scan that finishes while the player is reading the ledger has to leave a
            // trace, or the answer they paid 35 cycles for is one they can miss entirely.
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            s.scan("quick");

            clock.advance(Duration.ofSeconds(29));
            s.tick();
            assertThat(s.tasks()).as("29s into a 30s scan").hasSize(1);

            clock.advance(Duration.ofSeconds(2));
            s.tick();
            assertThat(s.tasks()).isEmpty();
            assertThat(s.log(7, 100))
                    .anyMatch(l -> l.facility().equals("scan") && l.message().contains("finished"));
        }

        @Test
        @DisplayName("a scan that ended while the game was closed completes on the first tick back")
        void survivesARestart(@TempDir Path dir) {
            // Six minutes of real time is long enough that quitting mid-scan is ordinary. The task is
            // persisted for the same reason deployed-miner buffers are: work that continues while
            // the player is away has to still be there when they return.
            Path file = dir.resolve("s.json");
            MutableClock clock = new MutableClock(T0);
            SoloGame first = SoloGame.open(new SaveStore(file), "op", clock);
            new LocalGameSession(first).scan("full");
            first.persist();

            MutableClock later = new MutableClock(T0.plus(Duration.ofHours(2)));
            LocalGameSession reopened =
                    new LocalGameSession(SoloGame.open(new SaveStore(file), "op", later));

            reopened.tick();
            assertThat(reopened.tasks()).as("the scan finished while away").isEmpty();
            assertThat(reopened.log(7, 200))
                    .anyMatch(l -> l.message().contains("scan --full") && l.message().contains("finished"));
        }
    }

    @Nested
    @DisplayName("what else counts as running work")
    class OtherWork {

        @Test
        @DisplayName("recovering cycles are a task with a real deadline")
        void thermalRecoveryIsATask(@TempDir Path dir) {
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            // Loaded to 90%, because that is the only condition under which a recovery outlives its
            // scan — and it is precisely the asymmetry docs/design/04 §3.2 is built around: "a
            // Thorough Scan on a heavily loaded rig leaves you effectively down 35 cycles for far
            // longer than the scan runs." On a lean rig a Quick Scan's 5 cycles are back in about
            // eleven seconds, well before the 30-second scan ends, and nothing lingers.
            s.allocateSelfMining(90);
            s.scan("quick");

            clock.advance(Duration.ofSeconds(31));
            s.tick();

            assertThat(s.tasks())
                    .as("the scan is over")
                    .noneMatch(t -> t.facility().equals("scan"));
            assertThat(s.tasks())
                    .as("but the cycles it spent are still coming back")
                    .anyMatch(t -> t.facility().equals("compute") && t.label().equals("thermal recovery"));
        }

        @Test
        @DisplayName("a running scan is not also listed as its own recovering cycles")
        void noDoubleCounting(@TempDir Path dir) {
            // Otherwise a Thorough Scan appears twice — once as itself, once as the cycles paying
            // for it — and the player reasonably concludes the rig is doing two things.
            MutableClock clock = new MutableClock(T0);
            LocalGameSession s = session(dir, clock);
            s.scan("thorough");

            assertThat(s.tasks()).hasSize(1);
            assertThat(s.tasks()).noneMatch(t -> t.label().equals("thermal recovery"));
        }

        @Test
        @DisplayName("an idle rig reports no work rather than inventing some")
        void idleIsEmpty(@TempDir Path dir) {
            LocalGameSession s = session(dir, new MutableClock(T0));
            s.allocateSelfMining(40);
            // Self-mining is continuous and has no completion, so it is an allocation, not a task.
            // Listing it here would put a progress bar on something that never finishes.
            assertThat(s.tasks()).isEmpty();
        }
    }
}
