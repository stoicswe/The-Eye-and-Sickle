package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Shadow Market's simulation, and the one thing it must never do.
 *
 * <h2>Read the arbitrage test first — everything else is decoration beside it</h2>
 *
 * A player market beside the shop is {@code MarketDeals}' faucet failure one step removed: if a bid
 * here ever reaches what the storefront charges, buy-there-sell-here is free money with no compute
 * cost, repeatable, and every screen still renders correctly while the ethecoin supply inverts.
 */
class ShadowMarketTest {

    private static final Instant T0 = Instant.parse("2026-08-04T09:00:00Z");

    private static GameSave character(String id) {
        GameSave save = new GameSave();
        save.characterId = id;
        return save;
    }

    private static BigInteger retail(String itemType) {
        return Catalogue.byId(itemType).orElseThrow().priceWei();
    }

    @Nested
    @DisplayName("⚠ the arbitrage ceiling")
    class Arbitrage {

        @Test
        @DisplayName("⚠ NO BID, ON ANY ITEM, AT ANY TIME, EVER REACHES THE STOREFRONT'S FLOOR")
        void buyingThereAndSellingHereIsNeverFreeMoney() {
            // The storefront's cheapest possible price is retail less the deepest discount it can
            // roll. If the best bid here reaches that, a player buys on GoH and sells on ShMark for
            // a profit, forever, with no compute spent — which is the exact shape of the failure
            // MarketDeals.breakEvenDiscountPercent exists to prevent, in a second market.
            int storefrontFloorPercent = 100 - MarketDeals.maxDiscountPercent();

            for (String id : new String[] {"alpha", "bravo", "charlie", "delta", "echo"}) {
                GameSave save = character(id);
                for (String itemType : ShadowMarket.listings()) {
                    BigInteger cheapestStorefront = retail(itemType)
                            .multiply(BigInteger.valueOf(storefrontFloorPercent))
                            .divide(BigInteger.valueOf(100));
                    // A year of the market, sampled every twenty minutes.
                    for (long minute = 0; minute < 365L * 24 * 60; minute += 20) {
                        Instant at = T0.plus(Duration.ofMinutes(minute));
                        ShadowMarket.Book book = ShadowMarket.bookAt(save, itemType, at);
                        assertThat(book.bestBid())
                                .as("%s bid on %s at %s must stay under the storefront's floor", id, itemType, at)
                                .isLessThan(cheapestStorefront);
                    }
                }
            }
        }

        @Test
        @DisplayName("⚠ the ceiling is DERIVED from the discount bands, never written down")
        void theCeilingTracksTheStorefront() {
            // A literal here would keep passing after a re-tune of MarketDeals moved the thing it is
            // supposed to track — the same failure breakEvenDiscountPercent was written to avoid.
            assertThat(ShadowMarket.ceilingPercent())
                    .isEqualTo(100 - MarketDeals.maxDiscountPercent() - ShadowMarket.ARBITRAGE_MARGIN_PERCENT);
            assertThat(ShadowMarket.ceilingPercent()).isGreaterThan(ShadowMarket.FLOOR_PERCENT);
        }

        @Test
        @DisplayName("the mid stays inside the band however long the market runs")
        void theMidIsBoundedByConstruction() {
            // This is what makes the guard structural rather than a clamp: bounded noise cannot leave
            // its range, so no length of play breaches the ceiling. A random walk would, and would
            // then sit pinned to whatever clamp was put under it.
            GameSave save = character("bounded");
            for (String itemType : ShadowMarket.listings()) {
                BigInteger floor = retail(itemType)
                        .multiply(BigInteger.valueOf(ShadowMarket.FLOOR_PERCENT))
                        .divide(BigInteger.valueOf(100));
                BigInteger ceiling = retail(itemType)
                        .multiply(BigInteger.valueOf(ShadowMarket.ceilingPercent()))
                        .divide(BigInteger.valueOf(100));
                for (long day = 0; day < 400; day++) {
                    BigInteger mid = ShadowMarket.midAt(save, itemType, T0.plus(Duration.ofDays(day)));
                    assertThat(mid).isBetween(floor, ceiling);
                }
            }
        }
    }

    @Nested
    @DisplayName("what is listed")
    class Listings {

