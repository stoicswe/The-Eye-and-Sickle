package io.github.stoicswe.eyeandsickle.client.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.identity.HttpFetcher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Minting a service-auth token, and the scope that permits it. */
class ServiceAuthAndScopeTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    private static final AuthServer SERVER = new AuthServer(
            URI.create("https://as.example"),
            URI.create("https://as.example/authorize"),
            URI.create("https://as.example/token"),
            URI.create("https://as.example/par"));

    private static final class FakeHttp implements HttpFetcher {
        final List<Request> sent = new ArrayList<>();
        final List<Response> script = new ArrayList<>();

        FakeHttp answering(int status, String body) {
            script.add(new Response(status, body, Map.of()));
            return this;
        }

        @Override
        public Response send(Request request) {
            sent.add(request);
            return script.isEmpty() ? new Response(500, "{}", Map.of()) : script.remove(0);
        }

        String bodyOf(int index) {
            return new String(sent.get(index).body(), StandardCharsets.UTF_8);
        }
    }

    private static OauthClient client(FakeHttp http) {
        return new OauthClient(http, "http://localhost", URI.create("http://127.0.0.1:9/callback"), () -> NOW);
    }

    @Nested
    @DisplayName("the requested scope")
    class Scope {

        @Test
        @DisplayName("asks for identity AND permission to prove it to a home server")
        void requestsBoth() {
            FakeHttp http = new FakeHttp().answering(200, "{\"request_uri\":\"urn:x\"}");

            client(http).pushAuthorizationRequest(SERVER, DpopKey.generate(), Pkce.generate(), "s", null);

            assertThat(http.bodyOf(0)).contains("scope=atproto").contains("getServiceAuth");
        }

        @Test
        @DisplayName("⚠ lxm is pinned and only aud is wildcard — both cannot be")
        void onlyAudienceIsWildcard() {
            // The spec permits one wildcard, not two. This is the right way round: the method is
            // fixed forever, while the home server is not chosen until after sign-in.
            assertThat(OauthClient.SCOPE_SERVICE_AUTH)
                    .isEqualTo("rpc:com.atproto.server.getServiceAuth?aud=*")
                    .doesNotContain("lxm=*");
        }

        @Test
        @DisplayName("canMintServiceAuth reads the GRANTED scope, not the requested one")
        void grantedNotRequested() {
            // Granular scopes are still rolling out, so a server may grant less than it was asked.
            OauthClient.Tokens narrow = new OauthClient.Tokens("at", "rt", "did:plc:a", "atproto", NOW);
            OauthClient.Tokens full = new OauthClient.Tokens("at", "rt", "did:plc:a", OauthClient.FULL_SCOPE, NOW);

            assertThat(narrow.canMintServiceAuth()).isFalse();
            assertThat(full.canMintServiceAuth()).isTrue();
        }
    }

    @Nested
    @DisplayName("minting")
    class Minting {

        @Test
        @DisplayName("binds the token to the home server's DID and presents DPoP, not Bearer")
        void mintsBoundToken() {
            FakeHttp http = new FakeHttp().answering(200, "{\"token\":\"the.service.jwt\"}");

            String token = ServiceAuth.mint(
                    client(http),
                    URI.create("https://pds.example"),
                    DpopKey.generate(),
                    "access-token-1",
                    "did:web:home.example");

            assertThat(token).isEqualTo("the.service.jwt");
            HttpFetcher.Request request = http.sent.get(0);
            assertThat(request.uri().toString())
                    .contains("/xrpc/com.atproto.server.getServiceAuth")
                    .contains("aud=did%3Aweb%3Ahome.example");
            // ⚠ DPoP, never Bearer. A DPoP-bound token sent as Bearer is refused with an error about
            // the scheme rather than the binding, which sends the reader to the wrong place.
            assertThat(request.headers().get("Authorization")).startsWith("DPoP ");
            assertThat(request.headers()).containsKey("DPoP");
        }

        @Test
        @DisplayName("a PDS that returns no token is a protocol failure, not an empty string")
        void missingToken() {
            FakeHttp http = new FakeHttp().answering(200, "{}");

            assertThatThrownBy(() -> ServiceAuth.mint(
                            client(http),
                            URI.create("https://pds.example"),
                            DpopKey.generate(),
                            "at",
                            "did:web:home.example"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("no service-auth token");
        }
    }

    @Nested
    @DisplayName("joining a home server")
    class Joining {

        @Test
        @DisplayName("⚠ a 403 is reported as the ALLOWLIST, because that is what it almost always is")
        void allowlistRefusalIsNamed() {
            // Home servers are closed by default (03 §1). A generic "refused" sends the player to
            // look at their account when the answer is "ask the operator".
            FakeHttp http = new FakeHttp().answering(403, "{}");

            assertThatThrownBy(() -> new HomeServerSignIn(http).signIn(URI.create("https://home.example"), "jwt"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("allowlist");
        }

        @Test
        @DisplayName("a successful sign-in returns the account and its characters")
        void returnsAccount() {
            FakeHttp http = new FakeHttp()
                    .answering(
                            200,
                            "{\"did\":\"did:plc:abc\",\"handle\":\"alice.example\",\"characters\":"
                                    + "[{\"ref\":{\"id\":\"c-1\"},\"handle\":\"ghost\",\"status\":\"ACTIVE\","
                                    + "\"faction\":\"NONE\"}]}");

            HomeServerSignIn.Account account =
                    new HomeServerSignIn(http).signIn(URI.create("https://home.example"), "jwt");

            assertThat(account.did()).isEqualTo("did:plc:abc");
            assertThat(account.characters()).hasSize(1);
            assertThat(account.characters().get(0).handle()).isEqualTo("ghost");
        }
    }
}
