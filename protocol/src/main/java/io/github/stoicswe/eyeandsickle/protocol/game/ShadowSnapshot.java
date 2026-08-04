package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * The Shadow Market for one item, as the trading panel sees it.
 *
 * <h2>⚠ ONE snapshot, not six port calls</h2>
 *
 * The chart, the book, the tape and the order form are all views of the market <em>at one
 * instant</em>, and fetching them separately means fetching them at four different instants — a book
 * quoting one price beside a candle drawing another, on the same screen, with nothing to say which
 * is right. One call, one clock reading, one consistent picture.
 *
 * <h2>⚠ RESULTS, never the rules that produced them</h2>
 *
 * Prices, sizes and ratings — not the noise seed, the octave count, the reputation swing or the
 * arbitrage ceiling. A client holding the model could predict the next print, and predicting is one
 * step from asserting ({@code docs/architecture/13} §4).
 *
 * @param itemType which listing
 * @param displayName what to call it
 * @param asOf the instant the rules answered — <b>the session's clock, never the wall clock</b>
 * @param mid the current mid price
 * @param changePercent movement over the charted window, signed
 * @param candles oldest first, the newest still forming
 * @param bids best first
 * @param asks best first
 * @param tape recent prints, newest first
 * @param openOrders the player's own resting orders on this listing
 * @param holdings how many of this item the player owns and could sell
 */
public record ShadowSnapshot(
        String itemType,
        String displayName,
        Instant asOf,
        BigInteger mid,
        double changePercent,
        List<ShadowCandle> candles,
        List<ShadowLevel> bids,
        List<ShadowLevel> asks,
        List<ShadowPrint> tape,
        List<ShadowOrder> openOrders,
        int holdings) {

    public ShadowSnapshot {
        candles = List.copyOf(candles);
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
        tape = List.copyOf(tape);
        openOrders = List.copyOf(openOrders);
    }

    /** A market with nothing in it — what a session answers when it cannot price anything. */
    public static ShadowSnapshot none(String itemType) {
        return new ShadowSnapshot(
                itemType,
                itemType,
                Instant.EPOCH,
                BigInteger.ZERO,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0);
    }

    public BigInteger bestBid() {
        return bids.isEmpty() ? BigInteger.ZERO : bids.getFirst().price();
    }

    public BigInteger bestAsk() {
        return asks.isEmpty() ? BigInteger.ZERO : asks.getFirst().price();
    }

    public BigInteger spread() {
        return bids.isEmpty() || asks.isEmpty() ? BigInteger.ZERO : bestAsk().subtract(bestBid());
    }
}
