package io.github.stoicswe.eyeandsickle.server.migration;

/**
 * An operator-only migration operation was attempted without operator authorization
 * ({@code docs/architecture/09-player-state-portability.md} §5).
 *
 * <h2>Why Option B is operator-gated</h2>
 *
 * Option B moves a character's <em>full</em> state — economy included — between two servers, and that is
 * only legitimate because both operators cooperate and therefore trust each other (§5). A
 * <em>player</em> must never be able to trigger it: a full-state bundle accepted from an untrusted source
 * would import freely-assertable economy, violating the portable/non-portable split (§3) and Invariant
 * I14. So the B endpoints demand an operator credential, and this is the refusal when one is absent or
 * wrong.
 *
 * <p>Maps to {@code 403 Forbidden}: the caller is understood, but is not an operator of this server.
 */
public class OperatorAuthorizationException extends RuntimeException {

    /**
     * @param detail why authorization failed, kept deliberately generic so it discloses nothing about the
     *     configured credential
     */
    public OperatorAuthorizationException(String detail) {
        super(detail);
    }
}
