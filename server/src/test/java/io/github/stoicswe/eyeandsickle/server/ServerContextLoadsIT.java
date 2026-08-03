package io.github.stoicswe.eyeandsickle.server;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The single most important test of "does the server actually run": it boots the whole Spring
 * context against a real, Flyway-migrated PostgreSQL and asserts that every bean wires.
 *
 * <p>Compilation proves the code is type-correct; it says nothing about whether the six slices'
 * beans satisfy each other's dependencies. Each slice defined its own seams (a DID key resolver, a
 * signing identity, a peer transport) and none could see the others, so an unsatisfied injection
 * point is exactly the failure mode a context-load test exists to catch — and the only one that
 * turns "compiles" into "starts".
 *
 * <p>Runs under {@code mvn -Pit verify} (needs Docker), so the default Docker-free build stays green.
 * The datasource is wired via {@link DynamicPropertySource} rather than {@code @ServiceConnection} so
 * no extra Boot-testcontainers dependency is required.
 */
// classes = ... is explicit rather than relying on "search upwards for @SpringBootConfiguration":
// the discovery scan behaves differently once spring-boot:repackage has rewritten the module jar in
// the package phase, before failsafe runs. Naming the application class removes that fragility.
@SpringBootTest(classes = EyeAndSickleServerApplication.class)
class ServerContextLoadsIT {

    private static final String DB_NAME = "servercontextloadsit";

    /**
     * ⚠ Embedded H2, per test class — no Docker, no container, no daemon.
     *
     * <p>The database moved from PostgreSQL to embedded H2 on 2026-08-02, and this is the payoff for
     * the test suite: these were Testcontainers tests that could only run where a Docker daemon was
     * available. They now run everywhere the build does.
     *
     * <p>⚠ A UNIQUE database name per class. H2 keeps an in-memory database alive for as long as one
     * connection is open, so two classes sharing a name would share a schema — and the first to
     * finish would drop it out from under the second. The failure is order-dependent and only shows
     * up when the suite is run in a different sequence.
     *
     * <p>⚠ {@code MODE=PostgreSQL} and {@code DATABASE_TO_LOWER} must match {@code application.yml}.
     * A test running against a different dialect than production is a test that proves nothing about
     * production.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1".formatted(DB_NAME));
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        // Federation on, so those migrations and beans are exercised too, not just core.
        registry.add("spring.profiles.active", () -> "federation");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // Reaching here at all means the ApplicationContext started: Flyway migrated the schema, and
        // every @Service across identity, compute, economy, items, federation and discovery found the
        // beans it injects. An unsatisfied dependency would have failed this before the body ran.
        assertThat(dataSource).isNotNull();
    }
}
