package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * An amount of ethecoin (EC) — the in-game currency ({@code docs/design/01-core-resources.md} §2).
 *
 * <h2>Why this is a type and not a {@code long}</h2>
 *
 * Invariant I1 says compute is never purchasable with ethecoin, and that invariant is what stops the
 * economy collapsing into a compounding flywheel (mine EC → buy cycles → mine more). If ethecoin and
 * {@link Cycles} were both bare {@code long}s, performing the one conversion the entire design
 * forbids would be a <em>one-character mistake</em> that compiles, passes review, and is discovered
 * in playtest six weeks later.
 *
 * <p>So: two unrelated types, no common supertype, no conversion method in either direction, and no
 * arithmetic that accepts the other. Java cannot make the conversion impossible — anyone can write
 * {@code Cycles.of(amount.minorUnits())} — but it can make it a deliberate, visible, greppable act
 * instead of a typo. That is the whole ambition here.
 *
 * <h2>Why integral minor units and not a decimal or a double</h2>
 *
 * Binary floating point cannot represent 0.1, so two servers summing the same ledger in a different
 * order would disagree about a balance. In a federation, a disagreement about a balance is
 * indistinguishable from cheating ({@code docs/architecture/05-validator-quorum.md}). Money on a wire
 * is therefore an integral count of the smallest representable unit — the satoshi model — and
 * rounding happens once, on the server, where the rules live.
 *
 * <p><strong>The scale is Ethereum's: 18 decimal places (decided 2026-07-30).</strong> The smallest
 * representable amount is {@code 1e-18} EC, and this type counts those — the same relationship wei has
 * to ether. It replaces an earlier {@code [PROPOSAL]} of two decimal places, which was chosen because
 * two was the finest granularity any published figure used; what that missed is that a currency
 * players <em>send each other</em> needs room for amounts nobody published, and dust is a real thing
 * to send.
 *
 * <p>⚠ <b>This is why the carrier is a {@link BigInteger} and not a {@code long}.</b> At 18 decimals a
 * {@code long} tops out at <b>9.22 EC</b> — less than a single firmware image costs — so the obvious
 * representation is not merely tight, it is unusable. Measured before the change, not after.
 *
 * <p>Note what a scale is not: a representation decision, never a balance value. No price, rate or
 * yield lives in this module.
 *
 * <h2>Why amounts are never negative</h2>
 *
 * The public ledger ({@code docs/design/01-core-resources.md} §2.2) records a <em>direction</em>
 * (from/to) and a <em>magnitude</em>. A sign would encode direction a second time, in a second place,
 * and the two would eventually disagree. An overdrawn purchase is a server-side rejection, not a
 * negative balance travelling over the wire.
 *
 * @param wei the amount in units of {@code 1e-18} EC; never negative
 */
public record Ethecoin(BigInteger wei) implements Comparable<Ethecoin> {

    /** Decimal places. Ethereum's, and the reason the carrier is a {@link BigInteger}. */
    public static final int DECIMALS = 18;

    /** The smallest units in one whole ethecoin: {@code 10^18}. A scale, not a balance value. */
    public static final BigInteger WEI_PER_ETHECOIN = BigInteger.TEN.pow(DECIMALS);

    /** The empty wallet. */
    public static final Ethecoin ZERO = new Ethecoin(BigInteger.ZERO);

    public Ethecoin {
        Objects.requireNonNull(wei, "wei");
        if (wei.signum() < 0) {
            throw new IllegalArgumentException("Ethecoin amounts are never negative, was " + wei);
        }
    }

    /**
     * An amount given directly in minor units — the wire form.
     *
     * @param minorUnits hundredths of an ethecoin; must not be negative
     * @return the amount
     */
    public static Ethecoin ofWei(BigInteger wei) {
        return new Ethecoin(wei);
    }

    /** The same, from a {@code long} count of wei. */
    public static Ethecoin ofWei(long wei) {
        return new Ethecoin(BigInteger.valueOf(wei));
    }

    /**
     * An amount written the way a person writes one: {@code "0.037097927036961408"}.
     *
     * <p>⚠ Parsed through {@link BigDecimal}, never {@code double}. Binary floating point cannot
     * represent {@code 0.1}, and this type exists partly because two servers summing the same ledger
     * in a different order must not disagree about a balance.
     *
     * @throws ArithmeticException if the amount is finer than {@link #DECIMALS} — silently dropping
     *     the tail would be the rounding this whole representation exists to avoid
     */
    public static Ethecoin ofDecimal(String amount) {
        return new Ethecoin(new BigDecimal(amount).movePointRight(DECIMALS).toBigIntegerExact());
    }

