package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;

/**
 * The production {@link AtProtoIdentityProvider} — <b>W-6</b> closed.
 *
 * <h2>It verifies a signature; it does not complete a handshake</h2>
 *
 * Under {@code docs/architecture/10-oauth-and-did-resolution.md} §1 (Option C) the OAuth handshake
 * happens in the desktop client. This server's job is to reach the same conclusion <em>independently</em>,
 * and it does that by checking a service-auth JWT against a DID document it resolves itself
 * ({@link ServiceAuthVerifier}). So there is no code exchange here, no client secret, no token
 * storage, and nothing that would break if the client lied.
 *
 * <p>⚠ Which is why {@link SignInCredentials#claimedDid()} is <strong>never read</strong> here. The
 * DID that comes back is the token's {@code iss}, proven by its signature — a claimed DID in the
 * request body is exactly the input {@link DevAtProtoIdentityProvider} trusts, and the reason that
 * one is disabled by default.
 */
public class ServiceAuthIdentityProvider implements AtProtoIdentityProvider {

    private final ServiceAuthVerifier verifier;

    public ServiceAuthIdentityProvider(ServiceAuthVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Override
    public ResolvedIdentity authenticate(SignInCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        Did did = verifier.verify(credentials.serviceAuthJwt());

        // ⚠ The handle is deliberately NOT resolved here. SignInService refreshes it through
        // VerifiedHandleDirectory, which is the one place the bidirectional check lives (10 section
        // 4.1) — resolving it here as well would mean two paths to a display name and only one of
        // them verified.
        return new ResolvedIdentity(did, null);
    }
}
