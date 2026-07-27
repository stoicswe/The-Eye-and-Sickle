package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Optional;

/**
 * The whole player-visible network: where they are, how far they can see, which servers they know of,
 * every machine they have discovered and every edge between those machines.
 *
 * <p>This is the network's equivalent of {@link ComputeBudget} and {@link BreachSnapshot}, and it is the
 * same seam for the same reason: the authoritative side computes, this module describes, and the client
 * renders two views — a graph and an exhaustive list — without ever learning whether it is talking to an
 * in-process rules engine or a home server. A view that could tell the difference is a view where single
 * player and multiplayer quietly become different games.
 *
 * <h2>Empty, never null, all the way down</h2>
 *
 * Every list is copied and every absent value is a blank rather than a null, because the consumer is
 * always a renderer and a renderer that throws mid-repaint is a worse answer than a blank panel.
 * {@link #empty()} is the shape a session with nothing to say returns — notably
 * {@code RemoteGameSession}, which has no transport, no OAuth flow and no reconnect loop today (CL-8),
 * and must therefore be able to answer honestly rather than plausibly.
 *
 * <h2>{@code hopCeiling} is transmitted, never derived</h2>
 *
 * The client is told how far it can see; it has no way to work the number out and no business trying.
 * Reach is a <strong>hard ceiling</strong> raised only by a schematic-gated tool —
 * {@code docs/design/07-recon-tools.md} §2 makes the Topology Mapper "a <em>ceiling</em> on information
 * (1 hop → 2 hops), hence schematic-gated not purchasable (Invariant I2)". Sensitivity is what ethecoin
 * buys, and no amount of it moves this field. Putting the ceiling on the wire rather than deriving it
 * client-side means there is no code path from a purchase to this number, which is I2 satisfied
 * structurally rather than by review.
 *
 * <p>It is on the wire at all because the map prints it: a player who cannot see their own horizon
 * cannot tell an empty region from an unreachable one, and those two call for opposite decisions —
 * sweep harder, or go and stand somewhere else.
 *
 * <h2>There is no count of what is not here</h2>
 *
 * The map holds only what the player has discovered. It reports no total, no per-server population, and
 * no "N nearby" hint; the sole aggregate the whole vocabulary permits about undetected machines is
 * {@link SweepReport#inRange()}, which reports one sweep's own sensitivity rather than the network's
 * contents. Everything else about a machine the player has not found is absent by construction — see
 * {@link Sighting}.
 *
 * @param currentServer the server the player is working from; blank when nothing is known
 * @param vantageAddress where the player is operating from — every {@link Sighting#hopsFromVantage()} is
 *     measured from here, and moving it is what traversal <em>is</em>
 * @param hopCeiling how many hops the player's instruments reach, at least 1
 * @param knownServers every server the player has heard of, including through a bridge's advertisement
 * @param sightings every machine the player has discovered, and only those
 * @param links every edge between two discovered machines
 */
public record NetMap(
        ServerRef currentServer,
        String vantageAddress,
        int hopCeiling,
        List<ServerRef> knownServers,
        List<Sighting> sightings,
        List<NetLink> links) {

    /**
     * What {@code currentServer} reads as before anything is known. Home is the truthful default for a
     * world nobody has left: a player with no map has not crossed a bridge.
     */
    private static final ServerRef UNKNOWN_SERVER = new ServerRef("", "", 0, true);

    public NetMap {
        currentServer = currentServer == null ? UNKNOWN_SERVER : currentServer;
        vantageAddress = vantageAddress == null ? "" : vantageAddress;

        // List.copyOf both freezes the list against a producer that keeps mutating its own state and
        // rejects null elements outright, which is why "unknown" is "" everywhere in this vocabulary
        // and never a null entry.
        knownServers = List.copyOf(knownServers == null ? List.of() : knownServers);
        sightings = List.copyOf(sightings == null ? List.of() : sightings);
        links = List.copyOf(links == null ? List.of() : links);

        // A ceiling of 0 is not "sees nothing"; it is a network view that can never contain anything
        // but the vantage itself, i.e. an instrument that has been sold to the player and does not
        // function. The floor belongs here rather than in a rules module because it is the domain of
        // the field, not a balance value: what the ceiling actually is, and what moves it, is decided
        // by the authoritative side and arrives already computed.
        if (hopCeiling < 1) {
            throw new IllegalArgumentException("hop ceiling must be >= 1");
        }
    }

    /**
     * A map with nothing on it.
     *
     * @return a map holding no servers, no machines and no edges, with the smallest legal horizon
     */
    public static NetMap empty() {
        return new NetMap(UNKNOWN_SERVER, "", 1, List.of(), List.of(), List.of());
    }

    /**
     * The machine at an address, if the player has discovered it.
     *
     * <p>Empty is the answer for an address the player has not found <em>and</em> for one that does not
     * exist, deliberately and indistinguishably. A lookup that could tell "no such machine" from "not
     * discovered yet" would be a free probe: type an address, learn whether something is there.
     *
     * @param address the address to look for; {@code null} and {@code ""} find nothing
     * @return the sighting, or empty
     */
    public Optional<Sighting> at(String address) {
        if (address == null || address.isEmpty()) {
            return Optional.empty();
        }
        for (Sighting sighting : sightings) {
            if (sighting.address().equals(address)) {
                return Optional.of(sighting);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether there is anything to draw. True for {@link #empty()}, and true for a session that has
     * nothing to report — which is a state a view must render as an instruction rather than as a blank,
     * since a player looking at an empty network needs to be told that sweeping is what fills it.
     */
    public boolean isEmpty() {
        return sightings.isEmpty();
    }
}
