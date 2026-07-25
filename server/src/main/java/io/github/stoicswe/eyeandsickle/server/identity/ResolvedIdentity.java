package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;

/**
 * A <em>verified</em> identity: the DID an {@link AtProtoIdentityProvider} has authenticated, plus the
 * handle it resolved at the same time.
 *
 * <h2>The distinction this type encodes</h2>
 *
 * A {@link Did} is only a well-shaped string — anyone can name one. A {@code ResolvedIdentity} is the
 * provider's assertion that the caller <em>proved control</em> of that DID through the AT Proto OAuth
 * handshake ({@code docs/architecture/02-identity-and-auth.md} §3). The sign-in flow only ever gates
 * and creates players from a {@code ResolvedIdentity}, never from a raw DID a client sent, so the type
 * boundary is what keeps "authenticated" from quietly degrading into "claimed".
 *
 * <p>The handle rides along because resolving it is a side effect of resolving the DID, and it is worth
 * capturing so the player's display handle can be refreshed on every sign-in — kept current without
 * ever becoming the thing the mapping is keyed on (§5). It may be {@code null} if the provider could
 * not resolve one.
 *
 * @param did the authenticated, portable identity
 * @param handle the current display handle, or {@code null}
 */
public record ResolvedIdentity(Did did, String handle) {

    public ResolvedIdentity {
        Objects.requireNonNull(did, "did");
    }
}
