package io.github.stoicswe.eyeandsickle.protocol.identity;

/**
 * A DID or handle could not be resolved.
 *
 * <h2>Refused and unreachable are different here too</h2>
 *
 * The client makes the same distinction for game intents ({@code docs/client/01-visual-language.md}
 * §9.4: "the server refused this" and "we could not reach the server" must never collapse into one
 * message), and it matters at least as much during sign-in. {@link Kind#NOT_FOUND} means the identity
 * genuinely does not exist and the player should check what they typed; {@link Kind#UNAVAILABLE}
 * means the directory or the host is down and there is nothing for them to fix;
 * {@link Kind#REFUSED_BY_POLICY} means <em>this</em> process declined to make the request at all
 * (see {@link SsrfGuard}), which is not a network condition and must never be reported as one.
 *
 * <p>⚠ {@link Kind#INVALID} is the one that must not be softened into {@code NOT_FOUND}. A handle
 * that resolves to a DID whose document does not claim it back is a <strong>failed verification</strong>
 * ({@code docs/architecture/10-oauth-and-did-resolution.md} §4.1), not a missing record — and it is
 * exactly what an impersonation attempt looks like from here.
 */
public class IdentityResolutionException extends RuntimeException {

    /** What kind of failure this was. */
    public enum Kind {
        /** The identity does not exist. */
        NOT_FOUND,
        /** A directory or host could not be reached, or answered with a server error. */
        UNAVAILABLE,
        /** A response arrived but was malformed, or verification failed. */
        INVALID,
        /** This process declined to make the request — an SSRF denylist hit, a bad scheme, a size cap. */
        REFUSED_BY_POLICY
    }

    private final Kind kind;

    public IdentityResolutionException(Kind kind, String message) {
        this(kind, message, null);
    }

    public IdentityResolutionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
