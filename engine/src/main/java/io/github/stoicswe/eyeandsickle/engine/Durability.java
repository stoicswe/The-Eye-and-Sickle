package io.github.stoicswe.eyeandsickle.engine;

/**
 * Whether an offering is spent or kept — the axis the market's deal bands turn on.
 *
 * <h2>⚠ Why this is a classification and not a guess</h2>
 *
 * Nothing already on {@code Catalogue.Offering} answers it. {@code equippedCycles == 0} is the
 * tempting proxy and it is wrong in both directions: a Net Sweep holds nothing while idle and is kept
 * forever, while a Relay hop is charged per session and holds nothing either. Inferring durability
 * from a compute figure would put the sweep ladder on consumable discounts, which is the deepest
 * band in the game.
 *
 * <h2>What it is used for, and what it is NOT</h2>
 *
 * It decides how deep a discount an offering may be given ({@code rules/MarketDeals}) and which side
 * of a bundle it can fill. It buys and gates nothing: an item's unlock gate is
 * {@code docs/design/02-unlock-gates.md}'s business and this does not touch it. ⚠ Two different
 * questions — "what does it take to be allowed this" and "is it spent when used" — and collapsing
 * them would be an unlock gate nobody wrote down.
 */
public enum Durability {

    /**
     * Spent, or bought again for the next use. Deeper discounts.
     *
     * <p>⚠ A consumable is where a discount does the least harm and the most good: it is bought
     * repeatedly, so a sale changes a decision a player makes often, and it never accumulates into a
     * capability. {@code docs/design/03-economy.md} §2 puts these in the 5–15 EC band precisely
     * because they are meant to be a recurring cost rather than a milestone.
     */
    CONSUMABLE,

    /**
     * Bought once and kept. Shallower discounts.
     *
     * <p>⚠ The reason the band is shallower is not caution about power — Invariant I2 already
     * guarantees nothing here is a ceiling — it is that a permanent purchase is made ONCE. A deep
     * discount on it removes a fixed lump of ethecoin from the game's only real sink, permanently,
     * for one decision the player was going to make anyway.
     */
    PERMANENT
}
