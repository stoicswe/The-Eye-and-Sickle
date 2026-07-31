package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The offset cipher: three rows of hex, and a number to type under each column.
 *
 * <h2>Three rows, one column each, and they must line up</h2>
 *
 * {@code OBSERVED} on top, {@code TARGET} under it, the player's offset under that. The whole puzzle
 * is reading down a column and subtracting, so the columns have to align exactly — which is why every
 * cell is padded to a fixed width and why the whole thing is one character-cell texture rather than
 * three independently laid out rows.
 *
 * <h2>Typed, not stepped</h2>
 *
 * An offset ranges to ±255, so a stepper would be up to 255 presses for one cell. The player types
 * digits straight into the cursor cell: {@code -} and {@code +} set the sign, digits append,
 * Backspace deletes, left and right move. That is the same shape as entering a number anywhere else
 * and needs no text field, which keeps the row a fixed-width texture.
 *
 * <p>⚠ Typing is <b>free and reversible</b>. {@code docs/design/05} §3.7 makes composition not-a-move,
 * so the player can rewrite the whole row as often as they like; only {@code COMMIT} costs anything.
 * The widget therefore sends a {@code type} for every keystroke and the engine holds the draft — a
 * local buffer here would be a second copy of the answer that a reload could disagree with.
 *
 * <h2>⚠ What it never draws</h2>
 *
 * The correct offsets. They are derivable from the two rows above — that is the entire puzzle — but
 * derivable by the player is not the same as printed by the client. Cells the last commit rejected are
 * marked, because the engine published which ones; nothing here computes that mark itself.
 */
public final class OffsetRack extends VBox {

    /** Every cell is this wide, so the three rows line up down the column. */
    private static final int CELL = 5;

    private final Label observedRow = new Label();
    private final Label targetRow = new Label();
    private final HBox offsetRow = new HBox(0);
    private final Label hint = Ui.micro(
            // Spelled out rather than drawn: U+232B is not in either bundled font, and GlyphCoverageTest
            // fails the build on it — a host fallback would be a different shape AND a different advance
            // width per platform, which shears a character-cell row.
            "← → choose a byte   digits type an offset   - + set the sign   BACKSPACE clears");

    private OffsetBoard board;
    private int cursor;
    private String typing = "";
    private BiConsumer<Integer, String> onType = (index, value) -> {};
    private Runnable onCursor = () -> {};

    public OffsetRack() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-cipher");
        observedRow.getStyleClass().addAll("es-cipher-row", "es-cipher-observed");
        targetRow.getStyleClass().addAll("es-cipher-row", "es-cipher-target");
        offsetRow.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("es-cipher-hint");

        getChildren()
                .addAll(
                        Ui.micro("OBSERVED"),
                        observedRow,
                        Ui.micro("TARGET"),
                        targetRow,
                        Ui.micro("OFFSET"),
                        offsetRow,
                        hint);

