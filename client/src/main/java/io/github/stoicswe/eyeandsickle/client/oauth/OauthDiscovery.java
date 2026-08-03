package io.github.stoicswe.eyeandsickle.client.oauth;

import io.github.stoicswe.eyeandsickle.protocol.identity.DidDocument;
import io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.HandleResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.HttpFetcher;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Walks handle → DID → PDS → authorization server.
 *
 * <h2>The chain, and why every hop is attacker-influenced</h2>
 *
 * <ol>
 *   <li>The player types a handle. Resolve it to a DID <strong>bidirectionally</strong> — the DID
 *       document must claim the handle back ({@link HandleResolver}), or anyone could put
 *       {@code at://a-rivals.handle} in their own document and be signed in as them.
 *   <li>Read the PDS from the DID document's {@code service} array.
 *   <li>{@code GET <pds>/.well-known/oauth-protected-resource} → {@code authorization_servers[0]}.
 *   <li>{@code GET <as>/.well-known/oauth-authorization-server} → the endpoints.
 * </ol>
 *
 * <p>⚠ Every URL after step 1 comes out of a document somebody else controls, which is why all of it
 * goes through the hardened, SSRF-guarded, address-pinned fetcher and never a bare HTTP client.
 *
 * <h2>⚠ The issuer must equal the origin it was fetched from</h2>
 *
 * Without that check a hostile PDS can name any authorization server, and the {@code iss} returned on
 * the callback can then be made to agree with it — the client would be checking a value against
 * another value the same attacker chose. Anchoring the issuer to the URL the metadata actually came
 * from is what makes the later {@code iss} comparison mean anything.
 */
public final class OauthDiscovery {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpFetcher http;
    private final HandleResolver handles;
    private final DidResolver dids;

    public OauthDiscovery(HttpFetcher http, HandleResolver handles, DidResolver dids) {
        this.http = Objects.requireNonNull(http, "http");
        this.handles = Objects.requireNonNull(handles, "handles");
        this.dids = Objects.requireNonNull(dids, "dids");
    }

    /** Where a sign-in is aimed: a verified account and the server that speaks for it. */
    public record Target(String did, String handle, AuthServer authServer) {}

    /**
     * @param typedHandle what the player entered
     * @return the verified account and its authorization server
     */
    public Target forHandle(String typedHandle) {
        HandleResolver.VerifiedHandle verified;
        try {
            verified = handles.resolve(typedHandle);
        } catch (IdentityResolutionException unresolvable) {
            throw new OauthException(
                    unresolvable.kind() == IdentityResolutionException.Kind.UNAVAILABLE
                            ? OauthException.Kind.UNAVAILABLE
                            : OauthException.Kind.DENIED,
                    unresolvable.getMessage(),
                    unresolvable);
        }
        return new Target(verified.did(), verified.handle(), authServerFor(verified.did()));
    }

    /**
     * @param did an already-verified DID
     * @return its authorization server
     */
    public AuthServer authServerFor(String did) {
        DidDocument document;
        try {
            document = dids.resolve(did);
        } catch (IdentityResolutionException unresolvable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unresolvable.getMessage(), unresolvable);
        }
        String pds = document.pdsEndpoint();
        if (pds == null) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "the DID document for " + did + " declares no PDS");
        }
        URI issuer = authorizationServerOf(URI.create(pds));
        return metadataOf(issuer);
    }

    /**
     * Finds the authorization server for a PDS the player named directly.
     *
     * <p>⚠ Used when sign-in starts from a <em>server</em> rather than a handle — a self-hosted PDS,
     * or a player who knows their server but not the exact spelling of their handle. The account is
     * unknown at this point, so {@code SignInFlow} must make the substitute check the spec requires
     * once a {@code sub} comes back.
     *
     * @param host a hostname such as {@code bsky.social}, with or without a scheme
     * @return its authorization server
     */
    public AuthServer authServerForPds(String host) {
        String trimmed = host == null ? "" : host.trim();
        if (trimmed.isBlank()) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "no server given");
        }
        // Players type "bsky.social", not "https://bsky.social". Accepting either and normalising
        // here is cheaper than a validation message about a scheme nobody thinks about.
        String url = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        return metadataOf(authorizationServerOf(URI.create(url)));
    }

    /**
     * The verified handle for a DID, or null if none verifies.
     *
     * @param did an authenticated DID
     * @return the handle, or null
     */
    public String verifiedHandleFor(String did) {
        try {
            return handles.verifiedHandleFor(did);
        } catch (IdentityResolutionException unresolvable) {
            // A lapsed handle is not a failed sign-in. The DID is the identity.
            return null;
        }
    }

    /**
     * The PDS a DID's document declares.
     *
     * @param did an authenticated DID
     * @return its PDS endpoint
     */
    public URI pdsFor(String did) {
        try {
            String pds = dids.resolve(did).pdsEndpoint();
            if (pds == null) {
                throw new OauthException(
                        OauthException.Kind.PROTOCOL, "the DID document for " + did + " declares no PDS");
            }
            return URI.create(pds);
        } catch (IdentityResolutionException unresolvable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unresolvable.getMessage(), unresolvable);
        }
    }

    private URI authorizationServerOf(URI pds) {
        JsonNode root = fetchJson(pds.resolve("/.well-known/oauth-protected-resource"));
        JsonNode servers = root.get("authorization_servers");
        if (servers == null || !servers.isArray() || servers.isEmpty()) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the PDS at " + pds + " names no authorization server");
        }
        JsonNode first = servers.get(0);
        if (!first.isString()) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "malformed authorization_servers entry");
        }
        return URI.create(first.stringValue());
    }

    private AuthServer metadataOf(URI issuerOrigin) {
        URI metadataUrl = issuerOrigin.resolve("/.well-known/oauth-authorization-server");
        JsonNode root = fetchJson(metadataUrl);

        String issuer = text(root, "issuer");
        if (issuer == null) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "authorization server metadata has no issuer");
        }
        // ⚠ The anchor. See the class comment — everything the client later checks `iss` against
        // depends on the issuer being tied to where the document actually came from.
        if (!issuer.equals(trimSlash(issuerOrigin.toString()))) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL,
                    "authorization server metadata claims issuer '" + issuer + "' but was fetched from '" + issuerOrigin
                            + "'");
        }

        AuthServer.verify(
                root.path("require_pushed_authorization_requests").asBoolean(false),
                strings(root, "code_challenge_methods_supported"),
                strings(root, "dpop_signing_alg_values_supported"),
                strings(root, "scopes_supported"));

        return new AuthServer(
                URI.create(issuer),
                required(root, "authorization_endpoint"),
                required(root, "token_endpoint"),
                required(root, "pushed_authorization_request_endpoint"));
    }

    private JsonNode fetchJson(URI url) {
        HttpFetcher.Response response;
        try {
            response = http.get(url, "application/json");
        } catch (IdentityResolutionException unreachable) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, unreachable.getMessage(), unreachable);
        }
        if (!response.isSuccess()) {
            throw new OauthException(
                    OauthException.Kind.UNAVAILABLE, "fetching " + url + " returned HTTP " + response.status());
        }
        try {
            JsonNode root = MAPPER.readTree(response.body());
            if (root == null || !root.isObject()) {
                throw new OauthException(OauthException.Kind.PROTOCOL, url + " did not return a JSON object");
            }
            return root;
        } catch (JacksonException malformed) {
            throw new OauthException(OauthException.Kind.PROTOCOL, url + " did not return valid JSON", malformed);
        }
    }

    private static URI required(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "authorization server metadata has no '" + field + "'");
        }
        return URI.create(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        JsonNode array = node.get(field);
        if (array != null && array.isArray()) {
            array.forEach(entry -> {
                if (entry.isString()) {
                    out.add(entry.stringValue());
                }
            });
        }
        return out;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
