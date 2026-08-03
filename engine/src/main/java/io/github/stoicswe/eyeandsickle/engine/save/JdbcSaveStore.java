package io.github.stoicswe.eyeandsickle.engine.save;

import io.github.stoicswe.eyeandsickle.engine.persistence.Timestamps;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The engine's state, in a database — <strong>the only {@link SaveStore} there is</strong>.
 *
 * <h2>⚠ One store, every mode, and that is the point</h2>
 *
 * This used to be the server's half of a pair: a JSON file for single player, a database row for LAN
 * and federated. The pair was never carrying its weight. {@code character_game_state.state} is
 * {@code text} holding the engine's own JSON document — deliberately opaque to SQL, see the V7
 * migration — so <em>both stores were writing identical bytes</em>, and what the split actually bought
 * was two atomicity stories, two failure modes and two places for the save format to drift.
 *
 * <p>So there is one now, and single player runs the {@code engine} migration tier against a local
 * H2 file. What changes between modes is the connection and the tiers migrated, not the code.
 *
 * <h2>⚠ Invariant I14 is untouched by single player using this</h2>
 *
 * I14 governs whose machine holds state that <em>others</em> must trust. What made a player-editable
 * save safe was never its file format: it is that a solo character is local-only and can never
 * federate (the quarantine rule, {@code docs/architecture/12-lan-mode.md} §1). Moving the same bytes
 * into a local database changes nothing about that. On a server the row is still the server's, the
 * engine still runs there, and the client still renders a snapshot.
 *
 * <p>⚠ <strong>A local database is not a tamper-proof one and must never be mistaken for one.</strong>
 * H2 is as editable as JSON was, to anyone who wants to; it is simply less pleasant to read. Nothing
 * here may ever be treated as trusted because it is in a database — the quarantine is the protection,
 * and it is the whole protection.
 *
 * <h2>Atomicity</h2>
 *
 * The file store this replaced wrote to a temporary sibling and moved it into place, because a
 * half-written save eats a run. That is free here: one {@code MERGE} either lands or does not.
 *
 * <h2>⚠ One store per character, and it is not a bean</h2>
 *
 * The engine is stateful and per-character, so a shared singleton store would serve one character's
 * state to another. {@link #forCharacter} builds one bound to a single id, and the caller's lifetime
 * is the session's.
 */
public final class JdbcSaveStore implements SaveStore {

    /**
     * ⚠ {@code WRITE_DATES_AS_TIMESTAMPS} stays disabled, so an {@code Instant} is written as
     * ISO-8601 rather than epoch nanoseconds. It matters more than it looks: this is the same
     * configuration the retired file store used, which is what lets {@code JsonSaveImport} read an
     * existing {@code save.json} and hand it straight to this class without a conversion step.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final JdbcClient jdbcClient;
    private final UUID characterId;
    private final Supplier<Instant> clock;

    private JdbcSaveStore(JdbcClient jdbcClient, UUID characterId, Supplier<Instant> clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
        this.characterId = Objects.requireNonNull(characterId, "characterId");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static JdbcSaveStore forCharacter(JdbcClient jdbcClient, UUID characterId, Supplier<Instant> clock) {
        return new JdbcSaveStore(jdbcClient, characterId, clock);
    }

    @Override
    public boolean exists() {
        return jdbcClient
                        .sql("SELECT count(*) FROM character_game_state WHERE character_id = :id")
                        .param("id", characterId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    public GameSave load() {
        return jdbcClient
                .sql("SELECT state FROM character_game_state WHERE character_id = :id")
                .param("id", characterId)
                .query(String.class)
                .optional()
                .map(this::parse)
                .orElse(null);
    }

    private GameSave parse(String json) {
        GameSave save;
        try {
            save = MAPPER.readValue(json, GameSave.class);
        } catch (RuntimeException unreadable) {
            // ⚠ Refused loudly, never partially applied — the same rule the file store follows. A
            // half-loaded character is worse than an error, and on a server it is worse still because
            // the player cannot inspect the row.
            throw new UnreadableSaveException(
                    "Game state for character " + characterId + " is not readable", unreadable);
        }
        if (save == null) {
            throw new UnreadableSaveException("Game state for character " + characterId + " is empty", null);
        }
        if (save.format > GameSave.CURRENT_FORMAT) {
            // ⚠ Downgrading is refused. A newer save may hold state this build has no rule for, and
            // silently dropping it loses progress — on a server, somebody else's progress.
            throw new UnreadableSaveException(
                    "Game state for character " + characterId + " has format " + save.format
                            + ", but this build understands at most " + GameSave.CURRENT_FORMAT
                            + ". Update the server to load it.",
                    null);
        }
        return save;
    }

    @Override
    public void save(GameSave save) {
        jdbcClient
                .sql("""
                        MERGE INTO character_game_state AS t
                        USING (VALUES (CAST(:id AS uuid), CAST(:state AS text), CAST(:format AS int),
                                       CAST(:updatedAt AS timestamp with time zone)))
                              AS s(character_id, state, format, updated_at)
                           ON t.character_id = s.character_id
                         WHEN MATCHED THEN UPDATE
                              SET state = s.state, format = s.format, updated_at = s.updated_at
                         WHEN NOT MATCHED THEN INSERT (character_id, state, format, updated_at)
                              VALUES (s.character_id, s.state, s.format, s.updated_at)
                        """)
                .param("id", characterId)
                .param("state", MAPPER.writeValueAsString(save))
                .param("format", save.format)
                // ⚠ Timestamps.at, never a bare Instant. This began as a Postgres driver rule
                // ("Can't infer the SQL type") and survives the move to H2 as a house rule: the read
                // side (Row.instant) returns OffsetDateTime, so binding through Timestamps keeps one
                // spelling on both sides. Only the -Pit repository tests catch a raw bind.
                .param("updatedAt", Timestamps.at(clock.get()))
                .update();
    }
}
