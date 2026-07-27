package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.AttentionBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BandReading;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachActionKind;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachResolution;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.EnumerationBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.LatticeNode;
import io.github.stoicswe.eyeandsickle.protocol.game.LayerOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicProbe;
import io.github.stoicswe.eyeandsickle.protocol.game.PortSlot;
import io.github.stoicswe.eyeandsickle.protocol.game.PortState;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.ResolutionRecord;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import io.github.stoicswe.eyeandsickle.protocol.game.TraversalBoard;
import io.github.stoicswe.eyeandsickle.solo.state.AttentionEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.BreachState;
import io.github.stoicswe.eyeandsickle.solo.state.LatticeNodeState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.PortSlotState;
import io.github.stoicswe.eyeandsickle.solo.state.ProbeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the persisted breach into the immutable view the client renders.
 *
 * <h2>⚠ This class is the one place a puzzle answer could leak, and it is built so it cannot</h2>
 *
 * The rule (D-2) is that <b>a snapshot carries only revealed information</b>. The Logic code, the
 * true port states, the true objective node and the trap flags never appear in one. Unknown is
 * encoded as {@link PortState#UNKNOWN}, as {@code ""}, or by absence from a list — never as
 * {@code null}, because every list on the wire is defensively copied with {@code List.copyOf}, which
 * rejects nulls.
 *
 * <p>It is written so that a hidden field is <b>not in scope</b> rather than filtered out:
 * {@link PortSlotState#revealed} is read before {@link PortSlotState#truth}, {@code hintRead} before
 * {@code hint}, {@code trapKnown} before {@code trapped}, and {@code LayerState.secret},
 * {@code factDeck} and {@code objectiveNodeId} are not read at all. A builder that reads a secret and
 * then decides not to publish it is one careless edit from publishing it; a builder that never reads
 * it is not.
 *
 * <p>The obvious objection — this is a save file the player can already open in a text editor — is
 * answered by what these records are <em>for</em>. They are protocol types (D-1) because a real home
 * server will send exactly this shape over a wire, where the client is never authoritative
 * (Invariant I14). Getting the discipline right here means the client physically cannot render a
 * cheat when the same records arrive from somewhere the player does not control.
 */
public final class BreachSnapshots {

    private BreachSnapshots() {}

    /** The open breach, or null when there is none. */
    public static BreachSnapshot of(SoloSave save) {
        BreachState breach = save.activeBreach;
        if (breach == null) {
            return null;
        }
        List<BreachLayer> layers = new ArrayList<>();
        for (LayerState layer : breach.layers) {
            layers.add(layer(layer));
        }
        List<AttentionEntry> ledger = new ArrayList<>();
        for (AttentionEntryState entry : breach.ledger) {
            ledger.add(new AttentionEntry(
                    entry.sequence,
                    entry.layerIndex,
                    entry.actionId,
                    kind(entry.kind),
                    entry.label,
                    entry.cost,
                    entry.spentAfter,
                    entry.result,
                    entry.alarm));
        }
        return new BreachSnapshot(
                breach.breachId,
                breach.targetId,
                breach.targetLabel,
                DifficultyTier.of(breach.difficultyTier),
                TargetState.valueOf(breach.liveOrDormant),
                breach.minerCrack,
                breach.outcome.isEmpty() ? breach.activeLayer : -1,
                layers,
                BreachRules.actions(save),
                ledger,
                breach.noise,
                breach.reservedCycles,
                resolution(breach));
    }

    private static BreachLayer layer(LayerState layer) {
        PuzzleClass puzzleClass = PuzzleClass.valueOf(layer.puzzleClass);
        return new BreachLayer(
                layer.index,
                puzzleClass,
                "LAYER " + layer.index + " - " + layer.puzzleClass,
                new AttentionBudget(Math.min(layer.spent, layer.budget), Math.max(1, layer.budget)),
                LayerOutcome.valueOf(layer.state),
                layer.strikes,
                layer.strikeLimit,
                layer.probesUsed,
                board(layer, puzzleClass));
    }

    /**
     * The board, or null on a layer the player has not reached.
     *
     * <p>A pending layer's board exists — every layer is generated at {@code begin} (D-4) — but it is
     * not published. Sending it would hand over three layers' worth of answers the moment the attempt
     * opened, which is the same leak as sending the secret, arriving one indirection later.
     */
    private static BreachBoard board(LayerState layer, PuzzleClass puzzleClass) {
        if ("PENDING".equals(layer.state)) {
            return null;
        }
        return switch (puzzleClass) {
            case LOGIC -> logic(layer);
            case TRAVERSAL -> traversal(layer);
            default -> enumeration(layer);
        };
    }

    private static EnumerationBoard enumeration(LayerState layer) {
        List<PortSlot> ports = new ArrayList<>();
        for (PortSlotState slot : layer.ports) {
            // ⚠ revealed FIRST. An unrevealed slot's truth is not read, so it cannot be published by
            // a later edit that forgets which branch it is in.
            if (slot.revealed) {
                ports.add(new PortSlot(slot.index, PortState.valueOf(slot.truth), slot.service));
            } else {
                ports.add(new PortSlot(slot.index, PortState.UNKNOWN, ""));
            }
        }
        List<BandReading> readings = new ArrayList<>();
        for (int[] reading : layer.readingRanges) {
            readings.add(new BandReading(reading[0], reading[1], reading[2]));
        }
        return new EnumerationBoard(
                layer.banner,
                layer.bannerNote,
                layer.slots,
                layer.bandSize,
                ports,
                readings,
                layer.knownOpenTotal,
                List.copyOf(layer.declared));
    }

    private static LogicBoard logic(LayerState layer) {
        // ⚠ layer.secret and layer.factDeck are not touched anywhere in this method. Everything
        // below is either the player's own working or a response they already paid for.
        List<LogicProbe> history = new ArrayList<>();
        for (ProbeState probe : layer.probes) {
            history.add(new LogicProbe(
                    probe.sequence,
                    List.copyOf(probe.guess),
                    probe.exact,
                    probe.partial,
                    probe.volley,
                    probe.inconsistent));
        }
        List<String> facts = new ArrayList<>();
        for (String card : layer.facts) {
            // The prose only. The machine form behind it is scaffolding for the candidate filter and
            // has no business on a wire — see Facts.
            facts.add(Facts.prose(card));
        }
        return new LogicBoard(
                layer.secret.size(),
                List.copyOf(layer.alphabet),
                layer.salted,
                history,
                facts,
                List.copyOf(layer.known),
                List.copyOf(layer.draft),
                layer.keyspace,
                layer.candidatesRemaining);
    }

    private static TraversalBoard traversal(LayerState layer) {
        // ⚠ layer.objectiveNodeId is not read here. The client renders K identical candidates and
        // has no way to tell them apart, which is exactly the state the human read exists to resolve.
        List<LatticeNode> nodes = new ArrayList<>();
        for (LatticeNodeState node : layer.nodes) {
            if (!node.visible) {
                // Invisible nodes are published as a shape with nothing in them: the player can see
                // that the lattice continues, and nothing about what is there.
                nodes.add(new LatticeNode(
                        node.id, node.rank, node.index, "", List.of(), false, false, false, "", false, 0));
                continue;
            }
            nodes.add(new LatticeNode(
                    node.id,
                    node.rank,
                    node.index,
                    node.label,
                    List.copyOf(node.exits),
                    node.visited,
                    true,
                    node.objectiveCandidate,
                    node.hintRead ? node.hint : "",
                    node.trapKnown,
                    node.stepCost));
        }
        return new TraversalBoard(
                layer.ranks, layer.objectiveRank, layer.currentNodeId, nodes, List.copyOf(layer.manifest));
    }

    /** Null while the attempt is live; the slate once it has resolved and not yet been dismissed. */
    private static BreachResolution resolution(BreachState breach) {
        if (breach.outcome.isEmpty()) {
            return null;
        }
        int spent = 0;
        int budget = 0;
        String deepest = breach.layers.isEmpty() ? "ENUMERATION" : breach.layers.getFirst().puzzleClass;
        for (LayerState layer : breach.layers) {
            spent += Math.min(layer.spent, layer.budget);
            budget += layer.budget;
            if (!"PENDING".equals(layer.state)) {
                deepest = layer.puzzleClass;
            }
        }
        double traceProgress = budget <= 0 ? 0.0d : spent / (double) budget;
        return new BreachResolution(
                new ResolutionRecord(
                        PuzzleClass.valueOf(deepest),
                        DifficultyTier.of(breach.difficultyTier),
                        TargetState.valueOf(breach.liveOrDormant),
                        BreachOutcome.valueOf(breach.outcome)),
                breach.resolvedNoise,
                traceProgress,
                breach.resolvedHeat,
                breach.resolvedLootMinorUnits,
                breach.resolvedLootLabel,
                breach.resolvedSchematicMaterial,
                List.copyOf(breach.consequences));
    }

    /**
     * Reads a persisted kind name, defaulting rather than throwing.
     *
     * <p>A save is a document that outlives the code that wrote it, and a ledger row is history. An
     * unknown kind on one row must not make the whole attempt unreadable — the row's cost, result and
     * alarm flag are all still true, and they are the parts that explain a loss.
     */
    private static BreachActionKind kind(String name) {
        try {
            return BreachActionKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BreachActionKind.PROBE;
        }
    }
}
