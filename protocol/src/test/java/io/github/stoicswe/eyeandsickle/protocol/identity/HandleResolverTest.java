package io.github.stoicswe.eyeandsickle.protocol.identity;

import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.FakeClock;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.FakeHttp;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.document;
import static io.github.stoicswe.eyeandsickle.protocol.identity.IdentityFixture.resolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The bidirectional check, which is the one thing in this package that must not be skipped
 * ({@code docs/architecture/10-oauth-and-did-resolution.md} §4.1).
 */
class HandleResolverTest {

    private static TxtLookup dns(Map<String, List<String>> records) {
        return name -> records.getOrDefault(name, List.of());
    }

    private static HandleResolver resolverWith(FakeHttp http, TxtLookup txt) {
        return new HandleResolver(http, resolver(http, new FakeClock()), txt);
    }

    @Nested
    @DisplayName("resolving a handle the player typed")
    class Forward {

        @Test
        @DisplayName("verifies over DNS and confirms the document claims the handle back")
        void dnsHappyPath() {
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThat(resolverWith(http, txt).resolve("alice.example"))
                    .isEqualTo(new HandleResolver.VerifiedHandle("alice.example", "did:plc:abc"));
        }

        @Test
        @DisplayName("falls through to /.well-known when DNS says nothing")
        void wellKnownFallback() {
            FakeHttp http = new FakeHttp()
                    .serving("https://alice.example/.well-known/atproto-did", "did:plc:abc")
                    .serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));

            assertThat(resolverWith(http, TxtLookup.none())
                            .resolve("alice.example")
                            .did())
                    .isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("⚠ REFUSES a handle whose DID document does not claim it back")
        void impersonationIsRefused() {
            // The attack: mallory controls did:plc:mallory and writes at://alice.example into her own
            // DID document. Without the reverse check she is displayed as alice everywhere — and on
            // this game's surfaces a display name is evidence (design/12).
            //
            // Here DNS is authoritative and says alice.example is did:plc:abc, but the document that
            // DID serves claims a different handle. That mismatch is INVALID, not NOT_FOUND.
            FakeHttp http = new FakeHttp()
                    .serving("https://plc.example/did:plc:abc", document("did:plc:abc", "someone-else.example"));
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThatThrownBy(() -> resolverWith(http, txt).resolve("alice.example"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("does not claim it")
                    .extracting(e -> ((IdentityResolutionException) e).kind())
                    .isEqualTo(Kind.INVALID);
        }

        @Test
        @DisplayName("DNS wins over /.well-known when the two disagree")
        void dnsTakesPrecedence() {
            // The spec: "If the two methods return conflicting results, the DNS TXT result should be
            // preferred." A host that serves a different DID at /.well-known must not be able to
            // override the domain owner's own DNS.
            FakeHttp http = new FakeHttp()
                    .serving("https://alice.example/.well-known/atproto-did", "did:plc:IMPOSTER")
                    .serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThat(resolverWith(http, txt).resolve("alice.example").did()).isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("TXT records that are not did= are IGNORED, not treated as failures")
        void unrelatedTxtRecordsAreIgnored() {
            // Other records legitimately live on that name. Treating the first one as the answer
            // would break any domain that also has, say, a verification token there.
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            TxtLookup txt = dns(Map.of(
                    "_atproto.alice.example",
                    List.of("v=spf1 -all", "google-site-verification=xyz", "did=did:plc:abc")));

            assertThat(resolverWith(http, txt).resolve("alice.example").did()).isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("a /.well-known answered by a catch-all page is not mistaken for an identity")
        void catchAllPageIsNotADid() {
            // Very common: a web server answers every /.well-known/* with the site's 200 OK homepage.
            // Requiring the body to start with "did:" is what keeps that from becoming an identity.
            FakeHttp http =
                    new FakeHttp().serving("https://alice.example/.well-known/atproto-did", "<!DOCTYPE html><html>...");

            assertThatThrownBy(() -> resolverWith(http, TxtLookup.none()).resolve("alice.example"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .extracting(e -> ((IdentityResolutionException) e).kind())
                    .isEqualTo(Kind.NOT_FOUND);
        }

        @Test
        @DisplayName("an unresolvable handle is NOT_FOUND")
        void unknownHandle() {
            assertThatThrownBy(
                            () -> resolverWith(new FakeHttp(), TxtLookup.none()).resolve("nobody.example"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .extracting(e -> ((IdentityResolutionException) e).kind())
                    .isEqualTo(Kind.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("finding the handle for an authenticated DID")
    class Reverse {

        @Test
        @DisplayName("returns the claim that verifies")
        void verifies() {
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThat(resolverWith(http, txt).verifiedHandleFor("did:plc:abc")).isEqualTo("alice.example");
        }

        @Test
        @DisplayName("returns null — NOT an exception — when the handle has lapsed")
        void lapsedHandleIsNotAFailedSignIn() {
            // Bluesky renders this as handle.invalid. Refusing to sign somebody in because their DNS
            // is briefly wrong would be a far worse failure than showing them their DID.
            FakeHttp http =
                    new FakeHttp().serving("https://plc.example/did:plc:abc", document("did:plc:abc", "alice.example"));

            assertThat(resolverWith(http, TxtLookup.none()).verifiedHandleFor("did:plc:abc"))
                    .isNull();
        }

        @Test
        @DisplayName("a stale claim does not hide a good one later in the list")
        void oneBadClaimDoesNotShadowAGoodOne() {
            FakeHttp http = new FakeHttp()
                    .serving(
                            "https://plc.example/did:plc:abc",
                            document("did:plc:abc", List.of("at://old.example", "at://alice.example")));
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThat(resolverWith(http, txt).verifiedHandleFor("did:plc:abc")).isEqualTo("alice.example");
        }

        @Test
        @DisplayName("a claim pointing at somebody else's DID never verifies")
        void mismatchedClaimIsRejected() {
            FakeHttp http = new FakeHttp()
                    .serving("https://plc.example/did:plc:mallory", document("did:plc:mallory", "alice.example"));
            // alice.example genuinely belongs to did:plc:abc, so mallory's claim on it must fail.
            TxtLookup txt = dns(Map.of("_atproto.alice.example", List.of("did=did:plc:abc")));

            assertThat(resolverWith(http, txt).verifiedHandleFor("did:plc:mallory"))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("normalisation")
    class Normalise {

        @Test
        @DisplayName("lowercases, strips a leading @ and a trailing dot")
        void normalises() {
            assertThat(HandleResolver.normalise("  @Alice.Example.  ")).isEqualTo("alice.example");
        }

        @Test
        @DisplayName("refuses things that are not handles")
        void refusesNonHandles() {
            assertThatThrownBy(() -> HandleResolver.normalise("alice")).isInstanceOf(IdentityResolutionException.class);
            assertThatThrownBy(() -> HandleResolver.normalise("")).isInstanceOf(IdentityResolutionException.class);
            assertThatThrownBy(() -> HandleResolver.normalise("https://alice.example"))
                    .isInstanceOf(IdentityResolutionException.class);
            assertThatThrownBy(() -> HandleResolver.normalise("alice example.com"))
                    .isInstanceOf(IdentityResolutionException.class);
        }
    }
}
