package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LatticeNodeState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import java.util.ArrayList;
import java.util.List;

/**
 * The Traversal class: route through an internal graph to the data node.
 *
 * <h2>The human read is the whole class (D-9)</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3.2 specifies it verbatim: "the true objective node
 * hidden among decoys distinguishable only by cross-referencing recovered logs". So:
 *
 * <ul>
 *   <li>the manifest names one service and one time, and is public from the start;
 *   <li>each objective candidate carries a log fragment naming a service and a time, recoverable
 *       with {@code listen} at 1 attention;
 *   <li>exactly one candidate matches <em>both</em> fields. The others match one or neither.
 * </ul>
 *
 * A player who cross-references extracts once. A fixed heuristic that cannot read must extract at
 * random among K candidates: {@code (K+1)/2} attempts on average, at 2 attention each, plus a strike
 * per miss and a canary on any trapped decoy. <b>That difference is P-3</b> — the number behind
 * Invariant I10, which {@code 05} §4 made answerable at all by denominating the bot-versus-human gap
 * in probes rather than seconds.
 *
 * <p>⚠ It is built to be measured, not tuned. If the gap turns out too small the fix is a larger K
 * or a subtler decoy, never a cheaper extract — cheapening the miss is exactly what would make the
 * heuristic competitive and the reading pointless.
 *
 * <h2>The failure mode this class punishes is committing to a branch</h2>
 *
 * {@code back} costs the same 2 attention a {@code step} does, so a wrong branch is paid for twice:
 * once going in and once coming out. That is what makes {@code listen} at 1 worth taking before you
 * move rather than after.
 */
public final class TraversalRules {

    private TraversalRules() {}

    static Move act(LayerState layer, String actionId, String argument, boolean topologyMapper) {
        return switch (actionId) {
            case "listen" -> listen(layer, argument);
            case "step" -> step(layer, argument, topologyMapper);
            case "traceroute" -> traceroute(layer);
            case "extract" -> extract(layer, argument);
            case "back" -> back(layer);
            default -> Move.of("nothing happened");
        };
    }

    /** Recovers one adjacent node's log fragment. The cheapest action, and the one that wins the layer. */
    private static Move listen(LayerState layer, String argument) {
        LatticeNodeState node = node(layer, argument);
        if (node == null) {
            return Move.of("no such node: " + argument);
        }
        if (!adjacent(layer, node) && !node.id.equals(layer.currentNodeId)) {
            return Move.of(node.label + " is not within earshot from here");
        }
        if (node.hint.isEmpty()) {
            return Move.of(node.label + " keeps no logs worth reading");
        }
        node.hintRead = true;
        node.visible = true;
        return Move.of(node.label + ": " + node.hint);
    }

    /**
     * Moves to an adjacent node and reveals what it can see.
     *
     * <p>The Topology Mapper doubles the reveal, which is exactly its published function — {@code
     * docs/design/07-recon-tools.md} §1: "extends graph visibility from one hop to two", and §2 calls
     * that "a <b>ceiling</b> on information, hence schematic-gated not purchasable (Invariant I2)"
     * and notes that "two-hop vision fundamentally changes Traversal-class planning". This is where
     * that change is.
     */
    private static Move step(LayerState layer, String argument, boolean topologyMapper) {
        LatticeNodeState node = node(layer, argument);
        if (node == null) {
            return Move.of("no such node: " + argument);
        }
        if (!adjacent(layer, node)) {
            return Move.of("no route from here to " + node.label);
        }
        layer.currentNodeId = node.id;
        node.visited = true;
        node.visible = true;

        int revealed = illuminate(layer, node, topologyMapper ? 2 : 1);
        return Move.of("at " + node.label + "; " + revealed + (revealed == 1 ? " route" : " routes") + " visible");
    }

    /**
     * Reveals the outbound subtree two ranks ahead, including trap flags. Loud.
     *
     * <p>Six attention buys what a Topology Mapper gives away for free on every step — plus the trap
     * flags, which nothing else in the class reveals at all. That is {@code
     * docs/design/05-hacking-minigame.md} §4's loud tool exactly: not more efficient, just faster and
     * more visible, and the only way to see a canary before you touch it.
     */
    private static Move traceroute(LayerState layer) {
        LatticeNodeState here = node(layer, layer.currentNodeId);
        if (here == null) {
            return Move.of("nothing to trace from here");
        }
        int revealed = illuminate(layer, here, Balance.BREACH_TRACEROUTE_RANKS);
        int traps = 0;
        for (LatticeNodeState node : layer.nodes) {
            if (node.visible && node.trapped && !node.trapKnown) {
                node.trapKnown = true;
                traps++;
            }
        }
        return Move.of(revealed + " nodes mapped ahead"
                + (traps > 0 ? "; " + traps + (traps == 1 ? " trap" : " traps") + " flagged" : "; nothing flagged"));
    }

