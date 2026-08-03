package io.github.stoicswe.eyeandsickle.engine.state;

import java.util.ArrayList;
import java.util.List;

/**
 * One generated virtual server: a bounded network of up to fifty machines, reachable from the others
 * only across a {@code BRIDGE} host.
 *
 * <h2>Depth is the difficulty axis, and it is unambiguous by construction</h2>
 *
 * {@link #depthFromHome} is BFS distance over the server graph from the player's home server, and
 * every generated table — machine count, host kind, difficulty tier, firewall tier, tarpit, canaries,
 * honeypot, document chance and counter-hack chance — reads off it. That is the whole "further from
 * home is harder, richer and more dangerous" gradient, in one number.
 *
 * <p>⚠ It is only unambiguous because of a constraint in the generator that looks cosmetic and is
 * not: the server graph is a spanning tree plus at most two <b>depth-preserving</b> chords, where a
 * chord may only join servers whose depths differ by at most one. A chord between depths {@code d}
 * and {@code d+2} would shorten a BFS path and silently re-depth a server <em>after</em> its machines
 * had already been generated against the old depth — a whole server generated one tier too hard, or
 * too soft, with nothing in the save to show it. A ring or an Erdős–Rényi graph fails the same way
 * for a different reason: with two directions home, "depth" stops naming one thing.
 *
 * <h2>Federation is written down and not built</h2>
 *
 * Every server here is generated locally, and {@link #peerServerIds} always names another local one.
 * {@code docs/design/13} governs what a bridge would mean between two real operators' home servers —
 * a handshake exposing the sightings the far server chooses to publish, never the far topology, and
 * an explicit per-server opt-in before a player crosses. None of that is implemented: {@code CL-8}
 * records that {@code RemoteGameSession} has no transport, no OAuth flow and no reconnect loop, so a
 * cross-server bridge has nothing to cross to. In solo, {@code GameSave.federable} stays false, which
 * is how Invariant I14 survives a save file the player can edit.
 */
public final class ServerState {

    /** Stable id, {@code srv-<index>}. The join key {@link HostState#serverId} points at. */
    public String serverId = "";

    /** What the map calls it. Short, lowercase, and it commits nothing narrative (decision N-4). */
    public String name = "";

    /** BFS distance from the home server. 0 for home. Every difficulty table reads this. */
    public int depthFromHome = 0;

    public boolean home = false;

    /** Server ids reachable across one bridge. Symmetric — the generator writes both sides. */
    public List<String> peerServerIds = new ArrayList<>();
}
