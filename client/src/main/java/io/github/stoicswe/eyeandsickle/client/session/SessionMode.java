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
    ONLINE("Online", "Connected to a home server. Losses here are real."),

    /**
     * Connected to a LAN server — a friends game on a private network
     * ({@code docs/architecture/12-lan-mode.md}).
     *
     * <p>State is the server's, as in {@link #ONLINE}, and losses between players on that network are
     * real. What differs is the identity behind it and where the state may go:
     *
     * <ul>
     *   <li>⚠ Identity is a <strong>server-assigned UUID with no proof behind it</strong> — a bearer
     *       token. Whoever holds it is that player.
     *   <li>⚠ <strong>Nothing here can ever move to a federated server.</strong> Same rule as
     *       {@link #SOLO}, one rung up: unproven identity means unverifiable items and un-quorumed
     *       outcomes, and importing any of it is what <b>I15</b> exists to prevent.
     * </ul>
     *
     * <p>The explanation says the second point rather than the first, because it is the one with a
     * consequence a player cannot reverse — and {@code 12} §5 requires it be said before the first
     * character is made, not after forty hours.
     */
    LAN(
            "LAN",
            "A friends game on this network. Losses here are real, and nothing here can move to a "
                    + "federated server.");

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
