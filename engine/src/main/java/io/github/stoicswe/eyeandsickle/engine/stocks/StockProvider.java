package io.github.stoicswe.eyeandsickle.engine.stocks;

/**
 * Which quote service a player has chosen to use their own key with.
 *
 * <h2>⚠ THE LIMITS BELOW WERE CHECKED ON 2026-08-04 AND WILL GO STALE</h2>
 *
 * They are recorded so the picker can tell a player what they are choosing between, and they are
 * <b>not</b> enforced against — the client obeys whatever the service actually returns, including a
 * 429. A hard-coded limit that drifted from the real one would either throttle a player who had
 * headroom or hammer a service that had cut them off.
 *
 * <h2>⚠ THE PLAYER'S OWN KEY, AND THAT IS ALSO THE LICENSING ANSWER</h2>
 *
 * Public pricing pages state the rate limits plainly; none of them stated, in terms I could read,
 * whether a <em>distributed desktop application</em> may use a free key. That question does not
 * arise here: each player signs up themselves, agrees to that provider's terms themselves, and
 * fetches their own data for their own use. Nothing is redistributed and no key ships in this repo.
 * ⚠ The picker must therefore link the provider's terms rather than summarise them — a summary in
 * this file is a claim this project would be making on the player's behalf.
 */
public enum StockProvider {

    /**
     * Finnhub — the most generous per-minute allowance of the three, and the default.
     *
     * <p>Checked 2026-08-04: 60 calls/minute on the free tier with real-time US quotes. ⚠ One
     * third-party report also describes a daily cap of roughly 300 calls that the pricing page does
     * not mention; treat the per-minute figure as the documented one and the daily as unverified.
     */
    FINNHUB(
            "Finnhub",
            "https://finnhub.io/api/v1/quote?symbol=%s&token=%s",
            "https://finnhub.io/api/v1/search?q=%s&token=%s",
            "https://finnhub.io/dashboard",
            "60 calls/minute, real-time US quotes"),

    /**
     * Twelve Data — a middle option with a clear daily budget.
     *
     * <p>Checked 2026-08-04: 800 API credits/day resetting at 00:00 UTC and 8 calls/minute on the
     * free Basic plan, covering real-time US equities. Most quote endpoints cost one credit.
     */
    TWELVE_DATA(
            "Twelve Data",
            "https://api.twelvedata.com/quote?symbol=%s&apikey=%s",
            "https://api.twelvedata.com/symbol_search?symbol=%s&apikey=%s",
            "https://twelvedata.com/pricing",
            "800 credits/day, 8 calls/minute"),

    /**
     * Alpha Vantage — included for completeness, and ⚠ <b>too small for a live panel</b>.
     *
     * <p>Checked 2026-08-04: <b>25 requests per day</b> and 5 per minute on the free tier. That is a
     * few refreshes of a single symbol, so the panel will spend most of its time on the offline
     * feed. Offered anyway because a player may already hold a key, and because being told why a
     * provider is a poor fit is more useful than not being offered it.
     */
    ALPHA_VANTAGE(
            "Alpha Vantage",
            "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
            "https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=%s&apikey=%s",
            "https://www.alphavantage.co/support/",
            "25 requests/DAY — too small for live updates");

    private final String label;
    private final String quoteUrl;
    private final String searchUrl;
    private final String signupUrl;
    private final String limits;

    StockProvider(String label, String quoteUrl, String searchUrl, String signupUrl, String limits) {
        this.label = label;
        this.quoteUrl = quoteUrl;
        this.searchUrl = searchUrl;
        this.signupUrl = signupUrl;
        this.limits = limits;
    }

    public String label() {
        return label;
    }

    /** ⚠ Contains the key. Never log the result of this. */
    public String quoteUrl(String symbol, String apiKey) {
        return String.format(quoteUrl, symbol, apiKey);
    }

    /**
     * Symbol lookup, so the universe grows past the bundled set.
     *
     * <h2>⚠ A SECOND CALL against the same allowance</h2>
     *
     * Every provider meters searches out of the same budget as quotes. That is why a lookup only
     * fires when a query matches nothing already known and looks like a ticker — a keystroke-by-
     * keystroke search would spend a free tier's whole day in one session of typing.
     *
     * <p>⚠ Contains the key. Never log the result.
     */
    public String searchUrl(String query, String apiKey) {
        return String.format(searchUrl, java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8), apiKey);
    }

    /** Where a player goes to get a key and read the terms they are agreeing to. */
    public String signupUrl() {
        return signupUrl;
    }

    /** What was documented on 2026-08-04. Shown with that date attached. */
    public String limits() {
        return limits;
    }

    /** ⚠ The date the figures above were checked, shown beside them so nobody reads them as current. */
    public static final String LIMITS_CHECKED = "2026-08-04";

    /** The one to offer first. */
    public static StockProvider preferred() {
        return FINNHUB;
    }

    /** Tolerant parse — this arrives from a settings file a player can edit. */
    public static StockProvider parse(String name) {
        if (name == null) {
            return preferred();
        }
        for (StockProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(name.trim())) {
                return provider;
            }
        }
        return preferred();
    }
}
