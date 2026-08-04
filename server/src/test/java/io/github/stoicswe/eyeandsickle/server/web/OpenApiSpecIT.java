package io.github.stoicswe.eyeandsickle.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.server.EyeAndSickleServerApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates the OpenAPI document and proves it describes the API that actually exists.
 *
 * <h2>⚠ Why this is a TEST and not a build step</h2>
 *
 * Adding springdoc to the classpath produces a spec; nothing about that makes the spec <em>true</em>.
 * The failure this guards is quiet and normal: somebody adds a controller, the spec silently does not
 * grow, and the document stays confidently wrong until a client developer builds against it. So the
 * check runs both directions — every mapped endpoint appears in the document, and every documented
 * path is really mapped.
 *
 * <p>⚠ It also writes the spec to {@code target/openapi.json}. That is the artifact an operator or a
 * client developer wants, and producing it from the same run that verifies it means the file on disk
 * cannot describe a different build from the one that was checked.
 *
 * <h2>⚠ Docs are enabled HERE and nowhere else</h2>
 *
 * {@code application.yml} ships them off — a home server is closed by default and an interactive API
 * console is not something to switch on for somebody else's machine. This test turns them on for
 * itself, which is what lets the shipped default stay closed <em>and</em> the spec stay checked. A
 * suite that could only verify the docs by shipping them enabled would have made that a false choice.
 */
@SpringBootTest(classes = EyeAndSickleServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// ⚠ @ActiveProfiles, NOT registry.add("spring.profiles.active", …) in @DynamicPropertySource.
// Profiles are resolved while the Environment is being prepared, BEFORE dynamic properties are
// contributed, so the registry form is accepted, ignored, and reported by Boot as "No active profile
// set, falling back to 1 default profile" — a line nobody reads in a passing test. The federation
// controllers would then be absent and this spec check would pass over a spec missing a whole tag.
@ActiveProfiles("federation")
class OpenApiSpecIT {

    private static final String DB_NAME = "openapispecit";

    /** Where the generated spec lands, for anyone who wants to read or publish it. */
    private static final Path SPEC_FILE = Path.of("target", "openapi.json");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // ⚠ A unique in-memory database per test class. H2 keeps one alive while a connection is
        // open, so two classes sharing a name share a schema and the first to finish drops it out
        // from under the second — an order-dependent failure that hides until the suite is reordered.
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1".formatted(DB_NAME));
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("springdoc.api-docs.enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    /**
     * ⚠ Qualified by name. The actuator contributes a second {@code RequestMappingHandlerMapping}
     * ({@code controllerEndpointHandlerMapping}) for its own endpoints, so an unqualified injection
     * is ambiguous and the context fails to prepare the test — and the one wanted here is the
     * application's, not the actuator's.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("⚠ the spec describes every endpoint that exists, and no endpoint that does not")
    void specCoversTheRealApi() throws Exception {
        String json = RestClient.create()
                .get()
                .uri("http://localhost:%d/v3/api-docs".formatted(port))
                .retrieve()
                .body(String.class);
        assertThat(json).as("the docs endpoint answered nothing").isNotBlank();

        Files.createDirectories(SPEC_FILE.getParent());
        Files.writeString(SPEC_FILE, json, StandardCharsets.UTF_8);

        JsonNode spec = JsonMapper.builder().build().readTree(json);
        Set<String> documented = new TreeSet<>();
        documented.addAll(spec.get("paths").propertyNames());

        assertThat(documented).as("the document has no paths at all").isNotEmpty();
        assertThat(mappedPaths())
                .as("⚠ every mapped endpoint must be documented — a controller added without the spec "
                        + "growing leaves the document confidently wrong, and nothing else would say so")
                .allSatisfy(mapped -> assertThat(documented)
                        .as("%s is mapped but absent from the spec", mapped)
                        .anySatisfy(doc -> assertThat(matches(doc, mapped)).isTrue()));

        assertThat(documented)
                .as("⚠ and no documented path may be imaginary — a spec that promises an endpoint the "
                        + "server does not serve sends a client developer to build against nothing")
                .allSatisfy(doc -> assertThat(mappedPaths())
                        .as("%s is documented but not mapped", doc)
                        .anySatisfy(mapped -> assertThat(matches(doc, mapped)).isTrue()));
    }

    @Test
    @DisplayName("the document carries the metadata that makes an endpoint list readable")
    void specCarriesItsOwnContext() throws Exception {
        JsonNode spec = JsonMapper.builder().build().readTree(Files.exists(SPEC_FILE)
                ? Files.readString(SPEC_FILE)
                : RestClient.create()
                        .get()
                        .uri("http://localhost:%d/v3/api-docs".formatted(port))
                        .retrieve()
                        .body(String.class));

        // A bare schema dump is not documentation. These are the four things a reader needs before
        // the endpoint list means anything: what this is, which build, how to authenticate, and which
        // endpoints belong to which mode.
        assertThat(spec.get("info").get("title").asString()).contains("Eye and Sickle");
        assertThat(spec.get("info").get("version").asString()).isNotBlank();
        assertThat(spec.get("components").get("securitySchemes").propertyNames()).contains("serviceAuth");

        Set<String> tags = new LinkedHashSet<>();
        spec.get("tags").forEach(tag -> tags.add(tag.get("name").asString()));
        assertThat(tags).contains("identity", "session", "lan", "compute", "federation");
    }

    /**
     * Every path Spring actually serves, minus the framework's own.
     *
     * <p>⚠ Read from {@link RequestMappingHandlerMapping} rather than from a hand-kept list, for the
     * same reason the rig monitor's legend walks its enum: a list somebody has to remember to update
     * is a list that documents the API as it was when they last remembered.
     */
    private Set<String> mappedPaths() {
        Set<String> paths = new TreeSet<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            String type = method.getBeanType().getName();
            // springdoc's own controllers and Boot's actuator are framework surface, not this API.
            if (type.startsWith("org.springdoc") || type.startsWith("org.springframework")) {
                continue;
            }
            info.getPathPatternsCondition().getPatternValues().forEach(paths::add);
        }
        return paths;
    }

    /**
     * ⚠ OpenAPI writes a path variable as {@code {characterId}} and so does Spring, but the NAMES need
     * not agree — Spring accepts {@code {id:[0-9a-f-]+}} and other forms springdoc normalises away.
     * Comparing with every variable reduced to a placeholder compares the SHAPE, which is the thing
     * that has to match; comparing the strings would fail on a regex nobody changed the meaning of.
     */
    private static boolean matches(String documented, String mapped) {
        return normalise(documented).equals(normalise(mapped));
    }

    private static String normalise(String path) {
        return path.replaceAll("\\{[^}]*}", "{}");
    }
}
