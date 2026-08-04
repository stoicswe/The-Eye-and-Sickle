package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.Durability;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The market's daily stock. */
class MarketStockTest {

    private static final Instant T0 = Instant.parse("2026-08-04T12:00:00Z");

    private static Catalogue.Offering byId(String id) {
        return Catalogue.byId(id).orElseThrow();
    }

    @Nested
    @DisplayName("the ration")
    class Ration {

        @Test
        @DisplayName("⚠ derived from (item, day) — the same day gives the same ration")
        void rationsAreDerivedNotDrawn() {
            // The storefront repaints on a clock. A drawn quantity would restock the shelf every
            // second, and "3 left" would mean nothing.
            Catalogue.Offering canary = byId("canary-token");
            int first = MarketStock.rationFor(canary, false, T0);
            assertThat(MarketStock.rationFor(canary, false, T0.plus(Duration.ofHours(6))))
                    .isEqualTo(first);
        }

        @Test
        @DisplayName("consumables are stocked deeper than permanents")
        void consumablesAreStockedDeeper() {
            // A consumable is bought over and over, so a low ration reads as the shop being broken.
            // A permanent is bought once — one or two is a genuine race.
            assertThat(MarketStock.CONSUMABLE_MIN).isGreaterThan(MarketStock.PERMANENT_MAX);
            for (long day = 0; day < 60; day++) {
                Instant when = T0.plus(Duration.ofDays(day));
                for (Catalogue.Offering offering : Catalogue.offerings()) {
                    if (!offering.purchasable()) {
                        continue;
                    }
                    int ration = MarketStock.rationFor(offering, false, when);
                    if (offering.durability() == Durability.CONSUMABLE) {
                        assertThat(ration).isBetween(MarketStock.CONSUMABLE_MIN, MarketStock.CONSUMABLE_MAX);
                    } else {
                        assertThat(ration).isBetween(MarketStock.PERMANENT_MIN, MarketStock.PERMANENT_MAX);
                    }
                }
            }
        }

        @Test
        @DisplayName("⚠ an item on offer is stocked shorter, but NEVER to zero")
        void anOfferIsScarcerButAlwaysBuyable() {
            // Scarcity is the pressure a discount is not — but a deal nobody can buy is worse than no
            // deal, so the reduction is subtracted and floored rather than scaled.
            for (long day = 0; day < 120; day++) {
                Instant when = T0.plus(Duration.ofDays(day));
                for (Catalogue.Offering offering : Catalogue.offerings()) {
                    if (!offering.purchasable()) {
                        continue;
                    }
                    int onOffer = MarketStock.rationFor(offering, true, when);
                    assertThat(onOffer)
                            .as("%s on day %d must still be buyable", offering.id(), day)
                            .isGreaterThan(0);
                    assertThat(onOffer).isLessThanOrEqualTo(MarketStock.rationFor(offering, false, when));
                }
            }
        }

        @Test
        @DisplayName("⚠ a gated offering is NOT STOCKED, which is not the same as zero")
        void gatedItemsAreNotStocked() {
            // "0 in stock" says come back tomorrow. A gated item is never coming, and the gate's whole
            // purpose is to say what it would actually take.
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                if (!offering.purchasable()) {
                    assertThat(MarketStock.rationFor(offering, false, T0)).isZero();
                }
            }
        }
    }

    @Nested
    @DisplayName("taking from the shelf")
    class Taking {

        @Test
        @DisplayName("buying reduces what is left, and the shelf refills next day")
        void stockDepletesAndRestocks() {
            GameSave save = new GameSave();
            MarketStock.Held held = new SaveMarketStock(save);
            Catalogue.Offering canary = byId("canary-token");

            int start = MarketStock.remaining(held, canary, false, T0);
            MarketStock.take(held, canary.id(), T0);
            assertThat(MarketStock.remaining(held, canary, false, T0)).isEqualTo(start - 1);

            Instant tomorrow = T0.plus(MarketStock.RESTOCK);
            assertThat(MarketStock.remaining(held, canary, false, tomorrow))
                    .as("the shelf refills — yesterday's takings do not carry over")
                    .isEqualTo(MarketStock.rationFor(canary, false, tomorrow));
        }

        @Test
        @DisplayName("buying out the shelf sells it out, and never goes negative")
        void sellingOutIsReachableAndBounded() {
            GameSave save = new GameSave();
            MarketStock.Held held = new SaveMarketStock(save);
            Catalogue.Offering canary = byId("canary-token");

            int start = MarketStock.remaining(held, canary, false, T0);
            for (int i = 0; i < start + 5; i++) {
                MarketStock.take(held, canary.id(), T0);
            }
            assertThat(MarketStock.remaining(held, canary, false, T0)).isZero();
            assertThat(MarketStock.inStock(held, canary, false, T0)).isFalse();
        }

        @Test
        @DisplayName("⚠ yesterday's keys are pruned rather than accumulating")
        void oldDaysAreForgotten() {
            // Day-scoped keys are never read again once the shelf restocks. Without pruning, a save
            // played daily for a year carries a few thousand dead entries — not a size problem, but a
            // save file a human can no longer read, and this one is meant to be readable.
            GameSave save = new GameSave();
            MarketStock.Held held = new SaveMarketStock(save);
            for (long day = 0; day < 30; day++) {
                MarketStock.take(held, "canary-token", T0.plus(Duration.ofDays(day)));
            }
            assertThat(save.marketTaken).hasSize(1);
        }

        @Test
        @DisplayName("two items on one day are tracked apart")
        void itemsDoNotShareACounter() {
            GameSave save = new GameSave();
            MarketStock.Held held = new SaveMarketStock(save);
            MarketStock.take(held, "canary-token", T0);

            assertThat(held.taken("canary-token", MarketStock.dayOf(T0))).isEqualTo(1);
            assertThat(held.taken("relay-hop", MarketStock.dayOf(T0)))
                    .as("buying one item must not deplete another")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("the clock")
    class Clock {

        @Test
        @DisplayName("⚠ an instant before 1970 does not share a day with one after it")
        void daysFloorRatherThanTruncate() {
            assertThat(MarketStock.dayOf(Instant.ofEpochSecond(-1)))
                    .isLessThan(MarketStock.dayOf(Instant.ofEpochSecond(1)));
        }

        @Test
        @DisplayName("stock refills daily, offers rotate every three days — they are different clocks")
        void restockIsFasterThanRotation() {
            // Deliberately out of step: a shelf that restocked exactly when the offers changed would
            // make the two indistinguishable, and the scarcity would read as part of the sale rather
            // than as a separate pressure.
            assertThat(MarketStock.RESTOCK).isLessThan(MarketDeals.ROTATION);
            assertThat(MarketStock.restocksAt(T0)).isAfter(T0);
            assertThat(Duration.between(T0, MarketStock.restocksAt(T0)))
                    .isLessThanOrEqualTo(MarketStock.RESTOCK);
        }
    }
}
