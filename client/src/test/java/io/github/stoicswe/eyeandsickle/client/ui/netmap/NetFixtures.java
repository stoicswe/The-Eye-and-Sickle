package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built networks for the map tests.
 *
 * <p>Deliberately hand-built rather than generated: the assertions here are about geometry, and a
 * fixture whose shape comes out of the rules engine would make a failure ambiguous between "the
 * renderer is wrong" and "the generator changed". These are the shapes the picture has to survive —
 * a fan-out, a lateral pair, a crossing, a bridge on the outermost layer, and a column too wide to
 * draw.
 */
final class NetFixtures {

    static final ServerRef HOME = new ServerRef("srv-home", "home-relay", 0, true);
    static final ServerRef SOUTH = new ServerRef("srv-south", "south-exchange", 1, false);

    private NetFixtures() {}

    static Sighting self(String address) {
        return sighting(address, HostKind.SELF, 0, true, false, false, "");
    }

    static Sighting sighting(
            String address,
            HostKind kind,
            int hops,
            boolean vantage,
            boolean foothold,
            boolean trap,
            String peerServerName) {
        return new Sighting(
                address,
                "",
                HOME.serverId(),
                kind,
                DifficultyTier.of(2),
                SignalStrength.MODERATE,
                hops,
                vantage,
                foothold,
                false,
                trap,
                false,
                false,
                peerServerName);
    }

    static NetLink link(String from, String to) {
        return new NetLink(from, to, false);
    }

    static NetMap map(List<Sighting> sightings, List<NetLink> links, int ceiling) {
        return new NetMap(HOME, "10.0.0.1", ceiling, List.of(HOME, SOUTH), sightings, links);
    }

    /**
     * The opening position: the player's rig and four one-hop contacts, none of them typed.
     *
     * <p>This is the picture the acceptance narrative demands a new character sees after their first
     * sweep, so it is the fixture most of the geometry assertions run against.
     */
    static NetMap opening() {
        List<Sighting> sightings = new ArrayList<>();
        List<NetLink> links = new ArrayList<>();
        sightings.add(self("10.0.0.1"));
        for (String address : List.of("10.0.0.2", "10.0.0.4", "10.0.0.6", "10.0.0.9")) {
            sightings.add(sighting(address, HostKind.UNKNOWN, 1, false, false, false, ""));
            links.add(link("10.0.0.1", address));
        }
        return map(sightings, links, 1);
    }

    /**
     * Two hops deep, with a lateral pair, a crossing and a bridge on the outermost layer.
     *
     * <p>The crossing is the point: {@code 10.0.0.9} sits below {@code 10.0.0.6} but links forward to
     * a machine above it, so its horizontal run has to pass through another edge's vertical lane.
     */
    static NetMap twoHops() {
        List<Sighting> sightings = new ArrayList<>();
        sightings.add(self("10.0.0.1"));
        sightings.add(sighting("10.0.0.2", HostKind.UNKNOWN, 1, false, false, false, ""));
        sightings.add(sighting("10.0.0.4", HostKind.TERMINAL, 1, false, true, false, ""));
        sightings.add(sighting("10.0.0.6", HostKind.UNKNOWN, 1, false, false, false, ""));
        sightings.add(sighting("10.0.0.9", HostKind.SENTRY, 1, false, false, true, ""));
        sightings.add(sighting("10.0.0.12", HostKind.BRIDGE, 2, false, false, false, SOUTH.name()));
        sightings.add(sighting("10.0.0.17", HostKind.STORE, 2, false, false, false, ""));
        sightings.add(sighting("10.0.0.21", HostKind.UNKNOWN, 2, false, false, false, ""));

        List<NetLink> links = new ArrayList<>();
        links.add(link("10.0.0.1", "10.0.0.2"));
        links.add(link("10.0.0.1", "10.0.0.4"));
        links.add(link("10.0.0.1", "10.0.0.6"));
        links.add(link("10.0.0.1", "10.0.0.9"));
        links.add(link("10.0.0.2", "10.0.0.12"));
        links.add(link("10.0.0.4", "10.0.0.17"));
        links.add(link("10.0.0.9", "10.0.0.21"));
        links.add(link("10.0.0.4", "10.0.0.6"));
        links.add(link("10.0.0.17", "10.0.0.21"));
        return map(sightings, links, 2);
    }

    /** One vantage and {@code count} one-hop contacts: a column far wider than the graph draws. */
    static NetMap crowded(int count) {
        List<Sighting> sightings = new ArrayList<>();
        List<NetLink> links = new ArrayList<>();
        sightings.add(self("10.0.0.1"));
        for (int i = 0; i < count; i++) {
            String address = "10.0.1." + (2 + i);
            sightings.add(sighting(address, HostKind.UNKNOWN, 1, false, false, false, ""));
            links.add(link("10.0.0.1", address));
        }
        return map(sightings, links, 1);
    }
}
