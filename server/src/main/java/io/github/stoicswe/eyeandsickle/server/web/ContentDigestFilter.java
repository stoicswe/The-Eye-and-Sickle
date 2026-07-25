package io.github.stoicswe.eyeandsickle.server.web;

import io.github.stoicswe.eyeandsickle.protocol.crypto.PayloadDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Adds an RFC 9530 {@code Content-Digest} checksum to every response body, and validates one on every
 * request body that carries it — the corruption check on payloads exchanged with clients and with
 * other servers.
 *
 * <h2>What this is and is not</h2>
 *
 * This catches <em>accidental</em> corruption — a truncated read, a buggy proxy, a flipped bit, a
 * store-and-forward hop that mangled the bytes — early and cheaply, before the payload reaches JSON
 * parsing or signature verification where the failure would be far more confusing. It is <strong>not
 * the tamper defence</strong>: an adversary who can alter the body can recompute the digest to match,
 * so authenticity still rests on the secure channel's AEAD and on the Ed25519 signatures inside
 * provenance and duel records ({@link PayloadDigest} documents this distinction in full). The filter
 * exists alongside those, not instead of them.
 *
 * <h2>Behaviour</h2>
 *
 * <ul>
 *   <li><strong>Response:</strong> every response with a body gets a {@code Content-Digest} header,
 *       so any client or peer can verify what it received.
 *   <li><strong>Request:</strong> if a request carries a {@code Content-Digest} header, the body is
 *       hashed and compared. A mismatch is rejected with {@code 422 Unprocessable Entity} — the body
 *       arrived corrupted, so it cannot be processed. A request <em>without</em> the header is passed
 *       through unchanged: the header is an optional integrity aid, not an authentication requirement
 *       (that is the secure channel's job), so requiring it would break ordinary clients.
 *   <li>An over-large body is refused with {@code 413} before it is buffered, so the filter cannot be
 *       used to exhaust memory.
 * </ul>
 *
 * <p>Ordered early so the digest covers the exact bytes on the wire, before any later filter can
 * rewrite them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ContentDigestFilter extends OncePerRequestFilter {

    /** RFC 9530 field name. */
    static final String CONTENT_DIGEST = "Content-Digest";

    /**
     * Upper bound on a body this filter will buffer to check or digest, in bytes. A home server's
     * federation payloads (descriptors, provenance chains, duel outcomes) are far smaller; anything
     * larger is refused rather than held in memory.
     */
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpServletRequest effectiveRequest = request;
        String presentedDigest = request.getHeader(CONTENT_DIGEST);

        if (hasBody(request)) {
            byte[] body = CachedBodyHttpServletRequest.readAll(request);
            if (body.length > MAX_BODY_BYTES) {
                response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request body too large");
                return;
            }
            if (presentedDigest != null && !PayloadDigest.matches(body, presentedDigest)) {
                // The body does not match the digest the sender computed: it arrived corrupted (or the
                // sender used an algorithm we do not speak). Either way it cannot be trusted to parse.
                response.sendError(
                        HttpStatus.UNPROCESSABLE_ENTITY.value(), "Content-Digest does not match the request body");
                return;
            }
            effectiveRequest = new CachedBodyHttpServletRequest(request, body);
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        chain.doFilter(effectiveRequest, wrapped);

        byte[] responseBody = wrapped.getContentAsByteArray();
        if (responseBody.length > 0) {
            wrapped.setHeader(CONTENT_DIGEST, PayloadDigest.contentDigest(responseBody));
        }
        // copyBodyToResponse writes the buffered bytes (and the header set above) to the real
        // response. Without it the client receives an empty body.
        wrapped.copyBodyToResponse();
    }

    private static boolean hasBody(HttpServletRequest request) {
        // A declared positive length, or a streamed body with no length (chunked). GET/DELETE with no
        // body report length 0 and no content type, and are skipped.
        if (request.getContentLengthLong() > 0) {
            return true;
        }
        String method = request.getMethod();
        boolean bodyMethod = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        return bodyMethod && request.getContentType() != null;
    }
}
