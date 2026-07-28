package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.RigProcess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The process table: sortable columns, one row per process, and a menu on each.
 *
 * <h2>Character cells, not a {@code TableView}</h2>
 *
 * The same call {@code NetHostList} makes and for the same reasons. A {@code TableView} brings Modena
 * with it — {@code docs/design/ui-design-language.md} §9 rejects its radius, its shadows and its
 * proportional type — and it would give this panel a selection model, a focus model and a sort
 * comparator that all have to be re-taught the house style. A fixed-width text row gives per-row hit
 * testing, a keyboard route, a focus ring and an accessible sentence, and it lines up down the column,
 * which is the only property that matters here: <b>this table exists to be compared down a column.</b>
 *
 * <h2>⚠ No row is styled by what it is</h2>
 *
 * A parasite hides here by looking like the rows around it, and every tell is in the data. There is
 * deliberately no "suspicious" class, no alarm ramp and no ordering that floats anything to the top —
 * a renderer that highlighted the answer would turn {@code docs/design/04-mining.md} §3.1's
 * investigation into a game of spot-the-red-row. The only visual distinction any row gets is whether
 * it is <em>selected</em>, which is the player's own doing.
 *
 * <h2>Sorting is the instrument</h2>
 *
 * It is how two of the disguises are actually caught: sort by CPU and a resource hog surfaces; sort by
 * name and a typosquat lands next to the daemon it is imitating. So the header is not decoration and
 * the sort must be <b>reversible</b> — clicking a column twice gives the reverse order — and
 * <b>stable at equal values</b>, or two rows the comparator cannot separate would swap on every
 * repaint.
 *
 * <p>⚠ <b>Rows genuinely move, and that is the feature.</b> The figures advance on a five-second tick
 * ({@code Vitals}), so a table sorted by %CPU visibly re-orders as processes get busier and quieter —
 * the same thing a real monitor does, and the thing that makes a row which stays pinned at the top
 * worth a second look.
 *
 * <h2>⚠ It repaints on its own clock, not on the session's</h2>
 *
 * The session fires a change when the <em>game</em> changes, which on an idle rig can be never. A
 * table that only repainted then would freeze the moment the player stopped doing anything — exactly
 * when they are most likely to be reading it. So this holds a {@link Pulse#every} subscription, which
 * is the non-decorative driver and therefore still fires under reduced motion: this is instrumentation,
 * not animation, and a player who has asked for less movement has not asked for stale numbers.
 */
public final class ProcessTableView extends VBox {

    /**
     * One column: what it is called, how wide, how to print it, and how to order by it.
     *
     * @param numeric right-aligned and sorted descending on the first click, the way a resource column
     *     behaves everywhere. A name column sorts ascending first; a CPU column does not, and getting
     *     that backwards makes the header feel broken before anyone can say why
     */
    public record Column(
            String title,
            int width,
            boolean numeric,
            java.util.function.Function<RigProcess, String> text,
            Comparator<RigProcess> order) {}

    private final Label header = new Label();
    private final VBox rows = new VBox(UiTokens.SPACE_1);

    private List<Column> columns = List.of();
    private List<RigProcess> processes = List.of();
    private int sortColumn;
    private boolean descending;
    private String selected = "";
    private String painted = "";

    private Consumer<RigProcess> onKill = process -> {};
    private Consumer<RigProcess> onRestart = process -> {};
    private Consumer<RigProcess> onSelect = process -> {};
    private Consumer<List<RigProcess>> onSample = rows -> {};

    /**
     * The menu currently on screen, or null.
     *
     * <p>⚠ Held so a repaint can decline to happen underneath it. The rows rebuild every five seconds
     * and a rebuilt {@code Label} takes its {@code ContextMenu} with it — so without this, opening
     * the menu on a row and then reading it for longer than one tick makes the menu vanish mid-read,
     * and on a slow hand the kill lands on whatever row moved into that position. The most
     * consequential control on the panel must not be a race.
     */
    private ContextMenu open;

    private Supplier<List<RigProcess>> source = List::of;
    private AutoCloseable ticker;

    public ProcessTableView() {
        super(UiTokens.SPACE_2);
        getStyleClass().addAll("es-netlist", "es-proctable");
        header.getStyleClass().addAll("es-netlist-head", "es-proc-head");
        getChildren().addAll(header, rows);
    }

    /**
     * Binds the table to a live source and starts its own clock.
     *
     * <p>A supplier rather than a list, because the point of the tick is to re-read: the figures move
     * whether or not the game does, and a snapshot handed in once would tick nothing.
     */
    public void bind(Supplier<List<RigProcess>> source) {
        this.source = source == null ? List::of : source;
        stop();
        // Vitals.INTERVAL_SECONDS is the period the engine advances every figure on. Repainting on a
        // different one would either miss changes or draw them at an unrelated moment, and both read
        // as instrumentation that cannot be trusted.
        ticker = Pulse.shared().every(TICK_MS, this::pull);
        pull();
    }

    /** Five seconds, matching {@code Vitals.INTERVAL_SECONDS} — the engine's own step. */
    private static final double TICK_MS = 5_000;

    private void pull() {
        List<RigProcess> rows = source.get();
        setProcesses(rows);
        onSample.accept(rows);
    }

    /** Stops the clock. Called when the panel leaves the scene. */
    public void dispose() {
        stop();
    }

    private void stop() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // Unsubscribing cannot fail; AutoCloseable's checked exception is the only reason
                // this is not a one-liner.
            }
            ticker = null;
        }
    }

    /**
     * Sets the columns this tab shows.
     *
     * <p>Resets the sort to the first column, because a sort index is only meaningful against the
     * column list it was chosen from — carrying it across a tab change would sort MEMORY by whatever
     * happened to be third on the CPU tab.
     */
    public void setColumns(List<Column> columns) {
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.sortColumn = 0;
        // The first column of every tab but Overview is a resource figure, and a resource table
        // opens on "the biggest thing first" or it opens useless.
        this.descending = !this.columns.isEmpty() && this.columns.get(0).numeric();
        this.painted = "";
        apply();
    }

    public void setProcesses(List<RigProcess> processes) {
        this.processes = processes == null ? List.of() : List.copyOf(processes);
        apply();
    }

    public void setOnKill(Consumer<RigProcess> handler) {
        this.onKill = handler == null ? p -> {} : handler;
    }

    public void setOnRestart(Consumer<RigProcess> handler) {
        this.onRestart = handler == null ? p -> {} : handler;
    }

    /**
     * Called with the whole table every time it refreshes.
     *
     * <p>⚠ For anything that has to sample on the <b>same beat</b> the figures move on. The history
     * graphs above the table use this rather than a clock of their own: two five-second timers
     * started a moment apart would put the same spike at two different places on the chart, and the
     * chart is instrumentation — the whole value of it is that it can be trusted against the table.
     */
    public void setOnSample(Consumer<List<RigProcess>> handler) {
        this.onSample = handler == null ? rows -> {} : handler;
    }

    public void setOnSelect(Consumer<RigProcess> handler) {
        this.onSelect = handler == null ? p -> {} : handler;
    }

    public void setSelected(String processId) {
        this.selected = processId == null ? "" : processId;
        apply();
    }

    /** The whole table as text — the headless seam, read off the labels actually on screen. */
    public String frame() {
        List<String> lines = new ArrayList<>();
        lines.add(header.getText());
        for (var child : rows.getChildren()) {
            if (child instanceof Label label) {
                lines.add(label.getText());
            }
        }
        return String.join("\n", lines);
    }

    // ------------------------------------------------------------------ ordering

    /**
     * The rows in the order the player asked for.
     *
     * <p>⚠ Always tie-broken on {@code pid}. Two rows the chosen comparator calls equal — and a CPU
     * column full of daemons at 0.4% produces a lot of those — would otherwise swap places between
     * repaints, which makes the row under the pointer stop being the row that gets clicked and makes
     * comparing two readings impossible. Both of those are how a table stops being usable.
     */
    List<RigProcess> ordered() {
        if (columns.isEmpty()) {
            return processes;
        }
        Comparator<RigProcess> comparator = columns.get(Math.min(sortColumn, columns.size() - 1)).order();
        if (descending) {
            comparator = comparator.reversed();
        }
        List<RigProcess> out = new ArrayList<>(processes);
        out.sort(comparator.thenComparingInt(RigProcess::pid));
        return out;
    }

    // ------------------------------------------------------------------ rendering

    private void apply() {
        // ⚠ Never underneath an open menu. See the `open` field: a rebuilt row takes its menu with
        // it, so repainting here would close the menu mid-read and, worse, leave the next click
        // landing on whatever row had moved into that position.
        if (open != null && open.isShowing()) {
            return;
        }
        String key = sortColumn + (descending ? "-" : "+") + selected + processes.hashCode();
        if (key.equals(painted) && !rows.getChildren().isEmpty()) {
            return;
        }
        painted = key;
        header.setText(headerText());
        rows.getChildren().clear();

        if (processes.isEmpty()) {
            Label empty = new Label("Nothing is running that this view can see.");
            empty.getStyleClass().add("es-netlist-empty");
            empty.setWrapText(true);
            rows.getChildren().add(empty);
            return;
        }
        for (RigProcess process : ordered()) {
            rows.getChildren().add(row(process));
        }
        // Rebuilt whole, so the header's clickable regions go with it. The header is one Label with
        // per-column hit testing rather than a Label per column, because a row of Labels does not
        // line up with a single fixed-width row of text underneath it — the gap between two Labels
        // is a layout gap, not a character.
        installHeaderHits();
    }

    private String headerText() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            // The sort marker lives inside the column's own width, so turning it on never shifts
            // anything to its right. A header that reflowed when you sorted it would be unreadable
            // at the exact moment you were reading it.
            String title = column.title() + (i == sortColumn ? (descending ? " v" : " ^") : "");
            out.append(pad(title, column.width(), false));
        }
        return out.toString().stripTrailing();
    }

    private void installHeaderHits() {
        Cursors.shared().clickable(header);
        header.setOnMouseClicked(event -> {
            int column = columnAt(event.getX());
            if (column < 0) {
                return;
            }
            if (column == sortColumn) {
                descending = !descending;
            } else {
                sortColumn = column;
                descending = columns.get(column).numeric();
            }
            painted = "";
            apply();
        });
    }

    /**
     * Which column a click landed in.
     *
     * <p>Measured in character cells against the header's own font, because the columns are character
     * widths rather than pixel widths. Estimating from the Label's width divided by the character
     * count is what makes this survive a theme change: every theme in this client sets a different
     * font size and none of them changes the character grid.
     */
    private int columnAt(double x) {
        double total = 0;
        for (Column column : columns) {
            total += column.width();
        }
        if (total <= 0 || header.getWidth() <= 0) {
            return -1;
        }
        double cell = header.getWidth() / total;
        int at = (int) (x / cell);
        int cursor = 0;
        for (int i = 0; i < columns.size(); i++) {
            cursor += columns.get(i).width();
            if (at < cursor) {
                return i;
            }
        }
        return -1;
    }

    private Label row(RigProcess process) {
        StringBuilder text = new StringBuilder();
        for (Column column : columns) {
            text.append(pad(column.text().apply(process), column.width(), column.numeric()));
        }

        Label label = new Label(text.toString().stripTrailing());
        label.getStyleClass().addAll("es-netlist-row", "es-proc-row", "es-focusable");
        if (process.processId().equals(selected)) {
            label.getStyleClass().add("es-netlist-selected");
        }
        label.setFocusTraversable(true);
        // The same hole BreachTargetList had: a Labeled is picked where it paints, and these rows
        // paint no background. A right-click landing in the gap after a short process name would
        // otherwise miss the row it is plainly over — and on this panel that click is a kill.
        label.setPickOnBounds(true);
        label.setMaxWidth(Double.MAX_VALUE);
        Cursors.shared().clickable(label);
        label.setAccessibleText(describe(process));

        ContextMenu menu = menuFor(process);
        menu.setOnHidden(event -> open = null);
        label.setOnMouseClicked(event -> {
            event.consume();
            selected = process.processId();
            onSelect.accept(process);
            if (event.getButton() == MouseButton.SECONDARY) {
                open = menu;
                menu.show(label, event.getScreenX(), event.getScreenY());
            } else {
                apply();
            }
        });
        label.setOnContextMenuRequested(event -> {
            // ⚠ Also bound here, not only on the right-click above. A context menu raised by the
            // keyboard (or by a trackpad's two-finger tap, which some configurations deliver as this
            // event and not as a SECONDARY click) must reach the same menu, or the feature is
            // mouse-only — which docs/client/07 §4 does not allow for an action with no other route.
            event.consume();
            selected = process.processId();
            onSelect.accept(process);
            open = menu;
            menu.show(label, event.getScreenX(), event.getScreenY());
        });
        label.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                event.consume();
                selected = process.processId();
                onSelect.accept(process);
                apply();
            }
        });
        return label;
    }

    /**
     * The menu on a row.
     *
     * <p>⚠ A system process is offered <b>restart and nothing else</b>, and it is not a disabled
     * "kill" beside it. A greyed-out control still asks to be understood and invites a player to hunt
     for a way to enable it; an absent one says the verb does not apply. The rules refuse a kill on
     * these too, so this is the second of two guards rather than the only one.
     */
    private ContextMenu menuFor(RigProcess process) {
        ContextMenu menu = new ContextMenu();
        if (process.killable()) {
            MenuItem kill = new MenuItem("Kill " + process.name());
            kill.setOnAction(event -> onKill.accept(process));
            menu.getItems().add(kill);
        }
        if (process.restartable()) {
            MenuItem restart = new MenuItem("Restart " + process.name());
            restart.setOnAction(event -> onRestart.accept(process));
            menu.getItems().add(restart);
        }
        if (menu.getItems().isEmpty()) {
            MenuItem none = new MenuItem("Nothing to do to this one");
            none.setDisable(true);
            menu.getItems().add(none);
        }
        return menu;
    }

    /**
     * A row as a sentence.
     *
     * <p>{@code docs/client/07-accessibility.md} §5.2: meaning must not rest on appearance. A
     * fixed-width row read aloud is a run of numbers, and the tells in this table are relationships
     * between those numbers — which means a screen-reader user needs the figures said in words or the
     * whole audit is closed to them.
     */
    static String describe(RigProcess process) {
        StringBuilder out = new StringBuilder(process.name())
                .append(", pid ").append(process.pid())
                .append(", running as ").append(process.user())
                .append(", ").append(String.format(java.util.Locale.ROOT, "%.1f", process.cpuPercent()))
                .append("% CPU, ").append(process.cpuTimeText()).append(" of processor time, ")
                .append(process.memoryText()).append(" of memory");
        if (process.cycles() > 0) {
            out.append(", ").append(process.cycles()).append(" cycles");
        }
        out.append(process.killable() ? ". Can be killed." : process.restartable() ? ". Can be restarted." : ".");
        if (!process.detail().isBlank()) {
            out.append(' ').append(process.detail());
        }
        return out.toString();
    }

    /**
     * Left-aligns into a fixed cell, or right-aligns a numeric one.
     *
     * <p>Right-alignment on numbers is not a nicety: a column of figures the player is comparing has
     * to line up on its last digit or the comparison is by string length. Over-long values clip
     * rather than push, so one long process name cannot shear every column to its right.
     */
    static String pad(String value, int width, boolean numeric) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, Math.max(0, width - 1)) + " ";
        }
        String gap = " ".repeat(width - text.length());
        return numeric ? gap.substring(1) + text + " " : text + gap;
    }
}
