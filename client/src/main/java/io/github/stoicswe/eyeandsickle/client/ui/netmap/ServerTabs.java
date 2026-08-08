package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One tab per server the player has heard of, and the map filtered to whichever is open.
 *
 * <h2>Why the map is per server at all</h2>
 *
 * The graph used to draw <b>the whole world in one grid</b>, layered by hop distance from the rig,
 * with bridges as ordinary edges between columns. That is a truthful picture and an unreadable one
 * past the first bridge: {@code docs/client/09} §8 measured maps at 4–10 columns deep before any
 * server was crossed, and each crossing adds a server's whole depth to the right-hand end.
 * {@code docs/design/18} §2 then made a server 4–13 machines deep on purpose, which turns a
 * three-server map into a forty-column strip nobody can read.
 *
 * <p>A tab per server is the structure the network already has. A server is what a bridge connects,
 * what difficulty steps across ({@code design/18} §4), and what the sweep's yield band keys on — so
 * it is also the unit a player already thinks in. Each tab lays its server out from its own
 * shallowest known machine, which is what {@code NetLayout}'s rebase exists for.
 *
 * <h2>⚠ THE TAB LIST IS WHAT THE PLAYER HAS HEARD OF, never the world</h2>
 *
 * {@link NetMap#knownServers()} is already exactly that — a server reaches it by being swept, or by a
 * bridge the player has identified advertising it. Enumerating anything else here would publish the
 * shape of the world for free, which is the rule {@code NetRules} states as "undiscovered hosts do
 * not exist in {@code knownNodes}, and the map draws nothing where they are". A tab for a server the
 * player has never heard of is a placeholder with a name on it, which is worse than a placeholder.
 *
 * <p>⚠ <b>A tab may legitimately be EMPTY.</b> A bridge that has been identified advertises the
 * server on its far side by name, and that is all the player has — no machine over there has been
 * swept, because sweeping it needs a foothold on the bridge and a {@code connect}. So the tab exists,
 * carries the name, and says it is unexplored. That is the honest reading and it is the whole point
 * of the bridge finding: <em>knowing the door is there</em> ({@code design/07} §5.1a).
 */
public final class ServerTabs {

    private ServerTabs() {}

    /**
     * One tab.
     *
     * @param serverId the id the rest of the client keys on — never shown
     * @param label the server's name, which is what a bridge advertises and what the tab reads
     * @param home whether this is the player's own server
     * @param current whether the player's vantage is on it right now
     * @param machines how many machines on it the player has discovered; {@code 0} is legitimate
     */
    public record Tab(String serverId, String label, boolean home, boolean current, int machines) {

        /** Whether the player has actually seen anything on this server. */
        public boolean explored() {
            return machines > 0;
        }
    }

    /**
     * The tabs, in the order they are drawn.
     *
     * <h2>⚠ HOME FIRST, THEN BY NAME — deliberately NOT by depth or by discovery order</h2>
     *
     * Home first because it is the one tab that is always there and always means the same thing, and
     * a strip whose first entry moves is a strip whose muscle memory is wrong. Everything after it is
     * alphabetical, because the alternatives are worse: <b>depth</b> reorders the strip the moment a
     * chord changes a server's distance, and <b>discovery order</b> is a private history that makes
     * two players' strips disagree about a world they are both looking at. A name does not move.
     */
    public static List<Tab> of(NetMap map) {
        if (map == null) {
            return List.of();
        }
        String currentId = map.currentServer() == null ? "" : map.currentServer().serverId();
        String homeId = homeIdOf(map);

        List<Tab> tabs = new ArrayList<>();
        for (ServerRef server : map.knownServers()) {
            int machines = 0;
            for (Sighting sighting : map.sightings()) {
                if (server.serverId().equals(sighting.serverId())) {
                    machines++;
                }
            }
            tabs.add(new Tab(
                    server.serverId(),
                    server.name() == null || server.name().isBlank() ? server.serverId() : server.name(),
                    server.serverId().equals(homeId),
                    server.serverId().equals(currentId),
                    machines));
        }
        tabs.sort(Comparator.comparing((Tab tab) -> tab.home() ? 0 : 1).thenComparing(Tab::label));
        return List.copyOf(tabs);
    }

    /**
     * Which server's tab should be open when the panel has no opinion yet.
     *
     * <p>The one the player is standing on. Opening on home would be defensible and is wrong in the
     * one case that matters — a player four servers out, who opens the map and is shown a server they
     * left an hour ago.
     */
    public static String initial(NetMap map) {
        List<Tab> tabs = of(map);
        for (Tab tab : tabs) {
            if (tab.current()) {
                return tab.serverId();
            }
        }
        return tabs.isEmpty() ? "" : tabs.getFirst().serverId();
    }

    /**
     * The map as one server sees it.
     *
     * <h2>⚠ THE RIG IS CARRIED ONTO EVERY TAB IT BELONGS ON, and only there</h2>
     *
     * {@code Sighting.self} is the player's own machine and it sits on the home server, so it appears
     * on the home tab like any other machine and on no other. It is not special-cased in — a tab that
     * planted the rig on a foreign server would draw a link from the player's own machine to a server
     * they have to cross two bridges to reach.
     *
     * <h2>⚠ LINKS ARE KEPT ONLY WHEN BOTH ENDS SURVIVE THE FILTER</h2>
     *
     * A bridge's own link crosses to a machine on the far side, so on a filtered map one end is
     * missing. Keeping it would leave {@code NetLayout} an edge pointing at an address it has no
     * sighting for — which is not a crash, it is worse: {@code adjacency} would silently build a
     * neighbour set containing a machine that is not on the grid, and the barycentre pass would
     * arrange the layer around something invisible.
     *
     * <p>Nothing is lost by dropping it. The bridge is still drawn, still carries its BRIDGE glyph,
     * and still names the server on its far side — which is the same information the edge carried,
     * said in the one place a player can act on it.
     *
     * @param serverId the server to keep; blank or unknown yields an empty map rather than the world
     */
    public static NetMap filter(NetMap map, String serverId) {
        if (map == null) {
            return NetMap.empty();
        }
        if (serverId == null || serverId.isBlank()) {
            return withSightings(map, List.of(), List.of());
        }

        List<Sighting> kept = new ArrayList<>();
        Set<String> addresses = new HashSet<>();
        for (Sighting sighting : map.sightings()) {
            if (serverId.equals(sighting.serverId())) {
                kept.add(sighting);
                addresses.add(sighting.address());
            }
        }

        List<NetLink> links = new ArrayList<>();
        for (NetLink link : map.links()) {
            if (addresses.contains(link.fromAddress()) && addresses.contains(link.toAddress())) {
                links.add(link);
            }
        }
        return withSightings(map, kept, links);
    }

    /**
     * The server a bridge on this tab leads to, if the player has identified it.
     *
     * <p>Used to decide which tabs exist at all on a map whose {@code knownServers} has not caught up
     * — and, more usefully, to answer "which of these tabs is through that door".
     */
    public static Set<String> advertisedFrom(NetMap map, String serverId) {
        Set<String> names = new LinkedHashSet<>();
        if (map == null) {
            return names;
        }
        for (Sighting sighting : map.sightings()) {
            if (serverId.equals(sighting.serverId())
                    && !sighting.bridgePeerServerName().isBlank()) {
                names.add(sighting.bridgePeerServerName());
            }
        }
        return names;
    }

    /**
     * The home server's id.
     *
     * <p>⚠ Read off the <b>rig's own sighting</b> rather than off {@code knownServers}. {@link NetMap}
     * publishes no "which of these is home" flag — {@link ServerRef#home()} exists, but a
     * {@code ServerRef} reaches the client through several producers and the tests' fixtures set it
     * inconsistently, so the machine that says {@code self} is the answer that cannot be wrong.
     * Falls back to the flag, then to nothing.
     */
    private static String homeIdOf(NetMap map) {
        for (Sighting sighting : map.sightings()) {
            if (sighting.self()) {
                return sighting.serverId();
            }
        }
        for (ServerRef server : map.knownServers()) {
            if (server.home()) {
                return server.serverId();
            }
        }
        return "";
    }

    private static NetMap withSightings(NetMap map, List<Sighting> sightings, List<NetLink> links) {
        return new NetMap(
                map.currentServer(),
                map.vantageAddress(),
                map.hopCeiling(),
                map.knownServers(),
                sightings,
                links);
    }
}
