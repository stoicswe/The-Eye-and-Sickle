package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.BrokerageState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.stocks.MarketCalendar;
import java.time.Duration;
import io.github.stoicswe.eyeandsickle.engine.stocks.StockFeed;
import io.github.stoicswe.eyeandsickle.engine.stocks.Tickers;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Buying and selling shares on AnonShare.
 *
 * <h2>⚠ THE COMMISSION IS THE ONLY THING BOUNDING THIS MARKET</h2>
 *
 * Every other market in the game has a ceiling derived from a number the game controls — the
 * storefront's discount bands, the resale percentage, the arbitrage margin. This one tracks prices
 * the game does not control and cannot predict, so there is no ceiling to derive: a player who buys
 * before a real rally and sells after it has created ethecoin out of an external event.
 *
 * <p>{@link Balance#BROKERAGE_COMMISSION_BP}, charged <b>both ways</b>, is what makes that a gamble
 * rather than a printer — a round trip must beat roughly twice the commission before it makes
 * anything, so the expected value of trading noise is negative. ⚠ Lowering it towards zero re-opens
 * the faucet and every screen will still render correctly while it does.
 *
 * <h2>⚠ Trading is gated on the REAL session, in the player's own timezone</h2>
 *
 * You cannot trade a closed market. The hours are New York's because that is a fact about the
 * exchange; what the player sees is an instant rendered locally, so a player in Berlin is told the
 * market opens at 15:30 and one in Tokyo at 23:30, and both are right.
 */
public final class Brokerage {

    private Brokerage() {}

    /** Why a trade was refused. */
    public enum Refusal {
        /** Not a symbol AnonShare lists. */
        UNKNOWN_SYMBOL,

        /** The market is shut. */
        MARKET_CLOSED,

        /** No price — usually a feed that cannot reach anything. */
        NO_QUOTE,

        /** Zero or negative shares. */
        MALFORMED,

        /** Not enough ethecoin, commission included. */
        CANNOT_AFFORD,

        /** You do not hold that many. */
        NOT_HELD,

        /** No portfolio under that name. */
        NO_SUCH_PORTFOLIO
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

    /**
     * The commission on a notional amount.
     *
     * <p>⚠ Rounds <b>up</b>, so the house never loses a wei to truncation and a trade small enough
     * cannot arrange a commission of zero — which would be a fee-free path for exactly the
     * high-frequency grinding the commission exists to make unprofitable.
     */
    public static BigInteger commissionOn(BigInteger notional) {
        if (notional == null || notional.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger scale = BigInteger.valueOf(10_000L);
        return notional
                .multiply(BigInteger.valueOf(Balance.BROKERAGE_COMMISSION_BP))
                .add(scale)
                .subtract(BigInteger.ONE)
                .divide(scale);
    }

    /**
     * Buys shares at the feed's price.
     *
     * <p>⚠ The commission is charged <b>on top</b> of the notional, so the debit is more than the
     * quote — and it is checked against the balance <em>including</em> the commission before
     * anything moves. Charging it out of the notional instead would silently hand the player fewer
     * shares than they asked for.
     */
    public static Result buy(GameSave save, StockFeed feed, String symbol, int shares, Instant now) {
        Optional<Tickers.Listing> listing = Tickers.bySymbol(symbol);
        if (listing.isEmpty()) {
            return Result.refused(Refusal.UNKNOWN_SYMBOL, "AnonShare does not list " + symbol + ".");
        }
        if (shares <= 0) {
            return Result.refused(Refusal.MALFORMED, "how many shares?");
        }
        MarketCalendar.Session session = MarketCalendar.sessionAt(now);
        if (!session.tradable()) {
            return Result.refused(Refusal.MARKET_CLOSED, "the market is shut. " + describe(session));
        }
        Optional<StockFeed.Quote> quote = feed.quote(listing.get().symbol(), now);
        if (quote.isEmpty()) {
            return Result.refused(Refusal.NO_QUOTE, "no price for " + listing.get().symbol() + " right now.");
        }
        BigInteger notional = quote.get().priceWei().multiply(BigInteger.valueOf(shares));
        BigInteger commission = commissionOn(notional);
        BigInteger total = notional.add(commission);
        if (save.ethecoinWei.compareTo(total) < 0) {
            return Result.refused(
                    Refusal.CANNOT_AFFORD,
                    "that is "
                            + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(total)
                            + " including commission, and you do not have it.");
        }
        save.ethecoinWei = save.ethecoinWei.subtract(total);

        BrokerageState.Holding holding = new BrokerageState.Holding();
        holding.symbol = listing.get().symbol();
        holding.shares = shares;
        holding.costPerShareWei = quote.get().priceWei();
        holding.boughtAt = now;
        stampQuarter(holding, now);
        save.brokerage.holdings.add(holding);
        record(save, listing.get().symbol(), true, shares, quote.get().priceWei(), commission,
                BigInteger.ZERO, now);

        return Result.ok("bought " + shares + " × " + listing.get().displayName() + " at "
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(quote.get().priceWei())
                + " (commission "
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(commission) + ").");
    }

    /**
     * Sells a parcel.
     *
     * <p>⚠ By {@code holdingId}, never by symbol. A player with two parcels bought at different
     * prices is choosing which one to close, and a symbol-keyed sell would pick for them — then show
     * them a profit computed against a cost basis they did not choose.
     */
    public static Result sell(GameSave save, StockFeed feed, String holdingId, int shares, Instant now) {
        Optional<BrokerageState.Holding> found = save.brokerage.holdings.stream()
                .filter(holding -> holding.holdingId.equals(holdingId))
                .findFirst();
        if (found.isEmpty()) {
            return Result.refused(Refusal.NOT_HELD, "you do not hold that.");
        }
        BrokerageState.Holding holding = found.get();
        if (shares <= 0 || shares > holding.shares) {
            return Result.refused(Refusal.MALFORMED, "you hold " + holding.shares + " of those.");
        }
        MarketCalendar.Session session = MarketCalendar.sessionAt(now);
        if (!session.tradable()) {
            return Result.refused(Refusal.MARKET_CLOSED, "the market is shut. " + describe(session));
        }
        Optional<StockFeed.Quote> quote = feed.quote(holding.symbol, now);
        if (quote.isEmpty()) {
            return Result.refused(Refusal.NO_QUOTE, "no price for " + holding.symbol + " right now.");
        }
        BigInteger notional = quote.get().priceWei().multiply(BigInteger.valueOf(shares));
        BigInteger commission = commissionOn(notional);
        // ⚠ Out of the proceeds this time, not on top — a seller has no other pocket to take it from,
        // and asking them to fund it separately would refuse sales from players who are fully invested.
        BigInteger net = notional.subtract(commission).max(BigInteger.ZERO);
        save.ethecoinWei = save.ethecoinWei.add(net);

        BigInteger basis = holding.costPerShareWei.multiply(BigInteger.valueOf(shares));
        holding.shares -= shares;
        if (holding.shares <= 0) {
            save.brokerage.holdings.remove(holding);
        }
        BigInteger gain = net.subtract(basis);
        record(save, holding.symbol, false, shares, quote.get().priceWei(), commission, gain, now);
        return Result.ok("sold " + shares + " × " + displayName(holding.symbol) + " for "
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(net)
                + " net — " + (gain.signum() >= 0 ? "up " : "down ")
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(gain.abs())
                + " on the parcel.");
    }

    /** Files a completed trade, oldest first, bounded. */
    private static void record(
            GameSave save,
            String symbol,
            boolean buy,
            int shares,
            BigInteger price,
            BigInteger commission,
            BigInteger realised,
            Instant now) {
        BrokerageState.Trade trade = new BrokerageState.Trade();
        trade.symbol = symbol;
        trade.buy = buy;
        trade.shares = shares;
        trade.pricePerShareWei = price;
        trade.commissionWei = commission;
        trade.realisedWei = realised;
        trade.at = now;
        save.brokerage.trades.add(trade);
        while (save.brokerage.trades.size() > BrokerageState.TRADE_LIMIT) {
            save.brokerage.trades.removeFirst();
        }
    }

    /** Every recorded trade, newest first — which is the order a history is read in. */
    public static List<BrokerageState.Trade> trades(GameSave save) {
        if (save == null) {
            return List.of();
        }
        List<BrokerageState.Trade> out = new java.util.ArrayList<>(save.brokerage.trades);
        java.util.Collections.reverse(out);
        return List.copyOf(out);
    }

    private static String displayName(String symbol) {
        return Tickers.bySymbol(symbol).map(Tickers.Listing::displayName).orElse(symbol);
    }

    private static String describe(MarketCalendar.Session session) {
        return switch (session.phase()) {
            case PRE -> "It opens shortly.";
            case POST -> "It closed for the day.";
            default -> "It is the weekend or a holiday.";
        };
    }

    // ── price history ─────────────────────────────────────────────────────────────────────────

    /**
     * How often a sample is taken.
     *
     * <p>⚠ Not every tick. The tick runs every second; at that rate a day is 86,400 rows per symbol
     * and the save stops being a file anybody can open. Five minutes is finer than the chart can
     * usefully draw and coarse enough that {@code HISTORY_LIMIT} covers a real stretch of play.
     */
    public static final Duration SAMPLE_EVERY = Duration.ofMinutes(5);

    /**
     * Records where the portfolio is, if it is time.
     *
     * <h2>⚠ ONLY WHILE THE MARKET IS OPEN</h2>
     *
     * Prices freeze out of hours, so sampling overnight writes hundreds of identical rows and pushes
     * the interesting ones off the front of a bounded buffer. A real chart has weekend gaps for the
     * same reason, so this is the honest shape as well as the cheap one.
     *
     * <h2>⚠ Only symbols the player is TRACKING — held or watched</h2>
     *
     * The rest of the catalogue is not their money. Recording it would be hundreds of series nobody
     * reads, bought with the space the ones they do read need.
     *
     * <p>⚠ It is the SAME set that gets the fast refresh cadence ({@link #tracked}), and that is not
     * a coincidence to be tidied away — a symbol whose price is fetched often is exactly the one
     * whose series is worth keeping, and a watchlist with no chart behind it is a list of names.
     * Wiring these to two different sets would give a watched symbol a graph made of daily points.
     *
     * @param now the session clock
     * @return whether anything was written
     */
    public static boolean sample(GameSave save, StockFeed feed, Instant now) {
        if (save == null || !MarketCalendar.sessionAt(now).tradable()) {
            return false;
        }
        BrokerageState brokerage = save.brokerage;
        Instant lastAt = brokerage.valueHistory.isEmpty()
                ? Instant.EPOCH
                : brokerage.valueHistory.getLast().at;
        if (Duration.between(lastAt, now).compareTo(SAMPLE_EVERY) < 0) {
            return false;
        }

        BigInteger total = BigInteger.ZERO;
        for (BrokerageState.Holding holding : brokerage.holdings) {
            BigInteger each = feed.quote(holding.symbol, now)
                    .map(StockFeed.Quote::priceWei)
                    .orElse(holding.costPerShareWei);
            total = total.add(each.multiply(BigInteger.valueOf(holding.shares)));
        }
        // ⚠ The TOTAL is over holdings; the SERIES are over everything tracked. A watchlist entry is
        // not the player's money, so it must not move the portfolio line — but it does need a chart.
        java.util.Set<String> tracking = tracked(save);
        for (String symbol : tracking) {
            BigInteger price = feed.quote(symbol, now)
                    .map(StockFeed.Quote::priceWei)
                    .orElse(BigInteger.ZERO);
            if (price.signum() <= 0) {
                continue;
            }
            List<BrokerageState.Sample> series =
                    brokerage.priceHistory.computeIfAbsent(symbol, key -> new java.util.ArrayList<>());
            series.add(new BrokerageState.Sample(now, price));
            trim(series);
        }
        // ⚠ Series for symbols neither held nor watched are DROPPED. Keeping them would grow the save
        // forever with the history of things the player sold or stopped following, and there is no
        // longer a chart to draw them on.
        brokerage.priceHistory.keySet().retainAll(tracking);

        brokerage.valueHistory.add(new BrokerageState.Sample(now, total));
        trim(brokerage.valueHistory);
        return true;
    }

    private static void trim(List<BrokerageState.Sample> series) {
        while (series.size() > BrokerageState.HISTORY_LIMIT) {
            series.removeFirst();
        }
    }

    /** The recorded value series, oldest first. */
    public static List<BrokerageState.Sample> valueHistory(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.brokerage.valueHistory);
    }

    /** The recorded price series for one symbol, oldest first. */
    public static List<BrokerageState.Sample> priceHistory(GameSave save, String symbol) {
        if (save == null) {
            return List.of();
        }
        return List.copyOf(save.brokerage.priceHistory.getOrDefault(symbol, List.of()));
    }

    // ── positions ─────────────────────────────────────────────────────────────────────────────

    /**
     * One symbol's whole position, however many parcels it was bought in.
     *
     * @param shares the total
     * @param costWei what the lot cost altogether
     */
    public record Position(String symbol, int shares, BigInteger costWei) {

        /** ⚠ Derived, not stored — an averaged basis that drifted from its parcels would be a lie. */
        public BigInteger averageCostWei() {
            return shares <= 0 ? BigInteger.ZERO : costWei.divide(BigInteger.valueOf(shares));
        }
    }

    /**
     * Holdings collapsed to one row per symbol.
     *
     * <h2>⚠ The PARCELS still exist underneath, and that is not a contradiction</h2>
     *
     * A player with two buys at different prices has one <em>position</em> and two <em>lots</em>.
     * Every broker shows the position and keeps the lots, because the position is what you look at
     * and the lots are what the tax and the cost basis are made of. Showing two rows for one company
     * made the panel read as a ledger of transactions rather than as a portfolio.
     */
    public static List<Position> positions(GameSave save) {
        if (save == null) {
            return List.of();
        }
        java.util.Map<String, Position> bySymbol = new java.util.LinkedHashMap<>();
        for (BrokerageState.Holding holding : save.brokerage.holdings) {
            bySymbol.merge(
                    holding.symbol,
                    new Position(
                            holding.symbol,
                            holding.shares,
                            holding.costPerShareWei.multiply(BigInteger.valueOf(holding.shares))),
                    (a, b) -> new Position(a.symbol(), a.shares() + b.shares(), a.costWei().add(b.costWei())));
        }
        return List.copyOf(bySymbol.values());
    }

    /**
     * Sells from a symbol's oldest parcel first.
     *
     * <h2>⚠ FIFO, and it is the default every broker uses</h2>
     *
     * Selling used to name a parcel, so the player chose their own cost basis — correct, and useless
     * once the panel shows one row per symbol, because there is no longer a parcel on screen to name.
     * Oldest-first is what a broker does when you do not specify, it is the one rule a player can
     * predict without being told, and the per-parcel basis survives underneath for anyone who does
     * want to pick.
     */
    public static Result sellPosition(GameSave save, StockFeed feed, String symbol, int shares, Instant now) {
        int remaining = shares;
        if (remaining <= 0) {
            return Result.refused(Refusal.MALFORMED, "how many shares?");
        }
        List<BrokerageState.Holding> lots = save.brokerage.holdings.stream()
                .filter(holding -> holding.symbol.equals(symbol))
                .sorted(java.util.Comparator.comparing(holding -> holding.boughtAt))
                .toList();
        int owned = lots.stream().mapToInt(holding -> holding.shares).sum();
        if (owned < remaining) {
            return Result.refused(Refusal.NOT_HELD, "you hold " + owned + " of those.");
        }
        Result last = Result.refused(Refusal.NOT_HELD, "nothing sold.");
        for (BrokerageState.Holding lot : lots) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, lot.shares);
            last = sell(save, feed, lot.holdingId, take, now);
            if (!last.ok()) {
                // ⚠ Stops on the first refusal rather than pressing on. A closed market or a missing
                // quote refuses every lot identically, and reporting the last of five identical
                // refusals is no clearer than reporting the first.
                return last;
            }
            remaining -= take;
        }
        return last;
    }

    // ── dividends ─────────────────────────────────────────────────────────────────────────────

    /** One dividend payment, for the log. */
    public record Paid(String symbol, int shares, BigInteger amountWei, long quarter) {}

    /**
     * Pays every parcel whatever it is owed for the current quarter.
     *
     * <h2>⚠ ONCE per parcel per quarter, and {@code lastPaidQuarter} is the whole guarantee</h2>
     *
     * The tick runs every second and a quarter stays current for three months. Without the marker a
     * holder would be paid once per second for a quarter of a year — an ethecoin faucet several
     * orders of magnitude larger than anything else in the game, arriving quietly.
     *
     * <h2>⚠ Paid whether or not the market is open</h2>
     *
     * A dividend is not a trade. Gating it on session hours would mean a player who only plays at
     * weekends never collected anything, which is the opposite of what holding is supposed to be.
     *
     * @param now the session clock
     * @return what was paid
     */
    public static List<Paid> settleDividends(GameSave save, StockFeed feed, Instant now) {
        if (save == null || save.brokerage.holdings.isEmpty()) {
            return List.of();
        }
        long quarter = io.github.stoicswe.eyeandsickle.engine.stocks.Dividends.quarterOf(now);
        // Nothing is due until the payment date itself, or a quarter would pay on its first second.
        if (now.atZone(io.github.stoicswe.eyeandsickle.engine.stocks.MarketCalendar.EXCHANGE)
                .toLocalDate()
                .isBefore(io.github.stoicswe.eyeandsickle.engine.stocks.Dividends.payDate(quarter))) {
            return List.of();
        }
        List<Paid> paid = new java.util.ArrayList<>();
        for (BrokerageState.Holding holding : save.brokerage.holdings) {
            if (holding.lastPaidQuarter >= quarter) {
                continue;
            }
            holding.lastPaidQuarter = quarter;
            if (!io.github.stoicswe.eyeandsickle.engine.stocks.Dividends.paysIn(holding.symbol, quarter)) {
                continue;
            }
            Optional<StockFeed.Quote> quote = feed.quote(holding.symbol, now);
            if (quote.isEmpty()) {
                continue;
            }
            BigInteger perShare = io.github.stoicswe.eyeandsickle.engine.stocks.Dividends.perShare(
                    holding.symbol, quote.get().priceWei());
            BigInteger amount = perShare.multiply(BigInteger.valueOf(holding.shares));
            if (amount.signum() <= 0) {
                continue;
            }
            // ⚠ No commission on a dividend. AnonShare takes its cut when you trade; charging to
            // receive money you were owed for holding would be a second fee the player never agreed
            // to, on the one part of this market that is supposed to be passive.
            save.ethecoinWei = save.ethecoinWei.add(amount);
            save.brokerage.dividendsPaidWei = save.brokerage.dividendsPaidWei.add(amount);
            paid.add(new Paid(holding.symbol, holding.shares, amount, quarter));
        }
        return paid;
    }

    /**
     * ⚠ Stamps a fresh parcel with the current quarter, so buying does not immediately collect.
     *
     * <p>Without it, a player could buy on a payment date, take the quarter's dividend and sell —
     * repeatedly, within one session. This is the simplification that stands in for a record date:
     * you are paid for quarters you held <em>through</em>, not for the one you arrived in.
     */
    private static void stampQuarter(BrokerageState.Holding holding, Instant now) {
        holding.lastPaidQuarter = io.github.stoicswe.eyeandsickle.engine.stocks.Dividends.quarterOf(now);
    }

    // ── portfolios ────────────────────────────────────────────────────────────────────────────

    /** Creates a named collection. */
    public static Result createPortfolio(GameSave save, String name) {
        if (name == null || name.isBlank()) {
            return Result.refused(Refusal.MALFORMED, "give it a name.");
        }
        BrokerageState.Portfolio portfolio = new BrokerageState.Portfolio();
        portfolio.name = name.trim();
        save.brokerage.portfolios.add(portfolio);
        return Result.ok("portfolio \"" + portfolio.name + "\" created.");
    }

    public static Result deletePortfolio(GameSave save, String portfolioId) {
        Optional<BrokerageState.Portfolio> found = portfolio(save, portfolioId);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_PORTFOLIO, "no such portfolio.");
        }
        // ⚠ Holdings filed under it are UNFILED, never deleted. A portfolio is a label; deleting the
        // label must not delete the shares, and there is no confirmation dialog in the world that
        // makes losing somebody's positions to a tidy-up acceptable.
        save.brokerage.holdings.stream()
                .filter(holding -> portfolioId.equals(holding.portfolioId))
                .forEach(holding -> holding.portfolioId = "");
        save.brokerage.portfolios.remove(found.get());
        return Result.ok("portfolio removed; the holdings in it are unfiled, not sold.");
    }

    /** Adds a symbol to a portfolio's watchlist. */
    public static Result watch(GameSave save, String portfolioId, String symbol) {
        Optional<BrokerageState.Portfolio> found = portfolio(save, portfolioId);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_PORTFOLIO, "no such portfolio.");
        }
        Optional<Tickers.Listing> listing = Tickers.bySymbol(symbol);
        if (listing.isEmpty()) {
            return Result.refused(Refusal.UNKNOWN_SYMBOL, "AnonShare does not list " + symbol + ".");
        }
        if (!found.get().watching.contains(listing.get().symbol())) {
            found.get().watching.add(listing.get().symbol());
        }
        return Result.ok("watching " + listing.get().displayName() + ".");
    }

    public static Result unwatch(GameSave save, String portfolioId, String symbol) {
        Optional<BrokerageState.Portfolio> found = portfolio(save, portfolioId);
        if (found.isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_PORTFOLIO, "no such portfolio.");
        }
        found.get().watching.remove(symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT));
        return Result.ok("no longer watching it.");
    }

    /** Files a holding under a portfolio, or unfiles it with a blank id. */
    public static Result file(GameSave save, String holdingId, String portfolioId) {
        Optional<BrokerageState.Holding> found = save.brokerage.holdings.stream()
                .filter(holding -> holding.holdingId.equals(holdingId))
                .findFirst();
        if (found.isEmpty()) {
            return Result.refused(Refusal.NOT_HELD, "you do not hold that.");
        }
        if (portfolioId != null && !portfolioId.isBlank() && portfolio(save, portfolioId).isEmpty()) {
            return Result.refused(Refusal.NO_SUCH_PORTFOLIO, "no such portfolio.");
        }
        found.get().portfolioId = portfolioId == null ? "" : portfolioId;
        return Result.ok("filed.");
    }

    public static Optional<BrokerageState.Portfolio> portfolio(GameSave save, String portfolioId) {
        if (save == null || portfolioId == null) {
            return Optional.empty();
        }
        return save.brokerage.portfolios.stream()
                .filter(portfolio -> portfolio.portfolioId.equals(portfolioId))
                .findFirst();
    }

    public static List<BrokerageState.Holding> holdings(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.brokerage.holdings);
    }

    /**
     * The symbols worth spending quota on: everything held, plus everything on a watchlist.
     *
     * <h2>⚠ This decides where a player's API allowance goes</h2>
     *
     * A free tier is a few hundred calls a day, and the catalogue is a couple of hundred symbols
     * and grows with every discovery. Refreshing all
     * of them at the player's chosen cadence would burn the whole day's budget in minutes on prices
     * nobody is looking at. So these get the fast cadence and everything else gets one call a day —
     * which is also the honest split, because these are the only prices that are about the player's
     * own money.
     */
    public static java.util.Set<String> tracked(GameSave save) {
        if (save == null) {
            return java.util.Set.of();
        }
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        save.brokerage.holdings.forEach(holding -> out.add(holding.symbol));
        save.brokerage.portfolios.forEach(portfolio -> out.addAll(portfolio.watching));
        return out;
    }

    public static List<BrokerageState.Portfolio> portfolios(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.brokerage.portfolios);
    }
}
