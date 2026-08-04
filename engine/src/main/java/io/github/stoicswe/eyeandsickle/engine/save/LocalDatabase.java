package io.github.stoicswe.eyeandsickle.engine.save;

import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The single-player database: an embedded H2 file, migrated by the {@code engine} tier.
 *
 * <h2>⚠ It runs the SAME migration and the SAME store as a server</h2>
 *
 * The engine has one implementation, so its state has one shape. This opens a local file and applies
 * {@code classpath:db/migration/engine} — exactly the tier a server applies before its own — and hands
 * back a {@link JdbcClient} that {@link JdbcSaveStore} uses without knowing which mode it is in.
 *
 * <p>⚠ <strong>The {@code engine} tier ONLY.</strong> {@code core} and {@code federation} are the
 * authority tiers — players, items, the ledger, peers, validators — and single player has no authority
 * to model. Applying them here would create eighteen tables that nothing can fill and would drag the
 * DID alias and the append-only triggers along with them.
 *
 * <h2>⚠ What this is not</h2>
 *
 * It is not a server. There is no container, no HTTP, no pool and no thread — a {@code JdbcDataSource}
 * opens a connection when asked and that is the whole lifecycle. The module's enforcer rule still
 * refuses Spring Boot, {@code spring-web} and the server module, because those are what would make
 * this a second server rather than a place to keep bytes.
 *
 * <p>⚠ And it is <strong>not</strong> a tamper barrier. A local database is exactly as editable as the
 * JSON file it replaced; what keeps a solo character harmless is the quarantine rule
 * ({@code docs/architecture/12-lan-mode.md} §1), never the storage format.
 *
 * <h2>Connection settings, and why each one is there</h2>
 *
 * <ul>
 *   <li>{@code MODE=PostgreSQL} and {@code DATABASE_TO_LOWER=TRUE} — ⚠ must match the server's
 *       {@code application.yml}. The same migration runs in both, so a dialect difference would mean
 *       the schema this project tests is not the schema single player gets.
 *   <li>{@code DB_CLOSE_ON_EXIT=FALSE} — H2's shutdown hook otherwise races the JVM's, and the
 *       loser is a half-closed database. The application closes it deliberately instead.
 *   <li>⚠ {@code DB_CLOSE_DELAY=-1} — <strong>load-bearing, and its absence LOSES WRITES.</strong>
 *       There is no pool here: {@code JdbcDataSource} opens a connection per statement and closes it
 *       again, so with H2's default delay of 0 the database is opened and closed <em>between every
 *       operation</em>. Measured on H2 2.3.232: a save written through one call was visible to the
 *       next read and <em>absent from the one after that</em> — a row that existed at
 *       {@code readSlot(1)} and was gone by {@code readSlot(2)}, with nothing in between but the loop.
 *       It looks exactly like a phantom deletion and it is not one; the database is simply not the
 *       same database from one statement to the next. This keeps it open for the life of the JVM,
 *       which is also what the application wants: one character, held while the game is running.
 * </ul>
 *
 * <p>⚠ <strong>{@code AUTO_SERVER=TRUE} IS DELIBERATELY ABSENT, and it is not merely unwanted — H2
 * REFUSES THE COMBINATION.</strong> With {@code DB_CLOSE_ON_EXIT=FALSE} it is a hard error at connect
 * time ("Feature not supported: AUTO_SERVER=TRUE &amp;&amp; DB_CLOSE_ON_EXIT=FALSE", SQLState 50100),
 * so a build shipping both does not open a database at all. The server's {@code application.yml}
 * shipped exactly that pair from the PostgreSQL migration until this class was written and a test
 * finally opened a <em>file</em> database rather than an in-memory one.
 *
 * <p>It would be the wrong choice here anyway. {@code AUTO_SERVER} lets a second process attach to
 * the same database, and for a desktop game that means a player who launches twice quietly gets two
 * engines writing one character. A refusal to open is the better failure: it is visible, and it is
 * about the thing that actually went wrong.
 */
public final class LocalDatabase {

    /** ⚠ JUL, matching the client's capture. Flyway logs to JUL too, so a migration and
     * the code that asked for it land in ONE ordered stream on the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(LocalDatabase.class.getName());

    private final JdbcClient jdbcClient;
    private final Path file;

    private LocalDatabase(JdbcClient jdbcClient, Path file) {
        this.jdbcClient = jdbcClient;
        this.file = file;
    }

    /**
     * Opens (creating if absent) the database at {@code file} and brings its schema up to date.
     *
     * @param file the database path, without H2's {@code .mv.db} suffix — the profile directory's
     *     {@code characters} is the convention
     * @return the opened, migrated database
     */
    public static LocalDatabase openAt(Path file) {
        Objects.requireNonNull(file, "file");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:file:" + file.toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return migrate(dataSource, file);
    }

    /**
     * An in-memory database, for tests and for a session that must leave nothing behind.
     *
     * @param name distinguishes one in-memory database from another within a JVM
     * @return the opened, migrated database
     */
    public static LocalDatabase inMemory(String name) {
        Objects.requireNonNull(name, "name");
        JdbcDataSource dataSource = new JdbcDataSource();
        // ⚠ DB_CLOSE_DELAY=-1 keeps it alive for the life of the JVM. H2 drops an in-memory database
        // when its LAST connection closes, so without it the schema vanishes the moment nothing holds
        // one — intermittently, and dependent on timing rather than on anything the caller did.
        dataSource.setURL("jdbc:h2:mem:" + name
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return migrate(dataSource, null);
    }

    private static LocalDatabase migrate(DataSource dataSource, Path file) {
        // ⚠ Deliberately NOT `baseline-on-migrate`, matching the server. If the history is ever
        // unexpected the honest outcome is a failure, not a silent baseline that pretends the earlier
        // migrations ran — and here the cost of getting that wrong is somebody's character.
        LOG.log(java.util.logging.Level.INFO, "opening character database at {0}", file == null ? "memory" : file);
        var result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/engine")
                .load()
                .migrate();
        // ⚠ Says how many ran, which is the difference between "first launch" and "already there" —
        // the single most useful fact when a character does not appear.
        LOG.log(
                java.util.logging.Level.INFO,
                "character database ready at schema version {0} ({1} migration(s) applied)",
                new Object[] {result.targetSchemaVersion, result.migrationsExecuted});
        return new LocalDatabase(JdbcClient.create(dataSource), file);
    }

    /** @return a client on the migrated database. */
    public JdbcClient jdbcClient() {
        return jdbcClient;
    }

    /**
     * A store bound to one character in this database.
     *
     * <p>⚠ One store per character, never a shared one: the engine is stateful and per-character, so a
     * singleton store would serve one character's state to another. This is the same
     * {@link JdbcSaveStore} the server builds — the only difference between the modes is which
     * connection it is handed.
     *
     * @param characterId which character's row
     * @param clock supplies the {@code updated_at} stamp
     * @return the store
     */
    public SaveStore store(java.util.UUID characterId, java.util.function.Supplier<java.time.Instant> clock) {
        return JdbcSaveStore.forCharacter(jdbcClient, characterId, clock);
    }

    /** @return where this database lives, or {@code null} in memory. For logs and diagnostics. */
    public Path file() {
        return file;
    }
}
