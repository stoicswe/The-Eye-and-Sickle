package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

/**
 * The exhaustive list of everything the player has discovered.
 *
 * <h2>Why the list is rebuilt rather than kept</h2>
 *
 * The window that shipped before this one rendered known nodes into a raw {@code TableView} styled
 * with {@code es-panel-title}, {@code es-mono}, {@code es-text-secondary}, {@code es-state-refused}
 * and {@code es-state-unreachable} — <b>none of which {@code theme.css} declares</b>. A style class
 * the stylesheet does not know about is not an error: JavaFX applies nothing and the control renders
 * at Modena's defaults while the Java reads as though it were themed. So the old list looked
 * deliberate in source and was, on screen, the one panel in the client outside the design language.
 * Rebuilding it is cheaper than retrofitting a {@code TableView} that {@code ui-design-language.md}
 * §9 rejects four ways over (radius, shadow, gradient, proportional type).
 *
 * <h2>The list is the exhaustive surface; the graph is the legible one</h2>
 *
 * A server may hold up to fifty machines and the generated world up to seven servers, so there is a
 * density at which a picture stops being readable and a table does not. That split is the design
 * rather than a limitation, and both halves of it are honest: the graph clamps a layer and says how
 * many it dropped, and this shows every row it is given, with no cap and no paging.
 *
 * <h2>What a row may say</h2>
 *
 * Everything printed here comes from {@link NetText}, so the terminal's {@code net} and this panel
 * cannot drift apart — see that class for why that matters and for the two things a row is not
 * allowed to infer. Nothing undiscovered appears at all: a node the sweep did not detect is absent
 * from {@link NetMap#sightings()}, and there is deliberately no count, no placeholder and no
 * "3 contacts nearby" to hint that it exists.
 *
 * <h2>Rows are only rebuilt when their text changes</h2>
 *
 * Same discipline as {@link BreachTargetList}: the session fires a change on most ticks, and
 * rebuilding a list of focusable labels on each of those would drop keyboard focus out from under
 * anyone tabbing through it. The rendered lines are the comparison key — exact, cheap, and true by
 * definition, because the lines <em>are</em> what the player can see.
 */
public final class NetHostList extends VBox {

    private final Label head = new Label();
    private final VBox rows = new VBox(UiTokens.SPACE_1);

    private List<String> rendered = List.of();
    private NetMap map = NetMap.empty();
    private Consumer<String> onNode = address -> {};

    /** Right-click on a row. Separate from {@link #onNode} — see {@code NetGraph} for why. */
    private java.util.function.BiConsumer<String, javafx.scene.input.ContextMenuEvent> onNodeMenu =
            (address, event) -> {};

    private boolean verbose;
    private String selected = "";
    private String paintedFor = "";

    public NetHostList() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-netlist");

        head.getStyleClass().add("es-netlist-head");
        head.setText(NetText.header(false));

