package io.github.stoicswe.eyeandsickle.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The hand-written HTTP/1.1 response parser.
 *
 * <p>This exists because {@link HardenedHttpClient} drives a socket to pin the connection against DNS
 * rebinding, which makes reading the response ours to do. It is the riskiest code in the package, and
 * it is testable without a TLS server precisely because it was split out — so it is tested hard, with
 * the sender assumed hostile.
 */
class HttpResponseReaderTest {

    private static InputStream wire(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponseReader.Parsed read(String raw) throws IOException {
        return HttpResponseReader.read(wire(raw), 1024);
    }

    @Nested
    @DisplayName("well-formed responses")
    class Wellformed {

        @Test
        @DisplayName("content-length body")
        void contentLength() throws Exception {
            var parsed = read(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 13\r\n\r\n{\"id\":\"did\"}\n");

            assertThat(parsed.status()).isEqualTo(200);
            assertThat(parsed.headers()).containsEntry("content-type", "application/json");
            assertThat(parsed.body()).startsWith("{\"id\":\"did\"}");
        }

        @Test
        @DisplayName("CHUNKED body — /.well-known endpoints commonly use it")
        void chunked() throws Exception {
            var parsed = read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                    + "7\r\ndid:plc\r\n"
                    + "4\r\n:abc\r\n"
                    + "0\r\n\r\n");

            assertThat(parsed.body()).isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("a chunk-size extension after ';' is ignored, not treated as garbage")
        void chunkExtensions() throws Exception {
            var parsed =
                    read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + "3;name=value\r\nabc\r\n0\r\n\r\n");

            assertThat(parsed.body()).isEqualTo("abc");
        }

        @Test
        @DisplayName("no length and no chunking — the body ends when the connection closes")
        void readUntilClose() throws Exception {
            // We send `Connection: close`, so this is a legitimate framing and not an error.
            var parsed = read("HTTP/1.1 200 OK\r\n\r\ndid:plc:abc");

            assertThat(parsed.body()).isEqualTo("did:plc:abc");
        }

        @Test
        @DisplayName("header names are matched case-insensitively")
        void headerCaseInsensitive() throws Exception {
            var parsed = read("HTTP/1.1 302 Found\r\nLOCATION: https://elsewhere.example/x\r\n\r\n");

            // The redirect walk looks up "location". A server spelling it any other way must not
            // silently produce "redirect with no Location".
            assertThat(parsed.headers()).containsEntry("location", "https://elsewhere.example/x");
        }

        @Test
        @DisplayName("204 and 304 carry no body and are not waited on")
        void bodylessStatuses() throws Exception {
            // Reading a body that will never arrive blocks until the socket times out — a hang that
            // presents as a slow sign-in rather than as an error.
            assertThat(read("HTTP/1.1 204 No Content\r\n\r\n").body()).isEmpty();
            assertThat(read("HTTP/1.1 304 Not Modified\r\n\r\n").body()).isEmpty();
        }

        @Test
        @DisplayName("a duplicated header takes the FIRST value")
        void duplicateHeaders() throws Exception {
            // Duplicate Content-Length/Location is request-smuggling shaped. First-wins is the
            // conservative reading and is at least deterministic.
            var parsed = read("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Type: text/html\r\n\r\nx");

            assertThat(parsed.headers()).containsEntry("content-type", "text/plain");
        }
    }

    @Nested
    @DisplayName("hostile or malformed responses")
    class Hostile {

        @Test
        @DisplayName("a body over the cap is refused — Content-Length is never trusted")
        void oversizeBody() {
            // Declares 1 byte, sends 5000. Trusting the header would let a hostile host stream this
            // process out of memory.
            String raw = "HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\n" + "x".repeat(5000);

            assertThatThrownBy(() -> HttpResponseReader.read(wire(raw), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("an oversize CHUNKED body is refused before the chunk is read")
        void oversizeChunk() {
            // Checked against the declared size BEFORE reading, so a declared 2 GiB chunk costs
            // nothing to refuse.
            String raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n7fffffff\r\n";

            assertThatThrownBy(() -> HttpResponseReader.read(wire(raw), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("a header flood is refused")
        void headerFlood() {
            StringBuilder raw = new StringBuilder("HTTP/1.1 200 OK\r\n");
            for (int i = 0; i < 500; i++) {
                raw.append("X-Pad-").append(i).append(": x\r\n");
            }
            raw.append("\r\n");

            assertThatThrownBy(() -> HttpResponseReader.read(wire(raw.toString()), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("headers");
        }

        @Test
        @DisplayName("an endless header LINE is refused")
        void endlessHeaderLine() {
            String raw = "HTTP/1.1 200 OK\r\nX-Pad: " + "x".repeat(20_000) + "\r\n\r\n";

            assertThatThrownBy(() -> HttpResponseReader.read(wire(raw), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("header line");
        }

        @Test
        @DisplayName("a Content-Encoding we never asked for is refused, not returned as gibberish")
        void unexpectedContentEncoding() {
            // We send no Accept-Encoding. Returning a gzip body undecoded would hand the caller bytes
            // that are not the document, and DidDocument.parse would report "not valid JSON" — a
            // misleading error two layers away from the cause.
            String raw = "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\n";

            assertThatThrownBy(() -> HttpResponseReader.read(wire(raw), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("Content-Encoding");
        }

        @Test
        @DisplayName("something that is not HTTP at all is refused")
        void notHttp() {
            assertThatThrownBy(() -> HttpResponseReader.read(wire("<html>hello</html>"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("not an HTTP response");
        }

        @Test
        @DisplayName("headers that never end are refused rather than read forever")
        void unterminatedHeaders() {
            assertThatThrownBy(() -> HttpResponseReader.read(wire("HTTP/1.1 200 OK\r\nX: y\r\n"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("without a blank line");
        }

        @Test
        @DisplayName("a chunked body with no terminating 0-chunk is refused")
        void unterminatedChunks() {
            assertThatThrownBy(() -> HttpResponseReader.read(
                            wire("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("terminator");
        }

        @Test
        @DisplayName("a chunk shorter than it declared is refused, not silently truncated")
        void shortChunk() {
            assertThatThrownBy(() -> HttpResponseReader.read(
                            wire("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\nabc"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("shorter than");
        }

        @Test
        @DisplayName("a non-numeric status code is refused")
        void malformedStatus() {
            assertThatThrownBy(() -> HttpResponseReader.read(wire("HTTP/1.1 OK OK\r\n\r\n"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("status code");
        }

        @Test
        @DisplayName("a header with no colon is refused")
        void malformedHeader() {
            assertThatThrownBy(() -> HttpResponseReader.read(wire("HTTP/1.1 200 OK\r\ngarbage\r\n\r\n"), 1024))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("malformed header");
        }
    }
}
