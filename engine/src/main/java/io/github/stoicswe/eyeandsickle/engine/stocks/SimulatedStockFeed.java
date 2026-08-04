package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Prices with nobody on the other end of them.
 *
 * <h2>⚠ It says so, loudly, and the panel must repeat it</h2>
 *
 * {@link #describe()} is rendered on screen and {@link #live()} is false. A player must never be
 * able to mistake these for the market — somebody acting on a simulated quote in the belief it was
 * real is the one failure this tab could cause outside the game.
 *
 * <h2>Same machinery as the Shadow Market, for the same reasons</h2>
 *
 * Bounded fractal noise: seekable, so a chart costs no history; deterministic, so the price does not
 * reshuffle on every repaint; and bounded, so a symbol cannot wander to zero or to infinity over a
 * long campaign. Anchored on the listing's reference price so the numbers are plausible rather than
 * arbitrary.
 *
 * <p>⚠ It moves on a <b>daily</b> and an <b>intraday</b> scale but <b>only while the market is
 * open</b> — a simulated price that drifted over the weekend would teach a player that this market
 * trades at times the real one does not, which is exactly the thing the calendar exists to model.
 */
public final class SimulatedStockFeed implements StockFeed {

    /** How far a symbol can wander from its reference, either way. */
    private static final double SWING = 0.35d;

    private final long seed;

    /**
     * @param seed usually the character id's hash, so two characters see different simulated markets
     *     — and the same character sees the same one every session
     */
    public SimulatedStockFeed(long seed) {
        this.seed = seed;
    }

    @Override
    public Optional<Quote> quote(String symbol, Instant now) {
        Optional<Tickers.Listing> listing = Tickers.bySymbol(symbol);
        if (listing.isEmpty()) {
            return Optional.empty();
        }
        Tickers.Listing it = listing.get();
        // ⚠ Frozen outside session hours. The last trade of the day is the price until the next open,
        // which is what a real quote does and what makes the calendar mean something.
        // ⚠ The BELL, not "now". A frozen price has to freeze on one instant, and stepping back a
        // fixed span instead gives Saturday morning and Saturday evening two different answers — so
        // the frozen weekend price drifts all weekend, which is what freezing exists to prevent.
        Instant effective = MarketCalendar.sessionAt(now).tradable() ? now : MarketCalendar.previousClose(now);
        BigInteger price = priceAt(it, effective);
        // The prior session's close, so the change-on-day figure compares two closes rather than
        // two arbitrary instants a day apart.
        BigInteger previous = priceAt(it, MarketCalendar.previousClose(effective.minusSeconds(60)));
        return Optional.of(new Quote(it.symbol(), price, previous, effective, false));
    }

    private BigInteger priceAt(Tickers.Listing listing, Instant at) {
        long symbolSeed = seed * 31L + listing.symbol().hashCode();
        double unit = noise(symbolSeed, at.getEpochSecond());
        double multiplier = 1 + (unit * 2 - 1) * SWING;
        // Whole wei, so the value type never sees a fraction it cannot hold exactly.
        return BigInteger.valueOf(Math.max(1L, Math.round(listing.referencePrice() * multiplier * 100)))
                .multiply(BigInteger.TEN.pow(16));
    }

    /** Three octaves — a slow trend, a daily swing and an intraday wobble. */
    private static double noise(long symbolSeed, long epochSecond) {
        double total = 0;
        double amplitude = 1;
        double normal = 0;
        long bucket = 30L * 24 * 3600;
        for (int octave = 0; octave < 3; octave++) {
            long index = Math.floorDiv(epochSecond, bucket);
            double t = (epochSecond - index * bucket) / (double) bucket;
            double a = unit(hash(symbolSeed, index, octave));
            double b = unit(hash(symbolSeed, index + 1, octave));
            total += (a + (b - a) * t) * amplitude;
            normal += amplitude;
            amplitude /= 2;
            bucket /= 12;
        }
        return total / normal;
    }

    private static long hash(long seed, long a, long b) {
        long x = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    private static double unit(long hash) {
        return Math.floorMod(hash, 1_000_003L) / 1_000_003.0d;
    }

    @Override
    public String describe() {
        return "simulated — not real market data";
    }

    @Override
    public boolean live() {
        return false;
    }
}
