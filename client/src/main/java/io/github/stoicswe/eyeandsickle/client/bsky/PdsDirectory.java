package io.github.stoicswe.eyeandsickle.client.bsky;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Finds which server actually hosts an account: handle → DID → PDS.
 *
 * <h2>⚠ THIS EXISTS BECAUSE {@code bsky.social} IS NOT A PDS, AND ASSUMING IT WAS BROKE DIRECT
 * MESSAGES OUTRIGHT</h2>
 *
 * The client signed in against a hard-coded {@code https://bsky.social} and sent every later call
 * there too. Sign-in worked — which is exactly what made the bug so hard to read — and then every
 * {@code chat.bsky.*} call came back <b>501 MethodNotImplemented</b>.
 *
 * <p>{@code bsky.social} is the <b>entryway</b>. It fronts account and session methods for every
 * Bluesky-hosted account, so {@code com.atproto.server.createSession} succeeds there for anybody. It
 * is not the machine holding the repository, and it does not pipethrough {@code chat.bsky.*}. The
 * real host is named in the account's DID document — measured, for the account this was found on:
 *
 * <pre>{@code
 * stoicswe.com → did:plc:zczf6tbnu4prqmdtj2hemgqu → https://leccinum.us-west.host.bsky.network
 * }</pre>
 *
 * <p>⚠ <b>501 is the signature of an unrouted method, and it is worth knowing what it rules out.</b>
 * Probed against the live services while diagnosing this: {@code api.bsky.chat} answers <b>401</b> for
 * {@code getLog} (the method exists, auth is missing) and <b>501</b> for a method that does not exist
 * at all. So a 501 never meant a wrong header, a wrong scope or a missing parameter — it meant the
 * server that answered had never heard of the method and was not forwarding it. Only the host was
 * wrong.
 *
 * <h2>⚠ RESOLUTION HAPPENS BEFORE THE PASSWORD IS SENT, AND THAT ORDER IS THE POINT</h2>
 *
 * The cheaper fix was to keep signing in at the entryway and adopt the {@code didDoc} that
 * {@code createSession} returns — which is free, and is what {@code @atproto/api} does. It is kept as
 * a second correction below. But on its own it means <b>a self-hosting player's app password is sent
 * to Bluesky</b> before anyone discovers their account is not there. A password belongs to exactly one
 * server, so the host is settled first, out of public data, and the credential is posted only to the
 * machine that is supposed to hold it.
 *
 * <p>The cost is two extra requests, <b>once per sign-in</b> — never per poll — and they carry only a
 * handle and a DID, both public by construction.
 */
public final class PdsDirectory {

    private static final Logger LOG = Logger.getLogger(PdsDirectory.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Where a handle is turned into a DID.
     *
     * <p>⚠ The entryway is the right host for <b>this one call</b> even though it is the wrong host
     * for everything else. {@code com.atproto.identity.resolveHandle} runs the full network
     * resolution, so it answers for self-hosted handles too — verified against {@code pfrazee.com},
     * which is not a Bluesky-hosted account and resolves here correctly. The alternative is DNS plus
     * a well-known fetch against a domain the player typed, which is more moving parts and more
     * outbound hosts to reach the same public fact.
     */
    private final String resolver;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // ⚠ Same rule as BlueskyChat: a redirect would move an identity lookup to a host nobody
            // chose, and the endpoint it yields is where a password is about to be posted.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public PdsDirectory(String resolver) {
        this.resolver = resolver == null || resolver.isBlank() ? BlueskyChat.DEFAULT_PDS : resolver.strip();
    }

    /**
     * The PDS hosting {@code identifier}, or empty if it cannot be worked out.
     *
     * <p>⚠ Empty is a <b>soft</b> failure and the caller falls back to the entryway. A player whose
     * DNS is briefly unhappy should get the old behaviour — which works for the common case — rather
     * than a sign-in that refuses to be attempted at all.
     *
     * @param identifier a handle ({@code alice.bsky.social}, {@code example.com}) or a bare DID
     */
    public Optional<String> resolve(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String id = identifier.strip();
        // ⚠ A leading @ is what a player types, because that is how a handle is written everywhere
        // else. Left in, it resolves to nothing and the failure looks like a wrong handle.
        if (id.startsWith("@")) {
            id = id.substring(1);
        }
        Optional<String> did = id.startsWith("did:") ? Optional.of(id) : resolveHandle(id);
        if (did.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> pds =
                didDocumentUrl(did.get()).flatMap(this::fetchDidDocument).flatMap(PdsDirectory::pdsFromDidDocument);
        pds.ifPresent(host -> LOG.log(Level.INFO, "bluesky: {0} is hosted at {1}", new Object[] {did.get(), host}));
        if (pds.isEmpty()) {
            LOG.log(Level.WARNING, "bluesky: no PDS endpoint in the DID document for {0}", did.get());
        }
        return pds;
    }

    /** handle → DID, via the resolver. Public data both ends; no credential is involved. */
    private Optional<String> resolveHandle(String handle) {
        String url = resolver + "/xrpc/com.atproto.identity.resolveHandle?handle="
                + URLEncoder.encode(handle, StandardCharsets.UTF_8);
        return fetch(url, "resolveHandle")
                .map(node -> node.path("did").asText(""))
                .filter(did -> did.startsWith("did:"));
    }

    private Optional<JsonNode> fetchDidDocument(String url) {
        return fetch(url, "did document");
    }

    private Optional<JsonNode> fetch(String url, String what) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.log(Level.WARNING, "bluesky: {0} lookup returned {1}", new Object[] {what, response.statusCode()});
                return Optional.empty();
            }
            return Optional.of(JSON.readTree(response.body()));
        } catch (Exception unreachable) {
            // ⚠ FINE, not WARNING. This is a best-effort improvement on a working default, and a
            // player offline at launch should not get a stack trace about a lookup that was only
            // ever going to refine a host they were about to use anyway.
            LOG.log(Level.FINE, "bluesky: " + what + " lookup failed", unreachable);
            return Optional.empty();
        }
    }

    /**
     * The URL a DID document is read from.
     *
     * <p>⚠ Two DID methods and no others, because those are the two AT Protocol defines. Anything
     * else is refused rather than guessed at — a wrong guess here posts a password somewhere.
     *
     * <ul>
     *   <li>{@code did:plc:xxx} → the PLC directory, which is the registry for that method.
     *   <li>{@code did:web:example.com} → {@code https://example.com/.well-known/did.json}, and with
     *       further colon-separated segments those become path elements, per the did:web method. ⚠ A
     *       colon in a host (a port) arrives percent-encoded and is decoded back, which is the one
     *       part of that spec it is easy to drop.
     * </ul>
     */
    static Optional<String> didDocumentUrl(String did) {
        if (did == null) {
            return Optional.empty();
        }
        if (did.startsWith("did:plc:") && did.length() > "did:plc:".length()) {
            return Optional.of("https://plc.directory/" + did);
        }
        if (did.startsWith("did:web:")) {
            String[] parts = did.substring("did:web:".length()).split(":");
            if (parts.length == 0 || parts[0].isBlank()) {
                return Optional.empty();
            }
            StringBuilder url =
                    new StringBuilder("https://").append(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
            for (int i = 1; i < parts.length; i++) {
                url.append('/').append(parts[i]);
            }
            url.append(parts.length == 1 ? "/.well-known/did.json" : "/did.json");
            return Optional.of(url.toString());
        }
        return Optional.empty();
    }

    /**
     * The PDS endpoint out of a DID document.
     *
     * <h2>⚠ MATCHED ON THE SERVICE, NEVER TAKEN AS "THE FIRST ONE"</h2>
     *
     * A DID document's {@code service} array is a list of <em>different</em> services — a labeler and
     * a feed generator sit in exactly the same array. Taking {@code service[0].serviceEndpoint}
     * happens to work for a plain Bluesky account today, and silently points the client at a labeler
     * for anybody who has ever run one. The entry is identified by {@code #atproto_pds}, per the
     * specification, with the {@code AtprotoPersonalDataServer} type accepted as well.
     */
    static Optional<String> pdsFromDidDocument(JsonNode document) {
        if (document == null || !document.isObject()) {
            return Optional.empty();
        }
        for (JsonNode service : document.path("service")) {
            String id = service.path("id").asText("");
            String type = service.path("type").asText("");
            if (!id.endsWith("#atproto_pds") && !"AtprotoPersonalDataServer".equals(type)) {
                continue;
            }
            String endpoint = service.path("serviceEndpoint").asText("").strip();
            if (usableEndpoint(endpoint)) {
                // ⚠ Trailing slash removed, because every caller appends "/xrpc/..." and a document
                // is free to include one. Two slashes are not always tolerated and the resulting
                // 404 names a method rather than a URL.
                return Optional.of(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether an endpoint out of a DID document may be used.
     *
     * <h2>⚠ THIS IS A CREDENTIAL DESTINATION, NOT A LINK</h2>
     *
     * The value arrives from a third party and the very next thing that happens is an app password
     * being POSTed to it. <b>HTTPS is mandatory</b> — a document naming {@code http://} would put the
     * credential on the wire in clear — and userinfo is refused, because {@code https://evil@real/}
     * reads as the real host to a person and resolves to neither.
     */
    static boolean usableEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            return "https"
                            .equals(
                                    uri.getScheme() == null
                                            ? null
                                            : uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException notAUri) {
            return false;
        }
    }
}
