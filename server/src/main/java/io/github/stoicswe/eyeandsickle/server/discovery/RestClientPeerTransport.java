package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The production {@link PeerTransport}: bounded HTTP over a {@link RestClient}, inside TLS.
 *
 * <h2>Bounds, because the far end is untrusted</h2>
 *
 * Short connect and read timeouts stop a slow-loris peer from tying up a connection; a body-size guard
 * stops a peer from returning a body too large to hold in memory; and only {@code max} descriptors are
 * ever extracted from a directory response regardless of how many the peer sent. A failed or malformed
 * response becomes an empty result, never an exception that propagates into a gossip round — an
 * unreachable peer is a normal, expected condition here, handled by back-off, not an error.
 *
 * <h2>Wire shapes</h2>
 *
 * <ul>
 *   <li>{@code GET {endpoint}/federation/descriptor} &rarr; this server's own signed self-descriptor
 *       envelope (a JSON object).
 *   <li>{@code GET {endpoint}/federation/peers?limit=N} &rarr; {@code {"descriptors": [<envelope>, ...]}},
 *       a bounded sample of the peer's directory.
 * </ul>
 *
 * Descriptors are returned to the caller as raw JSON strings, never parsed into a
 * {@link ServerDescriptor} here — verification is the caller's job and must not be short-circuited by
 * the transport handing back a "trusted" object.
 */
@Component
public class RestClientPeerTransport implements PeerTransport {

    /** Path a server serves its own signed descriptor at. */
    static final String DESCRIPTOR_PATH = "/federation/descriptor";

    /** Path a server serves its bounded directory sample at. */
    static final String PEERS_PATH = "/federation/peers";

    /** The array field in a directory response. */
    static final String DESCRIPTORS_FIELD = "descriptors";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final int maxDescriptorBytes;
    private final int maxPeersPerExchange;

    // @Autowired marks this as the constructor Spring uses. Without it, the two constructors are
    // ambiguous and the container falls back to a (non-existent) no-arg one and fails to start.
    // This is the production wiring — it builds its own timeout-bounded RestClient; the two-arg
    // constructor below is for tests that inject a stub client.
    @org.springframework.beans.factory.annotation.Autowired
    RestClientPeerTransport(DiscoveryProperties properties) {
        this.maxDescriptorBytes = properties.maxDescriptorBytes();
        this.maxPeersPerExchange = properties.maxPeersPerExchange();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** For tests: inject a pre-built client (e.g. against a stub server). */
    RestClientPeerTransport(RestClient restClient, DiscoveryProperties properties) {
        this.restClient = restClient;
        this.maxDescriptorBytes = properties.maxDescriptorBytes();
        this.maxPeersPerExchange = properties.maxPeersPerExchange();
    }

    @Override
    public Optional<String> fetchSelfDescriptor(String endpoint) {
        try {
            String body = restClient
                    .get()
                    .uri(join(endpoint, DESCRIPTOR_PATH))
                    .retrieve()
                    .body(String.class);
            if (body == null || overBudget(body, maxDescriptorBytes)) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (RuntimeException e) {
            // Unreachable / non-2xx / timeout: a normal condition, reported as "nothing", handled by
            // back-off upstream. Never rethrown into the discovery round.
            return Optional.empty();
        }
    }

    @Override
    public List<String> fetchDirectory(String endpoint, int max) {
        int bounded = Math.min(max, maxPeersPerExchange);
        try {
            String body = restClient
                    .get()
                    .uri(join(endpoint, PEERS_PATH) + "?limit=" + bounded)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return List.of();
            }
            // A directory response can legitimately hold up to `bounded` descriptors; anything past that
            // product of bounds is an abusive body and is refused whole rather than parsed.
            if (overBudget(body, (long) maxDescriptorBytes * (bounded + 1))) {
                return List.of();
            }
            return extractDescriptors(body, bounded);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @Override
    public boolean probe(String endpoint) {
        try {
            restClient.get().uri(join(endpoint, DESCRIPTOR_PATH)).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<String> extractDescriptors(String body, int max) {
        Map<String, Object> parsed;
        try {
            parsed = Jsonb.readObject(body);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (!(parsed.get(DESCRIPTORS_FIELD) instanceof List<?> descriptors)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object element : descriptors) {
            if (out.size() >= max) {
                break; // Loop control: never process more than the bound, whatever the peer sent.
            }
            if (element instanceof Map<?, ?> envelope) {
                try {
                    // Re-serialize the element to a JSON string for the verifier. Canonicalization keys
                    // off the descriptor payload's values, not the envelope's byte layout, so this does
                    // not disturb the signature.
                    @SuppressWarnings("unchecked")
                    Map<String, Object> object = (Map<String, Object>) envelope;
                    out.add(Jsonb.writeObject(object));
                } catch (RuntimeException ignored) {
                    // One malformed element does not spoil the batch.
                }
            }
        }
        return out;
    }

    private static boolean overBudget(String body, long maxBytes) {
        // Char length is a cheap over-estimate of UTF-8 byte length for the ASCII-heavy JSON here; if it
        // is already over the cap the body is rejected without the cost of encoding it.
        return (long) body.length() > maxBytes || body.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }

    private static String join(String base, String path) {
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + path;
    }
}
