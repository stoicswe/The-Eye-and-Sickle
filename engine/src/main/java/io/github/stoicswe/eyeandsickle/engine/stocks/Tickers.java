package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The symbols AnonShare lists.
 *
 * <h2>⚠ REAL symbols, aliased names</h2>
 *
 * A player who types {@code AAPL} should get the thing {@code AAPL} tracks — that is the whole point
 * of using the real market. What comes back is {@link Aliaser}'s version of the name, because the
 * game is not about real companies and a darknet brokerage in a surveillance dystopia is not a place
 * to put somebody's actual trademark.
 *
 * <h2>Why the universe is bundled rather than fetched</h2>
 *
 * The client must work with no network — that is a standing promise, not a preference — so the list
 * of <em>what exists</em> cannot depend on a request succeeding. Prices are the part that needs a
 * feed; the universe is a fact about the market that changes slowly, and a stale entry is a symbol
 * that quotes nothing rather than a broken screen.
 */
public final class Tickers {

    private Tickers() {}

    /**
     * One listed company.
     *
     * @param symbol the real ticker
     * @param realName the real registered name, used ONLY to derive {@link #displayName}
     * @param sector the tab's grouping
     * @param referencePrice a plausible price in whole EC, used by the offline feed as an anchor —
     *     ⚠ <b>not</b> a claim about the real price, and never shown as one
     * @param annualYieldBp what a holder receives across a year, in basis points of the price. ⚠ Like
     *     {@code referencePrice}, a plausible anchor shaped like the real market — mature industrials
     *     and staples pay, growth names do not — and <b>not</b> a claim about any company's actual
     *     declared dividend
     */
    public record Listing(
            String symbol, String realName, String sector, long referencePrice, long annualYieldBp) {

        /**
         * The many entries that pay nothing.
         *
         * <p>⚠ Zero is the default because "this one pays no dividend" is a real and interesting
         * property of a share — a growth company that reinvests everything. Defaulting the other way
         * would invent income for companies famous for not paying any.
         */
        public Listing(String symbol, String realName, String sector, long referencePrice) {
            this(symbol, realName, sector, referencePrice, 0);
        }

        /** Whether a holder is paid anything for simply holding. */
        public boolean paysDividend() {
            return annualYieldBp > 0;
        }

        /** What AnonShare calls it. Derived, never stored, so the vocabulary can grow. */
        public String displayName() {
            return Aliaser.alias(realName, symbol);
        }
    }

    private static final List<Listing> ALL = List.of(
            new Listing("AAPL", "Apple Inc.", "technology", 225, 45),
            new Listing("MSFT", "Microsoft Corp.", "technology", 420, 70),
            new Listing("GOOGL", "Alphabet Inc.", "technology", 165, 45),
            new Listing("AMZN", "Amazon.com Inc.", "retail", 185),
            new Listing("META", "Meta Platforms Inc.", "technology", 505, 35),
            new Listing("NVDA", "NVIDIA Corp.", "semiconductors", 125, 3),
            new Listing("AMD", "Advanced Micro Devices Inc.", "semiconductors", 160),
            new Listing("INTC", "Intel Corp.", "semiconductors", 32, 180),
            new Listing("AVGO", "Broadcom Inc.", "semiconductors", 165, 120),
            new Listing("QCOM", "Qualcomm Inc.", "semiconductors", 170, 200),
            new Listing("TSLA", "Tesla Inc.", "automotive", 245),
            new Listing("F", "Ford Motor Co.", "automotive", 11, 520),
            new Listing("GM", "General Motors Co.", "automotive", 45, 90),
            new Listing("NFLX", "Netflix Inc.", "media", 690),
            new Listing("DIS", "Walt Disney Co.", "media", 95, 90),
            new Listing("SPOT", "Spotify Technology SA", "media", 340),
            new Listing("CRM", "Salesforce Inc.", "software", 265, 40),
            new Listing("ORCL", "Oracle Corp.", "software", 170, 90),
            new Listing("ADBE", "Adobe Inc.", "software", 510),
            new Listing("PLTR", "Palantir Technologies Inc.", "software", 35),
            new Listing("SHOP", "Shopify Inc.", "retail", 78),
            new Listing("WMT", "Walmart Inc.", "retail", 78, 95),
            new Listing("COST", "Costco Wholesale Corp.", "retail", 880, 55),
            new Listing("TGT", "Target Corp.", "retail", 150, 290),
            new Listing("HD", "Home Depot Inc.", "retail", 385, 240),
            new Listing("NKE", "Nike Inc.", "retail", 80, 180),
            new Listing("SBUX", "Starbucks Corp.", "retail", 95, 250),
            new Listing("KO", "Coca-Cola Co.", "consumer", 68, 300),
            new Listing("PEP", "PepsiCo Inc.", "consumer", 170, 340),
            new Listing("JPM", "JPMorgan Chase & Co.", "finance", 215, 210),
            new Listing("GS", "Goldman Sachs Group Inc.", "finance", 500, 230),
            new Listing("MS", "Morgan Stanley", "finance", 105, 320),
            new Listing("WFC", "Wells Fargo & Co.", "finance", 57, 240),
            new Listing("V", "Visa Inc.", "finance", 280, 75),
            new Listing("MA", "Mastercard Inc.", "finance", 490, 55),
            new Listing("PYPL", "PayPal Holdings Inc.", "finance", 72),
            new Listing("XOM", "Exxon Mobil Corp.", "energy", 118, 330),
            new Listing("CVX", "Chevron Corp.", "energy", 150, 420),
            new Listing("JNJ", "Johnson & Johnson", "health", 160, 300),
            new Listing("PFE", "Pfizer Inc.", "health", 29, 590),
            new Listing("MRNA", "Moderna Inc.", "health", 65),
            new Listing("UNH", "UnitedHealth Group Inc.", "health", 580, 160),
            new Listing("BA", "Boeing Co.", "industrial", 155),
            new Listing("GE", "General Electric Co.", "industrial", 180, 70),
            new Listing("CAT", "Caterpillar Inc.", "industrial", 345, 150),
            new Listing("UNP", "Union Pacific Corp.", "industrial", 240, 220),
            new Listing("DAL", "Delta Air Lines Inc.", "industrial", 48, 100),
            new Listing("UBER", "Uber Technologies Inc.", "industrial", 72),
            new Listing("ABNB", "Airbnb Inc.", "travel", 130),
            new Listing("CSCO", "Cisco Systems Inc.", "technology", 52, 300));

    public static List<Listing> all() {
        return ALL;
    }

    /** @return the listing for a symbol, case-insensitively — a player types {@code aapl}. */
    public static Optional<Listing> bySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String wanted = symbol.trim().toUpperCase(Locale.ROOT);
        return ALL.stream().filter(listing -> listing.symbol().equals(wanted)).findFirst();
    }

    /**
     * Symbol or aliased-name search.
     *
     * <p>⚠ Searches the ALIAS, never the real name. A player who found "Apple" by typing it would
     * have been told the real name, which is the one thing this layer exists not to do.
     */
    public static List<Listing> search(String query) {
        if (query == null || query.isBlank()) {
            return ALL;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(listing -> listing.symbol().toLowerCase(Locale.ROOT).contains(needle)
                        || listing.displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || listing.sector().contains(needle))
                .toList();
    }

    /** Every sector, for the picker. */
    public static List<String> sectors() {
        return ALL.stream().map(Listing::sector).distinct().sorted().toList();
    }
}