    /**
     * An amount given in whole ethecoin. Spelled out rather than overloading {@code of(...)}, because
     * a money factory whose unit you have to remember is a money factory someone will get wrong.
     *
     * @param ethecoin whole ethecoin; must not be negative
     * @return the amount
     * @throws ArithmeticException if the amount does not fit in a {@code long} of minor units
     */
    public static Ethecoin ofWholeEthecoin(long ethecoin) {
        return new Ethecoin(BigInteger.valueOf(ethecoin).multiply(WEI_PER_ETHECOIN));
    }

    /**
     * This amount plus {@code other}.
     *
     * @param other the amount to add
     * @return the sum
     * @throws ArithmeticException on overflow — a silently wrapped balance is worse than a failed
     *     request, because it looks like a legitimate number to every layer above it
     */
    public Ethecoin plus(Ethecoin other) {
        Objects.requireNonNull(other, "other");
        return new Ethecoin(wei.add(other.wei));
    }

    /**
     * This amount minus {@code other}.
     *
     * @param other the amount to subtract
     * @return the difference
     * @throws IllegalArgumentException if the result would be negative; balances do not go negative,
     *     and "can they afford it" is a server-side question asked before the subtraction, not a
     *     property discovered from its sign
     */
    public Ethecoin minus(Ethecoin other) {
        Objects.requireNonNull(other, "other");
        return new Ethecoin(wei.subtract(other.wei));
    }

    /** Whether this amount is zero. */
    public boolean isZero() {
        return wei.signum() == 0;
    }

    /**
     * Orders by amount. Typed to {@code Ethecoin} specifically, so no sort or {@code max} can ever
     * line an amount of money up against an amount of compute.
     */
    @Override
    public int compareTo(Ethecoin other) {
        return wei.compareTo(other.wei);
    }

    /**
     * The canonical form: {@code 4.8 EC}, {@code 0.037097927036961408 EC}, {@code 500 EC}.
     *
     * <h2>⚠ This REVERSES a documented decision, and the evidence is why</h2>
     *
     * This class used to carry a note saying display formatting was deliberately absent — that a
     * formatted amount is a localization decision belonging to the client, and that the record's
     * generated {@code toString} is unambiguous, which is what logs and test failures need. Both
     * halves of that are true and the conclusion was still wrong, because it ignored what a record's
     * generated {@code toString} actually is: <b>the thing you get by accident</b>. Any {@code "..." +
     * amount} compiles, renders without complaint, and printed the internal at the player on
     * <b>five</b> surfaces before anyone noticed.
     *
     * <p>The localization argument survives untouched: a <em>localized</em> amount — grouped
     * separators, a symbol in the reader's position — is a different method and still belongs to the
     * client. What was missing was a safe <em>canonical</em> default. ⚠ {@code Locale.ROOT} for
     * exactly that reason: this is the invariant machine form, not a presentation choice.
     */
    @Override
    public String toString() {
        return format(wei);
    }

