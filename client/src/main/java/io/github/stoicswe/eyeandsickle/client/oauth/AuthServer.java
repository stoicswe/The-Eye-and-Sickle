package io.github.stoicswe.eyeandsickle.client.oauth;

import java.net.URI;
import java.util.List;

/**
 * An authorization server's metadata, after the checks the spec calls mandatory.
 *
 * @param issuer the issuer, which must equal the origin it was fetched from
 * @param authorizationEndpoint where the player is sent
 * @param tokenEndpoint where codes and refresh tokens are exchanged
 * @param parEndpoint the pushed-authorization-request endpoint
 */
record AuthServer(URI issuer, URI authorizationEndpoint, URI tokenEndpoint, URI parEndpoint) {

    /**
     * Checks the guarantees this client depends on, and refuses rather than degrading.
     *
     * <p>⚠ Each of these is a <em>security</em> property, not a capability probe. A server that does
     * not require PAR, or does not offer {@code S256}, or does not support DPoP's {@code ES256}, is
     * one where this client would have to fall back to a weaker flow — and silently doing that is
     * exactly how a downgrade attack succeeds. Refusing is the correct outcome, so the checks live in
     * the constructor of the type the rest of the flow consumes.
     *
     * @param requiresPar the server's {@code require_pushed_authorization_requests}
     * @param codeChallengeMethods its {@code code_challenge_methods_supported}
     * @param dpopAlgorithms its {@code dpop_signing_alg_values_supported}
     * @param scopes its {@code scopes_supported}
     */
    static void verify(
            boolean requiresPar, List<String> codeChallengeMethods, List<String> dpopAlgorithms, List<String> scopes) {
        if (!requiresPar) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL,
                    "the authorization server does not require pushed authorization requests, which atproto mandates");
        }
        if (!codeChallengeMethods.contains("S256")) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "the authorization server does not offer PKCE S256");
        }
        if (!dpopAlgorithms.contains("ES256")) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the authorization server does not offer DPoP with ES256");
        }
        if (!scopes.isEmpty() && !scopes.contains("atproto")) {
            // Empty is tolerated — the field is not universally populated — but a populated list that
            // omits `atproto` is a server that cannot serve this client at all.
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the authorization server does not offer the 'atproto' scope");
        }
    }
}
