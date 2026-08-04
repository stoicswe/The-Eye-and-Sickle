package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.BrokerageState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.stocks.MarketCalendar;
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
        return Result.ok("sold " + shares + " × " + displayName(holding.symbol) + " for "
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(net)
                + " net — " + (gain.signum() >= 0 ? "up " : "down ")
                + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(gain.abs())
                + " on the parcel.");
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

    public static List<BrokerageState.Portfolio> portfolios(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.brokerage.portfolios);
    }
}
