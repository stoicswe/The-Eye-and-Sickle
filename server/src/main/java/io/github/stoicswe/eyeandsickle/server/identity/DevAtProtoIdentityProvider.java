package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;

/**
 * A development-only {@link AtProtoIdentityProvider} that trusts a DID the client claims, with no real
 * authentication.
 *
 * <h2>⚠ This does not authenticate anyone. It must never face real players.</h2>
 *
 * A production provider completes the AT Proto OAuth handshake and <em>proves</em> the caller controls
 * the DID. This one skips all of that and takes {@link SignInCredentials#claimedDid()} at face value —
 * so with it enabled, anyone can sign in as any DID. That is useful for developing and testing every
 * downstream system (the allowlist gate, create-on-first-sign-in, sessions, profiles) without standing
 * up a PDS, and it is dangerous anywhere else. It is therefore gated behind
 * {@code eyeandsickle.identity.dev-signin.enabled}, which defaults to {@code false}: when the switch is
 * off, this provider refuses every attempt with {@link SignInUnavailableException} rather than
 * quietly authenticating.
 *
 * <h2>What "unfinished" means here, precisely</h2>
 *
 * The authoritative half of sign-in around this provider is complete and tested. The provider itself is
 * a stand-in: a real {@code AtProtoIdentityProvider} that performs dynamic client registration, the
 * authorization-code exchange, and DPoP-bound token validation is <strong>not</strong> written, and is
 * required before this server can accept real players over federation. Replacing this bean is the whole
 * of that work — nothing else in the slice assumes the stub.
 */
// Registered unconditionally, but it self-guards: unless eyeandsickle.identity.dev-signin.enabled is
// true it refuses every sign-in with SignInUnavailableException. So as the sole provider in a normal
// deployment it is fail-closed (nobody signs in until a real network AT Proto resolver is wired), and
// with the dev switch on it authenticates any claimed DID for local development. A real
// @ConditionalOnMissingBean provider supersedes it later.
@org.springframework.stereotype.Component
public final class DevAtProtoIdentityProvider implements AtProtoIdentityProvider {

    private final IdentityProperties properties;

    /**
     * @param properties supplies the development sign-in switch
     */
    public DevAtProtoIdentityProvider(IdentityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public ResolvedIdentity authenticate(SignInCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        if (!properties.devSignin().enabled()) {
            // The default state on any real deployment. Say what is missing and how to fix it, without
            // pretending a login is possible.
            throw new SignInUnavailableException(
                    "No AT Protocol OAuth provider is wired, and development sign-in is disabled. "
                            + "Enable eyeandsickle.identity.dev-signin.enabled for local use only, or supply a "
                            + "production AtProtoIdentityProvider bean before accepting real players.");
        }
        if (credentials.claimedDid() == null || credentials.claimedDid().isBlank()) {
            // Even the dev shortcut needs to be told which DID to impersonate.
            throw new SignInException(
                    "Development sign-in requires a claimed DID; none was supplied in the credentials");
        }
        Did did;
        try {
            did = Did.of(credentials.claimedDid());
        } catch (IllegalArgumentException e) {
            // A malformed DID is a bad request, but as an authentication failure to the client — the
            // dev provider does not distinguish "wrong shape" from "not you" any more than a real one
            // would leak which step failed.
            throw new SignInException("Claimed DID is not well-shaped: " + credentials.claimedDid(), e);
        }
        return new ResolvedIdentity(did, credentials.handle());
    }
}
