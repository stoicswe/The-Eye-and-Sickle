package io.github.stoicswe.eyeandsickle.client.events;

import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

/**
 * The client's event broker — Spring's multicaster, publishing {@link CloudEvent}s.
 *
 * <h2>⚠ This is the ONE thing spring-context is here for</h2>
 *
 * {@code client/pom.xml}'s enforcer bans every Spring module except this one's, and the ban's message
 * says why: an event multicaster does not make the client authoritative, which is what Invariant I14
 * protects, while spring-web and the jdbc layers are exactly how it would. Anything that starts
 * reaching for more Spring than this has left the amendment behind.
 *
 * <h2>Why the multicaster and not an ApplicationContext</h2>
 *
 * {@link SimpleApplicationEventMulticaster} <em>is</em> Spring's event broker — it is what
 * {@code AbstractApplicationContext} delegates every {@code publishEvent} to. Taking it directly
 * gives three things a context would not:
 *
 * <ul>
 *   <li><b>Unsubscription.</b> {@code AbstractApplicationContext} can add a listener after refresh
 *       and offers no public way to remove one. Every panel in this client is created and destroyed
 *       as windows open and close, so a bus they cannot detach from is a leak per window.
 *   <li><b>No bean lifecycle.</b> There are no beans here — views are constructed imperatively by the
 *       desk. A context would exist solely to hold a multicaster, and would need refreshing on the
 *       startup path the PowerOn splash is already measuring.
 *   <li><b>No component scan.</b> Nothing to scan, and scanning is where startup cost lives.
 * </ul>
 *
 * <p>It still implements {@link ApplicationEventPublisher}, so what the rest of the client depends on
 * is Spring's own interface rather than a type of ours.
 *
 * <h2>⚠ Delivery is SYNCHRONOUS and on the caller's thread, deliberately</h2>
 *
 * No {@code TaskExecutor} is set. Two reasons, and both are correctness rather than simplicity: this
 * client's subscribers are JavaFX views, and JavaFX may only be touched from the application thread —
 * an executor here would hand every panel a threading bug. And synchronous delivery preserves
 * <b>ordering</b>, which is the property that makes the event log a usable debugging record instead
 * of a plausible-looking reordering of what happened.
 */
public final class EventBus implements ApplicationEventPublisher {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(EventBus.class.getName());

    private final SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
    private final EventRecorder recorder = new EventRecorder();

    public EventBus() {
        // ⚠ Spring PROPAGATES a listener's exception to the publisher unless an error handler is set,
        // and that default is wrong here. The publisher is a game rule; the subscriber is a panel. A
        // repaint that throws should be a broken panel — with the default it is a broken *purchase*,
        // unwinding the caller with the ethecoin already spent, on a stack trace that names a view.
        // Swallowing is the lesser evil precisely because the swallowed failure is not lost: the
        // recorder has already seen the event, and the throwable is published as one of its own.
        multicaster.setErrorHandler(this::subscriberFailed);
        // ⚠ The recorder subscribes FIRST, in the constructor, so that "every event is logged" is a
        // property of the bus rather than of whoever remembered to attach it. An event published
        // before the log window has ever been opened is still in the record when it is.
        subscribe(recorder::record);
    }

    /**
     * What happens when a subscriber throws.
     *
     * <p>⚠ Recorded as an event rather than printed. A stack trace on stdout is invisible in a
     * packaged client — there is no console behind a {@code .app} — and the EVENTS tab is the surface
     * this whole layer exists to give a player who is reporting a bug.
     *
     * <p>The failure event is published through {@link #publish} like any other, so it is subject to
     * the same handler; a subscriber that throws <em>while handling a failure</em> is caught by the
     * same net rather than recursing, because the recorder never throws and nothing else is required
     * to be listening.
     */
    private void subscriberFailed(Throwable failure) {
        // ⚠ Logged as well as recorded, and the two are not redundant. The recorded event is
        // visible on the EVENTS tab and carries only the message; this carries the STACK TRACE,
        // which is the half that says which subscriber threw. A packaged client has no console
        // behind it, so without this the trace has nowhere to go at all.
        LOG.log(java.util.logging.Level.SEVERE, "an event subscriber threw", failure);
        recorder.record(CloudEvent.of(
                EventTypes.of("subscriber.failed"),
                "/client/events",
                failure.getClass().getSimpleName(),
                Map.of("message", String.valueOf(failure.getMessage()))));
    }

    /** Everything published so far, for the LOG window's EVENTS tab. */
    public EventRecorder recorder() {
        return recorder;
    }

    /**
     * Publishes an event.
     *
     * @return the event, so a caller can log or assert on the id the envelope generated
     */
    public CloudEvent publish(CloudEvent event) {
        if (event != null) {
            multicaster.multicastEvent(new GameEvent(this, event));
        }
        return event;
    }

    /** Builds the envelope and publishes it. The form nearly every caller wants. */
    public CloudEvent publish(String type, String source, String subject, Map<String, String> data) {
        return publish(CloudEvent.of(type, source, subject, data));
    }

    /** The same, with no payload. */
    public CloudEvent publish(String type, String source, String subject) {
        return publish(type, source, subject, Map.of());
    }

    /**
     * Subscribes to every event.
     *
     * @return the handle that detaches it. ⚠ Panels MUST close this — see the class comment: a bus
     *     that cannot be detached from leaks one listener per window opened.
     */
    public AutoCloseable subscribe(Consumer<CloudEvent> listener) {
        ApplicationListener<GameEvent> adapter = event -> listener.accept(event.cloudEvent());
        multicaster.addApplicationListener(adapter);
        return () -> multicaster.removeApplicationListener(adapter);
    }

    /**
     * Subscribes to one branch of the type tree.
     *
     * <p>⚠ Prefix matching, not equality — {@code io.…eyeandsickle.portscan} catches
     * {@code portscan.started} and {@code portscan.finished} both. A subscriber wanting one exact
     * type says the whole thing; the prefix is what makes the reverse-DNS naming worth having.
     */
    public AutoCloseable subscribe(String typePrefix, Consumer<CloudEvent> listener) {
        return subscribe(event -> {
            if (event.isA(typePrefix)) {
                listener.accept(event);
            }
        });
    }

    /** Spring's own entry point. Anything that is not a {@link GameEvent} is passed through as-is. */
    @Override
    public void publishEvent(ApplicationEvent event) {
        multicaster.multicastEvent(event);
    }

    @Override
    public void publishEvent(Object event) {
        if (event instanceof CloudEvent envelope) {
            publish(envelope);
            return;
        }
        if (event instanceof ApplicationEvent applicationEvent) {
            publishEvent(applicationEvent);
        }
    }
}
