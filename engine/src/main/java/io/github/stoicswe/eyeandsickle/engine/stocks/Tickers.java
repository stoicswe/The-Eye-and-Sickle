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
            new Listing("CSCO", "Cisco Systems Inc.", "technology", 52, 300),
            new Listing("ABBV", "AbbVie Inc.", "health", 175, 340),
            new Listing("ABT", "Abbott Laboratories", "health", 112, 190),
            new Listing("ACN", "Accenture plc", "technology", 340, 160),
            new Listing("ADI", "Analog Devices Inc.", "semiconductors", 225, 170),
            new Listing("ADP", "Automatic Data Processing Inc.", "technology", 290, 200),
            new Listing("AIG", "American International Group Inc.", "finance", 76, 200),
            new Listing("AMAT", "Applied Materials Inc.", "semiconductors", 190, 90),
            new Listing("AMGN", "Amgen Inc.", "health", 315, 290),
            new Listing("AMT", "American Tower Corp.", "realty", 195, 330),
            new Listing("AON", "Aon plc", "finance", 350, 90),
            new Listing("APD", "Air Products and Chemicals Inc.", "industrial", 290, 240),
            new Listing("AXP", "American Express Co.", "finance", 270, 100),
            new Listing("AZO", "AutoZone Inc.", "retail", 3100, 0),
            new Listing("BAC", "Bank of America Corp.", "finance", 42, 240),
            new Listing("BAX", "Baxter International Inc.", "health", 34, 340),
            new Listing("BBY", "Best Buy Co. Inc.", "retail", 88, 420),
            new Listing("BDX", "Becton Dickinson and Co.", "health", 235, 160),
            new Listing("BIIB", "Biogen Inc.", "health", 190, 0),
            new Listing("BK", "Bank of New York Mellon Corp.", "finance", 73, 220),
            new Listing("BKNG", "Booking Holdings Inc.", "travel", 3900, 80),
            new Listing("BLK", "BlackRock Inc.", "finance", 890, 220),
            new Listing("BMY", "Bristol-Myers Squibb Co.", "health", 52, 470),
            new Listing("BSX", "Boston Scientific Corp.", "health", 84, 0),
            new Listing("C", "Citigroup Inc.", "finance", 63, 320),
            new Listing("CB", "Chubb Ltd.", "finance", 275, 130),
            new Listing("CCL", "Carnival Corp.", "travel", 22, 0),
            new Listing("CI", "Cigna Group", "health", 330, 180),
            new Listing("CL", "Colgate-Palmolive Co.", "consumer", 95, 210),
            new Listing("CMCSA", "Comcast Corp.", "media", 42, 320),
            new Listing("CME", "CME Group Inc.", "finance", 225, 190),
            new Listing("CMG", "Chipotle Mexican Grill Inc.", "retail", 58, 0),
            new Listing("COF", "Capital One Financial Corp.", "finance", 165, 150),
            new Listing("COP", "ConocoPhillips", "energy", 105, 290),
            new Listing("CRWD", "CrowdStrike Holdings Inc.", "software", 300, 0),
            new Listing("CSX", "CSX Corp.", "industrial", 34, 130),
            new Listing("CVS", "CVS Health Corp.", "health", 58, 450),
            new Listing("DE", "Deere & Co.", "industrial", 400, 140),
            new Listing("DHR", "Danaher Corp.", "health", 250, 50),
            new Listing("DOW", "Dow Inc.", "industrial", 42, 650),
            new Listing("DUK", "Duke Energy Corp.", "energy", 110, 370),
            new Listing("EBAY", "eBay Inc.", "retail", 62, 190),
            new Listing("ECL", "Ecolab Inc.", "industrial", 245, 90),
            new Listing("ED", "Consolidated Edison Inc.", "energy", 98, 330),
            new Listing("EL", "Estee Lauder Companies Inc.", "consumer", 78, 320),
            new Listing("EMR", "Emerson Electric Co.", "industrial", 110, 190),
            new Listing("EOG", "EOG Resources Inc.", "energy", 125, 280),
            new Listing("EQIX", "Equinix Inc.", "realty", 790, 210),
            new Listing("ETN", "Eaton Corp. plc", "industrial", 300, 120),
            new Listing("EW", "Edwards Lifesciences Corp.", "health", 68, 0),
            new Listing("FDX", "FedEx Corp.", "industrial", 260, 200),
            new Listing("FIS", "Fidelity National Information Services Inc.", "finance", 78, 180),
            new Listing("GD", "General Dynamics Corp.", "industrial", 290, 200),
            new Listing("GILD", "Gilead Sciences Inc.", "health", 88, 350),
            new Listing("GIS", "General Mills Inc.", "consumer", 65, 360),
            new Listing("GLW", "Corning Inc.", "technology", 48, 200),
            new Listing("HAL", "Halliburton Co.", "energy", 28, 240),
            new Listing("HCA", "HCA Healthcare Inc.", "health", 340, 80),
            new Listing("HON", "Honeywell International Inc.", "industrial", 210, 200),
            new Listing("HPQ", "HP Inc.", "technology", 34, 340),
            new Listing("HUM", "Humana Inc.", "health", 290, 120),
            new Listing("IBM", "International Business Machines Corp.", "technology", 230, 300),
            new Listing("ICE", "Intercontinental Exchange Inc.", "finance", 155, 120),
            new Listing("IDXX", "IDEXX Laboratories Inc.", "health", 440, 0),
            new Listing("ILMN", "Illumina Inc.", "health", 120, 0),
            new Listing("INTU", "Intuit Inc.", "software", 620, 60),
            new Listing("ISRG", "Intuitive Surgical Inc.", "health", 480, 0),
            new Listing("ITW", "Illinois Tool Works Inc.", "industrial", 255, 230),
            new Listing("JCI", "Johnson Controls International plc", "industrial", 78, 200),
            new Listing("KDP", "Keurig Dr Pepper Inc.", "consumer", 34, 260),
            new Listing("KHC", "Kraft Heinz Co.", "consumer", 32, 480),
            new Listing("KLAC", "KLA Corp.", "semiconductors", 700, 80),
            new Listing("KMB", "Kimberly-Clark Corp.", "consumer", 135, 360),
            new Listing("KMI", "Kinder Morgan Inc.", "energy", 22, 510),
            new Listing("KR", "Kroger Co.", "retail", 56, 200),
            new Listing("LHX", "L3Harris Technologies Inc.", "industrial", 230, 200),
            new Listing("LIN", "Linde plc", "industrial", 450, 120),
            new Listing("LLY", "Eli Lilly and Co.", "health", 790, 70),
            new Listing("LMT", "Lockheed Martin Corp.", "industrial", 470, 270),
            new Listing("LOW", "Lowe's Companies Inc.", "retail", 250, 180),
            new Listing("LRCX", "Lam Research Corp.", "semiconductors", 78, 110),
            new Listing("MAR", "Marriott International Inc.", "travel", 250, 90),
            new Listing("MCD", "McDonald's Corp.", "retail", 295, 230),
            new Listing("MCK", "McKesson Corp.", "health", 580, 50),
            new Listing("MDLZ", "Mondelez International Inc.", "consumer", 66, 280),
            new Listing("MDT", "Medtronic plc", "health", 88, 320),
            new Listing("MET", "MetLife Inc.", "finance", 78, 270),
            new Listing("MMC", "Marsh & McLennan Companies Inc.", "finance", 215, 140),
            new Listing("MMM", "3M Co.", "industrial", 130, 220),
            new Listing("MNST", "Monster Beverage Corp.", "consumer", 52, 0),
            new Listing("MO", "Altria Group Inc.", "consumer", 52, 780),
            new Listing("MPC", "Marathon Petroleum Corp.", "energy", 150, 230),
            new Listing("MRK", "Merck & Co. Inc.", "health", 98, 320),
            new Listing("MSCI", "MSCI Inc.", "finance", 560, 110),
            new Listing("MU", "Micron Technology Inc.", "semiconductors", 95, 50),
            new Listing("NEE", "NextEra Energy Inc.", "energy", 72, 290),
            new Listing("NEM", "Newmont Corp.", "mining", 42, 240),
            new Listing("NOC", "Northrop Grumman Corp.", "industrial", 480, 170),
            new Listing("NOW", "ServiceNow Inc.", "software", 900, 0),
            new Listing("NSC", "Norfolk Southern Corp.", "industrial", 245, 220),
            new Listing("NXPI", "NXP Semiconductors NV", "semiconductors", 230, 170),
            new Listing("ORLY", "O'Reilly Automotive Inc.", "retail", 1150, 0),
            new Listing("OXY", "Occidental Petroleum Corp.", "energy", 50, 180),
            new Listing("PANW", "Palo Alto Networks Inc.", "software", 340, 0),
            new Listing("PCAR", "PACCAR Inc.", "industrial", 100, 110),
            new Listing("PGR", "Progressive Corp.", "finance", 245, 50),
            new Listing("PG", "Procter & Gamble Co.", "consumer", 165, 240),
            new Listing("PH", "Parker-Hannifin Corp.", "industrial", 620, 100),
            new Listing("PLD", "Prologis Inc.", "realty", 112, 330),
            new Listing("PM", "Philip Morris International Inc.", "consumer", 120, 430),
            new Listing("PNC", "PNC Financial Services Group Inc.", "finance", 180, 320),
            new Listing("PSA", "Public Storage", "realty", 300, 400),
            new Listing("PSX", "Phillips 66", "energy", 130, 340),
            new Listing("REGN", "Regeneron Pharmaceuticals Inc.", "health", 900, 0),
            new Listing("ROK", "Rockwell Automation Inc.", "industrial", 270, 180),
            new Listing("ROP", "Roper Technologies Inc.", "technology", 560, 60),
            new Listing("ROST", "Ross Stores Inc.", "retail", 150, 100),
            new Listing("RTX", "RTX Corp.", "industrial", 120, 220),
            new Listing("SCHW", "Charles Schwab Corp.", "finance", 72, 140),
            new Listing("SHW", "Sherwin-Williams Co.", "industrial", 360, 80),
            new Listing("SLB", "Schlumberger NV", "energy", 44, 250),
            new Listing("SNPS", "Synopsys Inc.", "software", 540, 0),
            new Listing("SO", "Southern Co.", "energy", 88, 330),
            new Listing("SPGI", "S&P Global Inc.", "finance", 500, 70),
            new Listing("SRE", "Sempra", "energy", 78, 300),
            new Listing("STZ", "Constellation Brands Inc.", "consumer", 240, 150),
            new Listing("SYK", "Stryker Corp.", "health", 360, 90),
            new Listing("SYY", "Sysco Corp.", "consumer", 74, 270),
            new Listing("T", "AT&T Inc.", "technology", 22, 500),
            new Listing("TEL", "TE Connectivity plc", "technology", 150, 160),
            new Listing("TJX", "TJX Companies Inc.", "retail", 120, 120),
            new Listing("TMO", "Thermo Fisher Scientific Inc.", "health", 560, 30),
            new Listing("TMUS", "T-Mobile US Inc.", "technology", 230, 140),
            new Listing("TRV", "Travelers Companies Inc.", "finance", 255, 170),
            new Listing("TXN", "Texas Instruments Inc.", "semiconductors", 200, 270),
            new Listing("UPS", "United Parcel Service Inc.", "industrial", 130, 480),
            new Listing("USB", "U.S. Bancorp", "finance", 46, 410),
            new Listing("VLO", "Valero Energy Corp.", "energy", 140, 320),
            new Listing("VRTX", "Vertex Pharmaceuticals Inc.", "health", 450, 0),
            new Listing("VZ", "Verizon Communications Inc.", "technology", 42, 630),
            new Listing("WM", "Waste Management Inc.", "industrial", 210, 140),
            new Listing("WMB", "Williams Companies Inc.", "energy", 52, 380),
            new Listing("YUM", "Yum! Brands Inc.", "retail", 140, 190),
            new Listing("ZTS", "Zoetis Inc.", "health", 175, 110));

    /**
     * Symbols learned from a provider's lookup endpoint, on top of the bundled set.
     *
     * <h2>⚠ GLOBAL AND MUTABLE, which this codebase otherwise avoids — here is the argument</h2>
     *
     * This is <b>reference data, never game state</b>: a mapping from a real ticker to the real
     * company's registered name. It is the same for every character, every save and every player,
     * it is append-only, and nothing about it can advantage anybody — an unknown symbol is simply
     * one the search has not met yet. Threading a directory through {@code Brokerage},
     * {@code Dividends}, the feed and the view would be six seams carrying a fact that is true
     * everywhere.
     *
     * <p>⚠ It is <b>not</b> a cache of anything the rules depend on. Prices are the feed's, holdings
     * are the save's, and this only decides what a symbol is <em>called</em>. If that ever stops
     * being true, this has to become a port.
     *
     * <p>⚠ {@link #forget()} exists for tests, because a static registry that leaked between them
     * would make one test's discovery another test's mystery.
     */
    private static final java.util.Map<String, Listing> DISCOVERED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Adds a symbol learned from a lookup.
     *
     * <p>⚠ Never overwrites a bundled entry. The bundled ones carry a hand-set reference price and
     * sector that the offline feed needs; a provider's description would replace those with nothing.
     *
     * @param symbol the real ticker
     * @param realName the registered name, which {@link Aliaser} will rename
     */
    public static void register(String symbol, String realName) {
        if (symbol == null || symbol.isBlank() || realName == null || realName.isBlank()) {
            return;
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        if (ALL.stream().anyMatch(listing -> listing.symbol().equals(upper))) {
            return;
        }
        // ⚠ A plausible reference price DERIVED from the symbol, so the offline feed has an anchor
        // and a discovered symbol is tradeable with no network. It is not a claim about the real
        // price and is never shown as one — the live feed overwrites it the moment a quote lands.
        long anchor = 20 + Math.floorMod(upper.hashCode(), 400L);
        DISCOVERED.putIfAbsent(upper, new Listing(upper, realName.trim(), "discovered", anchor, 0));
    }

    /** ⚠ Tests only. A static registry leaking between them is one test's discovery becoming another's mystery. */
    public static void forget() {
        DISCOVERED.clear();
    }

    /**
     * The bundled set — what the LISTINGS panel shows.
     *
     * <h2>⚠ Deliberately the BUNDLED FIFTY and not everything known</h2>
     *
     * ⚠ <b>The symbols are real; the names are only used to derive an alias.</b> A player never sees
     * {@code realName} — {@link Aliaser} renames every one — so a name that is slightly off produces
     * a slightly different <em>invented</em> name rather than a false claim about a company. That is
     * what makes a bundled list this size defensible: the <b>symbol</b> is the part that has to be
     * right, because it is what a quote is fetched against.
     *
     * <p>⚠ {@code referencePrice} and {@code annualYieldBp} are plausible ANCHORS for the offline
     * feed and never claims about a real price or a declared dividend. The live feed overwrites the
     * price the moment a quote lands.
     *
     * <p>Anything not here is reached by typing its ticker, which is how a real terminal works and
     * how the universe grows.
     */
    public static List<Listing> all() {
        return ALL;
    }

    /** Everything known — bundled plus discovered. What search and price lookups consult. */
    public static List<Listing> known() {
        List<Listing> out = new java.util.ArrayList<>(ALL);
        out.addAll(DISCOVERED.values());
        return List.copyOf(out);
    }

    /** Symbols learned so far, for the client to persist. */
    public static java.util.Map<String, String> discovered() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        DISCOVERED.forEach((symbol, listing) -> out.put(symbol, listing.realName()));
        return out;
    }

    /** @return the listing for a symbol, case-insensitively — a player types {@code aapl}. */
    public static Optional<Listing> bySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String wanted = symbol.trim().toUpperCase(Locale.ROOT);
        return known().stream().filter(listing -> listing.symbol().equals(wanted)).findFirst();
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
        return known().stream()
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
