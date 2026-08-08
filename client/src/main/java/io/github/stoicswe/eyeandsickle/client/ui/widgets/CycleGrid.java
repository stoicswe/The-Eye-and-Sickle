package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The 100-cell compute grid — the signature component.
 *
 * <h2>Compute is countable, not a percentage</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 puts it in one line, and it is the most load-bearing
 * sentence in the component catalog. A progress bar at 97% says the rig is nearly full. A hundred
 * cells with three of them hollow says <em>you have three</em>, and the player's next question —
 * "is that enough for a thorough scan?" — is answerable by looking. {@code docs/design/01} §1.2
 * makes cycles an integer resource that is allocated and returned; the readout should be an integer
 * readout.
 *
 * <p>It also encodes income versus overhead before it is read (§2.1): self-mining and control
 * channels are amber, defensive arrays and frames are grey steps. A rig that has quietly become all
 * overhead looks wrong from across the room.
 *
 * <h2>Why the layout is hand-written</h2>
 *
 * §7.2: JavaFX has no {@code aspect-ratio}, so square cells that also scale with the panel cannot be
 * expressed in CSS or in any stock layout pane. {@link CellField} therefore does its own
 * {@code layoutChildren} — which is also where the responsive column count lives, so 25 → 20 → 10
 * happens at real measured widths rather than at a guess about the window.
 *
 * <h2>The last slice is the unsettling one</h2>
 *
 * {@link Owner#UNKNOWN} renders {@code ComputeBudget.unaccountedFor()} — capacity the rig is
 * spending on something it cannot name. {@code docs/design/04-mining.md} §3.1 makes noticing exactly
 * that the way a player discovers a miner they did not deploy. It is drawn as blinking alarm cells
 * and it is never synthesised: if the number is zero the slice does not exist, so its appearance
 * always means something.
 */
public final class CycleGrid extends VBox {

    private static final double GAP = UiTokens.HAIR;

    /**
     * The largest a cell may grow.
     *
     * <p>{@link UiTokens#CELL} is the 11px base §2.3 fixes; a little headroom above it keeps the
     * grid comfortable on a large display without letting it turn into a chessboard on a wide panel.
     */
    private static final double MAX_CELL = UiTokens.CELL + 3;

    /** The smallest a cell may shrink before the row count drops instead. The reference's floor. */
    private static final double MIN_CELL = 6;

    /** Narrowest a legend column may be before the legend drops to fewer columns. */
    private static final double LEGEND_MIN_COLUMN = 230;

    private final CellField field = new CellField();
    private final HBox beside = new HBox(UiTokens.SPACE_6);

    /** The left half: the cell field and the legend that names its colours. */
    private final VBox left = new VBox(UiTokens.SPACE_6);

    private final javafx.scene.layout.GridPane legend = new javafx.scene.layout.GridPane();
    private final List<Cell> cells = new ArrayList<>();
    private final List<Slice> slices = new ArrayList<>();
    private AutoCloseable blink;

    public CycleGrid() {
        super(UiTokens.SPACE_6);
        field.getStyleClass().add("es-grid-well");
        // Left-aligned and shrink-wrapped. With the cell size capped the field no longer fills a
        // wide panel, and a stretched well with a hundred small cells huddled in one corner would
        // look like a layout fault rather than a deliberate density.
        // ⚠ maxWidth on the FIELD, never setFillWidth(false) on this VBox. Turning fill off shrinks
        // every child to its preferred width, which silently pins the legend to one column no matter
        // how wide the panel is.
        field.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        legend.getStyleClass().add("es-legend");
        legend.setHgap(UiTokens.HAIR);
        legend.setVgap(UiTokens.HAIR);
        // ⚠ A GridPane with percentage columns, not a FlowPane. The legend's own background IS the
        // 1px gap between rows (§4: "key:value rows on a 1px grid, not chips"), so any part of it
        // a row does not cover renders as a solid block of rule colour. A FlowPane leaves exactly
        // that: the ragged remainder at the end of each line.
        legend.widthProperty().addListener((obs, was, now) -> relayoutLegend());
        // ── the split ────────────────────────────────────────────────────────────────────────
        //
        // Two halves: the allocation on the left — the cell field AND the legend that names its
        // colours — and the instruments on the right. The legend belongs with the thing it
        // explains; it used to run the full width underneath both columns, which put the key to
        // the left half's colours under the right half's animations.
        //
        // ⚠ The two columns are TOP-aligned so the instruments begin at the same line the cell
        // field does. That is the whole point of a split: two things being read side by side have
        // to start together, or the eye reads them as sequence rather than as comparison.
        left.getChildren().addAll(field, legend);
        left.setAlignment(Pos.TOP_LEFT);
        // The well grows to take whatever height the row ends up with, so the left half reads as
        // one panel rather than a small grid floating above empty space. The cells stay their own
        // size — see CellField.layoutChildren, which lays out from the top left and does not
        // stretch them.
        VBox.setVgrow(field, javafx.scene.layout.Priority.ALWAYS);
        beside.getChildren().add(left);
        beside.setAlignment(Pos.TOP_LEFT);
        // ⚠ fillHeight TRUE here, unlike the shrink-wrapped arrangement this replaced. Both halves
        // are columns now and both want the row's height: the left so its well can fill, the right
        // so its own VBox can top-align the instruments inside it. What must NOT happen is the old
        // failure — a StackPane handed the full height and centring its content — and `setAside`
        // guards that by wrapping whatever it is given in a top-aligned box.
        beside.setFillHeight(true);
        // ⚠ prefWidth ZERO on both halves, plus Hgrow on both, is what makes the split EQUAL. With
        // their natural preferred widths left in place the row divides the *surplus* evenly rather
        // than the whole, so the half holding the wider content (the legend) keeps its head start —
        // measured at roughly 64/36 before this line.
        left.setPrefWidth(0);
        HBox.setHgrow(left, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(beside);
        // Recovering and unaccounted cells alternate between two states on a two-step loop (§5).
        // One subscription drives every such cell, not one per cell (§7.3).
        blink = Pulse.shared().animate(UiTokens.RECOVERY_BLINK_MS, this::flip);
    }

    /**
     * Replaces the whole allocation.
     *
     * <p>Rebuilding rather than diffing: a hundred {@link Region}s is explicitly fine (§7.3), and a
     * diff would be a cache that can disagree with the model — which is precisely the failure the
     * grid exists to make visible.
     */
    public void show(List<Slice> allocation) {
        slices.clear();
        slices.addAll(allocation);
        cells.clear();
        field.getChildren().clear();
        legend.getChildren().clear();

        for (Slice slice : allocation) {
            for (int i = 0; i < slice.cells(); i++) {
                Cell cell = new Cell(slice.owner());
                cells.add(cell);
                field.getChildren().add(cell);
            }
        }
        relayoutLegend();
        field.requestLayout();
    }

    /** Re-flows the legend into as many equal columns as the current width affords. */
    private void relayoutLegend() {
        legend.getChildren().clear();
        legend.getColumnConstraints().clear();
        // ⚠ HIDDEN is filtered out here and nowhere else, so the cells exist and the row does not.
        // A legend entry is a NAME, and naming unattributed capacity is precisely what the readout
        // must not do before an audit — see Owner.HIDDEN.
        List<Slice> listed =
                slices.stream().filter(s -> s.owner() != Owner.HIDDEN).toList();
        if (listed.isEmpty()) {
            return;
        }
        double width = legend.getWidth() > 0 ? legend.getWidth() : LEGEND_MIN_COLUMN;
        int columns = Math.max(1, Math.min(listed.size(), (int) Math.floor(width / LEGEND_MIN_COLUMN)));
        for (int i = 0; i < columns; i++) {
            var constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setFillWidth(true);
            legend.getColumnConstraints().add(constraint);
        }
        for (int i = 0; i < listed.size(); i++) {
            legend.add(legendRow(listed.get(i)), i % columns, i / columns);
        }
        // Fill the remainder of the last row. The legend's background IS the 1px gap between rows,
        // so an empty grid cell is not empty on screen — it is a solid block of rule colour sitting
        // where a row should be, which reads as a rendering fault.
        int remainder = listed.size() % columns;
        if (remainder != 0) {
            for (int column = remainder; column < columns; column++) {
                Region filler = new Region();
                filler.getStyleClass().add("es-legend-row");
                filler.setMaxWidth(Double.MAX_VALUE);
                legend.add(filler, column, listed.size() / columns);
            }
        }
    }

    private Node legendRow(Slice slice) {
        Region swatch = Ui.block(8, 8, "es-swatch");
        swatch.getStyleClass().add(slice.owner().styleClass());

        Label key = Ui.label(slice.label());
        key.getStyleClass().add("es-legend-key");
        HBox.setHgrow(key, javafx.scene.layout.Priority.ALWAYS);

        Label count = new Label(slice.cells() + "c");
        count.getStyleClass().add("es-legend-n");
        Label detail = Ui.label(slice.detail());
        detail.getStyleClass().add("es-legend-sub");

        HBox row = Ui.row(UiTokens.SPACE_4, swatch, key, count, detail);
        row.getStyleClass().add("es-legend-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        // Instant isolation, no transition (§4). Hovering a row is a question — "which of these are
        // mine?" — and an animated answer would be slower than the question.
        row.setOnMouseEntered(e -> isolate(slice.owner()));
        row.setOnMouseExited(e -> isolate(null));

        if (slice.freeText() != null && slice.onFree() != null) {
            row.getStyleClass().add("es-legend-freeable");
            ContextMenu menu = new ContextMenu();
            MenuItem free = new MenuItem(slice.freeText());
            free.setOnAction(event -> slice.onFree().run());
            menu.getItems().add(free);
            // ⚠ ANCHORED TO THE WINDOW, NEVER TO THE ROW. This panel repaints on the one-second data
            // tick and `relayoutLegend` clears and rebuilds every row, so the Label the player
            // right-clicked is detached from the scene by the time a popup anchored to it would show
            // — JavaFX throws "The owner node needs to be associated with a window" on the FX thread,
            // on every right-click. NetMapView and NotesView both record this exact failure; screen
            // coordinates make it identical on screen either way.
            row.setOnContextMenuRequested(event -> {
                if (row.getScene() == null || row.getScene().getWindow() == null) {
                    return;
                }
                menu.show(row.getScene().getWindow(), event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }
        return row;
    }

    private void isolate(Owner owner) {
        for (Cell cell : cells) {
            boolean muted = owner != null && cell.owner != owner;
            cell.getStyleClass().removeAll("es-cell-muted");
            if (muted) {
                cell.getStyleClass().add("es-cell-muted");
            }
        }
    }

    private void flip() {
        for (Cell cell : cells) {
            cell.flip();
        }
    }

    /**
     * Places a node in the space to the right of the cell field.
     *
     * <p>The field is capped at 25 cells of at most 14px, so on any panel wider than about 400px
     * there is room beside it that the legend below does not use. Rather than stretch the grid —
     * which would turn the signature component into a chessboard, the exact failure
     * {@link #MAX_CELL} exists to prevent — the space takes a second instrument.
     */
    public void setAside(javafx.scene.Node aside) {
        while (beside.getChildren().size() > 1) {
            beside.getChildren().removeLast();
        }
        if (aside == null) {
            return;
        }
        // ⚠ Wrapped in a TOP-ALIGNED box rather than added directly. The row hands its children the
        // full height now, and a StackPane given a height centres its content inside it — which is
        // exactly the gap-above-the-cutaway defect this class already carries a note about. The
        // wrapper makes "starts at the top" a property of the slot rather than of whatever is put
        // in it.
        VBox right = new VBox(aside);
        right.setAlignment(Pos.TOP_LEFT);
        right.setPrefWidth(0);
        HBox.setHgrow(right, javafx.scene.layout.Priority.ALWAYS);
        beside.getChildren().add(right);
    }

    /** Stops this grid's share of the shared driver. Called when the panel closes. */
    public void dispose() {
        if (blink != null) {
            try {
                blink.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; the unsubscribe itself cannot fail.
            }
            blink = null;
        }
    }

    /** One contiguous run of cells with the same owner, plus what the legend says about it. */
    /**
     * One band of the grid, and its legend row.
     *
     * <h2>⚠ THE FREE ACTION RIDES ON THE SLICE, not on the {@link Owner}</h2>
     *
     * The obvious design is a {@code Consumer<Owner>} on the widget, and it cannot work: the owner is
     * a <b>colour</b>, and two different consumers deliberately share one. {@code SHELL_SESSION} and
     * {@code ACTIVE_TOOL} are both {@link Owner#ACTIVE_TOOL} — "the player's own work reaching
     * outward" — so a handler told only the owner could not tell an open shell from a running scan,
     * and would unmount a machine when the player meant to cancel a port scan.
     *
     * <p>Carrying the action instead keeps this widget knowing nothing about compute, sessions or the
     * rules. It draws bands and rows; whoever built the slice decided what freeing it means.
     *
     * @param freeText what the menu item reads, or {@code null} for a band that cannot be freed —
     *     in which case no menu appears at all
     * @param onFree what to run; ignored when {@code freeText} is {@code null}
     */
    public record Slice(Owner owner, int cells, String label, String detail, String freeText, Runnable onFree) {

        /** A band nothing can be done to — every caller that is not offering a free action. */
        public Slice(Owner owner, int cells, String label, String detail) {
            this(owner, cells, label, detail, null, null);
        }
    }

    /**
     * Who is holding a cycle.
     *
     * <p>Mirrors {@code protocol}'s {@code ComputeConsumer} plus the three states that are not a
     * consumer at all — recovering under the Thermal Budget curve, free, and unaccounted for.
     */
    public enum Owner {
        SELF_MINING("es-cell-self-mining", false),
        CONTROL_CHANNEL("es-cell-channel", false),
        BOT_FRAME("es-cell-bot", false),
        DETECTION("es-cell-detection", false),
        FIREWALL("es-cell-firewall", false),
        ACTIVE_TOOL("es-cell-tool", false),
        RELAY_HOP("es-cell-relay", false),
        RECOVERING("es-cell-recovering", true),
        FREE("es-cell-free", false),
        UNKNOWN("es-cell-unknown", true),

        /**
         * Capacity that is gone and unattributed — drawn, dark, and <b>never in the legend</b>.
         *
         * <h2>The one owner that says nothing</h2>
         *
         * A parasite the player has not audited is omitted from the published ledger entirely
         * ({@code ComputeRules.snapshot}), so the rig is short and nothing names the shortfall. These
         * cells are that shortfall made countable. They are deliberately not {@link #UNKNOWN}: that
         * one blinks in alarm red and carries a legend row reading "Unaccounted", which is the readout
         * telling the player they are being robbed — for free, before the audit that
         * {@code docs/design/04-mining.md} §3.2 sells exactly that answer for.
         *
         * <p>What is left is the arithmetic, and §3.1 calls noticing it "the game's second-strongest
         * tutorial vector": claimed plus recovering plus free comes to less than the ceiling the
         * headline states. A player who counts sees a hole. A player who glances sees a slightly
         * emptier grid. Nobody is told anything.
         *
         * <p>⚠ Once an audit names the process its allocation rejoins the snapshot as an ordinary
         * {@code DEPLOYED_MINER} row, so these cells become a labelled {@link #UNKNOWN} slice with an
         * alarm and a note. The transition from silence to alarm <em>is</em> the product the scan
         * ladder sells.
         */
        HIDDEN("es-cell-hidden", false);

        private final String styleClass;
        private final boolean blinks;

        Owner(String styleClass, boolean blinks) {
            this.styleClass = styleClass;
            this.blinks = blinks;
        }

        public String styleClass() {
            return styleClass;
        }

        String alternateClass() {
            return styleClass + "-alt";
        }

        public boolean blinks() {
            return blinks;
        }
    }

    /** One cell. A {@link Region} rather than a shape — CSS owns every colour (§10 criterion 2). */
    private static final class Cell extends Region {
        private final Owner owner;
        private boolean alternate;

        private Cell(Owner owner) {
            this.owner = owner;
            getStyleClass().addAll("es-cell", owner.styleClass());
        }

        private void flip() {
            if (!owner.blinks()) {
                return;
            }
            alternate = !alternate;
            getStyleClass().removeAll(owner.styleClass(), owner.alternateClass());
            getStyleClass().add(alternate ? owner.alternateClass() : owner.styleClass());
        }
    }

    /**
     * Square cells that scale with the panel.
     *
     * <p>The one place in the client with a hand-written {@code layoutChildren}. Everything it does
     * is something JavaFX cannot express declaratively: a square aspect (§7.2 — no
     * {@code aspect-ratio}), an integral cell size so 1px gaps stay 1px instead of blurring across
     * subpixels, and a column count chosen from the measured width.
     */
    private static final class CellField extends Pane {

        /**
         * How many cells fit on a row.
         *
         * <p>⚠ Derived from how many cells the width can hold at {@link #MIN_CELL}, never from a
         * width threshold. The threshold version had a circular dependency that is worth naming: the
         * field shrink-wraps to its preferred width, its preferred width is a function of the column
         * count, and the column count was a function of its current width — which is zero before the
         * first layout. It settled on the narrowest option and stayed there, so the signature
         * component quietly rendered ten cells a row on a panel with room for twenty-five.
         */
        private int columns() {
            double inner = getWidth() - getInsets().getLeft() - getInsets().getRight();
            if (inner <= 0) {
                return UiTokens.CYCLE_PER_ROW;
            }
            int fits = (int) Math.floor((inner + GAP) / (MIN_CELL + GAP));
            if (fits >= UiTokens.CYCLE_PER_ROW) {
                return UiTokens.CYCLE_PER_ROW;
            }
            return fits >= UiTokens.CYCLE_PER_ROW_NARROW ? UiTokens.CYCLE_PER_ROW_NARROW : UiTokens.CYCLE_PER_ROW_TIGHT;
        }

        private double cellSize(int columns, double width) {
            double inner = width - getInsets().getLeft() - getInsets().getRight();
            // Floored to a whole pixel: a fractional cell makes every gap in the row a slightly
            // different width, and at 1px the difference between 1.0 and 0.6 of a pixel is the
            // difference between a hairline and a smudge.
            double fitted = Math.floor((inner - (columns - 1) * GAP) / columns);
            // Capped, and this matters more than it looks. §2.3 fixes an 11px base cell and §4 calls
            // the grid a field of small discrete cells. Left to fill the panel, 25 cells across a
            // wide pane become 33px squares and the component stops reading as "a hundred countable
            // things" and starts reading as a chessboard — which is the density argument in §1 lost
            // at exactly the size where there was most room to honour it.
            return Math.max(2, Math.min(MAX_CELL, fitted));
        }

        @Override
        protected void layoutChildren() {
            int columns = columns();
            double size = cellSize(columns, getWidth());
            double left = getInsets().getLeft();
            double top = getInsets().getTop();
            List<Node> children = getChildren();
            for (int i = 0; i < children.size(); i++) {
                int row = i / columns;
                int column = i % columns;
                children.get(i).resizeRelocate(left + column * (size + GAP), top + row * (size + GAP), size, size);
            }
        }

        @Override
        protected double computePrefHeight(double width) {
            double w = width > 0 ? width : getWidth();
            int columns = columns();
            double size = cellSize(columns, w);
            int rows = (int) Math.ceil(getChildren().size() / (double) columns);
            return getInsets().getTop() + getInsets().getBottom() + rows * size + Math.max(0, rows - 1) * GAP;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        /**
         * A full row at the maximum cell size — independent of the current width, which is what
         * breaks the cycle described in {@link #columns()}.
         */
        @Override
        protected double computePrefWidth(double height) {
            int used =
                    Math.min(UiTokens.CYCLE_PER_ROW, Math.max(1, getChildren().size()));
            return getInsets().getLeft() + getInsets().getRight() + used * MAX_CELL + Math.max(0, used - 1) * GAP;
        }
    }
}
