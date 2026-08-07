package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.engine.rules.EventLog;
import io.github.stoicswe.eyeandsickle.engine.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.engine.state.AllocationState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.engine.state.TopologyState;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovery, traversal and settlement over the generated world.
 *
 * <h2>The five sentences this class is written against</h2>
 *
 * <ol>
 *   <li><b>Schematics buy reach, ethecoin buys sensitivity.</b> {@link #hopCeiling} is 1, or 2 with
 *       the Topology Mapper schematic, and takes no sweep tier. There is no code path from ethecoin
 *       to reach — Invariant I2 is structural here, not a rule somebody remembers.
 *   <li><b>The ceiling is measured from the player's <em>vantage</em>, not from their rig.</b>
 *       Traversal is repositioning: breach a host, take a foothold, {@link #connect} to it, sweep
 *       again from there. That is what makes a one-hop ceiling survivable across a seven-server
 *       world, and it is why position is earned rather than bought.
 *   <li><b>Detection is a roll made once, at world generation, and stored.</b> A sweep compares
 *       {@link HostState#detectRoll} against a threshold. Nothing here draws for detection, ever.
 *   <li><b>Undiscovered hosts do not exist in {@code knownNodes}, and the map draws nothing where
 *       they are.</b> No placeholder, no count, no "three contacts nearby". The single aggregate that
 *       may be published is {@link SweepReport#inRange} — how many machines were inside the ceiling,
 *       which describes the player's own instrument and carries no address, type, tier or value.
 *   <li>Amber is for live, earning data; a network node is not earning. That one is the client's, and
 *       it is repeated here because this class is what feeds it.
 * </ol>
 *
 * <h2>Why repeated sweeps are not free rerolls</h2>
 *
 * Same tier plus same vantage yields a bit-identical candidate set, every time, forever — because the
 * roll predates the sweep by the whole game. Quitting without saving changes nothing. Only two things
 * move the outcome and both cost: a <b>higher sweep tier</b> (ethecoin, plus its own compute, duration
 * and noise) or a <b>closer vantage</b> (a breach, a foothold and a {@code connect}). When a sweep
 * finds nothing new it says so in those words, because a mechanic that punishes without explaining is
 * indistinguishable from a bug.
 *
 * <h2>The whole sweep is rolled at begin and persisted</h2>
 *
 * {@link #beginSweep} decides everything — which machines were in range, which were detected, and
 * whether the sweep provoked a counter-hack — and freezes it into {@link TaskState#outcome}. The same
 * rule {@code docs/design/16-breach-implementation.md} §2 gives breach boards and
 * {@code ScanRules.roll} gives scan findings: a result computed at completion would quietly depend on
 * whether the player was watching, and under a persisted RNG it would also be a reroll a player could
 * force by quitting.
 *
 * <h2>A sweep is cheap and loud, and those are two different numbers</h2>
 *
 * Cycles are reserved through {@link ComputeConsumer#CONTROL_CHANNEL} — work that reaches other
 * machines — but the <em>loudness</em> is stated separately on the task
 * ({@link TaskState#noiseCycles}, from {@link SweepTier#noiseCycles()}) rather than inferred from the
 * cycle count. The two were once the same value and the identity was wrong on screen: noise renders
 * as outward cycles over rig capacity, so a two-cycle sweep moved the meter by two percent and got
 * quieter as the player's rig grew. A sweep now costs almost nothing and shouts, which is what
 * {@code docs/design/08-stealth-and-noise.md} §1 describes and what makes it a decision.
 *
 * <p>⚠ It shouts <b>only while it runs</b>. {@code NoiseRules} counts a task while {@code now} is
 * inside its window and the allocation goes into thermal recovery at settlement, so a finished sweep
 * contributes exactly nothing — no trailing figure, no decay curve to tune. What a loud act leaves
 * behind is heat, which is a persisted field charged by different rules.
 *
 * <h2>A sweep never costs ethecoin</h2>
 *
 * ⚠ There is no {@code LedgerRules} call on this path and there must never be one. The tiers are
 * bought once with ethecoin, which is breadth and therefore Invariant <b>I2</b>-legal; <em>running</em>
 * one spends the player's own cycles and their own exposure and nothing else. A per-run charge would
 * make discovery — the thing every other network mechanic is downstream of — meterable in currency,
 * and a player short of ethecoin would be unable to find the machines that are how you earn it.
 */
public final class NetRules {

    private NetRules() {}

    /** The schematic — and the only thing in the game — that moves the hop ceiling. */
    public static final String TOPOLOGY_MAPPER = "topology-mapper";

    /** The ledger type a host's one-time payout is credited under. */
    private static final String LOOT_LEDGER_TYPE = "NET_LOOT";

    // ================================================================== reach

    /**
     * How far a sweep can see from the current vantage: 1 hop, or 2 with the Topology Mapper.
     *
     * <p>⚠ <b>Nothing else may raise this, at any price.</b> {@code docs/design/07-recon-tools.md} §2
     * calls the Topology Mapper "a <b>ceiling</b> on information (1 hop → 2 hops), hence
     * schematic-gated not purchasable (Invariant I2)". No sweep tier is a parameter of this method
     * and no item id but one is consulted, so the invariant holds by the shape of the signature
     * rather than by anyone remembering it. {@code NetRulesTest} enumerates every purchasable
     * offering and asserts none of them moves the answer.
     */
    public static int hopCeiling(GameSave save) {
        return save != null && save.schematics != null && save.schematics.contains(TOPOLOGY_MAPPER) ? 2 : 1;
    }

    /**
     * Hop distance from {@code from} to every reachable host, over the <em>full</em> link graph.
     *
     * <p>⚠ Not over the discovered subgraph. Undiscovered machines still conduct: a host the player
     * has never seen is still the reason a further one is two hops away rather than three. A BFS over
     * what the player knows would make the ceiling widen as they learned things, which is reach for
     * free and therefore an I2 violation wearing a graph algorithm's clothes.
     *
     * <p>Hosts outside the graph are absent from the result rather than present at
     * {@code Integer.MAX_VALUE}, so a caller that forgets to check gets a {@code null} rather than a
     * silently in-range machine.
     */
    public static Map<String, Integer> hopsFrom(GameSave save, String from) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return Map.of();
        }
        return TopologyGenerator.bfs(index(topology), from);
    }

    // ================================================================== the read model

    /**
     * The player's whole visible network: their own rig, every node they have discovered, and the
     * links between them.
     *
     * <p>Built from {@code knownNodes} intersected with the topology, which is what keeps the
     * discovery rule honest in one place — an undiscovered host is not in {@code knownNodes}, so it
     * is not in the map, so the graph draws nothing where it is and {@code ls /net/} does not list it.
     * Never null; an absent topology yields {@link NetMap#empty()}.
     */
    public static NetMap view(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return NetMap.empty();
        }
        Map<String, HostState> hosts = index(topology);
        Map<String, ServerState> servers = servers(topology);
        Map<String, NodeState> known = knownNodes(save);
        String vantage = vantageAddress(save);
        Map<String, Integer> hops = TopologyGenerator.bfs(hosts, vantage);

        // The rig is always visible; everything else has to have been detected. Ordered so the map is
        // stable across repaints: the vantage first, then by address.
        Set<String> visible = new LinkedHashSet<>();
        visible.add(topology.playerAddress);
        for (NodeState node : known.values()) {
            if (hosts.containsKey(node.address)) {
                visible.add(node.address);
            }
        }

        List<Sighting> sightings = new ArrayList<>();
        for (String address : visible) {
            sightings.add(sighting(save, hosts.get(address), known.get(address), servers, hops, vantage, topology));
        }

        List<NetLink> links = new ArrayList<>();
        for (String address : visible) {
            HostState host = hosts.get(address);
            for (String neighbour : host.links) {
                // Emitted once, from the lower address, so the client never has to de-duplicate an
                // undirected edge it was handed twice.
                if (visible.contains(neighbour) && address.compareTo(neighbour) < 0) {
                    HostState other = hosts.get(neighbour);
                    links.add(new NetLink(address, neighbour, !host.serverId.equals(other.serverId)));
                }
            }
        }

        List<ServerRef> knownServers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Sighting sighting : sightings) {
            if (seen.add(sighting.serverId())) {
                knownServers.add(serverRef(servers.get(sighting.serverId())));
            }
        }
        knownServers.sort(Comparator.comparingInt(ServerRef::depthFromHome).thenComparing(ServerRef::serverId));

        HostState vantageHost = hosts.get(vantage);
        ServerRef current = serverRef(servers.get(vantageHost == null ? topology.homeServerId : vantageHost.serverId));
        return new NetMap(current, vantage, hopCeiling(save), knownServers, sightings, links);
    }

    /**
     * One machine as the player knows it.
     *
     * <p>⚠ <b>{@code label} is one of them, and it was the truth until 2026-08-07.</b> A sweep used to
     * copy {@link HostState#label} straight into the player's knowledge, so every machine arrived
     * already named. Its name is now a finding — {@code PortScanTarget.IDENTITY}, the cheapest rung —
     * and until that rung has been paid for, or the machine has been breached, this is empty and the
     * interface shows the address alone. Read from the recon file rather than from a second copy on
     * {@link NodeState}, because one stored answer cannot disagree with itself.
     *
     * <p>Five fields are deliberately the player's knowledge and not the truth beside them:
     * {@code kind} stays {@code UNKNOWN} until a type-revealing tool has run (a sweep sells existence
     * and adjacency, the Passive Sniffer sells identity), {@code honeypotSuspected} comes off
     * {@link NodeState} and never off {@link HostState#honeypot}, {@code documentAvailable} is only
     * true once the player holds a foothold — knowing a fragment is there before you are inside would
     * be a recon product nobody sold — and the peer server name is published only by a bridge the
     * player has actually identified.
     */
    private static Sighting sighting(
            GameSave save,
            HostState host,
            NodeState node,
            Map<String, ServerState> servers,
            Map<String, Integer> hops,
            String vantage,
            TopologyState topology) {

        boolean self = host.address.equals(topology.playerAddress);
        boolean identified = self || host.identified || (node != null && !"UNKNOWN".equals(node.kind));
        HostKind kind = identified ? HostArchetypes.kindOrUnknown(host.kind) : HostKind.UNKNOWN;

        // ⚠ The player's own rig has no breach tier, and null is the honest reading: DifficultyTier
        // is a 1–5 scale for "how hard is this to breach", and the answer for your own machine is not
        // a number on that scale. The list renders it as "--".
        DifficultyTier tier = self
                ? null
                : DifficultyTier.of(Math.max(DifficultyTier.LOWEST, Math.min(DifficultyTier.HIGHEST, host.tier)));

        boolean hostsMiner = node != null && node.deployedMiners != null && !node.deployedMiners.isEmpty();
        SignalStrength signal = HostArchetypes.signalOf(host, hostsMiner);

        // The graph is connected by construction, so the fallback is unreachable in a save this
        // engine wrote. It exists because a hand-edited one is not.
        int distance = hops.getOrDefault(host.address, 0);

        String peerServerName = "";
        if (kind == HostKind.BRIDGE && !host.bridgePeer.isEmpty()) {
            peerServerName = peerServerName(host, servers, topology);
        }

        // Your own rig is never a finding about somebody else's machine — it is called what it is
        // called, from the first second, and gating it would make `localhost` something to scan for.
        // Its operator is the player, and the top strip already says who that is, so it stays empty
        // rather than printing the handle twice.
        Optional<NodeReportState> file = self ? Optional.empty() : NodeReports.find(save, host.address);
        String knownName =
                self ? host.label : file.map(report -> report.hostName).orElse("");
        String knownOperator = file.map(report -> report.operatorName).orElse("");

        return new Sighting(
                host.address,
                knownName,
                host.serverId,
                kind,
                tier,
                signal,
                distance,
                host.address.equals(vantage),
                host.foothold,
                // ⚠ A patched host is one that WAS breached and is not any more, so it can never be
                // true while the foothold still stands. Reporting both would let the map draw a
                // node as simultaneously open and shut.
                host.patched && !host.foothold,
                host.looted,
                node != null && node.honeypotSuspected,
                hostsMiner,
                host.foothold && !host.documentId.isEmpty() && !host.documentTaken,
                peerServerName,
                NodeReports.any(save, host.address),
                knownOperator);
    }

    /**
     * The name of the network on the other side of a bridge — and nothing else about it.
     *
     * <p>A bridge's entire published function is to name what it connects to, so this is legitimate
     * where a peer address, a host count, or anything about what is over there would not be. It is
     * also exactly the shape a federated bridge would take: {@code docs/design/13} §4 lets servers
     * share the minimum needed to recognise identities, never enough for one to grief another's
     * internal state, so a cross-server bridge exposes a handshake and not a topology.
     */
    private static String peerServerName(HostState host, Map<String, ServerState> servers, TopologyState topology) {
        for (HostState candidate : topology.hosts) {
            if (candidate.address.equals(host.bridgePeer)) {
                ServerState server = servers.get(candidate.serverId);
                return server == null ? "" : server.name;
            }
        }
        return "";
    }

    // ================================================================== the sweep

    /**
     * Commissions a sweep: reserves its compute, rolls its entire result, and freezes that into the
     * returned task.
     *
     * <p>Refuses — empty, with nothing spent — when the tool is not owned or the rig cannot afford
     * the cycles. Refusing rather than throwing because the shell prints a refusal, and a rules engine
     * that threw on an unaffordable action would be deciding how the client reports it.
     *
     * <p>Exactly one draw happens here: the counter-hack roll. It is made now, stored, and applied at
     * settlement, so a reload mid-sweep replays nothing. Detection makes no draw at all.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — a rule that reads the wall clock
     *     behind its caller's back reports every task as complete the moment it starts under a test
     *     clock, and agrees with itself only in production
     */
    public static Optional<TaskState> beginSweep(GameSave save, SweepTier tier, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || tier == null) {
            return Optional.empty();
        }
        if (!owns(save, tier)) {
            return Optional.empty();
        }
        AllocationState allocation =
                ComputeRules.reserve(save.rig, ComputeConsumer.CONTROL_CHANNEL, tier.label(), tier.cycles());
        if (allocation == null) {
            return Optional.empty();
        }
        // Held, not spent: GameEngine.settleTasks hands it to ComputeRules.beginRecovery when the sweep
        // ends, the same shape a scan takes since UI-6. Stamped so the rig monitor can draw the hold.
        allocation.startedAt = now;

        String vantage = vantageAddress(save);
        Map<String, HostState> hosts = index(topology);
        Map<String, ServerState> servers = servers(topology);
        Map<String, Integer> hops = TopologyGenerator.bfs(hosts, vantage);
        int ceiling = hopCeiling(save);

        List<HostState> candidates = new ArrayList<>();
        int deepestInRange = 0;
        for (HostState host : topology.hosts) {
            Integer distance = hops.get(host.address);
            if (distance == null || distance < 1 || distance > ceiling) {
                continue;
            }
            if (host.address.equals(topology.playerAddress)) {
                continue;
            }
            candidates.add(host);
            ServerState server = servers.get(host.serverId);
            deepestInRange = Math.max(deepestInRange, server == null ? 0 : server.depthFromHome);
        }

        List<String> found = new ArrayList<>();
        for (HostState host : candidates) {
            if (host.discovered) {
                continue;
            }
            boolean hostsMiner = false;
            double threshold = Balance.netSweepBase(
                            tier.tier(),
                            HostArchetypes.signalOf(host, hostsMiner).name())
                    * hopFactor(hops.get(host.address));
            if (host.detectRoll < threshold) {
                found.add(host.address);
            }
        }

        // The one draw. Rolled against the CANDIDATE set rather than the detected set: the machines
        // notice you probing them whether or not you learn anything.
        Rng rng = Rng.of(save);
        double roll = rng.nextDouble();
        rng.commit(save);
        int counterHackDepth = roll < Balance.netCounterHackChance(deepestInRange) ? deepestInRange : -1;

        // ⚠ Longer on an infested rig, baked in at commission — see ComputeRules.slowedSeconds. A
        // sweep that takes 26 seconds instead of 20 is the cheapest hint in the game that something
        // is eating the machine, and it costs nothing to notice.
        long seconds = ComputeRules.slowedSeconds(save.rig, tier.seconds());
        TaskState task = new TaskState(
                "sweep", tier.label(), allocation.allocationId, tier.cycles(), now, now.plusSeconds(seconds));
        // The sweep's loudness, declared on the task rather than derived from its cycles. It is
        // present-tense by construction: NoiseRules counts a task only while it is still running, so
        // the meter drops back the instant this one's countdown reaches zero.
        task.noiseCycles = tier.noiseCycles();
        task.outcome = encode(tier.itemId(), vantage, candidates.size(), counterHackDepth, found);
        save.tasks.add(task);

        EventLog.notice(
                save,
                "net",
                tier.label() + ": " + tier.cycles() + " cycles held, ~" + seconds + "s, and loud the whole time.",
                now);
        return Optional.of(task);
    }

    /**
     * Applies a finished sweep: materialises the nodes it found, plants any counter-hack, and logs.
     *
     * <p>Draws nothing. Everything was decided at {@link #beginSweep}, so this method is a pure
     * application of a frozen result — which is why a sweep that finished while the game was closed
     * reports exactly what it would have reported in session.
     */
    public static SweepReport settleSweep(GameSave save, TaskState task, Instant now) {
        Encoded encoded = decode(task);
        SweepReport report = report(task);
        TopologyState topology = topology(save);
        if (topology == null) {
            return report;
        }
        Map<String, HostState> hosts = index(topology);

        for (String address : encoded.found()) {
            HostState host = hosts.get(address);
            if (host == null || host.discovered) {
                continue;
            }
            host.discovered = true;
            save.knownNodes.add(nodeFor(host, now));
        }

        if (encoded.counterHackDepth() >= 0) {
            counterHack(save, encoded.counterHackDepth(), now);
        }

        if (report.found() > 0) {
            EventLog.notice(
                    save, "net", task.label + ": " + report.inRange() + " in range, " + report.found() + " new.", now);
        } else {
            EventLog.info(
                    save, "net", task.label + ": " + report.inRange() + " in range, 0 new. " + report.note(), now);
        }
        return report;
    }

    /**
     * Cuts a sweep's frozen result down to the fraction it managed before it was killed.
     *
     * <h2>⚠ A truncation of the stored answer, never a new roll</h2>
     *
     * {@link #beginSweep} decides the whole sweep at commission precisely so that quitting cannot
     * change it. Killing early therefore takes the machines it had already reached — the first
     * {@code round(progress × n)} of them, in the order they were found — and drops the rest. Asking
     * the rules for a fresh, smaller sweep would be a re-roll the player could force at will, which
     * is the exploit every frozen outcome in this engine exists to close.
     *
     * <p>The counter-hack is dropped with the tail. It is the network answering a sweep that ran to
     * completion, and one the player pulled the plug on halfway did not finish provoking anybody —
     * which is a real and legible reason to kill a deep sweep that is making you nervous.
     *
     * @param progress how far it got, {@code [0, 1]}
     */
    public static void truncate(TaskState task, double progress) {
        Encoded encoded = decode(task);
        double fraction = Math.max(0.0d, Math.min(1.0d, progress));
        int keep = (int) Math.round(encoded.found().size() * fraction);
        List<String> kept =
                encoded.found().subList(0, Math.max(0, Math.min(encoded.found().size(), keep)));
        task.outcome = encode(
                encoded.toolId(),
                encoded.vantage(),
                encoded.inRange(),
                fraction >= 1.0d ? encoded.counterHackDepth() : -1,
                kept);
    }

    /**
     * Decodes a finished sweep's frozen result without applying it — the readout's seam.
     *
     * <p>Tolerant of a malformed line for the same reason {@code ScanRules.finding} is: a save
     * written before this existed, or edited by hand, must open. It reports an empty sweep rather
     * than a confident wrong one.
     */
    public static SweepReport report(TaskState task) {
        Encoded encoded = decode(task);
        return new SweepReport(
                encoded.toolId(),
                encoded.vantage(),
                encoded.inRange(),
                encoded.found().size(),
                encoded.found(),
                encoded.counterHackDepth() >= 0,
                note(encoded));
    }

    /**
     * What the sweep says when it found nothing.
     *
     * <p>⚠ In the player's language, and it must stay there. The mechanic — a fixed roll, compared
     * against a threshold the player can only move by buying a better instrument or standing
     * somewhere closer — is a good one and an invisible one. A sweep that silently returned nothing
     * twice would read as a bug; one that explains why repetition is not the answer teaches the whole
     * model in three lines.
     */
    private static String note(Encoded encoded) {
        if (!encoded.found().isEmpty()) {
            return "";
        }
        if (encoded.inRange() == 0) {
            return "Nothing within reach of this position. A foothold you can connect to is what "
                    + "moves reach; the instrument does not.";
        }
        return "Nothing at this sensitivity that you have not already seen. A louder instrument or a "
                + "closer position is what changes this; running the same sweep again is not.";
    }

    /**
     * Plants a parasite the sweep provoked.
     *
     * <p>The planting itself moved to {@code IntrusionRules}: a loud breach can now provoke one too,
     * and two packages that must not depend on each other both needed it. What stays here is the
     * decision — a sweep's counter-hack is rolled at commission and frozen into the task, so a reload
     * mid-sweep replays nothing.
     */
    private static void counterHack(GameSave save, int depth, Instant now) {
        io.github.stoicswe.eyeandsickle.engine.rules.IntrusionRules.plantCounterHack(save, depth, now);
    }

    /** {@code 1.00} at one hop, {@code 0.60} at two — see {@code Balance.NET_HOP_FACTOR_2}. */
    private static double hopFactor(Integer hops) {
        return hops != null && hops >= 2 ? Balance.NET_HOP_FACTOR_2 : Balance.NET_HOP_FACTOR_1;
    }

    /**
     * Whether the player owns this sweep tier.
     *
     * <p>The base tier is starting kit — the same class as Port Sweep, which
     * {@code docs/design/06-intrusion-tools.md} §2 calls "the free starting enumerator. Everyone has
     * it." Without it a new player has no way to find the machines next to them, which is the problem
     * this whole system exists to fix.
     *
     * <p>⚠ Checked here rather than through {@code Targets.owns}, because that method's map is the
     * <em>breach loadout</em> and a sweep tool must never be in it: adding one would silently raise
     * {@code Targets.attemptCycles} for every breach the player opens.
     */
    public static boolean owns(GameSave save, SweepTier tier) {
        if (tier == SweepTier.BASE) {
            return true;
        }
        if (save == null || save.items == null) {
            return false;
        }
        for (ItemState item : save.items) {
            if (tier.itemId().equals(item.itemType)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== traversal

    /**
     * Moves the vantage — the whole traversal loop, in one method.
     *
     * <p>Refuses unless the address is a host the player holds a foothold on, or their own rig. That
     * refusal is the reason a one-hop ceiling is survivable and the reason it is not exploitable:
     * position substitutes for reach, and position is earned with a breach rather than bought with
     * ethecoin. See {@code TopologyState}'s class note.
     */
    public static boolean connect(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || address == null || address.isBlank()) {
            return false;
        }
        HostState host = host(save, address.trim());
        if (host == null) {
            return false;
        }
        boolean ownRig = host.address.equals(topology.playerAddress);
        if (!ownRig && !host.foothold) {
            return false;
        }
        if (!host.address.equals(topology.vantageAddress)) {
            topology.vantageAddress = host.address;
            EventLog.notice(
                    save,
                    "net",
                    "operating from " + host.address + (ownRig ? " (localhost)" : " — sweeps now reach from here."),
                    now);
        }
        return true;
    }

    /**
     * Grants footholds and one-time payouts for every successful breach that has not been settled.
     *
     * <p>⚠ <b>Idempotent by construction, not by bookkeeping.</b> There is no "settled" flag on a
     * resolution; instead a host records that it has a foothold and that it has been looted, and both
     * are one-way. So replaying the entire resolution list on every load is correct rather than merely
     * cheap, and a save whose resolutions were duplicated by a bad merge still pays out once.
     *
     * <p>⚠ The payout is currency, and that reads against {@code BreachRules.resolveOffensive}'s
     * standing note that breach loot is "an item, never ethecoin" ({@code docs/design/03-economy.md}
     * §5 rule 3, no new faucets). Both survive, and the distinction is real: the breach engine still
     * mints a data cache and no currency, while this credits a <b>finite quantity placed in the world
     * at generation</b> — a stock, not a flow. Home's entire pool is about 68 EC and then it is gone,
     * so no rate exists to compare against §5 rule 1's 70 EC/hr cap. Flagged for the integrator to log
     * in {@code docs/design/15-open-questions.md} §3.
     *
     * @return true if anything changed and the caller should persist
     */
    public static boolean reconcileFootholds(GameSave save, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || save.resolutions == null) {
            return false;
        }
        boolean changed = false;
        for (ResolutionState resolution : save.resolutions) {
            if (!"BREACHED".equals(resolution.outcome) || resolution.targetId == null) {
                continue;
            }
            if (!resolution.targetId.startsWith("node:")) {
                continue;
            }
            HostState host = host(save, resolution.targetId.substring("node:".length()));
            if (host == null) {
                continue;
            }
            if (!host.foothold) {
                host.foothold = true;
                changed = true;
                EventLog.notice(
                        save,
                        "net",
                        "foothold on " + host.address + "; `connect " + host.address + "` to sweep from it.",
                        now);
            }
            // ⚠ Outside the foothold guard, so a machine breached before identities were recorded
            // gets one on the next load rather than staying permanently anonymous — this method is
            // idempotent by construction and runs on every resume, which is what makes that safe.
            // It is write-once itself, so the name a player learned on the first break-in is the name
            // they keep. See NodeReports#establishIdentity.
            if (NodeReports.establishIdentity(save, host, now)) {
                changed = true;
            }
            if (!host.looted) {
                host.looted = true;
                changed = true;
                if (host.lootWei.signum() > 0) {
                    LedgerRules.apply(save, host.lootWei, LOOT_LEDGER_TYPE, "Recovered from " + host.address, now);
                    EventLog.info(
                            save, "net", Ethecoin.format(host.lootWei) + " recovered from " + host.address + ".", now);
                }
            }
        }
        return changed;
    }

    // ================================================================== documents

    /**
     * Pulls a story fragment off a host the player holds.
     *
     * <p>⚠ Schematic material only at tier 3 or above — {@code Balance.SCHEMATIC_MATERIAL_MIN_TIER},
     * the same constant and the same denominator the breach salvage path already uses. Invariant I13:
     * the drop is gated on <em>engagement tier</em>, never on a count, because the alternative is that
     * the optimal play becomes farming the softest thing that qualifies. A deep-but-easy host yields
     * flavour and nothing else, which is exactly the exploit I13 exists to close.
     *
     * <p>Refuses on a host with no foothold, no fragment, or a fragment already taken. Nothing here is
     * required to advance (decision N-4) — the fragments are pull, not path.
     */
    public static Optional<NetDocument> download(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return Optional.empty();
        }
        HostState host = host(save, address);
        if (host == null || !host.foothold || host.documentId.isEmpty() || host.documentTaken) {
            return Optional.empty();
        }
        host.documentTaken = true;
        host.documentTakenAt = now;
        topology.documents.add(host.documentId);

        int material = host.tier >= Balance.SCHEMATIC_MATERIAL_MIN_TIER ? Balance.SCHEMATIC_MATERIAL_PER_BREACH : 0;
        save.schematicMaterial += material;

        EventLog.notice(
                save,
                "net",
                "recovered " + DocumentPool.title(host.documentId) + " from " + host.address
                        + (material > 0 ? "; it carried schematic material." : "."),
                now);
        return Optional.of(
                new NetDocument(host.documentId, DocumentPool.title(host.documentId), host.address, now, material));
    }

    /**
     * Every fragment recovered so far, oldest first.
     *
     * <p>Read off the hosts rather than off {@code TopologyState.documents}, because twelve fragment
     * ids are spread across up to 350 machines and two hosts can carry the same one — an id list
     * cannot say which host a duplicate came from or when. Ordered by recovery time with the address
     * as a stable tiebreak, so the list does not reshuffle between repaints.
     */
    public static List<NetDocument> documents(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return List.of();
        }
        List<HostState> taken = new ArrayList<>();
        for (HostState host : topology.hosts) {
            if (host.documentTaken && !host.documentId.isEmpty()) {
                taken.add(host);
            }
        }
        taken.sort(Comparator.comparing((HostState h) -> h.documentTakenAt == null ? Instant.EPOCH : h.documentTakenAt)
                .thenComparing(h -> h.address));

        List<NetDocument> out = new ArrayList<>(taken.size());
        for (HostState host : taken) {
            out.add(new NetDocument(
                    host.documentId,
                    DocumentPool.title(host.documentId),
                    host.address,
                    host.documentTakenAt == null ? Instant.EPOCH : host.documentTakenAt,
                    host.tier >= Balance.SCHEMATIC_MATERIAL_MIN_TIER ? Balance.SCHEMATIC_MATERIAL_PER_BREACH : 0));
        }
        return out;
    }

    // ================================================================== lookups

    /**
     * Ground truth for one address, or null.
     *
     * <p>⚠ Not a read model. Nothing that reaches the client may be built from this — {@link #view}
     * is the seam, and it exists so the fields a player has not paid for cannot leak through a
     * convenient lookup. Public because {@code GameEngine} needs it to answer "is this a real address"
     * before refusing a command.
     */
    public static HostState host(GameSave save, String address) {
        TopologyState topology = topology(save);
        if (topology == null || address == null) {
            return null;
        }
        String wanted = address.trim();
        for (HostState host : topology.hosts) {
            if (host.address.equals(wanted)) {
                return host;
            }
        }
        return null;
    }

    /** Where sweeps are run from right now. Falls back to the player's rig on a truncated save. */
    public static String vantageAddress(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return "";
        }
        return topology.vantageAddress == null || topology.vantageAddress.isBlank()
                ? topology.playerAddress
                : topology.vantageAddress;
    }

    private static TopologyState topology(GameSave save) {
        return save == null ? null : save.topology;
    }

    private static Map<String, HostState> index(TopologyState topology) {
        Map<String, HostState> out = new HashMap<>();
        for (HostState host : topology.hosts) {
            out.put(host.address, host);
        }
        return out;
    }

    private static Map<String, ServerState> servers(TopologyState topology) {
        Map<String, ServerState> out = new HashMap<>();
        for (ServerState server : topology.servers) {
            out.put(server.serverId, server);
        }
        return out;
    }

    private static Map<String, NodeState> knownNodes(GameSave save) {
        Map<String, NodeState> out = new HashMap<>();
        if (save.knownNodes != null) {
            for (NodeState node : save.knownNodes) {
                out.put(node.address, node);
            }
        }
        return out;
    }

    private static ServerRef serverRef(ServerState server) {
        return server == null
                ? new ServerRef("", "", 0, false)
                : new ServerRef(server.serverId, server.name, server.depthFromHome, server.home);
    }

    /**
     * The player's knowledge of a machine a sweep just found.
     *
     * <p>⚠ {@code kind} stays {@code UNKNOWN} and {@code trafficAnalyzed} and
     * {@code honeypotSuspected} stay false. Those three are the Passive Sniffer's, the Traffic
     * Analyzer's and the Honeypot Detector's products respectively; a sweep that set any of them
     * would delete a purchased or gated tool at the point of discovery, and setting
     * {@code trafficAnalyzed} would additionally hand out proof-of-skill credit (Invariant I7). A test
     * asserts all three.
     *
     * <p>⚠ <b>{@code label} is now a fourth, added 2026-08-07.</b> This method used to copy
     * {@link HostState#label} in, which named every machine the moment a sweep touched it. The name
     * is a port-scan finding now ({@code PortScanTarget.IDENTITY}) and lives on the recon file, so
     * this field stays empty and nothing reads it — a sweep's product is existence and adjacency, and
     * a name is neither.
     */
    private static NodeState nodeFor(HostState host, Instant now) {
        NodeState node = new NodeState();
        node.address = host.address;
        node.serverId = host.serverId;
        node.kind = HostKind.UNKNOWN.name();
        // ⚠ The default is Instant.now(); a rule that leaves it there dates every discovery to the
        // real world's present regardless of the clock its caller is running on.
        node.discoveredAt = now;
        node.reconLevel = 1;
        node.tier = host.tier;
        node.firewallTier = host.firewallTier;
        node.tarpit = host.tarpit;
        node.canaries = host.canaries;
        node.defended = host.defended;
        return node;
    }

    // ================================================================== the frozen result

    /**
     * A sweep's decided outcome, as it sits in {@link TaskState#outcome}.
     *
     * @param counterHackDepth the depth that provoked a counter-hack, or {@code -1} for none — the
     *     depth is carried rather than a bare flag because it sets the planted miner's tier, its host
     *     cycles, whether it is rootkit-wrapped, and how much heat it costs
     */
    private record Encoded(String toolId, String vantage, int inRange, int counterHackDepth, List<String> found) {}

    /**
     * One line, pipe-separated, versioned.
     *
     * <p>A string rather than a nested object for the same reason every other {@code state} class
     * uses strings: this lands in a JSON document that outlives the code that wrote it, and
     * {@link TaskState#outcome} is already a {@code String} carrying a scan's frozen finding. The
     * {@code v1} tag is what lets a later shape be added without a save written today decoding as
     * garbage — an unrecognised version reads as an empty sweep, which is honest.
     */
    private static String encode(String toolId, String vantage, int inRange, int counterHackDepth, List<String> found) {
        return String.join(
                "|",
                "sweep",
                "v1",
                toolId,
                vantage,
                Integer.toString(inRange),
                Integer.toString(counterHackDepth),
                String.join(",", found));
    }

    private static Encoded decode(TaskState task) {
        String raw = task == null || task.outcome == null ? "" : task.outcome;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 7 || !"sweep".equals(parts[0]) || !"v1".equals(parts[1])) {
            // A sweep from a build that predates this encoding, or a hand-edited save. Reporting an
            // empty sweep is the reading that cannot invent contacts or plant a miner nobody earned.
            return new Encoded("", "", 0, -1, List.of());
        }
        List<String> found = new ArrayList<>();
        for (String address : parts[6].split(",")) {
            if (!address.isBlank()) {
                found.add(address);
            }
        }
        return new Encoded(parts[2], parts[3], parseInt(parts[4], 0), parseInt(parts[5], -1), List.copyOf(found));
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
