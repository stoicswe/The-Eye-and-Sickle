package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * An order the player has resting on the Shadow Market.
 *
 * <h2>⚠ A BUY holds real money, and that is why this is state rather than a derived thing</h2>
 *
 * Everything else about the market is a pure function of the clock and can be recomputed. This
 * cannot: the player committed ethecoin when they placed it, and the ethecoin has to be somewhere.
 * It sits in {@link #escrowWei} — debited at placement, returned on cancel, spent on fill — because
 * the alternative is checking the balance at fill time, and a player who spent the money in between
 * would get an order that silently did not execute.
 *
 * <h2>⚠ A SELL holds the item, by id</h2>
 *
 * {@link #heldItemId} is the specific copy being sold, not the type. Items stopped stacking on
 * 2026-08-04 — two Tarpits are two things with different builds and different tiers — so an order
 * that named only the type would sell whichever one the code happened to find, and the player would
 * watch the wrong build leave the vault.
 */
public final class ShadowOrderState {

    public String orderId = UUID.randomUUID().toString();

    public String itemType = "";

    /** True to buy, false to sell. */
    public boolean buy = true;

    /** What the player will pay or accept, in wei. */
    public BigInteger limitPriceWei = BigInteger.ZERO;

    public int quantity = 1;

    public Instant placedAt = Instant.EPOCH;

    /**
     * ⚠ Always zero. Kept as a field so an existing save still parses, and as a marker.
     *
     * <p>This market had escrow until 2026-08-04 and deliberately does not any more: it is a market
     * between people who can defect, and escrow is precisely the thing that makes defecting
     * impossible. A bid now commits nothing until it fills. ⚠ <b>Do not reintroduce it here</b> —
     * doing so would also collapse {@code DeliveryMode}'s two options into one, since the whole
     * difference between them is whether the buyer is carrying risk.
     *
     * <p>⚠ Initialised, never left null — the money-field rule {@code CLAUDE.md} records after
     * {@code ContributionState.creditedWei} threw an NPE on the login screen for want of one.
     */
    public BigInteger escrowWei = BigInteger.ZERO;

    /** For a sell, which copy. Empty for a buy. */
    public String heldItemId = "";

    public ShadowOrderState() {}
}
