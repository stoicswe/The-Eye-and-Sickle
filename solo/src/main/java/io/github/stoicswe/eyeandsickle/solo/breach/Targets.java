package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the player can breach, and what they would bring to it.
 *
 * <h2>Two kinds of target, and they are not variants of one thing</h2>
 *
 * A <b>crack</b> runs against a foreign miner on the player's own rig ({@code
 * docs/design/04-mining.md} §5.1). It generates no heat on any outcome (Invariant I9), its prize is
 * a buffer that already exists on the player's own disk (a transfer, never a faucet — {@code
 * docs/design/03-economy.md} §5 rule 3), and losing it costs the buffer the player never had. That
 * combination is why §5.1 calls cracking "the strongest early-game teaching vector for the core
 * minigame".
 *
 * <p>An <b>offensive breach</b> runs against a node out in the world. It generates heat, it can trip
 * canaries that tag the player's handle, and it yields items rather than money — because the moment
 * breaching minted ethecoin it would be a faucet, and because ethecoin must never buy a ceiling
 * (Invariants I1 and I2).
 *
 * <h2>⚠ Crack targets are reported DORMANT, and that is a decision (BR-2)</h2>
 *
 * {@code docs/design/02-unlock-gates.md} §2.4 requires proof-of-skill credit to come from "a live or
 * defended target — not a dormant one", and Invariant I7 exists because count-gating "invites
 * farming the weakest available target". A miner squatting on the player's own rig is neither live
 * nor defended: it does not fight back, nothing about it can hurt the player, and it is available
 * on demand as soon as one is planted. Reporting it {@code LIVE} would make the safest action in the
 * game also the proof-of-skill source, which is the exact farming failure the gate was written to
 * prevent. Logged in {@code docs/design/16-breach-implementation.md} §7.
 */
public final class Targets {

    private Targets() {}

    /**
     * The compute each intrusion or recon tool reserves while equipped, from the published tool
     * tables in {@code docs/design/06-intrusion-tools.md} §1 and {@code
     * docs/design/07-recon-tools.md} §1.
     *
     * <p>These are <b>established</b> numbers, not proposals — they are the "gate, EC cost, compute,
     * noise" stats §1 of {@code 06} calls "first-class balance levers". They live here rather than in
     * {@code Balance} because they are a mapping from item id to a figure another document owns, not
     * a figure this module chose; {@code Balance} is for numbers solo had to decide.
     *
     * <p>Port Sweep is in the map and is also always owned: {@code 06} §2 calls it "the free
     * starting enumerator. Everyone has it; it's the baseline the Enumeration class is tuned
     * against."
     *
     * <p>⚠ <b>The {@code net-sweep*} tools must never be added here.</b> They are named
     * {@code net-sweep}, {@code net-sweep-wide} and {@code net-sweep-deep} partly so they cannot be
     * confused with {@code port-sweep} in this map. A sweep is a pre-breach recon action with its own
     * duration and its own reservation — it is not part of a breach loadout, and adding one would
     * silently raise {@link #attemptCycles} for every breach the player ever opens, in a number the
     * client renders as the attempt's published cost. {@code NetRules.owns} does the sweep ownership
     * check separately for exactly this reason.
     */
    private static final Map<String, Long> TOOL_CYCLES = toolCycles();

