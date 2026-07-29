package io.github.stoicswe.eyeandsickle.client.events;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Every event the bus has carried, for the LOG window's EVENTS tab.
 *
 * <h2>Why the bus records unconditionally</h2>
 *
 * "All events are logged" is only true if it is a property of the broker rather than a habit of its
 * publishers. {@link EventBus} attaches this in its constructor, before anything can publish, so
 * there is no window in which an event is emitted and not recorded — including everything that
 * happens before the log window has ever been opened.
 *
 * <h2>⚠ Bounded, and the bound is a debugging decision</h2>
 *
 * A session that runs for hours publishes a great many events, and an unbounded list is a slow leak
 * whose symptom is the game getting heavier the longer it is played — the exact class of bug an event
 * log exists to help find. {@link #LIMIT} is generous enough to cover far more than anyone scrolls
 * and small enough to cost nothing.
 *
 * <p>⚠ It is <b>session state and is never saved</b>. A debugging record of what this process did is
 * meaningless in the next one, and writing it to the profile would put a growing file on the player's
 * disk in exchange for nothing.
 */
public final class EventRecorder {

    /** How many events are kept. Oldest are dropped first. */
    public static final int LIMIT = 2_000;

    private final Deque<CloudEvent> events = new ArrayDeque<>();
    private long dropped;

    void record(CloudEvent event) {
        events.addLast(event);
        while (events.size() > LIMIT) {
            events.removeFirst();
            dropped++;
        }
    }

    /**
     * Everything held, newest last.
     *
     * <p>A copy: the LOG panel repaints from this while the bus may be publishing into it, and
     * handing out the live deque would be a concurrent modification on the one surface a developer
     * reaches for when something is already going wrong.
     */
    public List<CloudEvent> events() {
        return List.copyOf(events);
    }

    /** How many were discarded to stay inside {@link #LIMIT}. Shown, never hidden. */
    public long dropped() {
        return dropped;
    }

    public int size() {
        return events.size();
    }

    /** Forgets everything. The EVENTS tab offers this so a developer can isolate one interaction. */
    public void clear() {
        events.clear();
        dropped = 0;
    }
}
