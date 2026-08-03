package io.github.stoicswe.eyeandsickle.server.session;

import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The engine's state, in <em>this server's own database</em> — the second driver for one rules engine.
 *
 * <h2>⚠ This is what makes Invariant I14 true rather than aspirational</h2>
 *
 * I14: game state never lives in a player's PDS or player-controlled infrastructure — only in the
 * server's own database. A {@link io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore} on the player's
 * disk is fine for single player precisely because nothing downstream trusts it and a solo character
 * can never federate. The moment other players are involved, the same engine has to run against state
 * the server holds — and that is all this class is.
 *
 * <h2>⚠ And it is what removed the duplicate-rules problem, rather than adding one</h2>
 *
 * {@code CLAUDE.md} carried a standing warning that {@code solo} was "a SECOND IMPLEMENTATION of a
 * subset of the rules", and that re-tuning {@code design/03} meant re-reading
 * {@code solo/Balance.java}. That was the cost of the old plan, in which the server grew its own
 * engine. There is now <strong>one</strong> engine and two places to keep its state, so a balance
 * change lands in every mode at once and cannot drift between them.
 *
 * <h2>Atomicity</h2>
 *
 * The file store writes to a temporary sibling and moves it into place, because a half-written save
 * eats a run. The equivalent here is free: a single {@code INSERT … ON CONFLICT DO UPDATE} inside the
 * caller's transaction either lands or does not.
 *
 * <h2>⚠ One store per character, and it is not a bean</h2>
 *
 * The engine is stateful and per-character, so a shared singleton store would serve one character's
 * state to another. {@link #forCharacter} builds one bound to a single id, and the caller's lifetime
 * is the session's.
 */
public final class JdbcSaveStore implements SaveStore {

    /**
     * ⚠ Configured identically to the file store's mapper, and that is a requirement rather than a
     * coincidence: a save written by one and read by the other must round-trip. In particular
     * {@code WRITE_DATES_AS_TIMESTAMPS} stays disabled, so an {@code Instant} is ISO-8601 in both.
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
    public SoloSave load() {
        return jdbcClient
                .sql("SELECT state FROM character_game_state WHERE character_id = :id")
                .param("id", characterId)
                .query(String.class)
                .optional()
                .map(this::parse)
                .orElse(null);
    }

    private SoloSave parse(String json) {
        SoloSave save;
        try {
            save = MAPPER.readValue(json, SoloSave.class);
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
        if (save.format > SoloSave.CURRENT_FORMAT) {
            // ⚠ Downgrading is refused. A newer save may hold state this build has no rule for, and
            // silently dropping it loses progress — on a server, somebody else's progress.
            throw new UnreadableSaveException(
                    "Game state for character " + characterId + " has format " + save.format
                            + ", but this build understands at most " + SoloSave.CURRENT_FORMAT
                            + ". Update the server to load it.",
                    null);
        }
        return save;
    }

    @Override
    public void save(SoloSave save) {
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
