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
        List<Portfolio> portfolios) {

    public SharesSnapshot {
        results = List.copyOf(results);
        holdings = List.copyOf(holdings);
        portfolios = List.copyOf(portfolios);
    }

    public boolean tradable() {
        return "OPEN".equals(marketPhase);
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

    /** A watchlist and whatever is filed under it. */
    public record Portfolio(String portfolioId, String name, List<String> watching, BigInteger valueWei) {

        public Portfolio {
            watching = List.copyOf(watching);
        }
    }
}
