package io.github.stoicswe.eyeandsickle.client.events;

import org.springframework.context.ApplicationEvent;

/**
 * A {@link CloudEvent} on Spring's event bus.
 *
 * <h2>⚠ Why the envelope is wrapped rather than published directly</h2>
 *
 * {@code ApplicationEventPublisher.publishEvent(Object)} accepts any payload, but Spring then wraps
 * it in a {@code PayloadApplicationEvent<T>} and resolves listeners by that generic parameter. That
 * works, and it means every listener signature in the codebase becomes
 * {@code ApplicationListener<PayloadApplicationEvent<CloudEvent>>} — a type nobody reads correctly at
 * a glance, and one whose resolution depends on generic information that erases under a lambda.
 *
 * <p>One concrete event class costs a file and makes every subscription
 * {@code ApplicationListener<GameEvent>}, which says what it is.
 */
public final class GameEvent extends ApplicationEvent {

    private final transient CloudEvent event;

    /**
     * @param source what published it — Spring's own {@code source}, not the CloudEvent's. The two
     *     are different: this is the object that called publish, the envelope's is a URI naming the
     *     part of the app it came from.
     */
    public GameEvent(Object source, CloudEvent event) {
        super(source);
        this.event = event;
    }

    public CloudEvent cloudEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "GameEvent[" + event.type() + " " + event.subject() + "]";
    }
}
