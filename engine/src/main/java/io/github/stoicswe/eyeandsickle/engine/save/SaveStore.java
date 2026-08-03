package io.github.stoicswe.eyeandsickle.engine.save;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.nio.file.Path;

/**
 * Where a game's state is kept — the seam that lets <strong>one rules engine serve every mode</strong>.
 *
 * <h2>⚠ Why this is an interface, and what it replaced</h2>
 *
 * {@code CLAUDE.md} used to warn that this module is "a SECOND IMPLEMENTATION of a subset of the
 * rules", and that re-tuning {@code design/03} meant re-reading {@code solo/Balance.java}. That warning
 * existed because the plan was for the server to grow its own engine — two codebases computing the
 * same yields, drifting.
 *
 * <p><strong>That is no longer the plan and no longer true.</strong> {@link GameEngine} is the engine,
 * and where its state lives is a detail behind this interface:
 *
 * <ul>
 *   <li>{@link FileSaveStore} — a JSON file on the player's disk. Single player.
 *   <li>the home server's implementation — the same state in <em>its</em> Postgres. LAN and federated.
 * </ul>
 *
 * ⚠ <strong>Invariant I14 is satisfied by the server's implementation, not weakened by it.</strong>
 * I14 says game state lives in the server's database and never in player-controlled infrastructure.
 * A server-side store keeps it exactly there — the player's copy is a render, and the engine that
 * decides anything runs on the server against server-held state.
 *
 * <p>⚠ And the reason a player-editable save is safe in single player is unchanged: nothing downstream
 * trusts it, and a solo character can never federate. That protection comes from the <em>quarantine</em>
 * ({@code docs/architecture/12-lan-mode.md} §1), never from the engine being a different one.
 */
public interface SaveStore {

    /** @return where this store keeps its state, for logs and diagnostics; may be null for non-file stores */
    default Path file() {
        return null;
    }

    /** @return whether any state exists yet */
    boolean exists();

    /**
     * Loads the state, or returns {@code null} if there is none yet.
     *
     * @throws UnreadableSaveException if state exists but must not be used — corrupt, or written by a
     *     newer build. Both are refused loudly rather than partially applied, because a half-loaded
     *     character is worse than an error message.
     */
    GameSave load();

    /** Writes the state. ⚠ Implementations must make this atomic; a half-written save eats a run. */
    void save(GameSave save);

    /** Thrown when state exists but must not be used. */
    final class UnreadableSaveException extends RuntimeException {
        public UnreadableSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
