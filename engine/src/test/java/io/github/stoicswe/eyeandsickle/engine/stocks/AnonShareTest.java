package io.github.stoicswe.eyeandsickle.engine.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.rules.Brokerage;
import java.time.LocalTime;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AnonShare — the calendar, the aliasing and the one guard that bounds it.
 *
 * <h2>Read the commission test first</h2>
 *
 * This is the only market in the game whose prices the game does not control, so it is the only one
 * with no ceiling to derive. The commission is what stands in for one; if it ever stops making a
 * round trip negative-expectation, real market movement becomes an ethecoin printer and every screen
 * will still render correctly while it does.
 */
class AnonShareTest {

    /** A Wednesday, mid-session in New York. */
    private static Instant openInstant() {
        return ZonedDateTime.of(LocalDate.of(2026, 8, 5), LocalTime.of(11, 0), MarketCalendar.EXCHANGE)
                .toInstant();
    }

    private static GameSave rich() {
        GameSave save = new GameSave();
        save.characterId = "broker";
        save.ethecoinWei = Balance.ec("1000000");
        return save;
    }

    @Nested
    @DisplayName("⚠ the commission is the guard")
    class Commission {

        @Test
        @DisplayName("⚠ a round trip at an UNCHANGED price LOSES money")
        void tradingNoiseIsNegativeExpectation() {
            // The whole reason this market is safe to connect to real prices. If a flat round trip
            // ever broke even, a player could grind the spread; if it made money, real volatility
            // would be a faucet.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Instant now = openInstant();
            BigInteger before = save.ethecoinWei;

            assertThat(Brokerage.buy(save, feed, "AAPL", 10, now).ok()).isTrue();
            assertThat(Brokerage.sell(save, feed, save.brokerage.holdings.getFirst().holdingId, 10, now)
                            .ok())
                    .isTrue();

            assertThat(save.ethecoinWei)
                    .as("bought and sold at the same instant and the same price")
                    .isLessThan(before);
        }

        @Test
        @DisplayName("and it is charged BOTH ways")
        void chargedOnTheWayInAndOut() {
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Instant now = openInstant();
            BigInteger price = feed.quote("AAPL", now).orElseThrow().priceWei();
            BigInteger notional = price.multiply(BigInteger.valueOf(10));
            BigInteger before = save.ethecoinWei;

            Brokerage.buy(save, feed, "AAPL", 10, now);
            assertThat(before.subtract(save.ethecoinWei))
                    .as("the debit is the notional PLUS commission, not the notional")
                    .isEqualTo(notional.add(Brokerage.commissionOn(notional)));

            BigInteger afterBuy = save.ethecoinWei;
            Brokerage.sell(save, feed, save.brokerage.holdings.getFirst().holdingId, 10, now);
            assertThat(save.ethecoinWei.subtract(afterBuy))
                    .as("and the credit is the notional LESS commission")
                    .isEqualTo(notional.subtract(Brokerage.commissionOn(notional)));
        }

        @Test
        @DisplayName("⚠ it rounds UP, so a tiny trade cannot dodge it")
        void commissionRoundsUp() {
            assertThat(Brokerage.commissionOn(BigInteger.ONE)).isEqualTo(BigInteger.ONE);
            assertThat(Brokerage.commissionOn(BigInteger.ZERO)).isZero();
        }

        @Test
        @DisplayName("a real rally still pays — this is a gamble, not a tax")
        void aRealRallyStillWins() {
            // The commission must not be so large that the feature is pointless. A 20% move has to
            // clear it comfortably, or nobody would ever trade.
            BigInteger notional = Balance.ec("1000");
            BigInteger bothWays = Brokerage.commissionOn(notional).multiply(BigInteger.TWO);
            assertThat(bothWays)
                    .as("a round trip costs well under a fifth of the notional")
                    .isLessThan(notional.divide(BigInteger.valueOf(5)));
        }
    }

    @Nested
    @DisplayName("the session")
    class Session {

