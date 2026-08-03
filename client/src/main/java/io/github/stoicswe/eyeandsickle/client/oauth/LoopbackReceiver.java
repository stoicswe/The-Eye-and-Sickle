package io.github.stoicswe.eyeandsickle.client.oauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Catches the authorization redirect on {@code 127.0.0.1}.
 *
 * <h2>Why loopback and not a custom scheme</h2>
 *
 * atproto permits a native client either a loopback redirect or a reverse-domain custom scheme. A
 * custom scheme has to be registered with the operating system, which means an installer step, three
 * platform mechanisms, and a failure mode where the URL opens the <em>wrong</em> copy of the game.
 * Loopback needs nothing registered. ⚠ The spec ignores the port when matching a loopback redirect,
 * so binding to an ephemeral port is legitimate — and it must be, because a fixed port is one another
 * process may already hold.
 *
 * <h2>⚠ Bound to the loopback interface explicitly</h2>
 *
 * {@code new ServerSocket(port)} listens on <strong>every</strong> interface, so on a laptop on a
 * café network it would accept authorization codes from anyone who could reach the machine. The
 * three-argument constructor with an explicit {@link InetAddress} is what confines it to
 * {@code 127.0.0.1}.
 *
 * <h2>⚠ What this deliberately does not do</h2>
 *
 * It serves exactly one request and closes, it reads only the request line, and it never reads the
 * body. It is not a web server and must not grow into one: anything more is attack surface listening
 * on a socket for the sole purpose of receiving one redirect.
 */
final class LoopbackReceiver implements AutoCloseable {

    /** Long enough for a real person to read a consent screen and decide; short enough to end. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final ServerSocket server;

    LoopbackReceiver() {
        try {
            // Port 0 = ephemeral. The explicit loopback address is the important argument.
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        } catch (IOException failed) {
            throw new OauthException(
                    OauthException.Kind.UNAVAILABLE, "could not listen for the sign-in redirect", failed);
        }
    }

    /** @return the redirect URI to register with the authorization server */
    URI redirectUri() {
        return URI.create("http://127.0.0.1:" + server.getLocalPort() + "/callback");
    }

    /** The parameters the authorization server sent back. */
    record Callback(String code, String state, String issuer, String error, String errorDescription) {}

    /**
     * Waits for the redirect.
     *
     * @param timeout how long to wait for the player
     * @return the callback parameters
     */
    Callback await(Duration timeout) {
        try {
            server.setSoTimeout((int) timeout.toMillis());
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5_000);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                if (requestLine == null || !requestLine.startsWith("GET ")) {
                    throw new OauthException(OauthException.Kind.PROTOCOL, "the sign-in redirect was malformed");
                }
                String path = requestLine.split(" ")[1];
                Map<String, String> params = queryOf(path);
                respond(socket.getOutputStream(), params.containsKey("code"));
                return new Callback(
                        params.get("code"),
                        params.get("state"),
                        params.get("iss"),
                        params.get("error"),
                        params.get("error_description"));
            }
        } catch (java.net.SocketTimeoutException waitedTooLong) {
            throw new OauthException(
                    OauthException.Kind.ABANDONED, "sign-in was not completed in the browser", waitedTooLong);
        } catch (IOException failed) {
            throw new OauthException(OauthException.Kind.UNAVAILABLE, "the sign-in redirect failed", failed);
        }
    }

    private static Map<String, String> queryOf(String path) {
        Map<String, String> params = new HashMap<>();
        int question = path.indexOf('?');
        if (question < 0) {
            return params;
        }
        for (String pair : path.substring(question + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                params.put(
                        URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    /**
     * Answers the browser so the player sees something other than a connection error.
     *
     * <p>⚠ The page is a fixed literal and interpolates <strong>nothing</strong> from the query
     * string. Echoing the server's {@code error_description} back into HTML would be a cross-site
     * scripting hole on a page served from localhost — small blast radius, entirely avoidable, and
     * exactly the shortcut that gets taken here.
     */
    private static void respond(OutputStream out, boolean success) throws IOException {
        String body = success
                ? "<!doctype html><meta charset=utf-8><title>Signed in</title>"
                        + "<body style='background:#12100e;color:#d8d2c8;font:14px monospace;padding:3rem'>"
                        + "<p>Signed in. You can close this tab and return to the client.</p>"
                : "<!doctype html><meta charset=utf-8><title>Sign-in failed</title>"
                        + "<body style='background:#12100e;color:#d8d2c8;font:14px monospace;padding:3rem'>"
                        + "<p>Sign-in did not complete. Return to the client for details.</p>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
            // Closing a listener that has already failed is not itself a failure.
        }
    }
}
