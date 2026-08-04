package io.github.stoicswe.eyeandsickle.server.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document's own metadata — what a reader needs before the endpoint list means anything.
 *
 * <h2>Who this is for</h2>
 *
 * Two audiences, and they want different things. A <strong>client developer</strong> wants the wire
 * shapes and the refusal codes. An <strong>operator</strong> wants to know which endpoints are open to
 * the world, which need an allowlisted account, and which exist only in one mode — because a
 * self-hosted server is their machine and its exposed surface is their problem.
 *
 * <p>Both are served by tags and descriptions rather than by a bare schema dump. An endpoint list with
 * no statement of who may call it is a list that reads as "all of these are available", which for this
 * server is wrong about most of them.
 *
 * <h2>⚠ Generation is OFF BY DEFAULT, and that is a decision rather than caution</h2>
 *
 * See {@code application.yml}. A home server is closed by default ({@code docs/architecture/03} §1) —
 * the allowlist starts empty, LAN mode refuses to bind a public address — and switching on an
 * interactive API console for somebody else's box would sit badly beside that. The operator turns it
 * on; the spec is then at {@code /v3/api-docs} and the console at {@code /swagger-ui.html}.
 *
 * <p>⚠ <strong>The spec is still generated and CHECKED on every build</strong> even though it is not
 * served: {@code OpenApiSpecIT} boots the context with docs enabled and asserts the document covers
 * every mapped endpoint. Documentation that is only produced when somebody remembers to enable it is
 * documentation that silently falls behind the code.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    /**
     * ⚠ Read from the build rather than written here, so a spec and the build that produced it name
     * the same version. A hard-coded string is right on the day it is written and wrong from the next
     * release onwards — worse than absent, because a reader cannot tell a stale version from a
     * current one.
     *
     * <p>⚠ {@link ObjectProvider}, because {@code BuildProperties} exists only when the Boot plugin's
     * {@code build-info} goal has run. It has not during a plain {@code mvn test} or an IDE launch,
     * and a hard dependency would turn "documentation metadata is missing" into "the server does not
     * start" — which is the wrong severity by a wide margin.
     *
     * @param build the build's own properties, if the packaging step produced them
     * @return the document metadata
     */
    @Bean
    OpenAPI eyeAndSickleOpenApi(ObjectProvider<BuildProperties> build) {
        String version = build.getIfAvailable() != null ? build.getObject().getVersion() : "unversioned";
        return new OpenAPI()
                .info(new Info()
                        .title("The Eye and Sickle — home server API")
                        .version(version)
                        .description(
                                """
                                The REST surface of a self-hostable home server.

                                **The server is authoritative.** Every endpoint here decides something; \
                                the client renders the answer and decides nothing (Invariant I14). An \
                                endpoint that appears to accept a game outcome from the caller is a bug \
                                report, not a feature.

                                **The server is closed by default.** The allowlist starts empty \
                                (`EYEANDSICKLE_ALLOWLIST_DIDS`), so a correctly-configured new server \
                                refuses everybody until its operator says otherwise. A 403 from an \
                                identity endpoint almost always means the allowlist rather than a bad \
                                token.

                                **Modes are not variations, they are different servers.** A LAN server \
                                has no federation surface at all — the controllers are absent, not \
                                disabled — and a federated one has no LAN join. See the `lan` and \
                                `federation` tags.

                                **`Content-Digest` (RFC 9530) is accepted on any request and returned \
                                on any response with a body.** It catches accidental corruption early; \
                                it is not authentication, and a mismatch is a 422.\
                                """)
                        .license(new License().name("See LICENSE in the repository")))
                // ⚠ Ordered by what an operator asks first — who am I, who may join — rather than
                // alphabetically. The tag list is the table of contents.
                .tags(List.of(
                        new Tag()
                                .name("identity")
                                .description("Sign-in, this server's own identity, and character creation. "
                                        + "AT Protocol supplies a DID and nothing else; game state is never "
                                        + "read from or written to a player's PDS."),
                        new Tag()
                                .name("lan")
                                .description("LAN mode only, and ABSENT on a federated server. There is no "
                                        + "authentication here by design — the network is the trust boundary "
                                        + "— which is why the server refuses to start on a public address."),
                        new Tag()
                                .name("session")
                                .description("The game transport: one snapshot endpoint and one intent "
                                        + "endpoint, not one per method. Adding a system adds a field or a "
                                        + "sealed variant, never a controller."),
                        new Tag()
                                .name("compute")
                                .description("Read-only views of the compute ledger. A DISCREPANCY between a "
                                        + "rig's ceiling and the sum of its allocations is representable on "
                                        + "purpose — it is the signal a player hunts for."),
                        new Tag()
                                .name("federation")
                                .description("Peer discovery, the character directory and cross-server "
                                        + "migration. Present only when federation is enabled; a LAN server "
                                        + "does not register these at all.")))
                .components(new Components()
                        // ⚠ Documented as a scheme even though no SecurityFilterChain enforces it yet.
                        // The token is a real, verified AT Protocol service-auth JWT — see
                        // `identity/ServiceAuthVerifier` — and naming it here is how a client developer
                        // learns what to present. What is NOT yet true is that the framework refuses a
                        // request without one; that is CL-8's remaining wiring, and the spec must not
                        // imply otherwise.
                        .addSecuritySchemes(
                                "serviceAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("An AT Protocol inter-service auth JWT "
                                                + "(`com.atproto.server.getServiceAuth`) whose `aud` is this "
                                                + "server's own DID. The server resolves the signing key from "
                                                + "the caller's DID document and verifies it independently — "
                                                + "it never takes the client's word for who it is.")));
    }
}
