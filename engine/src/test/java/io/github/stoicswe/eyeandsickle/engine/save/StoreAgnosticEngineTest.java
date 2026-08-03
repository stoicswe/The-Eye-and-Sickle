package io.github.stoicswe.eyeandsickle.engine.save;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The engine runs against <strong>any</strong> {@link SaveStore}, not just a file.
 *
 * <h2>What this is actually protecting</h2>
 *
 * {@code SaveStore} became an interface so the home server could run this same engine against its own
 * Postgres — one set of rules for single player, LAN and federated, instead of two implementations
 * drifting apart. That only holds if {@link GameEngine} is genuinely indifferent to where its state
 * lives.
 *
 * <p>⚠ It would be easy for it not to be: an {@code instanceof}, a call to {@code file()}, or an
 * assumption that {@code load()} is cheap enough to call in a loop would each re-couple the engine to
 * a filesystem — and the failure would appear only on the server, at runtime, in front of players.
 *
 * <p>This uses a store with no filesystem behind it at all, so any such coupling fails here instead.
 * The server's own store is exercised against a real Postgres under {@code -Pit}; what is checked here
 * is the property that makes that store possible.
 */
class StoreAgnosticEngineTest {

    /** A {@link SaveStore} with no file, no path, and no disk. */
    private static final class InMemoryStore implements SaveStore {
        private GameSave held;
        int writes;

        @Override
        public boolean exists() {
            return held != null;
        }

        @Override
        public GameSave load() {
            return held;
        }

        @Override
        public void save(GameSave save) {
            writes++;
            held = save;
        }
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("⚠ a brand-new game opens against a store that has no filesystem")
    void opensWithoutAFile() {
        InMemoryStore store = new InMemoryStore();

        GameEngine game = GameEngine.open(store, "ghost", at(Instant.parse("2026-08-02T12:00:00Z")));

        assertThat(game.state().handle).isEqualTo("ghost");
        assertThat(game.computeBudget()).isNotNull();
        assertThat(game.balance()).isNotNull();
    }

    @Test
    @DisplayName("⚠ file() returning null breaks nothing — a database-backed store has no path")
    void noPathIsFine() {
        // The interface defaults file() to null precisely because a non-file store has none. Engine
        // code reaching for it would fail HERE rather than on somebody's server.
        InMemoryStore store = new InMemoryStore();
        GameEngine game = GameEngine.open(store, "ghost", at(Instant.parse("2026-08-02T12:00:00Z")));

        assertThat(store.file()).isNull();
        game.persist();
        assertThat(store.writes).isPositive();
    }

    @Test
    @DisplayName("state persists and reloads through a non-file store")
    void roundTripsThroughTheStore() {
        // The property the server depends on: what the engine wrote is what it reads back, with no
        // file involved anywhere.
        Instant start = Instant.parse("2026-08-02T12:00:00Z");
        InMemoryStore store = new InMemoryStore();

        GameEngine first = GameEngine.open(store, "ghost", at(start));
        String characterId = first.state().characterId;
        first.persist();

        GameEngine reopened = GameEngine.open(store, null, at(start.plus(Duration.ofMinutes(5))));

        assertThat(reopened.state().characterId).isEqualTo(characterId);
        assertThat(reopened.state().handle).isEqualTo("ghost");
    }

    @Test
    @DisplayName("⚠ the engine ADVANCES against a non-file store — it is not merely readable, it runs")
    void theEngineActuallyRuns() {
        // Opening and reloading would pass even if the engine quietly did nothing. This drives it:
        // time passes, the world moves.
        Instant start = Instant.parse("2026-08-02T12:00:00Z");
        InMemoryStore store = new InMemoryStore();
        GameEngine game = GameEngine.open(store, "ghost", at(start));
        long chainAtStart = game.chainHeight();
        game.persist();

        GameEngine later = GameEngine.open(store, null, at(start.plus(Duration.ofHours(6))));
        later.resume();

        // Six hours of chain is not zero blocks. The exact number is the chain's business.
        assertThat(later.chainHeight()).isGreaterThan(chainAtStart);
    }
}
