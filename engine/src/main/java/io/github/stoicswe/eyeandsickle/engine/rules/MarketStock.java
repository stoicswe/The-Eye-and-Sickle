package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.Durability;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * How many of a thing the shop has today.
 *
 * <h2>⚠ STOCK IS WORLD STATE, NOT CHARACTER STATE — and that is why this is a PORT</h2>
 *
 * On a server the shelf is shared: every player on it draws from one stock, and a run on a discounted
 * item is supposed to be felt by everybody. Putting a counter in {@code GameSave} would give each
 * character their own private shelf, which is the exact opposite — a "limited stock" nobody can ever
 * be beaten to is a number, not a scarcity.
 *
 * <p>So this class decides <b>how much stock exists</b> and {@link Held} records <b>what has been
 * taken</b>, with the store behind {@link Held} chosen by the mode:
 *
 * <ul>
 *   <li><b>Solo</b> — the character's own save. There is one player, so per-character and per-world
 *       are the same thing, and the behaviour matches LAN by construction.
 *   <li><b>LAN and federated</b> — the server's database, one row per (server, item, day). ⚠ Not
 *       built: {@code W-7} in {@code docs/design/15-open-questions.md}, stubbed the same way the
 *       other server seams are.
 * </ul>
 *
 * <h2>⚠ The ration is DERIVED from (item, day), never drawn and never stored</h2>
 *
 * Same rule as {@link MarketDeals}: the storefront repaints on a clock, and a drawn quantity would
 * restock the shelf every second. What is <em>stored</em> is only the count taken — the smallest fact
 * that cannot be recomputed.
 *
 * <h2>⚠ Stock is the sink that a discount is not</h2>
 *
 * A deal lowers the price, which takes ethecoin <em>out</em> of the sink. Scarcity raises urgency
 * without touching the price at all — it makes a player spend today rather than eventually, which is
 * the pressure a shop actually wants and the one that costs the economy nothing. That asymmetry is
 * why the discount bands are narrow ({@link MarketDeals}) and the stock bands are not.
 */
public final class MarketStock {

    private MarketStock() {}

    /** How often the shelf is restocked. Daily, per the request that set this up. */
    public static final Duration RESTOCK = Duration.ofDays(1);

    /**
     * ⚠ Consumables are stocked deep, permanents shallow, and the inversion is deliberate.
     *
     * <p>A consumable is bought over and over, so a low ration reads as the shop being broken rather
     * than as scarcity. A permanent is bought once and never again — one or two on the shelf is a
     * genuine race, and it is the only item type where "somebody else got it" is a real outcome.
     */
    public static final int CONSUMABLE_MIN = 6;

    /** @see #CONSUMABLE_MIN */
    public static final int CONSUMABLE_MAX = 14;

    /** @see #CONSUMABLE_MIN */
    public static final int PERMANENT_MIN = 1;

    /** @see #CONSUMABLE_MIN */
    public static final int PERMANENT_MAX = 3;

    /**
     * ⚠ An item on offer is stocked SHORTER, and this is the whole point of pairing the two systems.
     *
     * <p>A discount with unlimited stock is a price cut a player can take whenever they get round to
     * it. A discount with a short shelf is a decision. Subtracted rather than scaled so it cannot
     * reduce a ration to zero — a deal nobody can buy is worse than no deal.
     */
    public static final int ON_OFFER_REDUCTION = 2;

    /** Where the taken-count lives. Backed by the save in solo, by the server's database online. */
    public interface Held {

        /**
         * @param offeringId the item
         * @param day which restock window, from {@link #dayOf}
         * @return how many have been taken from that day's ration
         */
        int taken(String offeringId, long day);

        /**
         * Records one taken.
         *
         * <p>⚠ Must be atomic against concurrent buyers on a server: two players hitting the last
         * unit have to resolve to one sale and one refusal. The engine cannot enforce that from here
         * — it is the store's job, and a store that cannot promise it will oversell the shelf.
         *
         * @param offeringId the item
         * @param day the restock window
         */
        void take(String offeringId, long day);
    }

    /**
     * Which restock window an instant falls in.
     *
     * <p>⚠ {@code floorDiv}, for the reason {@link MarketDeals#epochOf} documents: truncation would
     * put an instant before 1970 in the same window as one after it.
     *
     * @param now the instant
     * @return the day index
     */
    public static long dayOf(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), RESTOCK.toSeconds());
    }

    /**
     * How many the shop stocked today, before anything was bought.
     *
     * @param offering the item
     * @param onOffer whether it is discounted today
     * @param now the instant
     * @return the day's ration
     */
    public static int rationFor(Catalogue.Offering offering, boolean onOffer, Instant now) {
        if (!offering.purchasable()) {
            // ⚠ A gated item has no stock because it has no sale. Reporting "0 in stock" would say
            // it is temporarily unavailable, when what is true is that it is never for sale at all —
            // two completely different messages, and the gate's whole purpose is the second one.
            return 0;
        }
        long day = dayOf(now);
        Random random = new Random(offering.id().hashCode() * 31L + day);
        int min = offering.durability() == Durability.CONSUMABLE ? CONSUMABLE_MIN : PERMANENT_MIN;
        int max = offering.durability() == Durability.CONSUMABLE ? CONSUMABLE_MAX : PERMANENT_MAX;
        int ration = min + random.nextInt(max - min + 1);
        // ⚠ Floored at one. An offer nobody can buy is worse than no offer, and a scarce permanent
        // could otherwise reach zero the moment it went on sale.
        return Math.max(1, onOffer ? ration - ON_OFFER_REDUCTION : ration);
    }

    /**
     * How many are left right now.
     *
     * @param held the store
     * @param offering the item
     * @param onOffer whether it is discounted today
     * @param now the instant
     * @return the remaining count, never negative
     */
    public static int remaining(Held held, Catalogue.Offering offering, boolean onOffer, Instant now) {
        int ration = rationFor(offering, onOffer, now);
        if (ration <= 0) {
            return 0;
        }
        return Math.max(0, ration - held.taken(offering.id(), dayOf(now)));
    }

    /**
     * Whether one can be bought.
     *
     * @param held the store
     * @param offering the item
     * @param onOffer whether it is discounted today
     * @param now the instant
     * @return true if stock remains
     */
    public static boolean inStock(Held held, Catalogue.Offering offering, boolean onOffer, Instant now) {
        return remaining(held, offering, onOffer, now) > 0;
    }

    /**
     * Takes one off the shelf.
     *
     * <p>⚠ The caller must have checked {@link #inStock} in the same breath as debiting. This does
     * not re-check, because a check here would be a second answer to a question the purchase path has
     * already asked — and the two could disagree across a restock boundary, taking a player's money
     * for a unit the shop then refuses to hand over.
     *
     * @param held the store
     * @param offeringId the item
     * @param now the instant
     */
    public static void take(Held held, String offeringId, Instant now) {
        held.take(offeringId, dayOf(now));
    }

    /**
     * When the shelf restocks.
     *
     * @param now the instant
     * @return the next restock
     */
    public static Instant restocksAt(Instant now) {
        return Instant.ofEpochSecond((dayOf(now) + 1) * RESTOCK.toSeconds());
    }
}
