package io.github.stoicswe.eyeandsickle.server.session;

import io.github.stoicswe.eyeandsickle.protocol.game.GameIntent;
import io.github.stoicswe.eyeandsickle.protocol.game.GameSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.IntentOutcome;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The game transport — two endpoints, closing the first slice of <b>CL-8</b>.
 *
 * <h2>Two endpoints, not a hundred</h2>
 *
 * {@code docs/architecture/13-the-game-transport.md} §1. Reads come from one {@link GameSnapshot};
 * writes go through one {@link GameIntent}. Adding a system adds a field and a variant rather than a
 * controller, and — the part that matters — <strong>there is exactly one place a client-supplied value
 * crosses into the rules</strong>, which makes <b>I14</b> auditable instead of a property somebody
 * re-checks per controller.
 *
 * <h2>⚠ The character in the path is NOT the authorisation</h2>
 *
 * A path variable is whatever the caller typed. The account is the authenticated principal, and
 * {@link GameSessionService} checks the character belongs to it before answering — otherwise this is
 * an endpoint for reading anybody's rig by guessing a UUID.
 *
 * <p>⚠ Authentication is still a seam here, exactly as it is on {@code CharacterController}, whose
 * javadoc says the DID comes "from the authenticated principal in a real deployment". See
 * {@code 13} §4 step 4, where the channel supplies it.
 */
@RestController
@RequestMapping("/api/session/{characterId}")
public class GameSessionController {

    private final GameSessionService sessions;

    GameSessionController(GameSessionService sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * The authoritative state of one character.
     *
     * <p>⚠ Polled, for now. {@code 13} §2 keeps deltas and push behind "once the snapshot is
     * measurably too big" and "once a poll feels wrong": a delta protocol is a second representation
     * that can disagree with the first, and a push that is not also reflected in the next snapshot is
     * a second source of truth.
     */
    @GetMapping("/state")
    public GameSnapshot state(@PathVariable UUID characterId) {
        return sessions.snapshot(characterId);
    }

    /**
     * Asks the server to do something.
     *
     * @return what it did — ⚠ including the resulting revision, so the client can tell whether the
     *     snapshot it already holds reflects this change rather than guessing from a timer
     */
    @PostMapping(value = "/intent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IntentOutcome intent(@PathVariable UUID characterId, @RequestBody GameIntent intent) {
        return sessions.apply(characterId, intent);
    }
}