        @Test
        @DisplayName("⚠ hours are NEW YORK's; what changes is what the player calls them")
        void theSessionIsTheExchanges() {
            // A player in Berlin is told 15:30 and one in Tokyo 23:30, and both are right, because
            // every figure handed out is an Instant. Storing "09:30" and comparing it to a local
            // wall clock would open the market at four different instants.
            Instant open = ZonedDateTime.of(
                            LocalDate.of(2026, 8, 5), LocalTime.of(9, 30), MarketCalendar.EXCHANGE)
                    .toInstant();
            assertThat(MarketCalendar.sessionAt(open.plusSeconds(60)).tradable()).isTrue();
            assertThat(MarketCalendar.sessionAt(open.minusSeconds(60)).tradable()).isFalse();
        }

        @Test
        @DisplayName("shut at the weekend")
        void weekendsAreClosed() {
            Instant saturday = ZonedDateTime.of(
                            LocalDate.of(2026, 8, 8), LocalTime.of(11, 0), MarketCalendar.EXCHANGE)
                    .toInstant();
            assertThat(MarketCalendar.sessionAt(saturday).phase()).isEqualTo(MarketCalendar.Phase.CLOSED);
            assertThat(MarketCalendar.sessionAt(saturday).changesAt())
                    .as("and it says when it opens next, which is not tomorrow")
                    .isAfter(saturday.plus(Duration.ofDays(1)));
        }

        @Test
        @DisplayName("⚠ a holiday at the weekend is OBSERVED on the adjacent weekday")
        void observedHolidays() {
            // Independence Day 2026 falls on a Saturday, so the market shuts on the Friday. A
            // calendar comparing raw dates would have it open on a day nobody was trading.
            assertThat(MarketCalendar.isTradingDay(LocalDate.of(2026, 7, 3))).isFalse();
            assertThat(MarketCalendar.isTradingDay(LocalDate.of(2025, 12, 25))).isFalse();
            assertThat(MarketCalendar.isTradingDay(LocalDate.of(2026, 1, 1))).isFalse();
        }

        @Test
        @DisplayName("half-days close early")
        void halfDays() {
            assertThat(MarketCalendar.closeFor(LocalDate.of(2026, 11, 27)))
                    .isEqualTo(MarketCalendar.EARLY_CLOSE);
            assertThat(MarketCalendar.closeFor(LocalDate.of(2026, 8, 5))).isEqualTo(MarketCalendar.CLOSE);
        }

