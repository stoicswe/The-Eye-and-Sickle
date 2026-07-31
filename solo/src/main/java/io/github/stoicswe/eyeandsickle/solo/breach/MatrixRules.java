package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import java.util.ArrayList;
import java.util.List;

/**
 * Breach Protocol: pick a code, the path turns, the buffer fills, and nothing can be taken back.
 *
 * <h2>The whole game in one sentence</h2>
 *
 * Every pick both <em>appends</em> to a buffer that cannot be emptied and <em>constrains</em> the
 * next pick to a single row or column — so the player is choosing a path through a grid where each
 * step decides what the next step may be, with a hard limit on how many steps there are.
 *
 * <h2>⚠ Open information, and it stays open</h2>
 *
 * The grid, the goals and the buffer are all published from the first frame ({@code MatrixBoard}), so
 * this class has nothing to hide and no secret to keep. That makes it the exact opposite of a
 * deduction game and it is why there is no probe action here: there is nothing to ask. A player who
 * can see everything and still cannot find a path that lands two sequences inside six slots has been
 * beaten by the puzzle rather than by what they were not told.
 *
 * <p>What this class must <b>never</b> publish is a solution — no reachable-goal count, no "best next
 * cell", no marker on the cell a solver would take. Working that out is the entire game.
 *
 * <h2>Why a wasted pick is not a strike</h2>
 *
 * Taking a code that advances nothing is already punished: it spends one of a very small number of
 * buffer slots and it moves the cursor somewhere the player did not choose. Charging a strike on top
 * would punish the same mistake twice and would make an exploratory pick — sometimes the only legal
 * move — read as an error. Strikes here are reserved for <em>illegal</em> picks, which the client
 * cannot make and a hand-typed command can.
 */
public final class MatrixRules {

    private MatrixRules() {}

    /** The action id the client and the terminal both use. */
    public static final String PICK = "pick";

    /**
     * Takes a cell.
     *
     * @param argument {@code "row:column"}, zero-indexed
     */
    public static Move act(LayerState layer, String actionId, String argument) {
        if (!PICK.equals(actionId)) {
            return Move.refunded("that move does nothing on a protocol grid - pick a code");
        }
        int[] at = parse(argument);
        if (at == null) {
            return Move.refunded("pick takes row:column, both zero-indexed");
        }
        int row = at[0];
        int column = at[1];

        if (row < 0 || row >= layer.matrixSize || column < 0 || column >= layer.matrixSize) {
            return Move.refunded("no such cell on this grid");
        }
        int index = row * layer.matrixSize + column;
        if (layer.matrixUsed.get(index)) {
            return Move.refunded("that code has already been taken");
        }
        // ⚠ Refunded, not a strike. An illegal pick is a mis-typed command rather than a bad
        // decision, and the client cannot produce one at all — every cell it offers is selectable.
        // Charging attention for a typo would make the terminal worse to play than the window.
        boolean legal = layer.matrixRowTurn ? row == layer.matrixCursorRow : column == layer.matrixCursorColumn;
        if (!legal) {
            return Move.refunded(
                    layer.matrixRowTurn
                            ? "the path is in row " + layer.matrixCursorRow + " this pick"
                            : "the path is in column " + layer.matrixCursorColumn + " this pick");
        }
        if (layer.matrixBuffer.size() >= layer.matrixBufferSize) {
            return Move.refunded("the buffer is full");
        }

        String code = layer.matrixGrid.get(index);
        layer.matrixUsed.set(index, true);
        layer.matrixBuffer.add(code);

        // The turn flips and the cursor lands on the cell just taken: a pick made in a row confines
        // the next one to that pick's column, and the other way round.
        layer.matrixCursorRow = row;
        layer.matrixCursorColumn = column;
        layer.matrixRowTurn = !layer.matrixRowTurn;

        int solvedBefore = solvedCount(layer);
        rescore(layer);
        int solvedNow = solvedCount(layer);
        boolean full = layer.matrixBuffer.size() >= layer.matrixBufferSize;

        if (solvedNow > solvedBefore) {
            String label = layer.matrixGoalLabels.get(justSolved(layer, solvedBefore));
            if (solvedNow == layer.matrixGoalLabels.size() || full) {
                return Move.cleared(code + " - " + label + " uploaded; nothing left to take");
            }
            return Move.of(code + " - " + label + " uploaded");
        }
        if (full) {
            // Out of buffer. Cleared when anything was uploaded, because a partial datamine is still
            // a datamine; LOCKED when nothing was, because there is no legal move left — every pick
            // from here is refused for want of a slot, so a strike would leave the player in front of
            // a board they cannot touch with a counter that will never reach its limit.
            return solvedNow > 0
                    ? Move.cleared("buffer full - " + solvedNow + " uploaded")
                    : Move.locked("buffer full and nothing uploaded");
        }
        return Move.of(code + " taken");
    }

