package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Whether the goods are in the listing or merely promised.
 *
 * <h2>⚠ THIS IS THE WHOLE DECISION A BUYER MAKES, and it must never be a detail</h2>
 *
 * There is <b>no escrow</b> on this market. Money moves the instant a buyer commits, and what they
 * get back depends entirely on which of these two the seller chose — so the mode is the single most
 * important thing on a listing, more important than the price. A screen that showed the price
 * prominently and the mode quietly would be selling risk without naming it.
 */
public enum DeliveryMode {

    /**
     * The item is held with the listing and transfers the moment it is paid for.
     *
     * <p>⚠ The seller has already given it up — it left their storage when they listed it — so there
     * is nothing left for them to withhold. This is the safe side of the market and it costs the
     * seller the use of the item while it sits unsold, which is the price of being trusted.
     */
    ATTACHED,

    /**
     * The seller keeps the item and owes delivery.
     *
     * <p>⚠ The buyer pays now and receives nothing until the seller acts. If the seller never acts,
     * the buyer has lost the money — <b>there is no refund</b>, because there was never anything
     * holding it. All that stands between the two is the seller's reputation, which is exactly what
     * a reputation is for and why this market has one.
     */
    SEND_LATER;

    /** @return whether a buyer is taking counterparty risk. */
    public boolean risky() {
        return this == SEND_LATER;
    }
}
