package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.DeliveryMode;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Listings, buy-now, and what happens when somebody does not deliver.
 *
 * <h2>The property that matters: there is no escrow</h2>
 *
 * Money moves when a buyer commits and nothing holds it. Every test here that looks like it is about
 * bookkeeping is really about that — if a refund ever appears, the two delivery modes collapse into
 * one and the whole risk decision goes with them.
 */
class ShadowTradingTest {

    private static final Instant T0 = Instant.parse("2026-08-04T09:00:00Z");

    private static String listed() {
        return ShadowMarket.listings().getFirst();
    }

    private static GameSave saveWith(int copies) {
        GameSave save = new GameSave();
        save.characterId = "trader";
        save.handle = "operator";
        save.ethecoinWei = Balance.ec("500");
        for (int i = 0; i < copies; i++) {
            ItemState item = new ItemState();
            item.itemType = listed();
            item.displayName = "Copy " + i;
            item.tier = StorageTier.VAULT.name();
            save.items.add(item);
        }
        return save;
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("⚠ you cannot list what you do not hold — in EITHER mode")
        void possessionIsRequired() {
            // Checked for send-later too, not only for attached. A promise-only listing for an item
            // the seller has never owned is a confidence trick with no cost of entry, and a market
            // where those are free is one where every listing is presumed fake.
            GameSave empty = saveWith(0);
            for (DeliveryMode mode : DeliveryMode.values()) {
                var refused = ShadowTrading.list(
                        empty, listed(), BigInteger.TEN, List.of("nonexistent"), mode, T0);
                assertThat(refused.ok()).as("%s", mode).isFalse();
                assertThat(refused.refusal()).isEqualTo(ShadowTrading.Refusal.NOT_HELD);
            }
            assertThat(empty.shadowListings).isEmpty();
        }

        @Test
        @DisplayName("⚠ ATTACHED REMOVES the item from storage — it is not a reservation")
        void attachedTakesTheGoods() {
            // The whole difference between the two modes. A reservation would look equivalent and be
            // a lie: the seller could equip, sell elsewhere or delete the reserved copy and the
            // "safe" purchase would fail at delivery with nothing able to say why.
            GameSave save = saveWith(1);
            String itemId = save.items.getFirst().itemId;

            var ok = ShadowTrading.list(save, listed(), BigInteger.TEN, List.of(itemId), DeliveryMode.ATTACHED, T0);
            assertThat(ok.ok()).isTrue();
            assertThat(save.items).as("it has left storage").isEmpty();
            assertThat(save.shadowListings.getFirst().attachedItemIds).containsExactly(itemId);
        }

        @Test
        @DisplayName("SEND_LATER leaves the item where it is")
        void sendLaterKeepsTheGoods() {
            GameSave save = saveWith(1);
            ShadowTrading.list(
                    save, listed(), BigInteger.TEN, List.of(save.items.getFirst().itemId), DeliveryMode.SEND_LATER, T0);
            assertThat(save.items).as("still the seller's until they send it").hasSize(1);
            assertThat(save.shadowListings.getFirst().attachedItemIds).isEmpty();
        }

        @Test
        @DisplayName("an equipped copy cannot be listed")
        void equippedIsNotForSale() {
            GameSave save = saveWith(1);
            save.items.getFirst().equipped = true;
            var refused = ShadowTrading.list(
                    save,
                    listed(),
                    BigInteger.TEN,
                    List.of(save.items.getFirst().itemId),
                    DeliveryMode.ATTACHED,
                    T0);
            assertThat(refused.ok()).isFalse();
        }

        @Test
        @DisplayName("withdrawing an attached listing gives the goods back")
        void cancelReturnsAttached() {
            GameSave save = saveWith(1);
            ShadowTrading.list(
                    save, listed(), BigInteger.TEN, List.of(save.items.getFirst().itemId), DeliveryMode.ATTACHED, T0);
            assertThat(save.items).isEmpty();

            assertThat(ShadowTrading.cancel(save, save.shadowListings.getFirst().listingId, T0).ok())
                    .isTrue();
            assertThat(save.items).hasSize(1);
            assertThat(save.items.getFirst().tier)
                    .as("to arrivals — the listing did not remember where it came from, and "
                            + "inventing a destination would file goods somewhere unchosen")
                    .isEqualTo(StorageRules.ARRIVALS.name());
        }
    }

