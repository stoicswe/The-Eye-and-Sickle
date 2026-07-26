package io.github.stoicswe.eyeandsickle.solo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A hand-wound clock.
 *
 * <p>{@link Clock#fixed} cannot be advanced and {@link Clock#offset} needs a new instance each time,
 * neither of which suits an engine that is handed one clock at construction and keeps it. This is the
 * smallest thing that lets a test say "now three hours pass" to an object that already exists.
 */
final class TestClock extends Clock {

    private Instant instant;

    TestClock(Instant start) {
        this.instant = start;
    }

    void advance(Duration by) {
        instant = instant.plus(by);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
