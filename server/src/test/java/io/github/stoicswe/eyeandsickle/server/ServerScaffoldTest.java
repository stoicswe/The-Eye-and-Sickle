package io.github.stoicswe.eyeandsickle.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

/**
 * Cheap checks that do not need a database.
 *
 * <p>There is deliberately no {@code @SpringBootTest} here yet. Booting the context would require a
 * live PostgreSQL, and the honest way to get one is Testcontainers — which needs a Docker daemon.
 * Making the default {@code mvn verify} depend on Docker would lock out anyone who only wants to
 * work on the JavaFX client. Container-backed integration tests belong in {@code src/test/java/**IT}
 * classes run by failsafe under {@code mvn -Pit verify}.
 */
class ServerScaffoldTest {

    @Test
    @DisplayName("the application class is a Spring Boot entry point")
    void applicationIsAnnotated() {
        assertThat(EyeAndSickleServerApplication.class.getAnnotation(SpringBootApplication.class))
                .as("component scanning is rooted at this class's package")
                .isNotNull();
    }

    @Test
    @DisplayName("configuration and the Flyway baseline are on the classpath")
    void configurationIsPackaged() {
        assertThat(new ClassPathResource("application.yml").exists()).isTrue();
        assertThat(new ClassPathResource("db/migration/core/V1__baseline.sql").exists())
                .as("Flyway's configured location must actually contain migrations, or "
                        + "the server fails at startup rather than at build time")
                .isTrue();
    }

    @Test
    @DisplayName("Flyway is actually auto-configured, not merely on the classpath")
    void flywayIsAutoConfigured() {
        // This guards a failure mode that is invisible to every other kind of check.
        //
        // Spring Boot 4 split spring-boot-autoconfigure into per-technology modules. Depending on
        // plain org.flywaydb:flyway-core gives you Flyway's CLASSES without its AUTO-CONFIGURATION:
        // the build is green, the app starts, every spring.flyway.* key binds to nothing, and
        // migrations silently never run. A self-hoster would find out when their database turned
        // out to be empty.
        //
        // Asserting on the auto-configuration class is what actually distinguishes the two states.
        assertThat(canLoad("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"))
                .as("spring-boot-starter-flyway must be a dependency — flyway-core alone is not enough "
                        + "on Boot 4, and the difference is invisible until migrations don't run")
                .isTrue();
        assertThat(canLoad("org.flywaydb.core.Flyway")).isTrue();
    }

    private static boolean canLoad(String className) {
        try {
            Class.forName(className, false, ServerScaffoldTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
