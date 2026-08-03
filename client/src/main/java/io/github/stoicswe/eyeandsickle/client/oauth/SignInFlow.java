package io.github.stoicswe.eyeandsickle.client.oauth;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs a whole sign-in, end to end, and owns the checks that only exist at the seams.
 *
 * <h2>What this class is for</h2>
 *
 * {@link OauthClient} performs the exchanges and {@link OauthDiscovery} finds the server, but two
 * mandatory checks belong to neither because they compare something sent with something returned:
 * the {@code state} echo and the {@code iss} of the callback. Splitting them across the other classes
 * would mean each holds half a check, which is how a check ends up not being made.
 *
 * <h2>⚠ Two ways in, and they verify differently</h2>
 *
 * <ul>
 *   <li><strong>By handle</strong> — the normal path. The handle is resolved bidirectionally to a DID
 *       first, so the account is known <em>before</em> the browser opens and the returned {@code sub}
 *       can be compared against it.
 *   <li><strong>By server hostname</strong> — for a self-hosted PDS whose handle does not resolve, or
 *       a player who knows their server but not the exact handle spelling. ⚠ Here the account is
 *       <em>not</em> known in advance, so the {@code sub} check cannot be made against an expectation.
 *       The spec's substitute is to resolve the returned {@code sub}'s DID document afterwards and
 *       confirm its PDS binds back to the authorization server the session actually used — otherwise
 *       a hostile server could return any account it liked.
 * </ul>
 */
public final class SignInFlow {

    /** Where a player is sent when they name no server. */
    public static final String DEFAULT_PDS = "bsky.social";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OauthDiscovery discovery;
    private final TokenStore store;
    private final Consumer<URI> openBrowser;
    private final OauthClientFactory clients;

    /**
     * Builds an {@link OauthClient} once the loopback port is known.
     *
     * <p>The redirect URI contains that port, and the port is only known after the listener binds —
     * so the client cannot be constructed before the flow starts.
     */
    @FunctionalInterface
    public interface OauthClientFactory {
        OauthClient create(URI redirectUri);
    }

    public SignInFlow(
            OauthDiscovery discovery, TokenStore store, Consumer<URI> openBrowser, OauthClientFactory clients) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.store = Objects.requireNonNull(store, "store");
        this.openBrowser = Objects.requireNonNull(openBrowser, "openBrowser");
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    /**
     * A completed sign-in.
     *
     * @param pds the account's PDS — where a service-auth token is minted
     * @param authServer the authorization server this session belongs to
     */
    public record Identity(
            String did,
            String handle,
            boolean handleVerified,
            OauthClient.Tokens tokens,
            URI pds,
            AuthServer authServer) {

        /** Whether this session can join a home server at all. See {@link OauthClient.Tokens}. */
        public boolean canJoinHomeServer() {
            return tokens.canMintServiceAuth();
        }
    }

    /**
     * Signs in.
     *
     * <p>⚠ Blocks until the player finishes in the browser or the wait times out, so it must never be
     * called on the JavaFX application thread — the UI would freeze for up to five minutes with no
     * way to cancel.
     *
     * @param typedHandle the handle the player entered, or blank to go by server alone
     * @param serverHost the PDS host, or blank for {@link #DEFAULT_PDS}
     * @return the signed-in identity
     */
    public Identity signIn(String typedHandle, String serverHost) {
        boolean byHandle = typedHandle != null && !typedHandle.isBlank();
        String expectedDid = null;
        String verifiedHandle = null;
        AuthServer server;

        if (byHandle) {
            OauthDiscovery.Target target = discovery.forHandle(typedHandle);
            expectedDid = target.did();
            verifiedHandle = target.handle();
            server = target.authServer();
        } else {
            String host = serverHost == null || serverHost.isBlank() ? DEFAULT_PDS : serverHost.trim();
            server = discovery.authServerForPds(host);
        }

        try (LoopbackReceiver receiver = new LoopbackReceiver()) {
            OauthClient client = clients.create(receiver.redirectUri());
            DpopKey dpop = DpopKey.generate();
            Pkce pkce = Pkce.generate();
            String state = randomState();

            String hint = byHandle ? typedHandle : null;
            URI authorizeUrl;
            String requestedScope = OauthClient.FULL_SCOPE;
            try {
                authorizeUrl = client.pushAuthorizationRequest(server, dpop, pkce, state, hint, requestedScope);
            } catch (OauthException refused) {
                // ⚠ Granular `rpc:` scopes are still rolling out across the atproto PDS distribution,
                // so an older authorization server refuses the whole request rather than granting a
                // subset. Retrying with identity-only lets such a player still sign in — they simply
                // cannot join a home server, which Identity.canJoinHomeServer() reports rather than
                // leaving them to discover at the point of joining.
                if (refused.kind() != OauthException.Kind.DENIED) {
                    throw refused;
                }
                requestedScope = OauthClient.SCOPE;
                authorizeUrl = client.pushAuthorizationRequest(server, dpop, pkce, state, hint, requestedScope);
            }
            openBrowser.accept(authorizeUrl);

            LoopbackReceiver.Callback callback = receiver.await(LoopbackReceiver.DEFAULT_TIMEOUT);
            verifyCallback(callback, state, server);

            OauthClient.Tokens tokens = client.exchangeCode(server, dpop, pkce, callback.code(), expectedDid);

            if (!byHandle) {
                // ⚠ The account was unknown in advance, so this is where it is checked instead: the
                // returned sub must resolve to a DID document whose PDS binds back to the very
                // authorization server this session used. Without it a hostile server hands back any
                // account it likes and the client adopts it.
                AuthServer boundTo = discovery.authServerFor(tokens.did());
                if (!boundTo.issuer().equals(server.issuer())) {
                    throw new OauthException(
                            OauthException.Kind.PROTOCOL,
                            "the account " + tokens.did() + " belongs to " + boundTo.issuer()
                                    + ", not to the server that authenticated it (" + server.issuer() + ")");
                }
                verifiedHandle = discovery.verifiedHandleFor(tokens.did());
            }

            store.save(new TokenStore.Credentials(
                    tokens.did(),
                    tokens.refreshToken(),
                    dpop.exportPrivate(),
                    dpop.exportPublic(),
                    server.issuer().toString()));

            return new Identity(
                    tokens.did(),
                    verifiedHandle,
                    verifiedHandle != null,
                    tokens,
                    discovery.pdsFor(tokens.did()),
                    server);
        }
    }

