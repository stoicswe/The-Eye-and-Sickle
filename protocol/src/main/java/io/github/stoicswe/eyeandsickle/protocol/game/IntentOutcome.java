package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What the server did with a {@link GameIntent}.
 *
 * <h2>⚠ "Refused" and "unreachable" must never collapse</h2>
 *
 * {@code docs/client/01-visual-language.md} §9.4 requires that "the server refused this" and "we could
 * not reach the server" stay distinguishable all the way down — the client already encodes them as
 * different exit statuses ({@code 1} versus {@code 69}) for exactly this reason. A player who cannot
 * tell them apart does not know whether to change what they did or wait.
 *
 * <p>⚠ {@link #UNAVAILABLE} is therefore something the <em>client</em> produces when nothing arrived,
 * and something the server should almost never send. A server that answers "unavailable" is telling
 * the player a lie about where the problem is.
 *
 * @param status what happened
 * @param message the server's reason, shown to the player
 * @param revision the snapshot revision this outcome produced — ⚠ lets the client know whether the
 *     snapshot it holds already reflects this change, instead of guessing from a timer
 */
public record IntentOutcome(Status status, String message, long revision) {

    public enum Status {
        /** The server did it. */
        OK,
        /** The server considered it and declined. The message says why, and the player can act on it. */
        REFUSED,
        /** Nothing arrived. ⚠ Produced by the client, not the server. */
        UNAVAILABLE
    }

    public static IntentOutcome ok(long revision) {
        return new IntentOutcome(Status.OK, "", revision);
    }

    public static IntentOutcome refused(String why) {
        return new IntentOutcome(Status.REFUSED, why, -1);
    }
}
