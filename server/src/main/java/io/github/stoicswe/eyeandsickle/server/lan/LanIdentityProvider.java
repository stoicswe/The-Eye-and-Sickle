package io.github.stoicswe.eyeandsickle.server.lan;

import io.github.stoicswe.eyeandsickle.server.identity.AtProtoIdentityProvider;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.ResolvedIdentity;
import io.github.stoicswe.eyeandsickle.server.identity.SignInCredentials;
import io.github.stoicswe.eyeandsickle.server.identity.SignInException;

/**
 * The LAN identity provider: it takes a UUID at face value, on purpose.
 *
 * <h2>⚠ This trusts the client, and that is the mode, not a bug</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.identity.DevAtProtoIdentityProvider} also trusts a
 * claimed DID and is disabled by default <em>because it is a development shortcut in a mode that has
 * real security</em>. This is different: in LAN mode there is no stronger identity to fall short of.
 * The UUID is a bearer token by design ({@code docs/architecture/12-lan-mode.md} §2), and the trust
 * boundary is the network — enforced by {@link LanAddressInterlock}, which is what makes this
 * acceptable rather than merely convenient.
 *
 * <p>⚠ <strong>It accepts only {@code did:easlan:} identities.</strong> Without that check, a client
 * on the LAN could present a real {@code did:plc:} DID and be admitted as that person with no proof
 * whatsoever — a LAN server would become an impersonation oracle for federated identities. The
 * refusal is what keeps LAN identity confined to the namespace that carries its own quarantine.
 */
public class LanIdentityProvider implements AtProtoIdentityProvider {

    @Override
    public ResolvedIdentity authenticate(SignInCredentials credentials) {
        String claimed = credentials == null ? null : credentials.claimedDid();
        if (claimed == null || claimed.isBlank()) {
            throw new SignInException("no LAN identity presented; join this server to be given one");
        }
        if (!LanIdentity.isLanIdentity(claimed)) {
            // ⚠ See the class note. A did:plc: presented here would otherwise be accepted unverified.
            throw new SignInException(
                    "this is a LAN server and accepts only LAN identities; '" + claimed + "' is not one");
        }
        Did did;
        try {
            did = Did.of(claimed);
        } catch (IllegalArgumentException malformed) {
            throw new SignInException("that is not a well-formed LAN identity");
        }
        // The handle rides in from the client because on a LAN there is nothing to resolve it against.
        // It is a display name and nothing is keyed on it (12 §2).
        return new ResolvedIdentity(did, credentials.handle());
    }
}