        getChildren().addAll(head, rows);
        apply();
    }

    /**
     * Binds the panel to a map.
     *
     * <p>Takes the whole {@link NetMap} rather than a list of sightings because a row needs the
     * map's server table to turn a {@code serverId} into the name a player recognises, and handing
     * this class the sightings alone would mean either a second lookup table here or a column
     * printing raw ids.
     */
    public void setMap(NetMap map) {
        this.map = map == null ? NetMap.empty() : map;
        apply();
    }

    /** Adds the {@code SIGNAL} and {@code DEPTH} columns — the terminal's {@code net -v}. */
    public void setVerbose(boolean verbose) {
        if (this.verbose == verbose) {
            return;
        }
        this.verbose = verbose;
        head.setText(NetText.header(verbose));
        rendered = List.of();
        apply();
    }

    /**
     * Marks the machine the player has picked — the same one the graph double-frames.
     *
     * <p>Here for parity rather than for its own sake: the three views select one thing, so a player
     * who picks a machine in the graph and switches to the list must not have to find it again by
     * reading addresses. The mark is a style class only, because unlike the graph this surface has a
     * {@code STATE} column carrying the row's standing in words already — a geometric marker here
     * would be a second vocabulary for a fact the row states.
     */
    public void setSelected(String address) {
        this.selected = address == null ? "" : address;
        apply();
    }

    /** Called with a row's address when it is clicked or activated from the keyboard. */
    public void setOnNodeMenu(java.util.function.BiConsumer<String, javafx.scene.input.ContextMenuEvent> handler) {
        this.onNodeMenu = handler == null ? (address, event) -> {} : handler;
    }

    public void setOnNode(Consumer<String> onNode) {
        this.onNode = onNode == null ? address -> {} : onNode;
    }

    /**
     * The whole panel as text, header included.
     *
     * <p>The headless seam. It reads the labels that are actually on screen rather than
     * re-rendering, so a test that passes here is a statement about what a player sees and not about
     * what the formatter would have produced if it had been asked twice.
     */
    public String frame() {
        List<String> lines = new ArrayList<>();
        lines.add(head.getText());
        for (var child : rows.getChildren()) {
            if (child instanceof Label label) {
                lines.add(label.getText());
            }
        }
        return String.join("\n", lines);
    }

    // ------------------------------------------------------------------ rendering

    private void apply() {
        List<String> lines = NetText.rows(map, verbose);
        // The selection joins the comparison key. It is a visible change the lines alone do not
        // carry, so leaving it out would mean clicking a row repainted nothing until the next sweep.
        if (lines.equals(rendered)
                && selected.equals(paintedFor)
                && !rows.getChildren().isEmpty()) {
            return;
        }
        rendered = List.copyOf(lines);
        paintedFor = selected;
        rows.getChildren().clear();

        if (lines.isEmpty()) {
            Label empty = new Label(NetText.EMPTY);
            empty.getStyleClass().add("es-netlist-empty");
            empty.setWrapText(true);
            rows.getChildren().add(empty);
            head.setVisible(false);
            head.setManaged(false);
            return;
        }
        head.setVisible(true);
        head.setManaged(true);

        List<Sighting> sightings = NetText.ordered(map);
        for (int i = 0; i < lines.size(); i++) {
            rows.getChildren().add(row(sightings.get(i), lines.get(i)));
        }
    }

    /**
     * One row: a focusable {@link Label}, not a table cell.
     *
     * <p>Same structure the breach's lattice uses, and for the same four reasons — it is the only
     * shape in this client that gives per-row hit testing, a keyboard route, a focus ring and
     * per-row accessible text on a character-cell surface at once. A {@code TableView} would give
     * three of the four and bring Modena with it.
     */
    private Label row(Sighting sighting, String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("es-netlist-row", "es-focusable");
        if (sighting.address().equals(selected)) {
            label.getStyleClass().add("es-netlist-selected");
        }
        // ⚠ `self`, not `vantage`. Greying is for machines the player cannot operate from, and
        // their own rig is never one of those — but keyed to the vantage, moving it greyed out the
        // player's own machine in their own host list.
        if (!sighting.self() && !sighting.foothold()) {
            // The grey ramp, second. The distinction that matters most on this panel — where the
            // player can actually operate from — is already carried by the STATE column in words,
            // which is what survives a greyscale capture and a screen reader both.
            label.getStyleClass().add("es-netlist-muted");
        }
        label.setAccessibleText(describe(sighting));
        label.setFocusTraversable(true);
        Cursors.shared().clickable(label);
        label.setOnContextMenuRequested(event -> {
            onNodeMenu.accept(sighting.address(), event);
            event.consume();
        });
        label.setOnMouseClicked(event -> {
            event.consume();
            onNode.accept(sighting.address());
        });
        label.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                event.consume();
                onNode.accept(sighting.address());
            }
        });
        return label;
    }

    /**
     * A row, as a sentence.
     *
     * <p>{@code docs/client/07-accessibility.md} §5.2: meaning must not rest on appearance. A
     * fixed-width row read aloud is a run of numbers and dashes, so the accessible text says the
     * same facts in the order a person would ask for them — where it is, what it is (or that this
     * has not been established), and what standing the player has on it.
     */
    static String describe(Sighting sighting) {
        StringBuilder out = new StringBuilder(sighting.address());
        if (!sighting.label().isBlank()) {
            out.append(", ").append(sighting.label());
        }
        out.append(", ")
                .append(sighting.hopsFromVantage())
                .append(sighting.hopsFromVantage() == 1 ? " hop away" : " hops away");
        out.append(
                sighting.kind() == HostKind.UNKNOWN
                        ? ", type not established"
                        : ", " + sighting.kind().name().toLowerCase(java.util.Locale.ROOT));
        if (sighting.tier() != null) {
            out.append(", tier ").append(sighting.tier().tier());
        }
        out.append(", ").append(NetText.state(sighting));
        String note = NetText.note(sighting);
        if (!note.isEmpty()) {
            out.append(", ").append(note);
        }
        return out.append('.').toString();
    }
}