    @Nested
    @DisplayName("buying")
    class Buying {

        @Test
        @DisplayName("ATTACHED hands the goods over in the same call")
        void attachedIsInstant() {
            GameSave save = saveWith(0);
            BigInteger before = save.ethecoinWei;

            var ok = ShadowTrading.buyNow(
                    save, listed(), Balance.ec("5"), 1, DeliveryMode.ATTACHED, "coldbroker", 70, T0);
            assertThat(ok.ok()).isTrue();
            assertThat(save.items).hasSize(1);
            assertThat(save.ethecoinWei).isLessThan(before);
            assertThat(save.shadowObligations).as("nothing is owed — it is already done").isEmpty();
        }

        @Test
        @DisplayName("⚠ SEND_LATER takes the money NOW and hands over nothing")
        void sendLaterIsARisk() {
            GameSave save = saveWith(0);
            BigInteger before = save.ethecoinWei;

            var ok = ShadowTrading.buyNow(
                    save, listed(), Balance.ec("5"), 1, DeliveryMode.SEND_LATER, "nullhand", -60, T0);
            assertThat(ok.ok()).isTrue();
            assertThat(save.items).as("nothing received").isEmpty();
            assertThat(save.ethecoinWei).as("and paid anyway").isEqualTo(before.subtract(Balance.ec("5")));
            assertThat(save.shadowObligations).hasSize(1);
            assertThat(save.shadowObligations.getFirst().owedByMe)
                    .as("the SELLER owes; the flag is what tells the panel whether to show a "
                            + "countdown to act on or one to watch")
                    .isFalse();
        }

