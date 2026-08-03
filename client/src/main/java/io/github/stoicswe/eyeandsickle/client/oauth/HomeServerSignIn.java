package io.github.stoicswe.eyeandsickle.client.oauth;

import io.github.stoicswe.eyeandsickle.protocol.identity.HttpFetcher;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Presents a service-auth token to a home server and receives the account back.
 *
 * <p>The last hop of {@code docs/architecture/10-oauth-and-did-resolution.md} §1: the client has
 * minted a proof bound to <em>this</em> server's DID ({@link ServiceAuth}), and the server verifies
 * the signature against a DID document it resolves itself. Nothing the client says is trusted; what
 * crosses is one short-lived assertion.
 *
 * <h2>⚠ The home server's DID has to come from the home server, and that is a real gap</h2>
 *
 * A service-auth token is bound to an audience, so the client must know the server's DID <em>before</em>
 * minting one. Today it asks the server, which means the server names its own audience — so a hostile
 * server could name somebody else's DID and collect a token minted for them.
 *
 * <p>It cannot use that token: {@code aud} is checked against the <em>receiving</em> server's own DID,
 * so a token for {@code did:web:a} presented at {@code did:web:b} is refused by b. The residual is
 * narrower but real — a hostile server can induce a client to mint a token for a third party, then
 * relay it there. ⚠ Closing it needs the DID to arrive from somewhere the server does not control:
 * the signed descriptor of `08`, or a discovery list (`11`). Recorded rather than papered over, and it
 * is why {@link #discoverDid} is a separate, documented step instead of an inline field read.
 */
public final class HomeServerSignIn {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpFetcher http;

    public HomeServerSignIn(HttpFetcher http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /** What a home server said about this account. */
    public record Account(String did, String handle, List<Character> characters) {}

    /** One playable character. */
    public record Character(String id, String handle, String status, String faction) {}

    /**
     * Asks a home server which DID it is.
     *
     * <p>⚠ Self-asserted — see the class note. Kept as its own method so the day a signed descriptor
     * or a discovery list can supply it, there is one call site to repoint.
     *
     * @param server the home server's base URL
     * @return the DID it claims
     */
    public String discoverDid(URI server) {
        JsonNode root = getJson(server.resolve("/api/server"));
        JsonNode did = root.get("did");
        if (did == null || !did.isString()) {
            throw new OauthException(OauthException.Kind.PROTOCOL, server + " did not say which DID it is");
        }
        return did.stringValue();
    }

    /**
     * Signs in to a home server.
     *
     * @param server the home server's base URL
     * @param serviceAuthToken a token minted for that server's DID
     * @return the account and its characters
     */
    public Account signIn(URI server, String serviceAuthToken) {
        String body = "{\"serviceAuthToken\":\"" + serviceAuthToken.replace("\"", "\\\"") + "\"}";
        HttpFetcher.Response response;
        try {
            response = http.send(new HttpFetcher.Request(
                    "POST",
                    server.resolve("/api/sign-in"),
                    "application/json",
                    "application/json",
                    body.getBytes(StandardCharsets.UTF_8),
                    Map.of()));
        } catch (IdentityResolutionException unreachable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unreachable.getMessage(), unreachable);
        }
        if (response.status() == 403) {
            // ⚠ Named specifically. Home servers are closed by default (03 §1), so "not on the
            // allowlist" is the SINGLE most likely outcome of joining a server you were not invited
            // to — and reporting it as a generic refusal would send the player looking at their
            // account instead of asking the operator.
            throw new OauthException(
                    OauthException.Kind.DENIED,
                    "This server has not added you to its allowlist. Ask its operator to let you in.");
        }
        if (!response.isSuccess()) {
            throw new OauthException(
                    OauthException.Kind.DENIED, server + " refused the sign-in (HTTP " + response.status() + ")");
        }
        JsonNode root = parse(response.body(), server);
        return new Account(text(root, "did"), text(root, "handle"), characters(root));
    }

    private static List<Character> characters(JsonNode root) {
        JsonNode array = root.get("characters");
        if (array == null || !array.isArray()) {
            return List.of();
        }
        return array.valueStream()
                .filter(JsonNode::isObject)
                .map(node -> new Character(
                        node.path("ref").path("id").asString(""),
                        text(node, "handle"),
                        text(node, "status"),
                        text(node, "faction")))
                .toList();
    }

    private JsonNode getJson(URI url) {
        HttpFetcher.Response response;
        try {
            response = http.get(url, "application/json");
        } catch (IdentityResolutionException unreachable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unreachable.getMessage(), unreachable);
        }
        if (!response.isSuccess()) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, url + " returned HTTP " + response.status());
        }
        return parse(response.body(), url);
    }

    private static JsonNode parse(String body, URI source) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root == null || !root.isObject()) {
                throw new OauthException(OauthException.Kind.PROTOCOL, source + " did not return a JSON object");
            }
            return root;
        } catch (JacksonException malformed) {
            throw new OauthException(OauthException.Kind.PROTOCOL, source + " did not return valid JSON", malformed);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }
}
