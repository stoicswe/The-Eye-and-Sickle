package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * The one outbound HTTP client identity resolution is allowed to use.
 *
 * <h2>⚠ It connects to a PINNED ADDRESS, and that is why it is not {@code java.net.http}</h2>
 *
 * The URLs this fetches are chosen by other people ({@link SsrfGuard}), so the denylist is what stops
 * a hostile DID document pointing this process at {@code 169.254.169.254}. But a denylist applied to
 * a <em>hostname</em> is defeated by <strong>DNS rebinding</strong>: the guard resolves the name and
 * sees a public address, the HTTP client resolves the name <em>again</em> when it connects, and an
 * attacker running authoritative DNS with a one-second TTL answers the second lookup with loopback.
 * Every check passes and the request goes somewhere it was never allowed to go.
 *
 * <p>{@code java.net.http} offers no way to say "connect to this address, but speak TLS and HTTP for
 * that hostname" — which is why the first version of this class documented rebinding as an accepted
 * residual risk. It is closed now, by driving the socket:
 *
 * <ol>
 *   <li>resolve the host <strong>once</strong> and check every address ({@link SsrfGuard#reject});
 *   <li>connect a plain {@link Socket} to an address that passed — <em>the address</em>, never the
 *       name, so there is no second lookup left to poison;
 *   <li>layer TLS with {@link SSLSocketFactory#createSocket(Socket, String, int, boolean)}, which
 *       takes the <strong>hostname</strong> separately — so SNI and certificate verification use the
 *       real name while the connection stays pinned to the checked address.
 * </ol>
 *
 * ⚠ {@code setEndpointIdentificationAlgorithm("HTTPS")} is what turns certificate <em>hostname</em>
 * verification on. A raw {@link SSLSocket} does not do it by default: it validates the chain but not
 * that the certificate is for the host you asked for. Omitting it would make the pinning worse than
 * useless, because any host holding a valid certificate for anything could answer for this one.
 *
 * <h2>The other limits, each a specific refusal</h2>
 *
 * <ul>
 *   <li><strong>HTTPS only, no credentials</strong> — {@link SsrfGuard#rejectUrl}.
 *   <li><strong>Redirects walked by hand</strong>, so the guard runs again on each hop. A redirect is
 *       a fresh attacker-chosen URL; validating only the first is the usual way this is beaten.
 *   <li><strong>A body cap enforced while reading</strong> — never by trusting {@code Content-Length},
 *       which the sender controls. {@link HttpResponseReader}.
 *   <li><strong>Connect and read timeouts</strong> — a host that accepts a connection and then says
 *       nothing holds a thread, and sign-in is on the request path.
 * </ul>
 *
 * <h2>What it does not do</h2>
 *
 * No caching (that is {@link DidResolver}'s), no retries (a retry loop against an unreachable host
 * multiplies the timeout by the retry count, on the request path), no connection reuse, no HTTP/2,
 * and no logging (this module takes no logging backend — callers report).
 */
public final class HardenedHttpClient implements HttpFetcher {

    /** A DID document is a few hundred bytes; 256 KiB is room for absurdity without room for harm. */
    public static final int DEFAULT_MAX_BYTES = 256 * 1024;

    /** Enough hops for a legitimate {@code /.well-known} redirect to a canonical host, and no more. */
    public static final int DEFAULT_MAX_REDIRECTS = 3;

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final int maxBytes;
    private final int maxRedirects;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HardenedHttpClient() {
        this(DEFAULT_MAX_BYTES, DEFAULT_MAX_REDIRECTS, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public HardenedHttpClient(int maxBytes, int maxRedirects, Duration connectTimeout, Duration requestTimeout) {
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;
        this.connectTimeoutMillis =
                (int) Objects.requireNonNull(connectTimeout, "connectTimeout").toMillis();
        this.readTimeoutMillis =
                (int) Objects.requireNonNull(requestTimeout, "requestTimeout").toMillis();
    }

    @Override
    public Response send(Request request) {
        URI target = request.uri();
        for (int hop = 0; hop <= maxRedirects; hop++) {
            InetAddress pinned = resolveAndCheck(target);
            HttpResponseReader.Parsed response = fetch(target, pinned, request);

            if (isRedirect(response.status())) {
                String location = response.headers().get("location");
                if (location == null || location.isBlank()) {
                    throw new IdentityResolutionException(Kind.INVALID, "redirect with no Location from " + target);
                }
                if (!"GET".equals(request.method())) {
                    // ⚠ A redirected POST is refused rather than replayed. Following one means
                    // re-sending the body — which for the token endpoint is an authorization code or
                    // a refresh token — to a host the AUTHORIZATION SERVER's response chose. Both are
                    // single-use, so replaying one to an attacker-nominated URL both leaks it and
                    // burns it. Every endpoint here is discovered from metadata and should be final.
                    throw new IdentityResolutionException(
                            Kind.INVALID,
                            "refusing to follow a redirect on a " + request.method() + " to " + target
                                    + " — the body carries a single-use credential");
                }
                // resolve() handles a relative Location. The result goes back through
                // resolveAndCheck() at the top of the next iteration — the point of manual redirects.
                target = target.resolve(location);
                continue;
            }
            return new Response(response.status(), response.body(), response.headers());
        }
        throw new IdentityResolutionException(
                Kind.REFUSED_BY_POLICY, "more than " + maxRedirects + " redirects from " + request.uri());
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /**
     * Resolves the host and returns an address that passed the guard.
     *
     * <p>⚠ Every address is checked, not the first. A name answering with one public and one loopback
     * address is a deliberate construction, and checking only {@code [0]} would let whichever the OS
     * happens to prefer decide whether the guard applies.
     */
    private InetAddress resolveAndCheck(URI target) {
        String urlProblem = SsrfGuard.rejectUrl(target);
        if (urlProblem != null) {
            throw new IdentityResolutionException(Kind.REFUSED_BY_POLICY, urlProblem);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(target.getHost());
        } catch (UnknownHostException unknown) {
            throw new IdentityResolutionException(Kind.NOT_FOUND, "no such host: " + target.getHost(), unknown);
        }
        if (addresses.length == 0) {
            throw new IdentityResolutionException(Kind.NOT_FOUND, "no such host: " + target.getHost());
        }
        for (InetAddress address : addresses) {
            String refusal = SsrfGuard.reject(address);
            if (refusal != null) {
                throw new IdentityResolutionException(
                        Kind.REFUSED_BY_POLICY, "refusing to fetch " + target.getHost() + ": resolves to " + refusal);
            }
        }
        return addresses[0];
    }

    private HttpResponseReader.Parsed fetch(URI target, InetAddress pinned, Request request) {
        String host = target.getHost();
        int port = target.getPort() > 0 ? target.getPort() : 443;

        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress(pinned, port), connectTimeoutMillis);
            plain.setSoTimeout(readTimeoutMillis);

            // ⚠ The four-argument form. It layers TLS over an ALREADY-CONNECTED socket while taking
            // the hostname separately, which is exactly the split this class needs: TCP is pinned to
            // `pinned`, and TLS is told the name so SNI and certificate verification are about the
            // host we meant. createSocket(InetAddress, port) would verify against the IP and fail.
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(plain, host, port, false)) {
                socket.setSoTimeout(readTimeoutMillis);

                SSLParameters parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(host)));
                socket.setSSLParameters(parameters);
                socket.startHandshake();

                writeRequest(socket.getOutputStream(), target, host, port, request);
                InputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
                return HttpResponseReader.read(in, maxBytes);
            }
        } catch (IdentityResolutionException refused) {
            throw refused;
        } catch (IOException io) {
            throw new IdentityResolutionException(
                    Kind.UNAVAILABLE, "could not reach " + target + ": " + io.getMessage(), io);
        }
    }

    private void writeRequest(OutputStream out, URI target, String host, int port, Request request) throws IOException {
        String path = target.getRawPath() == null || target.getRawPath().isEmpty() ? "/" : target.getRawPath();
        if (target.getRawQuery() != null) {
            path = path + "?" + target.getRawQuery();
        }
        // A default-port Host header omits the port, which is what every server expects.
        String hostHeader = port == 443 ? host : host + ":" + port;
        byte[] body = request.body();
        StringBuilder head = new StringBuilder()
                .append(request.method())
                .append(' ')
                .append(path)
                .append(" HTTP/1.1\r\n")
                .append("Host: ")
                .append(hostHeader)
                .append("\r\n")
                .append("Accept: ")
                .append(request.accept())
                .append("\r\n")
                // No Accept-Encoding: refusing an unrequested Content-Encoding (HttpResponseReader)
                // is simpler and safer than decoding one.
                .append("User-Agent: eyeandsickle-identity/1\r\n")
                // No reuse, so a server closing the connection is how a length-less body ends.
                .append("Connection: close\r\n");
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            // ⚠ A header value carrying CR or LF is request splitting: the rest of it is read as a
            // new header, or a new request. Values here come from a DPoP proof we build, but the
            // check is cheap and the failure mode is not.
            if (name.indexOf('\r') >= 0
                    || name.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0) {
                throw new IdentityResolutionException(
                        Kind.REFUSED_BY_POLICY, "header '" + name + "' contains a line break");
            }
            head.append(name).append(": ").append(value).append("\r\n");
        }
        if (body != null) {
            head.append("Content-Type: ")
                    .append(request.contentType())
                    .append("\r\n")
                    .append("Content-Length: ")
                    .append(body.length)
                    .append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body != null) {
            out.write(body);
        }
        out.flush();
    }
}