    // ================================================================== scoring

    /**
     * Re-reads the buffer and updates every goal's progress.
     *
     * <h2>⚠ Recomputed from the whole buffer, never advanced incrementally</h2>
     *
     * The obvious version keeps a per-goal pointer and steps it on each pick. It is wrong in a way
     * that is invisible until it matters: a run can <em>restart</em> mid-buffer. With a goal of
     * {@code 1C 1C 55}, a buffer of {@code 1C 1C 1C 55} contains it — but a pointer that advanced on
     * the first two and reset on the third would miss it. Rescanning is a handful of comparisons on
     * a buffer of at most eight and cannot get that wrong.
     *
     * <p>A goal that has ever been solved stays solved. A later pick cannot un-earn a sequence that
     * has already gone up the wire.
     */
    static void rescore(LayerState layer) {
        for (int goal = 0; goal < layer.matrixGoalLabels.size(); goal++) {
            List<String> codes = goalCodes(layer, goal);
            if (codes.isEmpty()) {
                continue;
            }
            boolean solved = layer.matrixGoalSolved.get(goal) || contains(layer.matrixBuffer, codes);
            layer.matrixGoalSolved.set(goal, solved);
            layer.matrixGoalMatched.set(goal, solved ? codes.size() : trailingMatch(layer.matrixBuffer, codes));
        }
    }

    /** Whether {@code codes} appears as a contiguous run anywhere in {@code buffer}. */
    static boolean contains(List<String> buffer, List<String> codes) {
        for (int start = 0; start + codes.size() <= buffer.size(); start++) {
            boolean all = true;
            for (int i = 0; i < codes.size() && all; i++) {
                all = buffer.get(start + i).equals(codes.get(i));
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * How much of {@code codes} the buffer currently <em>ends</em> with.
     *
     * <p>The progress figure the board publishes. It is deliberately about the tail rather than the
     * best match anywhere: what the player needs to know is whether the next pick continues a run,
     * and a figure counting a partial match they have already walked away from would be encouraging
     * them towards a sequence that can no longer be completed from here.
     */
    static int trailingMatch(List<String> buffer, List<String> codes) {
        int best = 0;
        for (int length = 1; length <= Math.min(codes.size(), buffer.size()); length++) {
            boolean all = true;
            for (int i = 0; i < length && all; i++) {
                all = buffer.get(buffer.size() - length + i).equals(codes.get(i));
            }
            if (all) {
                best = length;
            }
        }
        return best;
    }

    static int solvedCount(LayerState layer) {
        int count = 0;
        for (Boolean solved : layer.matrixGoalSolved) {
            if (solved) {
                count++;
            }
        }
        return count;
    }

    /** The index of a goal solved since {@code before}, for the log line. */
    private static int justSolved(LayerState layer, int before) {
        int seen = 0;
        for (int goal = 0; goal < layer.matrixGoalSolved.size(); goal++) {
            if (layer.matrixGoalSolved.get(goal) && ++seen > before) {
                return goal;
            }
        }
        return 0;
    }

    /** One goal's codes, sliced out of the flat list the save stores them in. */
    static List<String> goalCodes(LayerState layer, int goal) {
        int from = 0;
        for (int i = 0; i < goal; i++) {
            from += layer.matrixGoalLengths.get(i);
        }
        int to = from + layer.matrixGoalLengths.get(goal);
        if (to > layer.matrixGoalCodes.size()) {
            return List.of();
        }
        return new ArrayList<>(layer.matrixGoalCodes.subList(from, to));
    }

    private static int[] parse(String argument) {
        if (argument == null) {
            return null;
        }
        String[] parts = argument.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
