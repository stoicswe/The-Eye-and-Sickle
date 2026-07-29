package io.github.stoicswe.eyeandsickle.client.events;

/**
 * Every event type this client publishes, in one place.
 *
 * <h2>Why the strings are named constants rather than literals at the publisher</h2>
 *
 * A type is what a subscriber filters on, so a typo does not fail — it produces an event nobody
 * receives and a subscription that never fires, with nothing anywhere reporting either. Two names for
 * one thing is the same bug wearing a second face. Collecting them here also makes the vocabulary
 * reviewable: the list below is the complete answer to "what can happen in this game", which is a
 * question worth being able to answer.
 *
 * <p>⚠ All of them carry {@link CloudEvent#NAMESPACE}, per §3.1.1's rule that a type SHOULD be
 * prefixed with a reverse-DNS name the producer owns. {@link #of} applies it, so no constant here
 * repeats the prefix and none can drift from it.
 */
public final class EventTypes {

    private EventTypes() {}

    /** Prefixes a bare name with the namespace. */
    public static String of(String name) {
        return CloudEvent.NAMESPACE + "." + name;
    }

    // ── the player doing something ─────────────────────────────────────────────────────────────

    /** A window was opened, raised, or closed. Subject is the window id. */
    public static final String WINDOW = "window";

    /** Any intent that reached the rules — the session chokepoint. Subject is what it acted on. */
    public static final String INTENT = "intent";

    // ── the world doing something ──────────────────────────────────────────────────────────────

    /** A task the rig was running finished. Subject is the task kind. */
    public static final String TASK = "task";

    /** The chain moved: a block landed, a transaction confirmed, a fill completed. */
    public static final String CHAIN = "chain";

    /** The session told its listeners something changed — the background heartbeat. */
    public static final String SESSION = "session";
}
