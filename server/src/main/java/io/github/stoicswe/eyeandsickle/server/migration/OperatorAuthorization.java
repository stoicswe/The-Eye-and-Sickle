package io.github.stoicswe.eyeandsickle.server.migration;

/**
 * The gate that makes Option B <strong>operator</strong>-authenticated rather than player-triggered
 * ({@code docs/architecture/09-player-state-portability.md} §5).
 *
 * <h2>Why full-state transfer must never be a player action</h2>
 *
 * Option B carries a character's whole state — economy included — between two servers, and that is only
 * legitimate because both operators cooperate and therefore trust each other (§5). If a player could
 * trigger it, they could hand a destination a full-state bundle from a server they control and import
 * freely-assertable economy, violating the portable/non-portable split (§3) and Invariant I14. This seam
 * is what proves the caller is an operator of this server before any full-state export or import runs.
 *
 * <p>The default {@link TokenOperatorAuthorization} checks a configured shared secret and — crucially —
 * <strong>denies when none is configured</strong>, so full-state transfer is off until an operator turns
 * it on. A deployment with a real operator identity system supersedes it via {@code
 * @ConditionalOnMissingBean}.
 */
public interface OperatorAuthorization {

    /**
     * Requires that the presented credential proves operator authority, or refuses.
     *
     * @param presentedToken the credential the request carried, or {@code null} if it carried none
     * @throws OperatorAuthorizationException if the caller is not an authorized operator of this server
     */
    void requireOperator(String presentedToken);
}
