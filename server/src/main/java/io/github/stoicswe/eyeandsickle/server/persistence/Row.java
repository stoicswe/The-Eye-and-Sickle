package io.github.stoicswe.eyeandsickle.server.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of a result set, with typed accessors that fail loudly instead of quietly.
 *
 * <h2>Why this exists at all</h2>
 *
 * The alternative is Spring's {@code BeanPropertyRowMapper} / {@code DataClassRowMapper}, which match
 * columns to constructor parameters by reflection. Two things make that a bad fit for an authoritative
 * server:
 *
 * <ul>
 *   <li>A reflective mapper that cannot find a column can be configured to leave the field at its
 *       default. On this schema, "left at its default" means a balance of zero, a heat of zero, or an
 *       allocation of zero cycles — values that look entirely plausible to every layer above.
 *   <li>Reflection maps {@code total_cycles} to {@code totalCycles} by convention, so a column rename
 *       is caught by nothing at all until a query runs in production.
 * </ul>
 *
 * Explicit mapping cannot make a column rename a compile error — SQL is a string either way — but it
 * makes the runtime failure immediate, specific, and attributable to a named mapper.
 *
 * <h2>Nullability is in the method name</h2>
 *
 * Every accessor comes in two flavours: {@code text(...)} requires a value and {@code textOrNull(...)}
 * permits one. That is not ceremony. A silent {@code null} out of a NOT NULL column means the query
 * selected something other than what the mapper thinks it selected, and the first place it would show
 * up otherwise is a {@code NullPointerException} several frames away with no column name in it.
 *
 * <h2>Timestamps</h2>
 *
 * Read as {@link OffsetDateTime} and converted, never as {@code java.sql.Timestamp}. {@code Timestamp}
 * is interpreted against the JVM's default time zone, so the same {@code timestamptz} column would
 * produce different instants on an operator's laptop and in their Docker container. Self-hosted
 * servers run in every zone there is; the schema uses {@code timestamptz} everywhere for this reason
 * and the mapper must not undo it.
 *
 * <p>Instances are created by {@link RowMappers} and live only for the duration of one
 * {@code mapRow} call. Never retain one: the underlying {@link ResultSet} is positioned on a row that
 * will have moved on.
 */
public final class Row {

    private final ResultSet resultSet;
    private final String description;

    Row(ResultSet resultSet, String description) {
        this.resultSet = resultSet;
        this.description = description;
    }

    // ------------------------------------------------------------------ text

    /**
     * A required text value.
     *
     * @param column the column label
     * @return the value, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public String text(String column) {
        return required(column, textOrNull(column));
    }

    /**
     * An optional text value.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public String textOrNull(String column) {
        return read(column, () -> resultSet.getString(column));
    }

    // ------------------------------------------------------------------ uuid

    /**
     * A required uuid value.
     *
     * @param column the column label
     * @return the value, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public UUID uuid(String column) {
        return required(column, uuidOrNull(column));
    }

    /**
     * An optional uuid value.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public UUID uuidOrNull(String column) {
        return read(column, () -> resultSet.getObject(column, UUID.class));
    }

    // ------------------------------------------------------------------ numbers

    /**
     * A required {@code integer} value.
     *
     * @param column the column label
     * @return the value
     * @throws RowMappingException if the column is absent or NULL
     */
    public int int32(String column) {
        Integer value = read(column, () -> {
            int raw = resultSet.getInt(column);
            // getInt returns 0 for SQL NULL, which is indistinguishable from a real zero and is
            // exactly the class of bug this whole type exists to prevent.
            return resultSet.wasNull() ? null : raw;
        });
        return required(column, value);
    }

    /**
     * A required {@code bigint} value.
     *
     * @param column the column label
     * @return the value
     * @throws RowMappingException if the column is absent or NULL
     */
    public long int64(String column) {
        Long value = read(column, () -> {
            long raw = resultSet.getLong(column);
            return resultSet.wasNull() ? null : raw;
        });
        return required(column, value);
    }

    /**
     * An optional {@code bigint} value.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public Long int64OrNull(String column) {
        return read(column, () -> {
            long raw = resultSet.getLong(column);
            return resultSet.wasNull() ? null : raw;
        });
    }

    /**
     * A required {@code boolean} value.
     *
     * @param column the column label
     * @return the value
     * @throws RowMappingException if the column is absent or NULL
     */
    public boolean bool(String column) {
        Boolean value = read(column, () -> {
            boolean raw = resultSet.getBoolean(column);
            return resultSet.wasNull() ? null : raw;
        });
        return required(column, value);
    }

