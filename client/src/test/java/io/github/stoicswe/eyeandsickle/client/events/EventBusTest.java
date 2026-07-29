package io.github.stoicswe.eyeandsickle.client.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The broker, and the promise that <b>every</b> event is logged.
 *
 * <h2>⚠ The recorder is attached in the constructor, and that is the whole design</h2>
 *
 * "All events must be logged" is a property a bus either has structurally or does not have at all. If
 * the log were an ordinary subscriber that some startup path attaches, then every event published
 * before that path ran is missing from the one place a developer looks — and the events published
 * during startup are exactly the ones worth having.
 */
class EventBusTest {

    @Test
    @DisplayName("an event published before anything subscribes is still in the log")
    void nothingIsMissedBeforeSubscribers() {
        EventBus bus = new EventBus();
        bus.publish(EventTypes.of(EventTypes.SESSION), "/client", "boot");
        assertThat(bus.recorder().events()).hasSize(1);
        assertThat(bus.recorder().events().getFirst().subject()).isEqualTo("boot");
    }

    @Test
    @DisplayName("subscribers see events in publication order")
    void deliveryIsOrdered() {
        // ⚠ Synchronous delivery on purpose: publishing happens on the JavaFX thread and every
        // subscriber touches the scene graph. An async multicaster would be both a threading bug and
        // a reordering, and "more efficient" is not worth either.
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(event -> seen.add(event.subject()));
        for (String subject : List.of("a", "b", "c")) {
            bus.publish(EventTypes.of(EventTypes.WINDOW), "/client", subject);
        }
        assertThat(seen).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("a prefix subscription takes a whole branch and leaves the rest alone")
    void prefixSubscription() {
        EventBus bus = new EventBus();
        List<String> windows = new ArrayList<>();
        bus.subscribe(EventTypes.of(EventTypes.WINDOW), event -> windows.add(event.shortType()));
        bus.publish(EventTypes.of("window.opened"), "/client", "ledger");
        bus.publish(EventTypes.of("window.closed"), "/client", "ledger");
        bus.publish(EventTypes.of("task.started"), "/client", "scan");
        assertThat(windows).containsExactly("window.opened", "window.closed");
    }

    @Test
    @DisplayName("unsubscribing stops delivery, and the log keeps recording")
    void unsubscribe() throws Exception {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        AutoCloseable handle = bus.subscribe(event -> seen.add(event.subject()));
        bus.publish(EventTypes.of(EventTypes.INTENT), "/client", "before");
        handle.close();
        bus.publish(EventTypes.of(EventTypes.INTENT), "/client", "after");
        assertThat(seen).containsExactly("before");
        // The log is not a subscriber anyone can detach — that is the point of attaching it first.
        assertThat(bus.recorder().events()).hasSize(2);
    }

    /**
     * ⚠ A subscriber that throws must not take the publisher down with it.
     *
     * <p>The publisher is a game rule and the subscriber is a panel. A repaint that fails is a broken
     * panel; a repaint that fails and unwinds the caller is a broken <em>purchase</em>, with the
     * ethecoin already spent.
     */
    @Test
    @DisplayName("a subscriber that throws does not break the publisher or the other subscribers")
    void oneBadSubscriber() {
        EventBus bus = new EventBus();
        List<String> survivor = new ArrayList<>();
        bus.subscribe(event -> {
            throw new IllegalStateException("panel is mid-rebuild");
        });
        bus.subscribe(event -> survivor.add(event.subject()));
        bus.publish(EventTypes.of(EventTypes.INTENT), "/client", "purchase");
        assertThat(survivor).containsExactly("purchase");
        // ⚠ And the failure itself is recorded, not swallowed. A packaged client has no console
        // behind it, so a printed stack trace would be genuinely lost; the EVENTS tab is where a
        // player reporting a bug can actually see it.
        assertThat(bus.recorder().events()).extracting(CloudEvent::shortType)
                .containsExactly("intent", "subscriber.failed");
        assertThat(bus.recorder().events().getLast().payload()).contains("panel is mid-rebuild");
    }

    @Test
    @DisplayName("the log is bounded, and says how much it dropped")
    void theLogIsBounded() {
        // Session state, never persisted — but a client left running overnight must not grow a list
        // forever. Dropping the oldest is right for a debugging log; dropping silently is not, so the
        // count is kept and the panel shows it.
        EventBus bus = new EventBus();
        int over = EventRecorder.LIMIT + 250;
        for (int i = 0; i < over; i++) {
            bus.publish(EventTypes.of(EventTypes.TASK), "/client", "tick-" + i);
        }
        assertThat(bus.recorder().events()).hasSize(EventRecorder.LIMIT);
        assertThat(bus.recorder().dropped()).isEqualTo(250);
        assertThat(bus.recorder().events().getFirst().subject()).isEqualTo("tick-250");
        assertThat(bus.recorder().events().getLast().subject()).isEqualTo("tick-" + (over - 1));
    }

    @Test
    @DisplayName("the recorded list is a copy — a reader cannot mutate the log")
    void eventsAreACopy() {
        EventBus bus = new EventBus();
        bus.publish(EventTypes.of(EventTypes.CHAIN), "/client/chain", "block", Map.of("height", "9"));
        List<CloudEvent> first = bus.recorder().events();
        bus.publish(EventTypes.of(EventTypes.CHAIN), "/client/chain", "block", Map.of("height", "10"));
        assertThat(first).hasSize(1);
        assertThat(bus.recorder().events()).hasSize(2);
    }
}
