package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.MatrixBoard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Breach Protocol: the grid, the buffer, and the sequences worth landing in it.
 *
 * <h2>The whole board is on screen, which is what the layout has to serve</h2>
 *
 * This is an open-information puzzle — nothing is hidden and there is nothing to ask for — so the
 * job of the widget is not to reveal things but to make a <em>path</em> readable. Three facts have to
 * be available at a glance and stay available: which cells the next pick may take, what is already in
 * the buffer, and how close each sequence is. Everything below serves those three.
 *
 * <h2>⚠ Legality is drawn, never enforced only by the click handler</h2>
 *
 * A cell the path cannot reach is dimmed and is not clickable, and the row or column that <em>is</em>
 * live is marked. That is not decoration: the alternating rule is the entire puzzle, and a player who
 * has to remember whose turn it is has been given a memory test instead of a routing one. The rules
 * refuse an illegal pick as well — this is the second of two guards, not the only one.
 *
 * <h2>Keyboard, routed from the panel</h2>
 *
 * Arrows move a highlight along the live row or column and Enter takes the cell. ⚠ The panel installs
 * the filter, not this widget — a filter here only fires for events targeted inside it, and the
 * breach window's focus is almost always on an action chip or the scroll pane. That mistake cost two
 * rounds on the control this replaces; see {@code BreachView}.
 */
public final class MatrixGrid extends VBox {

    private final VBox rows = new VBox(UiTokens.HAIR);
    private final Label bufferLine = new Label();
    private final Label turnLine = Ui.micro("");
    private final VBox goals = new VBox(UiTokens.SPACE_1);

    private MatrixBoard board;
    private int highlight;
    private BiConsumer<Integer, Integer> onPick = (row, column) -> {};
    private Runnable onCursor = () -> {};

    public MatrixGrid() {
        super(UiTokens.SPACE_3);
        getStyleClass().add("es-matrix");
        bufferLine.getStyleClass().add("es-matrix-buffer");
        turnLine.getStyleClass().add("es-matrix-turn");

        VBox left = new VBox(UiTokens.SPACE_2, turnLine, rows);
        VBox right = new VBox(UiTokens.SPACE_2, Ui.label("Buffer"), bufferLine, goals);
        right.setAlignment(Pos.TOP_LEFT);

        HBox split = Ui.row(UiTokens.SPACE_6, left, right);
        split.setAlignment(Pos.TOP_LEFT);
        getChildren().add(split);
        setFocusTraversable(true);
        setPickOnBounds(true);
        setOnMousePressed(event -> requestFocus());
    }

    /** Called with the cell the player took. */
    public void setOnPick(BiConsumer<Integer, Integer> handler) {
        this.onPick = handler == null ? (row, column) -> {} : handler;
    }

    /** Called whenever the highlight moves, so the panel can say what a chip would act on. */
    public void setOnCursor(Runnable handler) {
        this.onCursor = handler == null ? () -> {} : handler;
    }

    /**
     * The cell a chip would take, as the engine's {@code row:column} argument, or {@code ""}.
     *
     * <p>The grid can dispatch a pick by itself, but {@code TAKE CODE} is also on the action strip
     * with its price on it — and a chip that could not be used from the keyboard would put the only
     * priced route to the move behind the pointer.
     */
    public String selection() {
        if (board == null || !board.selectable(rowOf(highlight), columnOf(highlight))) {
            return "";
        }
        return rowOf(highlight) + ":" + columnOf(highlight);
    }

    public void show(MatrixBoard next) {
        this.board = next;
        if (next == null) {
            rows.getChildren().clear();
            goals.getChildren().clear();
            bufferLine.setText("");
            turnLine.setText("");
            return;
        }
        // Keep the highlight somewhere the player can actually take. A pick moves the live line, so
        // the cell that was under the cursor is very often no longer selectable.
        if (!next.selectable(rowOf(highlight), columnOf(highlight))) {
            highlight = firstSelectable(next);
        }
        onCursor.run();
        buildGrid(next);
        buildBuffer(next);
        buildGoals(next);
        setAccessibleText(describe(next));
    }

    /** Arrow keys and Enter, handed in by the panel. See the class comment. */
    public void handleKey(javafx.scene.input.KeyEvent event) {
        if (board == null) {
            return;
        }
        switch (event.getCode()) {
            case LEFT, UP -> {
                step(-1);
                event.consume();
            }
            case RIGHT, DOWN -> {
                step(1);
                event.consume();
            }
            case ENTER, SPACE -> {
                if (board.selectable(rowOf(highlight), columnOf(highlight))) {
                    onPick.accept(rowOf(highlight), columnOf(highlight));
                }
                event.consume();
            }
            default -> {
                // Everything else belongs to whatever else is listening.
            }
        }
    }

