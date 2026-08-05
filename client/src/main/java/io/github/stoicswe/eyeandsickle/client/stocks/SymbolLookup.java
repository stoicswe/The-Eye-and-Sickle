package io.github.stoicswe.eyeandsickle.client.stocks;

import io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider;
import io.github.stoicswe.eyeandsickle.engine.stocks.Tickers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Grows the tradeable universe by asking the provider what a ticker is.
 *
 * <h2>Why this exists</h2>
 *
 * The bundled set is a browsable starting point, not an attempt at the whole market. A player who
 * types a symbol that is not among them should get it rather than "nothing matches that" — and once
 * it has been looked up it is <b>kept</b>, so the universe a character can reach grows with use.
 *
 * <h2>⚠ A LOOKUP IS A CALL, and a free tier is a few hundred a day</h2>
 *
 * So it fires only when all of these hold: there is a key, the query matches nothing already known,
 * and it <em>looks like a ticker</em>. A search that fired on every keystroke would spend the whole
 * day's allowance on the way to typing four letters. ⚠ It also remembers what it has already asked
 * — including the misses — because asking twice for a symbol that does not exist is the same cost as
 * asking for one that does.
 *
 * <h2>⚠ Never blocks the FX thread</h2>
 *
 * Same rule as {@link HttpStockFeed}: the lookup runs on a virtual thread and the caller is told
 * afterwards. A synchronous request in a text listener would freeze the client for the length of
 * somebody else's round trip, on every keystroke.
 */
public final class SymbolLookup {

    private static final Logger LOG = Logger.getLogger(SymbolLookup.class.getName());

    /** How long to stay quiet after a refusal. */
    private static final Duration BACKOFF = Duration.ofMinutes(5);

    /** The most symbols one query may add. A lookup for "A" would otherwise return the alphabet. */
    private static final int MAX_PER_QUERY = 8;

    private final StockProvider provider;
    private final String apiKey;
    private final HttpClient http;
    /** ⚠ Includes misses. A symbol that does not exist costs exactly as much to ask about twice. */
    private final Set<String> asked = ConcurrentHashMap.newKeySet();

    private volatile Instant quietUntil = Instant.EPOCH;

    public SymbolLookup(StockProvider provider, String apiKey) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                // ⚠ The URL carries the key; a redirect would hand it to whatever host answered.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Whether this looks like something worth spending a call on. */
    public static boolean looksLikeATicker(String query) {
        if (query == null) {
            return false;
        }
        String trimmed = query.trim();
        // Real US tickers are one to five letters, sometimes with a class suffix. Anything longer is
        // a company name, which the local search already handles against what is known.
        return trimmed.length() >= 1
                && trimmed.length() <= 6
                && trimmed.chars().allMatch(c -> Character.isLetter(c) || c == '.' || c == '-');
    }

    /**
     * Looks a query up, if it is worth doing, and registers whatever comes back.
     *
     * @param onFound called on a background thread when the universe grew — the caller must hop to
     *     the FX thread itself, which is deliberately not done here so this class stays toolkit-free
     */
    public void discover(String query, Consumer<Integer> onFound) {
        if (apiKey == null || apiKey.isBlank() || !looksLikeATicker(query)) {
            return;
        }
        String key = query.trim().toUpperCase(Locale.ROOT);
        if (Instant.now().isBefore(quietUntil) || !asked.add(key)) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(provider.searchUrl(key, apiKey)))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429) {
                    quietUntil = Instant.now().plus(BACKOFF);
                    LOG.log(Level.INFO, "symbol lookup rate-limited; quiet for {0}", BACKOFF);
                    return;
                }
                if (response.statusCode() != 200) {
                    // ⚠ The status and the QUERY, never the URL — the URL carries the key.
                    LOG.log(Level.FINE, "symbol lookup {0} for {1}", new Object[] {response.statusCode(), key});
                    return;
                }
                int found = register(response.body());
                if (found > 0) {
                    onFound.accept(found);
                }
            } catch (Exception offline) {
                // The player is not online, or the service is down. The local universe already
                // answered; a dialog here would interrupt a game to report somebody else's outage.
                LOG.log(Level.FINE, "symbol lookup unavailable for " + key, offline);
            }
        });
    }

    /**
     * Pulls symbol/name pairs out of whichever shape the provider returns.
     *
     * <p>⚠ Deliberately crude, and it must fail CLOSED. The three responses share no schema, and a
     * shape this does not recognise must add <b>nothing</b> rather than a wrong name — a symbol
     * registered under the wrong company would rename it for the rest of the character's life, and
     * the alias is derived from that name.
     */
    private int register(String body) {
        String symbolKey = switch (provider) {
            case FINNHUB, TWELVE_DATA -> "\"symbol\"";
            case ALPHA_VANTAGE -> "\"1. symbol\"";
        };
        String nameKey = switch (provider) {
            case FINNHUB -> "\"description\"";
            case TWELVE_DATA -> "\"instrument_name\"";
            case ALPHA_VANTAGE -> "\"2. name\"";
        };
        int added = 0;
        int at = 0;
        while (added < MAX_PER_QUERY) {
            int s = body.indexOf(symbolKey, at);
            if (s < 0) {
                break;
            }
            String symbol = text(body, s + symbolKey.length());
            int n = body.indexOf(nameKey, s);
            String name = n < 0 ? null : text(body, n + nameKey.length());
            at = s + symbolKey.length();
            if (symbol == null || name == null || symbol.isBlank() || name.isBlank()) {
                continue;
            }
            // ⚠ Letters only. Provider search returns futures, options and foreign listings whose
            // symbols carry punctuation this game has no prices for.
            if (!symbol.chars().allMatch(Character::isLetter) || symbol.length() > 5) {
                continue;
            }
            Tickers.register(symbol, name);
            added++;
        }
        return added;
    }

    private static String text(String body, int from) {
        int colon = body.indexOf(':', from);
        if (colon < 0) {
            return null;
        }
        int open = body.indexOf('"', colon);
        if (open < 0) {
            return null;
        }
        int close = body.indexOf('"', open + 1);
        return close < 0 ? null : body.substring(open + 1, close);
    }
}
