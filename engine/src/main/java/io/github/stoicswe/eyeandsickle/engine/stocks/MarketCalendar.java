package io.github.stoicswe.eyeandsickle.engine.stocks;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * When the US market is open, answered in whatever timezone the player is sitting in.
 *
 * <h2>⚠ THE SESSION IS NEW YORK'S; THE CLOCK ON SCREEN IS THE PLAYER'S</h2>
 *
 * The market opens at 09:30 America/New_York whoever is watching — that is a fact about the market,
 * not about the viewer. What changes is what that instant is <em>called</em>: 14:30 in London, 23:30
 * in Tokyo. So every decision here is made in New York and every figure handed out is an
 * {@link Instant}, which the client renders in the local zone. Storing "09:30" and comparing it to a
 * local wall clock would open the market at 09:30 everywhere, which is four different instants.
 *
 * <p>⚠ It also means the answer is identical in solo and multiplayer, because it does not depend on
 * anybody's server — it depends on a calendar.
 *
 * <h2>⚠ Holidays are a LIST, and a list goes stale</h2>
 *
 * The fixed-date and nth-weekday holidays are computable; Good Friday is not, because Easter moves.
 * The table below is computed where it can be and enumerated where it cannot, and the enumerated
 * part <b>runs out</b>. Past its horizon the calendar reports a normal weekday, which is the safe
 * direction — the market appearing open on a day it was shut is a cosmetic error, while the reverse
 * would lock a player out of a feature for no visible reason.
 */
public final class MarketCalendar {

    private MarketCalendar() {}

    /** Where the session's hours are defined. Not where the player is. */
    public static final ZoneId EXCHANGE = ZoneId.of("America/New_York");

    public static final LocalTime OPEN = LocalTime.of(9, 30);

    public static final LocalTime CLOSE = LocalTime.of(16, 0);

