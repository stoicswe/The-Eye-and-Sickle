package io.github.stoicswe.eyeandsickle.engine.session;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.SaveStore;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Holds one live rules engine per character, and serialises access to it.
 *
 * <h2>⚠ ONE host for every mode — this class is the reason there is no second one</h2>
 *
 * This used to live in {@code server}, taking a {@code JdbcClient} and a {@code PlayerRepository}, so
 * the only code that knew how to run the engine safely was code the client could not reach. The client
 * therefore grew its own way of owning a {@link GameEngine}, and "load, tick, act, persist" existed
 * twice — in one place enforced, in the other a convention.
 *
 * <p>It takes two <em>functions</em> now: where a character's state lives, and what a character is
 * called. Both are answered from a database on a server and from local storage in single player, and
 * neither answer belongs in here. What belongs in here is the ordering, the locking and the bound —
 * and those are identical in every mode, which is precisely why they should exist once.
 *
 * <p>⚠ <strong>No Spring and no JDBC.</strong> That is not incidental austerity: it is what lets this
 * class sit in {@code solo}, which is what lets the client use the same one. A {@code @Component}
 * annotation here would drag a container into single player for a map and a lock.
 *
 * <h2>⚠ The engine is STATEFUL, so the lock is not optional</h2>
 *
 * {@link GameEngine} is a running game, not a stateless calculator: it advances a clock, mines blocks,
 * settles tasks and mutates its own state. Two requests for the same character running it
 * concurrently would interleave those mutations — and the corruption would be silent, showing up
 * later as a balance that does not match a ledger nobody can reconcile.
 *
 * <p>So: <strong>one engine per character, and one request at a time through it.</strong> The lock is
 * per character rather than global, so two players are never in each other's way. In single player
 * there is one character and the lock is nearly free; it stays because the alternative is a rule that
 * holds in one mode and not the other.
 *
 * <h2>⚠ Load, tick, act, persist — in that order, every time</h2>
 *
 * The engine advances time when told to. A request that acted without ticking first would apply a
 * player's intent against a world frozen at their last visit, and one that did not persist would
 * discard everything it just decided. {@link #inSession} enforces the order so no call site has to
 * remember it.
 *
 * <h2>Eviction</h2>
 *
 * ⚠ Bounded, because the key is a character id and a busy server would otherwise hold every character
 * that ever connected. An evicted engine is simply reloaded from its store on the next request; its
 * state is in the store, not in this map. The map is a <em>cache</em>, never the source of truth,
 * which is what makes eviction free.
 */
public final class EngineSessions {

    /**
     * ⚠ A bound, not a tuning knob. Each entry holds a whole game's state in memory; the cost of
     * being wrong is an out-of-memory on a machine somebody is hosting for friends.
     */
    public static final int MAX_LIVE_ENGINES = 512;

    private record Session(GameEngine game, ReentrantLock lock) {}

    private final Map<UUID, Session> live = new ConcurrentHashMap<>();
    private final Function<UUID, SaveStore> stores;
    private final Function<UUID, String> handles;
    private final Clock clock;

    /**
     * @param stores where a character's state lives — a row on a server, a local database in single
     *     player. Called once per engine load, never per request.
     * @param handles the character's display name, or {@code null} if this mode does not know one
     *     before the engine has loaded. ⚠ Only consulted when the engine is <em>creating</em> fresh
     *     state: {@link GameEngine#open} falls back to a default handle when given none, so a mode that
     *     returns null for a known character would name them "operator" on their own rig.
     * @param clock the session clock. ⚠ Never {@code Instant.now()} inside the engine — every
     *     deadline is measured against this, and a test clock is how those become assertable.
     */
    public EngineSessions(Function<UUID, SaveStore> stores, Function<UUID, String> handles, Clock clock) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.handles = Objects.requireNonNull(handles, "handles");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Runs {@code work} against the character's engine, holding its lock.
     *
     * <p>⚠ Ticks before and persists after, always. A read is not exempt: the engine's answer to
     * "what is my balance" depends on how much time has passed, so a read that skipped the tick would
     * report a stale world and the next write would then jump.
     *
     * @param <T> what the work returns
     * @param characterId the character
     * @param work what to do with the engine
     * @return whatever {@code work} returned
     */
    public <T> T inSession(UUID characterId, Function<GameEngine, T> work) {
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
            // already in its store, so the worst case is a reload. An LRU would need a second
            // structure and a lock to protect its ordering, for no correctness gain.
            live.clear();
        }
        // ⚠ resume() settles work that finished while nobody was connected — offline mining, completed
        // scans, chain catch-up. Without it a returning player's world would jump the moment they
        // acted rather than when they arrived.
        GameEngine game = GameEngine.open(stores.apply(characterId), handles.apply(characterId), clock);
        game.resume();
        return new Session(game, new ReentrantLock());
    }

    /** @param characterId the character to drop, persisting first. For sign-out and for tests. */
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

    /** @return how many engines are resident. Diagnostics only. */
    public int liveCount() {
        return live.size();
    }
}
