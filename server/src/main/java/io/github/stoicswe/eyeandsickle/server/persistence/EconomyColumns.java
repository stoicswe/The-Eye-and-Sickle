package io.github.stoicswe.eyeandsickle.server.persistence;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.util.Objects;

/**
 * Reads and writes the two quantities that must never convert into one another.
 *
 * <h2>Invariant I1, enforced at the persistence boundary</h2>
 *
 * "Compute is never purchasable with ethecoin." The protocol module keeps {@link Ethecoin} and {@link
 * Cycles} as separate types with no conversion, no shared supertype and no cross-type arithmetic —
 * but the moment either becomes a column, both are a {@code bigint} and the type system stops
 * helping. {@code row.int64("amount_ec_minor")} and {@code row.int64("allocated_cycles")} are the same
 * expression with a different string in it.
 *
 * <p>So the column NAME carries the type. An ethecoin column ends in {@value #ETHECOIN_SUFFIX}; a
 * cycles column ends in {@value #CYCLES_SUFFIX}. Asking this class for an ethecoin amount out of a
 * cycles column is refused immediately, by name, with the invariant quoted — which turns the one
 * conversion the whole economy is built to forbid from a plausible-looking line of code into a loud
 * failure on the first test that touches it.
 *
 * <p>This cannot be a compile-time check while SQL is a string. It is the strongest mechanical check
 * available at this layer, and it is cheap.
 *
 * <h2>Columns this covers today</h2>
 *
 * Ethecoin: {@code players.ethecoin_balance_ec_minor}, {@code ledger_transactions.amount_ec_minor},
 * {@code deployed_miners.buffer_ec_minor}. Cycles: {@code rigs.total_cycles},
 * {@code compute_allocations.allocated_cycles}, and the {@code rig_compute_reconciliation} view's
 * {@code active_cycles} / {@code recovering_cycles} / {@code available_cycles}.
 *
 * <p>New columns of either kind must follow the suffix. That rule is why {@code
 * docs/architecture/06-data-model.md} §2's {@code compute_cores} is spelled {@code total_cycles} in
 * the migration.
 *
 * <h2>What is deliberately absent</h2>
 *
 * No rates, no prices, no yields. 0.4 EC per cycle-hour ({@code docs/design/04-mining.md} §1) is a
 * balance value calibrated as a set with the rest of {@code docs/design/03-economy.md}; it belongs to
 * the mining system's configuration, not to a persistence helper that happens to touch both units.
 */
public final class EconomyColumns {

    /**
     * Required suffix for a column holding ethecoin, in integral hundredths (protocol {@link
     * Ethecoin}).
     */
    public static final String ETHECOIN_SUFFIX = "_ec_minor";

    /** Required suffix for a column holding compute, in whole cycles (protocol {@link Cycles}). */
    public static final String CYCLES_SUFFIX = "_cycles";

    private EconomyColumns() {}

    // ------------------------------------------------------------------ ethecoin

    /**
     * Reads an amount of ethecoin.
     *
     * @param row the row being mapped
     * @param column a column named {@code *}{@value #ETHECOIN_SUFFIX}
     * @return the amount
     * @throws IllegalArgumentException if the column is not named as an ethecoin column
     * @throws RowMappingException if the column is absent or NULL
     */
    public static Ethecoin ethecoin(Row row, String column) {
        Objects.requireNonNull(row, "row");
        requireEthecoinColumn(column);
        return Ethecoin.ofMinorUnits(row.int64(column));
    }

    /**
     * Reads an optional amount of ethecoin.
     *
     * @param row the row being mapped
     * @param column a column named {@code *}{@value #ETHECOIN_SUFFIX}
     * @return the amount, or {@code null} if the column is NULL
     * @throws IllegalArgumentException if the column is not named as an ethecoin column
     */
    public static Ethecoin ethecoinOrNull(Row row, String column) {
        Objects.requireNonNull(row, "row");
        requireEthecoinColumn(column);
        Long minorUnits = row.int64OrNull(column);
        return minorUnits == null ? null : Ethecoin.ofMinorUnits(minorUnits);
    }

