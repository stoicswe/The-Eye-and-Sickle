package io.github.stoicswe.eyeandsickle.engine.net;

import static io.github.stoicswe.eyeandsickle.engine.support.Money.ec;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.TopologyState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The generated world's structural guarantees.
 *
 * <p>Most of these are properties rather than examples, and they are checked over thousands of seeds
 * for one reason: a generator's failures are almost never on the seed a developer happened to try.
 * The two that would be catastrophic and silent — a re-rolling world and a server whose difficulty
 * tables were applied at the wrong depth — get the largest sample.
 */
class TopologyGeneratorTest {

    /**
     * The two properties that would be catastrophic AND silent get the full sample; everything
     * else gets a smaller one.
     *
     * <p>A re-rolling world and a server generated at the wrong depth both fail invisibly — the
     * game keeps working, it is simply a different game than the one that was tuned. Those are
     * worth ten thousand seeds. A malformed link or an out-of-range tier fails loudly the first
     * time it happens, so two thousand is already generous.
     */
    private static final int SEEDS = 10_000;

    private static final int SAMPLE = 2_000;

    private static long seed(int i) {
        // Spread rather than sequential: splitmix64 decorrelates adjacent seeds well, but a test that
        // walked 0,1,2… would be sampling one narrow region of the state space and calling it random.
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    @Nested
    @DisplayName("determinism — the world is a pure function of the seed")
    class Determinism {

        @Test
        @DisplayName("the same seed builds a byte-identical world, twice")
        void sameSeedSameWorld() {
            for (int i = 0; i < 200; i++) {
                GameSave first = NetTestKit.world(seed(i));
                GameSave second = NetTestKit.world(seed(i));
                assertThat(NetTestKit.dump(second.topology)).isEqualTo(NetTestKit.dump(first.topology));
                // And the RNG lands in the same place, which is what makes everything AFTER
                // generation reproducible too.
                assertThat(second.rngSeed).isEqualTo(first.rngSeed);
            }
        }

        @Test
        @DisplayName("generation costs exactly the published number of draws")
        void drawCountIsAPureFunctionOfShape() {
            // ⚠ This is the RNG contract made checkable. The published sequence draws
            // unconditionally and discards conditionally, so the total is a pure function of
            // (server count, host counts, edge count) and nothing else. If a conditional draw ever
            // sneaks in — a rejection loop, an `if` around a nextDouble — this fails, and it fails
            // BEFORE the change ships and silently re-rolls the world of everyone who already has a
            // save. There is no cheaper place to catch that.
            for (int i = 0; i < 2_000; i++) {
                long start = seed(i);
                GameSave save = NetTestKit.world(start);
                assertThat(NetTestKit.drawsConsumed(start, save.rngSeed))
                        .as("draws for seed %d", start)
                        .isEqualTo(NetTestKit.expectedDraws(save.topology));
            }
        }

        @Test
        @DisplayName("generation never runs twice over an existing world")
        void generateIsIdempotent() {
            GameSave save = NetTestKit.world(seed(7));
            String before = NetTestKit.dump(save.topology);
            long seedBefore = save.rngSeed;

            TopologyGenerator.generate(save, NetTestKit.T0);

            // A world that could regenerate is a world a player could reroll — the same save-scumming
            // failure Rng's javadoc is written against, one level up.
            assertThat(NetTestKit.dump(save.topology)).isEqualTo(before);
            assertThat(save.rngSeed).isEqualTo(seedBefore);
        }

        @Test
        @DisplayName("the seed advances, so nothing after generation replays the same stream")
        void theSeedIsCommitted() {
            long start = seed(11);
            GameSave save = NetTestKit.world(start);
            // ⚠ Without the commit, the save still holds the seed the draws started from and the
            // entire world re-rolls on the next load. This is the single most expensive mistake
            // available in this module.
            assertThat(save.rngSeed).isNotEqualTo(start);
        }
    }

    @Nested
    @DisplayName("shape — the graph is traversable by construction")
    class Shape {

        @Test
        @DisplayName("every server is reachable from home, and stored depth survives the chords")
        void depthIsInvariantUnderChords() {
            // ⚠ THE theorem. depthFromHome is assigned from the spanning tree, before any chord
            // exists. A chord joining depths d and d+2 would shorten a BFS path and silently re-depth
            // a server AFTER its machines had been generated against the old depth — a whole server
            // one tier too hard or too soft, with nothing in the save to show it. Constraining chords
            // to |d(a) − d(b)| ≤ 1 makes the stored depth provably equal to BFS depth over the FULL
            // graph, and this is that proof, on ten thousand worlds.
            for (int i = 0; i < SEEDS; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                Map<String, Integer> bfs = serverDepths(topology);
                for (ServerState server : topology.servers) {
                    assertThat(bfs.get(server.serverId))
                            .as("BFS depth of %s on seed %d", server.serverId, seed(i))
                            .isEqualTo(server.depthFromHome);
                }
            }
        }

        @Test
        @DisplayName("every edge in the server graph joins depths that differ by at most one")
        void noEdgeSkipsADepth() {
            for (int i = 0; i < SAMPLE; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (ServerState server : topology.servers) {
                    for (String peerId : server.peerServerIds) {
                        int delta = Math.abs(server.depthFromHome - NetTestKit.server(topology, peerId).depthFromHome);
                        assertThat(delta)
                                .as("edge %s→%s", server.serverId, peerId)
                                .isLessThanOrEqualTo(1);
                    }
                }
            }
        }

        @Test
        @DisplayName("five to seven servers, never more than fifty machines on one")
        void sizeCapsHold() {
            for (int i = 0; i < 2_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                assertThat(topology.servers.size()).isBetween(Balance.NET_SERVERS_MIN, Balance.NET_SERVERS_MAX);
                for (ServerState server : topology.servers) {
                    assertThat(NetTestKit.hostsOn(topology, server.serverId).size())
                            .as("machines on %s", server.serverId)
                            .isBetween(1, Balance.NET_MACHINES_HARD_CAP);
                }
            }
        }

        @Test
        @DisplayName("every machine in the world is reachable from the player's rig")
        void nothingIsStranded() {
            // The brief asks for servers "connected so the player can traverse across them". A tree
            // gives that by construction rather than probabilistically — which is exactly why an
            // Erdős–Rényi graph was rejected: with it, "no server is unreachable" becomes a retry
            // loop instead of a guarantee.
            for (int i = 0; i < 2_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                Map<String, Integer> hops = NetTestKit.hops(topology, topology.playerAddress);
                assertThat(hops).hasSize(topology.hosts.size());
            }
        }

        @Test
        @DisplayName("every link is symmetric and nothing links to itself")
        void linksAreWellFormed() {
            for (int i = 0; i < 2_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    assertThat(host.links).doesNotContain(host.address);
                    assertThat(host.links).doesNotHaveDuplicates();
                    for (String neighbour : host.links) {
                        HostState other = NetTestKit.host(topology, neighbour);
                        assertThat(other).as("link target %s exists", neighbour).isNotNull();
                        assertThat(other.links)
                                .as("%s links back to %s", neighbour, host.address)
                                .contains(host.address);
                    }
                }
            }
        }

        @Test
        @DisplayName("addresses are unique, and the rig sits one link from home's gateway")
        void addressingIsSane() {
            for (int i = 0; i < 500; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                Set<String> addresses = new HashSet<>();
                for (HostState host : topology.hosts) {
                    assertThat(addresses.add(host.address))
                            .as("%s is unique", host.address)
                            .isTrue();
                }
                // 10.0.0.1 is a real private-range address for a real interface. Loopback was
                // rejected: 127.0.0.1 is by definition not reachable from another host, and adjacency
                // is this graph's entire subject.
                assertThat(topology.playerAddress).isEqualTo("10.0.0.1");
                HostState rig = NetTestKit.host(topology, "10.0.0.1");
                assertThat(rig.links).contains("10.0.0.2");
                assertThat(NetTestKit.host(topology, "10.0.0.2").kind).isEqualTo(HostKind.GATEWAY.name());
            }
        }

        @Test
        @DisplayName("exactly one gateway per server, always at the lowest address")
        void oneGatewayPerServer() {
            for (int i = 0; i < 1_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (ServerState server : topology.servers) {
                    List<HostState> hosts = NetTestKit.hostsOn(topology, server.serverId);
                    long gateways = hosts.stream()
                            .filter(h -> HostKind.GATEWAY.name().equals(h.kind))
                            .count();
                    assertThat(gateways).as("gateways on %s", server.serverId).isEqualTo(1);
                    assertThat(hosts.getFirst().kind).isEqualTo(HostKind.GATEWAY.name());
                }
            }
        }

        @Test
        @DisplayName("every bridge names a peer on another server, and the link is real")
        void bridgesAreTraversable() {
            // Bridge nodes are REAL, traversable objects (decision N-3), not a rendering of a server
            // edge. If this ever passes vacuously the world has no way across itself.
            int bridges = 0;
            for (int i = 0; i < 500; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    if (!HostKind.BRIDGE.name().equals(host.kind)) {
                        continue;
                    }
                    bridges++;
                    HostState peer = NetTestKit.host(topology, host.bridgePeer);
                    assertThat(peer).as("%s names a real peer", host.address).isNotNull();
                    assertThat(peer.serverId).isNotEqualTo(host.serverId);
                    assertThat(host.links).contains(peer.address);
                }
            }
            assertThat(bridges).isPositive();
        }
    }

    @Nested
    @DisplayName("ranges — nothing generated can crash a protocol type")
    class Ranges {

        @Test
        @DisplayName("tier is 1-5 and firewall tier is 0-3, on every host of every world")
        void everyValueIsInsideItsScale() {
            for (int i = 0; i < 2_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    assertThat(host.tier)
                            .as("tier of %s", host.address)
                            .isBetween(DifficultyTier.LOWEST, DifficultyTier.HIGHEST);
                    // ⚠ BreachTarget's compact constructor THROWS above 3. A fourth firewall band
                    // would not be a balance mistake, it would be an exception raised while building
                    // the target list — a save that cannot render its own network.
                    assertThat(host.firewallTier)
                            .as("firewall of %s", host.address)
                            .isBetween(0, 3);
                    assertThat(host.detectRoll).isBetween(0.0d, 1.0d);
                    assertThat(host.lootWei).isNotNegative();
                }
            }
        }

        @Test
        @DisplayName("a gateway is a signpost: no loot, no documents")
        void gatewaysPayNothing() {
            for (int i = 0; i < 1_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    if (HostKind.GATEWAY.name().equals(host.kind)) {
                        assertThat(host.lootWei).isZero();
                        assertThat(host.documentId).isEmpty();
                    }
                }
            }
        }

        @Test
        @DisplayName("only stores and sentries carry story fragments, and only known ones")
        void documentsSitWhereTheyBelong() {
            for (int i = 0; i < 2_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    if (host.documentId.isEmpty()) {
                        continue;
                    }
                    assertThat(HostArchetypes.carriesDocuments(host.kind))
                            .as("%s (%s) may carry a fragment", host.address, host.kind)
                            .isTrue();
                    assertThat(DocumentPool.known(host.documentId)).isTrue();
                }
            }
        }

        @Test
        @DisplayName("generation sets no recon flag it has not earned")
        void groundTruthNeverBecomesKnowledge() {
            // ⚠ The single most valuable assertion in this file. `defended` and `honeypot` are truth;
            // `trafficAnalyzed` and `honeypotSuspected` are the Traffic Analyzer's and the Honeypot
            // Detector's paid products. A generator that set the second pair would hand out a
            // reputation-gated tool's entire output — and via trafficAnalyzed, proof-of-skill credit
            // itself (Invariant I7), because Targets reports LIVE exactly when trafficAnalyzed &&
            // defended.
            for (int i = 0; i < 500; i++) {
                GameSave save = NetTestKit.world(seed(i));
                // Generation creates no player knowledge at all. Discovery is a sweep's job, and a
                // world that arrived pre-discovered would skip the mechanic entirely.
                assertThat(save.knownNodes).isEmpty();
                for (HostState host : save.topology.hosts) {
                    boolean rig = host.address.equals(save.topology.playerAddress);
                    assertThat(host.discovered)
                            .as("%s discovered", host.address)
                            .isEqualTo(rig);
                    assertThat(host.identified)
                            .as("%s identified", host.address)
                            .isEqualTo(rig);
                    assertThat(host.foothold).as("%s foothold", host.address).isEqualTo(rig);
                    assertThat(host.looted).isFalse();
                    assertThat(host.documentTaken).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("the gradient — deeper is harder, richer and more dangerous")
    class Gradient {

        @Test
        @DisplayName("mean difficulty rises with every hop from home")
        void depthRaisesDifficulty() {
            double[] tierSum = new double[5];
            double[] firewallSum = new double[5];
            long[] count = new long[5];
            for (int i = 0; i < 1_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    if (host.address.equals(topology.playerAddress)) {
                        continue;
                    }
                    int d = Balance.netDepth(NetTestKit.server(topology, host.serverId).depthFromHome);
                    tierSum[d] += host.tier;
                    firewallSum[d] += host.firewallTier;
                    count[d]++;
                }
            }
            // The brief: "the more bridge hops from home, the harder on average". Checked as a strict
            // ordering rather than against target means, because the means are [PROPOSAL] and the
            // ordering is the design.
            double previousTier = -1;
            double previousFirewall = -1;
            for (int d = 0; d < 5; d++) {
                if (count[d] == 0) {
                    continue; // depth 4 is reachable but not on every sample
                }
                double tier = tierSum[d] / count[d];
                double firewall = firewallSum[d] / count[d];
                assertThat(tier).as("mean tier at depth %d", d).isGreaterThan(previousTier);
                assertThat(firewall).as("mean firewall at depth %d", d).isGreaterThan(previousFirewall);
                previousTier = tier;
                previousFirewall = firewall;
            }
            assertThat(count[0]).isPositive();
            assertThat(count[3]).isPositive();
        }

        @Test
        @DisplayName("home is always the easiest place in the world, on every seed")
        void homeIsAlwaysEasiest() {
            for (int i = 0; i < SEEDS; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : NetTestKit.hostsOn(topology, NetTestKit.home(topology).serverId)) {
                    assertThat(host.tier).as("tier of %s", host.address).isLessThanOrEqualTo(2);
                    assertThat(host.firewallTier).isLessThanOrEqualTo(1);
                    assertThat(host.tarpit).isFalse();
                    assertThat(host.canaries).isFalse();
                    assertThat(host.honeypot).isFalse();
                    // N-4 made structural: the flavour layer starts one bridge out, so nothing on the
                    // early critical path can depend on it.
                    assertThat(host.documentId).isEmpty();
                }
            }
        }

        @Test
        @DisplayName("deeper servers carry more machines and more of the world's money")
        void depthRaisesReward() {
            double[] lootSum = new double[5];
            long[] count = new long[5];
            for (int i = 0; i < 1_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : topology.hosts) {
                    if (host.address.equals(topology.playerAddress)) {
                        continue;
                    }
                    int d = Balance.netDepth(NetTestKit.server(topology, host.serverId).depthFromHome);
                    // ⚠ Summed in EC rather than wei. These are statistical checks over hundreds
                    // of hosts and the array is a double[]; a wei total passes a double's exact range
                    // immediately, and the band assertions below are quoted in EC anyway.
                    lootSum[d] += ec(host.lootWei);
                    count[d]++;
                }
            }
            double previous = -1;
            for (int d = 0; d < 5; d++) {
                if (count[d] == 0) {
                    continue;
                }
                double mean = lootSum[d] / (double) count[d];
                assertThat(mean).as("mean payout at depth %d", d).isGreaterThan(previous);
                previous = mean;
            }
        }

        @Test
        @DisplayName("home's whole payout pool is roughly one Passive Sniffer and one wide sweep")
        void homeIsAnOnboardingBudgetAndNotAnIncome() {
            // ⚠ The economic argument in one number. This is a STOCK, not a flow: home is worth about
            // 68 EC once, ever, which buys the 15 EC Passive Sniffer and the 25 EC wide sweep with
            // change. docs/design/03-economy.md §5 rule 1's 70 EC/hr cap is untouched because nothing
            // here repeats. If this figure ever climbs into the hundreds, the home server has quietly
            // become a faucet.
            double total = 0;
            int worlds = 500;
            for (int i = 0; i < worlds; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : NetTestKit.hostsOn(topology, NetTestKit.home(topology).serverId)) {
                    total += ec(host.lootWei);
                }
            }
            // ⚠ Already in EC — `ec()` divides by the wei scale, so the old /100 for
            // hundredths would now be a hundredfold understatement.
            double meanEc = total / worlds;
            assertThat(meanEc).isBetween(40.0d, 130.0d);
        }
    }

    /** BFS depth over the server graph as it finally stands — tree edges and chords together. */
    private static Map<String, Integer> serverDepths(TopologyState topology) {
        Map<String, ServerState> byId = new HashMap<>();
        for (ServerState server : topology.servers) {
            byId.put(server.serverId, server);
        }
        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        depth.put(topology.homeServerId, 0);
        queue.add(topology.homeServerId);
        while (!queue.isEmpty()) {
            String at = queue.removeFirst();
            for (String peer : byId.get(at).peerServerIds) {
                if (!depth.containsKey(peer)) {
                    depth.put(peer, depth.get(at) + 1);
                    queue.addLast(peer);
                }
            }
        }
        List<String> unreachable = new ArrayList<>();
        for (ServerState server : topology.servers) {
            if (!depth.containsKey(server.serverId)) {
                unreachable.add(server.serverId);
            }
        }
        assertThat(unreachable).as("every server is reachable from home").isEmpty();
        return depth;
    }
}
