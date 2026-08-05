package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * AnonShare, as the panel sees it.
 *
 * <h2>⚠ {@code feedIsLive} MUST reach the screen</h2>
 *
 * A simulated price presented as a real one is the only harm this tab could cause outside the game —
 * somebody could act on it believing it was the market. The flag and {@code feedLabel} travel with
 * every quote so the panel has no way to render one without the other.
 *
 * @param symbol the real ticker
 * @param displayName the aliased company name — ⚠ the real one never crosses this boundary
 * @param sector the grouping
 * @param priceWei the price, in EC at $1 = 1 EC
 * @param previousCloseWei the prior session's close, for the change figure
 * @param changePercent movement since that close, signed
 * @param annualYieldBp what a holder is paid across a year, in basis points; zero for a non-payer
 * @param marketPhase {@code OPEN}, {@code PRE}, {@code POST} or {@code CLOSED}
 * @param phaseChangesAt when that next changes — <b>an Instant</b>, rendered in the player's own
 *     timezone, because the session is New York's and the clock on screen is theirs
 * @param asOf the session's clock
 * @param feedLabel what the price source calls itself
 * @param feedIsLive whether these are real prices
 * @param results what a search matched
 * @param holdings the player's parcels
 * @param portfolios their collections
 * @param positions holdings collapsed to one row per symbol — ⚠ the PARCELS still exist underneath;
 *     a player has one position and several lots, and every broker shows the first and keeps the second
 * @param valueHistory what the portfolio has been worth, oldest first. ⚠ RECORDED rather than derived:
 *     a real quote cannot be recomputed after the fact
 * @param portfolioValueWei what the holdings are worth now
 * @param portfolioCostWei what they cost, so the panel can show the difference without recomputing it
 * @param cashWei uninvested ethecoin
 * @param dividendsWei what dividends have paid out in total
 * @param tracked every symbol the player holds OR watches, each with its recorded series. ⚠ The same
 *     set that gets the fast refresh cadence, which is what makes a watchlist's chart worth drawing
 * @param trades every buy and sell this character has made, <b>newest first</b>. ⚠ RECORDED at the
 *     moment of the trade and never recomputed: the price paid is a fact about an instant, and a
 *     history rebuilt from today's quotes would rewrite what somebody actually paid
 * @param nextRefreshAt when this symbol's price is next fetched. ⚠ {@code EPOCH} means the feed is
 *     derived and never refreshes, so the panel must render no timer rather than an invented one
 */
public record SharesSnapshot(
        String symbol,
        String displayName,
        String sector,
        BigInteger priceWei,
        BigInteger previousCloseWei,
        double changePercent,
        long annualYieldBp,
        String marketPhase,
        Instant phaseChangesAt,
        Instant asOf,
        String feedLabel,
        boolean feedIsLive,
        List<Result> results,
        List<Holding> holdings,
        List<Portfolio> portfolios,
        List<Position> positions,
        List<Point> valueHistory,
        BigInteger portfolioValueWei,
        BigInteger portfolioCostWei,
        BigInteger cashWei,
        BigInteger dividendsWei,
        List<Tracked> tracked,
        List<Trade> trades,
        Instant nextRefreshAt) {

    public SharesSnapshot {
        results = List.copyOf(results);
        holdings = List.copyOf(holdings);
        portfolios = List.copyOf(portfolios);
        positions = List.copyOf(positions);
        valueHistory = List.copyOf(valueHistory);
        tracked = List.copyOf(tracked);
        trades = List.copyOf(trades);
    }

    public boolean tradable() {
        return "OPEN".equals(marketPhase);
    }

    /**
     * How long until the next fetch, or empty when the feed does not fetch.
     *
     * <p>⚠ Measured against {@link #asOf}, the SESSION's clock, like everything else with a deadline
     * here. A countdown built on the wall clock reports a different number from the one the engine
     * will act on.
     */
    public java.util.Optional<Duration> untilRefresh() {
        if (nextRefreshAt == null || nextRefreshAt.equals(Instant.EPOCH)) {
            return java.util.Optional.empty();
        }
        Duration left = Duration.between(asOf, nextRefreshAt);
        return java.util.Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    /** How long until the session changes, never negative. */
    public Duration untilPhaseChange() {
        Duration left = Duration.between(asOf, phaseChangesAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** One search hit. */
    public record Result(String symbol, String displayName, String sector, BigInteger priceWei, double changePercent) {}

    /**
     * One parcel.
     *
     * @param costPerShareWei what was paid — ⚠ per parcel, because two buys at different prices are
     *     two positions with two different answers to "am I up on this"
     * @param valueWei what it is worth now
     * @param portfolioId which collection it is filed under, blank if unfiled
     */
    public record Holding(
            String holdingId,
            String symbol,
            String displayName,
            int shares,
            BigInteger costPerShareWei,
            BigInteger valueWei,
            String portfolioId) {

        /** Signed, so the panel can colour it without recomputing. */
        public BigInteger gainWei() {
            return valueWei.subtract(costPerShareWei.multiply(BigInteger.valueOf(shares)));
        }
    }

    /**
     * One symbol's whole position.
     *
     * @param priceWei what it is worth per share now
     * @param history the recorded price series for it, oldest first
     */
    public record Position(
            String symbol,
            String displayName,
            int shares,
            BigInteger averageCostWei,
            BigInteger priceWei,
            double changePercent,
            List<Point> history) {

        public Position {
            history = List.copyOf(history);
        }

        public BigInteger valueWei() {
            return priceWei.multiply(BigInteger.valueOf(shares));
        }

        /** Signed against what the lot actually cost. */
        public BigInteger gainWei() {
            return valueWei().subtract(averageCostWei.multiply(BigInteger.valueOf(shares)));
        }
    }

    /**
     * A symbol the player is following, held or merely watched.
     *
     * <p>⚠ This is what a watchlist row is made of. A watchlist that carried only symbols would be a
     * list of names, and the panel would have to go and quote each one itself — on a tab that is
     * usually off screen.
     */
    public record Tracked(
            String symbol,
            String displayName,
            String sector,
            BigInteger priceWei,
            double changePercent,
            long annualYieldBp,
            int sharesHeld,
            List<Point> history) {

        public Tracked {
            history = List.copyOf(history);
        }

        /** Whether this is the player's money or only something they are following. */
        public boolean held() {
            return sharesHeld > 0;
        }
    }

    /**
     * One executed trade.
     *
     * <p>⚠ {@code realisedWei} is meaningful on a SELL only, and is the gain against what the lots
     * sold actually cost — a buy realises nothing, so it carries zero rather than a placeholder.
     *
     * <p>⚠ The commission is kept SEPARATE from the price rather than folded into it. They are two
     * different facts: what the market charged and what the broker charged, and a history that
     * merged them could not answer why a round trip at the same price lost money.
     */
    public record Trade(
            String tradeId,
            String symbol,
            String displayName,
            boolean buy,
            int shares,
            BigInteger pricePerShareWei,
            BigInteger commissionWei,
            BigInteger realisedWei,
            Instant at) {

        /** What changed hands, before the commission. */
        public BigInteger considerationWei() {
            return pricePerShareWei.multiply(BigInteger.valueOf(shares));
        }
    }

    /** One recorded point. */
    public record Point(Instant at, BigInteger wei) {}

    /** A watchlist and whatever is filed under it. */
    public record Portfolio(String portfolioId, String name, List<String> watching, BigInteger valueWei) {

        public Portfolio {
            watching = List.copyOf(watching);
        }
    }
}