    private static Map<String, Long> toolCycles() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("port-sweep", 2L);
        map.put("fuzzer", 6L);
        map.put("rainbow-table", 8L);
        map.put("overflow-kit", 10L);
        map.put("credential-harvester", 7L);
        map.put("side-channel-reader", 14L);
        map.put("topology-mapper", 9L);
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Every target the player could open a breach against right now, cracks first. */
    public static List<BreachTarget> available(SoloSave save) {
        List<BreachTarget> out = new ArrayList<>();
        long cost = attemptCycles(save);
        long free = ComputeRules.availableCycles(save.rig);
        String refusal = free >= cost
                ? ""
                : "not enough available compute - " + cost + " needed, " + free + " free";

        for (MinerState miner : save.rig.foreignMiners) {
            // ⚠ A parasite nobody has audited is not a target, because it is not KNOWN.
            //
            // It used to be listed the moment it was planted, which handed a new character the
            // tutorial crack before they had run a single scan — and, worse, told them a process was
            // stealing from them at the same moment the rig monitor was being careful not to
            // (MinerState.discovered). Two windows disagreeing about what the player knows is worse
            // than either answer, and this is the one that costs nothing to fix: `scan --full` finds
            // the tutorial miner, and the audit → crack pipeline is what docs/design/04-mining.md
            // §3.1 and §3.2 describe in the first place.
            if (!miner.discovered) {
                continue;
            }
            out.add(new BreachTarget(
                    "miner:" + miner.minerId,
                    "localhost",
                    miner.label.isEmpty() ? "unidentified miner" : miner.label,
                    "",
                    DifficultyTier.of(crackTier(miner)),
                    // See the class note (BR-2): your own rig is never a proof-of-skill source.
                    TargetState.DORMANT,
                    true,
                    0,
                    false,
                    false,
                    false,
                    miner.bufferedWei,
                    cost,
                    refusal.isEmpty(),
                    refusal));
        }

        for (NodeState node : save.knownNodes) {
            // Already breached machines stay in the list and stay un-attemptable. Removing them
            // would answer "why is it gone" with silence; a row carrying the reason is the same
            // choice every other refusal on this list makes.
            HostState host = host(save, node.address);
            boolean held = host != null && host.foothold;
            String nodeRefusal = held
                    ? "already breached — you hold a foothold here; `connect " + node.address
                            + "` to sweep from it"
                    : refusal;
            out.add(new BreachTarget(
                    "node:" + node.address,
                    node.address,
                    node.label.isEmpty() ? node.address : node.label,
                    // The Enumeration banner, when recon has established it — and empty otherwise,
                    // which is the state a sweep leaves a node in. A sweep sells existence and
                    // adjacency; naming the type is the 15 EC Passive Sniffer's product
                    // (docs/design/07-recon-tools.md §1), so printing a role here for an unidentified
                    // node would delete a purchased tool at the point of rendering.
                    node.kind == null || node.kind.isBlank() || "UNKNOWN".equals(node.kind) ? "" : node.kind,
                    DifficultyTier.of(Math.max(DifficultyTier.LOWEST, Math.min(DifficultyTier.HIGHEST, node.tier))),
                    // Dormant until recon has established otherwise. docs/design/07 §2 makes
                    // distinguishing live from dormant the Traffic Analyzer's entire function and
                    // notes it "directly supports proof-of-skill" — so an unexamined node reports
                    // dormant, which is the reading that cannot accidentally hand out an unlock.
                    node.trafficAnalyzed && node.defended ? TargetState.LIVE : TargetState.DORMANT,
                    false,
                    // ⚠ BreachTarget's compact constructor THROWS above 3, so an out-of-range value
                    // here is not a balance mistake but an exception raised while building the target
                    // list — a save that cannot render its own network. The generator never emits a 4
                    // (Balance.netFirewallTier has no fourth band), so this clamp exists purely for a
                    // hand-edited save, which SoloSave's class note says is expected rather than
                    // exceptional. Clamping opens; throwing does not.
                    Math.max(0, Math.min(3, node.firewallTier)),
                    node.tarpit,
                    node.canaries,
                    node.honeypotSuspected,
                    java.math.BigInteger.ZERO,
                    cost,
                    nodeRefusal.isEmpty(),
                    nodeRefusal));
        }
        return out;
    }

    /** Ground truth for one address, or null. Only ever consulted for a fact the player already has. */
    private static HostState host(SoloSave save, String address) {
        if (save.topology == null || save.topology.hosts == null) {
            return null;
        }
        for (HostState candidate : save.topology.hosts) {
            if (candidate.address.equals(address)) {
                return candidate;
            }
        }
        return null;
    }

    public static Optional<BreachTarget> byId(SoloSave save, String targetId) {
        return available(save).stream().filter(t -> t.targetId().equals(targetId)).findFirst();
    }

    /**
     * Crack difficulty for a foreign miner.
     *
     * <p>{@code docs/design/04-mining.md} §5.1: "Difficulty scales with miner tier, raised further by
     * Rootkit Wrapper (which gives that item a defensive-denial role)." {@code
     * docs/design/09-defense-and-hardening.md} §2 says the same from the other side — the Wrapper
     * "hides your deployed miner from routine host scans <em>and</em> raises crack difficulty against
     * it". One tier, clamped to the published scale.
     */
    static int crackTier(MinerState miner) {
        int tier = miner.tier + (miner.rootkitWrapped ? 1 : 0);
        return Math.max(DifficultyTier.LOWEST, Math.min(DifficultyTier.HIGHEST, tier));
    }

