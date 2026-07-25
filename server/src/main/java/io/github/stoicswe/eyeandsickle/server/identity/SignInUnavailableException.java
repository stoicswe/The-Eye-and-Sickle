package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * Sign-in cannot be attempted because no usable identity provider is configured.
 *
 * <p>This server ships only {@link DevAtProtoIdentityProvider}, which is disabled by default, and no
 * production AT Proto OAuth provider is wired yet ({@link AtProtoIdentityProvider}). When development
 * sign-in is off and nothing has replaced it, the sign-in endpoint answers with this rather than
 * pretending to authenticate — a {@code 503}, "the capability is not available here", which is the
 * honest state of the world and points the operator at the missing piece instead of failing obscurely.
 */
public class SignInUnavailableException extends SignInException {

    /**
     * @param message what is missing and how to supply it
     */
    public SignInUnavailableException(String message) {
        super(message);
    }
}