    /**
     * Moves the highlight to the next selectable cell along the live line.
     *
     * <p>Skips cells already taken rather than stopping on them: a used cell can never be picked, so
     * a cursor that could rest there would be offering a move that does not exist.
     */
    private void step(int direction) {
        if (board == null) {
            return;
        }
        int size = board.size();
        for (int i = 0; i < size; i++) {
            int line = board.rowTurn() ? columnOf(highlight) : rowOf(highlight);
            line = Math.floorMod(line + direction * (i + 1), size);
            int row = board.rowTurn() ? board.cursorRow() : line;
            int column = board.rowTurn() ? line : board.cursorColumn();
            if (board.selectable(row, column)) {
                highlight = row * size + column;
                buildGrid(board);
                onCursor.run();
                return;
            }
        }
    }

    private int firstSelectable(MatrixBoard next) {
        for (int row = 0; row < next.size(); row++) {
            for (int column = 0; column < next.size(); column++) {
                if (next.selectable(row, column)) {
                    return row * next.size() + column;
                }
            }
        }
        return 0;
    }

    private int rowOf(int cell) {
        return board == null || board.size() == 0 ? 0 : cell / board.size();
    }

    private int columnOf(int cell) {
        return board == null || board.size() == 0 ? 0 : cell % board.size();
    }

    // ------------------------------------------------------------------ rendering

    private void buildGrid(MatrixBoard next) {
        rows.getChildren().clear();
        turnLine.setText(Ui.upper(next.bufferRemaining() == 0
                ? "buffer full"
                : next.rowTurn()
                        ? "take from row " + next.cursorRow()
                        : "take from column " + next.cursorColumn()));

        for (int row = 0; row < next.size(); row++) {
            HBox line = new HBox(UiTokens.HAIR);
            line.setAlignment(Pos.CENTER_LEFT);
            for (int column = 0; column < next.size(); column++) {
                line.getChildren().add(cell(next, row, column));
            }
            rows.getChildren().add(line);
        }
    }

    private Label cell(MatrixBoard next, int row, int column) {
        boolean used = next.used().get(row).get(column);
        boolean live = next.selectable(row, column);

        Label label = new Label(" " + next.grid().get(row).get(column) + " ");
        label.getStyleClass().add("es-matrix-cell");
        // Three states, three weights, and the ramp encodes REACHABILITY rather than value: a cell
        // you may take now is brightest, one you might take later is ordinary, one that is gone is
        // dimmest. That is the only thing a player needs off the grid at a glance.
        label.getStyleClass().add(used ? "es-matrix-spent" : live ? "es-matrix-live" : "es-matrix-idle");
        if (live && row * next.size() + column == highlight) {
            label.getStyleClass().add("es-matrix-cursor");
        }
        label.setAccessibleText(next.grid().get(row).get(column)
                + " at row " + row + " column " + column
                + (used ? ", already taken" : live ? ", can be taken now" : ", not on the path this pick"));

        if (live) {
            label.setPickOnBounds(true);
            Cursors.shared().clickable(label);
            label.setOnMouseClicked(event -> {
                event.consume();
                highlight = row * next.size() + column;
                onCursor.run();
                requestFocus();
                onPick.accept(row, column);
            });
            label.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                    event.consume();
                    onPick.accept(row, column);
                }
            });
        }
        return label;
    }

    /** The buffer, with its empty slots drawn rather than implied — the slots are the budget. */
    private void buildBuffer(MatrixBoard next) {
        List<String> cells = new ArrayList<>(next.buffer());
        while (cells.size() < next.bufferSize()) {
            cells.add("__");
        }
        bufferLine.setText(String.join(" ", cells));
        bufferLine.setAccessibleText("Buffer: " + next.buffer().size()
                + " of " + next.bufferSize() + " used. " + String.join(", ", next.buffer()));
    }

    /**
     * One row per sequence, showing how much of it the buffer currently ends with.
     *
     * <p>⚠ The progress figure is about the <em>tail</em> of the buffer, which is what makes it
     * actionable: it answers "does my next pick continue this" rather than "did I ever have part of
     * this", and a figure counting a run the player has already walked away from would point them at
     * a sequence they can no longer finish.
     */
    private void buildGoals(MatrixBoard next) {
        goals.getChildren().clear();
        for (MatrixBoard.Goal goal : next.goals()) {
            Label line = new Label(String.join(" ", goal.codes())
                    + (goal.solved() ? "   UPLOADED" : "   " + goal.matched() + "/" + goal.codes().size()));
            line.getStyleClass().addAll("es-matrix-goal",
                    goal.solved() ? "es-matrix-goal-done" : "es-matrix-goal-open");
            line.setAccessibleText(goal.label() + ": " + String.join(", ", goal.codes())
                    + (goal.solved()
                            ? ", uploaded"
                            : ", " + goal.matched() + " of " + goal.codes().size() + " in the buffer's tail"));
            goals.getChildren().addAll(Ui.micro(goal.label()), line);
        }
    }

    private static String describe(MatrixBoard next) {
        return "Breach protocol, " + next.size() + " by " + next.size() + ". "
                + next.buffer().size() + " of " + next.bufferSize() + " buffer slots used. "
                + (next.rowTurn() ? "Take from row " + next.cursorRow() : "Take from column " + next.cursorColumn())
                + ". " + next.goals().size() + (next.goals().size() == 1 ? " sequence." : " sequences.");
    }
}
