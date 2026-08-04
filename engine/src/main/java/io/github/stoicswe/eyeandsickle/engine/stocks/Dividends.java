package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Being paid for holding.
 *
 * <h2>Quarterly, on a per-symbol schedule</h2>
 *
 * US dividend payers overwhelmingly pay four times a year, and not all in the same month — so the
 * quarter a symbol pays in is derived from the symbol. A market where every dividend landed on the
 * same day would make the whole mechanic one event a quarter rather than something that arrives
 * while you are doing other things.
 *
 * <h2>⚠ PAID ON THE PARCEL, using the SHARE COUNT AT PAYMENT</h2>
 *
 * Not on the position averaged across a symbol, and not on what was held when the quarter began.
 * Real dividends have a record date; modelling one would mean storing a second date per parcel and
 * explaining it, for a rule a player would only ever meet as "I bought yesterday and got nothing".
 * Paying on what is held when it lands is the simplification, and it is the one that cannot cheat
 * anybody out of money they can see they hold.
 *
 * <h2>⚠ It must be paid ONCE, and lastPaidQuarter is what guarantees that</h2>
 *
 * The tick runs every second and a quarter stays the current quarter for three months. Without a
 * marker, a holder would be paid once per second for a quarter of a year — which is not a bug that
 * degrades gracefully.
 */
public final class Dividends {

    private Dividends() {}

    /** Which quarter an instant falls in, counted from year zero so it never repeats. */
    public static long quarterOf(Instant at) {
        ZonedDateTime here = at.atZone(MarketCalendar.EXCHANGE);
        return here.getYear() * 4L + (here.getMonthValue() - 1) / 3;
    }

    /**
     * Whether this symbol pays in this quarter.
     *
     * <p>⚠ Derived from the symbol, so a company's payment months are stable for the life of the
     * character. A drawn schedule would move the dividend around and make "when does this pay" a
     * question with no answer.
     */
    public static boolean paysIn(String symbol, long quarter) {
        return Tickers.bySymbol(symbol).map(Tickers.Listing::paysDividend).orElse(false);
    }

    /**
     * What one share pays for one quarter, in wei.
     *
     * <p>⚠ Computed off the CURRENT price, so a dividend is a yield rather than a fixed sum. That is
     * how a yield works, and it means a holder whose stock has run up is paid more — which is the
     * behaviour a player will expect from the number they were shown.
     *
     * @param priceWei the share price at payment
     */
    public static BigInteger perShare(String symbol, BigInteger priceWei) {
        long yield = Tickers.bySymbol(symbol).map(Tickers.Listing::annualYieldBp).orElse(0L);
        if (yield <= 0 || priceWei == null || priceWei.signum() <= 0) {
            return BigInteger.ZERO;
        }
        // Annual basis points, quartered. Rounds DOWN — ⚠ the opposite of a fee, and deliberately:
        // rounding a payment up would create wei out of nothing on every dividend in the game.
        return priceWei.multiply(BigInteger.valueOf(yield)).divide(BigInteger.valueOf(10_000L * 4L));
    }

    /**
     * The date a quarter's dividend is treated as landing.
     *
     * <p>The 15th, or the next trading day — a payment date that fell at a weekend and was simply
     * skipped would drop a quarter's income four times a year for some holders and not others.
     */
    public static LocalDate payDate(long quarter) {
        int year = (int) (quarter / 4);
        int month = (int) (quarter % 4) * 3 + 1;
        LocalDate date = LocalDate.of(year, month, 15);
        for (int i = 0; i < 7 && !MarketCalendar.isTradingDay(date); i++) {
            date = date.plusDays(1);
        }
        return date;
    }
}
