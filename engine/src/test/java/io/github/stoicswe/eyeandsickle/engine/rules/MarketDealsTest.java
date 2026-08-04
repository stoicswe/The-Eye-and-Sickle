package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.Durability;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The market's rotating deals. */
class MarketDealsTest {

    private static final Instant T0 = Instant.parse("2026-08-04T12:00:00Z");

    private static GameSave saveWith(String characterId) {
        GameSave save = new GameSave();
        save.characterId = characterId;
        return save;
    }

    @Nested
    @DisplayName("⚠ the economy — a deal must never be worth flipping")
    class TheSink {

        /**
         * ⚠ THE ONE THAT MATTERS. Everything ethecoin-gated is resellable, and resale is a fraction
         * of the CATALOGUE price rather than of what was paid — so past a certain discount, buy-then-
         * resell is free money with no compute cost, and the economy's only real sink becomes a
         * faucet.
         *
         * <p>This walks a year of rotations across several characters and asserts no deal, and no
         * bundle, ever reaches break-even. It is the guard on a mistake that is completely silent:
         * the shop still works, the price still renders, and the ethecoin supply quietly inverts.
         */
        @Test
        @DisplayName("no deal in a year of rotations is ever profitable to buy and resell")
        void noDealIsWorthFlipping() {
            int breakEven = MarketDeals.breakEvenDiscountPercent();
            assertThat(breakEven)
                    .as("if resale ever reaches retail there is no safe discount at all, and the "
                            + "whole feature has to go rather than be tuned")
                    .isGreaterThan(MarketDeals.RESALE_SAFETY_MARGIN_PERCENT);

            for (String character : new String[] {"alpha", "bravo", "charlie", "delta", UUID.randomUUID().toString()}) {
                GameSave save = saveWith(character);
                for (long window = 0; window < 122; window++) {
                    Instant when = T0.plus(MarketDeals.ROTATION.multipliedBy(window));
                    MarketDeals.Window shelf = MarketDeals.current(save, when);

                    for (MarketDeals.Deal deal : shelf.deals()) {
                        assertThat(deal.percentOff())
                                .as("%s at window %d: %d%% off is at or past the %d%% break-even",
                                        deal.offeringId(), window, deal.percentOff(), breakEven)
                                .isLessThan(breakEven);
                        // And the arithmetic, not merely the percentage — rounding is where a
                        // "compliant" percentage still produces a price under break-even.
                        assertThat(deal.priceWei())
                                .as("%s must cost more than it resells for", deal.offeringId())
                                .isGreaterThan(Repac.resaleValue(
                                        deal.offeringId(),
                                        new io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion(
                                                io.github.stoicswe.eyeandsickle.engine.Balance
                                                        .MARKET_UPGRADE_VERSION_MAJOR,
                                                0)));
                    }
                    shelf.bundle().ifPresent(bundle -> assertThat(bundle.percentOff())
                            .as("a bundle's rate applies to every item in it, so it is bounded the same way")
                            .isLessThan(breakEven));
                }
            }
        }

        @Test
        @DisplayName("⚠ every band is declared below the derived ceiling")
        void thebandsAreWithinTheCeiling() {
            // The bands are constants somebody will widen. This fails at the declaration rather than
            // waiting for a rotation that happens to draw the top of the range.
            int max = MarketDeals.maxDiscountPercent();
            assertThat(MarketDeals.CONSUMABLE_MAX_PERCENT).isLessThanOrEqualTo(max);
            assertThat(MarketDeals.PERMANENT_MAX_PERCENT).isLessThanOrEqualTo(max);
            assertThat(MarketDeals.BUNDLE_MAX_PERCENT).isLessThanOrEqualTo(max);
        }

        @Test
        @DisplayName("consumables are discounted more deeply than permanent upgrades")
        void consumablesGetTheBetterDeals() {
            // The requested shape, pinned: a permanent is bought once, so a discount on it leaves the
            // sink permanently smaller for a decision the player was going to make anyway.
            assertThat(MarketDeals.CONSUMABLE_MAX_PERCENT).isGreaterThan(MarketDeals.PERMANENT_MAX_PERCENT);
            assertThat(MarketDeals.CONSUMABLE_MIN_PERCENT).isGreaterThan(MarketDeals.PERMANENT_MIN_PERCENT);
        }
    }

    @Nested
    @DisplayName("rotation")
    class Rotation {

        @Test
        @DisplayName("⚠ the same character on the same day gets the same shelf")
        void dealsAreDerivedNotDrawn() {
            // The storefront repaints on a clock. A drawn deal would reshuffle the shelves every
            // second — the same rule MempoolRules.projectionDepth follows.
            GameSave save = saveWith("stable");
            MarketDeals.Window first = MarketDeals.current(save, T0);
            MarketDeals.Window again = MarketDeals.current(save, T0.plus(Duration.ofHours(2)));

            assertThat(again.deals()).isEqualTo(first.deals());
            assertThat(again.bundle()).isEqualTo(first.bundle());
        }

