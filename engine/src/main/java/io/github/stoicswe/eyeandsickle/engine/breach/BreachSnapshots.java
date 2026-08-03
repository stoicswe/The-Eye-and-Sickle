package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.AttentionBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachActionKind;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachResolution;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.LayerOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.MatrixBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.ResolutionRecord;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import io.github.stoicswe.eyeandsickle.engine.state.AttentionEntryState;
import io.github.stoicswe.eyeandsickle.engine.state.BreachState;
import io.github.stoicswe.eyeandsickle.engine.state.LayerState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the persisted breach into the immutable view the client renders.
 *
 * <h2>⚠ This class is the one place a puzzle answer could leak, and it is built so it cannot</h2>
 *
 * The rule (D-2) is that <b>a snapshot carries only revealed information</b>, and the two puzzles
 * that replaced the original three make it easier to keep rather than harder — because neither of
 * them has a secret. Breach Protocol publishes its whole grid and every goal because it is a routing
 * game, and the cipher publishes both hex rows because the arithmetic <em>is</em> the game.
 *
 * <p>⚠ What must still never appear is a <b>solution</b>. For the grid that means no reachable-goal
 * count, no "best next cell" and no marker on the cell a solver would take; for the cipher it means
 * the computed offsets are never published, only the two rows they are derived from. Both are
 * enforced the same way the old boards were: the field is <em>not read here</em> rather than read and
 * filtered. {@code OffsetRules.expected} exists and this class does not call it.
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
    public static BreachSnapshot of(GameSave save) {
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
    /**
     * The board for a layer, or {@code null} when the layer has not been reached.
     *
     * <p>A pending layer publishes no board at all. The later layers of a target are not information
     * the player has bought, and handing over a tier-5 cipher's sixteen bytes while they are still on
     * layer 0 would be free planning time the design never sold them.
     */
    private static BreachBoard board(LayerState layer, PuzzleClass puzzleClass) {
        if (!"ACTIVE".equals(layer.state) && !"CLEARED".equals(layer.state) && !"FAILED".equals(layer.state)) {
            return null;
        }
        return puzzleClass == PuzzleClass.OFFSET_CIPHER ? cipher(layer) : matrix(layer);
    }

    /**
     * The protocol grid, whole.
     *
     * <p>⚠ Nothing is computed here beyond re-shaping the flat lists the save stores into the nested
     * ones the renderer indexes. In particular there is no "which goals are still reachable from
     * here" — that is the question the player is being asked, and answering it in the read model
     * would be the same leak as publishing a Logic code.
     */
    private static MatrixBoard matrix(LayerState layer) {
        int size = Math.max(1, layer.matrixSize);
        List<List<String>> grid = new ArrayList<>();
        List<List<Boolean>> used = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            List<String> codes = new ArrayList<>();
            List<Boolean> taken = new ArrayList<>();
            for (int column = 0; column < size; column++) {
                int cell = row * size + column;
                codes.add(cell < layer.matrixGrid.size() ? layer.matrixGrid.get(cell) : "00");
                taken.add(cell < layer.matrixUsed.size() && layer.matrixUsed.get(cell));
            }
            grid.add(codes);
            used.add(taken);
        }

        List<MatrixBoard.Goal> goals = new ArrayList<>();
        for (int goal = 0; goal < layer.matrixGoalLabels.size(); goal++) {
            goals.add(new MatrixBoard.Goal(
                    layer.matrixGoalLabels.get(goal),
                    MatrixRules.goalCodes(layer, goal),
                    layer.matrixGoalMatched.get(goal),
                    layer.matrixGoalSolved.get(goal),
                    layer.matrixGoalRewards.get(goal)));
        }

        return new MatrixBoard(
                grid,
                used,
                List.copyOf(layer.matrixBuffer),
                layer.matrixBufferSize,
                layer.matrixRowTurn,
                layer.matrixCursorRow,
                layer.matrixCursorColumn,
                goals);
    }

    /**
     * The cipher, both rows and whatever the player has typed.
     *
     * <p>⚠ {@code OffsetRules.expected} is deliberately not called from here. The answer is derivable
     * from the two rows this publishes — that is the point of the puzzle — but derivable by the
     * player is not the same as shipped in the read model, and a field carrying it would be one
     * refactor away from a renderer printing it.
     */
    private static OffsetBoard cipher(LayerState layer) {
        return new OffsetBoard(
                List.copyOf(layer.cipherObserved),
                List.copyOf(layer.cipherTarget),
                layer.cipherEntered,
                List.copyOf(layer.cipherWrong),
                layer.cipherCursor,
                "",
                layer.cipherCommits,
                layer.cipherGiven == null ? java.util.List.of() : List.copyOf(layer.cipherGiven));
    }

    /**
     * The finished attempt, or {@code null} while it is still running.
     *
     * <p>The class recorded is the <b>deepest layer reached</b>, which under one-puzzle-per-attempt
     * is simply the attempt's class — kept as a walk anyway so that a future multi-class attempt
     * records what the player actually got to rather than what they started on. Proof-of-skill reads
     * this ({@code docs/design/02} §2.4, Invariant I7).
     */
    private static BreachResolution resolution(BreachState breach) {
        if (breach.outcome.isEmpty()) {
            return null;
        }
        int spent = 0;
        int budget = 0;
        String deepest =
                breach.layers.isEmpty() ? PuzzleClass.BREACH_PROTOCOL.name() : breach.layers.getFirst().puzzleClass;
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
                        puzzle(deepest),
                        DifficultyTier.of(breach.difficultyTier),
                        TargetState.valueOf(breach.liveOrDormant),
                        BreachOutcome.valueOf(breach.outcome)),
                breach.resolvedNoise,
                traceProgress,
                breach.resolvedHeat,
                breach.resolvedLootWei,
                breach.resolvedLootLabel,
                breach.resolvedSchematicMaterial,
                List.copyOf(breach.consequences));
    }

    /**
     * A puzzle class name from a save, tolerantly.
     *
     * <p>⚠ A character saved mid-breach under the old three-class engine carries {@code "LOGIC"} or
     * {@code "TRAVERSAL"} here, and {@code valueOf} would throw on load — turning a retired minigame
     * into a save that will not open. It reads as Breach Protocol instead, which is honest enough for
     * a record of an attempt that no longer has a board to be played on, and {@code GameEngine.open}
     * abandons any live breach anyway.
     */
    private static PuzzleClass puzzle(String name) {
        try {
            return PuzzleClass.valueOf(name);
        } catch (IllegalArgumentException retired) {
            return PuzzleClass.BREACH_PROTOCOL;
        }
    }

    private static BreachActionKind kind(String name) {
        try {
            return BreachActionKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BreachActionKind.PROBE;
        }
    }
}
