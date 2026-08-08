package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
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
 * The shape of one server: how deep it runs, and how wide it fans.
 *
 * <h2>What this file is for</h2>
 *
 * {@code docs/design/18-network-topology.md} §2 turned a server's internal shape from an accident
 * into a decision. The accident is worth restating, because it is what a green build looked like for
 * a year: every host attached to a uniformly chosen predecessor — a <b>random recursive tree</b> —
 * which is connected, cheap, and has a depth of about {@code log(count)} and a branch factor nobody
 * picked. {@code docs/client/09} §8 measured it over seven worlds and filed it: "layers are 1–5
 * machines wide, maps are 4–10 columns deep… fan-out does not occur at reachable depth".
 *
 * <p>⚠ Everything here is asserted over the <b>final link graph</b>, not over the generator's
 * intermediate tree. That is the only surface the rest of the game can see, and it is where the
 * chord pass could quietly undo the construction.
 */
class ServerShapeTest {

    private static final int SAMPLE = 300;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    /** Hop distance from a server's gateway to every machine on it, over the real links. */
    private static Map<String, Integer> layers(GameSave save, ServerState server) {
        Map<String, HostState> onServer = new HashMap<>();
        for (HostState host : save.topology.hosts) {
            if (host.serverId.equals(server.serverId) && !host.address.equals(save.topology.playerAddress)) {
                onServer.put(host.address, host);
            }
        }
        String gateway = onServer.values().stream()
                .filter(host -> "GATEWAY".equals(host.kind))
                .map(host -> host.address)
                .findFirst()
                .orElseThrow();

        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        depth.put(gateway, 0);
        queue.add(gateway);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (String next : onServer.get(at).links) {
                // ⚠ Confined to this server. A bridge links to a host on the far side, and following
                // it would measure the world rather than the server.
                if (onServer.containsKey(next) && !depth.containsKey(next)) {
                    depth.put(next, depth.get(at) + 1);
                    queue.add(next);
                }
            }
        }
        return depth;
    }

    private static List<ServerState> serversOf(GameSave save) {
        return new ArrayList<>(save.topology.servers);
    }

    @Nested
    @DisplayName("depth")
    class Depth {

        @Test
        @DisplayName("every server runs at least the published floor deep")
        void reachesTheFloor() {
            // The band is 4–13 and the floor is the half that matters: a server crossed in three hops
            // is crossed before the player has decided anything. The ceiling is checked below, and is
            // subject to the budget clamp.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    int deepest = layers(save, server).values().stream()
                            .mapToInt(Integer::intValue)
                            .max()
                            .orElse(0);
                    assertThat(deepest)
                            .as("world %d, server %s", i, server.serverId)
                            .isGreaterThanOrEqualTo(Balance.NET_NODE_DEPTH_MIN);
                }
            }
        }

        @Test
        @DisplayName("and never past the ceiling")
        void respectsTheCeiling() {
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    int deepest = layers(save, server).values().stream()
                            .mapToInt(Integer::intValue)
                            .max()
                            .orElse(0);
                    assertThat(deepest)
                            .as("world %d, server %s", i, server.serverId)
                            .isLessThanOrEqualTo(Balance.NET_NODE_DEPTH_MAX);
                }
            }
        }

        @Test
        @DisplayName("⚠ the chord pass does not shortcut the spine")
        void chordsArePreserving() {
            // ⚠ THE FAILURE THIS CATCHES IS SILENT AND TOTAL. The intra-server chord pass runs AFTER
            // the tree is built and adds 22% more links; unconstrained, a single chord from the
            // gateway to a deep host collapses the whole spine the server's shape was built around,
            // and nothing in the save would show it. The server-level chord rule has forbidden the
            // same thing between SERVERS since this generator was written — "a depth-skipping chord
            // re-depths a server after its machines were generated against the old depth" — and it
            // simply had nothing to apply to at the machine level until a spine existed.
            //
            // The property is the one that rule buys: no machine is closer to its gateway than the
            // floor allows, which a shortcut would break immediately.
            int worldsWithChords = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    Map<String, Integer> depth = layers(save, server);
                    int machines = depth.size();
                    int links = 0;
                    for (HostState host : save.topology.hosts) {
                        if (host.serverId.equals(server.serverId)) {
                            links += host.links.size();
                        }
                    }
                    // More links than a tree has means chords landed on this server.
                    if (links / 2 > machines - 1) {
                        worldsWithChords++;
                    }
                    assertThat(depth.values().stream()
                                    .mapToInt(Integer::intValue)
                                    .max()
                                    .orElse(0))
                            .as("world %d server %s collapsed by a chord", i, server.serverId)
                            .isGreaterThanOrEqualTo(Balance.NET_NODE_DEPTH_MIN);
                }
            }
            assertThat(worldsWithChords)
                    .as("the fixture must actually contain chorded servers, or this asserts nothing")
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("branching")
    class Branching {

        @Test
        @DisplayName("⚠ at least two machines on every server fan out — a server is never a corridor")
        void twoForks() {
            // A server that was one long chain has no choice in it, which is the argument this
            // generator already makes for rejecting a chain at the SERVER level. Two, not one,
            // because a single fork is a fork and two is a shape.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    Map<String, Integer> depth = layers(save, server);
                    Map<String, HostState> onServer = new HashMap<>();
                    for (HostState host : save.topology.hosts) {
                        if (depth.containsKey(host.address)) {
                            onServer.put(host.address, host);
                        }
                    }
                    int forks = 0;
                    for (HostState host : onServer.values()) {
                        long deeper = host.links.stream()
                                .filter(depth::containsKey)
                                .filter(next -> depth.get(next) > depth.get(host.address))
                                .count();
                        if (deeper > 1) {
                            forks++;
                        }
                    }
                    assertThat(forks)
                            .as("world %d, server %s", i, server.serverId)
                            .isGreaterThanOrEqualTo(Balance.NET_MIN_BRANCHING_NODES);
                }
            }
        }

        @Test
        @DisplayName("the fan is wide enough that the map's stack fold can fire")
        void fansOut() {
            // ⚠ THE MEASUREMENT THAT MOTIVATED ALL OF THIS. docs/client/09 §8 measured the old
            // generator at "layers 1–5 machines wide" and concluded the stack fold — built, correct
            // and gated at NET_STACK_THRESHOLD (4) — was dormant because the generator would never
            // produce a layer wide enough to fold. This asserts the fold has something to do.
            int wideLayers = 0;
            int layersSeen = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    Map<Integer, Integer> width = new HashMap<>();
                    for (int d : layers(save, server).values()) {
                        width.merge(d, 1, Integer::sum);
                    }
                    for (int w : width.values()) {
                        layersSeen++;
                        if (w >= 4) {
                            wideLayers++;
                        }
                    }
                }
            }
            assertThat(layersSeen).isPositive();
            assertThat(wideLayers / (double) layersSeen)
                    .as("share of layers at or past the map's fold threshold")
                    .isGreaterThan(0.10d);
        }

        @Test
        @DisplayName("⚠ no machine fans past the published maximum")
        void neverWiderThanTheBand() {
            // Counted over the tree edges only — deeper neighbours. A chord is a link and is not a
            // branch, and counting it would measure the wrong thing and fail for the wrong reason.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    Map<String, Integer> depth = layers(save, server);
                    for (HostState host : save.topology.hosts) {
                        if (!depth.containsKey(host.address)) {
                            continue;
                        }
                        long deeper = host.links.stream()
                                .filter(depth::containsKey)
                                .filter(next -> depth.get(next) > depth.get(host.address))
                                .count();
                        assertThat(deeper)
                                .as("world %d, %s fans %d", i, host.address, deeper)
                                .isLessThanOrEqualTo(Balance.NET_BRANCH_MAX);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("difficulty")
    class Difficulty {

        @Test
        @DisplayName("⚠ is flat within a server and steps across a bridge")
        void flatWithinAServer() {
            // design/18 §4.1. Each depth row used to spread 35/45/20 across three tiers, so one
            // server could hand a player a tier 1 and a tier 3 next door to each other and the
            // difficulty of a PLACE meant nothing. The dominant tier now takes 55%, one step below it
            // takes 40%, and 5% spills above.
            //
            // ⚠ IN AGGREGATE PER SERVER DEPTH, NOT PER SERVER, and the first version was per server
            // and could not hold. A server carries twenty-odd ordinary machines, so a 5% tail lands
            // four times instead of one on about one server in twenty — and over the ~1500 servers
            // this fixture builds, the worst of them is always extreme. A per-server bound loose
            // enough to survive that noise is too loose to say anything.
            //
            // ⚠ INFRASTRUCTURE IS EXCLUDED, and including it hid the narrowing completely. §4.1 keeps
            // the +1 on gateways, bridges and relays deliberately — the machines a player must get
            // through to make progress should not also be the softest on their server — and a lift of
            // one whole step cannot be contained by any window, so with them in, the measured spread
            // was the same before and after the table changed. Flatness is a claim about the ordinary
            // machines on a server.
            Map<Integer, Map<Integer, Integer>> byDepthAndTier = new HashMap<>();
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                Map<String, Integer> serverDepth = new HashMap<>();
                for (ServerState server : serversOf(save)) {
                    serverDepth.put(server.serverId, server.depthFromHome);
                }
                for (HostState host : save.topology.hosts) {
                    if (host.address.equals(save.topology.playerAddress)
                            || HostArchetypes.infrastructure(host.kind)) {
                        continue;
                    }
                    byDepthAndTier
                            .computeIfAbsent(serverDepth.get(host.serverId), d -> new HashMap<>())
                            .merge(host.tier, 1, Integer::sum);
                }
            }

            for (Map.Entry<Integer, Map<Integer, Integer>> row : byDepthAndTier.entrySet()) {
                Map<Integer, Integer> byTier = row.getValue();
                int machines = byTier.values().stream().mapToInt(Integer::intValue).sum();
                if (machines < 200) {
                    continue; // depth 4+ is rare enough that its sample says nothing
                }
                // Two adjacent tiers account for nearly every ordinary machine: the table puts 55% on
                // one and 40% one step below. The 5% spill is what stops a server being perfectly
                // uniform, which would be a server with nothing to find in it.
                assertThat(bestWindow(byTier, 2) / (double) machines)
                        .as("server depth %d: two adjacent tiers cover, tiers %s", row.getKey(), byTier)
                        .isGreaterThan(0.9d);
            }
        }

        /** The most machines any run of {@code width} adjacent tiers accounts for. */
        private int bestWindow(Map<Integer, Integer> byTier, int width) {
            int best = 0;
            for (int low = 1; low <= 5; low++) {
                int sum = 0;
                for (int t = low; t < low + width; t++) {
                    sum += byTier.getOrDefault(t, 0);
                }
                best = Math.max(best, sum);
            }
            return best;
        }

        @Test
        @DisplayName("and a deeper server is meaningfully harder than a shallower one")
        void deeperIsHarder() {
            // The other half, and the one the narrowing could have destroyed: flattening each row is
            // only worth doing if the ROWS still separate. Compared as means over every world, since
            // one world's depth-2 server can legitimately be no worse than another's depth-1.
            Map<Integer, List<Integer>> byDepth = new HashMap<>();
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                Map<String, Integer> serverDepth = new HashMap<>();
                for (ServerState server : serversOf(save)) {
                    serverDepth.put(server.serverId, server.depthFromHome);
                }
                for (HostState host : save.topology.hosts) {
                    if (host.address.equals(save.topology.playerAddress)) {
                        continue;
                    }
                    byDepth.computeIfAbsent(serverDepth.get(host.serverId), d -> new ArrayList<>())
                            .add(host.tier);
                }
            }
            double previous = 0;
            for (int d = 0; d <= 3; d++) {
                double mean = byDepth.getOrDefault(d, List.of()).stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElseThrow();
                assertThat(mean).as("mean tier at server depth %d", d).isGreaterThan(previous + 0.4d);
                previous = mean;
            }
        }
    }

    @Nested
    @DisplayName("what did not move")
    class Unmoved {

        @Test
        @DisplayName("every machine is still reachable from its gateway")
        void stillConnected() {
            // Connectivity used to be free — a random recursive tree attaches every host to one
            // already placed. The spine construction has to earn it, and a server with an unreachable
            // machine is a machine that cannot be found, breached or seen.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (ServerState server : serversOf(save)) {
                    Set<String> expected = new HashSet<>();
                    for (HostState host : save.topology.hosts) {
                        if (host.serverId.equals(server.serverId)
                                && !host.address.equals(save.topology.playerAddress)) {
                            expected.add(host.address);
                        }
                    }
                    assertThat(layers(save, server).keySet())
                            .as("world %d, server %s", i, server.serverId)
                            .containsExactlyInAnyOrderElementsOf(expected);
                }
            }
        }

        @Test
        @DisplayName("⚠ the same seed still builds the same world, byte for byte")
        void deterministic() {
            for (int i = 0; i < 50; i++) {
                GameSave a = NetTestKit.world(seed(i));
                GameSave b = NetTestKit.world(seed(i));
                assertThat(NetTestKit.dump(b.topology)).isEqualTo(NetTestKit.dump(a.topology));
            }
        }
    }
}
