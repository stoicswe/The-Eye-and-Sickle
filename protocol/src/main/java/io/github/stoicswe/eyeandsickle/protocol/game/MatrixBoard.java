package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;

/**
 * The Breach Protocol board: a grid of hex codes, a buffer, and an alternating path through it.
 *
 * <h2>The rules, in four lines</h2>
 *
 * <ol>
 *   <li>The first pick is anywhere in <b>row 0</b>.
 *   <li>After a pick in a row, the next must be in that pick's <b>column</b>. After a pick in a
 *       column, the next must be in that pick's <b>row</b>. It alternates for the whole attempt.
 *   <li>Every pick appends its code to the buffer. <b>The buffer cannot be emptied</b>, and when it
 *       is full the attempt is over.
 *   <li>A goal is met when its codes appear as a <b>contiguous run</b> anywhere in the buffer.
 * </ol>
 *
 * <h2>⚠ Everything is published, and that is the design rather than an oversight</h2>
 *
 * The grid, the goals, the buffer and whose turn it is are all here. Nothing is hidden, because this
 * is not a deduction game — a player who can see the whole board and still cannot find a path that
 * lands two sequences in six slots has been beaten by the puzzle, not by a lack of information. That
 * is the opposite of {@link OffsetBoard}'s pressure and deliberately so; see {@link PuzzleClass}.
 *
 * <p>⚠ What is <b>not</b> published is any hint of a solution: no "best path", no reachable-goal
 * count, no marker on the cell a solver would take. Computing that for the player is the entire game,
 * and a field carrying it would be the same class of leak as a Logic board publishing its code.
 *
 * @param grid rows of two-character hex codes, square, indexed {@code grid.get(row).get(column)}
 * @param used which cells have already been taken; same shape as {@link #grid}. A used cell may never
 *     be picked again, which is what stops a two-cell ping-pong solving everything
 * @param buffer the codes taken so far, in order
 * @param bufferSize how many picks the attempt gets in total — the real budget of this puzzle
 * @param rowTurn true when the next pick must be in {@link #cursorRow}, false when it must be in
 *     {@link #cursorColumn}. Published rather than derived from the buffer length so the renderer and
 *     the rules cannot disagree about whose turn it is on the frame after a pick
 * @param cursorRow the row the next pick is confined to, or {@code 0} at the start
 * @param cursorColumn the column the next pick is confined to; meaningless while {@link #rowTurn}
 * @param goals the sequences worth completing, in the order they are drawn
 */
public record MatrixBoard(
        List<List<String>> grid,
        List<List<Boolean>> used,
        List<String> buffer,
        int bufferSize,
        boolean rowTurn,
        int cursorRow,
        int cursorColumn,
        List<Goal> goals)
        implements BreachBoard {

    /**
     * One sequence worth completing.
     *
     * @param label what it is called on screen — a datamine tier, in the fiction's vocabulary
     * @param codes the run that has to appear in the buffer
     * @param matched how many of {@code codes} the buffer currently ends with. ⚠ A <em>progress</em>
     *     figure, not a promise: it drops back to zero the moment a pick breaks the run, and showing
     *     it is what makes a buffer slot spent on the wrong code legible as the mistake it was
     * @param solved whether it is already met. Once true it stays true — a later pick cannot un-earn
     *     a sequence that has already appeared
     * @param reward what completing it is worth, in the layer's own scoring
     */
    public record Goal(String label, List<String> codes, int matched, boolean solved, int reward) {

        public Goal {
            label = label == null ? "" : label;
            codes = codes == null ? List.of() : List.copyOf(codes);
            matched = Math.max(0, Math.min(codes.size(), matched));
            reward = Math.max(0, reward);
        }
    }

    public MatrixBoard {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(used, "used");
        grid = List.copyOf(grid.stream().map(List::copyOf).toList());
        used = List.copyOf(used.stream().map(List::copyOf).toList());
        buffer = buffer == null ? List.of() : List.copyOf(buffer);
        goals = goals == null ? List.of() : List.copyOf(goals);

        // ⚠ The grid and the used mask must agree in shape, and this is worth throwing over rather
        // than clamping. They are indexed together by both the renderer and the rules; a mismatch is
        // an off-by-one that shows up as a cell the player can see and cannot take, which reads as a
        // broken game rather than as a bad state.
        if (used.size() != grid.size()) {
            throw new IllegalArgumentException("used has " + used.size() + " rows but the grid has " + grid.size());
        }
        for (int row = 0; row < grid.size(); row++) {
            if (used.get(row).size() != grid.get(row).size()) {
                throw new IllegalArgumentException("row " + row + " disagrees between grid and used");
            }
        }
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be positive, was " + bufferSize);
        }
    }

    /** How many picks are left. Zero means the attempt is over whatever the goals say. */
    public int bufferRemaining() {
        return Math.max(0, bufferSize - buffer.size());
    }

    /** Whether a cell is one the next pick may legally take. */
    public boolean selectable(int row, int column) {
        if (row < 0
                || row >= grid.size()
                || column < 0
                || column >= grid.get(row).size()) {
            return false;
        }
        if (used.get(row).get(column) || bufferRemaining() == 0) {
            return false;
        }
        return rowTurn ? row == cursorRow : column == cursorColumn;
    }

    public int size() {
        return grid.size();
    }
}
