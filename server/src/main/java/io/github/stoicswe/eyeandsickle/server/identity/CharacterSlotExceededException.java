package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * Creating a character would exceed the account's slot cap
 * ({@code docs/architecture/09-player-state-portability.md} §1, §2).
 *
 * <p>The cap — {@code CharacterProperties.maxCharacters}, default 3 — is refused, never silently
 * absorbed: an account that already holds its maximum recognized characters cannot create another
 * DID-bound one. This is the honest counterpart to the compute system's {@code InsufficientCompute}
 * refusal — an impossible request on an authoritative server is an error, not a best effort.
 *
 * <p>It maps to {@code 409 Conflict}: the request was well-formed, but the account's current state does
 * not permit it. Note the cap is <em>soft</em> across the federation (I15): this refusal is what an
 * honest server does; a defecting server that minted a 4th character anyway would simply not be
 * recognized (09 §2).
 */
public class CharacterSlotExceededException extends RuntimeException {

    /**
     * @param accountDid the account at its cap
     * @param recognized how many characters are already recognized for the account
     * @param maxCharacters the configured cap
     */
    public CharacterSlotExceededException(Did accountDid, int recognized, int maxCharacters) {
        super("Account " + accountDid + " already holds " + recognized + " recognized characters, at the cap of "
                + maxCharacters + "; creating another is refused (docs/architecture/09-player-state-portability.md §2). "
                + "The cap is a soft, product limit — raise eyeandsickle.characters.max-characters to change it.");
    }
}
