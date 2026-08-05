package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** What a character holds on AnonShare, and how they have filed it. */
public final class BrokerageState {

    /** One parcel of shares, bought at one moment for one price. */
    public static final class Holding {

        public String holdingId = UUID.randomUUID().toString();

        public String symbol = "";

        public int shares = 0;

        /**
         * What was paid per share, in wei.
         *
         * <p>⚠ Kept per PARCEL rather than averaged across the symbol. A player who bought twice at
         * different prices has two positions with two different answers to "am I up on this", and an
         * averaged book can only show one — which is the number they did not ask for.
         */
        public BigInteger costPerShareWei = BigInteger.ZERO;

        public Instant boughtAt = Instant.EPOCH;

        /** Which portfolio it is filed under. Empty means unfiled. */
        public String portfolioId = "";

        /**
         * The last quarter this parcel was paid a dividend for.
         *
         * <p>⚠ THE GUARANTEE THAT IT IS PAID ONCE. The tick runs every second and a quarter stays
         * the current quarter for three months — without this a holder would be paid once per second
         * for a quarter of a year, which is not a bug that degrades gracefully.
         *
         * <p>⚠ Initialised to zero, which is a quarter no character can be in, so a parcel bought
         * before this field existed is simply eligible for the current one rather than for every
         * quarter since year zero.
         */
        public long lastPaidQuarter = 0L;

        public Holding() {}
    }

    /** A named collection the player watches. */
    public static final class Portfolio {

        public String portfolioId = UUID.randomUUID().toString();

        public String name = "";

        /**
         * Symbols on the watchlist that are not held.
         *
         * <p>⚠ Separate from holdings on purpose: watching and owning are different relationships,
         * and a portfolio that could only contain what you already bought would be no use for
         * deciding what to buy.
         */
        public List<String> watching = new ArrayList<>();

        public Portfolio() {}
    }

    /**
     * One recorded price or portfolio value.
     *
     * <h2>⚠ RECORDED, because a live price cannot be recomputed</h2>
     *
     * Every other series in this game is derived — the Shadow Market's candles are seekable noise, so
     * a week of history costs nothing and is stored nowhere. A <b>real</b> quote is not: nobody can
     * ask what AAPL cost at 14:32 last Tuesday without having written it down at 14:32 last Tuesday.
     * So this is the one price series in the codebase that is genuinely state.
     */
    public static final class Sample {

        public Instant at = Instant.EPOCH;

        /** ⚠ Initialised, never null — the money-field rule. */
        public java.math.BigInteger wei = java.math.BigInteger.ZERO;

        public Sample() {}

        public Sample(Instant at, java.math.BigInteger wei) {
            this.at = at;
            this.wei = wei;
        }
    }

    /**
     * The most samples kept per series.
     *
     * <p>⚠ Bounded, and trimmed from the FRONT. A save is a file a human is meant to be able to read,
     * and an unbounded series sampled every few minutes for a year is tens of thousands of rows of
     * noise around the parts that matter.
     */
    public static final int HISTORY_LIMIT = 240;

    /**
     * Price history per symbol, oldest first.
     *
     * <p>⚠ Only for symbols the player actually <b>holds</b>. Recording the whole catalogue would be
     * fifty series nobody looks at, and the reason to keep any of them is that they are about the
     * player's own money.
     */
    public Map<String, List<Sample>> priceHistory = new LinkedHashMap<>();

    /**
     * What the whole portfolio was worth, oldest first.
     *
     * <p>⚠ Recorded as its own series rather than reconstructed from the per-symbol ones. Rebuilding
     * it would need the share count <em>at each past instant</em>, which is not stored and would be
     * wrong the moment anybody bought or sold — the line would silently rewrite its own past every
     * time the portfolio changed.
     */
    public List<Sample> valueHistory = new ArrayList<>();

    /**
     * Every dividend this character has ever collected, added up.
     *
     * <p>⚠ A running total rather than something derived from the ledger. Dividends are one credit
     * among many there, and separating them back out by parsing a description is the kind of thing
     * that works until somebody rewords a sentence.
     */
    public java.math.BigInteger dividendsPaidWei = java.math.BigInteger.ZERO;

    /**
     * One completed buy or sell.
     *
     * <h2>⚠ Recorded at execution, never reconstructed from the ledger</h2>
     *
     * The ledger carries a credit or a debit with a sentence attached; recovering "3 shares of MSFT
     * at 458.44 with 9.12 commission" from it would mean parsing that sentence, which works until
     * somebody rewords it. A trade has fields, so it gets a row.
     */
    public static final class Trade {

        public String tradeId = UUID.randomUUID().toString();

        public String symbol = "";

        /** True for a purchase. */
        public boolean buy = true;

        public int shares = 0;

        /** ⚠ Initialised, never null — the money-field rule. */
        public java.math.BigInteger pricePerShareWei = java.math.BigInteger.ZERO;

        /** What AnonShare took. ⚠ Kept separately: a net figure hides the fee that made it net. */
        public java.math.BigInteger commissionWei = java.math.BigInteger.ZERO;

        /**
         * Realised profit on a sale, signed. Zero on a purchase.
         *
         * <p>⚠ Computed against the LOT that was actually closed, at the moment it closed. Deriving
         * it later would need the cost basis of a parcel that no longer exists.
         */
        public java.math.BigInteger realisedWei = java.math.BigInteger.ZERO;

        public Instant at = Instant.EPOCH;

        public Trade() {}
    }

    /**
     * Every buy and sell, oldest first.
     *
     * <p>⚠ Bounded like the price series, and trimmed from the front. A save is a file a human is
     * meant to be able to open.
     */
    public List<Trade> trades = new ArrayList<>();

    /** The most trades kept. */
    public static final int TRADE_LIMIT = 300;

    public List<Holding> holdings = new ArrayList<>();

    public List<Portfolio> portfolios = new ArrayList<>();

    public BrokerageState() {}
}
