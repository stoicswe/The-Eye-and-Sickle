package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * A player authenticated successfully but is not permitted to join this server.
 *
 * <p>This is the allowlist refusal ({@code docs/architecture/03-server-and-federation.md} §1): the DID
 * is real and proven, but the operator has not allowed it in, so the server is closed to it. It is a
 * distinct type from {@link SignInException} because the causes are opposite — one is "we don't believe
 * you are who you say", the other is "we believe you and the answer is still no" — and they map to
 * different HTTP statuses ({@code 403} here). Keeping them separate stops an allowlist miss from being
 * misreported as an authentication failure, which would send an operator debugging the wrong thing.
 *
 * <p>Note the ordering it implies: the DID is authenticated <em>before</em> it is checked against the
 * allowlist. A server must not reveal whether an unauthenticated DID is on the list, and it cannot
 * gate on an identity it has not verified.
 */
public class SignInDeniedException extends SignInException {

    /**
     * @param did the authenticated-but-unauthorized identity, for the operator's log
     */
    public SignInDeniedException(Did did) {
        super("DID " + did + " is authenticated but not on this server's allowlist; join refused");
    }
}
