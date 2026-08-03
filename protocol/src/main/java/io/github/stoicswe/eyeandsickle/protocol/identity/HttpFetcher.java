package io.github.stoicswe.eyeandsickle.protocol.identity;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * The narrow HTTP this package and the OAuth client need.
 *
 * <h2>Why an interface rather than the concrete client everywhere</h2>
 *
 * ⚠ <strong>This is a test seam, not a choice.</strong> The only implementation intended to run in
 * production is {@link HardenedHttpClient}, and everything in
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §4.3 about SSRF and DNS rebinding applies
 * to it and not to this interface. The interface exists because the interesting failures in
 * {@link DidResolver}, {@link HandleResolver} and the OAuth flow are all <em>response</em>-shaped — a
 * document claiming the wrong DID, a handle that does not link back, a {@code DPoP-Nonce} that has to
 * be retried — and testing them against a real socket would mean owning domains and accepting
 * flakiness on the code paths where a false pass means a check that never ran.
 *
 * <p>⚠ A fake here is legitimate in a test and is a defect anywhere else.
 */
public interface HttpFetcher {

    /**
     * One request.
     *
     * @param method {@code GET} or {@code POST}
     * @param uri the target
     * @param accept the {@code Accept} header
     * @param contentType the body's type, or null for no body
     * @param body the request body, or null
     * @param headers extra headers — {@code DPoP} lives here
     */
    record Request(
            String method, URI uri, String accept, String contentType, byte[] body, Map<String, String> headers) {

        public Request {
            headers = Map.copyOf(headers == null ? Map.of() : headers);
        }

        public static Request get(URI uri, String accept) {
            return new Request("GET", uri, accept, null, null, Map.of());
        }

        public static Request get(URI uri, String accept, Map<String, String> headers) {
            return new Request("GET", uri, accept, null, null, headers);
        }

        /**
         * A form POST — what PAR and the token endpoint take.
         *
         * @param uri the endpoint
         * @param form already url-encoded {@code a=b&c=d}
         * @param headers extra headers
         * @return the request
         */
        public static Request form(URI uri, String form, Map<String, String> headers) {
            return new Request(
                    "POST",
                    uri,
                    "application/json",
                    "application/x-www-form-urlencoded",
                    form.getBytes(StandardCharsets.UTF_8),
                    headers);
        }
    }

    /**
     * A response body, already bounded and decoded.
     *
     * @param status the HTTP status
     * @param body the decoded body
     * @param headers response headers, keys <strong>lowercased</strong>
     */
    record Response(int status, String body, Map<String, String> headers) {

        public Response {
            headers = Map.copyOf(headers == null ? Map.of() : headers);
        }

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public String contentType() {
            return header("content-type");
        }

        /**
         * @param name a header name, in any case
         * @return the value, or null
         */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * @param request what to send
     * @return the response
     * @throws IdentityResolutionException if the URL is refused, unreachable, or oversized
     */
    Response send(Request request);

    /**
     * @param uri the URL to fetch
     * @param accept the {@code Accept} header value
     * @return the response
     */
    default Response get(URI uri, String accept) {
        return send(Request.get(uri, accept));
    }
}
