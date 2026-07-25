package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * A quantity of compute, measured in cycles — the master scarcity ({@code
 * docs/design/01-core-resources.md} §1).
 *
 * <h2>Capacity, not currency</h2>
 *
 * Cycles are not spent and gone; they are <em>allocated</em>, and most allocations are reservations
 * that persist while the thing they power runs. That distinction is why this type carries no notion
 * of a balance being "paid" — a {@link ComputeBudget} is a snapshot of where a rig's capacity
 * currently sits, not a wallet.
 *
 * <h2>Why this is a type and not a {@code long}</h2>
 *
 * See {@link Ethecoin} for the full argument. In short: Invariant I1 forbids buying compute with
 * ethecoin, and two bare {@code long}s make that forbidden conversion a typo rather than a decision.
 * There is deliberately no conversion, no shared supertype and no cross-type arithmetic. Rig capacity
 * expands only through schematics and story milestones ({@code docs/design/11-rig-infrastructure.md}),
 * never through a number produced by this class.
 *
 * <h2>Whole cycles only</h2>
 *
 * Nothing in the design ever divides a cycle: a starting rig is a whole number, a control channel
 * reservation is a whole number, a scan's cost is a whole number. Rates <em>per</em> cycle-hour are
 * fractional, but those are yields and they live on the server. Modelling cycles as integral keeps
 * the reconciliation in {@code ComputeBudget} exact, which matters because an inexact reconciliation
 * would produce phantom "unaccounted-for" cycles and turn the manual-audit loop ({@code
 * docs/design/04-mining.md} §3.1) into noise.
 *
 * @param cycles the number of cycles; never negative
 */
public record Cycles(long cycles) implements Comparable<Cycles> {

    /** No compute at all. */
    public static final Cycles ZERO = new Cycles(0L);

    public Cycles {
        if (cycles < 0) {
            throw new IllegalArgumentException("Cycles are never negative, was " + cycles);
        }
    }

    /**
     * A quantity of cycles.
     *
     * @param cycles whole cycles; must not be negative
     * @return the quantity
     */
    public static Cycles of(long cycles) {
        return new Cycles(cycles);
    }

    /**
     * This quantity plus {@code other}.
     *
     * @param other the quantity to add
     * @return the sum
     * @throws ArithmeticException on overflow
     */
    public Cycles plus(Cycles other) {
        Objects.requireNonNull(other, "other");
        return new Cycles(Math.addExact(cycles, other.cycles));
    }

    /**
     * This quantity minus {@code other}.
     *
     * @param other the quantity to subtract
     * @return the difference
     * @throws IllegalArgumentException if the result would be negative — a rig cannot have less than
     *     no capacity, and a negative here means the caller has mixed up which rig is being charged
     *     (Invariant I6 makes that a real and easy mistake)
     */
    public Cycles minus(Cycles other) {
        Objects.requireNonNull(other, "other");
        return new Cycles(Math.subtractExact(cycles, other.cycles));
    }

    /** Whether this quantity is zero. */
    public boolean isZero() {
        return cycles == 0L;
    }

    /** Orders by quantity. Typed to {@code Cycles} specifically — compute never sorts against money. */
    @Override
    public int compareTo(Cycles other) {
        return Long.compare(cycles, other.cycles);
    }
}