        @Test
        @DisplayName("⚠ you cannot trade a closed market")
        void tradingIsGatedOnTheSession() {
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Instant saturday = ZonedDateTime.of(
                            LocalDate.of(2026, 8, 8), LocalTime.of(11, 0), MarketCalendar.EXCHANGE)
                    .toInstant();
            BigInteger before = save.ethecoinWei;

            var refused = Brokerage.buy(save, feed, "AAPL", 1, saturday);
            assertThat(refused.ok()).isFalse();
            assertThat(refused.refusal()).isEqualTo(Brokerage.Refusal.MARKET_CLOSED);
            assertThat(save.ethecoinWei).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("names and symbols")
    class Naming {

        @Test
        @DisplayName("⚠ the alias is DETERMINISTIC — a portfolio must stay recognisable")
        void aliasesDoNotDrift() {
            for (Tickers.Listing listing : Tickers.all()) {
                String first = listing.displayName();
                for (int i = 0; i < 20; i++) {
                    assertThat(listing.displayName()).isEqualTo(first);
                }
                assertThat(first).isNotBlank();
            }
        }

        @Test
        @DisplayName("⚠ no alias leaks the real name")
        void theRealNameNeverSurvives() {
            // The point of the layer. A name that passed through unchanged would put a real
            // trademark on a darknet brokerage in a surveillance dystopia.
            for (Tickers.Listing listing : Tickers.all()) {
                assertThat(listing.displayName())
                        .as("%s", listing.symbol())
                        .isNotEqualToIgnoringCase(listing.realName());
            }
        }

        @Test
        @DisplayName("symbols are real and looked up case-insensitively")
        void symbolsAreReal() {
            assertThat(Tickers.bySymbol("aapl")).isPresent();
            assertThat(Tickers.bySymbol("AAPL")).isPresent();
            assertThat(Tickers.bySymbol("NOTREAL")).isEmpty();
        }

        @Test
        @DisplayName("⚠ search matches the ALIAS, never the real name")
        void searchDoesNotLeakEither() {
            // A player who found a company by typing its real name would have been told the real
            // name, which is the one thing this layer exists not to do.
            assertThat(Tickers.search("Apple")).isEmpty();
            assertThat(Tickers.search("AAPL")).isNotEmpty();
            assertThat(Tickers.search(Tickers.bySymbol("AAPL").orElseThrow().displayName()))
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("the offline feed")
    class Offline {

        @Test
        @DisplayName("⚠ it says it is not real, and the flag says so too")
        void itDeclaresItself() {
            // Somebody acting on a simulated quote believing it was the market is the one harm this
            // tab could cause outside the game.
            StockFeed feed = new SimulatedStockFeed(1);
            assertThat(feed.live()).isFalse();
            assertThat(feed.describe()).containsIgnoringCase("simulated");
        }

        @Test
        @DisplayName("the same instant gives the same price")
        void pricesAreStable() {
            StockFeed feed = new SimulatedStockFeed(1);
            Instant now = openInstant();
            BigInteger first = feed.quote("MSFT", now).orElseThrow().priceWei();
            for (int i = 0; i < 20; i++) {
                assertThat(feed.quote("MSFT", now).orElseThrow().priceWei()).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("⚠ but it does NOT move while the market is shut")
        void pricesFreezeOutOfHours() {
            // A simulated price drifting over the weekend would teach a player that this market
            // trades at times the real one does not, which is exactly what the calendar models.
            StockFeed feed = new SimulatedStockFeed(1);
            Instant saturday = ZonedDateTime.of(
                            LocalDate.of(2026, 8, 8), LocalTime.of(11, 0), MarketCalendar.EXCHANGE)
                    .toInstant();
            BigInteger morning = feed.quote("MSFT", saturday).orElseThrow().priceWei();
            BigInteger evening = feed.quote("MSFT", saturday.plus(Duration.ofHours(8)))
                    .orElseThrow()
                    .priceWei();
            assertThat(evening).isEqualTo(morning);
        }

        @Test
        @DisplayName("prices stay plausible over a long campaign")
        void pricesStayBounded() {
            StockFeed feed = new SimulatedStockFeed(3);
            for (Tickers.Listing listing : Tickers.all()) {
                BigInteger reference =
                        BigInteger.valueOf(listing.referencePrice()).multiply(BigInteger.TEN.pow(18));
                for (long day = 0; day < 500; day++) {
                    var quote = feed.quote(listing.symbol(), openInstant().plus(Duration.ofDays(day)));
                    assertThat(quote).isPresent();
                    assertThat(quote.get().priceWei()).isPositive();
                    assertThat(quote.get().priceWei())
                            .as("%s on day %d", listing.symbol(), day)
                            .isLessThan(reference.multiply(BigInteger.TWO));
                }
            }
        }
    }

    @Nested
    @DisplayName("dividends")
    class DividendPayments {

        /** A payment date in the quarter AFTER the one a purchase is stamped with. */
        private static Instant nextQuarterPayDate(Instant bought) {
            long quarter = Dividends.quarterOf(bought) + 1;
            return ZonedDateTime.of(
                            Dividends.payDate(quarter), LocalTime.of(12, 0), MarketCalendar.EXCHANGE)
                    .toInstant();
        }

        @Test
        @DisplayName("⚠ PAID ONCE per quarter, however many times the tick runs")
        void paidOnlyOncePerQuarter() {
            // The tick runs every second and a quarter stays current for three months. Without the
            // marker a holder is paid once per second for a quarter of a year — a faucet orders of
            // magnitude larger than anything else in the game, arriving quietly.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "KO", 100, openInstant());
            Instant pay = nextQuarterPayDate(openInstant());

            var first = Brokerage.settleDividends(save, feed, pay);
            assertThat(first).hasSize(1);
            BigInteger afterFirst = save.ethecoinWei;

            for (int i = 0; i < 500; i++) {
                assertThat(Brokerage.settleDividends(save, feed, pay.plusSeconds(i))).isEmpty();
            }
            assertThat(save.ethecoinWei).isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("⚠ buying does not immediately collect that quarter's dividend")
        void aFreshParcelWaits() {
            // Otherwise a player buys on a payment date, takes the dividend and sells — repeatedly,
            // inside one session. You are paid for quarters you held THROUGH.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Instant pay = ZonedDateTime.of(
                            Dividends.payDate(Dividends.quarterOf(openInstant())),
                            LocalTime.of(12, 0),
                            MarketCalendar.EXCHANGE)
                    .toInstant();
            // Buy on a trading day inside the quarter, then try to collect the same quarter.
            Brokerage.buy(save, feed, "KO", 100, openInstant());
            assertThat(Brokerage.settleDividends(save, feed, pay)).isEmpty();
        }

        @Test
        @DisplayName("a non-payer pays nothing, and that is a real property of the share")
        void growthNamesPayNothing() {
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "TSLA", 100, openInstant());
            BigInteger before = save.ethecoinWei;
            assertThat(Brokerage.settleDividends(save, feed, nextQuarterPayDate(openInstant())))
                    .isEmpty();
            assertThat(save.ethecoinWei).isEqualTo(before);
            assertThat(Tickers.bySymbol("TSLA").orElseThrow().paysDividend()).isFalse();
            assertThat(Tickers.bySymbol("KO").orElseThrow().paysDividend()).isTrue();
        }

        @Test
        @DisplayName("⚠ the amount is a YIELD on the current price, not a fixed sum")
        void dividendsFollowThePrice() {
            BigInteger cheap = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(50));
            BigInteger dear = cheap.multiply(BigInteger.TWO);
            assertThat(Dividends.perShare("KO", dear))
                    .as("a holder whose stock ran up is paid more — that is what a yield does")
                    .isGreaterThan(Dividends.perShare("KO", cheap));
        }

        @Test
        @DisplayName("⚠ it rounds DOWN — the opposite of a fee")
        void dividendsRoundDown() {
            // Rounding a payment up would create wei out of nothing on every dividend in the game.
            assertThat(Dividends.perShare("KO", BigInteger.ONE)).isZero();
        }

        @Test
        @DisplayName("paid whether or not the market is open — a dividend is not a trade")
        void dividendsIgnoreSessionHours() {
            // Gating on session hours would mean a weekend-only player never collected anything.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "KO", 100, openInstant());
            Instant pay = nextQuarterPayDate(openInstant());
            // Push to a Saturday inside the same quarter, after the pay date.
            Instant weekend = pay;
            while (MarketCalendar.isTradingDay(
                    weekend.atZone(MarketCalendar.EXCHANGE).toLocalDate())) {
                weekend = weekend.plus(Duration.ofDays(1));
            }
            assertThat(Brokerage.settleDividends(save, feed, weekend)).hasSize(1);
        }

        @Test
        @DisplayName("the pay date never lands on a closed day")
        void payDatesAreTradingDays() {
            for (long quarter = 2026L * 4; quarter < 2031L * 4; quarter++) {
                assertThat(MarketCalendar.isTradingDay(Dividends.payDate(quarter)))
                        .as("quarter %d", quarter)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("providers")
    class Providers {

        @Test
        @DisplayName("⚠ every provider carries the date its limits were checked")
        void limitsAreDated() {
            // Rate limits go stale. Recording when they were read is what stops a figure in this
            // repo being read as current fact two years from now.
            assertThat(StockProvider.LIMITS_CHECKED).isNotBlank();
            for (StockProvider provider : StockProvider.values()) {
                assertThat(provider.limits()).as("%s", provider).isNotBlank();
                assertThat(provider.signupUrl()).startsWith("https://");
                assertThat(provider.label()).isNotBlank();
            }
        }

        @Test
        @DisplayName("the default is the one with the usable allowance")
        void theDefaultIsFinnhub() {
            assertThat(StockProvider.preferred()).isEqualTo(StockProvider.FINNHUB);
        }

        @Test
        @DisplayName("⚠ an unknown provider name falls back rather than throwing")
        void parseIsTolerant() {
            // It arrives from a settings file a player can edit.
            assertThat(StockProvider.parse("nonsense")).isEqualTo(StockProvider.preferred());
            assertThat(StockProvider.parse(null)).isEqualTo(StockProvider.preferred());
            assertThat(StockProvider.parse("twelve_data")).isEqualTo(StockProvider.TWELVE_DATA);
        }
    }

    @Nested
    @DisplayName("portfolios")
    class Portfolios {

        @Test
        @DisplayName("⚠ deleting a portfolio UNFILES holdings, never sells them")
        void deletingALabelDoesNotDeleteShares() {
            // A portfolio is a label. There is no confirmation dialog that makes losing somebody's
            // positions to a tidy-up acceptable.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "AAPL", 5, openInstant());
            Brokerage.createPortfolio(save, "Long");
            String portfolioId = save.brokerage.portfolios.getFirst().portfolioId;
            Brokerage.file(save, save.brokerage.holdings.getFirst().holdingId, portfolioId);

            Brokerage.deletePortfolio(save, portfolioId);
            assertThat(save.brokerage.holdings).as("still held").hasSize(1);
            assertThat(save.brokerage.holdings.getFirst().portfolioId).isEmpty();
        }

        @Test
        @DisplayName("watching is separate from holding")
        void watchlistsAreNotHoldings() {
            GameSave save = rich();
            Brokerage.createPortfolio(save, "Ideas");
            String id = save.brokerage.portfolios.getFirst().portfolioId;
            assertThat(Brokerage.watch(save, id, "nvda").ok()).isTrue();
            assertThat(save.brokerage.portfolios.getFirst().watching).containsExactly("NVDA");
            assertThat(save.brokerage.holdings).isEmpty();
        }
    }

    @Nested
    @DisplayName("parcels")
    class Parcels {

        @Test
        @DisplayName("⚠ selling names a PARCEL, so the player picks their own cost basis")
        void parcelsAreSeparate() {
            // Two buys at different prices are two positions with two different answers to "am I up
            // on this". A symbol-keyed sell would choose for the player and then report a profit
            // against a basis they did not pick.
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "AAPL", 5, openInstant());
            Brokerage.buy(save, feed, "AAPL", 3, openInstant().plus(Duration.ofMinutes(30)));
            assertThat(save.brokerage.holdings).hasSize(2);

            String second = save.brokerage.holdings.get(1).holdingId;
            assertThat(Brokerage.sell(save, feed, second, 3, openInstant().plus(Duration.ofMinutes(45)))
                            .ok())
                    .isTrue();
            assertThat(save.brokerage.holdings).hasSize(1);
            assertThat(save.brokerage.holdings.getFirst().shares).isEqualTo(5);
        }

        @Test
        @DisplayName("a partial sale leaves the rest of the parcel")
        void partialSales() {
            GameSave save = rich();
            StockFeed feed = new SimulatedStockFeed(7);
            Brokerage.buy(save, feed, "AAPL", 10, openInstant());
            Brokerage.sell(save, feed, save.brokerage.holdings.getFirst().holdingId, 4, openInstant());
            assertThat(save.brokerage.holdings.getFirst().shares).isEqualTo(6);
        }
    }
}