    /**
     * Mints a proof of identity for one home server and presents it.
     *
     * <p>⚠ The token is bound to <em>that</em> server's DID, so it is useless anywhere else — which is
     * what makes it safe to hand over at all. The access token never leaves this process.
     *
     * @param identity a completed sign-in
     * @param homeServers the transport to the home server
     * @param serverUrl the home server's base URL
     * @return the account that server recognises
     */
    public HomeServerSignIn.Account joinHomeServer(Identity identity, HomeServerSignIn homeServers, URI serverUrl) {
        if (!identity.canJoinHomeServer()) {
            // Said plainly and early. The alternative is a 400 from the PDS about a scope, which
            // names nothing the player can act on.
            throw new OauthException(
                    OauthException.Kind.DENIED,
                    "This sign-in cannot join a home server: your provider did not grant permission to "
                            + "prove your identity to one. That permission is still being rolled out across "
                            + "AT Protocol servers.");
        }
        String audience = homeServers.discoverDid(serverUrl);
        String token = ServiceAuth.mint(
                clients.create(URI.create("http://127.0.0.1/callback")),
                identity.pds(),
                DpopKey.restore(store.load().dpopPrivateKey(), store.load().dpopPublicKey()),
                identity.tokens().accessToken(),
                audience);
        return homeServers.signIn(serverUrl, token);
    }

    /**
     * Resumes a stored session by refreshing it.
     *
     * @return the identity, or null if nothing is stored
     */
    public Identity resume() {
        TokenStore.Credentials stored = store.load();
        if (stored == null
                || stored.refreshToken() == null
                || stored.refreshToken().isBlank()) {
            return null;
        }
        AuthServer server = discovery.authServerFor(stored.did());
        DpopKey dpop = DpopKey.restore(stored.dpopPrivateKey(), stored.dpopPublicKey());
        try {
            OauthClient.Tokens tokens = clients.create(URI.create("http://127.0.0.1/callback"))
                    .refresh(server, dpop, stored.refreshToken(), stored.did());

            // ⚠ The refresh token ROTATED. Storing the new one immediately is not tidiness: the old
            // one is already dead, so a failure to persist here means the next launch presents a
            // token the server has invalidated and the player is signed out for no visible reason.
            store.save(new TokenStore.Credentials(
                    tokens.did(),
                    tokens.refreshToken(),
                    stored.dpopPrivateKey(),
                    stored.dpopPublicKey(),
                    stored.issuer()));

            return new Identity(
                    tokens.did(),
                    discovery.verifiedHandleFor(tokens.did()),
                    true,
                    tokens,
                    discovery.pdsFor(tokens.did()),
                    server);
        } catch (OauthException refused) {
            if (refused.kind() == OauthException.Kind.DENIED) {
                // ⚠ A refused refresh means the token is gone for good — revoked, expired, or the
                // two-week public-client cap. Keeping it means every launch retries a dead exchange
                // and shows an error where a sign-in button belongs.
                store.clear();
                return null;
            }
            throw refused;
        }
    }

    /** Forgets the session, locally. */
    public void signOut() {
        store.clear();
    }

    /**
     * The two checks that exist only at this seam.
     *
     * <ul>
     *   <li>⚠ <strong>{@code state}</strong> — must equal what was sent. This is the CSRF binding: it
     *       is what stops somebody feeding the loopback listener an authorization code obtained in
     *       <em>their</em> browser session, which would sign this client into the attacker's account
     *       (or, worse in this game, silently attach the player's rig to it).
     *   <li>⚠ <strong>{@code iss}</strong> — must equal the issuer of the server the flow used. This
     *       is the mix-up defence: without it, a malicious authorization server can hand back a code
     *       that the client then redeems at a <em>different</em> server.
     * </ul>
     */
    private static void verifyCallback(LoopbackReceiver.Callback callback, String state, AuthServer server) {
        if (callback.error() != null) {
            throw new OauthException(
                    OauthException.Kind.DENIED,
                    "sign-in was refused: " + callback.error()
                            + (callback.errorDescription() == null ? "" : " — " + callback.errorDescription()));
        }
        if (callback.code() == null || callback.code().isBlank()) {
            throw new OauthException(OauthException.Kind.PROTOCOL, "the redirect carried no authorization code");
        }
        // Constant-time is not required — `state` is not a secret the attacker is guessing byte by
        // byte, it is a value they must already know in full — but equality must be exact.
        if (!state.equals(callback.state())) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL, "the redirect's state did not match; the sign-in was not ours");
        }
        if (callback.issuer() != null
                && !callback.issuer().equals(server.issuer().toString())) {
            throw new OauthException(
                    OauthException.Kind.PROTOCOL,
                    "the redirect claims issuer '" + callback.issuer() + "' but the flow used '" + server.issuer()
                            + "'");
        }
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** How long the browser step may take before the flow gives up. */
    public static Duration browserTimeout() {
        return LoopbackReceiver.DEFAULT_TIMEOUT;
    }
}