    /**
     * The value to bind for an ethecoin column.
     *
     * <p>Takes the column name so the write side gets the same check as the read side. Binding
     * {@code amount.minorUnits()} directly would compile and would be wrong in exactly the way this
     * class exists to catch.
     *
     * @param column a column named {@code *}{@value #ETHECOIN_SUFFIX}
     * @param amount the amount
     * @return the integral hundredths to bind
     * @throws IllegalArgumentException if the column is not named as an ethecoin column
     */
    public static long ethecoinValue(String column, Ethecoin amount) {
        requireEthecoinColumn(column);
        Objects.requireNonNull(amount, "amount");
        return amount.minorUnits();
    }

    // ------------------------------------------------------------------ cycles

    /**
     * Reads a quantity of compute.
     *
     * @param row the row being mapped
     * @param column a column named {@code *}{@value #CYCLES_SUFFIX}
     * @return the quantity
     * @throws IllegalArgumentException if the column is not named as a cycles column
     * @throws RowMappingException if the column is absent or NULL
     */
    public static Cycles cycles(Row row, String column) {
        Objects.requireNonNull(row, "row");
        requireCyclesColumn(column);
        return Cycles.of(row.int64(column));
    }

    /**
     * The value to bind for a cycles column.
     *
     * @param column a column named {@code *}{@value #CYCLES_SUFFIX}
     * @param cycles the quantity
     * @return the whole cycles to bind
     * @throws IllegalArgumentException if the column is not named as a cycles column
     */
    public static long cyclesValue(String column, Cycles cycles) {
        requireCyclesColumn(column);
        Objects.requireNonNull(cycles, "cycles");
        return cycles.cycles();
    }

    /**
     * Reads a signed cycle figure — used only for {@code rig_compute_reconciliation.available_cycles},
     * which goes negative on an over-subscribed rig.
     *
     * <p>{@link Cycles} refuses negatives on purpose (a rig cannot have less than no capacity), so an
     * over-subscription cannot be expressed as one. The raw {@code long} keeps that situation
     * observable instead of throwing during a read, which is what an audit needs: a manual auditor's
     * whole job is to notice numbers that do not add up ({@code docs/design/04-mining.md} §3.1).
     *
     * @param row the row being mapped
     * @param column a column named {@code *}{@value #CYCLES_SUFFIX}
     * @return the figure, which may be negative
     * @throws IllegalArgumentException if the column is not named as a cycles column
     */
    public static long signedCycles(Row row, String column) {
        Objects.requireNonNull(row, "row");
        requireCyclesColumn(column);
        return row.int64(column);
    }

    // ------------------------------------------------------------------ naming discipline

    /**
     * Whether a column name declares itself to hold ethecoin.
     *
     * @param column the column label
     * @return {@code true} if it ends in {@value #ETHECOIN_SUFFIX}
     */
    public static boolean isEthecoinColumn(String column) {
        return column != null && column.endsWith(ETHECOIN_SUFFIX);
    }

    /**
     * Whether a column name declares itself to hold cycles.
     *
     * @param column the column label
     * @return {@code true} if it ends in {@value #CYCLES_SUFFIX}
     */
    public static boolean isCyclesColumn(String column) {
        return column != null && column.endsWith(CYCLES_SUFFIX);
    }

    private static void requireEthecoinColumn(String column) {
        Objects.requireNonNull(column, "column");
        if (!isEthecoinColumn(column)) {
            throw new IllegalArgumentException(refusal(column, "ethecoin", ETHECOIN_SUFFIX, CYCLES_SUFFIX));
        }
    }

    private static void requireCyclesColumn(String column) {
        Objects.requireNonNull(column, "column");
        if (!isCyclesColumn(column)) {
            throw new IllegalArgumentException(refusal(column, "cycles", CYCLES_SUFFIX, ETHECOIN_SUFFIX));
        }
    }

    private static String refusal(String column, String unit, String expectedSuffix, String otherSuffix) {
        String extra = column.endsWith(otherSuffix)
                ? " That column holds the OTHER quantity. Invariant I1: compute is never purchasable"
                        + " with ethecoin, and this is what that mistake looks like in code."
                : " Name new columns with the suffix so the unit is visible at every call site.";
        return "Column '" + column + "' is not an " + unit + " column; expected a name ending in '" + expectedSuffix
                + "'." + extra;
    }
}
