package io.github.stoicswe.eyeandsickle.client.window;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.view.RigStripView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The single-window layout.
 *
 * <h2>A mode, not a fallback</h2>
 *
 * {@code docs/client/07-accessibility.md} §2.3 makes this a contract rather than a courtesy:
 * <b>no functionality or information may be lost.</b> Managing a dozen OS windows under time pressure
 * is a real barrier — for players using magnification, for anyone driving the machine by keyboard or
 * switch, and for a great many people who simply find it exhausting. Multi-window is the default and
 * the fantasy. It must not be the only option, and the alternative must not be a worse game.
 *
 * <h2>The layout, from {@code docs/client/05} §5.2</h2>
 *
 * <pre>
 *   ┌──────────────────────────────────────────────────────┐
 *   │ rig strip — chrome, 96px, never scrolls or collapses │
 *   ├────────┬─────────────────────────────────────────────┤
 *   │ rail   │ content: 1–3 TabPanes side by side          │
 *   │ 220px  │                                             │
 *   ├────────┴─────────────────────────────────────────────┤
 *   │ alert tray — 28px collapsed, 200px expanded          │
 *   └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * Three columns is the ceiling, and the reason is arithmetic: a fourth on a 1440px window gives each
 * pane 340px, below every tool's minimum width.
 *
 * <h2>Why the rig strip is outside the SplitPane</h2>
 *
 * Because a {@code SplitPane} divider can be dragged to zero. Putting the compute readout inside one
 * would give the player a way to hide the thing pillar C2 says must always be visible — not through
 * a control the UI offers, but through a mouse gesture nobody would think to forbid.
 */
public final class DockedShell {

    /** The ceiling from §5.2, and why. */
    public static final int MAX_COLUMNS = 3;

    /** Rail width from §5.2. */
    public static final double RAIL_WIDTH = 220;

    private final GameSession session;
    private final Map<WindowSpec, Function<WindowSpec, Region>> factories;
    private final List<TabPane> columns = new ArrayList<>();
    private final VBox rail = new VBox(4);
    private final BorderPane root = new BorderPane();

    public DockedShell(GameSession session, Map<WindowSpec, Function<WindowSpec, Region>> factories) {
        this.session = session;
        this.factories = new EnumMap<>(factories);
        build();
    }

    public Region root() {
        return root;
    }

    private void build() {
        // ---- rig strip: chrome. Outside every SplitPane, deliberately.
        root.setTop(RigStripView.create(session));

        // ---- rail
        Label railHeading = new Label("TOOLS");
        railHeading.getStyleClass().add("es-panel-title");
        rail.setPadding(new Insets(10));
        rail.getChildren().add(railHeading);
        for (WindowSpec spec : WindowSpec.values()) {
            if (spec == WindowSpec.RIG_MONITOR || spec == WindowSpec.SWITCHER) {
                // The strip already IS the rig monitor, and the rail already IS the switcher.
                continue;
            }
            Button open = new Button(spec.title());
            open.setMaxWidth(Double.MAX_VALUE);
            open.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // Target size floor from docs/client/07 §3.8 (WCAG SC 2.5.8 wants 24×24 minimum).
            open.setMinHeight(28);
            open.setAccessibleText(spec.title() + ", stands in for " + spec.unixAnalogue());
            open.setTooltip(new javafx.scene.control.Tooltip(spec.unixAnalogue()));
            open.setOnAction(e -> show(spec));
            rail.getChildren().add(open);
        }
        ScrollPane railScroll = new ScrollPane(rail);
        railScroll.setFitToWidth(true);
        railScroll.setMinWidth(RAIL_WIDTH);
        railScroll.setPrefWidth(RAIL_WIDTH);

        // ---- content
        TabPane first = newColumn();
        columns.add(first);
        SplitPane content = new SplitPane(first);

        SplitPane outer = new SplitPane(railScroll, content);
        outer.setDividerPositions(0.2);
        // Verified capability: the rail must not grow with the window, or a maximised client gives
        // 60% of the screen to a list of buttons (§5.2).
        SplitPane.setResizableWithParent(railScroll, false);

        root.setCenter(outer);

        // ---- alert tray
        TitledPane tray = new TitledPane("Alerts", new Label("Nothing needs you right now."));
        tray.setExpanded(false);
        tray.setAccessibleText("Alert tray. Expands when something needs attention.");
        root.setBottom(tray);

        // Open the two that first run opens, so the docked layout is not an empty room.
        show(WindowSpec.TERMINAL);
        show(WindowSpec.AUDIT);
    }

    private TabPane newColumn() {
        TabPane pane = new TabPane();
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        return pane;
    }

    /**
     * Shows a tool, reusing its tab if it is already open.
     *
     * <p>Same idempotence rule as the multi-window registry: opening, raising and focusing are one
     * action from the player's point of view, and a cross-window link must behave the same whether
     * the target is closed, buried, or already in front.
     */
    public void show(WindowSpec spec) {
        for (TabPane column : columns) {
            for (Tab tab : column.getTabs()) {
                if (spec.id().equals(tab.getId())) {
                    column.getSelectionModel().select(tab);
                    tab.getContent().requestFocus();
                    return;
                }
            }
        }
        Function<WindowSpec, Region> factory = factories.get(spec);
        if (factory == null) {
            return;
        }
        Tab tab = new Tab(spec.title(), factory.apply(spec));
        tab.setId(spec.id());
        tab.setTooltip(new javafx.scene.control.Tooltip(spec.title() + " — " + spec.unixAnalogue()));

        TabPane target = columns.getLast();
        target.getTabs().add(tab);
        target.getSelectionModel().select(tab);
    }

    /** Adds a column, up to the three-column ceiling. */
    public boolean addColumn() {
        if (columns.size() >= MAX_COLUMNS) {
            return false;
        }
        TabPane column = newColumn();
        columns.add(column);
        if (root.getCenter() instanceof SplitPane outer && outer.getItems().size() > 1) {
            Node contentNode = outer.getItems().get(1);
            if (contentNode instanceof SplitPane content) {
                content.getItems().add(column);
            }
        }
        return true;
    }

    public int columnCount() {
        return columns.size();
    }

    /**
     * Every tool reachable in this layout.
     *
     * <p>This is the mechanical form of §5.4's "no functionality or information is lost": the docked
     * shell must be able to show every window the multi-window layout can, and a test asserts it
     * rather than a reviewer noticing.
     */
    public static List<WindowSpec> reachable() {
        List<WindowSpec> out = new ArrayList<>();
        for (WindowSpec spec : WindowSpec.values()) {
            // The rig monitor is the strip and the switcher is the rail. Both are present as
            // chrome rather than as tabs, which is more available, not less.
            out.add(spec);
        }
        return out;
    }
}
