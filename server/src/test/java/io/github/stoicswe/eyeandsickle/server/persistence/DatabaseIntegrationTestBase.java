package io.github.stoicswe.eyeandsickle.server.persistence;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class for every test that touches real SQL: one shared, Flyway-migrated database for the whole
 * test run.
 *
 * <h2>How to use it</h2>
 *
 * Extend it and <strong>name your class {@code SomethingIT}</strong>. Failsafe's default include
 * pattern picks up class names ending in {@code IT}, so such a class runs under
 * {@code mvn -Pit verify} and is invisible to the default {@code mvn verify}.
 *
 * <p>This class is deliberately named neither {@code ...IT} nor {@code ...Test} — it is abstract and
 * matches neither surefire's nor failsafe's include patterns, so neither runner will ever try to
 * execute it on its own.
 *
 * {@snippet lang = java:
 * class LedgerRepositoryIT extends DatabaseIntegrationTestBase {
 *
 *     // annotate with org.junit.jupiter.api.Test
 *     void writesAndReadsBack() {
 *         jdbcClient().sql("INSERT INTO ...").update();
 *     }
 * }
 *}
 *
 * <h2>⚠ No Docker, and the {@code -Pit} split is now about SPEED rather than about Docker</h2>
 *
 * These were Testcontainers tests, and the profile existed because "a real database" meant a
 * PostgreSQL container — {@code mvn verify} must never require a daemon a client-only contributor has
 * no reason to run. Since the database moved to embedded H2 (2026-08-02) that reason is gone: the
 * whole suite runs wherever the build does. The split is kept because these tests migrate a schema
 * and truncate it between every test, which is not what belongs in the fast loop.
 *
 * <p>⚠ <strong>These tests earn their keep and are not a formality.</strong> The port to H2 was green
 * on {@code mvn verify} while the schema was, in fact, broken: an {@code IN}-list CHECK constraint
 * that could not evaluate at all, a URL constraint that refused every URL, and a partial unique index
 * that had quietly become a total one. Every one of those is invisible to a unit test, because every
 * one of them is a property of the database rather than of the code that talks to it.
 *
 * <h2>All three migration tiers, always</h2>
 *
 * Tests migrate {@code engine}, {@code core} <em>and</em> {@code federation}, mirroring a server
 * started with the {@code federation} profile. Every other configuration runs a strict subset — a
 * non-federating server drops the last, single player runs {@code engine} alone — so testing the
 * superset exercises all of them. It is also the only configuration in which the cross-tier
 * dependencies are checked at all: V1001 uses {@code is_did} from core's V2, and core's V8 adds a
 * foreign key to the table the engine tier's V7 creates.
 *
 * <h2>Isolation between tests</h2>
 *
 * {@link #resetDatabase()} runs before each test and truncates every table, then restores the
 * {@code server_state} singleton that V2 seeds. Truncation rather than a rolled-back transaction,
 * because several things worth testing here — the append-only triggers, {@code SELECT ... FOR UPDATE}
 * contention, the monotonic-sequence guard — need real committed state and more than one connection.
 *
 * <p>The table list is discovered from the catalogue rather than hardcoded, so a table added by a
 * later migration is cleaned up without anyone having to remember to add it here.
 */
public abstract class DatabaseIntegrationTestBase {

    /**
     * ⚠ Embedded H2, shared by every repository test — no Docker, no container, no daemon.
     *
     * <p>These were Testcontainers tests behind {@code -Pit} because "a real database" meant a real
     * PostgreSQL. Since the database moved to embedded H2 (2026-08-02) they run wherever the build
     * does, which is the biggest practical gain of the migration for this codebase.
     *
     * <p>⚠ {@code DB_CLOSE_DELAY=-1} keeps the in-memory database alive for the life of the JVM. H2
     * drops one when its LAST connection closes, so without it the schema would vanish between tests
     * that happen to leave no connection open — intermittently, and dependent on pool timing.
     *
     * <p>⚠ {@code MODE=PostgreSQL} and {@code DATABASE_TO_LOWER} must match {@code application.yml}.
     * A suite running against a different dialect than production proves nothing about production.
     */
    private static final String URL =
            "jdbc:h2:mem:eyeandsickle_it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static final DataSource DATA_SOURCE;
    private static final JdbcClient JDBC_CLIENT;
    private static final TransactionTemplate TRANSACTIONS;

    static {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(URL);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        DATA_SOURCE = dataSource;

        // Exactly the locations application.yml configures for a federating server. Deliberately not
        // `baseline-on-migrate`: if the schema history is ever unexpected, the honest outcome is a
        // failure, not a silent baseline that pretends the earlier migrations ran.
        Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations(
                        "classpath:db/migration/engine",
                        "classpath:db/migration/core",
                        "classpath:db/migration/federation")
                .load()
                .migrate();

        JDBC_CLIENT = JdbcClient.create(DATA_SOURCE);
        TRANSACTIONS = new TransactionTemplate(new DataSourceTransactionManager(DATA_SOURCE));
    }

    /**
     * The migrated database.
     *
     * @return a {@code JdbcClient} on the shared database
     */
    protected static JdbcClient jdbcClient() {
        return JDBC_CLIENT;
    }

    /**
     * The underlying data source, for the rare test that needs a second connection of its own — lock
     * contention, for instance, which cannot be demonstrated on one connection.
     *
     * @return the shared data source
     */
    protected static DataSource dataSource() {
        return DATA_SOURCE;
    }

    /**
     * Transaction control, for testing anything whose correctness depends on transaction boundaries:
     * {@code SELECT ... FOR UPDATE}, a ledger row written atomically with the balance it describes, or
     * the fact that an append-only trigger aborts the whole transaction rather than one statement.
     *
     * @return a template over the shared data source
     */
    protected static TransactionTemplate transactions() {
        return TRANSACTIONS;
    }

    /**
     * Empties every table and restores the seeded singleton row.
     *
     * <p>The append-only triggers on {@code ledger_transactions} and {@code provenance_records} refuse
     * DELETE, deliberately. They are ROW triggers, and TRUNCATE does not fire row triggers — so this
     * harness can reset cleanly without the production schema needing an escape hatch that would then
     * exist in production too.
     */
    @BeforeEach
    protected void resetDatabase() {
        // Discovered from the catalogue, not hardcoded: a table added by a later migration gets
        // cleaned up without anyone remembering to update this method.
        //
        // information_schema, not pg_tables. The Postgres catalogue views and quote_ident() went with
        // PostgreSQL; information_schema is the SQL standard and both engines have it, so this is the
        // portable spelling rather than an H2-specific one.
        List<String> tables = JDBC_CLIENT.sql("""
                        SELECT table_name
                          FROM information_schema.tables
                         WHERE table_schema = 'public'
                           AND table_type = 'BASE TABLE'
                           AND table_name <> 'flyway_schema_history'
                         ORDER BY table_name
                        """).query(String.class).list();

        if (!tables.isEmpty()) {
            // Referential integrity is disabled for the truncation ONLY. H2 has no CASCADE on
            // TRUNCATE, so without this the order of the table list decides whether the reset works,
            // and it would break the first time a migration added a foreign key.
            JDBC_CLIENT.sql("SET REFERENTIAL_INTEGRITY FALSE").update();
            try {
                for (String table : tables) {
                    JDBC_CLIENT
                            .sql("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY")
                            .update();
                }
            } finally {
                JDBC_CLIENT.sql("SET REFERENTIAL_INTEGRITY TRUE").update();
            }
        }

        // V2 seeds this row so that "read the server state" never has to handle an absent singleton.
        // Truncation removes it, so the harness puts it back rather than leaving every test to
        // discover the difference.
        JDBC_CLIENT
                .sql("MERGE INTO server_state (only_row) KEY (only_row) VALUES (true)")
                .update();
    }
}