    /**
     * Extracts from an objective candidate.
     *
     * <p>Legal against any candidate the player can see, not only the one they are standing on — a
     * rule that forced a step before every attempt would make each guess cost 4 rather than 2 and
     * would move the {@code (K+1)/2} arithmetic that P-3 is measured against. See the class note: the
     * cost of a miss is a measurement instrument and is not free to change.
     *
     * <p>A trapped decoy fires a canary. {@code docs/design/09-defense-and-hardening.md} §2: a
     * canary "both alerts you <em>and</em> tags the toucher's handle", and that tag "feeds directly
     * into the evidence and informant systems ({@code 12})". So the consequence outlives the attempt
     * — it is the one thing in this class that a player cannot walk away from.
     */
    private static Move extract(LayerState layer, String argument) {
        LatticeNodeState node = node(layer, argument);
        if (node == null) {
            return Move.of("no such node: " + argument);
        }
        if (!node.objectiveCandidate) {
            return Move.of(node.label + " holds nothing worth extracting");
        }
        if (!node.visible) {
            return Move.of("you cannot reach " + node.label + " from where you are standing");
        }
        if (node.id.equals(layer.objectiveNodeId)) {
            layer.currentNodeId = node.id;
            node.visited = true;
            return Move.cleared("extracted from " + node.label + " - this was the one");
        }
        node.visited = true;
        if (node.trapped) {
            node.trapKnown = true;
            return Move.canary(
                    node.label + " was a decoy, and it was wired",
                    "a canary token on " + node.label + " tagged your handle",
                    Balance.NOISE_PER_ALARM);
        }
        return Move.strike(node.label + " was a decoy");
    }

    /**
     * Steps back one rank.
     *
     * <p>Costs the same as going forward, and that symmetry is the class's punished failure mode:
     * a wrong branch is paid for twice. Nothing is refunded for realising a mistake, which is why
     * the 1-attention {@code listen} before a 2-attention {@code step} is the whole skill.
     */
    private static Move back(LayerState layer) {
        LatticeNodeState here = node(layer, layer.currentNodeId);
        if (here == null || here.rank == 0) {
            return Move.of("you are already at the entry");
        }
        LatticeNodeState previous = null;
        for (LatticeNodeState candidate : layer.nodes) {
            if (candidate.visited && candidate.exits.contains(here.id) && candidate.rank < here.rank) {
                previous = candidate;
                break;
            }
        }
        if (previous == null) {
            return Move.of("there is no way back from here that you have already walked");
        }
        layer.currentNodeId = previous.id;
        return Move.of("back at " + previous.label);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Makes everything within {@code depth} hops of {@code from} visible.
     *
     * @return how many nodes this newly revealed
     */
    private static int illuminate(LayerState layer, LatticeNodeState from, int depth) {
        List<LatticeNodeState> frontier = new ArrayList<>(List.of(from));
        int revealed = 0;
        for (int hop = 0; hop < depth; hop++) {
            List<LatticeNodeState> next = new ArrayList<>();
            for (LatticeNodeState node : frontier) {
                for (String exit : node.exits) {
                    LatticeNodeState target = node(layer, exit);
                    if (target == null) {
                        continue;
                    }
                    if (!target.visible) {
                        target.visible = true;
                        revealed++;
                    }
                    next.add(target);
                }
            }
            frontier = next;
        }
        return revealed;
    }

    /** Whether {@code node} is one exit away from where the player is standing. */
    private static boolean adjacent(LayerState layer, LatticeNodeState node) {
        LatticeNodeState here = node(layer, layer.currentNodeId);
        return here != null && here.exits.contains(node.id);
    }

    static LatticeNodeState node(LayerState layer, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String wanted = id.trim();
        for (LatticeNodeState node : layer.nodes) {
            // Accepts either the stable id or the hostname the player can actually see, because the
            // hostname is what the manifest and the log fragments print and therefore what a player
            // typing into the shell will reach for.
            if (node.id.equals(wanted) || (node.visible && node.label.equals(wanted))) {
                return node;
            }
        }
        return null;
    }

    /** Extra attention a node charges on entry, on top of an ordinary step. */
    static int stepCostOf(LayerState layer, String nodeId) {
        LatticeNodeState node = node(layer, nodeId);
        return node == null ? 0 : Math.max(0, node.stepCost);
    }
}
