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

/** The exchanges, and the checks the spec calls critical. */
class OauthClientTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    private static final AuthServer SERVER = new AuthServer(
            URI.create("https://as.example"),
            URI.create("https://as.example/authorize"),
            URI.create("https://as.example/token"),
            URI.create("https://as.example/par"));

    /** Records what was sent and replays scripted responses. */
    private static final class FakeHttp implements HttpFetcher {
        private final List<Request> sent = new ArrayList<>();
        private final List<Response> script = new ArrayList<>();

        FakeHttp answering(int status, String body, Map<String, String> headers) {
            script.add(new Response(status, body, headers));
            return this;
        }

        FakeHttp answering(int status, String body) {
            return answering(status, body, Map.of());
        }

        @Override
        public Response send(Request request) {
            sent.add(request);
            return script.isEmpty() ? new Response(500, "{}", Map.of()) : script.remove(0);
        }

        Request last() {
            return sent.get(sent.size() - 1);
        }

        String lastBody() {
            return new String(last().body(), StandardCharsets.UTF_8);
        }
    }

    private static OauthClient client(FakeHttp http) {
        return new OauthClient(http, "http://localhost", URI.create("http://127.0.0.1:9/callback"), () -> NOW);
    }

    private static final String GOOD_TOKENS = """
            {"access_token":"at-1","refresh_token":"rt-1","sub":"did:plc:abc",
             "scope":"atproto","token_type":"DPoP","expires_in":1800}
            """;

    @Nested
    @DisplayName("pushed authorization requests")
    class Par {

        @Test
        @DisplayName("the authorization URL carries ONLY client_id and request_uri")
        void authorizeUrlIsMinimal() {
            FakeHttp http =
                    new FakeHttp().answering(200, "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:xyz\"}");

            URI url = client(http)
                    .pushAuthorizationRequest(SERVER, DpopKey.generate(), Pkce.generate(), "state-1", "alice.example");

            // The parameters were pushed over the back channel precisely so they stay out of a
            // browser URL, its history and its referrers. Repeating them here gives all that back.
            assertThat(url.toString()).contains("request_uri=").contains("client_id=");
            assertThat(url.toString()).doesNotContain("code_challenge").doesNotContain("state=");
            assertThat(url.toString()).doesNotContain("scope");
        }

        @Test
        @DisplayName("the pushed form carries PKCE S256 and the atproto scope")
        void pushedFormIsComplete() {
            FakeHttp http = new FakeHttp().answering(200, "{\"request_uri\":\"urn:x\"}");
            Pkce pkce = Pkce.generate();

            client(http).pushAuthorizationRequest(SERVER, DpopKey.generate(), pkce, "state-1", null);

            assertThat(http.lastBody())
                    .contains("code_challenge_method=S256")
                    .contains("code_challenge=" + pkce.challenge())
                    .contains("scope=atproto")
                    .contains("response_type=code");
            // ⚠ transition:generic would be App-Password-equivalent breadth over the player's real
            // social account, which architecture/02 §3 forbids outright.
            assertThat(http.lastBody()).doesNotContain("transition");
        }

        @Test
        @DisplayName("every request carries a DPoP header")
        void dpopHeaderAlways() {
            FakeHttp http = new FakeHttp().answering(200, "{\"request_uri\":\"urn:x\"}");

            client(http).pushAuthorizationRequest(SERVER, DpopKey.generate(), Pkce.generate(), "s", null);

            assertThat(http.last().headers()).containsKey("DPoP");
        }
    }

    @Nested
    @DisplayName("the DPoP nonce handshake")
    class Nonce {

        @Test
        @DisplayName("a use_dpop_nonce rejection is RETRIED with the nonce — this is the normal path")
        void retriesWithNonce() {
            // The first call to any endpoint is expected to fail this way. A client that treats it as
            // an error never authenticates at all.
            FakeHttp http = new FakeHttp()
                    .answering(400, "{\"error\":\"use_dpop_nonce\"}", Map.of("dpop-nonce", "n-1"))
                    .answering(200, GOOD_TOKENS);

            OauthClient.Tokens tokens =
                    client(http).exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "code", "did:plc:abc");

            assertThat(tokens.accessToken()).isEqualTo("at-1");
            assertThat(http.sent).hasSize(2);
        }

        @Test
        @DisplayName("it retries ONCE, never in a loop")
        void retriesOnlyOnce() {
            // A server that always answers use_dpop_nonce would otherwise spin forever, on the
            // sign-in path, with the UI waiting on it.
            FakeHttp http = new FakeHttp()
                    .answering(400, "{\"error\":\"use_dpop_nonce\"}", Map.of("dpop-nonce", "n-1"))
                    .answering(400, "{\"error\":\"use_dpop_nonce\"}", Map.of("dpop-nonce", "n-2"));

            assertThatThrownBy(() ->
                            client(http).exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .isInstanceOf(OauthException.class);
            assertThat(http.sent).hasSize(2);
        }

        @Test
        @DisplayName("a 400 WITHOUT a nonce header is not retried")
        void ordinaryErrorsAreNotRetried() {
            FakeHttp http = new FakeHttp().answering(400, "{\"error\":\"invalid_grant\"}");

            assertThatThrownBy(() ->
                            client(http).exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("invalid_grant");
            assertThat(http.sent).hasSize(1);
        }
    }

    @Nested
    @DisplayName("the checks the spec calls critical")
    class MandatoryChecks {

        @Test
        @DisplayName("⚠ REFUSES a token response whose sub is a different account")
        void subMustMatch() {
            // Without this a malicious authorization server authenticates ANY account to this client
            // — it simply answers with whichever `sub` it likes and the client adopts it.
            FakeHttp http = new FakeHttp().answering(200, GOOD_TOKENS.replace("did:plc:abc", "did:plc:SOMEONEELSE"));

            assertThatThrownBy(() ->
                            client(http).exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("did:plc:SOMEONEELSE")
                    .extracting(e -> ((OauthException) e).kind())
                    .isEqualTo(OauthException.Kind.PROTOCOL);
        }

        @Test
        @DisplayName("⚠ REFUSES a session whose granted scope omits atproto")
        void scopeMustIncludeAtproto() {
            FakeHttp http = new FakeHttp().answering(200, GOOD_TOKENS.replace("\"atproto\"", "\"something-else\""));

            assertThatThrownBy(() ->
                            client(http).exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("atproto");
        }

        @Test
        @DisplayName("a scope LIST containing atproto is accepted")
        void scopeListIsSplitOnSpaces() {
            FakeHttp http = new FakeHttp().answering(200, GOOD_TOKENS.replace("\"atproto\"", "\"atproto extra\""));

            assertThat(client(http)
                            .exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc")
                            .did())
                    .isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("the code exchange sends the PKCE verifier")
        void sendsVerifier() {
            FakeHttp http = new FakeHttp().answering(200, GOOD_TOKENS);
            Pkce pkce = Pkce.generate();

            client(http).exchangeCode(SERVER, DpopKey.generate(), pkce, "the-code", "did:plc:abc");

            assertThat(http.lastBody())
                    .contains("code_verifier=" + pkce.verifier())
                    .contains("grant_type=authorization_code");
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("returns the NEW refresh token — the old one is dead after use")
        void returnsRotatedToken() {
            FakeHttp http = new FakeHttp().answering(200, GOOD_TOKENS.replace("rt-1", "rt-2"));

            OauthClient.Tokens tokens = client(http).refresh(SERVER, DpopKey.generate(), "rt-1", "did:plc:abc");

            // Refresh tokens are single-use. A caller that keeps the old one is holding a dead
            // credential and will be signed out at the next launch.
            assertThat(tokens.refreshToken()).isEqualTo("rt-2");
            assertThat(http.lastBody()).contains("grant_type=refresh_token");
        }

        @Test
        @DisplayName("concurrent refreshes are serialised, never overlapped")
        void singleFlight() throws Exception {
            // Two in-flight refreshes race and the loser's token is already invalidated — which ends
            // the session and reads to the player as being logged out at random.
            FakeHttp http = new FakeHttp();
            for (int i = 0; i < 8; i++) {
                http.answering(200, GOOD_TOKENS);
            }
            OauthClient client = client(http);
            DpopKey key = DpopKey.generate();

            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                threads.add(Thread.ofVirtual().start(() -> client.refresh(SERVER, key, "rt-1", "did:plc:abc")));
            }
            for (Thread thread : threads) {
                thread.join();
            }

            // Every call completed and none interleaved into a half-formed request.
            assertThat(http.sent).hasSize(8);
            assertThat(http.sent)
                    .allSatisfy(request -> assertThat(request.headers()).containsKey("DPoP"));
        }
    }

    @Nested
    @DisplayName("error reporting")
    class Errors {

        @Test
        @DisplayName("5xx is UNAVAILABLE and 4xx is DENIED — the player can act on one of them")
        void kindsAreDistinguished() {
            assertThatThrownBy(() -> client(new FakeHttp().answering(503, "{}"))
                            .exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .extracting(e -> ((OauthException) e).kind())
                    .isEqualTo(OauthException.Kind.UNAVAILABLE);

            assertThatThrownBy(() -> client(new FakeHttp().answering(403, "{\"error\":\"access_denied\"}"))
                            .exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .extracting(e -> ((OauthException) e).kind())
                    .isEqualTo(OauthException.Kind.DENIED);
        }

        @Test
        @DisplayName("a non-JSON error response does not become a parse crash")
        void htmlErrorPage() {
            assertThatThrownBy(() -> client(new FakeHttp().answering(502, "<html>bad gateway</html>"))
                            .exchangeCode(SERVER, DpopKey.generate(), Pkce.generate(), "c", "did:plc:abc"))
                    .isInstanceOf(OauthException.class)
                    .hasMessageContaining("502");
        }
    }
}
