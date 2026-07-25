package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} whose "now" the test moves by hand.
 *
 * <p>Time is injected everywhere in this slice precisely so a test can step past an expiry
 * deterministically rather than sleeping ({@link InMemoryPlayerSessionStore}). A {@link Clock#fixed}
 * cannot advance, so tests that need to cross a boundary use this instead; the value it reports only
 * changes when a test tells it to, which keeps the assertion about a wall-clock instant rather than a
 * real one.
 */
final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    static MutableClock at(Instant instant) {
        return new MutableClock(instant, ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    void advance(Duration by) {
        this.instant = this.instant.plus(by);
    }

    void set(Instant now) {
        this.instant = now;
    }
}
