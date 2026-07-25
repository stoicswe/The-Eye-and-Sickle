package io.github.stoicswe.eyeandsickle.server.persistence;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every test that touches real SQL: one shared, Flyway-migrated PostgreSQL for the
 * whole test run.
 *
 * <h2>How to use it</h2>
 *
 * Extend it and <strong>name your class {@code SomethingIT}</strong>. Failsafe's default include
 * pattern picks up class names ending in {@code IT}, so such a class runs under
 * {@code mvn -Pit verify} and is invisible to the default {@code mvn verify}. That separation is
 * load-bearing: {@code mvn verify} must never require Docker, or a contributor who only wants to work
 * on the JavaFX client is blocked by a daemon they have no reason to run.
 *
 * <p>This class is deliberately named neither {@code ...IT} nor {@code ...Test} — it is abstract and
 * matches neither surefire's nor failsafe's include patterns, so neither runner will ever try to
 * execute it on its own.
 *
 * {@snippet lang = java:
 * class LedgerRepositoryIT extends PostgresIntegrationTestBase {
 *
 *     // annotate with org.junit.jupiter.api.Test
 *     void writesAndReadsBack() {
 *         jdbcClient().sql("INSERT INTO ...").update();
 *     }
 * }
 *}
 *
 * <h2>One container, not six</h2>
 *
 * The container starts in a static initializer and is never stopped. That is the documented
 * Testcontainers singleton pattern, and the reason is arithmetic: six test classes each with their
 * own {@code @Container} field means six PostgreSQL starts, six Flyway runs, and a test suite that
 * takes minutes instead of seconds. Testcontainers' Ryuk sidecar removes the container when the JVM
 * exits, so nothing leaks — calling {@code stop()} in a shutdown hook would only race Ryuk.
 *
 * <p>The image tag is pinned. An unpinned tag means the schema is validated against whatever
 * PostgreSQL happens to be current on the machine that ran the build, which is how a migration that
 * works locally fails in CI.
 *
 * <h2>Both migration locations, always</h2>
 *
 * Tests migrate {@code core} <em>and</em> {@code federation}, mirroring a server started with the
 * {@code federation} profile. A non-federating server runs a strict subset, so testing the superset
 * exercises both — and it is the only configuration in which a federation migration's dependency on a
 * core one (V1001 uses {@code is_did}, created in V2) is checked at all.
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
public abstract class PostgresIntegrationTestBase {

    /**
     * Pinned on purpose. Bump deliberately, and re-run the suite when you do — a migration is exactly
     * the kind of code whose behaviour can depend on the server version.
     */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

    private static final PostgreSQLContainer CONTAINER;
    private static final DataSource DATA_SOURCE;
    private static final JdbcClient JDBC_CLIENT;
    private static final TransactionTemplate TRANSACTIONS;

    static {
        CONTAINER = new PostgreSQLContainer(IMAGE)
                .withDatabaseName("eyeandsickle")
                .withUsername("eyeandsickle")
                .withPassword("eyeandsickle");
        CONTAINER.start();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(CONTAINER.getJdbcUrl());
        dataSource.setUsername(CONTAINER.getUsername());
        dataSource.setPassword(CONTAINER.getPassword());
        DATA_SOURCE = dataSource;

        // Exactly the locations application.yml configures for a federating server. Deliberately not
        // `baseline-on-migrate`: if the schema history is ever unexpected, the honest outcome is a
        // failure, not a silent baseline that pretends the earlier migrations ran.
        Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations("classpath:db/migration/core", "classpath:db/migration/federation")
                .load()
                .migrate();

        JDBC_CLIENT = JdbcClient.create(DATA_SOURCE);
        TRANSACTIONS = new TransactionTemplate(new DataSourceTransactionManager(DATA_SOURCE));
    }

    /**
     * The migrated database.
     *
     * @return a {@code JdbcClient} on the shared container
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
        // cleaned up without anyone remembering to update this method. quote_ident is applied in SQL
        // rather than trusted from Java, since these names are concatenated into a statement.
        List<String> tables = JDBC_CLIENT.sql("""
                        SELECT quote_ident(tablename)
                          FROM pg_tables
                         WHERE schemaname = current_schema()
                           AND tablename <> 'flyway_schema_history'
                         ORDER BY tablename
                        """).query(String.class).list();

        if (!tables.isEmpty()) {
            JDBC_CLIENT
                    .sql("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE")
                    .update();
        }

        // V2 seeds this row so that "read the server state" never has to handle an absent singleton.
        // Truncation removes it, so the harness puts it back rather than leaving every test to
        // discover the difference.
        JDBC_CLIENT
                .sql("INSERT INTO server_state (only_row) VALUES (true) ON CONFLICT DO NOTHING")
                .update();
    }
}
