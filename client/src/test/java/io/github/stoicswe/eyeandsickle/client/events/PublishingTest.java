package io.github.stoicswe.eyeandsickle.client.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.support.TestSaves;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What actually reaches the bus when the game is played.
 *
 * <h2>Why this is separate from {@link EventBusTest}</h2>
 *
 * That one proves the broker works. This one proves it is <b>wired to the things that happen</b>,
 * which is the requirement that can rot: a chokepoint someone reroutes, a background settle that
 * moves into a branch nothing publishes from. A missing event looks exactly like an interaction that
 * never occurred, so the only way to know the stream is complete is to make the game do something and
 * assert the event arrived.
 */
class PublishingTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    private static final class Winding extends Clock {
        private Instant now;

        Winding(Instant start) {
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

    private static List<String> types(LocalGameSession session) {
        return session.events().recorder().events().stream()
                .map(CloudEvent::shortType)
                .toList();
    }

    @Test
    @DisplayName("a successful intent is published, named after the method the player invoked")
    void successfulIntent(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = TestSaves.bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        session.events().recorder().clear();

        session.allocateSelfMining(2L);

        // ⚠ The subject is read off the call stack, so this assertion is what keeps that mechanism
        // honest: if the StackWalker filter ever skips the wrong frame, every event in the game is
        // suddenly named "changed" and nothing else fails.
        CloudEvent published = session.events().recorder().events().getLast();
        assertThat(published.shortType()).isEqualTo("intent");
        // ⚠ `startsWith`, because the frame named is the INNERMOST one that is not the mechanism —
        // here `allocateSelfMiningIntent`, the private method the public port delegates to. That is
        // the more precise answer and it is the right one to keep; pinning the whole string would
        // couple this test to a private name, and pinning only "intent" would let the walker start
        // reporting `changed` for everything with nothing failing.
        assertThat(published.subject()).startsWith("allocateSelfMining");
        assertThat(published.payload()).contains("outcome=ok");
    }

    @Test
    @DisplayName("a refusal is published too — the half a success-only stream would hide")
    void refusedIntent(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = TestSaves.bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        session.events().recorder().clear();

        session.refuse("net", "no route to that machine");

        CloudEvent published = session.events().recorder().events().getLast();
        assertThat(published.shortType()).isEqualTo("intent.refused");
        assertThat(published.subject()).isEqualTo("net");
        assertThat(published.payload()).contains("no route to that machine");
    }

    /**
     * ⚠ The background half, and the one with nobody watching.
     *
     * <p>These events are DIFFED across the tick rather than emitted by the rules, because the rules
     * live in {@code solo} and {@code solo} has no broker and must not gain one. That makes them the
     * fragile half: nothing in {@code solo} fails if the diff stops matching, so this test is the
     * only thing standing between a working stream and a silent one.
     */
    @Test
    @DisplayName("a task finishing while nobody is looking publishes on the next tick")
    void backgroundTaskCompletion(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = TestSaves.bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        session.scan("quick");
        assertThat(game.tasks()).as("the scan must actually be running").isNotEmpty();
        session.events().recorder().clear();

        clock.advance(Duration.ofMinutes(10));
        session.tick();

        assertThat(types(session)).contains("task.finished");
    }

    @Test
    @DisplayName("blocks landing publish once, carrying how far the chain moved")
    void chainAdvance(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = TestSaves.bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        long before = game.chainHeight();
        session.events().recorder().clear();

        clock.advance(Duration.ofHours(2));
        session.tick();

        long moved = game.chainHeight() - before;
        assertThat(moved)
                .as("two hours must produce blocks, or this test proves nothing")
                .isPositive();
        List<CloudEvent> blocks = session.events().recorder().events().stream()
                .filter(event -> event.shortType().equals("chain.block"))
                .toList();
        // ⚠ ONE event for a settle of many blocks, carrying the count — not one per block. A catch-up
        // after a long absence settles hundreds, and a stream that emitted one apiece would push every
        // other event out of a bounded log the moment the player came back from lunch.
        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().payload()).isEqualTo("blocks=" + moved);
        assertThat(blocks.getFirst().subject()).isEqualTo(String.valueOf(game.chainHeight()));
    }

    @Test
    @DisplayName("a quiet tick publishes nothing")
    void quietTicksAreSilent(@TempDir Path dir) {
        // The heartbeat runs once a second for the whole session. If an idle one published, the log
        // would hold nothing but heartbeats within the hour and the feature would be worse than absent.
        Winding clock = new Winding(T0);
        SoloGame game = TestSaves.bare(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        session.tick();
        session.events().recorder().clear();
        session.tick();
        assertThat(session.events().recorder().events()).isEmpty();
    }
}
