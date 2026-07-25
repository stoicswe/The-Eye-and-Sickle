package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * Sign-in could not be completed.
 *
 * <p>The base of the sign-in failure hierarchy. A bare {@code SignInException} means authentication
 * itself failed — the provider could not verify the caller controls the DID they are signing in as.
 * That is a client-facing {@code 401}: the request was well-formed but the identity was not proven.
 * More specific failures — a proven identity that is not permitted to join, or a provider that is not
 * wired up — are the subclasses, so the API can answer with the right status without leaking why.
 *
 * <h2>Deliberately vague to the client</h2>
 *
 * Authentication failures tell an attacker as little as possible about which step failed. The
 * {@code message} here is for the operator's log; the HTTP response the {@link IdentityExceptionHandler}
 * derives from the type is intentionally coarse.
 */
public class SignInException extends RuntimeException {

    /**
     * @param message operator-facing detail; not necessarily surfaced verbatim to the client
     */
    public SignInException(String message) {
        super(message);
    }

    /**
     * @param message operator-facing detail
     * @param cause the underlying failure (e.g. a transport error from the provider)
     */
    public SignInException(String message, Throwable cause) {
        super(message, cause);
    }
}
