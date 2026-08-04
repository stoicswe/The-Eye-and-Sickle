package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.Durability;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The market's rotating deals — what is on sale, for how much, and until when.
 *
 * <h2>⚠ A DISCOUNT CAN TURN THE ECONOMY'S SINK INTO A FAUCET, AND THE CEILING IS ARITHMETIC</h2>
 *
 * <strong>Read this before widening any band.</strong> Anything gated on ethecoin can be resold
 * ({@code Repac.sellable}), and resale is a fraction of the <em>catalogue</em> price, not of what was
 * paid. A market package ships at {@link Balance#MARKET_UPGRADE_VERSION_MAJOR}, so it fetches
 * {@code RESALE_PERCENT} scaled up by {@link Balance#UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR} per
 * major — <b>74.4% of retail</b> on today's constants.
 *
 * <p>So a discount deeper than <b>25.6%</b> makes buy-then-resell strictly profitable, with no
 * compute cost, no thermal recovery and no cap. That is not "a generous sale"; it is an unbounded
 * ethecoin generator, and it inverts the one real sink the economy has. {@code docs/design/00} §4's
 * meta-rule — compute is the master scarcity — is what it breaks.
 *
 * <p>⚠ {@link #breakEvenDiscountPercent()} therefore <strong>derives</strong> that number from the
 * constants rather than restating it, and {@link #maxDiscountPercent()} keeps a margin below it.
 * A re-tune of the resale percentage or the version scaling moves this automatically, and
 * {@code MarketDealsTest} fails the build if any band ever reaches it.
 *
 * <h2>⚠ Derived from the character and the date. Never drawn, never stored.</h2>
 *
 * Two callers asking on the same day get the same answer, and the storefront repaints on a clock —
 * a drawn deal would reshuffle the shelves every second. The same rule
 * {@code MempoolRules.projectionDepth} follows, for the same reason.
 *
 * <p>⚠ It is seeded on the <b>character</b> as well as the date, so two players are not looking at
 * an identical shop. Nothing about that is checkable by a player and nothing depends on it being
 * unpredictable — it is flavour, not a lottery, which is why an ordinary {@link Random} is right here
 * and a stored seed would be over-engineering.
 */
public final class MarketDeals {

    private MarketDeals() {}

    /** How long a set of deals stands. Three days, per the request that set this up. */
    public static final Duration ROTATION = Duration.ofDays(3);

    /**
     * How much of the shelf is on sale at once.
     *
     * <p>⚠ Not all of it. A shop where everything is discounted has no discounts — the sale price
     * simply becomes the price, and the strike-through stops meaning anything. Three is enough that
     * the shelf looks different every rotation and few enough that a deal is worth noticing.
     */
    public static final int DEALS_PER_WINDOW = 3;

    /** ⚠ The deepest a consumable may go. Comfortably under {@link #maxDiscountPercent()}. */
    public static final int CONSUMABLE_MAX_PERCENT = 20;

    /** The shallowest floor worth drawing — a 1% sale reads as a bug in the pricing. */
    public static final int CONSUMABLE_MIN_PERCENT = 10;

    /**
     * ⚠ The deepest a PERMANENT upgrade may go, and it is half the consumable band on purpose.
     *
     * <p>Not because permanents are more powerful — Invariant I2 already guarantees none of them is
     * a ceiling — but because a permanent is bought <em>once</em>. A deep discount on it removes a
     * fixed lump of ethecoin from the sink forever, for a decision the player was going to make
     * anyway. A consumable's discount is spent again next time.
     */
    public static final int PERMANENT_MAX_PERCENT = 10;

    /** @see #PERMANENT_MAX_PERCENT */
    public static final int PERMANENT_MIN_PERCENT = 5;

    /**
     * ⚠ A bundle's discount, and it is deliberately no deeper than a consumable's.
     *
     * <p>The saving in a bundle comes from the <em>number of items</em>, not from a steeper rate.
     * Rating a bundle more deeply would make it the only sensible way to buy anything, and the
     * per-item discount is what the resale ceiling constrains — a 40%-off bundle of two permanents is
     * two 40%-off permanents as far as {@code Repac.sell} is concerned.
     */
    public static final int BUNDLE_MAX_PERCENT = 18;

    /** @see #BUNDLE_MAX_PERCENT */
    public static final int BUNDLE_MIN_PERCENT = 12;

    /**
     * ⚠ The margin held below break-even, in percentage points.
     *
     * <p>At exactly break-even a purchase resells for what it cost — no profit, but no sink either,
     * and every rounding step in {@link #discounted} lands on one side or the other of it. Five
     * points keeps every deal a genuine loss to flip.
     */
    public static final int RESALE_SAFETY_MARGIN_PERCENT = 5;

    /**
     * The discount at which buying and immediately reselling breaks even.
     *
     * <p>⚠ Derived, never written down. {@code Repac.RESALE_PERCENT} and the version scaling are
     * exactly what an economy re-tune moves, and a hard-coded 25 here would keep passing its tests
     * while quietly becoming wrong.
     *
     * @return the percentage discount at which a resale returns what was paid
     */
    public static int breakEvenDiscountPercent() {
        // What a freshly-bought market package fetches, as a percentage of retail: the base resale
        // fraction, scaled by the version premium for the build the shop ships.
        long resaleOfRetail = Repac.RESALE_PERCENT
                * (100L
                        + Balance.UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR
                                * (Balance.MARKET_UPGRADE_VERSION_MAJOR - 1L))
                / 100L;
        // ⚠ Versions.resaleWei caps resale at retail - 1, so a premium that would exceed retail
        // cannot; mirrored here so this number never claims a break-even above 0%.
        return (int) Math.max(0L, 100L - Math.min(resaleOfRetail, 99L));
    }

    /**
     * The hardest discount any deal may carry.
     *
     * @return break-even less {@link #RESALE_SAFETY_MARGIN_PERCENT}
     */
    public static int maxDiscountPercent() {
        return Math.max(0, breakEvenDiscountPercent() - RESALE_SAFETY_MARGIN_PERCENT);
    }

    /** One discounted offering. */
    public record Deal(String offeringId, int percentOff, BigInteger fullPriceWei, BigInteger priceWei) {

        /** @return what this saves. */
        public BigInteger savingWei() {
            return fullPriceWei.subtract(priceWei);
        }
    }

    /**
     * Several offerings sold together for less than their sum.
     *
     * @param offeringIds what is in it, in shelf order
     * @param percentOff the rate applied to the combined price
     * @param fullPriceWei what the items cost separately
     * @param priceWei what the bundle costs
     */
    public record Bundle(List<String> offeringIds, int percentOff, BigInteger fullPriceWei, BigInteger priceWei) {

        public Bundle {
            offeringIds = List.copyOf(offeringIds);
        }

        /** @return what this saves against buying the items one at a time. */
        public BigInteger savingWei() {
            return fullPriceWei.subtract(priceWei);
        }
    }

    /**
     * Everything on sale for one three-day window.
     *
     * @param epoch which window this is — the rotation counter, for tests and for the seed
     * @param startsAt when it opened
     * @param endsAt when it closes and the shelf changes
     * @param deals the individually discounted offerings
     * @param bundle the multi-item offer, when the shelf could fill one
     */
    public record Window(long epoch, Instant startsAt, Instant endsAt, List<Deal> deals, Optional<Bundle> bundle) {

        public Window {
            deals = List.copyOf(deals);
        }

        /** @param offeringId the offering @return its deal, if it has one */
        public Optional<Deal> dealFor(String offeringId) {
            return deals.stream().filter(deal -> deal.offeringId().equals(offeringId)).findFirst();
        }

        /** @param now the instant @return how long until the shelf changes */
        public Duration remaining(Instant now) {
            Duration left = Duration.between(now, endsAt);
            return left.isNegative() ? Duration.ZERO : left;
        }
    }

    /**
     * Which rotation window an instant falls in.
     *
     * <p>⚠ {@code floorDiv}, not {@code /}. Integer division truncates toward zero, so an instant
     * before the Java epoch would share a window with one after it — and every save in this game
     * carries instants a test may set anywhere.
     *
     * @param now the instant
     * @return the window index
     */
    public static long epochOf(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), ROTATION.toSeconds());
    }

    /**
     * The deals standing for this character at this moment.
     *
     * @param save the character, for the seed
     * @param now the instant
     * @return the window
     */
    public static Window current(GameSave save, Instant now) {
        long epoch = epochOf(now);
        Instant startsAt = Instant.ofEpochSecond(epoch * ROTATION.toSeconds());
        Instant endsAt = startsAt.plus(ROTATION);

        // ⚠ Seeded on the character AND the window. Without the character every player sees one
        // shop; without the window it never rotates. The character id is a save field, so this is
        // stable across a reload — which it has to be, or closing the game would reroll the shelf.
        Random random = new Random(seed(save, epoch));

        List<Catalogue.Offering> sellable = Catalogue.offerings().stream()
                // ⚠ Only ethecoin-gated items can be discounted, because only they have a price. A
                // "sale" on a schematic-gated item would be a price where the whole point is that
                // there is none — Invariant I2, and the most misleading thing this panel could say.
                .filter(Catalogue.Offering::purchasable)
                .filter(offering -> offering.priceWei().signum() > 0)
                .sorted(java.util.Comparator.comparing(Catalogue.Offering::id))
                .toList();

        List<Catalogue.Offering> shuffled = new ArrayList<>(sellable);
        java.util.Collections.shuffle(shuffled, random);

        List<Deal> deals = new ArrayList<>();
        for (Catalogue.Offering offering : shuffled.stream().limit(DEALS_PER_WINDOW).toList()) {
            deals.add(dealFor(offering, random));
        }
        deals.sort(java.util.Comparator.comparing(Deal::offeringId));

        return new Window(epoch, startsAt, endsAt, deals, bundleFrom(sellable, random));
    }

    /**
     * What a player actually pays for an offering right now.
     *
     * <p>⚠ <strong>The one place a price is decided.</strong> Every caller — the storefront, the
     * purchase path, a refusal message quoting what something costs — must go through this, or the
     * shop advertises one number and the ledger records another.
     *
     * @param save the character
     * @param offering what is being priced
     * @param now the instant
     * @return the price, discounted if it is on offer
     */
    public static BigInteger priceFor(GameSave save, Catalogue.Offering offering, Instant now) {
        return current(save, now)
                .dealFor(offering.id())
                .map(Deal::priceWei)
                .orElseGet(offering::priceWei);
    }

    private static Deal dealFor(Catalogue.Offering offering, Random random) {
        int percent = percentFor(offering.durability(), random);
        BigInteger price = discounted(offering.priceWei(), percent);
        return new Deal(offering.id(), percent, offering.priceWei(), price);
    }

    private static int percentFor(Durability durability, Random random) {
        int min = durability == Durability.CONSUMABLE ? CONSUMABLE_MIN_PERCENT : PERMANENT_MIN_PERCENT;
        int max = durability == Durability.CONSUMABLE ? CONSUMABLE_MAX_PERCENT : PERMANENT_MAX_PERCENT;
        return bounded(min + random.nextInt(max - min + 1));
    }

    /**
     * ⚠ The last gate every discount passes through, whatever band produced it.
     *
     * <p>Clamped here rather than trusted to the constants, because the constants are exactly what a
     * re-tune moves — and the failure of a too-deep discount is silent: the shop still works, the
     * price still renders, and the economy quietly acquires a money printer.
     */
    private static int bounded(int percent) {
        return Math.max(0, Math.min(percent, maxDiscountPercent()));
    }

    /**
     * ⚠ Rounds the price UP, so the discount is never deeper than advertised.
     *
     * <p>Integer division truncates, which would round the PRICE down and the discount up — by at
     * most a wei, but in the one direction the resale ceiling is guarding.
     */
    private static BigInteger discounted(BigInteger priceWei, int percentOff) {
        BigInteger keep = BigInteger.valueOf(100L - percentOff);
        BigInteger hundred = BigInteger.valueOf(100L);
        BigInteger[] divided = priceWei.multiply(keep).divideAndRemainder(hundred);
        return divided[1].signum() == 0 ? divided[0] : divided[0].add(BigInteger.ONE);
    }

    /**
     * Builds the window's bundle, if the shelf can fill one.
     *
     * <p>Two shapes, per the design: <b>two permanent upgrades</b>, or <b>one permanent and two
     * consumables</b>. ⚠ Never two consumables alone — a bundle of two cheap items saves a few
     * ethecoin and reads as filler, and the point of a bundle is to make a larger purchase feel like
     * a decision.
     *
     * <p>⚠ Returns empty rather than inventing a shape when the catalogue cannot fill either. The
     * catalogue is short and content-gapped ({@code W-3}), and a bundle padded with whatever was
     * left would put unrelated items together at a discount for no reason a player could read.
     */
    private static Optional<Bundle> bundleFrom(List<Catalogue.Offering> sellable, Random random) {
        List<Catalogue.Offering> permanent = new ArrayList<>(sellable.stream()
                .filter(offering -> offering.durability() == Durability.PERMANENT)
                .toList());
        List<Catalogue.Offering> consumable = new ArrayList<>(sellable.stream()
                .filter(offering -> offering.durability() == Durability.CONSUMABLE)
                .toList());
        java.util.Collections.shuffle(permanent, random);
        java.util.Collections.shuffle(consumable, random);

        List<Catalogue.Offering> chosen;
        if (random.nextBoolean() && permanent.size() >= 2) {
            chosen = List.of(permanent.get(0), permanent.get(1));
        } else if (!permanent.isEmpty() && consumable.size() >= 2) {
            chosen = List.of(permanent.get(0), consumable.get(0), consumable.get(1));
        } else if (permanent.size() >= 2) {
            chosen = List.of(permanent.get(0), permanent.get(1));
        } else {
            return Optional.empty();
        }

        int percent = bounded(BUNDLE_MIN_PERCENT + random.nextInt(BUNDLE_MAX_PERCENT - BUNDLE_MIN_PERCENT + 1));
        BigInteger full = chosen.stream()
                .map(Catalogue.Offering::priceWei)
                .reduce(BigInteger.ZERO, BigInteger::add);
        return Optional.of(new Bundle(
                chosen.stream().map(Catalogue.Offering::id).toList(), percent, full, discounted(full, percent)));
    }

    private static long seed(GameSave save, long epoch) {
        String character = save == null || save.characterId == null ? "" : save.characterId;
        return character.hashCode() * 31L + epoch;
    }
}
