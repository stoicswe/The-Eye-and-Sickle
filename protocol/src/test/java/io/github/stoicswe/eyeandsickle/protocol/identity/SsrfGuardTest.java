package io.github.stoicswe.eyeandsickle.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The SSRF denylist, exercised without a socket.
 *
 * <p>Every literal here is an address a hostile DID document or handle could name. The guard is
 * pure, so all of it is testable — which is the reason it is a separate type from the HTTP client
 * rather than a private method on it.
 */
class SsrfGuardTest {

    /** {@link InetAddress#getByName} on a literal does no DNS lookup, so this test touches no network. */
    private static InetAddress at(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Nested
    @DisplayName("addresses that must never be fetched")
    class Refused {

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "127.0.0.1", // loopback
                    "127.9.9.9", // the whole 127/8, not just .0.1
                    "0.0.0.0", // wildcard
                    "0.1.2.3", // "this network"
                    "10.0.0.1", // RFC 1918
                    "172.16.0.1", // RFC 1918, the range people forget
                    "172.31.255.254", // ...and its far end
                    "192.168.1.1", // RFC 1918
                    "169.254.169.254", // THE cloud metadata address
                    "169.254.1.1", // link-local generally
                    "100.64.0.1", // carrier-grade NAT
                    "100.100.100.200", // Alibaba Cloud metadata, inside CGNAT
                    "192.0.0.1", // IETF protocol assignments
                    "198.18.0.1", // benchmarking
                    "240.0.0.1", // reserved
                    "255.255.255.255", // broadcast
                    "224.0.0.1", // multicast
                    "::1", // IPv6 loopback
                    "fe80::1", // IPv6 link-local
                    "fc00::1", // IPv6 unique-local
                    "fd12:3456::1", // ...the fd half of fc00::/7
                })
        void refusesPrivateAndSpecialAddresses(String literal) throws Exception {
            assertThat(SsrfGuard.permits(at(literal)))
                    .as("%s must be refused", literal)
                    .isFalse();
            assertThat(SsrfGuard.reject(at(literal))).isNotNull();
        }

        @Test
        @DisplayName("carrier-grade NAT is not covered by isSiteLocalAddress — hence its own branch")
        void cgnatNeedsItsOwnCheck() throws Exception {
            // This is the assertion that justifies the extra code. If the JDK ever started
            // classifying 100.64/10 as site-local, the hand-written branch could go; until then,
            // removing it silently opens Alibaba's metadata endpoint.
            assertThat(at("100.64.0.1").isSiteLocalAddress()).isFalse();
            assertThat(SsrfGuard.permits(at("100.64.0.1"))).isFalse();
        }

        @Test
        @DisplayName("IPv6 unique-local is not covered by isSiteLocalAddress either")
        void uniqueLocalNeedsItsOwnCheck() throws Exception {
            // isSiteLocalAddress covers only the DEPRECATED fec0::/10 for IPv6. Without the fc00::/7
            // branch the modern private IPv6 range walks straight through.
            assertThat(at("fd12:3456::1").isSiteLocalAddress()).isFalse();
            assertThat(SsrfGuard.permits(at("fd12:3456::1"))).isFalse();
        }

        @Test
        @DisplayName("the reason names the range, so an operator behind NAT is not left guessing")
        void refusalExplainsItself() throws Exception {
            assertThat(SsrfGuard.reject(at("169.254.169.254"))).contains("metadata");
            assertThat(SsrfGuard.reject(at("10.1.2.3"))).contains("RFC 1918");
        }
    }

    @Nested
    @DisplayName("addresses that are ordinary")
    class Permitted {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "93.184.216.34", "2606:4700:4700::1111", "172.32.0.1"})
        void permitsPublicAddresses(String literal) throws Exception {
            assertThat(SsrfGuard.permits(at(literal)))
                    .as("%s is a normal public address", literal)
                    .isTrue();
        }

        @Test
        @DisplayName("172.32/12 is public — the RFC 1918 block ends at 172.31")
        void offByOneAtTheEndOfRfc1918() throws Exception {
            // An over-broad mask here (0xF0 vs 0xFF on the wrong octet) would refuse a legitimate
            // public range, which fails closed but breaks real handles.
            assertThat(SsrfGuard.permits(at("172.15.255.254"))).isTrue();
            assertThat(SsrfGuard.permits(at("172.16.0.0"))).isFalse();
            assertThat(SsrfGuard.permits(at("172.31.255.255"))).isFalse();
            assertThat(SsrfGuard.permits(at("172.32.0.0"))).isTrue();
        }
    }

    @Nested
    @DisplayName("URL shape, checked before anything is resolved")
    class Urls {

        @Test
        @DisplayName("only https")
        void httpIsRefused() {
            assertThat(SsrfGuard.rejectUrl(URI.create("http://example.com/x"))).contains("only https");
            assertThat(SsrfGuard.rejectUrl(URI.create("file:///etc/passwd"))).isNotNull();
            assertThat(SsrfGuard.rejectUrl(URI.create("gopher://example.com/"))).isNotNull();
            assertThat(SsrfGuard.rejectUrl(URI.create("https://example.com/x"))).isNull();
        }

        @Test
        @DisplayName("credentials in a URL are refused")
        void userInfoIsRefused() {
            assertThat(SsrfGuard.rejectUrl(URI.create("https://user:pass@example.com/x")))
                    .contains("credentials");
        }

        @Test
        @DisplayName("a relative or hostless URL is refused rather than resolved against something")
        void relativeIsRefused() {
            assertThat(SsrfGuard.rejectUrl(URI.create("/.well-known/did.json"))).isNotNull();
            assertThat(SsrfGuard.rejectUrl(null)).isNotNull();
        }
    }
}
