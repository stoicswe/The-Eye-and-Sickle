package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves a DID to its document, for the two methods atproto blesses.
 *
 * <h2>Endpoints (verified against the specs, 2026-08-02)</h2>
 *
 * <ul>
 *   <li><strong>{@code did:plc}</strong> → {@code GET https://plc.directory/<did>}
 *   <li><strong>{@code did:web}</strong> → {@code GET https://<hostname>/.well-known/did.json}.
 *       ⚠ <strong>Hostname-level only.</strong> The atproto DID spec excludes path-based
 *       {@code did:web}, so {@code did:web:example.com:user:alice} is <em>refused</em> rather than
 *       resolved. Accepting it would let one host mint unlimited identities under paths it controls,
 *       and every one of them would look like a distinct, independent identity to everything
 *       downstream.
 * </ul>
 *
 * <h2>Why the cache is not an optimisation</h2>
 *
 * {@code plc.directory} is a single point of failure for sign-in on <em>every</em> home server in the
 * federation. Its published rate limits are "generous" and unquantified, which is not something to
 * design against — so a TTL cache is what keeps a busy server from being the reason the directory
 * starts refusing, and what keeps a directory outage from being a total sign-in outage.
 *
 * <p>⚠ The TTL is a real trade-off, not free. A DID document is how an account rotates a compromised
 * key, and a cached document serves the <em>old</em> key until it expires. Short enough to bound that
 * window, long enough to be worth having: fifteen minutes.
 *
 * <h2>Threading</h2>
 *
 * Safe for concurrent use. Two threads resolving the same cold DID will both fetch it — deliberately.
 * A per-DID lock would let one slow directory response block every other request for that DID, and
 * the wasted fetch is one HTTP GET.
 */
public final class DidResolver {

    public static final URI DEFAULT_PLC_DIRECTORY = URI.create("https://plc.directory");

    /** Long enough to matter under load, short enough to bound the stale-key window. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    /**
     * ⚠ A bound, because the key is attacker-supplied. An unbounded cache keyed on "any DID anyone
     * ever asked about" is a memory exhaustion primitive dressed as a performance feature.
     */
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    private final HttpFetcher http;
    private final URI plcDirectory;
    private final Duration ttl;
    private final int maxEntries;
    private final Supplier<Instant> clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(DidDocument document, Instant expiresAt) {}

    public DidResolver() {
        this(new HardenedHttpClient(), DEFAULT_PLC_DIRECTORY, DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Instant::now);
    }

    /**
     * @param http the hardened client — never a bare {@link java.net.http.HttpClient}
     * @param plcDirectory the PLC directory origin, overridable so a test never touches the real one
     * @param ttl how long a document stays cached
     * @param maxEntries the cache bound
     * @param clock the time source, injected so the TTL is testable without sleeping
     */
    public DidResolver(HttpFetcher http, URI plcDirectory, Duration ttl, int maxEntries, Supplier<Instant> clock) {
        this.http = Objects.requireNonNull(http, "http");
        this.plcDirectory = Objects.requireNonNull(plcDirectory, "plcDirectory");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.maxEntries = maxEntries;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Resolves a DID to its document.
     *
     * @param did a {@code did:plc:} or {@code did:web:} identifier
     * @return the document, whose {@code id} is guaranteed to equal {@code did}
     * @throws IdentityResolutionException if the method is unsupported, or resolution fails
     */
    public DidDocument resolve(String did) {
        Objects.requireNonNull(did, "did");
        Instant now = clock.get();
        Cached hit = cache.get(did);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return hit.document();
        }

        DidDocument document = fetch(did);
        if (cache.size() >= maxEntries) {
            // Crude, and deliberately so: this is a bound, not an eviction policy. An LRU here would
            // be a second data structure and a lock to protect the ordering, for a cache whose whole
            // job is to be cheaper than one HTTP request.
            cache.clear();
        }
        cache.put(did, new Cached(document, now.plus(ttl)));
        return document;
    }

    /** Drops every cached document — for a key rotation that must take effect now. */
    public void invalidateAll() {
        cache.clear();
    }

    private DidDocument fetch(String did) {
        URI url = documentUrl(did);
        HttpFetcher.Response response = http.get(url, "application/did+ld+json, application/json");
        if (response.status() == 404 || response.status() == 410) {
            throw new IdentityResolutionException(Kind.NOT_FOUND, "no DID document for " + did);
        }
        if (!response.isSuccess()) {
            throw new IdentityResolutionException(
                    Kind.UNAVAILABLE, "resolving " + did + " returned HTTP " + response.status());
        }
        return DidDocument.parse(response.body(), did);
    }

    /**
     * Derives the document URL for a DID.
     *
     * <p>Package-visible and separate from {@link #fetch} so the URL derivation — which is where the
     * {@code did:web} path-segment refusal lives — is testable without a network.
     *
     * @param did the DID
     * @return the URL its document is served from
     */
    URI documentUrl(String did) {
        if (did.startsWith("did:plc:")) {
            String identifier = did.substring("did:plc:".length());
            if (identifier.isBlank() || !identifier.chars().allMatch(DidResolver::isPlcChar)) {
                throw new IdentityResolutionException(Kind.INVALID, "not a well-formed did:plc identifier: " + did);
            }
            return plcDirectory.resolve("/" + did);
        }
        if (did.startsWith("did:web:")) {
            String remainder = did.substring("did:web:".length());
            if (remainder.isBlank()) {
                throw new IdentityResolutionException(Kind.INVALID, "did:web with no hostname: " + did);
            }
            if (remainder.indexOf(':') >= 0) {
                // Unencoded ':' is did:web's path-segment separator. atproto permits hostname-level
                // DIDs only, so this is a refusal rather than a URL to build.
                throw new IdentityResolutionException(
                        Kind.INVALID, "path-based did:web is not permitted by atproto, only a hostname: " + did);
            }
            String host = URLDecoder.decode(remainder, StandardCharsets.UTF_8);
            if (host.contains("/") || host.contains("?") || host.contains("#") || host.contains("@")) {
                throw new IdentityResolutionException(Kind.INVALID, "not a bare hostname in did:web: " + did);
            }
            return URI.create("https://" + host + "/.well-known/did.json");
        }
        throw new IdentityResolutionException(
                Kind.INVALID, "unsupported DID method (atproto blesses did:plc and did:web only): " + did);
    }

    private static boolean isPlcChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
