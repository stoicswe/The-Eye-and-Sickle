package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.LayerState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Instant;
import java.util.ArrayList;
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
     * <p>⚠ The scrub is not tidiness. {@code GameEngine.newCharacter} plants the scripted tutorial
     * miner ({@code docs/design/04-mining.md} §5.1), which puts a crack target at the head of
     * {@code Targets.available} for <em>every</em> save and holds a {@code DEPLOYED_MINER}
     * allocation against the rig. A test that then said {@code available(save).getFirst()} would
     * silently be testing a tier-1 crack instead of the node it set up, and a test that
     * planted its own miner would be working with two.
     *
     * <p>Written this way so the suite says what it means whether or not the plant is wired — the
     * plant lives in {@code GameEngine}, which this lane does not own.
     */
    static GameSave save(long seed) {
        GameSave save = GameEngine.newCharacter("operator", T0);
        save.rngSeed = seed;
        for (var miner : List.copyOf(save.rig.foreignMiners)) {
            ComputeRules.release(save.rig, miner.allocationId);
        }
        save.rig.foreignMiners.clear();
        return save;
    }

    /**
     * The one crack target on this rig — a foreign miner, never a node ({@code 04} §5.1).
     *
     * <p>⚠ Marks every parasite <b>discovered</b> first, because an unaudited one is not a target at
     * all ({@code Targets.available}). These tests are about what a crack DOES; the audit that makes
     * one available is {@code GameEngineTest}'s subject, and threading a scan through every fixture
     * here would bury the behaviour under an unrelated pipeline.
     */
    static BreachTarget crackTarget(GameSave save) {
        for (var miner : save.rig.foreignMiners) {
            miner.discovered = true;
        }
        return Targets.available(save).stream()
                .filter(BreachTarget::minerCrack)
                .findFirst()
                .orElseThrow();
    }

    /** The one offensive target on this rig — a known node, never a parasite. */
    static BreachTarget nodeTarget(GameSave save) {
        return Targets.available(save).stream()
                .filter(target -> !target.minerCrack())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Fills in a machine's port-scan report completely, as if every finding had been established.
     *
     * <p>⚠ Needed by any fixture that wants <b>Breach Protocol</b>. The offset cipher is the default
     * against a machine nothing is known about, and the protocol grid's odds rise with the report —
     * so a fixture that skips this is asking for a puzzle it has a zero percent chance of drawing,
     * and would loop through every seed and throw. Writing {@code learnedAt} directly is the same
     * kind of scaffolding as the rest of this class: it reaches past the scan pipeline, which is
     * {@code NodeReportTest}'s subject rather than this lane's.
     */
    static void fullyScanned(GameSave save, String address) {
        NodeReportState report = new NodeReportState();
        report.address = address;
        report.createdAt = T0;
        report.updatedAt = T0;
        report.scans = 1;
        for (var target : io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.values()) {
            report.learnedAt.put(target.name(), T0);
        }
        save.nodeReports.add(report);
    }

    /** A save holding one known node with the given tier and defence profile. */
    static GameSave withNode(long seed, int tier, int firewallTier, boolean tarpit, boolean canaries) {
        GameSave save = save(seed);
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

    static void give(GameSave save, String toolId) {
        ItemState item = new ItemState();
        item.itemType = toolId;
        item.displayName = toolId;
        save.items.add(item);
    }

    static LayerState active(GameSave save) {
        for (LayerState layer : save.activeBreach.layers) {
            if ("ACTIVE".equals(layer.state)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * A save with an open attempt at {@code tier} that definitely contains a layer of the given
     * class, already focused.
     *
     * <h2>⚠ Why this searches for a seed instead of picking one</h2>
     *
     * The class is drawn per layer at commission, so whether a given seed produces a cipher is
     * luck. A test that hard-codes a seed and calls {@code focus} passes today and starts skipping
     * silently the moment anything upstream of the draw changes the RNG's position — and a test that
     * skips is a test that reports success. Searching costs milliseconds and cannot go quiet.
     */
    static GameSave attemptWith(String puzzleClass, int tier) {
        for (long seed = 1; seed <= 500; seed++) {
            GameSave save = withNode(seed, tier, 0, false, false);
            // ⚠ A full report, always — for BREACH_PROTOCOL because it is otherwise unreachable, and
            // for OFFSET_CIPHER because a fixture whose puzzle depends on what it happened to know
            // is a fixture that changes meaning when the weighting is re-tuned. At full knowledge
            // both still occur (the informed share is 0.95, not 1.0), so the search finds either.
            fullyScanned(save, "10.0.0.5");
            BreachRules.begin(save, nodeTarget(save), T0);
            if (focus(save, puzzleClass) != null) {
                return save;
            }
        }
        throw new AssertionError("no " + puzzleClass + " layer at tier " + tier + " in 500 seeds");
    }

    /** Forces the layer of the given class to be the active one, clearing everything before it. */
    static LayerState focus(GameSave save, String puzzleClass) {
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

    /**
     * Clears the active layer by reading its answer. Test scaffolding; never a legal game move.
     *
     * <p>⚠ The two classes cheat in different ways, and only one of them <em>can</em>. The cipher's
     * answer is arithmetic over two published rows, so {@link OffsetRules#expected} derives it — a
     * player could do the same, slowly, and that is the puzzle. The grid has no answer to read at
     * all: it is an open-information routing problem, so the only way to clear it is to actually
     * search for a path, which is what {@link #matrixPath} does. That asymmetry is worth noticing —
     * if a shortcut ever appears for the grid, something has started publishing a solution.
     */
    static void solveActiveLayer(GameSave save) {
        LayerState layer = active(save);
        if (layer == null) {
            return;
        }
        if ("OFFSET_CIPHER".equals(layer.puzzleClass)) {
            for (int i = 0; i < layer.cipherObserved.size(); i++) {
                BreachRules.act(save, OffsetRules.TYPE, i + ":" + OffsetRules.expected(layer, i), T0);
            }
            BreachRules.act(save, OffsetRules.COMMIT, "", T0);
            return;
        }
        for (int[] cell : matrixPath(layer)) {
            if (active(save) != layer) {
                return;
            }
            BreachRules.act(save, MatrixRules.PICK, cell[0] + ":" + cell[1], T0);
        }
    }

    /**
     * A legal walk that lands the layer's first sequence in the buffer, or an empty list.
     *
     * <h2>Why this is a search and not a lookup</h2>
     *
     * {@code BoardFactory} cuts each goal out of a walk it actually took, so a solution provably
     * exists — but it does not store the walk, and it must not: a save that carried the answer would
     * be a save a player could read. So the test does the same work a player does, on a copy of the
     * board.
     *
     * <p>The prune is what keeps it instant. A branch is abandoned the moment the buffer has fewer
     * slots left than the sequence has codes still to place, which collapses a nominally
     * {@code size^bufferSize} space to a handful of live paths on every board this game generates.
     */
    static List<int[]> matrixPath(LayerState layer) {
        if (layer.matrixGoalLengths.isEmpty()) {
            return List.of();
        }
        List<String> goal = MatrixRules.goalCodes(layer, 0);
        boolean[] used = new boolean[layer.matrixSize * layer.matrixSize];
        for (int i = 0; i < used.length && i < layer.matrixUsed.size(); i++) {
            used[i] = layer.matrixUsed.get(i);
        }
        List<int[]> path = new ArrayList<>();
        return walk(
                        layer,
                        goal,
                        new ArrayList<>(layer.matrixBuffer),
                        used,
                        layer.matrixRowTurn,
                        layer.matrixCursorRow,
                        layer.matrixCursorColumn,
                        path)
                ? path
                : List.of();
    }

    private static boolean walk(
            LayerState layer,
            List<String> goal,
            List<String> buffer,
            boolean[] used,
            boolean rowTurn,
            int cursorRow,
            int cursorColumn,
            List<int[]> path) {
        if (MatrixRules.contains(buffer, goal)) {
            return true;
        }
        int left = layer.matrixBufferSize - buffer.size();
        if (left < goal.size() - MatrixRules.trailingMatch(buffer, goal)) {
            return false;
        }
        int size = layer.matrixSize;
        for (int line = 0; line < size; line++) {
            int row = rowTurn ? cursorRow : line;
            int column = rowTurn ? line : cursorColumn;
            int index = row * size + column;
            if (used[index]) {
                continue;
            }
            used[index] = true;
            buffer.add(layer.matrixGrid.get(index));
            path.add(new int[] {row, column});
            if (walk(layer, goal, buffer, used, !rowTurn, row, column, path)) {
                return true;
            }
            path.removeLast();
            buffer.removeLast();
            used[index] = false;
        }
        return false;
    }

    /**
     * One paid move on whichever board is active, chosen to be <em>wrong</em>.
     *
     * <h2>Why the suite needs this at all</h2>
     *
     * Half these tests are about what happens when a player loses — the ledger, the consequence
     * lines, the dead-man switch — and losing has to be reachable without knowing which puzzle the
     * attempt drew. Both classes offer exactly one paid move that can be made badly on purpose: a
     * commit of a row of zeroes (no column's answer is ever zero), and a pick that spends a buffer
     * slot on nothing.
     */
    static void spendOneBadly(GameSave save) {
        LayerState layer = active(save);
        if (layer == null) {
            return;
        }
        if ("OFFSET_CIPHER".equals(layer.puzzleClass)) {
            for (int i = 0; i < layer.cipherObserved.size(); i++) {
                // Zero is never the answer — BoardFactory draws the target as a non-zero step from
                // the observed byte — so a row of them is reliably, entirely wrong.
                BreachRules.act(save, OffsetRules.TYPE, i + ":0", T0);
            }
            BreachRules.act(save, OffsetRules.COMMIT, "", T0);
            return;
        }
        int[] cell = firstLegal(layer);
        if (cell != null) {
            BreachRules.act(save, MatrixRules.PICK, cell[0] + ":" + cell[1], T0);
        }
    }

    /** Plays badly until the attempt resolves, or gives up. */
    static void loseAll(GameSave save) {
        for (int guard = 0; guard < 300 && save.activeBreach.outcome.isEmpty(); guard++) {
            if (active(save) == null) {
                return;
            }
            spendOneBadly(save);
        }
    }

    /** The first cell the alternating rule allows, or {@code null} when the board is exhausted. */
    static int[] firstLegal(LayerState layer) {
        int size = layer.matrixSize;
        for (int line = 0; line < size; line++) {
            int row = layer.matrixRowTurn ? layer.matrixCursorRow : line;
            int column = layer.matrixRowTurn ? line : layer.matrixCursorColumn;
            if (!layer.matrixUsed.get(row * size + column)) {
                return new int[] {row, column};
            }
        }
        return null;
    }

    /** Plays an attempt through to a resolution. */
    static void solveAll(GameSave save) {
        for (int guard = 0; guard < 40 && save.activeBreach.outcome.isEmpty(); guard++) {
            if (active(save) == null) {
                return;
            }
            solveActiveLayer(save);
        }
    }
}
