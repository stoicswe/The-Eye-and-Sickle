package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.ServerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared fixtures for the network tests, in the shape {@code BreachTestKit} already established.
 *
 * <p>Every helper takes an explicit seed and an explicit clock. Neither is a convenience: this module
 * is a pure function of {@code (save, clock)}, and a fixture that reached for {@code Instant.now()} or
 * for an unseeded generator would make a failure reproducible only on the machine that saw it.
 */
final class NetTestKit {

    private NetTestKit() {}

    static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    /**
     * splitmix64's golden-ratio increment, restated from {@code Rng}.
     *
     * <p>Copied rather than exposed because it is an implementation detail of the generator and the
     * test that needs it — {@link #drawsConsumed} — is the only thing in the codebase that should
     * care. If {@code Rng} ever changes its step, that test failing is the correct outcome.
     */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    /**
     * A new character with a fixed seed and a generated world.
     *
     * <p>⚠ <b>The {@code topology = null} line is load-bearing and must not be tidied away.</b>
     * {@code SoloGame.newCharacter} is due to gain {@code s.rngSeed = Rng.derive(s.characterId, now)}
     * followed by {@code TopologyGenerator.generate(s, now)} — that is the integrator's job and it is
     * the only place a real character's world is built. The moment it lands, a character arrives here
     * already carrying a world, {@code generate} takes its idempotence guard and returns without
     * drawing, and the seed assigned on the next line stops meaning anything.
     *
     * <p>The failure that produces is worth spelling out, because it is not the one it looks like:
     * {@code characterId} is a fresh {@code UUID.randomUUID()}, so every world would be a
     * <em>different</em> random world rather than one fixed one — {@code sameSeedSameWorld} would
     * fail comparing two calls that were never given the same seed, and {@code theSeedIsCommitted}
     * would fail because nothing drew. Clearing the field first makes this fixture correct both
     * before and after that edit, and asks nothing of whoever makes it.
     */
    static SoloSave world(long seed) {
        SoloSave save = SoloGame.newCharacter("operator", T0);
        save.topology = null;
        save.rngSeed = seed;
        TopologyGenerator.generate(save, T0);
        return save;
    }

    /** Grants a purchasable sweep tier, the way the market would. */
    static void grant(SoloSave save, SweepTier tier) {
        ItemState item = new ItemState();
        item.itemType = tier.itemId();
        item.displayName = tier.label();
        item.acquiredAt = T0;
        save.items.add(item);
    }

    /** Runs a whole sweep — commission, then settle — and returns what it reported. */
    static SweepReport sweep(SoloSave save, SweepTier tier, Instant now) {
        Optional<TaskState> task = NetRules.beginSweep(save, tier, now);
        if (task.isEmpty()) {
            throw new IllegalStateException("sweep refused: tier " + tier + " not owned, or no compute");
        }
        save.tasks.remove(task.get());
        return NetRules.settleSweep(save, task.get(), now);
    }

    /**
     * How many values the generator drew, recovered exactly from the seed's movement.
     *
     * <p>splitmix64 steps its state by a constant, so the number of draws is
     * {@code (final − initial) × GAMMA⁻¹} in unsigned 64-bit arithmetic. That makes an exact draw
     * count checkable without hard-coding a magic number nobody can verify — and an exact draw count
     * is the strongest available statement of the RNG contract: it fails the moment a conditional
     * draw sneaks in, which is the failure that silently re-rolls every existing player's world.
     */
    static long drawsConsumed(long initialSeed, long finalSeed) {
        return (finalSeed - initialSeed) * inverseGamma();
    }

    /** The multiplicative inverse of {@link #GAMMA} mod 2⁶⁴, by Newton–Hensel lifting. */
    private static long inverseGamma() {
        long x = GAMMA; // correct to 3 bits for an odd input
        for (int i = 0; i < 6; i++) {
            x *= 2 - GAMMA * x; // doubles the number of correct bits each round
        }
        return x;
    }

