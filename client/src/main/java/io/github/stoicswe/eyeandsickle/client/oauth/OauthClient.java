package io.github.stoicswe.eyeandsickle.client.oauth;

import io.github.stoicswe.eyeandsickle.protocol.identity.HttpFetcher;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AT Protocol OAuth exchanges: PAR, code-for-token, and refresh.
 *
 * <h2>The client is PUBLIC, and the identity is a URL</h2>
 *
 * Per {@code docs/architecture/10-oauth-and-did-resolution.md} §1 (Option C) the desktop client runs
 * the flow as a public client. There is no client secret and no {@code private_key_jwt}; the
 * {@code client_id} <em>is</em> the URL its metadata document is served from, and the authorization
 * server fetches it. In development that may be the literal string {@code http://localhost}, which
 * the server expands into virtual native-client metadata with loopback redirects — so the whole flow
 * is exercisable against real accounts with no domain and no certificate (§3.3).
 *
 * <h2>⚠ The DPoP nonce dance is the normal path, not an error path</h2>
 *
 * A server issues {@code DPoP-Nonce} and requires it on the next request. The <em>first</em> call to
 * any endpoint therefore comes back {@code 400 use_dpop_nonce} with the nonce attached, and is retried
 * once. A client that treats that first rejection as a failure never authenticates at all — so the
 * retry is built into {@link #withDpop}, once, rather than left to each call site to remember.
 *
 * <h2>⚠ Refresh is single-flight, and that is mandatory rather than tidy</h2>
 *
 * Refresh tokens are single-use: each exchange returns a replacement and invalidates the old one. Two
 * concurrent refreshes therefore race, and the loser's token is already dead — which ends the session
 * and looks to the player like being logged out at random. The lock is what stops that.
 */
public final class OauthClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * The only scope this game requests.
     *
     * <p>⚠ {@code atproto} grants <em>no</em> access to the account's data — it is the authentication
     * scope. {@code docs/architecture/02-identity-and-auth.md} §3 requires that this game never ask
     * for write scope, and this constant is that requirement made mechanical. ⚠ Adding
     * {@code transition:generic} would grant App-Password-equivalent breadth over the player's real
     * social account and violate §3 outright.
     */
    public static final String SCOPE = "atproto";

    /**
     * The scope that lets this client mint a service-auth token for a home server.
     *
     * <p>⚠ <strong>{@code lxm} is pinned and only {@code aud} is the wildcard.</strong> The spec
     * permits one or the other to be wildcard but not both, and this is the right way round: the
     * method is fixed at {@code getServiceAuth} forever, while the audience is a home server the
     * player has not chosen yet at sign-in time. Pinning {@code aud} instead would mean re-running
     * the whole OAuth flow every time a player tried a different server.
     *
     * <p>⚠ It grants <em>no</em> read or write access to the account's data — it authorises minting a
     * proof of identity for a third party, which is precisely what Option C needs and nothing more.
     * {@code docs/architecture/02-identity-and-auth.md} §3 holds.
     */
    public static final String SCOPE_SERVICE_AUTH = "rpc:com.atproto.server.getServiceAuth?aud=*";

    /** What this client asks for when the authorization server is new enough to grant it. */
    public static final String FULL_SCOPE = SCOPE + " " + SCOPE_SERVICE_AUTH;

    private final HttpFetcher http;
    private final String clientId;
    private final URI redirectUri;
    private final Supplier<Instant> clock;

    /** Guards refresh. See the class comment — single-use tokens make this a correctness lock. */
    private final ReentrantLock refreshLock = new ReentrantLock();

    public OauthClient(HttpFetcher http, String clientId, URI redirectUri, Supplier<Instant> clock) {
        this.http = Objects.requireNonNull(http, "http");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A live session's tokens. */
    public record Tokens(String accessToken, String refreshToken, String did, String scope, Instant expiresAt) {

        /**
         * Whether this session may mint a service-auth token, i.e. whether it can join a home server.
         *
         * <p>⚠ Read from the <strong>granted</strong> scope, never from what was requested. Granular
         * scopes are still rolling out across the atproto PDS distribution, so a server may grant a
         * narrower set than it was asked for — and a client that assumed it got what it asked for
         * would fail later, at the point of joining, with an error about the home server.
         */
        public boolean canMintServiceAuth() {
            return scope != null && java.util.Arrays.asList(scope.split(" ")).contains(SCOPE_SERVICE_AUTH);
        }
    }

    /**
     * Pushes the authorization request and returns the URL to send the player to.
     *
     * @param server the discovered authorization server
     * @param dpop this session's key
     * @param pkce this attempt's PKCE pair
     * @param state the CSRF value echoed back on the callback
     * @param loginHint the handle or DID the player named, so the server can pre-fill
     * @return the authorization URL
     */
    public URI pushAuthorizationRequest(AuthServer server, DpopKey dpop, Pkce pkce, String state, String loginHint) {
        return pushAuthorizationRequest(server, dpop, pkce, state, loginHint, FULL_SCOPE);
    }

    /**
     * As above, with an explicit scope — so a caller can retry narrower when a server refuses.
     *
     * <p>⚠ Exists because granular {@code rpc:} scopes are still rolling out: an older authorization
     * server refuses the whole request rather than granting a subset, so the caller needs a way to ask
     * for less rather than leaving the player unable to sign in at all.
     *
     * @param scope the space-separated scope to request
     */
    public URI pushAuthorizationRequest(
            AuthServer server, DpopKey dpop, Pkce pkce, String state, String loginHint, String scope) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("response_type", "code");
        form.put("redirect_uri", redirectUri.toString());
        form.put("scope", scope);
        form.put("state", state);
        form.put("code_challenge", pkce.challenge());
        form.put("code_challenge_method", "S256");
        if (loginHint != null && !loginHint.isBlank()) {
            form.put("login_hint", loginHint);
        }

        JsonNode response = withDpop(server.parEndpoint(), dpop, null, form);
        String requestUri = text(response, "request_uri");
        if (requestUri == null) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "the PAR endpoint returned no request_uri");
        }
        // ⚠ ONLY request_uri and client_id go on the authorization URL. The parameters were pushed
        // over the back channel precisely so they are not in a browser URL, a history entry or a
        // referrer — putting them here as well would give all of that back.
        return URI.create(server.authorizationEndpoint()
                + (server.authorizationEndpoint().getQuery() == null ? "?" : "&")
                + "client_id=" + encode(clientId)
                + "&request_uri=" + encode(requestUri));
    }

    /**
     * Exchanges an authorization code for tokens, and runs the mandatory checks.
     *
     * @param server the authorization server
     * @param dpop this session's key
     * @param pkce the pair whose challenge was sent
     * @param code the code from the callback
     * @param expectedDid the DID the flow was started for
     * @return the tokens
     */
    public Tokens exchangeCode(AuthServer server, DpopKey dpop, Pkce pkce, String code, String expectedDid) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("redirect_uri", redirectUri.toString());
        form.put("code", code);
        form.put("code_verifier", pkce.verifier());

        return readTokens(withDpop(server.tokenEndpoint(), dpop, null, form), expectedDid);
    }

    /**
     * Refreshes an access token. Single-flight.
     *
     * @param server the authorization server
     * @param dpop this session's key
     * @param refreshToken the current refresh token
     * @param expectedDid the account
     * @return the new tokens, including a NEW refresh token which must replace the old one
     */
    public Tokens refresh(AuthServer server, DpopKey dpop, String refreshToken, String expectedDid) {
        refreshLock.lock();
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "refresh_token");
            form.put("client_id", clientId);
            form.put("refresh_token", refreshToken);
            return readTokens(withDpop(server.tokenEndpoint(), dpop, null, form), expectedDid);
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * An authenticated {@code GET} against the player's PDS, with DPoP and the same nonce retry.
     *
     * <p>⚠ The access token is presented as {@code Authorization: DPoP <token>} — not {@code Bearer}.
     * A DPoP-bound token sent as a bearer token is refused, and the error names the scheme rather than
     * the binding, which sends the reader looking in the wrong place.
     *
     * @param endpoint the XRPC endpoint
     * @param dpop the session key
     * @param accessToken the token to present
     * @return the parsed JSON response
     */
    public JsonNode authenticatedGet(URI endpoint, DpopKey dpop, String accessToken) {
        HttpFetcher.Response response = sendGet(endpoint, dpop, accessToken);
        if (response.status() >= 400 && needsNonce(response)) {
            dpop.rememberNonce(endpoint, response.header("dpop-nonce"));
            response = sendGet(endpoint, dpop, accessToken);
        }
        if (!response.isSuccess()) {
            throw errorFrom(response, endpoint);
        }
        return parse(response.body(), endpoint);
    }

    private HttpFetcher.Response sendGet(URI endpoint, DpopKey dpop, String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("DPoP", dpop.proof("GET", endpoint, accessToken, clock.get()));
        headers.put("Authorization", "DPoP " + accessToken);
        HttpFetcher.Response response;
        try {
            response = http.send(HttpFetcher.Request.get(endpoint, "application/json", headers));
        } catch (IdentityResolutionException unreachable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unreachable.getMessage(), unreachable);
        }
        dpop.rememberNonce(endpoint, response.header("dpop-nonce"));
        return response;
    }

    /**
     * Posts a form with a DPoP proof, retrying once when the server demands a nonce.
     *
     * @param endpoint the target
     * @param dpop the session key
     * @param accessToken the token to bind the proof to, or null
     * @param form the parameters
     * @return the parsed JSON response
     */
    private JsonNode withDpop(URI endpoint, DpopKey dpop, String accessToken, Map<String, String> form) {
        HttpFetcher.Response response = post(endpoint, dpop, accessToken, form);

        if (response.status() >= 400 && needsNonce(response)) {
            // The designed handshake: the server has now told us its nonce, so the retry carries it.
            // Exactly ONE retry — a loop here against a server that always answers use_dpop_nonce
            // would spin forever on the sign-in path.
            dpop.rememberNonce(endpoint, response.header("dpop-nonce"));
            response = post(endpoint, dpop, accessToken, form);
        }
        if (!response.isSuccess()) {
            throw errorFrom(response, endpoint);
        }
        return parse(response.body(), endpoint);
    }

    private HttpFetcher.Response post(URI endpoint, DpopKey dpop, String accessToken, Map<String, String> form) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("DPoP", dpop.proof("POST", endpoint, accessToken, clock.get()));
        if (accessToken != null) {
            headers.put("Authorization", "DPoP " + accessToken);
        }
        HttpFetcher.Response response;
        try {
            response = http.send(HttpFetcher.Request.form(endpoint, encodeForm(form), headers));
        } catch (IdentityResolutionException unreachable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unreachable.getMessage(), unreachable);
        }
        // Any DPoP-Nonce the server sends is recorded whether or not this call succeeded — the server
        // rotates them on a ≤5-minute lifetime and the next request must carry the newest.
        dpop.rememberNonce(endpoint, response.header("dpop-nonce"));
        return response;
    }

    private boolean needsNonce(HttpFetcher.Response response) {
        if (response.header("dpop-nonce") == null) {
            return false;
        }
        // The body carries `error: use_dpop_nonce`. Checked as a substring rather than parsed,
        // because this is an error response and may not be the JSON we expect.
        return response.body() != null && response.body().contains("use_dpop_nonce");
    }

    /**
     * Reads a token response, and runs the two checks the spec calls critical.
     *
     * <p>⚠ Both of these are mandatory ({@code 10} §4.5) and both are easy to omit because the happy
     * path works without them. Without the {@code sub} check a malicious authorization server can
     * authenticate <em>any</em> account to this client; without the {@code scope} check the client
     * may be holding a session with permissions it never asked for.
     */
    private Tokens readTokens(JsonNode response, String expectedDid) {
        String accessToken = text(response, "access_token");
        String refreshToken = text(response, "refresh_token");
        String sub = text(response, "sub");
        String scope = text(response, "scope");

        if (accessToken == null || sub == null) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "the token response is missing access_token or sub");
        }
        if (expectedDid != null && !expectedDid.equals(sub)) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL,
                    "the token response is for '" + sub + "' but this sign-in was for '" + expectedDid + "'");
        }
        if (scope == null || !java.util.Arrays.asList(scope.split(" ")).contains(SCOPE)) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the granted scope '" + scope + "' does not include '" + SCOPE + "'");
        }
        long expiresIn = response.path("expires_in").asLong(0);
        Instant expiresAt = clock.get().plusSeconds(expiresIn > 0 ? expiresIn : 300);
        return new Tokens(accessToken, refreshToken, sub, scope, expiresAt);
    }

    private static OauthException errorFrom(HttpFetcher.Response response, URI endpoint) {
        String error = null;
        String description = null;
        try {
            JsonNode body = MAPPER.readTree(response.body());
            if (body != null && body.isObject()) {
                error = text(body, "error");
                description = text(body, "error_description");
            }
        } catch (JacksonException notJson) {
            // An error response that is not JSON is still an error; the status carries the meaning.
        }
        String detail = (error == null ? "HTTP " + response.status() : error)
                + (description == null ? "" : " — " + description);
        // 4xx is the server declining; 5xx is the server failing. The player can act on the first.
        OauthException.Kind kind =
                response.status() >= 500 ? OauthException.Kind.UNAVAILABLE : OauthException.Kind.DENIED;
        return new OauthException(kind, endpoint + " refused the request: " + detail);
    }

    private static JsonNode parse(String body, URI endpoint) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root == null || !root.isObject()) {
                throw new OauthException(OauthException.Kind.PROTOCOL, endpoint + " did not return a JSON object");
            }
            return root;
        } catch (JacksonException malformed) {
            throw new OauthException(OauthException.Kind.PROTOCOL, endpoint + " did not return valid JSON", malformed);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder out = new StringBuilder();
        form.forEach((key, value) -> {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(encode(key)).append('=').append(encode(value));
        });
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
