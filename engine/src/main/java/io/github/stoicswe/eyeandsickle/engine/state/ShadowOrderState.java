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
     * Ethecoin held against a buy. Zero for a sell.
     *
     * <p>⚠ Initialised, never left null — the money-field rule {@code CLAUDE.md} records after
     * {@code ContributionState.creditedWei} threw an NPE on the login screen for want of one.
     */
    public BigInteger escrowWei = BigInteger.ZERO;

    /** For a sell, which copy. Empty for a buy. */
    public String heldItemId = "";

    public ShadowOrderState() {}
}
