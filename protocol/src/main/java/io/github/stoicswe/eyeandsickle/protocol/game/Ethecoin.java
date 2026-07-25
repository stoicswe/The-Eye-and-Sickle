package io.github.stoicswe.eyeandsickle.protocol.game;

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
 * <p><strong>[PROPOSAL] — the scale.</strong> The design docs never state ethecoin's precision. Two
 * decimal places is the finest granularity any published figure uses (0.4 EC per cycle-hour, ~2.67 EC
 * per mining block — {@code docs/design/04-mining.md} §1.3), so {@link #MINOR_UNITS_PER_ETHECOIN} is
 * 100. If block payouts or fee splits ever need finer resolution this constant changes, and it must
 * change <em>before</em> any ledger rows exist. Note what it is not: a scale is a representation
 * decision, not a balance value — no price, rate or yield lives in this module.
 *
 * <h2>Why amounts are never negative</h2>
 *
 * The public ledger ({@code docs/design/01-core-resources.md} §2.2) records a <em>direction</em>
 * (from/to) and a <em>magnitude</em>. A sign would encode direction a second time, in a second place,
 * and the two would eventually disagree. An overdrawn purchase is a server-side rejection, not a
 * negative balance travelling over the wire.
 *
 * @param minorUnits the amount in hundredths of an ethecoin; never negative
 */
public record Ethecoin(long minorUnits) implements Comparable<Ethecoin> {

    /**
     * Minor units in one whole ethecoin. See the {@code [PROPOSAL]} note on the class: this is a
     * precision decision, not a balance value.
     */
    public static final long MINOR_UNITS_PER_ETHECOIN = 100L;

    /** The empty wallet. */
    public static final Ethecoin ZERO = new Ethecoin(0L);

    public Ethecoin {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Ethecoin amounts are never negative, was " + minorUnits);
        }
    }

    /**
     * An amount given directly in minor units — the wire form.
     *
     * @param minorUnits hundredths of an ethecoin; must not be negative
     * @return the amount
     */
    public static Ethecoin ofMinorUnits(long minorUnits) {
        return new Ethecoin(minorUnits);
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
        return new Ethecoin(Math.multiplyExact(ethecoin, MINOR_UNITS_PER_ETHECOIN));
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
        return new Ethecoin(Math.addExact(minorUnits, other.minorUnits));
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
        return new Ethecoin(Math.subtractExact(minorUnits, other.minorUnits));
    }

    /** Whether this amount is zero. */
    public boolean isZero() {
        return minorUnits == 0L;
    }

    /**
     * Orders by amount. Typed to {@code Ethecoin} specifically, so no sort or {@code max} can ever
     * line an amount of money up against an amount of compute.
     */
    @Override
    public int compareTo(Ethecoin other) {
        return Long.compare(minorUnits, other.minorUnits);
    }

    // Deliberately no display formatting. "25.00 EC" is a localization decision (separator, symbol
    // placement, abbreviation of large amounts) and localization belongs to the client. The record's
    // generated toString is unambiguous, which is what logs and test failures actually need.
}
