package io.github.stoicswe.eyeandsickle.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.crypto.PayloadDigest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests for {@link ContentDigestFilter}, driven through Spring's mock servlet objects so they run in
 * the Docker-free {@code mvn verify} (no container, no web server).
 *
 * <p>The filter's job is symmetric — emit a checksum on responses, validate one on requests — and the
 * interesting cases are the failures: a corrupted request body must be refused, and a request with no
 * digest must still pass, because the header is an integrity aid, not an authentication gate.
 */
class ContentDigestFilterTest {

    private final ContentDigestFilter filter = new ContentDigestFilter();

    @Nested
    @DisplayName("responses carry a checksum")
    class Responses {

        @Test
        @DisplayName("a response body gets a Content-Digest the client can verify")
        void responseBodyIsDigested() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/players/me");
            MockHttpServletResponse response = new MockHttpServletResponse();

            byte[] payload = "{\"did\":\"did:plc:abc\",\"ethecoin\":500}".getBytes(StandardCharsets.UTF_8);
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                        throws java.io.IOException {
                    res.getOutputStream().write(payload);
                }
            };

            filter.doFilter(request, response, chain);

            String digest = response.getHeader(ContentDigestFilter.CONTENT_DIGEST);
            assertThat(digest).isEqualTo(PayloadDigest.contentDigest(payload));
            assertThat(PayloadDigest.matches(response.getContentAsByteArray(), digest))
                    .as("the emitted digest must verify against the body actually returned")
                    .isTrue();
        }

        @Test
        @DisplayName("an empty response gets no digest header")
        void emptyResponseHasNoDigest() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader(ContentDigestFilter.CONTENT_DIGEST)).isNull();
        }
    }

    @Nested
    @DisplayName("request validation")
    class Requests {

        @Test
        @DisplayName("a request whose body matches its digest is processed, and the handler sees the body")
        void intactRequestPassesThrough() throws Exception {
            byte[] body = "{\"op\":\"trade\",\"item\":\"uuid\"}".getBytes(StandardCharsets.UTF_8);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/items/trade");
            request.setContentType("application/json");
            request.setContent(body);
            request.addHeader(ContentDigestFilter.CONTENT_DIGEST, PayloadDigest.contentDigest(body));
            MockHttpServletResponse response = new MockHttpServletResponse();

            byte[][] seenByHandler = new byte[1][];
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                        throws java.io.IOException {
                    seenByHandler[0] = req.getInputStream().readAllBytes();
                }
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(seenByHandler[0])
                    .as("the handler must still be able to read the body the filter already consumed")
                    .isEqualTo(body);
        }

        @Test
        @DisplayName("a corrupted request body is rejected with 422 and never reaches the handler")
        void corruptedRequestIsRejected() throws Exception {
            byte[] body = "{\"op\":\"trade\",\"amount\":500}".getBytes(StandardCharsets.UTF_8);
            String digestOfOriginal = PayloadDigest.contentDigest(body);

            byte[] corrupted = body.clone();
            corrupted[9] ^= 0x01; // a bit flips somewhere between sender and here
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/items/trade");
            request.setContentType("application/json");
            request.setContent(corrupted);
            request.addHeader(ContentDigestFilter.CONTENT_DIGEST, digestOfOriginal);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean[] handlerRan = {false};
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    handlerRan[0] = true;
                }
            };

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            assertThat(handlerRan[0])
                    .as("a corrupted body must be refused before the handler acts on it")
                    .isFalse();
        }

        @Test
        @DisplayName("a request with no digest header still passes — the header is optional")
        void requestWithoutDigestPasses() throws Exception {
            byte[] body = "{\"op\":\"noop\"}".getBytes(StandardCharsets.UTF_8);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
            request.setContentType("application/json");
            request.setContent(body);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean[] handlerRan = {false};
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    handlerRan[0] = true;
                }
            };

            filter.doFilter(request, response, chain);

            assertThat(handlerRan[0])
                    .as("requiring the header would break ordinary clients; authenticity is the channel's job")
                    .isTrue();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("a request presenting an unsupported-algorithm digest is rejected, not ignored")
        void unsupportedAlgorithmIsRejected() throws Exception {
            byte[] body = "{\"op\":\"trade\"}".getBytes(StandardCharsets.UTF_8);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
            request.setContentType("application/json");
            request.setContent(body);
            // A digest field we cannot verify must not be silently treated as "no digest".
            request.addHeader(ContentDigestFilter.CONTENT_DIGEST, "sha-512=:AAAA:");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        }
    }

    @Test
    @DisplayName("wrapping a ContentCachingResponseWrapper still yields a verifiable digest")
    void integratesWithResponseCaching() throws Exception {
        // Guards the copyBodyToResponse() step: forget it and the client gets a digest header over an
        // empty body. This asserts the header and the delivered bytes agree.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ledger");
        MockHttpServletResponse raw = new MockHttpServletResponse();
        byte[] payload = "[{\"tx\":1},{\"tx\":2}]".getBytes(StandardCharsets.UTF_8);

        filter.doFilter(request, raw, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                res.getOutputStream().write(payload);
            }
        });

        assertThat(raw.getContentAsByteArray()).isEqualTo(payload);
        assertThat(PayloadDigest.matches(
                        raw.getContentAsByteArray(), raw.getHeader(ContentDigestFilter.CONTENT_DIGEST)))
                .isTrue();
    }
}
