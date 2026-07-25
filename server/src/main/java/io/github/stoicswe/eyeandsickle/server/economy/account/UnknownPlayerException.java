package io.github.stoicswe.eyeandsickle.server.economy.account;

/**
 * Thrown when an operation names a DID that is not a player on this server.
 *
 * <p>Its own type, not a generic {@code IllegalArgumentException}, so the REST layer can map it to
 * {@code 404} while mapping a genuine bad request to {@code 400}. A mint into a non-existent account,
 * or a balance query for an unknown DID, is a "not here", not a "malformed".
 *
 * <p>Note what it is <em>not</em> used for: a ledger counterparty that is an NPC or a remote DID is
 * not an error — the ledger has no foreign key to {@code players} precisely because a holder may be
 * off-server. This exception is for the local-account operations (mint recipient, balance lookup) that
 * genuinely require a row here.
 */
public class UnknownPlayerException extends RuntimeException {

    private final String did;

    /**
     * @param did the DID that resolved to no local player
     */
    public UnknownPlayerException(String did) {
        super("No player on this server has DID " + did);
        this.did = did;
    }

    /**
     * @return the offending DID
     */
    public String did() {
        return did;
    }
}
