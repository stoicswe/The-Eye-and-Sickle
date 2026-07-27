package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachActionKind;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Every move, what it cost, and what came back.
 *
 * <h2>This is the most load-bearing widget in the feature</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 states the constraint it exists to satisfy:
 * <b>"the player must always be able to see which action cost what. A loss has to read as 'I was too
 * loud', never 'the game decided'."</b> Everything else in the breach can be beautiful and the
 * feature still fails if this list is wrong, incomplete or hard to scan. That is also why the engine
 * appends a row for a move that was <em>refused in fiction</em> — a gap in the ledger is the bug, not
 * a quiet outcome.
 *
 * <h2>Append only, never re-sorted</h2>
 *
 * {@code docs/client/01-visual-language.md} §7.3 forbids "anything that reorders a list the player
 * may be pointing at". Rows arrive newest-at-the-bottom and stay where they were put. The
 * corollary is the scroll rule below, which is the same courtesy one level up.
 *
 * <h2>⚠ The scroll only follows a reader who is already at the bottom</h2>
 *
 * Yanking the viewport to the end while somebody is three rows up re-reading how they blew a strike
 * is the single most irritating thing a log surface can do, and it is a real risk here because every
 * intent appends. The position is sampled <em>before</em> the rebuild and only restored to the end
 * if it was already there.
 *
 * <h2>Colour is the last channel, not the first</h2>
 *
 * A strike row carries the word {@code STRIKE} and its own alarm class; a quiet read is dim; a loud
 * tool is bright. Take every colour away and the rows still sort into three kinds by their text,
 * which is what {@code docs/client/07-accessibility.md} §5.2 asks for. Strike rows are one of the two
 * alarm uses §2.1 permits on this screen — the other is the attention meter's lost cells, and they
 * are the same event seen twice, which is the point.
 */
public final class AttentionLedger extends VBox {

    /**
     * Column widths in pixels.
     *
     * <p>Fixed rather than character-padded: the result clause is free text of unbounded length and
     * padding it into a monospace grid would either truncate the one field that explains the loss or
     * push the row off the panel. Widths are on the closed spacing scale's terms — multiples that
     * hold a four-digit sequence, a two-digit cost and a running total without reflowing.
     */
    private static final double COL_SEQUENCE = 26;

    private static final double COL_ACTION = 150;

    private static final double COL_COST = 44;

    private static final double COL_TOTAL = 44;

    /** ⚠ U+2212. An ASCII hyphen beside a digit reads as a range; this is a debit. */
    private static final char MINUS = '−';

    private static final char BULLET = '·';

    private final VBox rows = new VBox();
    private final ScrollPane scroll = new ScrollPane();

    public AttentionLedger() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-ledger");

        Label title = Ui.label("Attention ledger");
        rows.getStyleClass().add("es-ledger-rows");

        scroll.setContent(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-ledger-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(title, header(), scroll);
        clear();
    }

    /**
     * Replaces the visible rows.
     *
     * <p>The list is capped at {@link UiTokens#LEDGER_MAX_ROWS} from the <em>end</em>, so what is
     * dropped is the oldest history rather than the move that just happened. A breach that runs past
     * sixty actions has already told the player everything the first rows had to say.
     */
    public void show(List<AttentionEntry> entries) {
        // ⚠ rowCount(), not getChildren().isEmpty(): the empty-state instruction is a child, so the
        // list is never structurally empty and the first real show() would decline to scroll.
        boolean wasAtBottom = rowCount() == 0 || scroll.getVvalue() >= 1.0 - 1e-4;
        rows.getChildren().clear();

        if (entries == null || entries.isEmpty()) {
            clear();
            return;
        }
        int from = Math.max(0, entries.size() - UiTokens.LEDGER_MAX_ROWS);
        for (int i = from; i < entries.size(); i++) {
            rows.getChildren().add(row(entries.get(i)));
        }
        if (wasAtBottom) {
            // ⚠ Laid out first. The vvalue is clamped against the CURRENT content height, so setting
            // it before the new rows have been measured scrolls to the end of the old list — which
            // looks like the ledger stopping one row short of the move that just resolved.
            rows.applyCss();
            rows.layout();
            scroll.setVvalue(1.0);
        }
        setAccessibleText("Attention ledger, " + entries.size() + " entries. "
                + "Latest: " + describe(entries.getLast()));
    }

    /** The empty state — an instruction, not a mood piece (§6). */
    public void clear() {
        rows.getChildren().clear();
        Label empty = Note.empty(
                "No moves yet. Every action is itemised here with what it cost and what came back.");
        empty.getStyleClass().add("es-ledger-empty");
        rows.getChildren().add(empty);
        setAccessibleText("Attention ledger, empty.");
    }

    private HBox header() {
        HBox head = new HBox(UiTokens.SPACE_2,
                cell("NO", COL_SEQUENCE),
                cell("ACTION", COL_ACTION),
                cell("COST", COL_COST),
                cell("TOTAL", COL_TOTAL),
                grow(cell("RESULT", 0)));
        head.getStyleClass().addAll("es-row-head", "es-ledger-head");
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    private HBox row(AttentionEntry entry) {
        HBox row = new HBox(UiTokens.SPACE_2,
                cell(String.format(Locale.ROOT, "%02d", entry.sequence()), COL_SEQUENCE),
                cell(Ui.upper(entry.label()), COL_ACTION),
                cell(entry.cost() == 0 ? "" : MINUS + Integer.toString(entry.cost()), COL_COST),
                cell(Integer.toString(entry.spentAfter()), COL_TOTAL),
                grow(cell(Ui.upper(entry.result()), 0)));
        row.getStyleClass().addAll("es-ledger-row", kindClass(entry));
        row.setAlignment(Pos.CENTER_LEFT);
        if (entry.alarm()) {
            // The word as well as the colour. A row that is only red is a row that means nothing to
            // a player who cannot see red, and this is the row that explains the loss.
            row.getChildren().add(cell(BULLET + " STRIKE", 0));
        }
        row.setAccessibleText(describe(entry));
        return row;
    }

    /**
     * The row's kind class.
     *
     * <p>Alarm wins over kind, deliberately: a quiet read that tripped a canary is not a quiet row.
     * {@code docs/design/05} §4 prices actions by how loud they are, and this is the same ordering —
     * what happened outranks what was intended.
     */
    private static String kindClass(AttentionEntry entry) {
        if (entry.alarm()) {
            return "es-ledger-strike";
        }
        BreachActionKind kind = entry.kind();
        if (kind == BreachActionKind.QUIET_READ || kind == BreachActionKind.SIDE_CHANNEL) {
            return "es-ledger-quiet";
        }
        if (kind == BreachActionKind.LOUD_TOOL || kind == BreachActionKind.BYPASS) {
            return "es-ledger-loud";
        }
        return "es-ledger-probe";
    }

    private static String describe(AttentionEntry entry) {
        return entry.label() + ", cost " + entry.cost()
                + ", " + entry.spentAfter() + " spent so far. " + entry.result()
                + (entry.alarm() ? " Alarm raised." : "");
    }

    private static Label cell(String text, double width) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-ledger-cell");
        if (width > 0) {
            label.setMinWidth(width);
            label.setPrefWidth(width);
            label.setMaxWidth(width);
        }
        return label;
    }

    private static Label grow(Label label) {
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    /** How many rows are showing. Test seam for the sixty-row cap. */
    public int rowCount() {
        return (int) rows.getChildren().stream().filter(HBox.class::isInstance).count();
    }
}
