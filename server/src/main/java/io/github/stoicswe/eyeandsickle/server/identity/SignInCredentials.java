package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * The raw inputs a client presents at sign-in, before any of them are trusted.
 *
 * <h2>Untrusted by definition</h2>
 *
 * Every field here came from the client and none of it is authoritative (Invariant I14). It is the
 * <em>input</em> to {@link AtProtoIdentityProvider#authenticate(SignInCredentials)}, whose entire job
 * is to turn some subset of it into a {@link ResolvedIdentity} that <em>is</em> trusted — or to refuse.
 * Nothing downstream of the provider should read these fields; it should read the {@code ResolvedIdentity}.
 *
 * <h2>Why one bag of optional fields</h2>
 *
 * Different providers consume different inputs, and the seam should not have to change shape when the
 * provider does. A production AT Proto OAuth provider completes a code-exchange and reads
 * {@link #authorizationCode()} / {@link #state()} / {@link #redirectUri()}; the development provider
 * ({@link DevAtProtoIdentityProvider}) trusts {@link #claimedDid()} directly, which is precisely why it
 * is disabled by default. A field being present is never itself permission to use it — the provider
 * decides what it honours.
 *
 * @param handle the AT Proto handle the player typed, or {@code null}
 * @param claimedDid a DID the client asserts it owns; honoured only by the development provider and
 *     only when development sign-in is explicitly enabled
 * @param authorizationCode the OAuth authorization code from a completed redirect, or {@code null}
 * @param state the OAuth {@code state} parameter echoed back, for CSRF binding, or {@code null}
 * @param redirectUri the redirect URI the code was issued against, or {@code null}
 * @param serviceAuthJwt an AT Protocol inter-service auth JWT proving control of a DID — what the
 *     production provider reads, and the only field here that carries a proof rather than a claim
 */
public record SignInCredentials(
        String handle,
        String claimedDid,
        String authorizationCode,
        String state,
        String redirectUri,
        String serviceAuthJwt) {

    /** The pre-Option-C shape, kept so existing callers and tests need no edit. */
    public SignInCredentials(
            String handle, String claimedDid, String authorizationCode, String state, String redirectUri) {
        this(handle, claimedDid, authorizationCode, state, redirectUri, null);
    }
}
