package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
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

    public List<Holding> holdings = new ArrayList<>();

    public List<Portfolio> portfolios = new ArrayList<>();

    public BrokerageState() {}
}