    /**
     * Formats a <b>signed</b> amount of wei, trimming trailing zeros.
     *
     * <h2>⚠ Why this takes a BigInteger rather than an Ethecoin</h2>
     *
     * An {@code Ethecoin} is never negative by construction, and rightly so: a balance does not go
     * below zero, and "can they afford it" is asked before the subtraction rather than discovered
     * from a sign. But a ledger <em>delta</em> is signed — a debit is a negative number — and it
     * still has to be rendered. This is the seam: the value type stays non-negative, and the
     * formatter accepts the signed quantity the ledger actually holds.
     *
     * <h2>⚠ Only significant decimals are shown, and that is the whole point at 18 places</h2>
     *
     * A fixed {@code %.18f} would render every ordinary amount as {@code 8.000000000000000000 EC} —
     * eighteen characters of noise on every row of the ledger, burying the two amounts on the screen
     * that actually have a tail. So the fraction is trimmed of trailing zeros and the point is
     * dropped entirely when nothing is left:
     *
     * <pre>
     *   500 EC                     a whole amount reads as one
     *   0.05 EC                    not 0.050000000000000000
     *   0.037097927036961408 EC    exact, all 18 places, nothing rounded away
     *   -0.06 EC                   a debit keeps its sign
     * </pre>
     *
     * <p>⚠ <b>Trimming is never rounding.</b> Every significant digit is printed however many there
     * are; what is dropped is only zeros that carry no information. An amount is never shortened to
     * fit, because a balance that renders differently from what it is would be the one bug this
     * representation exists to prevent.
     *
     * <h2>⚠ The sign bug this replaced, which had shipped</h2>
     *
     * There were <b>thirteen</b> private copies of a formatter across {@code solo} and {@code client},
     * twelve of them written as {@code String.format("%d.%02d EC", m / 100, Math.abs(m % 100))}.
     * Integer division truncates <b>toward zero</b>, so for any amount between −1 and −99 minor units
     * the whole part was {@code 0} — and {@code -0} is {@code 0}. The minus sign vanished, and since
     * every transaction fee in the game was 2, 6 or 30 minor units, <b>every fee row in the ledger
     * displayed as a credit</b>. The sign is therefore taken from the value before anything else.
     *
     * @param wei signed; may be negative for a debit
     */
    public static String format(BigInteger wei) {
        String sign = wei.signum() < 0 ? "-" : "";
        BigDecimal magnitude = new BigDecimal(wei.abs()).movePointLeft(DECIMALS);
        // ⚠ stripTrailingZeros can leave a NEGATIVE scale — 500 becomes 5E+2 — and toString would
        // then print scientific notation at the player. toPlainString is the guard, and max(0)
        // keeps a whole amount from rendering as "5E+2" even before it gets there.
        BigDecimal trimmed = magnitude.stripTrailingZeros();
        if (trimmed.scale() < 0) {
            trimmed = trimmed.setScale(0);
        }
        return sign + trimmed.toPlainString() + " EC";
    }

    /**
     * The same, capped at {@code decimals} places — for a DERIVED figure, never for a held amount.
     *
     * <h2>⚠ This ROUNDS, which {@link #format(BigInteger)} must never do</h2>
     *
     * The plain formatter prints every significant digit because a balance, a ledger row or a fee is
     * an exact quantity and a readout that disagreed with it would be the one bug this whole
     * representation exists to prevent. That rule is not relaxed here: this is a <b>different
     * method</b>, and the difference is the point.
     *
     * <p>What it is for is the other kind of number — a rate, an estimate, a projection. Those are
     * computed through a {@code double} somewhere (the network hashrate is one, and always has been),
     * so their low digits are arithmetic residue rather than information. Printed in full they read
     * as {@code 39.99999999999999802 EC/hr}, which is not a more precise answer than {@code 40} — it
     * is the same answer wearing seventeen digits of noise, on a readout a player is trying to
     * compare against a published figure.
     *
     * <h2>⚠ Where this may be used</h2>
     *
     * Where the number is already an approximation and is <em>labelled</em> as one — the rig
     * monitor's {@code ~40 EC/hr}, a projected payout, an expected yield. ⚠ Never on a ledger delta,
     * a fee actually charged, or a resale price: those are amounts somebody holds, and a rounded
     * rendering of one is a lie the player cannot detect.
     *
     * <h2>⚠ The one exception, and what earns it (amended 2026-07-30)</h2>
     *
     * The rule above said "never a balance" outright. The top strip is now an exception: at eighteen
     * places a real balance renders as {@code 1234.905777539252303541 EC} and pushes every other cell
     * off the strip, and an unreadable exact figure is not more honest than a readable abbreviated
     * one.
     *
     * <p>What makes it legitimate is <b>not</b> that the strip is short of room — it is that the
     * exact amount is still reachable. {@code BalanceReadout} carries the full figure in a tooltip
     * and in its accessible text, and the LEDGER shows every amount exactly. So the rule is sharper
     * than it was rather than weaker: <b>a held amount may be abbreviated only where the exact figure
     * is one hover away.</b> Abbreviating one with no route back to the real number is still
     * forbidden, and that is the case the original wording was reaching for.
     *
     * <p>Trailing zeros are still trimmed after the cap, so {@code 40.0000} renders as {@code 40}.
     *
     * @param wei signed; may be negative
     * @param decimals how many places to keep, at most
     */
    public static String formatApprox(BigInteger wei, int decimals) {
        String sign = wei.signum() < 0 ? "-" : "";
        BigDecimal magnitude = new BigDecimal(wei.abs())
                .movePointLeft(DECIMALS)
                .setScale(Math.max(0, decimals), java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (magnitude.scale() < 0) {
            magnitude = magnitude.setScale(0);
        }
        return sign + magnitude.toPlainString() + " EC";
    }

    /** The same, for a signed amount that already fits in a {@code long} of wei. */
    public static String format(long wei) {
        return format(BigInteger.valueOf(wei));
    }
}
