package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A listing this character has put up on the Shadow Market.
 *
 * <h2>⚠ ATTACHED means the items HAVE ALREADY LEFT storage</h2>
 *
 * {@link #attachedItemIds} is not a reservation — the {@code ItemState}s are removed from
 * {@code save.items} when the listing is created and live here until it sells or is cancelled. That
 * is the difference between the two delivery modes made mechanical rather than promised: an attached
 * listing has nothing left for the seller to withhold, because they no longer hold it.
 *
 * <p>A reservation-by-id would have looked equivalent and been a lie — the seller could equip, sell
 * elsewhere or delete the reserved copy, and the buyer's "safe" purchase would fail at delivery with
 * no mechanism able to say why.
 */
public final class ShadowListingState {

    public String listingId = UUID.randomUUID().toString();

    public String itemType = "";

    /** Per unit, in wei. */
    public BigInteger priceWei = BigInteger.ZERO;

    public int quantity = 1;

    /** {@code ATTACHED} or {@code SEND_LATER}. A string, because a save outlives the enum. */
    public String delivery = "ATTACHED";

    /**
     * The copies held with this listing. Empty for {@code SEND_LATER}.
     *
     * <p>⚠ Ids, not types. Items stopped stacking on 2026-08-04, so a listing that named a type
     * would hand over whichever copy the code found first — possibly a different build from the one
     * the seller meant to part with.
     */
    public List<String> attachedItemIds = new ArrayList<>();

    public Instant listedAt = Instant.EPOCH;

    public ShadowListingState() {}

    public boolean sendLater() {
        return "SEND_LATER".equals(delivery);
    }
}
