package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The player's machine and the network next to it, drawn as a character graph.
 *
 * <h2>What this panel is for</h2>
 *
 * The brief that produced it is blunt: discovery is unusable at the start, and a new player needs a
 * <b>network diagram</b> of the machines adjacent to their own. So this is the headline surface —
 * the answer to "what is next to me" — and the exhaustive list beside it is the reference, not the
 * picture. With up to fifty machines on a server that split is the honest one, and the panel says so
 * when a column has to clamp.
 *
 * <p>Columns are hop distance from the vantage, which is the same unit the rules are written in:
 * reach is a hard ceiling raised only by a schematic-gated tool (Invariant I2 — schematics buy reach,
 * ethecoin buys sensitivity), so a column is literally one purchase of reach away, and moving the
 * vantage by taking a foothold is the other way to cross one. See {@link NetLayout}.
 *
 * <h2>An undiscovered machine is drawn nowhere at all</h2>
 *
 * No cell, no placeholder, no {@code ··}, no "3 contacts nearby". A machine the player has not
 * detected has no {@code Sighting}, so there is nothing here to leak even by accident. The one thing
 * drawn outside the discovered set is a bridge's {@code ··} stub, which carries the peer
 * <em>server's name</em> and nothing else — that is the single fact a bridge exists to publish.
 *
 * <p>{@code ░░} is not a leak either, and the distinction is worth holding on to: it means "detected,
 * type not established", which is exactly what a sweep sells. Turning it into {@code ▒▒ TERMINAL} is
 * what the 15 EC Passive Sniffer sells.
 *
 * <h2>Structure: a Label per node cell, and the reason it is not one Label for the map</h2>
 *
 * Copied from {@code LatticeMap}, which is the only pattern in this codebase that gives per-node hit
 * testing, tooltips, keyboard focus and per-node accessible text on an ASCII graph — and this map
 * needs all four, because clicking a node is how a player targets one. The routing underneath is
 * computed as a <em>single</em> grid by {@link NetCanvas} and then sliced into those Labels, so a
 * lane that crosses a column boundary still merges correctly; per-column routing is what would let
 * two edges silently erase each other at the seam.
 *
 * <h2>Motion</h2>
 *
 * One {@code ·} steps along the vantage's outbound run, one cell per {@code NET_PACKET_MS} tick,
 * through {@link Pulse#animate} — decoration, so reduced motion freezes it — and it holds still
 * outright when there is no vantage or nothing leaves it. A map that keeps pulsing at a dead end
 * would be lying, which is {@code CoreCage}'s rule and {@code LatticeMap}'s after it.
 */
public final class NetGraph extends VBox {

    private final Label serverStrip = new Label();
    private final Label header = new Label();
    private final HBox columns = new HBox(0);
    private final Label note = Ui.micro("");

    /**
     * The gap Labels, in layer order — the only ones an animation step is allowed to touch.
     *
     * <p>⚠ Held so {@link #advance} can repaint the packet <b>without rebuilding the scene graph</b>.
     * A full rebuild every {@code NET_PACKET_MS} replaces every node cell three times a second, and a
     * replaced {@code Label} is one that has lost keyboard focus — so a player tabbing across the map
     * would be thrown back to the start of the traversal order before they could press SPACE, and a
     * screen reader would be re-announcing a cell that never moved. §4.8 requires per-node keyboard
     * focus; decoration does not get to take it away, which is the same rule that keeps the packet off
     * a junction glyph. The packet only ever lands inside a gap (see {@code NetCanvas.drawPacket}), so
     * repainting the gaps is the whole of the animation.
     */
    private final List<Label> gaps = new ArrayList<>();

    private final Consumer<String> onNode;

    /**
     * Right-click on a machine's cell.
     *
     * <p>Separate from {@link #onNode} because they answer different questions: a left click asks
     * "which machine am I talking about", a right click asks "what can I do to it". Folding the
     * second into the first would mean opening a menu also moved the selection, so the actions in
     * the menu would be about a machine the player had just changed by opening the menu.
     */
    private java.util.function.BiConsumer<String, javafx.scene.input.ContextMenuEvent> onNodeMenu =
            (address, event) -> {};

    private NetMap current = NetMap.empty();
    private String selected = "";
    private List<String> lines = List.of();
    private int packet;
    private int packetCells;
    private AutoCloseable ticker;

    /**
     * @param onNode receives a machine's address when its cell is clicked or activated with
     *     SPACE/ENTER. Never called for a bridge stub, which has no address the player has been sold
     */
    public NetGraph(Consumer<String> onNode) {
        super(UiTokens.SPACE_2);
        this.onNode = onNode == null ? address -> {} : onNode;

        getStyleClass().add("es-netmap");
        serverStrip.getStyleClass().add("es-netmap-server");
        header.getStyleClass().add("es-netmap-layer");
        note.getStyleClass().add("es-netmap-layer");
        serverStrip.setWrapText(false);
        header.setWrapText(false);
        columns.setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(serverStrip, header, columns, note);

        // ⚠ Every field advance() touches is assigned above, and render() runs before the
        // subscription: Pulse.animate fires its action once at subscribe time, so a field left null
        // until after this line is a NullPointerException on the first frame. Both breach widgets
        // subscribe on the constructor's last line for exactly this reason.
        render();
        ticker = Pulse.shared().animate(UiTokens.NET_PACKET_MS, this::advance);
    }

    /** Rebuilds the picture. Null-safe: a null map draws the same empty state an empty one does. */
    /** Installs the right-click handler. See the field for why it is not {@code onNode}. */
    public void setOnNodeMenu(
            java.util.function.BiConsumer<String, javafx.scene.input.ContextMenuEvent> handler) {
        this.onNodeMenu = handler == null ? (address, event) -> {} : handler;
    }

    public void setMap(NetMap map) {
        this.current = map == null ? NetMap.empty() : map;
        // The packet restarts with the map rather than carrying over. Its position means "this edge,
        // from here"; keeping a phase across a change of vantage would put the dot on a run that
        // belongs to a different machine.
        this.packet = 0;
        render();
    }

    /**
     * Marks the machine the player has picked — what CONNECT, DOWNLOAD and a breach would act on.
     *
     * <p>Repaints only when the answer changes, so a refresh that touched nothing but the clock —
     * which is most of them — costs nothing here.
     *
     * @param address {@code ""} clears the mark. An address not on the map marks nothing, which is
     *     the correct behaviour for a selection that outlived its sighting by a frame
     */
    public void setSelected(String address) {
        String wanted = address == null ? "" : address;
        if (wanted.equals(selected)) {
            return;
        }
        selected = wanted;
        render();
    }

    /** Advances exactly one animation step. The deterministic seam a harness drives. */
    public void tick() {
        advance();
    }

    /**
     * The whole graph as text, joined line by line.
     *
     * <p>The server strip is deliberately not part of it: it is chrome above the picture, and every
     * geometric assertion is about the picture. {@link NetCanvas#frame} computes the same string
     * without a scene graph, which is what the headless tests use.
     */
    public String frame() {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
    }

    // ── Painting ─────────────────────────────────────────────────────────────────────────────────

    /**
     * One step of the packet.
     *
     * <p>Holds when there is nothing to travel — no vantage, or a vantage with no outbound run.
     * {@link NetCanvas.Painted#packetCells} is that answer, computed by the same pass that drew the
     * run, so the two can never disagree about whether there is a lane to walk.
     *
     * <p>Repaints the gap Labels and nothing else. The picture outside the gaps is identical between
     * two phases of the same map — that is asserted, not assumed, by {@code NetGraphTest}'s
     * "a step only ever changes a plain horizontal run" — so a full rebuild would be both wasteful and
     * destructive. See {@link #gaps}.
     */
    private void advance() {
        if (packetCells <= 0) {
            return;
        }
        packet++;
        NetCanvas.Painted painted = NetCanvas.paint(current, UiTokens.NET_MAX_ROWS, packet, selected);
        if (painted.gaps().size() != gaps.size()) {
            // The picture changed shape under us, which a phase step alone cannot do. Rebuilding is
            // the only safe answer: painting half of one map over half of another is how a renderer
            // ends up showing an edge to a machine that is no longer on the panel.
            render();
            return;
        }
        lines = painted.lines();
        packetCells = painted.packetCells();
        for (int index = 0; index < gaps.size(); index++) {
            gaps.get(index).setText(painted.gaps().get(index));
        }
    }

    private void render() {
        NetCanvas.Painted painted = NetCanvas.paint(current, UiTokens.NET_MAX_ROWS, packet, selected);
        lines = painted.lines();
        packetCells = painted.packetCells();
        serverStrip.setText(painted.serverStrip());
        header.setText(painted.header());

        Map<Integer, NetCanvas.Piece> bySlot = new HashMap<>();
        for (NetCanvas.Piece piece : painted.pieces()) {
            bySlot.put(piece.layer() * UiTokens.NET_MAX_ROWS + piece.row(), piece);
        }

        columns.getChildren().clear();
        gaps.clear();
        for (int layer = 0; layer < painted.layers(); layer++) {
            columns.getChildren().add(column(layer, painted, bySlot));
            if (layer < painted.gaps().size()) {
                Label gap = text(painted.gaps().get(layer), "es-netmap-cell", "es-netmap-edge");
                gaps.add(gap);
                columns.getChildren().add(gap);
            }
        }

        note.setText(noteFor(painted));
        setAccessibleText(describe(painted));
    }

    /** One hop column: its lateral strip, then its stack of cells. */
    private HBox column(int layer, NetCanvas.Painted painted, Map<Integer, NetCanvas.Piece> bySlot) {
        VBox stack = new VBox(0);
        stack.setAlignment(Pos.TOP_LEFT);
        for (int row = 0; row < painted.rowsPerLayer(); row++) {
            NetCanvas.Piece piece = bySlot.get(layer * UiTokens.NET_MAX_ROWS + row);
            stack.getChildren().add(piece == null ? blank() : cell(piece));
        }
        HBox column = new HBox(
                0,
                text(
                        layer < painted.strips().size() ? painted.strips().get(layer) : "",
                        "es-netmap-cell",
                        "es-netmap-lateral"),
                stack);
        column.setAlignment(Pos.TOP_LEFT);
        return column;
    }

    /**
     * An empty grid position.
     *
     * <p>Exactly {@code NET_NODE_LINES} lines of {@code NET_NODE_COLS} spaces, because a slot that is
     * short by a line shears every column to its right — {@code LatticeMap} carries the same note for
     * the same reason. It is blank, not a placeholder: nothing is known to be there.
     */
    private Label blank() {
        String empty = NetCanvas.blank(UiTokens.NET_NODE_COLS);
        StringBuilder out = new StringBuilder();
        for (int line = 0; line < UiTokens.NET_NODE_LINES; line++) {
            if (line > 0) {
                out.append('\n');
            }
            out.append(empty);
        }
        return text(out.toString(), "es-netmap-cell");
    }

    /** A drawn box: a machine the player can act on, or a bridge's far side, which they cannot. */
    private Label cell(NetCanvas.Piece piece) {
        Label label = text(piece.text(), "es-netmap-cell", piece.styleClass());
        String words = piece.stub()
                ? "The network continues on " + piece.peerServerName()
                        + ". Beyond your reach from here — cross the bridge to see it."
                : describe(current.at(piece.address()).orElse(null));

        if (piece.selected()) {
            // The third signal, after the double frame and the pointer at the address. Colour alone
            // would be invisible in greyscale and silent to a screen reader, which is why it is the
            // last of the three rather than the only one — §4.4, weight first and the ramp second.
            label.getStyleClass().add("es-netmap-selected");
            words = "Selected. " + words + " CONNECT, DOWNLOAD and a breach act on this one.";
        }

        Tooltip tip = new Tooltip(words);
        tip.setWrapText(true);
        tip.setMaxWidth(280);
        tip.setShowDelay(Duration.millis(220));
        Tooltip.install(label, tip);
        label.setAccessibleText(words);

        if (piece.stub()) {
            // Not focusable and not clickable, deliberately. A stub is not a target: there is nothing
            // to sweep, breach or connect to, and offering the affordance would promise an action the
            // rules refuse.
            return label;
        }
        label.getStyleClass().add("es-focusable");
        label.setFocusTraversable(true);
        Cursors.shared().clickable(label);
        label.setOnMouseClicked(event -> onNode.accept(piece.address()));
        label.setOnContextMenuRequested(event -> {
            onNodeMenu.accept(piece.address(), event);
            event.consume();
        });
        label.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                onNode.accept(piece.address());
                event.consume();
            }
        });
        return label;
    }

    private static Label text(String value, String... styleClasses) {
        Label label = new Label(value);
        label.setWrapText(false);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    // ── Words ────────────────────────────────────────────────────────────────────────────────────

    /**
     * The line under the graph.
     *
     * <p>Three states, and the first is the one that matters most: an empty map is a <b>teaching</b>
     * surface, not a blank one. A new player looking at nothing has to be told that sweeping is what
     * fills it, and told what that costs, or discovery stays exactly as unusable as the brief says it
     * currently is.
     */
    private static String noteFor(NetCanvas.Painted painted) {
        if (painted.layers() == 0) {
            return "Nothing discovered. sweep is how you find out what is next to you.";
        }
        if (painted.overflow() > 0) {
            return "+" + painted.overflow() + " more in the outermost column than the graph draws. "
                    + "The graph is the legible view; the list is the exhaustive one.";
        }
        return "";
    }

    /** One machine, in words, for the tooltip and for a screen reader. */
    private String describe(Sighting sighting) {
        if (sighting == null) {
            return "An unmapped position.";
        }
        StringBuilder out = new StringBuilder(sighting.address());
        if (!sighting.label().isEmpty()) {
            out.append(", ").append(sighting.label());
        }
        out.append(". ")
                .append(sighting.hopsFromVantage())
                .append(sighting.hopsFromVantage() == 1 ? " hop away" : " hops away");
        if (sighting.kind() == HostKind.UNKNOWN) {
            // Named as an absence rather than as a fact, because that is what it is — and because a
            // player who does not know a sniffer would answer it will never buy one.
            out.append(". Type not established; a Passive Sniffer reads it");
        } else {
            out.append(". ").append(NetCanvas.kindOf(sighting));
        }
        if (sighting.tier() != null) {
            out.append(", tier ").append(sighting.tier().tier());
        }
        out.append(". Signal ").append(sighting.signal().name().toLowerCase(Locale.ROOT));
        if (sighting.vantage()) {
            out.append(". You are operating from here");
        } else if (sighting.foothold()) {
            out.append(". Breached — unlocked, and you may connect here");
        } else if (sighting.patched()) {
            out.append(". Patched — you were inside this once and are locked out now");
        } else {
            out.append(". Locked — never breached");
        }
        if (sighting.honeypotSuspected()) {
            out.append(". Suspected honeypot");
        }
        if (sighting.looted()) {
            out.append(". Already looted");
        }
        if (sighting.documentAvailable()) {
            out.append(". Carries a document you can download");
        }
        if (sighting.hostsDeployedMiner()) {
            out.append(". Hosting a deployed miner");
        }
        if (!sighting.bridgePeerServerName().isEmpty()) {
            out.append(". Bridges to ").append(sighting.bridgePeerServerName());
        }
        return out.append('.').toString();
    }

    /** The whole panel, in words. */
    private String describe(NetCanvas.Painted painted) {
        if (painted.layers() == 0) {
            return "Network map. Nothing discovered yet. Run a sweep to find the machines next to you.";
        }
        return "Network map of " + current.currentServer().name() + ". "
                + current.sightings().size() + " machines discovered across "
                + painted.layers() + (painted.layers() == 1 ? " hop column" : " hop columns")
                + ". Reach ceiling " + current.hopCeiling()
                + (current.hopCeiling() == 1 ? " hop." : " hops.");
    }
}