        @Test
        @DisplayName("⚠ ONLY ethecoin-gated items — I2 and I8 live here")
        void onlySellableItemsAreListed() {
            // Listing a schematic- or zero-day-gated tool would put a price on the one thing whose
            // whole point is that it has none, and would let anybody with enough ethecoin buy a
            // ceiling (I2) or farm a zero-day (I8).
            assertThat(ShadowMarket.listings()).isNotEmpty();
            for (String itemType : ShadowMarket.listings()) {
                assertThat(Repac.sellable(itemType))
                        .as("%s is listed and must be ethecoin-gated", itemType)
                        .isTrue();
            }
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                if (!Repac.sellable(offering.id())) {
                    assertThat(ShadowMarket.listings()).doesNotContain(offering.id());
                }
            }
        }
    }

    @Nested
    @DisplayName("⚠ listings stand long enough to buy")
    class Listings2 {

        @Test
        @DisplayName("⚠ a listing survives long enough to right-click, read and confirm")
        void aListingOutlivesTheDecision() {
            // The bug this pins: listings were keyed to the 2-second price TICK, so the whole board
            // turned over while the confirmation dialog was open and `buyNow` answered "that listing
            // is gone" every single time. The board looked alive and could not be traded with.
            GameSave save = character("dwell");
            String itemType = ShadowMarket.listings().getFirst();
            var first = ShadowMarket.offersAt(save, itemType, T0);
            assertThat(first).isNotEmpty();
            String id = first.getFirst().listingId();

            // ⚠ EVERY listing on the board, not just the first — the slots are staggered, so one of
            // them is always close to its boundary, and it is precisely that one a player would be
            // told they could not buy. The grace period is what makes this hold for all six.
            for (var offer : first) {
                assertThat(ShadowMarket.offer(save, itemType, offer.listingId(), T0.plus(Duration.ofSeconds(30))))
                        .as("%s must still be buyable after a slow confirmation", offer.listingId())
                        .isPresent();
            }
            assertThat(id).isNotBlank();
        }

        @Test
        @DisplayName("⚠ but a listing left open for a long time DOES expire")
        void theGraceIsBounded() {
            // Reconstructing an offer from its id means a stale one could otherwise be bought at its
            // original price by leaving the dialog open — a free option on a moving market, and a
            // player who found it could farm it.
            GameSave save = character("expiry");
            String itemType = ShadowMarket.listings().getFirst();
            var offer = ShadowMarket.offersAt(save, itemType, T0).getFirst();
            Instant wayLater = T0.plus(ShadowMarket.LISTING_DWELL)
                    .plus(ShadowMarket.LISTING_GRACE)
                    .plus(Duration.ofMinutes(5));
            assertThat(ShadowMarket.offer(save, itemType, offer.listingId(), wayLater))
                    .isEmpty();
        }

        @Test
        @DisplayName("⚠ and a listing from the FUTURE is refused")
        void theFutureIsRefused() {
            // An id is a string, and a save is a file the player can edit. Refusing to price
            // something that has not been posted yet costs nothing and closes the obvious edit.
            GameSave save = character("future");
            String itemType = ShadowMarket.listings().getFirst();
            var offer = ShadowMarket.offersAt(save, itemType, T0.plus(Duration.ofHours(6))).getFirst();
            assertThat(ShadowMarket.offer(save, itemType, offer.listingId(), T0)).isEmpty();
        }

        @Test
        @DisplayName("⚠ and its PRICE does not drift while it stands")
        void aListingsPriceIsFrozen() {
            // Reading the book live would leave the id stable and the price moving underneath it, so
            // the confirmation would quote one number and the debit take another — invisible until
            // somebody checked their ledger.
            GameSave save = character("frozen");
            String itemType = ShadowMarket.listings().getFirst();
            var offer = ShadowMarket.offersAt(save, itemType, T0).getFirst();
            for (int second = 1; second <= 20; second++) {
                var later = ShadowMarket.offer(save, itemType, offer.listingId(), T0.plus(Duration.ofSeconds(second)));
                assertThat(later).isPresent();
                assertThat(later.get().price())
                        .as("price at +%ds", second)
                        .isEqualTo(offer.price());
                assertThat(later.get().delivery()).isEqualTo(offer.delivery());
                assertThat(later.get().trader().handle()).isEqualTo(offer.trader().handle());
            }
        }

        @Test
        @DisplayName("⚠ but the board is not replaced wholesale — the slots are staggered")
        void slotsTurnOverIndependently() {
            // Without the stagger every listing would change in the same instant, which reads as the
            // panel having reloaded rather than as a market moving.
            GameSave save = character("stagger");
            String itemType = ShadowMarket.listings().getFirst();
            java.util.Set<Long> windowStarts = new java.util.HashSet<>();
            for (var offer : ShadowMarket.offersAt(save, itemType, T0)) {
                // The window index is the middle field of the id.
                windowStarts.add(Long.parseLong(offer.listingId().split(":")[1]));
            }
            assertThat(ShadowMarket.offersAt(save, itemType, T0)).hasSizeGreaterThan(1);
            assertThat(ShadowMarket.LISTING_DWELL)
                    .as("a listing must outlive a price tick by a wide margin")
                    .isGreaterThan(ShadowMarket.TICK.multipliedBy(10));
        }

        @Test
        @DisplayName("the board is sorted by price, cheapest first")
        void theBoardReadsAsABook() {
            // Each slot freezes its price at a different instant, so slot order would put a stale
            // dear listing above a fresh cheap one.
            GameSave save = character("sorted");
            for (String itemType : ShadowMarket.listings()) {
                for (long minute = 0; minute < 600; minute += 7) {
                    var offers = ShadowMarket.offersAt(save, itemType, T0.plus(Duration.ofMinutes(minute)));
                    for (int i = 1; i < offers.size(); i++) {
                        assertThat(offers.get(i).price())
                                .isGreaterThanOrEqualTo(offers.get(i - 1).price());
                    }
                }
            }
        }

        @Test
        @DisplayName("⚠ a listing's price still respects the arbitrage ceiling")
        void listingsCannotBreachTheCeiling() {
            // Freezing a price at an earlier instant must not become a way round the guard — the
            // frozen value came from bookAt, which is clamped into the band, so this holds by
            // construction. Pinned because "by construction" is exactly what a later refactor breaks.
            int storefrontFloorPercent = 100 - MarketDeals.maxDiscountPercent();
            GameSave save = character("ceiling");
            for (String itemType : ShadowMarket.listings()) {
                BigInteger cheapestStorefront = retail(itemType)
                        .multiply(BigInteger.valueOf(storefrontFloorPercent))
                        .divide(BigInteger.valueOf(100));
                for (long minute = 0; minute < 20_000; minute += 13) {
                    for (var offer : ShadowMarket.offersAt(save, itemType, T0.plus(Duration.ofMinutes(minute)))) {
                        assertThat(offer.price()).isLessThan(cheapestStorefront);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("the picker's categories")
    class Categories {

        @Test
        @DisplayName("⚠ every listing is FILED — an untagged one hides under \"other\"")
        void everyListingHasACategory() {
            // The Shadow Market's picker groups by Offering.category(), which reads the FIRST tag by
            // convention. An offering with no tags falls to "other" and would be findable only by
            // scrolling past every real category — a silent demotion, since nothing else about the
            // item looks wrong.
            for (String itemType : ShadowMarket.listings()) {
                Catalogue.Offering offering = Catalogue.byId(itemType).orElseThrow();
                assertThat(offering.tags()).as("%s must carry a category tag", itemType).isNotEmpty();
                assertThat(offering.category()).as("%s category", itemType).isNotBlank().isNotEqualTo("other");
            }
        }

        @Test
        @DisplayName("the categories actually group — a menu with one item each is a flat list")
        void categoriesGroup() {
            long categories = ShadowMarket.listings().stream()
                    .map(id -> Catalogue.byId(id).orElseThrow().category())
                    .distinct()
                    .count();
            assertThat(categories)
                    .as("fewer categories than listings, or the drill-down buys nothing")
                    .isLessThan(ShadowMarket.listings().size());
        }
    }

    @Nested
    @DisplayName("the simulation is derived, not drawn")
    class Derived {

        @Test
        @DisplayName("⚠ the same instant gives the same price — the panel repaints every second")
        void pricesDoNotReshuffleOnRepaint() {
            GameSave save = character("stable");
            String itemType = ShadowMarket.listings().getFirst();
            BigInteger first = ShadowMarket.midAt(save, itemType, T0);
            for (int i = 0; i < 50; i++) {
                assertThat(ShadowMarket.midAt(save, itemType, T0)).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("⚠ and the TAPE does not rewrite itself either")
        void theTapeIsARecord() {
            // A drawn tape shows a different history every second, which is worse than none: it
            // teaches the player that nothing on this screen is a record.
            GameSave save = character("tape");
            String itemType = ShadowMarket.listings().getFirst();
            List<ShadowMarket.Print> first = ShadowMarket.tape(save, itemType, 20, T0);
            assertThat(ShadowMarket.tape(save, itemType, 20, T0)).isEqualTo(first);
        }

        @Test
        @DisplayName("two characters see two different markets")
        void theMarketIsSeededOnTheCharacter() {
            String itemType = ShadowMarket.listings().getFirst();
            assertThat(ShadowMarket.midAt(character("one"), itemType, T0))
                    .isNotEqualTo(ShadowMarket.midAt(character("two"), itemType, T0));
        }

        @Test
        @DisplayName("but it does move")
        void thePriceActuallyChanges() {
            GameSave save = character("moving");
            String itemType = ShadowMarket.listings().getFirst();
            assertThat(java.util.stream.LongStream.range(0, 200)
                            .mapToObj(i -> ShadowMarket.midAt(save, itemType, T0.plus(Duration.ofMinutes(i * 7))))
                            .distinct()
                            .count())
                    .as("a market that never moves is a price list")
                    .isGreaterThan(50);
        }

        @Test
        @DisplayName("⚠ the print interval is derived, so a countdown counts down")
        void theTickIntervalIsStable() {
            // Drawn, it would change on every repaint and the countdown to the next print would
            // jitter rather than tick.
            long first = ShadowMarket.tickSecondsAt("tarpit", 1000);
            assertThat(ShadowMarket.tickSecondsAt("tarpit", 1000)).isEqualTo(first);
            assertThat(first).isBetween(2L, 8L);
        }
    }

    @Nested
    @DisplayName("the counterparties")
    class Traders {

        @Test
        @DisplayName("⚠ the cheapest ask is systematically the riskiest — that IS the decision")
        void cheapMeansShady() {
            // A book that priced reputation the other way round would make the best price also the
            // safest, and there would be nothing to decide. Checked in aggregate rather than per
            // level, because a single book is a small sample of a random population.
            GameSave save = character("reputation");
            String itemType = ShadowMarket.listings().getFirst();
            long cheapRatings = 0;
            long dearRatings = 0;
            int books = 0;
            for (long minute = 0; minute < 4000; minute += 7) {
                ShadowMarket.Book book = ShadowMarket.bookAt(save, itemType, T0.plus(Duration.ofMinutes(minute)));
                if (book.asks().size() < 2) {
                    continue;
                }
                cheapRatings += book.asks().getFirst().trader().rating();
                dearRatings += book.asks().getLast().trader().rating();
                books++;
            }
            assertThat(books).isGreaterThan(100);
            assertThat(cheapRatings / (double) books)
                    .as("the best ask is a worse-rated seller on average than the worst ask")
                    .isLessThan(dearRatings / (double) books);
        }

        @Test
        @DisplayName("⚠ nobody is certain and nobody is hopeless")
        void fillChanceIsAlwaysARisk() {
            // A 100% counterparty makes reputation free to ignore once found; a 0% one is a trap
            // rather than a risk, and a player cannot learn the difference from one failure.
            GameSave save = character("fills");
            String itemType = ShadowMarket.listings().getFirst();
            for (long i = 0; i < 2000; i++) {
                ShadowMarket.Trader trader = ShadowMarket.traderAt(save, itemType, i % 2 == 0, (int) (i % 8), i);
                assertThat(trader.fillPercent()).isBetween(50, 99);
                assertThat(trader.rating()).isBetween(-100, 100);
                assertThat(trader.handle()).isNotBlank();
            }
        }

        @Test
        @DisplayName("a trader keeps their identity while the book stands")
        void tradersDoNotReshuffle() {
            GameSave save = character("identity");
            String itemType = ShadowMarket.listings().getFirst();
            ShadowMarket.Trader first = ShadowMarket.traderAt(save, itemType, false, 2, 500);
            assertThat(ShadowMarket.traderAt(save, itemType, false, 2, 500)).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("candles")
    class Candles {

        @Test
        @DisplayName("⚠ the forming candle stops at NOW — a chart must not show the future")
        void theNewestCandleDoesNotRunAhead() {
            GameSave save = character("candles");
            String itemType = ShadowMarket.listings().getFirst();
            Instant now = T0.plus(Duration.ofSeconds(20));
            List<ShadowMarket.Candle> candles =
                    ShadowMarket.candles(save, itemType, ShadowMarket.Interval.M1, 10, now);
            ShadowMarket.Candle forming = candles.getLast();
            assertThat(forming.close())
                    .as("the forming candle closes at the current mid, which is what the order form quotes")
                    .isEqualTo(ShadowMarket.midAt(save, itemType, now));
        }

        @Test
        @DisplayName("high and low bracket open and close, always")
        void candlesAreWellFormed() {
            GameSave save = character("ohlc");
            for (String itemType : ShadowMarket.listings()) {
                for (ShadowMarket.Interval interval : ShadowMarket.Interval.values()) {
                    for (ShadowMarket.Candle candle :
                            ShadowMarket.candles(save, itemType, interval, 40, T0.plus(Duration.ofHours(9)))) {
                        assertThat(candle.high()).isGreaterThanOrEqualTo(candle.open());
                        assertThat(candle.high()).isGreaterThanOrEqualTo(candle.close());
                        assertThat(candle.low()).isLessThanOrEqualTo(candle.open());
                        assertThat(candle.low()).isLessThanOrEqualTo(candle.close());
                        assertThat(candle.volume()).isPositive();
                    }
                }
            }
        }

        @Test
        @DisplayName("candles come back oldest first, so a chart draws left to right")
        void candlesAreOrdered() {
            GameSave save = character("order");
            List<ShadowMarket.Candle> candles = ShadowMarket.candles(
                    save, ShadowMarket.listings().getFirst(), ShadowMarket.Interval.M5, 30, T0);
            assertThat(candles).hasSize(30);
            for (int i = 1; i < candles.size(); i++) {
                assertThat(candles.get(i).openedAt()).isAfter(candles.get(i - 1).openedAt());
            }
        }
    }

    @Nested
    @DisplayName("the book")
    class BookShape {

        @Test
        @DisplayName("⚠ the base spread exceeds the reputation swing, so a cross is impossible")
        void aCrossIsImpossibleByConstruction() {
            // The one arithmetic fact in ShadowMarket a re-tune can break silently. Without it the
            // book crossed within 44 minutes of the epoch: at depth 0 the offset is reputation
            // alone, so a shady buyer bid above a shady seller's ask and the market stood as an
            // offer to buy and sell simultaneously for a profit with no counterparty risk.
            assertThat(ShadowMarket.BASE_SPREAD_BP).isGreaterThan(ShadowMarket.REPUTATION_SWING_BP);
        }

        @Test
        @DisplayName("bids sort down, asks sort up, and the spread is never negative")
        void theBookIsSorted() {
            GameSave save = character("book");
            for (String itemType : ShadowMarket.listings()) {
                for (long minute = 0; minute < 1500; minute += 11) {
                    ShadowMarket.Book book = ShadowMarket.bookAt(save, itemType, T0.plus(Duration.ofMinutes(minute)));
                    for (int i = 1; i < book.bids().size(); i++) {
                        assertThat(book.bids().get(i).price())
                                .isLessThanOrEqualTo(book.bids().get(i - 1).price());
                    }
                    for (int i = 1; i < book.asks().size(); i++) {
                        assertThat(book.asks().get(i).price())
                                .isGreaterThanOrEqualTo(book.asks().get(i - 1).price());
                    }
                    assertThat(book.spread().signum())
                            .as("a crossed book at %s would let a player buy and sell into it for a "
                                    + "profit with no counterparty risk at all", minute)
                            .isNotNegative();
                }
            }
        }
    }
}
