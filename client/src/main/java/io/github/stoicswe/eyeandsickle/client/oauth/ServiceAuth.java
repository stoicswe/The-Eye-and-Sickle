package io.github.stoicswe.eyeandsickle.client.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Mints a short-lived proof of identity for one home server.
 *
 * <h2>Where this sits in Option C</h2>
 *
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §1 step 2. The client has an OAuth session
 * with the player's own PDS; it asks that PDS for a JWT signed by the account's atproto key, whose
 * {@code aud} is the home server's DID. The home server then verifies that signature against a DID
 * document it resolves itself — so its trust never rests on anything this client says.
 *
 * <p>⚠ <strong>The access token never leaves this process.</strong> What goes to the home server is
 * this JWT and nothing else: a ~60-second assertion, scoped to one audience, useless anywhere else.
 * That is the property that makes it safe to hand a home server anything at all.
 */
public final class ServiceAuth {

    /** The method this token is minted for; also the {@code lxm} it is bound to. */
    public static final String LXM = "com.atproto.server.getServiceAuth";

    private ServiceAuth() {}

    /**
     * Asks the player's PDS for a service-auth token.
     *
     * @param client the OAuth client holding the session
     * @param pds the player's PDS, from their DID document
     * @param dpop the session key
     * @param accessToken the current access token
     * @param audienceDid the home server's DID — what the token will be valid for, and only that
     * @return the compact JWS
     */
    public static String mint(OauthClient client, URI pds, DpopKey dpop, String accessToken, String audienceDid) {
        Objects.requireNonNull(audienceDid, "audienceDid");
        // ⚠ `lxm` is sent so the minted token is bound to a method as well as an audience. It costs
        // nothing and narrows what a captured token could ever be presented for.
        URI endpoint = pds.resolve("/xrpc/" + LXM + "?aud=" + encode(audienceDid) + "&lxm=" + encode(LXM));

        JsonNode response = client.authenticatedGet(endpoint, dpop, accessToken);
        JsonNode token = response.get("token");
        if (token == null || !token.isString() || token.stringValue().isBlank()) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the PDS returned no service-auth token for " + audienceDid);
        }
        return token.stringValue();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
