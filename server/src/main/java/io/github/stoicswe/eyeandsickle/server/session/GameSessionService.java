package io.github.stoicswe.eyeandsickle.server.session;

import io.github.stoicswe.eyeandsickle.protocol.game.GameIntent;
import io.github.stoicswe.eyeandsickle.protocol.game.GameSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.IntentOutcome;
import io.github.stoicswe.eyeandsickle.server.audit.OperatorLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Resolves game state and applies intent, by running <strong>the rules engine</strong>.
 *
 * <h2>⚠ ONE engine, two places to keep its state</h2>
 *
 * This does not reimplement anything. It drives {@code SoloGame} — the same engine single player runs
 * — against state held in <em>this server's</em> Postgres ({@link PostgresSaveStore}). A balance
 * change in {@code design/03} therefore lands in every mode at once and cannot drift between them,
 * which is what the old "second implementation of a subset of the rules" warning in {@code CLAUDE.md}
 * was about.
 *
 * <p>⚠ <strong>Invariant I14 is satisfied, not bent.</strong> The engine runs here, on the server,
 * against server-held state. The client renders a snapshot and sends intent; it decides nothing.
 *
 * <h2>Where the ordering lives</h2>
 *
 * Load → tick → act → persist is enforced by {@link EngineSessions#inSession}, not by this class, so
 * no call site can forget it and no future intent can skip it.
 */
@Service
public class GameSessionService {

    /**
     * ⚠ Process-local, and deliberately not persisted.
     *
     * <p>The revision lets a polling client tell "nothing changed" from "nothing arrived", which are
     * indistinguishable otherwise. It need not survive a restart — a reconnecting client refetches
     * the snapshot anyway — and a persisted counter would be a second piece of state to keep in step
     * with the first for no gain.
     */
    private final AtomicLong revision = new AtomicLong(1);

    private final EngineSessions engines;
    private final Supplier<Instant> clock;
    private final OperatorLog operatorLog;

    public GameSessionService(EngineSessions engines, OperatorLog operatorLog) {
        this(engines, Instant::now, operatorLog);
    }

    public GameSessionService(EngineSessions engines, Supplier<Instant> clock, OperatorLog operatorLog) {
        this.engines = Objects.requireNonNull(engines, "engines");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operatorLog = Objects.requireNonNull(operatorLog, "operatorLog");
    }

    /**
     * The authoritative state of one character, straight out of the engine.
     *
     * @param characterId the character
     * @return its snapshot
     */
    public GameSnapshot snapshot(UUID characterId) {
        return engines.inSession(
                characterId,
                game -> new GameSnapshot(
                        characterId,
                        revision.get(),
                        // ⚠ The SERVER's clock. Every deadline is measured against it, and the client's clock
                        // is a value a cheater controls.
                        clock.get(),
                        game.computeBudget(),
                        game.balance(),
                        // The engine carries heat as an int today; the wire type is BigDecimal so a later
                        // fractional heat does not need a wire change or a rounding decision.
                        BigDecimal.valueOf(game.state().personalHeat),
                        game.state().playedSeconds));
    }

    /**
     * Applies an intent by asking the engine to do it.
     *
     * <p>⚠ The {@code switch} is exhaustive over a <strong>sealed</strong> type, so a new variant
     * fails the build here rather than falling through to a default that silently accepts it.
     *
     * @param characterId the character
     * @param intent what the player wants
     * @return what happened
     */
    public IntentOutcome apply(UUID characterId, GameIntent intent) {
        if (intent == null) {
            return IntentOutcome.refused("no intent");
        }
        IntentOutcome outcome = engines.inSession(characterId, game -> switch (intent) {
            // ⚠ The ENGINE decides, not this method. It returns false when the rig cannot
            // afford the allocation, and that refusal is the game's own rule — duplicating the
            // check here is how a server and a client come to different answers.
            case GameIntent.AllocateSelfMining allocate ->
                game.allocateSelfMining(allocate.cycles())
                        ? IntentOutcome.ok(revision.incrementAndGet())
                        : IntentOutcome.refused("Your rig does not have those cycles free.");
            case GameIntent.SetMiningMode mode ->
                game.setMiningMode(mode.mode())
                        ? IntentOutcome.ok(revision.incrementAndGet())
                        : IntentOutcome.refused("That mining mode is not available to this rig.");
        });

        // ⚠ ONE place, because there is one intent endpoint. Operator visibility of "what are players
        // doing" cannot drift as systems are added, since every intent passes through here.
        String name = intent.getClass().getSimpleName();
        if (outcome.status() == IntentOutcome.Status.OK) {
            operatorLog.intentApplied(String.valueOf(characterId), name, outcome.revision());
        } else {
            operatorLog.intentRefused(String.valueOf(characterId), name, outcome.message());
        }
        return outcome;
    }
}
