package io.github.stoicswe.eyeandsickle.server.session;

import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerRepository;
import io.github.stoicswe.eyeandsickle.engine.session.EngineSessions;
import io.github.stoicswe.eyeandsickle.engine.save.JdbcSaveStore;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The server's wiring for the shared {@link EngineSessions}.
 *
 * <h2>Why this class exists and is this thin</h2>
 *
 * {@code EngineSessions} used to be a {@code @Component} here, taking a {@code JdbcClient} and a
 * {@code PlayerRepository} directly — which put the only correct way to run the engine behind a
 * container the client is forbidden to have. It now lives in {@code solo} and takes two functions, so
 * the ordering, locking and eviction rules are one implementation that single player uses too.
 *
 * <p>What is left here is the two answers only a server can give: state lives in a row of
 * {@code character_game_state}, and a handle comes from the {@code players} table. Both are
 * server-shaped facts, and neither belongs in the engine host.
 *
 * <p>⚠ It extends nothing and wraps nothing — it is a {@code @Bean} factory in class form, so the
 * shared type is what gets injected everywhere and no call site learns which mode it is in.
 */
@Component
public class ServerEngineSessions {

    private final EngineSessions sessions;

    public ServerEngineSessions(JdbcClient jdbcClient, PlayerRepository players, Clock clock) {
        this.sessions = new EngineSessions(
                characterId -> JdbcSaveStore.forCharacter(jdbcClient, characterId, clock::instant),
                // ⚠ The character's REAL handle, not null. GameEngine.open falls back to a default
                // handle when it is creating fresh state and is given none — so answering null would
                // name every character on the server "operator", and the player would meet somebody
                // else's name on their own rig. The players table is where the handle authoritatively
                // lives.
                characterId -> players.findCharacter(characterId).map(Player::handle).orElse(null),
                clock);
    }

    /** @return the shared engine host, wired for this server. */
    public EngineSessions sessions() {
        return sessions;
    }
}