    /**
     * The draw count the published sequence says a world of this shape must have cost.
     *
     * <p>Step 1: one for the server count. Step 2: three per server after home. Step 3: one per
     * unordered server pair, always. Step 4: one for the machine count, one reserved padding draw,
     * {@code n − 1} for the intra-server tree, and two per host for the extra links. Step 5: two per
     * server-graph edge. Step 7: exactly ten per host. Steps 6 and 8 take none.
     */
    static long expectedDraws(TopologyState topology) {
        int servers = topology.servers.size();
        long draws = 1L + 3L * (servers - 1) + (long) servers * (servers - 1) / 2;

        Map<String, Integer> perServer = new HashMap<>();
        int hostsTotal = 0;
        for (HostState host : topology.hosts) {
            if (host.address.equals(topology.playerAddress)) {
                continue; // the rig is not on the server grid and costs nothing
            }
            perServer.merge(host.serverId, 1, Integer::sum);
            hostsTotal++;
        }
        for (ServerState server : topology.servers) {
            int n = perServer.getOrDefault(server.serverId, 0);
            draws += 1L + 1L + (n - 1L) + 2L * n;
        }

        int edges = 0;
        for (ServerState server : topology.servers) {
            edges += server.peerServerIds.size();
        }
        draws += 2L * (edges / 2);

        return draws + 10L * hostsTotal;
    }

    /** Every host on a server, in generation order. */
    static List<HostState> hostsOn(TopologyState topology, String serverId) {
        List<HostState> out = new ArrayList<>();
        for (HostState host : topology.hosts) {
            if (host.serverId.equals(serverId) && !host.address.equals(topology.playerAddress)) {
                out.add(host);
            }
        }
        return out;
    }

    static ServerState home(TopologyState topology) {
        for (ServerState server : topology.servers) {
            if (server.home) {
                return server;
            }
        }
        throw new IllegalStateException("no home server");
    }

    static ServerState server(TopologyState topology, String serverId) {
        for (ServerState server : topology.servers) {
            if (server.serverId.equals(serverId)) {
                return server;
            }
        }
        throw new IllegalStateException("no server " + serverId);
    }

    static HostState host(TopologyState topology, String address) {
        for (HostState host : topology.hosts) {
            if (host.address.equals(address)) {
                return host;
            }
        }
        return null;
    }

    /** Hop distance from {@code from} over every link. The same BFS the rules use. */
    static Map<String, Integer> hops(TopologyState topology, String from) {
        Map<String, HostState> index = new HashMap<>();
        for (HostState host : topology.hosts) {
            index.put(host.address, host);
        }
        return TopologyGenerator.bfs(index, from);
    }

    /**
     * A canonical, order-stable text rendering of a whole world.
     *
     * <p>Used to assert that two generations from one seed are identical. A field-by-field comparison
     * would pass the day somebody adds a field and forgets to compare it; a dump fails.
     */
    static String dump(TopologyState topology) {
        StringBuilder out = new StringBuilder();
        out.append(topology.homeServerId).append('/')
                .append(topology.playerAddress).append('/')
                .append(topology.vantageAddress).append('\n');
        for (ServerState server : topology.servers) {
            out.append(server.serverId).append(' ')
                    .append(server.name).append(' ')
                    .append(server.depthFromHome).append(' ')
                    .append(server.home).append(' ')
                    .append(String.join(",", server.peerServerIds)).append('\n');
        }
        for (HostState host : topology.hosts) {
            out.append(host.address).append(' ')
                    .append(host.label).append(' ')
                    .append(host.serverId).append(' ')
                    .append(host.kind).append(' ')
                    .append(host.signal).append(' ')
                    .append(host.tier).append(' ')
                    .append(host.firewallTier).append(' ')
                    .append(host.tarpit).append(host.canaries).append(host.defended).append(host.honeypot)
                    .append(' ').append(host.detectRoll)
                    .append(' ').append(host.lootWei)
                    .append(' ').append(host.documentId)
                    .append(' ').append(host.bridgePeer)
                    .append(' ').append(String.join(",", host.links))
                    .append('\n');
        }
        return out.toString();
    }
}
