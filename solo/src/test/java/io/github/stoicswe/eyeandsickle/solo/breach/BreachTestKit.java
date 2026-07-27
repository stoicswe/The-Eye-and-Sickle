package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.LatticeNodeState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.PortSlotState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.List;

/**
 * Shared scaffolding for the breach tests.
 *
 * <h2>⚠ Everything here reads the answer out of the save, which no game code may do</h2>
 *
 * {@link #solveActiveLayer} looks at {@code LayerState.secret} and {@code objectiveNodeId} — the two
 * fields {@code BreachSnapshots} is built never to read. That is legitimate in a test and nowhere
 * else: a test needs to reach a resolution deterministically without playing a puzzle, and the
 * puzzles are designed so that playing them is the only other way.
 *
 * <p>The separation is the point. If any of this could be done through a snapshot, the snapshot would
 * be leaking.
 */
final class BreachTestKit {

    static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");

    private BreachTestKit() {}

    /**
     * A save with a fixed seed and a genuinely bare rig.
     *
     * <p>⚠ The scrub is not tidiness. {@code SoloGame.newCharacter} plants the scripted tutorial
     * miner ({@code docs/design/04-mining.md} §5.1), which puts a crack target at the head of
     * {@code Targets.available} for <em>every</em> save and holds a {@code DEPLOYED_MINER}
     * allocation against the rig. A test that then said {@code available(save).getFirst()} would
     * silently be testing a tier-1 Enumeration crack instead of the node it set up, and a test that
     * planted its own miner would be working with two.
     *
     * <p>Written this way so the suite says what it means whether or not the plant is wired — the
     * plant lives in {@code SoloGame}, which this lane does not own.
     */
    static SoloSave save(long seed) {
        SoloSave save = SoloGame.newCharacter("operator", T0);
        save.rngSeed = seed;
        for (var miner : List.copyOf(save.rig.foreignMiners)) {
            ComputeRules.release(save.rig, miner.allocationId);
        }
        save.rig.foreignMiners.clear();
        return save;
    }

    /** The one crack target on this rig — a foreign miner, never a node ({@code 04} §5.1). */
    static BreachTarget crackTarget(SoloSave save) {
        return Targets.available(save).stream().filter(BreachTarget::minerCrack).findFirst().orElseThrow();
    }

    /** The one offensive target on this rig — a known node, never a parasite. */
    static BreachTarget nodeTarget(SoloSave save) {
        return Targets.available(save).stream()
                .filter(target -> !target.minerCrack())
                .findFirst()
                .orElseThrow();
    }

    /** A save holding one known node with the given tier and defence profile. */
    static SoloSave withNode(long seed, int tier, int firewallTier, boolean tarpit, boolean canaries) {
        SoloSave save = save(seed);
        NodeState node = new NodeState();
        node.address = "10.0.0.5";
        node.label = "relay";
        node.tier = tier;
        node.firewallTier = firewallTier;
        node.tarpit = tarpit;
        node.canaries = canaries;
        // Both required for LIVE: the node is defended AND recon has established that it is.
        node.trafficAnalyzed = true;
        node.defended = true;
        save.knownNodes.add(node);
        return save;
    }

    static void give(SoloSave save, String toolId) {
        ItemState item = new ItemState();
        item.itemType = toolId;
        item.displayName = toolId;
        save.items.add(item);
    }

    static LayerState active(SoloSave save) {
        for (LayerState layer : save.activeBreach.layers) {
            if ("ACTIVE".equals(layer.state)) {
                return layer;
            }
        }
        return null;
    }

    /** Forces the layer of the given class to be the active one, clearing everything before it. */
    static LayerState focus(SoloSave save, String puzzleClass) {
        for (LayerState layer : save.activeBreach.layers) {
            if (puzzleClass.equals(layer.puzzleClass)) {
                layer.state = "ACTIVE";
                save.activeBreach.activeLayer = layer.index;
                return layer;
            }
            layer.state = "CLEARED";
        }
        return null;
    }

    /** Clears the active layer by reading its answer. Test scaffolding; never a legal game move. */
    static void solveActiveLayer(SoloSave save) {
        LayerState layer = active(save);
        if (layer == null) {
            return;
        }
        switch (layer.puzzleClass) {
            case "LOGIC" -> {
                for (int i = 0; i < layer.secret.size(); i++) {
                    BreachRules.act(save, "set", (i + 1) + ":" + layer.secret.get(i), T0);
                }
                BreachRules.act(save, "probe", "", T0);
            }
            case "TRAVERSAL" -> {
                for (int guard = 0; guard < 20 && active(save) == layer; guard++) {
                    LatticeNodeState here = TraversalRules.node(layer, layer.currentNodeId);
                    if (here.rank >= layer.objectiveRank - 1) {
                        BreachRules.act(save, "extract", layer.objectiveNodeId, T0);
                    } else {
                        BreachRules.act(save, "step", here.exits.getFirst(), T0);
                    }
                }
            }
            default -> {
                for (PortSlotState slot : layer.ports) {
                    if ("OPEN".equals(slot.truth) && !layer.declared.contains(slot.index)) {
                        BreachRules.act(save, "mark", String.valueOf(slot.index), T0);
                    }
                }
                BreachRules.act(save, "declare", "", T0);
            }
        }
    }

    /** Plays an attempt through to a resolution. */
    static void solveAll(SoloSave save) {
        for (int guard = 0; guard < 40 && save.activeBreach.outcome.isEmpty(); guard++) {
            if (active(save) == null) {
                return;
            }
            solveActiveLayer(save);
        }
    }
}