    /**
     * A required {@code numeric} value.
     *
     * <p>{@link BigDecimal} and never {@code double}: heat and validator reputation are compared
     * against thresholds, and two servers that disagree about whether a threshold was cleared are
     * indistinguishable from one server cheating ({@code docs/architecture/05-validator-quorum.md}).
     *
     * @param column the column label
     * @return the value, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public BigDecimal decimal(String column) {
        return required(column, decimalOrNull(column));
    }

    /**
     * An optional {@code numeric} value.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    /**
     * An exact integer of unbounded width — what an ethecoin column holds.
     *
     * <h2>⚠ Read through BigDecimal, never through {@code getLong}</h2>
     *
     * Ethecoin is stored as {@code numeric(78,0)} because at 18 decimal places a {@code bigint} tops
     * out at 9.22 EC. {@code getLong} on such a column throws or silently truncates depending on the
     * driver and the value, and a truncated balance is the one failure this layer exists to prevent.
     *
     * <p>{@code toBigIntegerExact} rather than {@code toBigInteger}: the column is declared with
     * scale 0, so a fractional value there is a schema violation and must fail loudly rather than be
     * rounded into something plausible.
     */
    public java.math.BigInteger integer(String column) {
        return decimal(column).toBigIntegerExact();
    }

    /** The same, or {@code null} when the column is NULL. */
    public java.math.BigInteger integerOrNull(String column) {
        BigDecimal value = decimalOrNull(column);
        return value == null ? null : value.toBigIntegerExact();
    }

    public BigDecimal decimalOrNull(String column) {
        return read(column, () -> resultSet.getBigDecimal(column));
    }

    // ------------------------------------------------------------------ time

    /**
     * A required {@code timestamptz} value.
     *
     * @param column the column label
     * @return the instant, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public Instant instant(String column) {
        return required(column, instantOrNull(column));
    }

    /**
     * An optional {@code timestamptz} value.
     *
     * @param column the column label
     * @return the instant, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public Instant instantOrNull(String column) {
        OffsetDateTime value = read(column, () -> resultSet.getObject(column, OffsetDateTime.class));
        return value == null ? null : value.toInstant();
    }

    // ------------------------------------------------------------------ bytes and json

    /**
     * A required {@code bytea} value.
     *
     * @param column the column label
     * @return the bytes, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public byte[] bytes(String column) {
        return required(column, bytesOrNull(column));
    }

    /**
     * An optional {@code bytea} value.
     *
     * @param column the column label
     * @return the bytes, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public byte[] bytesOrNull(String column) {
        return read(column, () -> resultSet.getBytes(column));
    }

    /**
     * A required {@code jsonb} column, as its JSON text.
     *
     * <p>The driver hands back {@code jsonb} as a string, which is what {@link Jsonb} parses. Prefer
     * {@link Jsonb#objectColumn(Row, String)} to get a parsed map in one step; this accessor is for
     * the cases where the raw document should be passed through untouched — a provenance envelope on
     * its way to a verifier, for instance, where re-serializing it would risk changing the bytes a
     * signature covers.
     *
     * @param column the column label
     * @return the JSON text, never {@code null}
     * @throws RowMappingException if the column is absent or NULL
     */
    public String json(String column) {
        return required(column, jsonOrNull(column));
    }

    /**
     * An optional {@code jsonb} column, as its JSON text.
     *
     * @param column the column label
     * @return the JSON text, or {@code null}
     * @throws RowMappingException if the column is absent
     */
    public String jsonOrNull(String column) {
        return read(column, () -> resultSet.getString(column));
    }

    // ------------------------------------------------------------------ internals

    /** A {@link ResultSet} read that is allowed to throw the checked exception JDBC insists on. */
    @FunctionalInterface
    private interface JdbcRead<T> {
        T read() throws SQLException;
    }

    private <T> T read(String column, JdbcRead<T> access) {
        Objects.requireNonNull(column, "column");
        try {
            return access.read();
        } catch (SQLException e) {
            // Almost always one of: the column is not in the SELECT list, the column was renamed in a
            // migration and this mapper was not, or the SQL type does not match the accessor. All
            // three want the same response from the caller and the same detail in the log.
            throw new RowMappingException(
                    "Mapping " + description + ": cannot read column '" + column
                            + "'. Check that the SELECT lists it and that the accessor matches its SQL type.",
                    e);
        }
    }

    private <T> T required(String column, T value) {
        if (value == null) {
            throw new RowMappingException("Mapping " + description + ": column '" + column
                    + "' was NULL, but this mapper requires a value. Use the *OrNull accessor if NULL is legitimate.");
        }
        return value;
    }
}
