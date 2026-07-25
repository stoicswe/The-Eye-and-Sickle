package io.github.stoicswe.eyeandsickle.server.persistence;

import java.util.Objects;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Checking what an {@code UPDATE} or {@code INSERT} actually did, and the concurrency rules that go
 * with it.
 *
 * <h2>Why an update that changed nothing must never be ignored</h2>
 *
 * {@code JdbcClient.update()} returns an affected-row count and nothing forces a caller to look at
 * it. On this schema, ignoring it is how a race becomes an exploit. Two requests that both read a
 * balance of 100, both check they can afford a 60 EC purchase, and both write 40 have produced one
 * item for free. The same shape applies to seizing a miner's yield buffer twice
 * ({@code docs/design/04-mining.md} §5.1) and to two allocations claiming the same cycles.
 *
 * <h2>The house pattern: version-checked updates</h2>
 *
 * Every mutable table in the schema carries {@value #ROW_VERSION}. A mutation reads the current
 * version, then writes conditionally on it:
 *
 * {@snippet lang = java:
 * int affected = jdbcClient
 *         .sql("""
 *              UPDATE players
 *                 SET ethecoin_balance_ec_minor = :balance,
 *                     row_version = row_version + 1
 *               WHERE player_id = :playerId
 *                 AND row_version = :expectedVersion
 *              """)
 *         .param("balance", EconomyColumns.ethecoinValue("ethecoin_balance_ec_minor", newBalance))
 *         .param("playerId", playerId)
 *         .param("expectedVersion", expectedVersion)
 *         .update();
 * Mutations.requireUpdated(affected, "players", playerId);
 *}
 *
 * A concurrent writer bumps the version, this update matches zero rows, and {@link
 * #requireUpdated(int, String, Object)} turns that into a retryable failure instead of a lost write.
 *
 * <h2>When a version check is not enough</h2>
 *
 * Optimistic concurrency detects a conflict on a row you already read. It does not help when the
 * decision depends on rows you did <em>not</em> read — most importantly the compute ledger, where
 * "may this allocation be made" is a question about the SUM of a rig's allocations, and a
 * concurrently inserted row is invisible to any version you hold.
 *
 * <p>For those, take a row lock on the parent first, inside the transaction:
 *
 * {@snippet lang = java:
 * // Serialises every allocation decision for this rig against every other one.
 * jdbcClient.sql("SELECT rig_id FROM rigs WHERE rig_id = :rigId FOR UPDATE")
 *         .param("rigId", rigId)
 *         .query()
 *         .singleValue();
 *}
 *
 * Lock the rig, not the allocations: the rows you need to exclude are the ones that do not exist yet,
 * and only the parent row is there to lock. Take locks in a consistent order — for a cross-rig
 * operation (Invariant I6 makes those routine), order by rig id, or two players deploying onto each
 * other deadlock.
 *
 * <h2>Ledger writes are transactional, always</h2>
 *
 * A {@code ledger_transactions} row and the balance change it describes are written in ONE
 * transaction, or the ledger stops being evidence and starts being a rumour. The ledger table is
 * append-only at the database level (a trigger refuses UPDATE and DELETE), so a half-written transfer
 * cannot be tidied up afterwards — which is the point. Annotate the service method
 * {@code @Transactional}; do not rely on the auto-commit behaviour of a single {@code JdbcClient}
 * call.
 */
public final class Mutations {

    /** The optimistic-concurrency column present on every mutable table in the schema. */
    public static final String ROW_VERSION = "row_version";

    private Mutations() {}

    /**
     * Asserts that a version-checked update matched exactly one row.
     *
     * @param affectedRows what {@code JdbcClient.update()} returned
     * @param table the table, for the failure message
     * @param id the row's identifier, for the failure message
     * @throws OptimisticLockingFailureException if no row matched — either the row is gone or another
     *     writer bumped its version. Both are retryable by re-reading and re-deciding; neither is
     *     safe to ignore.
     * @throws IncorrectResultSizeDataAccessException if more than one row matched, which means the
     *     WHERE clause is not selecting by primary key and the update hit rows the caller never
     *     reasoned about
     */
    public static void requireUpdated(int affectedRows, String table, Object id) {
        Objects.requireNonNull(table, "table");
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows == 0) {
            throw new OptimisticLockingFailureException("No row updated in " + table + " for " + id
                    + ": it was deleted, or a concurrent writer advanced its " + ROW_VERSION
                    + ". Re-read and re-decide; do not retry the same write.");
        }
        throw new IncorrectResultSizeDataAccessException(
                "Update on " + table + " for " + id + " affected " + affectedRows
                        + " rows; a version-checked update must identify exactly one row",
                1,
                affectedRows);
    }

    /**
     * Asserts that an insert wrote exactly one row.
     *
     * @param affectedRows what {@code JdbcClient.update()} returned
     * @param table the table, for the failure message
     * @throws IncorrectResultSizeDataAccessException if the count is anything but one — most often an
     *     {@code ON CONFLICT DO NOTHING} that silently did nothing, which the caller has almost
     *     certainly interpreted as success
     */
    public static void requireInserted(int affectedRows, String table) {
        Objects.requireNonNull(table, "table");
        if (affectedRows != 1) {
            throw new IncorrectResultSizeDataAccessException(
                    "Insert into " + table + " wrote " + affectedRows
                            + " rows. If this is an ON CONFLICT DO NOTHING, handle the conflict explicitly"
                            + " rather than treating a no-op as a write.",
                    1,
                    affectedRows);
        }
    }

    /**
     * The version a row will carry after a successful version-checked update.
     *
     * <p>The SQL writes {@code row_version + 1}; this is the same arithmetic on the Java side, so a
     * record returned to the caller reports the version the database now holds rather than the stale
     * one it was read with.
     *
     * @param currentVersion the version the update was conditioned on
     * @return the next version
     * @throws IllegalArgumentException if the version is negative, which the schema forbids
     * @throws ArithmeticException on overflow
     */
    public static long nextRowVersion(long currentVersion) {
        if (currentVersion < 0) {
            throw new IllegalArgumentException(ROW_VERSION + " is never negative, was " + currentVersion);
        }
        return Math.addExact(currentVersion, 1L);
    }
}
