package io.github.stoicswe.eyeandsickle.server;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
@Testcontainers
class ServerContextLoadsIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Federation on, so the federation migrations and beans are exercised too, not just core.
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
