package io.github.stoicswe.eyeandsickle.protocol.identity;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fakes for the identity tests.
 *
 * <p>⚠ A fake {@link HttpFetcher} is legitimate here and nowhere else — see {@code HttpFetcher}'s own
 * note. Production wiring passes a {@link HardenedHttpClient}, whose SSRF rules these fakes
 * deliberately bypass so the tests can exercise <em>response</em> handling.
 */
final class IdentityFixture {

    private IdentityFixture() {}

    static final Instant T0 = Instant.parse("2026-08-02T12:00:00Z");

    /** A DID document as plc.directory actually serves one, trimmed to the fields that are read. */
    static String document(String did, String handle) {
        return document(did, List.of("at://" + handle));
    }

    static String document(String did, List<String> alsoKnownAs) {
        String akas =
                String.join(", ", alsoKnownAs.stream().map(a -> "\"" + a + "\"").toList());
        return """
        {
          "@context": ["https://www.w3.org/ns/did/v1"],
          "id": "%s",
          "alsoKnownAs": [%s],
          "verificationMethod": [{
            "id": "%s#atproto",
            "type": "Multikey",
            "controller": "%s",
            "publicKeyMultibase": "zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"
          }],
          "service": [{
            "id": "#atproto_pds",
            "type": "AtprotoPersonalDataServer",
            "serviceEndpoint": "https://pds.example.com"
          }]
        }
        """.formatted(did, akas, did, did);
    }

    /** An {@link HttpFetcher} answering from a fixed URL→body map; anything unmapped is a 404. */
    static final class FakeHttp implements HttpFetcher {
        private final Map<String, String> bodies = new HashMap<>();
        int calls;

        FakeHttp serving(String url, String body) {
            bodies.put(url, body);
            return this;
        }

        @Override
        public Response send(Request request) {
            calls++;
            String body = bodies.get(request.uri().toString());
            return body == null
                    ? new Response(404, "", Map.of("content-type", "text/plain"))
                    : new Response(200, body, Map.of("content-type", "application/json"));
        }
    }

    /** A clock the test moves by hand, so a TTL is exercised without sleeping. */
    static final class FakeClock implements Supplier<Instant> {
        Instant now = T0;

        @Override
        public Instant get() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    static DidResolver resolver(HttpFetcher http, Supplier<Instant> clock) {
        return new DidResolver(
                http,
                URI.create("https://plc.example"),
                Duration.ofMinutes(15),
                DidResolver.DEFAULT_MAX_ENTRIES,
                clock);
    }
}
