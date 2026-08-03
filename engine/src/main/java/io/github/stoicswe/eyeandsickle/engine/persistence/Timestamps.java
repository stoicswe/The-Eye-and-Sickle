package io.github.stoicswe.eyeandsickle.engine.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The write side of every timestamp this project binds into SQL: turns an {@link Instant} into the
 * {@link OffsetDateTime} that goes to a {@code timestamp with time zone} column.
 *
 * <p>⚠ It began as a PostgreSQL driver rule — that driver refuses a bare {@code java.time.Instant},
 * throwing "Can't infer the SQL type to use for an instance of java.time.Instant". H2 is not so
 * fussy, and the class survives the move anyway as a <em>house rule</em>: the read side comes back
 * through {@code Row.instant} as an {@code OffsetDateTime}, so this keeps one spelling on both sides
 * of every column. Using one without the other is the bug this class removes.
 *
 * <p>⚠ It lives in the engine module rather than the server's persistence package because the engine
 * now owns a JDBC store of its own ({@link io.github.stoicswe.eyeandsickle.engine.save.JdbcSaveStore}),
 * and two copies of a one-line conversion is exactly how the two sides of a column drift apart. The
 * server imports it from here.
 *
 * <p>Always UTC. Provenance timestamps are signed and compared across servers in different timezones
 * ({@code docs/architecture/04-item-provenance.md}); a local-zone offset would let the same instant
 * bind two ways.
 */
public final class Timestamps {

    private Timestamps() {}

    /**
     * Converts an instant to a bindable {@code timestamptz} value.
     *
     * @param instant the instant to store
     * @return the same moment as a UTC {@link OffsetDateTime}
     */
    public static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Converts a nullable instant, preserving {@code null} so it binds as SQL {@code NULL}.
     *
     * @param instant the instant to store, or {@code null}
     * @return the UTC {@link OffsetDateTime}, or {@code null}
     */
    public static OffsetDateTime atOrNull(Instant instant) {
        return instant == null ? null : at(instant);
    }
}
