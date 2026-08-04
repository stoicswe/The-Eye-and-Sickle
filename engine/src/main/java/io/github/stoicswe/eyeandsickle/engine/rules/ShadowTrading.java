package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.ShadowListingState;
import io.github.stoicswe.eyeandsickle.engine.state.ShadowObligationState;
import io.github.stoicswe.eyeandsickle.protocol.game.DeliveryMode;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Listings, buy-now, and what happens when somebody does not deliver.
 *
 * <h2>⚠ THERE IS NO ESCROW, AND THAT IS THE FEATURE</h2>
 *
 * Money moves the instant a buyer commits. Nothing holds it, nothing can unwind it, and if the
 * seller never ships, <b>the buyer has simply lost it</b>. That is deliberate: a market between real
 * people who can defect is a different game from a market that cannot go wrong, and the whole point
 * of {@link #SHADOW} reputation is that it is the only thing standing in the gap.
 *
 * <p>⚠ It follows that the buyer must be able to see <em>which kind of trade this is</em> before
 * paying. {@link DeliveryMode} is therefore the most prominent thing on a listing, ahead of the
 * price — a screen that led with the number would be selling risk without naming it.
 *
 * <h2>The two modes, and why they differ in MECHANISM rather than in promise</h2>
 *
 * <ul>
 *   <li><b>{@code ATTACHED}</b> — the items <em>leave the seller's storage when the listing is
 *       created</em> and live on the listing. There is nothing left for the seller to withhold,
 *       because they no longer hold it. Safe for the buyer, and it costs the seller the use of the
 *       item while it sits unsold.
 *   <li><b>{@code SEND_LATER}</b> — the seller keeps it and owes delivery within
 *       {@link Balance#SHADOW_FULFILMENT_HOURS}. The buyer is trusting them.
 * </ul>
 *
 * ⚠ A reservation-by-id for {@code ATTACHED} would have looked equivalent and been a lie: the seller
 * could equip, sell elsewhere or delete the reserved copy, and the "safe" purchase would fail at
 * delivery with nothing able to explain why.
 *
 * <h2>⚠ You cannot list what you do not hold</h2>
 *
 * Checked at creation for <b>both</b> modes, not only for attached ones. A send-later listing for an
 * item the seller has never owned is a pure confidence trick with no cost of entry, and a market
 * where those are free is one where every listing is presumed fake.
 */
public final class ShadowTrading {

    private ShadowTrading() {}

    /** The subject the log files these under. */
    public static final String SHADOW = "market";

    /** Why a listing or a purchase was refused. */
    public enum Refusal {
        /** Not something this market lists — I2 keeps non-ethecoin gates off it. */
        NOT_LISTED,

        /** A price of zero, or a quantity of none. */
        MALFORMED,

        /** You do not have one of those to sell. */
        NOT_HELD,

        /** No such listing — it may have sold while the panel was open. */
        NO_SUCH_LISTING,

        /** Not enough ethecoin. */
        CANNOT_AFFORD,

        /** Nowhere to put it. */
        NO_ROOM,

        /** Your own listing. */
        YOUR_OWN,

        /** Nothing owed under that id. */
        NO_SUCH_OBLIGATION,

        /** You no longer hold what you promised. */
        CANNOT_DELIVER,

        /** Your standing means an up-front listing fee you cannot cover. */
        CANNOT_AFFORD_FEE
    }

    /** What happened. */
    public record Result(boolean ok, Refusal refusal, String message) {

        static Result refused(Refusal refusal, String message) {
            return new Result(false, refusal, message);
        }

        static Result ok(String message) {
            return new Result(true, null, message);
        }
    }

    // ── the listing fee ───────────────────────────────────────────────────────────────────────

    /**
     * The house's cut, in basis points, by how much the seller is trusted.
     *
     * <h2>⚠ Basis points, not percent — 1.5% is not an integer</h2>
     *
     * The trusted rate is a point and a half, and expressing the scale in whole percent would either
     * lose it or invite a {@code double}. Everything downstream is {@code BigInteger} arithmetic on
     * wei, so the rate has to be an exact integer at the resolution the design actually uses.
     *
     * <h2>⚠ The bands are the STANDING bands, not new ones</h2>
     *
     * {@code shady} / {@code known} / {@code unrated} / {@code trusted} already exist and are already
     * what the order book prices counterparties by. A second set of thresholds here would let a
     * seller read "trusted" on one screen and be charged the shady rate on another, with both screens
     * correct.
     */
    public static final int FEE_BP_TRUSTED = 150;

    /** @see #FEE_BP_TRUSTED */
    public static final int FEE_BP_STANDARD = 300;

    /** @see #FEE_BP_TRUSTED */
    public static final int FEE_BP_SHADY = 1200;

    /** At or above this reputation the fee drops to {@link #FEE_BP_TRUSTED}. */
    public static final int TRUSTED_AT = 60;

    /** Below this reputation the fee rises to {@link #FEE_BP_SHADY} and is charged twice. */
    public static final int SHADY_BELOW = -20;

    /**
     * What this seller is charged, in basis points of the transaction.
     *
     * @param save the seller
     */
    public static int feeBasisPoints(GameSave save) {
        int reputation = save == null ? 0 : save.traderReputation;
        if (reputation >= TRUSTED_AT) {
            return FEE_BP_TRUSTED;
        }
        if (reputation < SHADY_BELOW) {
            return FEE_BP_SHADY;
        }
        return FEE_BP_STANDARD;
    }

    /**
     * ⚠ Whether this seller pays the fee UP FRONT as well as on the sale.
     *
     * <p>Only the untrusted do, and they pay it <b>twice</b> — once to put the listing up and again
     * when it sells. That is the whole deterrent: a seller nobody trusts can still list, but
     * advertising costs them whether or not anything comes of it, so a shady account cannot paper the
     * board with offers it has no intention of honouring.
     */
    public static boolean chargedUpFront(GameSave save) {
        return feeBasisPoints(save) == FEE_BP_SHADY;
    }

    /**
     * The fee on an amount.
     *
     * <p>⚠ Rounds <b>up</b>, so the house never loses a wei to truncation and the seller can never
     * arrange a fee of zero by listing something small enough. Integer division truncates toward
     * zero, which would round every fee down.
     */
    public static BigInteger feeOn(BigInteger amount, GameSave save) {
        if (amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger bp = BigInteger.valueOf(feeBasisPoints(save));
        BigInteger scale = BigInteger.valueOf(10_000L);
        return amount.multiply(bp).add(scale).subtract(BigInteger.ONE).divide(scale);
    }

    /**
     * Takes the fee out of a sale and hands back what the seller actually receives.
     *
     * <h2>⚠ The fee is BURNED, not paid to anybody</h2>
     *
     * There is no house account to credit — and that is the point. Ethecoin taken here leaves
     * circulation, which makes every sale on this market a small sink. Paying it to an NPC would move
     * it somewhere the player could eventually take it back off, turning a sink into a delay.
     *
     * @return the net proceeds
     */
    public static BigInteger takeFee(GameSave save, BigInteger gross) {
        BigInteger fee = feeOn(gross, save);
        return gross.subtract(fee).max(BigInteger.ZERO);
    }

    // ── listing ───────────────────────────────────────────────────────────────────────────────

    /**
     * Puts something up for sale.
     *
     * @param itemIds which copies — ⚠ by id, because items do not stack and a type-named listing
     *     would part with whichever build the code found first
     * @param mode attached or promised
     */
    public static Result list(
            GameSave save,
            String itemType,
            BigInteger priceWei,
            List<String> itemIds,
            DeliveryMode mode,
            Instant now) {
        if (save == null || !ShadowMarket.listings().contains(itemType)) {
            return Result.refused(Refusal.NOT_LISTED, "the shadow market does not deal in that.");
        }
        if (priceWei == null || priceWei.signum() <= 0 || itemIds == null || itemIds.isEmpty()) {
            return Result.refused(Refusal.MALFORMED, "a price and at least one copy, both above zero.");
        }
        // ⚠ Possession is checked for BOTH modes. A send-later listing for something the seller has
        // never owned is a confidence trick with no cost of entry, and a market where those are free
        // is one where every listing is presumed fake.
        List<ItemState> held = new ArrayList<>();
        for (String itemId : itemIds) {
            Optional<ItemState> item = save.items.stream()
                    .filter(candidate -> candidate.itemId.equals(itemId))
                    .filter(candidate -> itemType.equals(candidate.itemType))
                    .filter(candidate -> !candidate.equipped)
                    .findFirst();
            if (item.isEmpty()) {
                return Result.refused(
                        Refusal.NOT_HELD,
                        "you do not hold an unequipped copy of that. Unequip it first, or list one you own.");
            }
            held.add(item.get());
        }

        // ⚠ THE UNTRUSTED PAY TO ADVERTISE, before anything is listed. Checked against the balance
        // and refused if they cannot cover it — taking a partial fee and putting the listing up
        // anyway would be the worst of both.
        BigInteger upFront = BigInteger.ZERO;
        if (chargedUpFront(save)) {
            upFront = feeOn(priceWei.multiply(BigInteger.valueOf(held.size())), save);
            if (save.ethecoinWei.compareTo(upFront) < 0) {
                return Result.refused(
                        Refusal.CANNOT_AFFORD_FEE,
                        "your standing means a "
                                + (feeBasisPoints(save) / 100) + "% listing fee up front — "
                                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(upFront)
                                + " — and you do not have it.");
            }
            save.ethecoinWei = save.ethecoinWei.subtract(upFront);
        }

        ShadowListingState listing = new ShadowListingState();
        listing.itemType = itemType;
        listing.priceWei = priceWei;
        listing.quantity = held.size();
        listing.delivery = mode.name();
        listing.listedAt = now;

        if (mode == DeliveryMode.ATTACHED) {
            // ⚠ REMOVED from storage, not reserved. See the class note — this is the whole
            // difference between the two modes, and a reservation would be a promise wearing a
            // mechanism's clothes.
            for (ItemState item : held) {
                listing.attachedItemIds.add(item.itemId);
                save.items.remove(item);
            }
        }
        save.shadowListings.add(listing);
        String feeNote = upFront.signum() > 0
                ? " A " + (feeBasisPoints(save) / 100) + "% listing fee of "
                        + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(upFront)
                        + " was taken up front and is not refundable."
                : "";
        return Result.ok(feeNote + (mode == DeliveryMode.ATTACHED
                ? "listed " + held.size() + " × " + Repac.displayName(itemType)
                        + ". The goods are held with the listing and transfer on sale."
                : "listed " + held.size() + " × " + Repac.displayName(itemType)
                        + " to send later. You will owe delivery within "
                        + Balance.SHADOW_FULFILMENT_HOURS + " hours of a sale."));
    }

    /**
     * Takes a listing down and returns anything attached to it.
     *
     * <p>⚠ Attached items go back to the <b>arrivals</b> tier, not to wherever they came from. The
     * listing did not remember, and inventing a destination would quietly file goods somewhere the
     * player did not choose — the arrivals tier is where everything else that turns up lands, and it
     * is the exposed one, which is the honest default.
     */
    public static Result cancel(GameSave save, String listingId, Instant now) {
        Optional<ShadowListingState> found = byId(save, listingId);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_LISTING, "no such listing.");
        }
        ShadowListingState listing = found.get();
        for (String itemId : listing.attachedItemIds) {
            ItemState item = new ItemState();
            item.itemId = itemId;
            item.itemType = listing.itemType;
            item.displayName = Repac.displayName(listing.itemType);
            item.tier = StorageRules.ARRIVALS.name();
            item.acquiredAt = now;
            item.origin = "returned from a shadow market listing";
            save.items.add(item);
        }
        // ⚠ The up-front fee is NOT returned. "The fee is always charged" is the rule, and a
        // refundable one would be no deterrent at all — a shady seller could paper the board and
        // withdraw for free, which is exactly the behaviour charging up front exists to stop.
        save.shadowListings.remove(listing);
        return Result.ok("listing withdrawn"
                + (listing.attachedItemIds.isEmpty() ? "." : "; the goods are back in your storage."));
    }

    public static Optional<ShadowListingState> byId(GameSave save, String listingId) {
        if (save == null || listingId == null) {
            return Optional.empty();
        }
        return save.shadowListings.stream()
                .filter(listing -> listingId.equals(listing.listingId))
                .findFirst();
    }

    /** Listings this character has up. */
    public static List<ShadowListingState> mine(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.shadowListings);
    }

    // ── buying ────────────────────────────────────────────────────────────────────────────────

    /**
     * Buys a specific listing outright.
     *
     * <h2>⚠ THE MONEY GOES FIRST AND DOES NOT COME BACK</h2>
     *
     * No escrow. On {@code ATTACHED} the goods arrive in the same call, so the risk is nil. On
     * {@code SEND_LATER} the buyer has paid and holds nothing but an obligation and a deadline —
     * which is exactly what they were shown before they confirmed.
     *
     * @param sellerHandle who is on the other side
     * @param sellerRating their standing, carried into the obligation for the log
     */
    public static Result buyNow(
            GameSave save,
            String itemType,
            BigInteger priceWei,
            int quantity,
            DeliveryMode mode,
            String sellerHandle,
            int sellerRating,
            Instant now) {
        if (save == null || !ShadowMarket.listings().contains(itemType)) {
            return Result.refused(Refusal.NO_SUCH_LISTING, "that listing is gone.");
        }
        if (priceWei == null || priceWei.signum() <= 0 || quantity <= 0) {
            return Result.refused(Refusal.MALFORMED, "that listing is malformed.");
        }
        BigInteger total = priceWei.multiply(BigInteger.valueOf(quantity));
        if (save.ethecoinWei.compareTo(total) < 0) {
            return Result.refused(
                    Refusal.CANNOT_AFFORD,
                    "not enough ethecoin — that is "
                            + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(total) + ".");
        }
        // ⚠ Room is checked BEFORE the money moves, and for an attached listing that is not
        // pedantry: the goods arrive in this same call, and taking payment for something with
        // nowhere to land would leave the buyer paid-up and empty-handed with no counterparty to
        // blame. A send-later purchase is checked too — the room has to exist when it is delivered,
        // and refusing now is far kinder than refusing in six hours.
        if (!StorageRules.roomFor(save, quantity)) {
            return Result.refused(Refusal.NO_ROOM, StorageRules.noRoomMessage(save, quantity));
        }

        save.ethecoinWei = save.ethecoinWei.subtract(total);

        if (mode == DeliveryMode.ATTACHED) {
            for (int i = 0; i < quantity; i++) {
                save.items.add(arrived(itemType, sellerHandle, now));
            }
            return Result.ok("bought " + quantity + " × " + Repac.displayName(itemType) + " from "
                    + sellerHandle + ". The goods were attached to the listing and are in your storage.");
        }

        ShadowObligationState owed = new ShadowObligationState();
        owed.itemType = itemType;
        owed.quantity = quantity;
        owed.paidWei = total;
        owed.counterpartyHandle = sellerHandle;
        // ⚠ owedByMe = false: the SELLER owes. The flag is what tells the panel whether to show a
        // countdown the player must act on or one they can only watch.
        owed.owedByMe = false;
        owed.incurredAt = now;
        owed.dueAt = now.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS));
        save.shadowObligations.add(owed);

        return Result.ok("paid " + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(total)
                + " to " + sellerHandle + ", who has " + Balance.SHADOW_FULFILMENT_HOURS
                + " hours to send it. Nothing holds that money but their reputation.");
    }

    private static ItemState arrived(String itemType, String from, Instant now) {
        ItemState item = new ItemState();
        item.itemType = itemType;
        item.displayName = Repac.displayName(itemType);
        // Everything acquired lands exposed; filing it is the player's decision.
        item.tier = StorageRules.ARRIVALS.name();
        item.acquiredAt = now;
        item.origin = "bought on the " + ShadowMarket.NAME + " from " + from;
        return item;
    }

    // ── delivering ────────────────────────────────────────────────────────────────────────────

    /**
     * Hands over what was promised.
     *
     * <p>⚠ Requires the item <em>now</em>, not at the time of sale. A seller who sold a copy and then
     * used it has nothing to deliver, and the refusal has to say so rather than conjuring one — the
     * obligation stays open and the clock keeps running, which is the correct consequence of having
     * spent something you had already sold.
     */
    public static Result fulfil(GameSave save, String obligationId, Instant now) {
        Optional<ShadowObligationState> found = obligation(save, obligationId);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_OBLIGATION, "nothing is owed under that.");
        }
        ShadowObligationState owed = found.get();
        if (!owed.owedByMe) {
            return Result.refused(Refusal.NO_SUCH_OBLIGATION, "that one is owed to you, not by you.");
        }
        List<ItemState> giving = new ArrayList<>();
        for (ItemState item : save.items) {
            if (owed.itemType.equals(item.itemType) && !item.equipped && giving.size() < owed.quantity) {
                giving.add(item);
            }
        }
        if (giving.size() < owed.quantity) {
            return Result.refused(
                    Refusal.CANNOT_DELIVER,
                    "you no longer hold " + owed.quantity + " × " + Repac.displayName(owed.itemType)
                            + " to send. The obligation stands and the clock is still running.");
        }
        save.items.removeAll(giving);
        save.shadowObligations.remove(owed);
        SecondaryMarket.deliver(save);
        return Result.ok("delivered " + owed.quantity + " × " + Repac.displayName(owed.itemType)
                + " to " + owed.counterpartyHandle + ".");
    }

    public static Optional<ShadowObligationState> obligation(GameSave save, String obligationId) {
        if (save == null || obligationId == null) {
            return Optional.empty();
        }
        return save.shadowObligations.stream()
                .filter(owed -> obligationId.equals(owed.obligationId))
                .findFirst();
    }

    public static List<ShadowObligationState> obligations(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.shadowObligations);
    }

    // ── somebody buying YOUR listing ──────────────────────────────────────────────────────────

    /**
     * How often a listing priced exactly at the market sells, per hour.
     *
     * <p>Tuned so a fairly-priced listing usually goes within a session or two rather than instantly:
     * selling has to be worth waiting for, or listing is strictly better than using the item.
     */
    public static final double SALE_RATE_AT_MARKET_PER_HOUR = 0.8;

    /**
     * How much faster an undercut listing sells, at the extreme.
     *
     * <p>A listing at the market floor sells this many times as fast as one at the market. Bounded,
     * because an unbounded bonus would make giving goods away the dominant strategy.
     */
    public static final double UNDERCUT_MAX_MULTIPLIER = 6.0;

    /**
     * Every this-many percent above the market halves the chance of a sale.
     *
     * <p>⚠ Steep on purpose — "significantly less" means a few percent over asking should visibly
     * stall, not merely slow. At 4% the rate halves; at 20% it is down to about 3% of the market rate.
     */
    public static final double OVERPRICE_HALVING_PERCENT = 4.0;

    /**
     * What an NPC purchase did, for the log.
     *
     * @param priceWei the gross, before the house took its cut
     * @param feeWei what the listing fee took — ⚠ reported separately, because a seller who only ever
     *     sees the net has no way to learn that their standing is costing them money
     */
    public record Sold(
            String itemType, BigInteger priceWei, BigInteger feeWei, int quantity, boolean owesDelivery) {}

    /**
     * Rolls whether a counterparty takes any of the player's listings.
     *
     * <h2>⚠ A RATE PER HOUR, never a chance per tick</h2>
     *
     * The obvious version rolls once per {@code tick()}, and it is wrong in two directions at once: a
     * client that ticks twice as often sells twice as fast, and a player returning from a three-day
     * absence gets exactly one roll for the whole absence. Both are invisible in play and both make
     * the number in {@link #SALE_RATE_AT_MARKET_PER_HOUR} mean nothing.
     *
     * <p>So the rate is per hour and each pass converts however much wall time actually elapsed into
     * a probability — {@code 1 - e^(-rate × hours)}. Tick frequency drops out, and a listing left up
     * over a long absence settles as though it had been standing the whole time, which it was.
     *
     * <h2>⚠ NOTHING SELLS ABOVE THE ARBITRAGE CEILING, at any probability</h2>
     *
     * This is a hard cutoff rather than a small number, and it is the guard that stops the feature
     * being a faucet. A player can name any price they like; an <b>NPC</b> will not pay above
     * {@code ShadowMarket.ceilingPercent()} of retail, because an NPC's ethecoin is invented and
     * buying above the storefront's floor would print money on a repeatable action. "Unlikely but
     * possible" is still a faucet — just a slower one.
     *
     * <p>⚠ Selling to a <b>real player</b> on a federated server is a transfer rather than an
     * issuance, so that path is not bound by this and must not inherit it.
     *
     * @param elapsed wall time since the last pass — the same figure the rest of the tick uses
     * @return what sold
     */
    public static List<Sold> settleListings(GameSave save, Duration elapsed, Instant now) {
        if (save == null || save.shadowListings.isEmpty() || elapsed == null || elapsed.isNegative()) {
            return List.of();
        }
        double hours = elapsed.toMillis() / 3_600_000.0d;
        if (hours <= 0) {
            return List.of();
        }
        List<Sold> sold = new ArrayList<>();
        for (ShadowListingState listing : List.copyOf(save.shadowListings)) {
            double rate = saleRatePerHour(save, listing, now);
            if (rate <= 0) {
                continue;
            }
            double chance = 1 - Math.exp(-rate * hours);
            long roll = Math.floorMod(
                    hash(listing.listingId.hashCode(), now.getEpochSecond(), 0x5A1E), 1_000_000L);
            if (roll >= (long) (chance * 1_000_000L)) {
                continue;
            }
            sold.add(sell(save, listing, now));
        }
        return sold;
    }

    /**
     * How fast one listing sells, per hour.
     *
     * <p>⚠ Returns <b>zero</b> above the arbitrage ceiling. See {@link #settleListings}.
     *
     * @return the rate, or zero if no counterparty would take it
     */
    public static double saleRatePerHour(GameSave save, ShadowListingState listing, Instant now) {
        BigInteger mid = ShadowMarket.midAt(save, listing.itemType, now);
        BigInteger retail = io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(listing.itemType)
                .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::priceWei)
                .orElse(BigInteger.ZERO);
        if (mid.signum() <= 0 || retail.signum() <= 0 || listing.priceWei.signum() <= 0) {
            return 0;
        }
        // ⚠ The hard cutoff, before any curve. An NPC's ethecoin is invented; paying above the
        // storefront's floor for it would be issuance, repeatable, with every screen still correct.
        BigInteger ceiling = retail.multiply(BigInteger.valueOf(ShadowMarket.ceilingPercent()))
                .divide(BigInteger.valueOf(100));
        if (listing.priceWei.compareTo(ceiling) > 0) {
            return 0;
        }
        double ratio = new java.math.BigDecimal(listing.priceWei)
                .divide(new java.math.BigDecimal(mid), java.math.MathContext.DECIMAL64)
                .doubleValue();
        double multiplier;
        if (ratio <= 1) {
            // Undercutting: faster, scaling towards the cap as the price approaches the band floor.
            double under = Math.min(1, 1 - ratio);
            multiplier = 1 + under * (UNDERCUT_MAX_MULTIPLIER - 1) * 2;
            multiplier = Math.min(UNDERCUT_MAX_MULTIPLIER, multiplier);
        } else {
            // Overpricing: halved every OVERPRICE_HALVING_PERCENT above the market.
            double overPercent = (ratio - 1) * 100;
            multiplier = Math.pow(0.5d, overPercent / OVERPRICE_HALVING_PERCENT);
        }
        return SALE_RATE_AT_MARKET_PER_HOUR * multiplier;
    }

    /**
     * Completes one unit of a sale.
     *
     * <p>⚠ ONE unit at a time. A listing of three selling out in a single roll would make quantity a
     * multiplier on luck rather than on time, and the player would never see the middle of it.
     */
    private static Sold sell(GameSave save, ShadowListingState listing, Instant now) {
        // ⚠ The fee comes off the PROCEEDS, so it is charged the moment the payment goes through and
        // is unaffected by whether the seller ever delivers. A fee taken at delivery instead would be
        // one a defaulting seller never paid.
        BigInteger fee = feeOn(listing.priceWei, save);
        save.ethecoinWei = save.ethecoinWei.add(listing.priceWei.subtract(fee).max(BigInteger.ZERO));
        boolean owes = listing.sendLater();

        if (owes) {
            // ⚠ The seller now OWES, on the same six-hour clock a buyer's purchase creates. This is
            // the whole point of the send-later mode reaching the player from the other side: they
            // have the money and somebody is waiting.
            ShadowObligationState owed = new ShadowObligationState();
            owed.itemType = listing.itemType;
            owed.quantity = 1;
            owed.paidWei = listing.priceWei;
            owed.counterpartyHandle = ShadowMarket.traderAt(save, listing.itemType, true, 0,
                            now.getEpochSecond() / ShadowMarket.TICK.toSeconds())
                    .handle();
            owed.owedByMe = true;
            owed.incurredAt = now;
            owed.dueAt = now.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS));
            save.shadowObligations.add(owed);
        } else if (!listing.attachedItemIds.isEmpty()) {
            // Attached: the copy was already off the player's books, so the sale simply consumes it.
            listing.attachedItemIds.removeFirst();
        }

        listing.quantity--;
        if (listing.quantity <= 0) {
            save.shadowListings.remove(listing);
        }
        return new Sold(listing.itemType, listing.priceWei, fee, 1, owes);
    }

    /** SplitMix64's finaliser, so a roll is reproducible and does not perturb the save's own stream. */
    private static long hash(long seed, long a, long b) {
        long x = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    // ── the deadline ──────────────────────────────────────────────────────────────────────────

    /** What a settled obligation did, for the log. */
    public record Lapsed(String itemType, String counterparty, boolean byMe, BigInteger paidWei) {}

    /**
     * Applies the consequence to anything past its deadline.
     *
     * <h2>⚠ ONCE, and {@code settled} is what makes it once</h2>
     *
     * The tick runs every second and an overdue obligation stays overdue, so without the flag a
     * seller who missed a deadline would be penalised once per second until they noticed — a
     * slow-motion account deletion rather than a consequence.
     *
     * <h2>⚠ NO REFUND, in either direction</h2>
     *
     * The money moved at purchase and nothing has held it since. Refunding here would quietly
     * reintroduce escrow, and with it the risk decision the two delivery modes exist to pose.
     *
     * @return what lapsed, so the caller can log each one
     */
    public static List<Lapsed> settleOverdue(GameSave save, Instant now) {
        if (save == null || save.shadowObligations.isEmpty()) {
            return List.of();
        }
        List<Lapsed> lapsed = new ArrayList<>();
        for (ShadowObligationState owed : List.copyOf(save.shadowObligations)) {
            if (owed.settled || now.isBefore(owed.dueAt)) {
                continue;
            }
            owed.settled = true;
            if (owed.owedByMe) {
                // The player defaulted. SecondaryMarket already owns what that costs and how the
                // rising detection chance works — this is the same act reached from a second place,
                // not a second reputation system.
                //
                // ⚠ Seeded from the SAVE's stream and committed back, never `new Random()`. Two
                // defections in one session must not share a roll, and the roll must survive a
                // reload — a fresh Random would make being caught depend on when the client happened
                // to be running.
                var rng = io.github.stoicswe.eyeandsickle.engine.breach.Rng.of(save);
                SecondaryMarket.defect(save, new java.util.Random(rng.nextLong()));
                rng.commit(save);
            }
            lapsed.add(new Lapsed(owed.itemType, owed.counterpartyHandle, owed.owedByMe, owed.paidWei));
            // ⚠ The row is removed either way. An obligation nobody can act on any more is history,
            // and leaving it on the panel would read as a debt the player could still discharge.
            save.shadowObligations.remove(owed);
        }
        return lapsed;
    }
}
