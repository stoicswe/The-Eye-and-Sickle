package io.github.stoicswe.eyeandsickle.server.lan;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.UUID;

/**
 * Mints and recognises LAN identities.
 *
 * <h2>⚠ A LAN identity IS a DID — {@code did:easlan:<uuid>} — and that is the design</h2>
 *
 * The obvious implementation gives LAN players a separate {@code uuid} column beside {@code did}. That
 * would be worse in three specific ways:
 *
 * <ol>
 *   <li>Every table keyed on a player — characters, the ledger, storage, gates, faction reputation —
 *       would need a second key and a rule about which one is in force. That is a migration and a new
 *       class of bug in every query.
 *   <li>A nullable-or-the-other-one pair is exactly the shape where a query forgets one branch, and
 *       the forgotten branch here means "a player's items belong to nobody".
 *   <li>⚠ Most importantly: <strong>the method name carries the quarantine</strong>. A federated
 *       server that sees {@code did:easlan:…} knows instantly that this identity is not resolvable and
 *       not to be trusted — no lookup, no table, no flag that can be out of date. The refusal in
 *       {@link #isLanIdentity} is one string comparison anyone can audit.
 * </ol>
 *
 * <p>It is a syntactically valid DID that <strong>deliberately resolves to nothing</strong>. That is
 * not an oversight to fix later — an unresolvable DID is an honest description of an identity with no
 * proof behind it, and any code that tries to resolve one should fail rather than invent an answer.
 *
 * <h2>⚠ {@code easlan}, not {@code eas-lan}</h2>
 *
 * A DID method is {@code [a-z0-9]+} — no hyphen — in both {@link Did}'s pattern and the {@code is_did}
 * CHECK in the core migration, which are deliberately identical. {@code did:eas-lan:…} would be
 * rejected by the database three layers below where it was constructed.
 */
public final class LanIdentity {

    /**
     * The DID method for a LAN identity.
     *
     * <p>Not registered with the W3C and never will be. A method nobody else uses is the point: it
     * cannot collide with a real identity, and it cannot be mistaken for one.
     */
    public static final String METHOD = "did:easlan:";

    private LanIdentity() {}

    /**
     * Mints a fresh LAN identity.
     *
     * <p>⚠ {@link UUID#randomUUID()} — version 4, from a CSPRNG. This value is a
     * <strong>bearer token</strong>: whoever holds it is that player, with no second factor and no
     * signature ({@code docs/architecture/12-lan-mode.md} §2). A sequential id, a hash of the
     * username, or anything else guessable would let anyone on the network wear anyone's identity.
     *
     * @return a new, unguessable LAN identity
     */
    public static Did mint() {
        return Did.of(METHOD + UUID.randomUUID());
    }

    /**
     * Whether a DID is a LAN identity — i.e. whether it is quarantined.
     *
     * <p>⚠ The check federated code paths use to refuse LAN state ({@code 12} §1). Deliberately a
     * prefix comparison on the identity itself rather than a lookup: a flag in a table can be stale or
     * absent, and this cannot.
     *
     * @param did any DID
     * @return true if it was minted by a LAN server
     */
    public static boolean isLanIdentity(Did did) {
        return did != null && did.value().startsWith(METHOD);
    }

    /** @see #isLanIdentity(Did) */
    public static boolean isLanIdentity(String did) {
        return did != null && did.startsWith(METHOD);
    }
}
