package io.github.stoicswe.eyeandsickle.server.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The write-side counterpart to {@link Row#instant(String)}: turns an {@link Instant} into the type
 * the PostgreSQL driver will bind to a {@code timestamptz} column.
 *
 * <p>This exists because the driver refuses a bare {@code java.time.Instant} — it throws "Can't infer
 * the SQL type to use for an instance of java.time.Instant" — while it accepts an {@link
 * OffsetDateTime} directly. So every timestamp bound into a statement must go through here, exactly as
 * every timestamp read back comes through {@code Row.instant} as an {@code OffsetDateTime}. The two
 * are a matched pair; using one without the other is the bug this class removes.
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