    /** Tool ids the player has, Port Sweep included because everyone has it ({@code 06} §2). */
    public static List<String> loadout(SoloSave save) {
        List<String> out = new ArrayList<>();
        out.add("port-sweep");
        for (ItemState item : save.items) {
            if (TOOL_CYCLES.containsKey(item.itemType) && !out.contains(item.itemType)) {
                out.add(item.itemType);
            }
        }
        return out;
    }

    /** What the loadout reserves while the attempt runs. */
    public static long loadoutCycles(SoloSave save) {
        long sum = 0L;
        for (String tool : loadout(save)) {
            sum += TOOL_CYCLES.getOrDefault(tool, 0L);
        }
        return sum;
    }

    /** Total cycles a breach attempt holds: the session baseline plus every equipped tool. */
    public static long attemptCycles(SoloSave save) {
        return Balance.BREACH_SESSION_CYCLES + loadoutCycles(save);
    }

    public static boolean owns(SoloSave save, String toolId) {
        return loadout(save).contains(toolId);
    }

    /**
     * Plants the scripted tutorial miner on a new character's rig.
     *
     * <p>{@code docs/design/04-mining.md} §5.1, established: "cracking is the strongest early-game
     * teaching vector for the core minigame — self-contained, on the player's own machine, visible
     * reward, comprehensible failure, no heat cost for losing. The tutorial flow should
     * <em>plant</em> a weak scripted miner early."
     *
     * <p>It does three jobs at once and each one was otherwise unserved. It gives the breach a target
     * that exists in the first session, without recon, without a network and without a purchase. It
     * gives the <em>scan</em> something true to be wrong about, which is what makes {@code
     * docs/design/04-mining.md} §3.2a's false-positive rate a lesson rather than a nuisance. And
     * because it holds a real {@code DEPLOYED_MINER} allocation, it makes §3.1's manual audit work
     * against real data — the cycle totals genuinely do not add up, which is the discrepancy that
     * section says must always be present.
     *
     * <p>⚠ Not rootkit-wrapped, deliberately. A hidden first miner would teach the player that scans
     * do not work before it taught them what a scan is.
     *
     * <p>⚠ <b>Charging its cycles is what makes it honest, and it lowers a new rig's free capacity
     * by {@link Balance#TUTORIAL_MINER_HOST_CYCLES}.</b> Invariant I6 puts a deployed miner's cost on
     * the host, and a parasite charged to nobody would leave no discrepancy to find. The client
     * already renders {@code DEPLOYED_MINER} as "Foreign miner / on your rig"; it had simply never
     * had one to render.
     *
     * <p>Called from {@code SoloGame.newCharacter} and nowhere else. Existing saves are deliberately
     * not retro-fitted — mutating a character somebody has already played is worse than a missing
     * tutorial, and those players reach the breach through their known nodes.
     *
     * @return the planted miner, so a caller can log it
     */
    public static MinerState plantTutorialMiner(SoloSave save, Instant now) {
        MinerState miner = new MinerState();
        miner.hostCycles = Balance.TUTORIAL_MINER_HOST_CYCLES;
        miner.tier = Balance.TUTORIAL_MINER_TIER;
        miner.label = "unregistered process";
        miner.deployerHandle = "unknown";
        miner.rootkitWrapped = false;
        miner.deployedAt = now;
        miner.lastAccruedAt = now;

        AllocationState allocation = ComputeRules.reserve(
                save.rig, ComputeConsumer.DEPLOYED_MINER, miner.label, miner.hostCycles);
        if (allocation != null) {
            allocation.startedAt = now;
            miner.allocationId = allocation.allocationId;
        }
        // The costume it wears in the process table, chosen once and then a fact about this miner.
        // Drawn here rather than at first render, because a disguise that changed between repaints
        // would be unfindable by construction — see Disguise.
        Rng rng = Rng.of(save);
        io.github.stoicswe.eyeandsickle.solo.proc.Disguise.dress(save, miner, rng);
        rng.commit(save);
        save.rig.foreignMiners.add(miner);
        return miner;
    }
}
