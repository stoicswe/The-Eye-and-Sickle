package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The join gate: decides whether an authenticated DID is allowed onto this server
 * ({@code docs/architecture/03-server-and-federation.md} §1).
 *
 * <h2>Closed by default</h2>
 *
 * When the allowlist is enforced (the default), a DID may join only if it has an active entry in
 * {@code allowlist_entries}. An empty table therefore admits nobody — private by default, which is the
 * safe posture for a server holding real player state. When the operator has explicitly disabled
 * enforcement ({@code eyeandsickle.allowlist.enabled=false}), any authenticated DID may join; that is a
 * chosen openness, never an accidental one, because the unset default is closed.
 *
 * <h2>Only ever asked about an authenticated DID</h2>
 *
 * This runs <em>after</em> {@link AtProtoIdentityProvider} has proven the caller controls the DID. It
 * never decides authentication and it never sees an unverified identity — the sign-in flow authenticates
 * first and gates second, so the server never reveals whether an unauthenticated DID is on the list, and
 * never admits one it has not verified.
 */
@Service
public class AllowlistPolicy {

    private final AllowlistRepository allowlist;
    private final AllowlistProperties properties;

    /**
     * @param allowlist the durable, runtime-editable allowlist table
     * @param properties whether enforcement is on
     */
    public AllowlistPolicy(AllowlistRepository allowlist, AllowlistProperties properties) {
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Whether an authenticated DID may join right now.
     *
     * @param did the authenticated identity
     * @return {@code true} if enforcement is off, or the DID has an active allowlist entry
     */
    public boolean permits(Did did) {
        Objects.requireNonNull(did, "did");
        if (!properties.isEnforced()) {
            // Explicit operator opt-out: run open. Still only reached for an already-authenticated DID.
            return true;
        }
        return allowlist.isAllowed(did);
    }
}
