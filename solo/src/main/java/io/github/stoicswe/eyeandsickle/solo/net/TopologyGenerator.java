package io.github.stoicswe.eyeandsickle.solo.net;

import java.math.BigInteger;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.ServerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the world once, from the save's seed, and never again.
 *
 * <h2>Shape: a depth-biased spanning tree over 5–7 servers, plus at most two depth-preserving chords</h2>
 *
 * The alternatives were considered and each fails for a specific reason. A <b>chain</b> gives one
 * path and no choice; depth is forced rather than chosen, and a single unlucky bridge placement
 * soft-locks the run. A <b>ring</b> makes depth from home ambiguous — two directions — and a seven-ring
 * caps depth at three, so the "deeper is more dangerous" gradient has nowhere to go. A <b>full mesh or
 * Erdős–Rényi graph</b> makes connectivity probabilistic, which turns "no server is unreachable" into a
 * retry loop instead of a construction. A <b>tree plus chords</b> gives connectivity by construction —
 * every server attaches to one already placed — an unambiguous depth, real branching so the player
 * chooses which way to push, and enough extra edges that the result reads as a network rather than a
 * taxonomy.
 *
 * <p>⚠ <b>The chord depth rule is load-bearing.</b> A chord between servers at depths {@code d} and
 * {@code d+2} would shorten a BFS path and silently re-depth a server <em>after</em> its machines had
 * already been generated against the old depth — a whole server one tier too hard or too soft, with
 * nothing in the save to show it. Constraining chords to {@code |d(a) − d(b)| ≤ 1} makes BFS depth
 * provably invariant under chord addition, and {@code TopologyGeneratorTest} checks that over ten
 * thousand seeds rather than trusting the argument.
 *
 * <h2>⚠ The RNG contract: draw unconditionally, discard conditionally</h2>
 *
 * Every loop below draws a fixed number of values per iteration, whether or not the values are used.
 * A conditional draw makes the stream's <em>shape</em> depend on the code path, and a replay from a
 * stored seed then depends on the code as well as the seed — which is the one thing {@code Rng} exists
 * to guarantee. So a host that is structurally a {@code GATEWAY} still rolls a kind and discards it;
 * the chord pass draws for every unordered pair including adjacent ones; and step 4 spends one draw on
 * nothing at all, reserving a slot so a future per-server property can be added without shifting every
 * downstream host's stream. <b>Do not remove the padding draw.</b>
 *
 * <p>The total number of draws is therefore a pure function of {@code (nServers, hostCounts,
 * edgeCount)}, each itself determined by earlier draws — so a fixed seed produces a byte-identical
 * world, and {@code save.rngSeed} after generation is a fixed known value. Both are tested.
 *
 * <h2>Detection is decided here, once</h2>
 *
 * {@link HostState#detectRoll} is drawn during generation and never re-drawn. A sweep compares it
 * against a threshold; it never rolls. That is what makes re-sweeping useless and save-scumming
 * pointless <em>by construction</em> rather than by cooldown — see {@code NetRules} §the sweep, and
 * {@code Rng}'s own javadoc for the same argument one level down.
 *
 * <h2>The home floor is a guarantee, not a tuning</h2>
 *
 * Step 8 runs after every roll and takes no draws. It is the fix for "discovery is unusable at the
 * start": whatever the seed did, the player's rig ends up with at least five neighbours, at least
 * three of which are tier-1, un-firewalled, undefended machines with {@code detectRoll = 0.0} — below
 * the base sweep's worst threshold — and a payout floor. The first sweep a new character runs always
 * returns at least three workable targets. Always, on every seed.
 */
public final class TopologyGenerator {

    private TopologyGenerator() {}

    /**
     * Generates the world into {@code save.topology}.
     *
     * <p>Idempotent by guard: returns immediately when a topology already exists, because
     * regenerating one would let a player reroll the world by any path that reached this method
     * twice. Draws from {@code Rng.of(save)} and <b>commits before returning</b> — without the commit
     * the save still holds the seed the draws started from and the entire world re-rolls on the next
     * load, which is the single most expensive mistake available in this module.
     *
     * @param now the session clock, used for every timestamp written; never {@code Instant.now()}
     */
    public static void generate(SoloSave save, Instant now) {
        if (save == null || save.topology != null) {
            return;
        }
        Rng rng = Rng.of(save);
        TopologyState topology = new TopologyState();

        // ── STEP 1: server count ───────────────────────────────────────────────────────── 1 draw
        int serverCount = Balance.NET_SERVERS_MIN
                + rng.nextInt(Balance.NET_SERVERS_MAX - Balance.NET_SERVERS_MIN + 1);

        // ── STEP 2: the spanning tree ──────────────────────────────── 3 draws per server after home
        int[] depth = new int[serverCount];
        boolean[][] treeEdge = new boolean[serverCount][serverCount];
        for (int i = 1; i < serverCount; i++) {
            // Recomputed each iteration, in ascending index order, from already-placed servers only.
            // Deterministic and draw-free — it must be, or the parent choice below would consume a
            // variable number of values.
            List<Integer> deepest = deepestIndices(depth, i);

            double mode = rng.nextDouble();
            int deepPick = rng.nextInt(deepest.size());
            int anyPick = rng.nextInt(i);

            int parent = mode < Balance.NET_SERVER_DEEPEN_BIAS ? deepest.get(deepPick) : anyPick;
            depth[i] = depth[parent] + 1;
            treeEdge[parent][i] = true;
            treeEdge[i][parent] = true;
        }

        // ── STEP 3: chords ───────────────────────── 1 draw per unordered pair, always, in order
        //
        // Adjacency is evaluated against the TREE ONLY, frozen before this loop starts, so the draw
        // count is a pure function of serverCount rather than of which chords happened to be taken.
        boolean[][] edge = copyOf(treeEdge);
        int chords = 0;
        for (int a = 0; a < serverCount; a++) {
            for (int b = a + 1; b < serverCount; b++) {
                double u = rng.nextDouble();
                if (treeEdge[a][b]) {
                    continue;
                }
                // The load-bearing constraint. See the class note: a depth-skipping chord re-depths a
                // server after its machines were generated against the old depth.
                if (Math.abs(depth[a] - depth[b]) > 1) {
                    continue;
                }
                if (chords >= Balance.NET_SERVER_CHORD_MAX) {
                    continue;
                }
                if (u < Balance.NET_SERVER_CHORD_CHANCE) {
                    edge[a][b] = true;
                    edge[b][a] = true;
                    chords++;
                }
            }
        }

        for (int s = 0; s < serverCount; s++) {
            ServerState server = new ServerState();
            server.serverId = HostArchetypes.serverId(s);
            server.name = HostArchetypes.serverName(s);
            server.depthFromHome = depth[s];
            server.home = s == 0;
            topology.servers.add(server);
        }
        topology.homeServerId = topology.servers.getFirst().serverId;
        for (int a = 0; a < serverCount; a++) {
            for (int b = 0; b < serverCount; b++) {
                if (a != b && edge[a][b]) {
                    topology.servers.get(a).peerServerIds.add(HostArchetypes.serverId(b));
                }
            }
        }

        // ── STEP 4: machines per server ──────────────── 1 + 1 + (n-1) + 2n draws for n machines
        List<List<HostState>> grid = new ArrayList<>();
        Map<String, HostState> byAddress = new HashMap<>();
        for (int s = 0; s < serverCount; s++) {
            int lo = Balance.netMachinesMin(depth[s]);
            int hi = Balance.netMachinesMax(depth[s]);
            int count = lo + rng.nextInt(hi - lo + 1);

            // ⚠ The reserved padding draw. It buys room for a future per-server property without
            // shifting every downstream host's stream, which would otherwise re-roll the whole world
            // for everyone the moment anything is added here. Document it; do not remove it.
            rng.nextInt(1);

            // A no-op against the published table, kept because the brief's cap is a hard promise and
            // a table edit is one line away from breaking it.
            count = Math.min(count, Balance.NET_MACHINES_HARD_CAP);

            String serverId = HostArchetypes.serverId(s);
            String serverName = HostArchetypes.serverName(s);
            List<HostState> hosts = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                HostState host = new HostState();
                host.address = address(s, j);
                host.label = HostArchetypes.hostLabel(serverName, j);
                host.serverId = serverId;
                hosts.add(host);
                byAddress.put(host.address, host);
            }
            // Exactly one gateway per server, always host index 0, always the lowest address on it.
            hosts.getFirst().kind = HostKind.GATEWAY.name();

            // A spanning tree over the machines: every host attaches to an already-placed one, so the
            // server is connected by construction and never by a retry loop.
            for (int j = 1; j < count; j++) {
                link(hosts.get(j), hosts.get(rng.nextInt(j)));
            }
            // Then the extra links, which are what make a foothold open more than one direction.
            for (int j = 0; j < count; j++) {
                double u = rng.nextDouble();
                int v = rng.nextInt(count);
                boolean wanted = u < Balance.NET_INTRA_CHORD_CHANCE;
                if (wanted && v != j && !hosts.get(j).links.contains(hosts.get(v).address)) {
                    link(hosts.get(j), hosts.get(v));
                }
            }
            grid.add(hosts);
        }

        // ── STEP 5: bridges ──────────────────────────────── 2 draws per server-graph edge, in order
        for (int a = 0; a < serverCount; a++) {
            for (int b = a + 1; b < serverCount; b++) {
                if (!edge[a][b]) {
                    continue;
                }
                List<HostState> left = grid.get(a);
                List<HostState> right = grid.get(b);
                int ia = rng.nextInt(left.size());
                int ib = rng.nextInt(right.size());
                // Never demote a gateway: a server with no gateway has no signpost, and the archetype
                // table promises exactly one per server at index 0.
                if (ia == 0) {
                    ia = Math.min(1, left.size() - 1);
                }
                if (ib == 0) {
                    ib = Math.min(1, right.size() - 1);
                }
                HostState from = left.get(ia);
                HostState to = right.get(ib);
                from.kind = HostKind.BRIDGE.name();
                to.kind = HostKind.BRIDGE.name();
                // ⚠ One host can be picked for two edges; the later assignment wins the ADVERTISED
                // peer while both links survive. See HostState#bridgePeer — the consequence is
                // cosmetic, because nothing routes on this field.
                from.bridgePeer = to.address;
                to.bridgePeer = from.address;
                link(from, to);
            }
        }

        // ── STEP 6: the player's rig ─────────────────────────────────────────────────── 0 draws
        HostState rig = new HostState();
        rig.address = topology.playerAddress;
        rig.label = "localhost";
        rig.serverId = topology.homeServerId;
        rig.kind = HostKind.SELF.name();
        rig.signal = HostArchetypes.baseSignal(rig.kind);
        // Known from the first second and never a sweep candidate — both, so no code path can count
        // the player's own machine as something they found.
        rig.discovered = true;
        rig.identified = true;
        rig.foothold = true;
        link(rig, grid.getFirst().getFirst());
        byAddress.put(rig.address, rig);

        // ── STEP 7: the per-host property block ─────── exactly 10 draws per host, canonical order
        //
        // Server index ascending, then host index ascending, every host, including the gateways and
        // bridges whose kind is already fixed. The rig is not on this grid and takes no draws at all.
        String[][] rolledKind = new String[serverCount][];
        for (int s = 0; s < serverCount; s++) {
            List<HostState> hosts = grid.get(s);
            rolledKind[s] = new String[hosts.size()];
            for (int j = 0; j < hosts.size(); j++) {
                double uKind = rng.nextDouble();
                double uTier = rng.nextDouble();
                double uFw = rng.nextDouble();
                double uTarpit = rng.nextDouble();
                double uCanary = rng.nextDouble();
                double uDefended = rng.nextDouble();
                double uHoneypot = rng.nextDouble();
                double uDoc = rng.nextDouble();
                double detectRoll = rng.nextDouble();
                double uLoot = rng.nextDouble();

                HostState host = hosts.get(j);
                int d = depth[s];

                // Rolled for every host and kept only where structure has not already spoken. Held
                // separately because the route floor may need to put a promoted bridge's predecessor
                // back to what it would have been.
                rolledKind[s][j] = Balance.netHostKind(d, uKind);
                boolean structural = HostKind.GATEWAY.name().equals(host.kind)
                        || HostKind.BRIDGE.name().equals(host.kind);
                if (!structural) {
                    host.kind = rolledKind[s][j];
                }

                host.tier = Balance.netTier(d, uTier);
                if (HostArchetypes.infrastructure(host.kind)) {
                    // "depth mean +1" — the two hosts a player must get through to make progress are
                    // not also the softest things on their server.
                    host.tier = Math.min(5, host.tier + 1);
                }
                host.firewallTier = Balance.netFirewallTier(d, uFw);
                host.tarpit = uTarpit < Balance.netTarpitChance(d);
                host.canaries = uCanary < Balance.netCanaryChance(d);

                // ⚠ Ground truth only. NodeState.trafficAnalyzed and NodeState.honeypotSuspected are
                // the Traffic Analyzer's and the Honeypot Detector's products; setting either here
                // would hand out a gated tool's entire output, and with `defended` it would hand out
                // proof-of-skill credit (Invariant I7).
                host.defended = uDefended < Balance.netDefendedChance(d);
                host.honeypot = uHoneypot < Balance.netHoneypotChance(d);

                host.signal = HostArchetypes.baseSignal(host.kind);
                host.detectRoll = detectRoll;

                if (HostArchetypes.carriesDocuments(host.kind) && uDoc < Balance.netDocumentChance(d)) {
                    host.documentId = DocumentPool.forAddress(host.address);
                }
                host.lootWei = HostArchetypes.carriesLoot(host.kind)
                        ? Balance.netLootWei(host.tier, uLoot)
                        : java.math.BigInteger.ZERO;
            }
        }

        // ── STEP 8: the home floor ───────────────────────────────────────────────────── 0 draws
        applyHomeFloor(grid.getFirst(), rig, byAddress, rolledKind[0], topology);

        topology.hosts.add(rig);
        for (List<HostState> hosts : grid) {
            topology.hosts.addAll(hosts);
        }
        topology.vantageAddress = rig.address;

        // ⚠ MANDATORY. Without it the save still holds the seed the draws started from and the whole
        // world re-rolls on the next load.
        rng.commit(save);
        save.topology = topology;
    }

    // ================================================================== the home floor (§1.7)

    /**
     * The anti-dead-end guarantee, applied deterministically after every roll.
     *
     * <p>Five steps, no draws, and each one closes a way a seed could hand a new player an unplayable
     * opening. Together they are what makes the acceptance narrative true on <em>every</em> seed
     * rather than on most of them, which is the difference between a guarantee and a tuning.
     */
    private static void applyHomeFloor(
            List<HostState> homeHosts,
            HostState rig,
            Map<String, HostState> byAddress,
            String[] rolledKind,
            TopologyState topology) {

        // 1. Clamp the whole home server. Home is where the game teaches, and a tier-2 firewall or a
        //    tarpit on the first machine a player ever breaches teaches them that the breach is
        //    unwinnable rather than that it is a puzzle.
        for (HostState host : homeHosts) {
            host.tier = Math.min(host.tier, 2);
            host.firewallTier = Math.min(host.firewallTier, 1);
            host.honeypot = false;
            host.tarpit = false;
            host.canaries = false;
            host.documentId = "";
        }

        // 2. Contact floor. The three machines a new player is guaranteed to find on their first
        //    sweep, forced to be workable: detectRoll 0.0 is below the base sweep's WORST threshold
        //    (0.35 — a quiet machine at one hop), so they are found whatever the seed did.
        //
        //    ⚠ Gateways AND bridges are skipped, and the eligible hosts are LINKED to the rig rather
        //    than selected from those that happen to be linked already. Two reasons, both of which
        //    are bugs in the obvious version. The gateway is the lowest address on the server and
        //    always one link from the rig, so "the first three at one link" would force the server's
        //    only signpost into a TERMINAL — and the acceptance narrative shows the gateway found at
        //    T2 alongside three T1 contacts. A bridge is worse: demoting one to a TERMINAL would
        //    leave a cross-server link on a machine that no longer claims to have one, which the map
        //    would render as a lie. Home has 12–20 machines and at most one gateway and three
        //    bridges, so at least eight are always eligible.
        //
        //    ⚠ "Ascending address order" is INDEX order, not lexicographic string order: as strings,
        //    "10.0.0.10" sorts before "10.0.0.2". Iterating the generated list is the correct reading
        //    and the one that cannot silently drift.
        List<HostState> contacts = new ArrayList<>();
        for (HostState host : homeHosts) {
            if (contacts.size() >= Balance.NET_HOME_GUARANTEED_CONTACTS) {
                break;
            }
            if (HostKind.GATEWAY.name().equals(host.kind) || HostKind.BRIDGE.name().equals(host.kind)) {
                continue;
            }
            contacts.add(host);
            link(rig, host);
            host.detectRoll = 0.0d;
            host.kind = HostKind.TERMINAL.name();
            host.signal = SignalStrength.MODERATE.name();
            host.tier = 1;
            host.firewallTier = 0;
            host.defended = false;
            host.looted = false;
            host.bridgePeer = "";
            host.lootWei = host.lootWei.max(Balance.NET_LOOT_FLOOR_WEI);
        }

        // 3. Neighbour floor. Whatever the intra-server tree did, the rig ends up one link from at
        //    least five machines — enough that the base sweep has something to MISS as well as
        //    something to find, which is what teaches that sensitivity is a purchase rather than a
        //    formality. Applied after the contact floor so the three guaranteed contacts count
        //    towards the five rather than being added on top of them.
        for (int j = 1; j < homeHosts.size() && rig.links.size() < Balance.NET_HOME_SEED_NEIGHBOURS; j++) {
            HostState candidate = homeHosts.get(j);
            if (!rig.links.contains(candidate.address)) {
                link(rig, candidate);
            }
        }

        // 4. Route floor. A way out of home has to be within reach of the opening position, or a
        //    player who has cleared their neighbourhood has nowhere to go and no way to see that they
        //    have nowhere to go.
        if (!hasNearbyBridge(homeHosts, rig, byAddress)) {
            HostState promoted = promotionTarget(homeHosts, rig, byAddress, contacts);
            HostState demoted = firstBridge(homeHosts);
            if (promoted != null && demoted != null) {
                repointBridge(demoted, promoted, byAddress, topology, rolledKind, homeHosts);
            }
        }

        // 5. Counter-hack floor. Nothing to do here: Balance.netCounterHackChance(0) returns the named
        //    constant NET_COUNTER_HACK_HOME, which is zero, and a test asserts the constant rather
        //    than the table row. A player who has never left home is never counter-hacked.
    }

    /** Whether any bridge on home is within two links of the rig. */
    private static boolean hasNearbyBridge(List<HostState> homeHosts, HostState rig, Map<String, HostState> byAddress) {
        Map<String, Integer> hops = bfs(byAddress, rig.address);
        for (HostState host : homeHosts) {
            if (HostKind.BRIDGE.name().equals(host.kind) && hops.getOrDefault(host.address, Integer.MAX_VALUE) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * The nearest home host that may be promoted to a bridge: not the gateway, not one of the
     * guaranteed contacts, and within two links.
     *
     * <p>⚠ The contacts are excluded because step 3 has already promised what they are — tier 1,
     * {@code TERMINAL}, unfirewalled — and a promotion would overwrite two of those. The neighbour
     * floor guarantees at least five hosts at one link, of which at most one is the gateway and three
     * are contacts, so a candidate always exists at hop 1.
     */
    private static HostState promotionTarget(
            List<HostState> homeHosts, HostState rig, Map<String, HostState> byAddress, List<HostState> contacts) {
        Map<String, Integer> hops = bfs(byAddress, rig.address);
        for (int wanted = 1; wanted <= 2; wanted++) {
            for (HostState host : homeHosts) {
                if (HostKind.GATEWAY.name().equals(host.kind) || contacts.contains(host)) {
                    continue;
                }
                if (hops.getOrDefault(host.address, Integer.MAX_VALUE) == wanted) {
                    return host;
                }
            }
        }
        return null;
    }

    private static HostState firstBridge(List<HostState> homeHosts) {
        for (HostState host : homeHosts) {
            if (HostKind.BRIDGE.name().equals(host.kind)) {
                return host;
            }
        }
        return null;
    }

    /**
     * Moves every cross-server link off {@code from} and onto {@code to}.
     *
     * <p>All of them, not just the advertised one. A host can be the endpoint of two server edges, and
     * moving one while leaving the other would leave a demoted host still holding a link to another
     * server — a cross-server edge on a machine that no longer claims to be a bridge, which is exactly
     * the kind of quiet inconsistency the map would render as a lie.
     */
    private static void repointBridge(
            HostState from,
            HostState to,
            Map<String, HostState> byAddress,
            TopologyState topology,
            String[] rolledKind,
            List<HostState> homeHosts) {

        String homeServerId = topology.homeServerId;
        List<String> crossServer = new ArrayList<>();
        for (String address : from.links) {
            HostState peer = byAddress.get(address);
            if (peer != null && !homeServerId.equals(peer.serverId)) {
                crossServer.add(address);
            }
        }
        if (crossServer.isEmpty()) {
            return;
        }
        for (String address : crossServer) {
            HostState peer = byAddress.get(address);
            unlink(from, peer);
            link(to, peer);
            peer.bridgePeer = to.address;
        }
        from.bridgePeer = "";
        // Put the demoted host back to the kind it originally rolled, so a promotion does not also
        // quietly change the archetype mix of the home server.
        int index = homeHosts.indexOf(from);
        String restored = index >= 0 && index < rolledKind.length && rolledKind[index] != null
                ? rolledKind[index]
                : HostKind.TERMINAL.name();
        from.kind = restored;
        from.signal = HostArchetypes.baseSignal(from.kind);

        to.kind = HostKind.BRIDGE.name();
        to.bridgePeer = crossServer.getFirst();
        to.signal = HostArchetypes.baseSignal(to.kind);
        // Infrastructure sits a tier above its neighbours, then the home clamp applies again.
        to.tier = Math.min(2, to.tier + 1);
    }

    // ================================================================== small helpers

    /**
     * Hop distance from {@code from} over every link, ignoring discovery.
     *
     * <p>Undiscovered machines still conduct. A host the player has never seen is still the reason a
     * further one is two hops away rather than three, and a BFS over the discovered subgraph would
     * make the hop ceiling widen as the player learned things — which would be reach for free.
     */
    static Map<String, Integer> bfs(Map<String, HostState> byAddress, String from) {
        Map<String, Integer> hops = new HashMap<>();
        HostState start = byAddress.get(from);
        if (start == null) {
            return hops;
        }
        hops.put(from, 0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            String at = queue.removeFirst();
            int next = hops.get(at) + 1;
            HostState host = byAddress.get(at);
            if (host == null) {
                continue;
            }
            for (String neighbour : host.links) {
                if (byAddress.containsKey(neighbour) && !hops.containsKey(neighbour)) {
                    hops.put(neighbour, next);
                    queue.addLast(neighbour);
                }
            }
        }
        return hops;
    }

    /**
     * {@code 10.<server>.<index/254>.<2 + index%254>}.
     *
     * <p>Home's gateway is therefore {@code 10.0.0.2} and the player's rig is {@code 10.0.0.1}, one
     * link away — an ordinary private-range neighbourhood, which is what the address scheme is for.
     * The third octet exists so the scheme survives a machine cap larger than 253 without changing
     * shape; at the published cap of fifty it is always zero.
     */
    static String address(int server, int index) {
        return String.format(Locale.ROOT, "10.%d.%d.%d", server, index / 254, 2 + (index % 254));
    }

    /** Symmetric, and idempotent — the generator writes both sides and never writes one twice. */
    private static void link(HostState a, HostState b) {
        if (a == null || b == null || a == b) {
            return;
        }
        if (!a.links.contains(b.address)) {
            a.links.add(b.address);
        }
        if (!b.links.contains(a.address)) {
            b.links.add(a.address);
        }
    }

    private static void unlink(HostState a, HostState b) {
        if (a == null || b == null) {
            return;
        }
        a.links.remove(b.address);
        b.links.remove(a.address);
    }

    /** Indices {@code 0..limit-1} whose depth is maximal, ascending. Never empty for {@code limit ≥ 1}. */
    private static List<Integer> deepestIndices(int[] depth, int limit) {
        int max = 0;
        for (int i = 0; i < limit; i++) {
            max = Math.max(max, depth[i]);
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            if (depth[i] == max) {
                out.add(i);
            }
        }
        return out;
    }

    private static boolean[][] copyOf(boolean[][] source) {
        boolean[][] out = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i].clone();
        }
        return out;
    }
}
