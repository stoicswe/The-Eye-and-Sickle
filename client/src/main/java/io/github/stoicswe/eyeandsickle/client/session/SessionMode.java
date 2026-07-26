package io.github.stoicswe.eyeandsickle.client.session;

/**
 * Which kind of game is running.
 *
 * <p>The client shows this permanently rather than hiding it. A player must always be able to tell
 * whether their losses are real: {@code docs/design/13-multiplayer-and-federation-play.md} makes
 * multiplayer opt-in precisely because real-loss play is a choice, and a mode indicator that could be
 * missed would make that choice ambiguous at exactly the wrong moment.
 */
public enum SessionMode {

    /**
     * A local, offline game. No network, no account, no database.
     *
     * <p>A solo character is local-only and can never federate ({@code docs/architecture/02} §4). The
     * save is on the player's own disk and is therefore player-editable — which is fine, because
     * nothing downstream trusts it and nothing it contains can reach another player.
     */
    SOLO("Solo", "Offline. Nothing here is shared, and nothing here can be lost to anyone else."),

    /**
     * Connected to a home server. State is the server's; the client renders and sends intent.
     *
     * <p>This is where Invariant I14 does its work and where losses are real.
     */
    ONLINE("Online", "Connected to a home server. Losses here are real.");

    private final String label;
    private final String explanation;

    SessionMode(String label, String explanation) {
        this.label = label;
        this.explanation = explanation;
    }

    public String label() {
        return label;
    }

    public String explanation() {
        return explanation;
    }
}
