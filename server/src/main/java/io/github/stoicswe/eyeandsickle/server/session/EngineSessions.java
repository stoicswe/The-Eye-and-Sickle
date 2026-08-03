package io.github.stoicswe.eyeandsickle.server.session;

import io.github.stoicswe.eyeandsickle.server.identity.PlayerRepository;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Holds one live rules engine per character, and serialises access to it.
 *
 * <h2>⚠ The engine is STATEFUL, so this is not optional</h2>
 *
 * {@link SoloGame} is a running game, not a stateless calculator: it advances a clock, mines blocks,
 * settles tasks and mutates its own state. Two requests for the same character running it
 * concurrently would interleave those mutations — and the corruption would be silent, showing up
 * later as a balance that does not match a ledger nobody can reconcile.
 *
 * <p>So: <strong>one engine per character, and one request at a time through it.</strong> The lock is
 * per character rather than global, so two players are never in each other's way.
 *
 * <h2>⚠ Load, tick, act, persist — in that order, every time</h2>
 *
 * The engine advances time when told to. A request that acted without ticking first would apply a
 * player's intent against a world frozen at their last visit, and one that did not persist would
 * discard everything it just decided. {@link #inSession} enforces the order so no call site has to
 * remember it — the same chokepoint reasoning as the operator log.
 *
 * <h2>Eviction</h2>
 *
 * ⚠ Bounded, because the key is a character id and a busy server would otherwise hold every character
 * that ever connected. An evicted engine is simply reloaded from Postgres on the next request; its
 * state is in the database, not in this map. The map is a <em>cache</em>, never the source of truth,
 * which is what makes eviction free.
 */
@Component
public class EngineSessions {

    /**
     * ⚠ A bound, not a tuning knob. Each entry holds a whole game's state in memory; the cost of
     * being wrong is an out-of-memory on a machine somebody is hosting for friends.
     */
    static final int MAX_LIVE_ENGINES = 512;

    private record Session(SoloGame game, ReentrantLock lock) {}

    private final Map<UUID, Session> live = new ConcurrentHashMap<>();
    private final JdbcClient jdbcClient;
    private final PlayerRepository players;
    private final Clock clock;

    public EngineSessions(JdbcClient jdbcClient, PlayerRepository players) {
        this(jdbcClient, players, Clock.systemUTC());
    }

    public EngineSessions(JdbcClient jdbcClient, PlayerRepository players, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.players = players;
        this.clock = clock;
    }

    /**
     * Runs {@code work} against the character's engine, holding its lock.
     *
     * <p>⚠ Ticks before and persists after, always. A read is not exempt: the engine's answer to
     * "what is my balance" depends on how much time has passed, so a read that skipped the tick would
     * report a stale world and the next write would then jump.
     *
     * @param characterId the character
     * @param work what to do with the engine
     * @return whatever {@code work} returned
     */
    public <T> T inSession(UUID characterId, Function<SoloGame, T> work) {
        Session session = live.computeIfAbsent(characterId, this::open);
        session.lock().lock();
        try {
            // Advance the world to now, THEN act. The other order applies intent to a frozen world.
            session.game().tick();
            T result = work.apply(session.game());
            // ⚠ Persisted inside the lock. Outside it, a second request could mutate the engine
            // between the work and the write, and the row would hold a state no request produced.
            session.game().persist();
            return result;
        } finally {
            session.lock().unlock();
        }
    }

    private Session open(UUID characterId) {
        if (live.size() >= MAX_LIVE_ENGINES) {
            // ⚠ Crude, and safe precisely because this is a cache: every evicted engine's state is
            // already in Postgres, so the worst case is a reload. An LRU would need a second
            // structure and a lock to protect its ordering, for no correctness gain.
            live.clear();
        }
        PostgresSaveStore store = PostgresSaveStore.forCharacter(jdbcClient, characterId, clock::instant);

        // ⚠ The character's REAL handle, not null. SoloGame.open falls back to a default handle when
        // it is creating fresh state and is given none — so passing null would name every character on
        // the server "operator", and the player would meet somebody else's name on their own rig.
        // Read from the players table, which is where the handle authoritatively lives.
        String handle = players.findCharacter(characterId)
                .map(io.github.stoicswe.eyeandsickle.server.identity.Player::handle)
                .orElse(null);

        // ⚠ resume() settles work that finished while nobody was connected — offline mining, completed
        // scans, chain catch-up. Without it a returning player's world would jump the moment they
        // acted rather than when they arrived.
        SoloGame game = SoloGame.open(store, handle, clock);
        game.resume();
        return new Session(game, new ReentrantLock());
    }

    /** Drops a character's cached engine, persisting first. For sign-out and for tests. */
    public void release(UUID characterId) {
        Session session = live.remove(characterId);
        if (session == null) {
            return;
        }
        session.lock().lock();
        try {
            session.game().persist();
        } finally {
            session.lock().unlock();
        }
    }

    /** How many engines are resident. Diagnostics only. */
    public int liveCount() {
        return live.size();
    }
}
