package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One thing offered for sale on the Shadow Market.
 *
 * <h2>⚠ A LISTING is not a resting order</h2>
 *
 * A resting order says "I will trade at this price if the market reaches it". A listing says
 * "this exact thing is for sale at this price, now" — it names a seller, a delivery mode and, when
 * attached, specific copies by id. A buyer chooses <em>this</em> listing rather than crossing a
 * spread, which is what makes {@code Buy now} a decision about a counterparty rather than about a
 * number.
 *
 * @param listingId what buy-now and cancel name
 * @param itemType the catalogue id
 * @param displayName what to call it
 * @param priceWei what the seller wants, per unit
 * @param quantity how many are left on it
 * @param delivery attached or promised — ⚠ the buyer's whole risk decision
 * @param sellerHandle who is selling
 * @param sellerStanding {@code trusted} / {@code known} / {@code unrated} / {@code shady}
 * @param sellerRating the number behind the standing
 * @param listedAt when it went up
 * @param mine whether this is the player's own listing
 * @param interestPerHour for the player's OWN listing, how briskly it is selling — ⚠ a RATE, not a
 *     probability, so it does not silently mean something different at a different tick frequency.
 *     Zero on somebody else's listing, and zero on one priced above what any counterparty will pay
 */
public record ShadowListing(
        String listingId,
        String itemType,
        String displayName,
        BigInteger priceWei,
        int quantity,
        DeliveryMode delivery,
        String sellerHandle,
        String sellerStanding,
        int sellerRating,
        Instant listedAt,
        boolean mine,
        double interestPerHour) {

    /**
     * How this listing is moving, in words.
     *
     * <p>⚠ Words rather than the number. "0.34/hr" is precise and means nothing to a player deciding
     * whether to drop their price; the point of showing it at all is that pricing above the market
     * has a cost, and the cost is time.
     */
    public String interest() {
        if (!mine) {
            return "";
        }
        if (interestPerHour <= 0) {
            // ⚠ Names the reason. A listing priced above what anybody will pay looks identical to one
            // that is merely slow, and a seller could wait forever without being told why.
            return "no takers at this price";
        }
        if (interestPerHour >= 2.0) {
            return "selling fast";
        }
        if (interestPerHour >= 0.7) {
            return "steady interest";
        }
        if (interestPerHour >= 0.2) {
            return "slow";
        }
        return "barely moving";
    }
}