        @Test
        @DisplayName("the deadline is Balance.SHADOW_FULFILMENT_HOURS from the sale")
        void theClockStartsAtTheSale() {
            GameSave save = saveWith(0);
            ShadowTrading.buyNow(
                    save, listed(), Balance.ec("5"), 1, DeliveryMode.SEND_LATER, "nullhand", -60, T0);
            assertThat(save.shadowObligations.getFirst().dueAt)
                    .isEqualTo(T0.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS)));
        }

        @Test
        @DisplayName("⚠ room is checked BEFORE the money moves")
        void nowhereToPutItIsRefusedFirst() {
            // For an attached listing the goods arrive in the same call, so taking payment for
            // something with nowhere to land would leave the buyer paid-up and empty-handed with no
            // counterparty to blame.
            GameSave save = saveWith(0);
            for (int i = 0; i < Balance.storageCapacity(StorageRules.ARRIVALS); i++) {
                ItemState filler = new ItemState();
                filler.itemType = "filler";
                filler.tier = StorageRules.ARRIVALS.name();
                save.items.add(filler);
            }
            BigInteger before = save.ethecoinWei;

            var refused = ShadowTrading.buyNow(
                    save, listed(), Balance.ec("5"), 1, DeliveryMode.ATTACHED, "coldbroker", 70, T0);
            assertThat(refused.ok()).isFalse();
            assertThat(save.ethecoinWei).isEqualTo(before);
        }

        @Test
        @DisplayName("cannot pay what you do not have")
        void affordabilityIsChecked() {
            GameSave save = saveWith(0);
            save.ethecoinWei = BigInteger.ONE;
            assertThat(ShadowTrading.buyNow(
                                    save, listed(), Balance.ec("5"), 1, DeliveryMode.ATTACHED, "x", 0, T0)
                            .ok())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("delivering, and not delivering")
    class Delivery {

        private static GameSave owing(Instant now) {
            GameSave save = saveWith(1);
            var owed = new io.github.stoicswe.eyeandsickle.engine.state.ShadowObligationState();
            owed.itemType = listed();
            owed.quantity = 1;
            owed.paidWei = Balance.ec("5");
            owed.counterpartyHandle = "buyer";
            owed.owedByMe = true;
            owed.incurredAt = now;
            owed.dueAt = now.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS));
            save.shadowObligations.add(owed);
            return save;
        }

        @Test
        @DisplayName("sending closes the obligation and credits the reputation")
        void deliveringPays() {
            GameSave save = owing(T0);
            int before = save.traderReputation;

            var ok = ShadowTrading.fulfil(save, save.shadowObligations.getFirst().obligationId, T0);
            assertThat(ok.ok()).isTrue();
            assertThat(save.items).as("the copy has gone").isEmpty();
            assertThat(save.shadowObligations).isEmpty();
            assertThat(save.traderReputation).isGreaterThan(before);
        }

        @Test
        @DisplayName("⚠ a seller who spent the copy CANNOT deliver, and the clock keeps running")
        void spendingWhatYouSoldIsYourProblem() {
            GameSave save = owing(T0);
            save.items.clear();

            var refused = ShadowTrading.fulfil(save, save.shadowObligations.getFirst().obligationId, T0);
            assertThat(refused.ok()).isFalse();
            assertThat(refused.refusal()).isEqualTo(ShadowTrading.Refusal.CANNOT_DELIVER);
            assertThat(save.shadowObligations).as("the obligation stands").hasSize(1);
        }

        @Test
        @DisplayName("⚠ missing the deadline costs reputation and refunds NOTHING")
        void defaultingCostsReputationAndNoMoneyMoves() {
            GameSave save = owing(T0);
            int before = save.traderReputation;

            var lapsed = ShadowTrading.settleOverdue(
                    save, T0.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS + 1)));
            assertThat(lapsed).hasSize(1);
            assertThat(save.traderReputation).isLessThan(before);
            assertThat(save.traderDefections).isEqualTo(1);
            // A refund here would quietly reintroduce escrow, and with it the risk decision the two
            // delivery modes exist to pose.
            assertThat(save.shadowObligations).isEmpty();
        }

        @Test
        @DisplayName("⚠ the penalty lands ONCE, not once per tick")
        void defaultIsSettledOnce() {
            // The tick runs every second and an overdue obligation stays overdue, so without the
            // settled flag a seller who missed a deadline would be penalised once per second until
            // they noticed — a slow-motion account deletion rather than a consequence.
            GameSave save = owing(T0);
            Instant late = T0.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS + 1));
            ShadowTrading.settleOverdue(save, late);
            int after = save.traderReputation;

            for (int i = 0; i < 50; i++) {
                assertThat(ShadowTrading.settleOverdue(save, late)).isEmpty();
            }
            assertThat(save.traderReputation).isEqualTo(after);
            assertThat(save.traderDefections).isEqualTo(1);
        }

        @Test
        @DisplayName("nothing lapses before its deadline")
        void theWindowIsRespected() {
            GameSave save = owing(T0);
            assertThat(ShadowTrading.settleOverdue(
                            save, T0.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS - 1))))
                    .isEmpty();
            assertThat(save.shadowObligations).hasSize(1);
        }

        @Test
        @DisplayName("⚠ a buyer whose seller defaults is NOT penalised — they did nothing wrong")
        void theBuyerKeepsTheirStanding() {
            GameSave save = saveWith(0);
            ShadowTrading.buyNow(
                    save, listed(), Balance.ec("5"), 1, DeliveryMode.SEND_LATER, "nullhand", -80, T0);
            int before = save.traderReputation;

            ShadowTrading.settleOverdue(save, T0.plus(Duration.ofHours(Balance.SHADOW_FULFILMENT_HOURS + 1)));
            assertThat(save.traderReputation)
                    .as("the reputation cost belongs to whoever failed to act")
                    .isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("the listing fee")
    class Fees {

        private static GameSave withStanding(int reputation) {
            GameSave save = saveWith(1);
            save.traderReputation = reputation;
            return save;
        }

        @Test
        @DisplayName("the three bands, and 1.5% is why it is basis points")
        void theBands() {
            assertThat(ShadowTrading.feeBasisPoints(withStanding(80))).isEqualTo(ShadowTrading.FEE_BP_TRUSTED);
            assertThat(ShadowTrading.feeBasisPoints(withStanding(0))).isEqualTo(ShadowTrading.FEE_BP_STANDARD);
            assertThat(ShadowTrading.feeBasisPoints(withStanding(-70))).isEqualTo(ShadowTrading.FEE_BP_SHADY);
            // Whole percent could not express the trusted rate at all.
            assertThat(ShadowTrading.FEE_BP_TRUSTED % 100).isNotZero();
        }

        @Test
        @DisplayName("⚠ only the untrusted are charged UP FRONT, and they pay it twice")
        void theUntrustedPayToAdvertise() {
            assertThat(ShadowTrading.chargedUpFront(withStanding(-70))).isTrue();
            assertThat(ShadowTrading.chargedUpFront(withStanding(0))).isFalse();
            assertThat(ShadowTrading.chargedUpFront(withStanding(80))).isFalse();

            GameSave shady = withStanding(-70);
            BigInteger before = shady.ethecoinWei;
            ShadowTrading.list(
                    shady,
                    listed(),
                    Balance.ec("10"),
                    List.of(shady.items.getFirst().itemId),
                    DeliveryMode.ATTACHED,
                    T0);
            assertThat(shady.ethecoinWei)
                    .as("12% of 10 EC taken to put it up")
                    .isEqualTo(before.subtract(ShadowTrading.feeOn(Balance.ec("10"), shady)));

            GameSave ordinary = withStanding(0);
            BigInteger unchanged = ordinary.ethecoinWei;
            ShadowTrading.list(
                    ordinary,
                    listed(),
                    Balance.ec("10"),
                    List.of(ordinary.items.getFirst().itemId),
                    DeliveryMode.ATTACHED,
                    T0);
            assertThat(ordinary.ethecoinWei).as("nothing up front").isEqualTo(unchanged);
        }

        @Test
        @DisplayName("⚠ an up-front fee that cannot be covered REFUSES the listing")
        void theUpFrontFeeMustBeAffordable() {
            // Taking a partial fee and putting the listing up anyway would be the worst of both.
            GameSave shady = withStanding(-70);
            shady.ethecoinWei = BigInteger.ONE;
            var refused = ShadowTrading.list(
                    shady,
                    listed(),
                    Balance.ec("100"),
                    List.of(shady.items.getFirst().itemId),
                    DeliveryMode.ATTACHED,
                    T0);
            assertThat(refused.ok()).isFalse();
            assertThat(refused.refusal()).isEqualTo(ShadowTrading.Refusal.CANNOT_AFFORD_FEE);
            assertThat(shady.ethecoinWei).as("and takes nothing").isEqualTo(BigInteger.ONE);
            assertThat(shady.items).as("and the copy stays put").hasSize(1);
        }

        @Test
        @DisplayName("⚠ withdrawing does NOT refund the up-front fee")
        void theFeeIsAlwaysCharged() {
            // "The fee is always charged" is the rule. A refundable one is no deterrent: a shady
            // seller could paper the board and withdraw for free, which is what charging up front
            // exists to stop.
            GameSave shady = withStanding(-70);
            ShadowTrading.list(
                    shady,
                    listed(),
                    Balance.ec("10"),
                    List.of(shady.items.getFirst().itemId),
                    DeliveryMode.ATTACHED,
                    T0);
            BigInteger afterListing = shady.ethecoinWei;

            ShadowTrading.cancel(shady, shady.shadowListings.getFirst().listingId, T0);
            assertThat(shady.ethecoinWei).isEqualTo(afterListing);
        }

        @Test
        @DisplayName("the fee comes off the proceeds when it sells")
        void theSaleFeeIsDeducted() {
            GameSave save = withStanding(0);
            BigInteger mid = ShadowMarket.midAt(save, listed(), T0);
            ShadowTrading.list(
                    save, listed(), mid.divide(BigInteger.TWO), List.of(save.items.getFirst().itemId),
                    DeliveryMode.ATTACHED, T0);
            BigInteger price = save.shadowListings.getFirst().priceWei;
            BigInteger before = save.ethecoinWei;

            for (long hour = 1; hour <= 400 && !save.shadowListings.isEmpty(); hour++) {
                ShadowTrading.settleListings(save, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
            }
            assertThat(save.shadowListings).isEmpty();
            assertThat(save.ethecoinWei)
                    .as("gross less the 3%")
                    .isEqualTo(before.add(price).subtract(ShadowTrading.feeOn(price, save)));
        }

        @Test
        @DisplayName("⚠ and it is charged even when the seller never delivers")
        void defaultingDoesNotEscapeTheFee() {
            // The fee comes off the proceeds at payment, so it is already gone by the time the
            // deadline lapses. A fee taken at delivery would be one a defaulting seller never paid.
            GameSave save = withStanding(0);
            BigInteger mid = ShadowMarket.midAt(save, listed(), T0);
            ShadowTrading.list(
                    save, listed(), mid.divide(BigInteger.TWO), List.of(save.items.getFirst().itemId),
                    DeliveryMode.SEND_LATER, T0);
            BigInteger price = save.shadowListings.getFirst().priceWei;
            BigInteger before = save.ethecoinWei;

            for (long hour = 1; hour <= 400 && !save.shadowListings.isEmpty(); hour++) {
                ShadowTrading.settleListings(save, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
            }
            BigInteger afterSale = save.ethecoinWei;
            assertThat(afterSale).isEqualTo(before.add(price).subtract(ShadowTrading.feeOn(price, save)));

            // Now blow the deadline.
            ShadowTrading.settleOverdue(save, T0.plus(Duration.ofDays(30)));
            assertThat(save.ethecoinWei).as("the fee is not handed back").isEqualTo(afterSale);
            assertThat(save.traderDefections).isEqualTo(1);
        }

        @Test
        @DisplayName("⚠ the fee ROUNDS UP, so a tiny listing cannot dodge it")
        void theFeeRoundsUp() {
            GameSave save = withStanding(0);
            // 1 wei at 3% truncates to zero; rounding up keeps it at one.
            assertThat(ShadowTrading.feeOn(BigInteger.ONE, save)).isEqualTo(BigInteger.ONE);
            assertThat(ShadowTrading.feeOn(BigInteger.ZERO, save)).isZero();
        }

        @Test
        @DisplayName("a trusted seller keeps more of the same sale than a shady one")
        void standingIsWorthMoney() {
            BigInteger gross = Balance.ec("100");
            assertThat(ShadowTrading.takeFee(withStanding(80), gross))
                    .isGreaterThan(ShadowTrading.takeFee(withStanding(0), gross));
            assertThat(ShadowTrading.takeFee(withStanding(0), gross))
                    .isGreaterThan(ShadowTrading.takeFee(withStanding(-70), gross));
        }
    }

    @Nested
    @DisplayName("somebody buying your listing")
    class Selling {

        private static io.github.stoicswe.eyeandsickle.engine.state.ShadowListingState at(
                GameSave save, java.math.BigDecimal fractionOfMid, DeliveryMode mode, Instant now) {
            BigInteger mid = ShadowMarket.midAt(save, listed(), now);
            BigInteger price = new java.math.BigDecimal(mid)
                    .multiply(fractionOfMid)
                    .toBigInteger()
                    .max(BigInteger.ONE);
            ShadowTrading.list(save, listed(), price, List.of(save.items.getFirst().itemId), mode, now);
            return save.shadowListings.getFirst();
        }

        @Test
        @DisplayName("⚠ cheaper sells faster, dearer sells slower — strictly, across the band")
        void priceDrivesTheRate() {
            GameSave save = saveWith(1);
            var under = at(saveWith(1), new java.math.BigDecimal("0.80"), DeliveryMode.ATTACHED, T0);
            var market = at(saveWith(1), new java.math.BigDecimal("1.00"), DeliveryMode.ATTACHED, T0);
            var over = at(saveWith(1), new java.math.BigDecimal("1.08"), DeliveryMode.ATTACHED, T0);

            double underRate = ShadowTrading.saleRatePerHour(save, under, T0);
            double marketRate = ShadowTrading.saleRatePerHour(save, market, T0);
            double overRate = ShadowTrading.saleRatePerHour(save, over, T0);

            assertThat(underRate).as("undercut beats market").isGreaterThan(marketRate);
            assertThat(overRate).as("over market is slower").isLessThan(marketRate);
            // "Significantly less" — a few percent over should visibly stall, not merely slow.
            assertThat(overRate).isLessThan(marketRate / 3);
        }

        @Test
        @DisplayName("⚠ NOTHING sells above the arbitrage ceiling, at any probability")
        void nothingSellsAboveTheCeiling() {
            // The guard that stops this feature being a faucet. An NPC's ethecoin is invented, so
            // paying above the storefront's floor for a player's item is issuance — repeatable, with
            // every screen still rendering correctly. "Unlikely but possible" is still a faucet.
            GameSave save = saveWith(1);
            BigInteger retail = Catalogue.byId(listed()).orElseThrow().priceWei();
            BigInteger overCeiling = retail
                    .multiply(BigInteger.valueOf(ShadowMarket.ceilingPercent() + 1))
                    .divide(BigInteger.valueOf(100))
                    .add(BigInteger.ONE);
            ShadowTrading.list(
                    save, listed(), overCeiling, List.of(save.items.getFirst().itemId), DeliveryMode.ATTACHED, T0);

            assertThat(ShadowTrading.saleRatePerHour(save, save.shadowListings.getFirst(), T0))
                    .isZero();
            // And a year of ticking never sells it.
            for (long hour = 0; hour < 24 * 365; hour++) {
                ShadowTrading.settleListings(save, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
            }
            assertThat(save.shadowListings).as("still unsold after a year").hasSize(1);
        }

        @Test
        @DisplayName("⚠ the rate is PER HOUR — tick frequency must not change how fast things sell")
        void tickFrequencyDoesNotChangeTheOutcome() {
            // A per-tick roll makes a faster-ticking client sell faster and gives a three-day absence
            // exactly one roll. Both are invisible in play and both make the tuned rate meaningless.
            // Compared statistically: the same wall time, delivered in very different slices.
            int coarseSold = 0;
            int fineSold = 0;
            for (int seed = 0; seed < 60; seed++) {
                Instant start = T0.plus(Duration.ofMinutes(seed * 37L));

                GameSave coarse = saveWith(1);
                at(coarse, new java.math.BigDecimal("1.00"), DeliveryMode.ATTACHED, start);
                ShadowTrading.settleListings(coarse, Duration.ofHours(2), start.plus(Duration.ofHours(2)));
                if (coarse.shadowListings.isEmpty()) {
                    coarseSold++;
                }

                GameSave fine = saveWith(1);
                at(fine, new java.math.BigDecimal("1.00"), DeliveryMode.ATTACHED, start);
                for (int minute = 1; minute <= 120; minute++) {
                    ShadowTrading.settleListings(
                            fine, Duration.ofMinutes(1), start.plus(Duration.ofMinutes(minute)));
                }
                if (fine.shadowListings.isEmpty()) {
                    fineSold++;
                }
            }
            // Same two hours either way. Loose bounds — this is a statistical claim about a random
            // process, and a tight one here would be a flaky test rather than a stronger guarantee.
            assertThat(Math.abs(coarseSold - fineSold))
                    .as("coarse=%d fine=%d out of 60", coarseSold, fineSold)
                    .isLessThan(22);
            assertThat(coarseSold).as("something sells").isPositive();
            assertThat(fineSold).as("something sells").isPositive();
        }

        @Test
        @DisplayName("an attached sale is done; a send-later sale leaves YOU owing")
        void deliveryModeDecidesWhatHappensNext() {
            GameSave attached = saveWith(1);
            at(attached, new java.math.BigDecimal("0.50"), DeliveryMode.ATTACHED, T0);
            GameSave promised = saveWith(1);
            at(promised, new java.math.BigDecimal("0.50"), DeliveryMode.SEND_LATER, T0);

            for (long hour = 1; hour <= 200 && !attached.shadowListings.isEmpty(); hour++) {
                ShadowTrading.settleListings(attached, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
            }
            for (long hour = 1; hour <= 200 && !promised.shadowListings.isEmpty(); hour++) {
                ShadowTrading.settleListings(promised, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
            }

            assertThat(attached.shadowListings).isEmpty();
            assertThat(attached.shadowObligations).as("nothing left to do").isEmpty();
            assertThat(attached.ethecoinWei).isGreaterThan(Balance.ec("500"));

            assertThat(promised.shadowListings).isEmpty();
            assertThat(promised.shadowObligations).as("you have the money and somebody is waiting").hasSize(1);
            assertThat(promised.shadowObligations.getFirst().owedByMe).isTrue();
            assertThat(promised.items).as("and you still hold the copy you owe").hasSize(1);
        }

        @Test
        @DisplayName("⚠ ONE unit per sale, so a stack visibly draws down")
        void listingsSellOneAtATime() {
            GameSave save = saveWith(3);
            BigInteger mid = ShadowMarket.midAt(save, listed(), T0);
            ShadowTrading.list(
                    save,
                    listed(),
                    mid.divide(BigInteger.TWO),
                    save.items.stream().map(item -> item.itemId).toList(),
                    DeliveryMode.ATTACHED,
                    T0);
            assertThat(save.shadowListings.getFirst().quantity).isEqualTo(3);

            int seen = save.shadowListings.getFirst().quantity;
            for (long hour = 1; hour <= 400 && !save.shadowListings.isEmpty(); hour++) {
                ShadowTrading.settleListings(save, Duration.ofHours(1), T0.plus(Duration.ofHours(hour)));
                int now = save.shadowListings.isEmpty() ? 0 : save.shadowListings.getFirst().quantity;
                assertThat(seen - now).as("never more than one unit at a time").isLessThanOrEqualTo(1);
                seen = now;
            }
            assertThat(save.shadowListings).isEmpty();
        }

        @Test
        @DisplayName("nothing sells in zero elapsed time")
        void noTimeNoSale() {
            GameSave save = saveWith(1);
            at(save, new java.math.BigDecimal("0.50"), DeliveryMode.ATTACHED, T0);
            assertThat(ShadowTrading.settleListings(save, Duration.ZERO, T0)).isEmpty();
            assertThat(save.shadowListings).hasSize(1);
        }
    }

    @Nested
    @DisplayName("⚠ there is no escrow")
    class NoEscrow {

        @Test
        @DisplayName("a resting bid holds nothing")
        void placingABidCommitsNoMoney() {
            // It used to escrow, which made a resting bid risk-free — and this is a market between
            // people who can defect, so risk-free is the one thing it must not be.
            GameSave save = saveWith(0);
            BigInteger before = save.ethecoinWei;

            var placed = ShadowMarket.place(save, listed(), true, Balance.ec("1"), 1, "", T0);
            assertThat(placed.succeeded()).isTrue();
            assertThat(save.ethecoinWei).as("nothing moved").isEqualTo(before);
            assertThat(save.shadowOrders.getFirst().escrowWei).isZero();
        }

        @Test
        @DisplayName("and cancelling one returns nothing, because nothing was held")
        void cancellingReturnsNothing() {
            GameSave save = saveWith(0);
            ShadowMarket.place(save, listed(), true, Balance.ec("1"), 1, "", T0);
            BigInteger before = save.ethecoinWei;

            assertThat(ShadowMarket.cancel(save, save.shadowOrders.getFirst().orderId)).isTrue();
            assertThat(save.ethecoinWei).isEqualTo(before);
        }
    }
}