    /** Half-days close early — the day after Thanksgiving, Christmas Eve and July 3rd, broadly. */
    public static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);

    /**
     * Good Fridays, which cannot be computed without an Easter algorithm.
     *
     * <p>⚠ This list ends. See the class note: past the horizon the market simply reads as open on a
     * day it was shut, which is the harmless direction.
     */
    private static final Set<LocalDate> GOOD_FRIDAYS = Set.of(
            LocalDate.of(2026, 4, 3),
            LocalDate.of(2027, 3, 26),
            LocalDate.of(2028, 4, 14),
            LocalDate.of(2029, 3, 30),
            LocalDate.of(2030, 4, 19));

    /** What the market is doing at an instant. */
    public enum Phase {
        /** Trading. */
        OPEN,

        /** Before the bell on a trading day. */
        PRE,

        /** After the bell on a trading day. */
        POST,

        /** Weekend or holiday. */
        CLOSED
    }

    /**
     * The state of the market at an instant, with the next boundary.
     *
     * @param phase what it is doing
     * @param changesAt when that next changes — <b>an Instant</b>, so the client renders it in the
     *     player's own zone rather than in New York's
     */
    public record Session(Phase phase, Instant changesAt) {

        public boolean tradable() {
            return phase == Phase.OPEN;
        }
    }

    /**
     * @param now the instant
     * @return what the market is doing and when that changes
     */
    public static Session sessionAt(Instant now) {
        ZonedDateTime here = now.atZone(EXCHANGE);
        LocalDate day = here.toLocalDate();

        if (!isTradingDay(day)) {
            return new Session(Phase.CLOSED, atExchange(nextTradingDay(day), OPEN));
        }
        Instant open = atExchange(day, OPEN);
        Instant close = atExchange(day, closeFor(day));
        if (now.isBefore(open)) {
            return new Session(Phase.PRE, open);
        }
        if (now.isBefore(close)) {
            return new Session(Phase.OPEN, close);
        }
        return new Session(Phase.POST, atExchange(nextTradingDay(day), OPEN));
    }

    /** @return whether the exchange trades that calendar day at all. */
    public static boolean isTradingDay(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isHoliday(day);
    }

    /** @return the bell time for a day, which is early on a half-day. */
    public static LocalTime closeFor(LocalDate day) {
        return isHalfDay(day) ? EARLY_CLOSE : CLOSE;
    }

    private static Instant atExchange(LocalDate day, LocalTime time) {
        return ZonedDateTime.of(day, time, EXCHANGE).toInstant();
    }

    private static LocalDate nextTradingDay(LocalDate from) {
        LocalDate day = from.plusDays(1);
        // Bounded, so a corrupt calendar cannot spin forever.
        for (int i = 0; i < 12 && !isTradingDay(day); i++) {
            day = day.plusDays(1);
        }
        return day;
    }

    /**
     * ⚠ A holiday falling at the weekend is observed on the adjacent weekday, which is why these are
     * resolved through {@link #observed} rather than compared as raw dates. Independence Day on a
     * Saturday shuts the market on the Friday, and a calendar that missed it would have the market
     * open on a day nobody was trading.
     */
    private static boolean isHoliday(LocalDate day) {
        int year = day.getYear();
        return day.equals(observed(LocalDate.of(year, Month.JANUARY, 1)))
                || day.equals(nth(year, Month.JANUARY, DayOfWeek.MONDAY, 3))
                || day.equals(nth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3))
                || GOOD_FRIDAYS.contains(day)
                || day.equals(last(year, Month.MAY, DayOfWeek.MONDAY))
                || day.equals(observed(LocalDate.of(year, Month.JUNE, 19)))
                || day.equals(observed(LocalDate.of(year, Month.JULY, 4)))
                || day.equals(nth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                || day.equals(nth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                || day.equals(observed(LocalDate.of(year, Month.DECEMBER, 25)));
    }

    private static boolean isHalfDay(LocalDate day) {
        int year = day.getYear();
        LocalDate thanksgiving = nth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
        return day.equals(thanksgiving.plusDays(1))
                || day.equals(LocalDate.of(year, Month.DECEMBER, 24))
                || day.equals(LocalDate.of(year, Month.JULY, 3));
    }

    /** Saturday moves back to Friday, Sunday forward to Monday — the exchange's own rule. */
    private static LocalDate observed(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.plusDays(1);
        }
        return date;
    }

    private static LocalDate nth(int year, Month month, DayOfWeek weekday, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, weekday));
    }

    private static LocalDate last(int year, Month month, DayOfWeek weekday) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(weekday));
    }

    /**
     * The instant of the most recent closing bell at or before {@code now}.
     *
     * <h2>⚠ THE BELL, not "now minus a day"</h2>
     *
     * A feed that freezes out of hours has to freeze on <em>one</em> instant, and the only one that
     * means anything is the last trade of the last session. Stepping back a fixed 24 hours instead
     * gives Saturday morning and Saturday evening two different answers — so the "frozen" weekend
     * price drifts all weekend, which is precisely the behaviour freezing exists to prevent.
     *
     * @param now the instant
     * @return the last close, or {@code now} if none can be found within a fortnight
     */
    public static Instant previousClose(Instant now) {
        LocalDate day = now.atZone(EXCHANGE).toLocalDate();
        // Today counts only if the bell has already gone.
        if (isTradingDay(day)) {
            Instant close = atExchange(day, closeFor(day));
            if (!now.isBefore(close)) {
                return close;
            }
        }
        for (int i = 0; i < 14; i++) {
            day = day.minusDays(1);
            if (isTradingDay(day)) {
                return atExchange(day, closeFor(day));
            }
        }
        return now;
    }

    /** How long until the phase changes, never negative. */
    public static Duration until(Session session, Instant now) {
        Duration left = Duration.between(now, session.changesAt());
        return left.isNegative() ? Duration.ZERO : left;
    }
}
