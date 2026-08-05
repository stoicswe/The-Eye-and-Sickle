package io.github.stoicswe.eyeandsickle.client.stocks;

import io.github.stoicswe.eyeandsickle.engine.stocks.StockFeed;
import io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider;
import io.github.stoicswe.eyeandsickle.engine.stocks.Tickers;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real quotes, from the player's own key.
 *
 * <h2>⚠ THIS LIVES IN THE CLIENT, not in the engine</h2>
 *
 * Network I/O belongs to the client. An engine that could fetch would also fetch on a home server,
 * which is a different question with different rate limits and a different party's terms — and
 * nobody has asked it. The engine holds the {@link StockFeed} port and an offline implementation;
 * this is the one thing that opens a socket.
 *
 * <h2>⚠ IT MUST NEVER BLOCK THE FX THREAD, and the cache is what guarantees that</h2>
 *
 * {@link #quote} is called from a repaint. A synchronous request there would freeze the whole client
 * for the length of somebody else's HTTP round trip — including when that round trip is a timeout.
 * So this answers <b>only from cache</b> and refreshes in the background: the first look at a symbol
 * returns the offline price, and the real one lands a moment later. That is also why a lagging feed
 * is fine for this feature, which the request explicitly allowed.
 *
 * <h2>⚠ RATE LIMITS ARE OBEYED BY BACKING OFF, never by counting</h2>
 *
 * The documented allowances are recorded on {@link StockProvider} for the picker, and deliberately
 * not enforced here. A hard-coded budget that drifted from the real one would either throttle a
 * player who had headroom or keep hammering a service that had already cut them off. What this does
 * instead is honour a 429 by going quiet for {@link #BACKOFF}, which is correct whatever the limit
 * turns out to be.
 *
 * <h2>⚠ The key is never logged</h2>
 *
 * It is in the URL, so the URL is never logged either — only the symbol and the status. A key in a
 * log file is a key in a bug report.
 */
public final class HttpStockFeed implements StockFeed {

    private static final Logger LOG = Logger.getLogger(HttpStockFeed.class.getName());

    /**
     * How long a COLD quote is served before a refresh is attempted.
     *
     * <h2>⚠ TWO CADENCES, and the split is what makes a free tier last the day</h2>
     *
     * The catalogue is a couple of hundred symbols and a free allowance is a few hundred calls a
     * day — and it only grows as the player discovers more. Refreshing every
     * symbol at the player's chosen rate would spend the entire day's budget in minutes on prices
     * nobody is watching — so only what they <b>hold or watch</b> gets the fast cadence, and
     * everything else is refreshed once a day.
     *
     * <p>That is also the honest split rather than merely the cheap one: the tracked symbols are the
     * only ones that are about the player's own money.
     */
    private static final Duration COLD = Duration.ofHours(24);

    /** How long to go quiet after a refusal. */
    private static final Duration BACKOFF = Duration.ofMinutes(5);

    private final StockProvider provider;
    private final String apiKey;
    private final StockFeed fallback;
    private final Duration hot;
    private final java.util.function.Supplier<java.util.Set<String>> tracked;
    private final HttpClient http;
    private final Map<String, Quote> cache = new HashMap<>();
    private volatile Instant quietUntil = Instant.EPOCH;

    /**
     * @param fallback what to answer with until a real quote arrives — the offline feed, so the
     *     panel is never blank and never waits
     */
    /**
     * @param hot how often a held or watched symbol is refreshed — the player's own setting
     * @param tracked ⚠ a SUPPLIER, asked at refresh time, not a set captured at construction. What a
     *     player holds and watches changes while the client runs, and a snapshot taken at startup
     *     would leave a symbol bought this session stuck on the daily cadence until a restart
     */
    public HttpStockFeed(
            StockProvider provider,
            String apiKey,
            StockFeed fallback,
            Duration hot,
            java.util.function.Supplier<java.util.Set<String>> tracked) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.fallback = fallback;
        this.hot = hot;
        this.tracked = tracked == null ? java.util.Set::<String>of : tracked;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                // ⚠ NEVER follow redirects. The URL carries the key, and a redirect would hand it to
                // whatever host the response named.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Optional<Quote> quote(String symbol, Instant now) {
        Optional<Tickers.Listing> listing = Tickers.bySymbol(symbol);
        if (listing.isEmpty()) {
            return Optional.empty();
        }
        String ticker = listing.get().symbol();
        Quote cached = cache.get(ticker);
        Duration freshness = tracked.get().contains(ticker) ? hot : COLD;
        if (cached == null || Duration.between(cached.asOf(), now).compareTo(freshness) > 0) {
            refresh(ticker, now);
        }
        Quote held = cache.get(ticker);
        // ⚠ Falls back rather than returning empty. An empty answer would make the panel say "no
        // price" every time a request was in flight, which is most of the time on first open.
        return held != null ? Optional.of(held) : fallback.quote(ticker, now);
    }

    /**
     * ⚠ Fire-and-forget on a virtual thread, so the caller returns immediately.
     *
     * <p>The result lands in the cache for the next repaint. A future that anybody waited on would
     * reintroduce exactly the block this exists to avoid.
     */
    private void refresh(String ticker, Instant now) {
        if (now.isBefore(quietUntil) || apiKey == null || apiKey.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(provider.quoteUrl(ticker, apiKey)))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429) {
                    quietUntil = Instant.now().plus(BACKOFF);
                    LOG.log(Level.INFO, "stock feed rate-limited; quiet for {0}", BACKOFF);
                    return;
                }
                if (response.statusCode() != 200) {
                    // ⚠ The status and the SYMBOL, never the URL — the URL carries the key.
                    LOG.log(Level.FINE, "stock feed {0} for {1}", new Object[] {response.statusCode(), ticker});
                    return;
                }
                parse(ticker, response.body()).ifPresent(quote -> cache.put(ticker, quote));
            } catch (Exception offline) {
                // ⚠ Swallowed to FINE, deliberately. The player is not online, or the service is
                // down; either way the offline feed already answered and there is nothing for them
                // to do. A dialog here would interrupt a game to report somebody else's outage.
                LOG.log(Level.FINE, "stock feed unavailable for " + ticker, offline);
            }
        });
    }

    /**
     * Pulls a price out of whichever shape the provider returns.
     *
     * <p>⚠ Deliberately crude: the three responses share no schema, and a JSON model per provider
     * would be three classes that exist to read two numbers. What matters is that a shape it does not
     * recognise yields <b>empty</b> rather than a wrong number — a mis-parsed price is worse than no
     * price, because the panel would show it as real.
     */
    private Optional<Quote> parse(String ticker, String body) {
        try {
            BigDecimal price;
            BigDecimal previous;
            switch (provider) {
                case FINNHUB -> {
                    price = number(body, "\"c\"");
                    previous = number(body, "\"pc\"");
                }
                case TWELVE_DATA -> {
                    price = number(body, "\"close\"");
                    previous = number(body, "\"previous_close\"");
                }
                case ALPHA_VANTAGE -> {
                    price = number(body, "\"05. price\"");
                    previous = number(body, "\"08. previous close\"");
                }
                default -> {
                    return Optional.empty();
                }
            }
            if (price == null || price.signum() <= 0) {
                return Optional.empty();
            }
            BigDecimal prior = previous == null || previous.signum() <= 0 ? price : previous;
            // $1 = 1 EC, a game rule and not an exchange rate.
            return Optional.of(new Quote(ticker, wei(price), wei(prior), Instant.now(), true));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static BigDecimal number(String body, String key) {
        int at = body.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = body.indexOf(':', at + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '"')) {
            i++;
        }
        int start = i;
        while (i < body.length() && (Character.isDigit(body.charAt(i)) || body.charAt(i) == '.'
                || body.charAt(i) == '-' || body.charAt(i) == 'e' || body.charAt(i) == 'E'
                || body.charAt(i) == '+')) {
            i++;
        }
        return start == i ? null : new BigDecimal(body.substring(start, i));
    }

    /** Dollars to wei at 1:1 with EC. */
    private static BigInteger wei(BigDecimal dollars) {
        return dollars.multiply(new BigDecimal(BigInteger.TEN.pow(18))).toBigInteger();
    }

    /**
     * ⚠ Derived from the CACHED quote plus this symbol's cadence, never from a stored deadline. A
     * countdown built on a second timer would drift from the thing it claims to be counting, and a
     * symbol that moved between the fast and daily tiers — because the player bought or watched it —
     * would keep counting to the old one.
     */
    @Override
    public Instant nextRefreshAt(String symbol, Instant now) {
        Quote cached = cache.get(symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT));
        if (cached == null) {
            // Nothing cached means a refresh is already in flight or about to be.
            return now;
        }
        Duration freshness = tracked.get().contains(cached.symbol()) ? hot : COLD;
        return cached.asOf().plus(freshness);
    }

    @Override
    public String describe() {
        return provider.label() + " — your key. Held and watched refresh every " + hot.toSeconds()
                + "s; everything else once a day.";
    }

    @Override
    public boolean live() {
        return true;
    }
}
