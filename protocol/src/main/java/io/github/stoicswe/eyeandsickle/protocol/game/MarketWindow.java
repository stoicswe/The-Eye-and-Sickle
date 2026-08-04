package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What the market is charging right now — the storefront's whole model.
 *
 * <h2>⚠ A wire type, so the shop is the same shop online</h2>
 *
 * The rules that produce this live in the engine ({@code rules/MarketDeals}), and the engine runs on
 * a server for LAN and federated play. The view must therefore read <em>this</em> rather than calling
 * the rules directly, or the storefront would work in single player and render an empty shelf against
 * a home server — which is precisely the drift the {@code GameSession} port exists to prevent.
 *
 * <h2>⚠ RESULTS, never the rules that produced them</h2>
 *
 * Prices and an expiry, not the discount bands, the seed or the rotation length. A client holding the
 * bands could predict the next window, and predicting is one step from asserting
 * ({@code docs/architecture/13} §4). What a shop tells you is what things cost today.
 *
 * @param asOf the instant the rules answered — <b>the session's clock, never the wall clock</b>
 * @param opensAt when this shelf went up
 * @param closesAt when it changes
 * @param deals the individually discounted items
 * @param bundle the multi-item offer, when there is one
 * @param restocksAt when the shelf is refilled — sooner than {@code closesAt}, since stock is daily
 *     and offers rotate every three days
 * @param stock how many of each item are left, by offering id. ⚠ An ABSENT key means "not stocked",
 *     which is not the same as zero: a gated item has no stock because it is never for sale, and a
 *     sold-out one has none because somebody got there first. Rendering both as "0 left" would tell a
 *     player to come back tomorrow for something that is never coming.
 */
public record MarketWindow(
        Instant asOf,
        Instant opensAt,
        Instant closesAt,
        List<Deal> deals,
        Optional<Bundle> bundle,
        Instant restocksAt,
        java.util.Map<String, Integer> stock) {

    public MarketWindow {
        deals = List.copyOf(deals);
        stock = java.util.Map.copyOf(stock);
    }

    /**
     * @param offeringId the item
     * @return how many are left, or empty when the item is not stocked at all
     */
    public Optional<Integer> stockFor(String offeringId) {
        return Optional.ofNullable(stock.get(offeringId));
    }

    /**
     * @param offeringId the item
     * @return whether it can be bought right now
     */
    public boolean inStock(String offeringId) {
        return stockFor(offeringId).orElse(0) > 0;
    }

    /** @return how long until the shelf is refilled, measured on the session's clock. */
    public java.time.Duration untilRestock() {
        java.time.Duration left = java.time.Duration.between(asOf, restocksAt);
        return left.isNegative() ? java.time.Duration.ZERO : left;
    }

    /** A shelf with nothing on it — what a session answers when it cannot price anything. */
    public static MarketWindow none() {
        return new MarketWindow(
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(),
                Optional.empty(),
                Instant.EPOCH,
                java.util.Map.of());
    }

    /**
     * How long this shelf has left.
     *
     * <p>⚠ Measured against {@link #asOf}, which is the SESSION's clock, and never against
     * {@code Instant.now()}. The two agree in production and diverge in every test and every render
     * harness — a countdown built on the wall clock reported "8h 5m" against a window the game clock
     * had opened minutes earlier. {@code CLAUDE.md}'s standing rule: anything with a deadline takes
     * the session's clock.
     *
     * @return the remaining time, never negative
     */
    public java.time.Duration remaining() {
        java.time.Duration left = java.time.Duration.between(asOf, closesAt);
        return left.isNegative() ? java.time.Duration.ZERO : left;
    }

    /**
     * @param offeringId the item
     * @return its deal, if it has one
     */
    public Optional<Deal> dealFor(String offeringId) {
        return deals.stream().filter(deal -> deal.offeringId().equals(offeringId)).findFirst();
    }

    /**
     * One discounted item.
     *
     * @param offeringId which item
     * @param percentOff the headline number
     * @param fullPriceWei what it costs the rest of the time — the struck-through figure
     * @param priceWei what it costs now
     */
    public record Deal(String offeringId, int percentOff, BigInteger fullPriceWei, BigInteger priceWei) {}

    /**
     * Several items sold together.
     *
     * @param offeringIds what is in it
     * @param percentOff the rate on the combined price
     * @param fullPriceWei the items bought separately
     * @param priceWei the bundle
     */
    public record Bundle(List<String> offeringIds, int percentOff, BigInteger fullPriceWei, BigInteger priceWei) {

        public Bundle {
            offeringIds = List.copyOf(offeringIds);
        }
    }
}
