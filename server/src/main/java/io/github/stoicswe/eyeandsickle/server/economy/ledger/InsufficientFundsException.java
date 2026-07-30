package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;

/**
 * Thrown when a local player is asked to pay more ethecoin than they hold.
 *
 * <h2>Why this is a rejection and never a negative balance</h2>
 *
 * "Can they afford it" is a question asked <em>before</em> the subtraction, on the server ({@code
 * protocol/game/Ethecoin}). An overdraft is not a small negative balance travelling through the
 * system; it is a request that must not complete. This exception is that refusal, and the whole
 * transaction — the debit, the credit, the ledger row — rolls back with it, so no half-transfer is
 * ever recorded.
 *
 * <p>Its own type so the REST layer can answer {@code 402 Payment Required} rather than a generic
 * error, and so a caller (a purchase flow, a payout splitter) can distinguish "declined for funds"
 * from "declined for a bad request".
 */
public class InsufficientFundsException extends RuntimeException {

    private final String did;
    private final transient Ethecoin balance;
    private final transient Ethecoin required;

    /**
     * @param did the player who cannot cover it
     * @param balance what they hold
     * @param required what the transfer needs
     */
    public InsufficientFundsException(String did, Ethecoin balance, Ethecoin required) {
        super("Player " + did + " holds " + balance + " but the transfer requires " + required);
        this.did = did;
        this.balance = balance;
        this.required = required;
    }

    /** @return the payer */
    public String did() {
        return did;
    }

    /** @return the payer's balance at the time of refusal */
    public Ethecoin balance() {
        return balance;
    }

    /** @return the amount the transfer required */
    public Ethecoin required() {
        return required;
    }
}
