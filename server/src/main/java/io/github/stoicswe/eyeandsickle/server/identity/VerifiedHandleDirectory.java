package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * Answers "what handle has this DID been <em>proven</em> to own", and nothing weaker.
 *
 * <h2>Why the server needs its own seam over a resolver that already exists</h2>
 *
 * {@code protocol.identity.HandleResolver} does the actual work, bidirectionally
 * ({@code docs/architecture/10-oauth-and-did-resolution.md} §4.1). This interface exists so the
 * identity slice can depend on the <em>capability</em> without depending on the network: the same
 * shape {@link io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver} already uses, and
 * for the same reason — sign-in must be writable, testable and reviewable with no PDS, no DNS and no
 * directory in the loop.
 *
 * <h2>⚠ {@link #canVerify()} is the part that is easy to get wrong</h2>
 *
 * A {@code null} from {@link #verifiedHandleFor} means <strong>"I checked, and nothing verified"</strong>.
 * That is a real answer: the account's handle has lapsed, or somebody is claiming a handle they do not
 * own, and in both cases the handle must not be displayed. It is <em>not</em> the same as "nobody has
 * wired a resolver yet", which is the state this server ships in today.
 *
 * <p>Collapsing those two into one {@code null} would mean that turning verification <em>on</em> and
 * having it fail is indistinguishable from never having turned it on — so a server with a broken DNS
 * resolver would silently fall back to displaying unverified handles, which is precisely the outcome
 * §4.1 exists to prevent. Hence a second method, and hence {@link #unresolved()} overriding it.
 */
@FunctionalInterface
public interface VerifiedHandleDirectory {

    /**
     * @param did an authenticated DID
     * @return the handle this DID was confirmed to own, or {@code null} if none was
     */
    String verifiedHandleFor(Did did);

    /**
     * Whether this directory is able to verify anything at all.
     *
     * @return true for a real resolver; false for {@link #unresolved()}
     */
    default boolean canVerify() {
        return true;
    }

    /**
     * A directory that verifies nothing and says so — the default until a resolver is wired.
     *
     * <p>Callers see {@code canVerify() == false} and leave the handle exactly as the identity
     * provider supplied it, unverified and marked as such. The conservative reading of an unwired
     * seam is "we do not know", not "it is fine".
     *
     * @return the no-op directory
     */
    static VerifiedHandleDirectory unresolved() {
        return new VerifiedHandleDirectory() {
            @Override
            public String verifiedHandleFor(Did did) {
                return null;
            }

            @Override
            public boolean canVerify() {
                return false;
            }
        };
    }
}
