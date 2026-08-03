package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an HTTP/1.1 response off a stream, with every length bounded.
 *
 * <h2>Why this is hand-written, and why it is a separate class</h2>
 *
 * {@link HardenedHttpClient} connects to a <em>pinned address</em> to close the DNS-rebinding hole,
 * which means it drives a socket rather than {@code java.net.http}. Reading the response is then ours
 * to do — and it is the part most likely to be wrong, so it lives here where it can be tested against
 * a {@code ByteArrayInputStream} instead of needing a TLS server.
 *
 * <h2>Everything is bounded, because the sender is hostile</h2>
 *
 * A server that answers a {@code /.well-known} lookup is chosen by an attacker (a handle, a
 * {@code did:web} hostname). It may stream headers forever, declare a {@code Content-Length} it does
 * not honour, or send a chunked body with no terminator. So: a cap on the status line, on each header
 * line, on the header count, and on the body — the last enforced <strong>while reading</strong>,
 * never by trusting the declared length.
 *
 * <h2>What it deliberately does not support</h2>
 *
 * No HTTP/2 (we send {@code HTTP/1.1} and {@code Connection: close}), no compression (never
 * negotiated, so a {@code Content-Encoding} we did not ask for is a malformed response and is
 * refused rather than silently returned as gibberish), no trailers beyond skipping them, and no
 * connection reuse.
 */
final class HttpResponseReader {

    private HttpResponseReader() {}

    /** Long enough for any legitimate status line; short enough that an endless one is not a DoS. */
    private static final int MAX_LINE = 8 * 1024;

    /** More than any real {@code /.well-known} response, and a bound on header-flood. */
    private static final int MAX_HEADERS = 100;

    /** A parsed response. */
    record Parsed(int status, Map<String, String> headers, String body) {}

    /**
     * @param stream the socket's input
     * @param maxBytes the body cap
     * @return the parsed response
     * @throws IdentityResolutionException if the response is malformed or over a limit
     */
    static Parsed read(InputStream stream, int maxBytes) throws IOException {
        String statusLine = readLine(stream);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IdentityResolutionException(Kind.INVALID, "not an HTTP response: '" + statusLine + "'");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IdentityResolutionException(Kind.INVALID, "malformed status line: '" + statusLine + "'");
        }
        int status;
        try {
            status = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException notANumber) {
            throw new IdentityResolutionException(Kind.INVALID, "malformed status code in '" + statusLine + "'");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (int count = 0; ; count++) {
            if (count > MAX_HEADERS) {
                throw new IdentityResolutionException(Kind.REFUSED_BY_POLICY, "more than " + MAX_HEADERS + " headers");
            }
            String line = readLine(stream);
            if (line == null) {
                throw new IdentityResolutionException(Kind.INVALID, "headers ended without a blank line");
            }
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IdentityResolutionException(Kind.INVALID, "malformed header: '" + line + "'");
            }
            // Lowercased keys: HTTP header names are case-insensitive, and a lookup that is not
            // would miss `Location` from a server that spells it `location`.
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            // First value wins. A duplicated Content-Length or Location is request-smuggling shaped;
            // taking the first and ignoring the rest is the conservative reading.
            headers.putIfAbsent(name, value);
        }

        String encoding = headers.get("content-encoding");
        if (encoding != null && !encoding.isBlank() && !encoding.equalsIgnoreCase("identity")) {
            // We never send Accept-Encoding, so a compressed body is a server ignoring the request.
            // Returning it undecoded would hand the caller bytes that are not the document.
            throw new IdentityResolutionException(
                    Kind.INVALID, "unexpected Content-Encoding '" + encoding + "' — none was requested");
        }

        String body = bodyless(status) ? "" : readBody(stream, headers, maxBytes);
        return new Parsed(status, headers, body);
    }

    /** 1xx, 204 and 304 carry no body; reading one would block until the socket timed out. */
    private static boolean bodyless(int status) {
        return status < 200 || status == 204 || status == 304;
    }

    private static String readBody(InputStream stream, Map<String, String> headers, int maxBytes) throws IOException {
        String transferEncoding = headers.get("transfer-encoding");
        if (transferEncoding != null
                && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return new String(readChunked(stream, maxBytes), StandardCharsets.UTF_8);
        }
        // ⚠ Content-Length is used as a CAP, never as a promise. A server declaring 10 and sending a
        // gigabyte would otherwise stream this process out of memory, which is the same reason the
        // JDK-client version of this read to maxBytes+1 rather than trusting the header.
        byte[] bytes = readBounded(stream, maxBytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readBounded(InputStream stream, int maxBytes) throws IOException {
        byte[] bytes = stream.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IdentityResolutionException(Kind.REFUSED_BY_POLICY, "response exceeds " + maxBytes + " bytes");
        }
        return bytes;
    }

    private static byte[] readChunked(InputStream stream, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(stream);
            if (sizeLine == null) {
                throw new IdentityResolutionException(Kind.INVALID, "chunked body ended without a terminator");
            }
            // A chunk size may carry extensions after a ';'.
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon)).trim();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException notHex) {
                throw new IdentityResolutionException(Kind.INVALID, "malformed chunk size: '" + sizeLine + "'");
            }
            if (size < 0) {
                throw new IdentityResolutionException(Kind.INVALID, "negative chunk size");
            }
            if (size == 0) {
                return out.toByteArray();
            }
            if (out.size() + size > maxBytes) {
                // Checked BEFORE reading, so a declared 2 GiB chunk is refused rather than attempted.
                throw new IdentityResolutionException(
                        Kind.REFUSED_BY_POLICY, "chunked response exceeds " + maxBytes + " bytes");
            }
            byte[] chunk = stream.readNBytes(size);
            if (chunk.length != size) {
                throw new IdentityResolutionException(Kind.INVALID, "chunk shorter than its declared size");
            }
            out.write(chunk);
            String terminator = readLine(stream);
            if (terminator == null || !terminator.isEmpty()) {
                throw new IdentityResolutionException(Kind.INVALID, "chunk not terminated by CRLF");
            }
        }
    }

    /**
     * Reads one CRLF-terminated line.
     *
     * <p>Byte at a time, which is fine: this is only used for the status line and headers, the stream
     * is buffered, and the alternative (a shared buffer straddling the header/body boundary) is how a
     * hand-written HTTP reader ends up losing the first bytes of the body.
     *
     * @return the line without its terminator, or null at end of stream
     */
    private static String readLine(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = stream.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = out.toByteArray();
                int length = (bytes.length > 0 && bytes[bytes.length - 1] == '\r') ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            out.write(b);
            if (out.size() > MAX_LINE) {
                throw new IdentityResolutionException(
                        Kind.REFUSED_BY_POLICY, "header line over " + MAX_LINE + " bytes");
            }
        }
        return out.size() == 0 ? null : new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }
}