        setFocusTraversable(true);
        setPickOnBounds(true);
        setOnMousePressed(event -> requestFocus());
    }

    /** Called with {@code (index, value)} where an empty value clears the cell. */
    public void setOnType(BiConsumer<Integer, String> handler) {
        this.onType = handler == null ? (index, value) -> {} : handler;
    }

    /** Called whenever the cursor moves, so the panel can say what a chip would act on. */
    public void setOnCursor(Runnable handler) {
        this.onCursor = handler == null ? () -> {} : handler;
    }

    /** The byte a chip would act on, as the engine's index argument. {@code CARRY} needs it. */
    public String selection() {
        return board == null || board.length() == 0 ? "" : String.valueOf(cursor);
    }

    public void show(OffsetBoard next) {
        this.board = next;
        if (next == null) {
            observedRow.setText("");
            targetRow.setText("");
            offsetRow.getChildren().clear();
            return;
        }
        if (cursor >= next.length()) {
            cursor = Math.max(0, next.length() - 1);
        }
        StringBuilder observed = new StringBuilder();
        StringBuilder target = new StringBuilder();
        for (int i = 0; i < next.length(); i++) {
            observed.append(pad(OffsetBoard.hex(next.observed().get(i))));
            target.append(pad(OffsetBoard.hex(next.target().get(i))));
        }
        observedRow.setText(observed.toString());
        targetRow.setText(target.toString());
        buildOffsets(next);
        setAccessibleText(describe(next));
    }

    /**
     * Keys, handed in by the panel.
     *
     * <p>⚠ The panel installs the filter, not this widget. A filter here only fires for events
     * targeted inside it, and the breach window's focus is almost always on an action chip or on the
     * scroll pane that wraps the whole panel — which treats arrows as scroll commands. That mistake
     * cost two rounds on the control this replaces.
     */
    public void handleKey(javafx.scene.input.KeyEvent event) {
        if (board == null || board.length() == 0) {
            return;
        }
        switch (event.getCode()) {
            case LEFT -> {
                move(-1);
                event.consume();
            }
            case RIGHT -> {
                move(1);
                event.consume();
            }
            case ENTER -> {
                // Enter finishes the cell and moves on, which is how anyone fills a row of numbers.
                // Committing is a separate, deliberate control: a row submitted by a stray Enter
                // would cost a strike for a keystroke rather than for the arithmetic.
                flush();
                move(1);
                event.consume();
            }
            case BACK_SPACE, DELETE -> {
                if (typing.isEmpty()) {
                    onType.accept(cursor, "");
                } else {
                    typing = typing.substring(0, typing.length() - 1);
                    buildOffsets(board);
                }
                event.consume();
            }
            default -> typed(event);
        }
    }

    private void typed(javafx.scene.input.KeyEvent event) {
        String text = event.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        char c = text.charAt(0);
        if (c == '-' || c == '+') {
            // The sign replaces whatever sign was there rather than toggling, so pressing it twice
            // is idempotent — a player correcting themselves should not have to count presses.
            typing = (c == '-' ? "-" : "") + typing.replace("-", "");
            buildOffsets(board);
            event.consume();
            return;
        }
        if (c >= '0' && c <= '9') {
            String next = typing + c;
            // Bounded while typing rather than on commit, so an impossible value can never be
            // entered at all. Three digits plus a sign is the whole domain.
            if (next.replace("-", "").length() <= 3) {
                typing = next;
                flushSoft();
            }
            event.consume();
        }
    }

    /** Sends the current draft to the engine without moving on. */
    private void flushSoft() {
        String value = typing.replace("-", "").isEmpty() ? "" : typing;
        onType.accept(cursor, value);
    }

    private void flush() {
        flushSoft();
        typing = "";
    }

    private void move(int step) {
        flush();
        if (board != null && board.length() > 0) {
            cursor = Math.floorMod(cursor + step, board.length());
            buildOffsets(board);
            onCursor.run();
        }
    }

    // ------------------------------------------------------------------ rendering

    private void buildOffsets(OffsetBoard next) {
        offsetRow.getChildren().clear();
        if (next == null) {
            return;
        }
        for (int i = 0; i < next.length(); i++) {
            String text = i == cursor && !typing.isEmpty() ? typing : OffsetBoard.offsetText(value(next, i));
            Label cell = new Label(pad(text));
            cell.getStyleClass().add("es-cipher-cell");
            if (next.wrong().contains(i)) {
                // The engine said this one was wrong. It never says what it should have been.
                cell.getStyleClass().add("es-cipher-wrong");
            } else if (next.isGiven(i)) {
                // ⚠ Ahead of `filled`, because a given column IS filled and the two must not read
                // alike. The whole value of a give is knowing which columns you do not have to
                // check — a cell that looked like your own answer would still have to be verified,
                // and the favour would have bought nothing but keystrokes.
                cell.getStyleClass().add("es-cipher-given");
            } else if (value(next, i) != null) {
                cell.getStyleClass().add("es-cipher-filled");
            }
            if (i == cursor) {
                cell.getStyleClass().add("es-cipher-cursor");
            }
            cell.setAccessibleText("Byte " + (i + 1) + " of " + next.length()
                    + ", observed " + OffsetBoard.hex(next.observed().get(i))
                    + ", target " + OffsetBoard.hex(next.target().get(i))
                    + ", offset " + OffsetBoard.offsetText(value(next, i))
                    + (next.wrong().contains(i) ? ", rejected by the last commit" : "")
                    // Said in words as well as in colour — §4.4. A locked column a screen reader
                    // announced identically to an editable one would be a cell the player kept
                    // trying to type into.
                    + (next.isGiven(i) ? ", came already solved and is locked" : ""));

            int index = i;
            cell.setPickOnBounds(true);
            Cursors.shared().clickable(cell);
            cell.setOnMouseClicked(event -> {
                event.consume();
                flush();
                cursor = index;
                onCursor.run();
                requestFocus();
                buildOffsets(board);
            });
            offsetRow.getChildren().add(cell);
        }
    }

    private static Integer value(OffsetBoard next, int index) {
        return index < next.entered().size() ? next.entered().get(index) : null;
    }

    /**
     * Right-aligned into a fixed cell, so the three rows line up on the column.
     *
     * <h2>⚠ Padded on both sides, and the previous control got this wrong</h2>
     *
     * The tumbler this replaces padded one side only and let the layout centre what was left, which
     * put every caret one cell right of the box it controlled — reported as <em>"clicking the second
     * arrow changes the left-most column"</em>. The handler had been correct the whole time and the
     * picture was lying about which control was which, which a player cannot debug. Here the cell
     * carries its own full width, so nothing downstream gets to re-centre it.
     */
    static String pad(String text) {
        String value = text == null ? "" : text;
        if (value.length() >= CELL) {
            return value.substring(0, CELL - 1) + " ";
        }
        return " ".repeat(CELL - value.length() - 1) + value + " ";
    }

    private static String describe(OffsetBoard next) {
        return "Offset cipher, " + next.length() + " bytes. "
                + next.filled() + " of " + next.length() + " offsets entered."
                + (next.wrong().isEmpty() ? "" : " " + next.wrong().size() + " rejected by the last commit.");
    }
}
