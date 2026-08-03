package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.HandleResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real {@link VerifiedHandleDirectory}, over AT Protocol handle resolution.
 *
 * <p>Thin by design: every rule about <em>how</em> a handle is verified lives in
 * {@code protocol.identity.HandleResolver}, because the client has to run the same check for
 * provenance and two implementations of a security check is one implementation that is wrong. This
 * class is the Spring-shaped adapter and the failure policy, nothing more.
 *
 * <h2>⚠ The failure policy: never fail a sign-in over a handle</h2>
 *
 * A handle is a display name. A DID is the identity. If DNS is down, the PLC directory is having a
 * bad day, or the account's handle has genuinely lapsed, the correct outcome is that the player signs
 * in and sees their DID — <em>not</em> that they are locked out of a game they own characters in.
 * Bluesky renders this case as {@code handle.invalid} rather than refusing to load the account, and
 * the same reasoning applies here with more force, because here the account has a vault attached.
 *
 * <p>So every resolution failure is swallowed into {@code null}, which
 * {@link VerifiedHandleDirectory} defines as "checked, nothing verified" — the handle is dropped and
 * the DID is shown. ⚠ The one thing this must never do is fall back to the <em>unverified</em>
 * handle: that would turn every transient DNS failure into an impersonation window.
 */
public class AtprotoHandleDirectory implements VerifiedHandleDirectory {

    private static final Logger log = LoggerFactory.getLogger(AtprotoHandleDirectory.class);

    private final HandleResolver resolver;

    public AtprotoHandleDirectory(HandleResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public String verifiedHandleFor(Did did) {
        if (did == null) {
            return null;
        }
        try {
            return resolver.verifiedHandleFor(did.value());
        } catch (IdentityResolutionException unresolvable) {
            // Logged at INFO, not WARN: an account with a lapsed handle is a normal state of the
            // world, not an operator problem, and a log line that cries wolf teaches its reader to
            // stop looking — which is alert-fatigue(7), a page in this game's own manual.
            log.info("no verified handle for {}: {}", did, unresolvable.getMessage());
            return null;
        } catch (RuntimeException unexpected) {
            log.warn("handle resolution failed unexpectedly for {}", did, unexpected);
            return null;
        }
    }
}
