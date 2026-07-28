package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionBudget;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The attention budget, one cell per point.
 *
 * <h2>Why this is {@link CycleGrid}'s argument and not {@link CellMeter}'s</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 bans a continuous bar, and both widgets obey that. The
 * difference is what the number <em>is</em>. A cell meter's cells are a sampling of a quantity; this
 * one's cells <b>are</b> the quantity — attention is a small integer the player counts, spends two of
 * at a time, and decides on. "Fifteen left" is a number a player acts on; "68% remaining" is not. So
 * it is the cycle grid's rule: one cell per point, wrapped at {@link UiTokens#ATTENTION_CELLS_PER_ROW}.
 *
 * <h2>The lost cells are the point of the whole widget</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 requires that a loss read as <em>"I was too loud"</em>
 * and never as <em>"the game decided"</em>. Attention spent on an alarm penalty is drawn in
 * {@code -es-alarm} at the end of the spent run, so the budget itself carries the history of how it
 * went — a player who ran out can see, without opening the ledger, how much of the shortfall was
 * moves and how much was noise. That is one of the two alarm uses this panel is allowed (§2.1 caps
 * it at two per screen; the other is the ledger's strike rows).
 *
 * <h2>The preview is the other half of §4's legibility requirement</h2>
 *
 * §4 asks for attention to be "visible and itemised at all times". Itemised after the fact is the
 * ledger; visible before the click is this — hovering or focusing an action chip lights the cells it
 * would consume, so the cost is answered in the same units and the same place as the balance. The
 * blink is a two-state <b>class swap</b> on {@link UiTokens#RECOVERY_BLINK_MS}, never an opacity
 * tween: §5 permits step timing only, and this is the same two-step loop the thermal-recovery cells
 * already use, so it lands on the same frames as the rest of the deck.
 *
 * <h2>⚠ Numbers snap</h2>
 *
 * {@code docs/client/01-visual-language.md} §7.3: "No count-up, no roll, no odometer" on any numeric
 * readout, no exceptions. The caption is rebuilt whole on every call and there is nowhere for an
 * intermediate value to live.
 */
public final class AttentionMeter extends VBox {

    /**
     * The two non-ASCII characters this widget emits, named so they are checkable at a glance.
     *
     * <p>Both are verified present in IBM Plex Mono ({@code GlyphCoverageTest} parses the cmap and
     * fails the build otherwise). {@code U+2212} rather than an ASCII hyphen because a hyphen beside
     * a digit reads as a range — {@code -2} next to {@code 22} looks like a span, and this is a
     * debit. The breach package keeps its whole drawing vocabulary in {@code ui/breach/AsciiCanvas};
     * these two live here because {@code ui/widgets} must not depend on a feature package.
     */
    private static final char BULLET = '·';

    private static final char MINUS = '−';

    private final Label title = Ui.label("Attention");
    private final VBox rows = new VBox(UiTokens.HAIR);
    private final Label caption = Ui.micro("");
    private final Label previewLabel = Ui.micro("");

    private final List<Region> cells = new ArrayList<>();

    /** Cells currently standing in for the previewed cost. Blinked as a set, not individually. */
    private final List<Region> previewCells = new ArrayList<>();

    private AttentionBudget budget = new AttentionBudget(0, 1);
    private int strikeCost;
    private int previewCost;
    private String previewName = "";
    private boolean blinkOn;
    private AutoCloseable blink;

    public AttentionMeter() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-attn");
        caption.getStyleClass().add("es-attn-caption");
        previewLabel.getStyleClass().add("es-attn-caption");

        // ⚠ Fixed width, always — see UiTokens.ATTENTION_PREVIEW_WIDTH. A caption that appeared and
        // disappeared with the pointer resized this whole widget, which reflowed the cost strip
        // beside it and made the chips oscillate under the cursor.
        previewLabel.setMinWidth(UiTokens.ATTENTION_PREVIEW_WIDTH);
        previewLabel.setPrefWidth(UiTokens.ATTENTION_PREVIEW_WIDTH);
        previewLabel.setMaxWidth(UiTokens.ATTENTION_PREVIEW_WIDTH);
        previewLabel.setAlignment(javafx.geometry.Pos.BASELINE_RIGHT);

        HBox head = Ui.row(UiTokens.SPACE_4, title, Ui.spacer(), previewLabel);
        head.setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(head, rows, caption);

        // One subscription for the whole widget, not one per cell (§7.3). Decorative: freezing it
        // costs nothing, because the previewed cost is also printed in words beside the title.
        blink = Pulse.shared().animate(UiTokens.RECOVERY_BLINK_MS, this::flip);
        render();
    }

    /**
     * @param budget the layer's attention, or the whole attempt's — the widget does not care which
     * @param strikeCost how many of the spent points went to alarm penalties rather than to moves.
     *     Drawn at the end of the spent run, because that is where the player's eye already is.
     */
    public void show(AttentionBudget budget, int strikeCost) {
        this.budget = budget == null ? new AttentionBudget(0, 1) : budget;
        this.strikeCost = Math.max(0, strikeCost);
        render();
    }

    /**
     * Lights the cells an action would consume.
     *
     * @param cost zero clears the preview
     * @param label what the player is hovering, for the printed line. Blank clears it.
     */
    public void preview(int cost, String label) {
        this.previewCost = Math.max(0, cost);
        this.previewName = label == null ? "" : label;
        render();
    }

    /**
     * Rebuilds the whole field.
     *
     * <p>Rebuilding rather than diffing, for {@link CycleGrid#show}'s reason: a few dozen
     * {@link Region}s is explicitly fine (§7.3), and a diff is a cache that can disagree with the
     * model — which is exactly the failure this widget exists to make impossible.
     */
    private void render() {
        rows.getChildren().clear();
        cells.clear();
        previewCells.clear();

        int total = Math.max(1, budget.budget());
        // Above the cap one cell stands for several points, and the caption says so rather than
        // quietly changing what a cell means. §4's "compute is countable" argument only holds while
        // the cells are countable; past that the printed figures are the readout and the field is
        // the shape.
        int scale = Math.max(1, (int) Math.ceil(total / (double) UiTokens.ATTENTION_CELLS_MAX));
        int cellCount = (int) Math.ceil(total / (double) scale);
        // Ceil on both, so any spending at all darkens a cell and any alarm at all shows red. The
        // transition from "clean" to "not clean" is the one the player most needs to see.
        int spentCells = Math.min(cellCount, (int) Math.ceil(budget.spent() / (double) scale));
        int lostCells = Math.min(spentCells, (int) Math.ceil(strikeCost / (double) scale));
        int costCells = Math.min(cellCount - spentCells,
                (int) Math.ceil(previewCost / (double) scale));

        HBox row = newRow();
        for (int i = 0; i < cellCount; i++) {
            if (i > 0 && i % UiTokens.ATTENTION_CELLS_PER_ROW == 0) {
                rows.getChildren().add(row);
                row = newRow();
            }
            String style;
            if (i < spentCells - lostCells) {
                style = "es-attn-cell-spent";
            } else if (i < spentCells) {
                style = "es-attn-cell-lost";
            } else if (i < spentCells + costCells) {
                style = "es-attn-cell-cost";
            } else {
                style = "es-attn-cell";
            }
            Region cell = Ui.block(
                    UiTokens.ATTENTION_CELL_WIDTH, UiTokens.ATTENTION_CELL_HEIGHT, "es-attn-cell-base", style);
            if ("es-attn-cell-cost".equals(style)) {
                previewCells.add(cell);
            }
            cells.add(cell);
            row.getChildren().add(cell);
        }
        rows.getChildren().add(row);
        applyBlink();

        StringBuilder line = new StringBuilder()
                .append(budget.spent()).append(" SPENT ").append(BULLET).append(' ')
                .append(budget.remaining()).append(" LEFT OF ").append(budget.budget());
        if (strikeCost > 0) {
            line.append(' ').append(BULLET).append(' ').append(strikeCost).append(" TO ALARMS");
        }
        if (scale > 1) {
            line.append(' ').append(BULLET).append(" 1 CELL = ").append(scale);
        }
        caption.setText(line.toString());
        previewLabel.setText(previewCost > 0
                ? Ui.upper("next: " + previewName + " " + MINUS + previewCost)
                : "");

        // The cells are colour and count; neither reaches a screen reader. docs/client/07 §5.2 does
        // not let meaning rest on appearance, so the same figures go down the second path.
        setAccessibleText("Attention " + budget.spent() + " spent of " + budget.budget()
                + ", " + budget.remaining() + " remaining"
                + (strikeCost > 0 ? ", " + strikeCost + " lost to alarms" : "")
                + (previewCost > 0 ? ". Next action " + previewName + " costs " + previewCost : "."));
    }

    private HBox newRow() {
        HBox row = new HBox(UiTokens.HAIR);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void flip() {
        blinkOn = !blinkOn;
        applyBlink();
    }

    private void applyBlink() {
        for (Region cell : previewCells) {
            cell.getStyleClass().remove("es-attn-cell-cost-on");
            if (blinkOn) {
                cell.getStyleClass().add("es-attn-cell-cost-on");
            }
        }
    }

    /** How many cells are lit in each state. Test seam — spent, lost, previewed, free. */
    public int[] census() {
        int spent = 0;
        int lost = 0;
        int cost = 0;
        for (Region cell : cells) {
            if (cell.getStyleClass().contains("es-attn-cell-lost")) {
                lost++;
            } else if (cell.getStyleClass().contains("es-attn-cell-spent")) {
                spent++;
            } else if (cell.getStyleClass().contains("es-attn-cell-cost")) {
                cost++;
            }
        }
        return new int[] {spent, lost, cost, cells.size() - spent - lost - cost};
    }

    public void dispose() {
        if (blink != null) {
            try {
                blink.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            blink = null;
        }
    }
}