        @Test
        @DisplayName("the shelf changes when the window does, and holds for three days")
        void dealsRotateOnSchedule() {
            GameSave save = saveWith("stable");
            MarketDeals.Window first = MarketDeals.current(save, T0);

            // Still inside the window an hour before it closes.
            assertThat(MarketDeals.current(save, first.endsAt().minus(Duration.ofHours(1))).epoch())
                    .isEqualTo(first.epoch());
            // A new one after it.
            assertThat(MarketDeals.current(save, first.endsAt()).epoch()).isEqualTo(first.epoch() + 1);
            assertThat(Duration.between(first.startsAt(), first.endsAt())).isEqualTo(MarketDeals.ROTATION);
        }

        @Test
        @DisplayName("two characters do not see an identical shop")
        void theShelfIsPerCharacter() {
            // Not a fairness property and nothing checks it in the fiction — it is flavour. But a
            // seed that ignored the character would make every player's shop identical, which is the
            // one thing that would make the rotation feel like a schedule rather than a shop.
            boolean anyDifference = false;
            for (long window = 0; window < 40 && !anyDifference; window++) {
                Instant when = T0.plus(MarketDeals.ROTATION.multipliedBy(window));
                anyDifference = !MarketDeals.current(saveWith("one"), when)
                        .deals()
                        .equals(MarketDeals.current(saveWith("two"), when).deals());
            }
            assertThat(anyDifference).isTrue();
        }

        @Test
        @DisplayName("⚠ an instant before 1970 does not share a window with one after it")
        void epochsFloorRatherThanTruncate() {
            // Integer division truncates toward zero, so -1s and +1s would both be window 0 and the
            // shelf would sit still across the boundary. Tests set instants anywhere.
            assertThat(MarketDeals.epochOf(Instant.ofEpochSecond(-1)))
                    .isLessThan(MarketDeals.epochOf(Instant.ofEpochSecond(1)));
        }
    }

    @Nested
    @DisplayName("what may be discounted")
    class Eligibility {

        @Test
        @DisplayName("⚠ a gated offering is never on sale — it has no price to discount")
        void onlyEthecoinGatedItemsGoOnSale() {
            // A "sale" on a schematic-gated item would put a price on the one thing whose whole point
            // is that it has none. Invariant I2, and the most misleading thing this panel could say.
            for (long window = 0; window < 60; window++) {
                MarketDeals.Window shelf =
                        MarketDeals.current(saveWith("x"), T0.plus(MarketDeals.ROTATION.multipliedBy(window)));
                for (MarketDeals.Deal deal : shelf.deals()) {
                    assertThat(Catalogue.byId(deal.offeringId()).orElseThrow().purchasable())
                            .as("%s is not purchasable and must never appear on the shelf", deal.offeringId())
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("a bundle is two permanents, or one permanent and two consumables")
        void bundlesTakeOneOfTwoShapes() {
            for (long window = 0; window < 60; window++) {
                MarketDeals.current(saveWith("y"), T0.plus(MarketDeals.ROTATION.multipliedBy(window)))
                        .bundle()
                        .ifPresent(bundle -> {
                            long permanent = bundle.offeringIds().stream()
                                    .map(id -> Catalogue.byId(id).orElseThrow())
                                    .filter(offering -> offering.durability() == Durability.PERMANENT)
                                    .count();
                            long consumable = bundle.offeringIds().size() - permanent;
                            assertThat(permanent >= 1).isTrue();
                            assertThat(bundle.offeringIds().size()).isBetween(2, 3);
                            // Never two consumables alone: a bundle of two cheap items saves a few
                            // ethecoin and reads as filler.
                            assertThat(permanent == 2 && consumable == 0 || permanent == 1 && consumable == 2)
                                    .as("bundle %s is neither shape", bundle.offeringIds())
                                    .isTrue();
                        });
            }
        }

        @Test
        @DisplayName("⚠ the discounted price rounds UP, so a discount is never deeper than advertised")
        void roundingFavoursTheSink() {
            // Integer division truncates, which rounds the PRICE down and the DISCOUNT up — by a wei,
            // but in the one direction the resale ceiling guards.
            GameSave save = saveWith("rounding");
            MarketDeals.Window shelf = MarketDeals.current(save, T0);
            for (MarketDeals.Deal deal : shelf.deals()) {
                BigInteger exact = deal.fullPriceWei()
                        .multiply(BigInteger.valueOf(100L - deal.percentOff()))
                        .divide(BigInteger.valueOf(100L));
                assertThat(deal.priceWei()).isGreaterThanOrEqualTo(exact);
            }
        }
    }

    @Nested
    @DisplayName("pricing")
    class Pricing {

        @Test
        @DisplayName("priceFor returns the deal price when there is one, and retail otherwise")
        void priceForHonoursTheShelf() {
            GameSave save = saveWith("pricing");
            MarketDeals.Window shelf = MarketDeals.current(save, T0);

            for (Catalogue.Offering offering : Catalogue.offerings()) {
                BigInteger charged = MarketDeals.priceFor(save, offering, T0);
                BigInteger expected = shelf.dealFor(offering.id())
                        .map(MarketDeals.Deal::priceWei)
                        .orElseGet(offering::priceWei);
                assertThat(charged)
                        .as("the shop must charge what it advertises for %s", offering.id())
                        .isEqualTo(expected);
            }
        }
    }
}
