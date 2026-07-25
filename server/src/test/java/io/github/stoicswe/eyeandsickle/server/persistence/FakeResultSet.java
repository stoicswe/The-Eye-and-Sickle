package io.github.stoicswe.eyeandsickle.server.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ResultSet} backed by a map, for testing {@link Row} without a database.
 *
 * <h2>Why a dynamic proxy rather than a mock or a real query</h2>
 *
 * {@code ResultSet} has around two hundred methods, so implementing it by hand is not an option, and
 * a real query would put this test behind a Docker daemon — which the build forbids for
 * {@code mvn verify}. A mocking framework would work, but a proxy over a map is deterministic, has no
 * stubbing ceremony, and models the two behaviours that actually matter here:
 *
 * <ul>
 *   <li>an unknown column raises {@link SQLException}, exactly as the driver does, so
 *       {@link RowMappingException}'s "column was renamed" path is genuinely exercised;
 *   <li>{@code getInt}/{@code getLong}/{@code getBoolean} return a zero-ish value for SQL NULL and
 *       set {@code wasNull()} — the JDBC quirk that makes a missing value indistinguishable from a
 *       real zero, and the whole reason {@link Row} exists.
 * </ul>
 */
final class FakeResultSet {

    private FakeResultSet() {}

    /**
     * A result set positioned on one row.
     *
     * @param values column label to value; a value of {@code null} models SQL NULL, and an absent key
     *     models a column that is not in the result set at all
     * @return the proxy
     */
    static ResultSet row(Map<String, Object> values) {
        Map<String, Object> copy = new HashMap<>(values);
        return (ResultSet) Proxy.newProxyInstance(
                FakeResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, new Handler(copy));
    }

    /**
     * Convenience for a single-column row.
     *
     * @param column the column label
     * @param value the value, possibly {@code null}
     * @return the proxy
     */
    static ResultSet singleColumn(String column, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, value);
        return row(values);
    }

    private static final class Handler implements InvocationHandler {

        private final Map<String, Object> values;
        private boolean lastWasNull;

        private Handler(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            String name = method.getName();
            switch (name) {
                case "wasNull" -> {
                    return lastWasNull;
                }
                case "toString" -> {
                    return "FakeResultSet" + values;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    // fall through to the column accessors below
                }
            }

            if (args == null || args.length == 0 || !(args[0] instanceof String column)) {
                throw new UnsupportedOperationException(
                        "FakeResultSet models label-based accessors only; " + name + " is not one");
            }
            if (!values.containsKey(column)) {
                // The message the PostgreSQL driver actually produces, so the test exercises the same
                // path a renamed column would take in production.
                throw new SQLException("The column name " + column + " was not found in this ResultSet.");
            }

            Object value = values.get(column);
            lastWasNull = value == null;
            return switch (name) {
                case "getString" -> value == null ? null : String.valueOf(value);
                case "getBytes", "getBigDecimal", "getObject" -> value;
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getBoolean" -> value != null && (Boolean) value;
                default -> throw new UnsupportedOperationException("FakeResultSet does not model " + name);
            };
        }
    }
}
