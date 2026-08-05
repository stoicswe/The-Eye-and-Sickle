package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Where a price comes from.
 *
 * <h2>⚠ A PORT, because the client must work with no network</h2>
 *
 * "Runs offline out of the box" is a standing promise of this client, so a feature that fetched
 * prices unconditionally would break it for every player who is not online — and there is no
 * sensible failure for a brokerage that cannot quote. So the price is behind a port with an offline
 * implementation as the default, and a real feed is something a player <b>opts into</b>.
 *
 * <h2>⚠ THE REAL FEED USES THE PLAYER'S OWN API KEY, and that is not laziness</h2>
 *
 * Every free quote service is keyed and rate-limited <em>per key</em>. A key shipped inside a
 * distributed desktop client is extractable from the jar in about a minute, shared by every player
 * at once, and revoked as soon as the provider notices — so the feature would work in testing and
 * die on release, which is the worst possible failure shape. A key the player pastes into Settings
 * is theirs, is rate-limited against their own usage, and puts nobody else's terms of service on
 * this project.
 *
 * <h2>⚠ Prices are EC at 1:1 with the dollar, and that is a GAME rule</h2>
 *
 * Not an exchange rate and not a claim about what ethecoin is worth. It exists so a player reading a
 * quote can reason about it with the number they already know.
 */
public interface StockFeed {

    /** One price, as of whenever the feed last knew. */
    record Quote(String symbol, BigInteger priceWei, BigInteger previousCloseWei, Instant asOf, boolean live) {

        /** Movement since the previous close, signed, in percent. */
        public double changePercent() {
            if (previousCloseWei == null || previousCloseWei.signum() <= 0) {
                return 0;
            }
            return priceWei.subtract(previousCloseWei).doubleValue() / previousCloseWei.doubleValue() * 100.0d;
        }
    }

    /**
     * @param symbol a real ticker
     * @param now the session clock
     * @return the quote, or empty when this feed cannot price it
     */
    Optional<Quote> quote(String symbol, Instant now);

    /**
     * What to call this feed on screen.
     *
     * <p>⚠ Shown to the player, always. A simulated price presented as a real one is the single most
     * misleading thing this tab could do — somebody could act on it believing it was the market.
     */
    String describe();

    /** Whether these are real prices. Drives the warning the panel must show when they are not. */
    boolean live();

    /**
     * When this symbol will next be asked for, so a panel can count down to it.
     *
     * <p>⚠ {@code Instant.EPOCH} means <b>not applicable</b>, which is the honest answer for a
     * derived feed: a simulated price is a continuous function of the clock and is never "refreshed",
     * so a countdown against it would be counting down to nothing. The panel renders no timer in that
     * case rather than an invented one.
     *
     * <p>⚠ It is the feed's answer and not the panel's, because only the feed knows which cadence a
     * symbol is on — held and watched symbols refresh at the player's interval and everything else
     * once a day.
     */
    default Instant nextRefreshAt(String symbol, Instant now) {
        return Instant.EPOCH;
    }
}
