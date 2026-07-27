package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * One server on the network, named well enough for the player to know where they are.
 *
 * <p>The map window always says which server the player is currently working from, in chrome that sits
 * inside the panel rather than in a tab or an overlay. That is not decoration: {@code depthFromHome} is
 * the single number that predicts danger — the further from home, the harder the machines, the more
 * likely one hacks back — so a player who cannot see it is making the game's central risk decision
 * blind.
 *
 * <h2>Depth is well defined, and that is a property of how the world is built</h2>
 *
 * {@code depthFromHome} is breadth-first distance over the server graph, from the one server flagged
 * {@link #home()}. It is unambiguous because the generator only ever adds a cross-link between servers
 * whose depths differ by at most one, which makes BFS depth provably invariant under adding those links.
 * A ring would have made depth ambiguous (two directions round) and an unconstrained mesh would have let
 * a late link silently re-depth a server whose machines had already been generated against the old
 * value. The shape is the rules' business; this record only carries the answer.
 *
 * <h2>What is not here</h2>
 *
 * <strong>A host count.</strong> "This server has 34 machines" is an aggregate about machines the player
 * has not discovered, and the discovery model permits exactly one such number in the whole vocabulary:
 * {@link SweepReport#inRange()}, which reports the player's own instrument rather than the network's
 * contents. A per-server total would leak the size of everything beyond the horizon, and it would do it
 * permanently rather than as one sweep's reading.
 *
 * <p><strong>Anything about federation.</strong> In single player every server here is locally generated
 * and a bridge's far side is another local server, so nothing crosses a machine boundary and Invariant
 * I14 survives a save file the player can edit. A real cross-server bridge would be the boundary
 * {@code docs/design/13-multiplayer-and-federation-play.md} §4 governs — servers "share the minimum
 * needed to recognize identities and validate provenance, never enough for one server to grief another's
 * internal state", so a bridge would expose a handshake answered with the sightings the far server
 * chooses to publish, never its topology. {@code RemoteGameSession} has no transport to make one over
 * today (CL-8). So there is no {@code federated} flag to get wrong; when there is something to say, it
 * will be said by the side that can prove it.
 *
 * @param serverId stable identity, and the join key {@link Sighting#serverId()} points at
 * @param name what to call it on screen, e.g. {@code "home-relay"}
 * @param depthFromHome breadth-first distance from the home server; {@code 0} for home itself
 * @param home whether this is the server the player started on — exactly one server in a world is
 */
public record ServerRef(String serverId, String name, int depthFromHome, boolean home) {

    public ServerRef {
        // "" rather than a rejected null, for the same reason the whole vocabulary reads that way: a
        // renderer that throws mid-repaint is a worse answer than a blank cell, and every consumer of
        // this record is a renderer.
        serverId = serverId == null ? "" : serverId;
        name = name == null ? "" : name;

        // A negative depth is not a mild data error. Depth is what the danger tables are indexed by, so
        // a negative one either clamps into the safest row (a deep server that generates like home) or
        // walks off the front of the table, and both failures are silent.
        if (depthFromHome < 0) {
            throw new IllegalArgumentException("depth must be >= 0");
        }
    }
}
