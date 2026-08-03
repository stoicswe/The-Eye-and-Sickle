package io.github.stoicswe.eyeandsickle.engine.save;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * A {@link SaveStore} for tests whose subject is not storage.
 *
 * <h2>⚠ ONE in-memory database for the whole JVM, and characters keyed by path</h2>
 *
 * Every one of the ~120 call sites this replaced said {@code new FileSaveStore(dir.resolve("s.json"))}
 * — a path standing in for "a place to keep a character". The path is kept as the <em>key</em> rather
 * than as a location: {@link #at} hashes it to a character id in one shared, migrated, in-memory
 * database.
 *
 * <p>That preserves the semantics those tests depend on exactly. Two calls with the same path address
 * the same character, which is what makes "save, reopen, assert it came back" work; two calls with
 * different paths are isolated, which is what makes a test that wants two characters work. And
 * because the database is shared, Flyway runs <strong>once per JVM</strong> rather than once per
 * store — the migration is not the thing under test and should not be paid for 120 times.
 *
 * <p>⚠ Tests that use a {@code @TempDir} get isolation for free, since the temporary path differs per
 * test. A test that hardcodes the same literal path in two methods is sharing a character
 * deliberately or by mistake, exactly as it was when these were real files.
 *
 * <h2>⚠ Not a third store</h2>
 *
 * This builds a {@link JdbcSaveStore} — the production one, against the production schema. It is a
 * fixture, not an implementation, which is the whole reason it can live here without reintroducing
 * the drift that unifying the stores removed.
 */
public final class TestSaves {

    /**
     * ⚠ Named, so a stray {@code jdbc:h2:mem:} URL elsewhere in the suite cannot collide with it, and
     * so a failure that mentions the database says which one.
     */
    private static final LocalDatabase DATABASE = LocalDatabase.inMemory("solo_test_saves");

    private TestSaves() {}

    /**
     * A store for the character identified by {@code path}.
     *
     * @param path stands in for the save file the test used to name; only its identity matters
     * @return a store on the shared in-memory database
     */
    public static SaveStore at(Path path) {
        return at(path, Instant::now);
    }

    /**
     * @param path stands in for the save file the test used to name
     * @param clock supplies the {@code updated_at} stamp
     * @return a store on the shared in-memory database
     */
    public static SaveStore at(Path path, java.util.function.Supplier<Instant> clock) {
        return DATABASE.store(characterFor(path), clock);
    }

    /**
     * ⚠ The raw client, for the few tests that are about the storage layer itself — the legacy-JSON
     * import, or writing a deliberately malformed document into the state column.
     *
     * @return the shared database
     */
    public static LocalDatabase database() {
        return DATABASE;
    }

    /**
     * Duplicates a stored character, so a test can load one starting state several times.
     *
     * <p>⚠ This replaces {@code Files.readAllBytes} / {@code Files.write} on a save file, and the
     * reason those tests copied bytes rather than building two characters is worth keeping in view:
     * <strong>two saves built identically are not identical.</strong> A fresh game draws its own
     * initial {@code networkWorkTarget} from the character id, so two independently-created
     * characters diverge within the hour — which reads exactly like a broken RNG contract when what
     * you were trying to compare was one save opened after different absences.
     *
     * @param from the character to copy
     * @param to where the copy lands
     */
    public static void copy(Path from, Path to) {
        DATABASE.jdbcClient()
                .sql("""
                        MERGE INTO character_game_state AS t
                        USING (SELECT CAST(:to AS uuid) AS character_id, state, format, updated_at
                                 FROM character_game_state WHERE character_id = :from) AS s
                           ON t.character_id = s.character_id
                         WHEN MATCHED THEN UPDATE
                              SET state = s.state, format = s.format, updated_at = s.updated_at
                         WHEN NOT MATCHED THEN INSERT (character_id, state, format, updated_at)
                              VALUES (s.character_id, s.state, s.format, s.updated_at)
                        """)
                .param("from", characterFor(from))
                .param("to", characterFor(to))
                .update();
    }

    /**
     * @param path any path
     * @return the character id this fixture associates with it — deterministic, so two calls agree
     */
    public static UUID characterFor(Path path) {
        return UUID.nameUUIDFromBytes(path.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
    }
}
