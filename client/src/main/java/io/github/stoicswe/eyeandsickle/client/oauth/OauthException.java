package io.github.stoicswe.eyeandsickle.client.oauth;

/**
 * A sign-in could not be completed.
 *
 * <h2>The kinds are the same distinction the rest of the client makes</h2>
 *
 * {@code docs/client/01-visual-language.md} §9.4 requires that "the server refused this" and "we could
 * not reach the server" never collapse into one message, and sign-in is where a player is least able
 * to guess which happened. {@link Kind#DENIED} is the authorization server saying no and is the
 * player's to act on; {@link Kind#UNAVAILABLE} is nothing they can fix; {@link Kind#STORAGE} means the
 * credentials on this machine are unreadable, which is a local problem with a local remedy (sign in
 * again); and {@link Kind#PROTOCOL} means a server answered in a way the spec does not allow, which is
 * a bug report rather than a retry.
 */
public class OauthException extends RuntimeException {

    public enum Kind {
        /** The authorization server refused, or the player declined at the consent screen. */
        DENIED,
        /** A host could not be reached, or answered with a server error. */
        UNAVAILABLE,
        /** A response was malformed, or a mandatory verification failed. */
        PROTOCOL,
        /** Local credential storage could not be read or written. */
        STORAGE,
        /** The player closed the browser, or the flow timed out waiting for them. */
        ABANDONED
    }

    private final Kind kind;

    public OauthException(Kind kind, String message) {
        this(kind, message, null);
    }

    public OauthException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
