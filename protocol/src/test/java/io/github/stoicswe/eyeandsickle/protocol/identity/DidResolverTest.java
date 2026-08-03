package io.github.stoicswe.eyeandsickle.protocol.identity;

import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.FakeClock;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.FakeHttp;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.document;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.resolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DidResolverTest {

    @Nested
    @DisplayName("document URLs")
    class Urls {

        private final DidResolver resolver = resolver(new FakeHttp(), new FakeClock());

        @Test
        @DisplayName("did:plc resolves against the PLC directory")
        void plc() {
            assertThat(resolver.documentUrl("did:plc:ewvi7nxzyoun6zhxrhs64oiz"))
                    .hasToString("https://plc.example/did:plc:ewvi7nxzyoun6zhxrhs64oiz");
        }

        @Test
        @DisplayName("did:web resolves to /.well-known/did.json on the hostname")
        void web() {
            assertThat(resolver.documentUrl("did:web:example.com"))
                    .hasToString("https://example.com/.well-known/did.json");
        }

        @Test
        @DisplayName("PATH-BASED did:web is refused, not resolved")
        void pathBasedWebIsRefused() {
            // atproto excludes path-based did:web. Accepting it would let one host mint unlimited
            // identities under paths it controls, each looking independent to everything downstream.
            assertThatThrownBy(() -> resolver.documentUrl("did:web:example.com:users:alice"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("path-based");
        }

        @Test
        @DisplayName("an unsupported DID method is refused")
        void otherMethodsRefused() {
            assertThatThrownBy(() -> resolver.documentUrl("did:key:z6Mk"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("did:plc and did:web");
        }

        @Test
        @DisplayName("a did:plc identifier with junk in it never reaches the directory")
        void malformedPlcIsRefused() {
            // The identifier lands in a URL path. Refusing anything non-alphanumeric here is what
            // stops "did:plc:../../x" being a request for something else entirely.
            assertThatThrownBy(() -> resolver.documentUrl("did:plc:../../etc"))
                    .isInstanceOf(IdentityResolutionException.class);
        }
    }

    @Nested
    @DisplayName("resolution")
    class Resolution {

        @Test
        @DisplayName("parses a document and exposes its key and PDS")
        void resolves() {
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));

            DidDocument doc = resolver(http, new FakeClock()).resolve("did:plc:abc");

            assertThat(doc.id()).isEqualTo("did:plc:abc");
            assertThat(doc.claimedHandles()).containsExactly("alice.example");
            assertThat(doc.atprotoSigningKey().id()).isEqualTo("did:plc:abc#atproto");
            assertThat(doc.pdsEndpoint()).isEqualTo("https://pds.example.com");
        }

        @Test
        @DisplayName("REFUSES a document that describes a different DID")
        void substitutedDocumentIsRefused() {
            // Without this check, anything that can answer for the directory returns a document for
            // an identity of its choosing and every key and endpoint in it is adopted for the DID
            // the caller meant. Same class of check as verifying the OAuth `sub`.
            FakeHttp http = new FakeHttp()
                    .serving("https://plc.example/did:plc:abc", document("did:plc:SOMEONEELSE", "alice.example"));

            assertThatThrownBy(() -> resolver(http, new FakeClock()).resolve("did:plc:abc"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("claims to be");
        }

        @Test
        @DisplayName("a missing document is NOT_FOUND, not UNAVAILABLE")
        void missingIsNotFound() {
            assertThatThrownBy(() -> resolver(new FakeHttp(), new FakeClock()).resolve("did:plc:abc"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .extracting(e -> ((IdentityResolutionException) e).kind())
                    .isEqualTo(Kind.NOT_FOUND);
        }

        @Test
        @DisplayName("malformed JSON is INVALID, not a crash")
        void malformedJson() {
            FakeHttp http = new FakeHttp().serving("https://plc.example/did:plc:abc", "<html>not json</html>");

            assertThatThrownBy(() -> resolver(http, new FakeClock()).resolve("did:plc:abc"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .extracting(e -> ((IdentityResolutionException) e).kind())
                    .isEqualTo(Kind.INVALID);
        }
    }

    @Nested
    @DisplayName("the TTL cache")
    class Cache {

        @Test
        @DisplayName("serves a second read without a second fetch")
        void cachesWithinTtl() {
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            DidResolver resolver = resolver(http, new FakeClock());

            resolver.resolve("did:plc:abc");
            resolver.resolve("did:plc:abc");

            assertThat(http.calls).isEqualTo(1);
        }

        @Test
        @DisplayName("re-fetches once the TTL has passed — a rotated key must eventually be seen")
        void expires() {
            // The stale-key window is the cost of the cache. This test is what pins it to a bounded
            // window rather than "until the process restarts".
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            FakeClock clock = new FakeClock();
            DidResolver resolver = resolver(http, clock);

            resolver.resolve("did:plc:abc");
            clock.advance(Duration.ofMinutes(16));
            resolver.resolve("did:plc:abc");

            assertThat(http.calls).isEqualTo(2);
        }

        @Test
        @DisplayName("invalidateAll forces a re-fetch immediately")
        void invalidate() {
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            DidResolver resolver = resolver(http, new FakeClock());

            resolver.resolve("did:plc:abc");
            resolver.invalidateAll();
            resolver.resolve("did:plc:abc");

            assertThat(http.calls).isEqualTo(2);
        }
    }
}
