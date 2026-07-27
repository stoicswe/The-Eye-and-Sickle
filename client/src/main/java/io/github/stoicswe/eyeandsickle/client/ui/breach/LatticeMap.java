package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.LatticeNode;
import io.github.stoicswe.eyeandsickle.protocol.game.TraversalBoard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Traversal board: a ranked lattice with its edges routed, and the logs the answer is hidden in.
 *
 * <h2>The map is not the puzzle — the manifest is</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3.2 defines Traversal's human-read step as "the true
 * objective node hidden among decoys distinguishable only by cross-referencing recovered logs". So
 * the pretty part of this widget is not where the game is. The game is the block of text under it:
 * one manifest line naming a service and a time, and K candidate hints of which exactly one matches
 * both fields. A player who reads it extracts in one move; a fixed heuristic must guess among K, at
 * two attention and a strike risk each. That gap is what P-3 asks for and D-9 makes measurable —
 * which is why the manifest is <b>selectable, copyable text in the body face</b> and not a styled
 * caption. A player who cannot copy a hostname cannot cross-reference, and the whole class collapses
 * to a coin flip.
 *
 * <h2>Edges are routed, not sampled</h2>
 *
 * Each rank gap is a small character grid with one bus column, and every edge is drawn by OR-ing
 * direction bits into cells — so two edges crossing produce {@code ┼} and a fan out of one node
 * produces {@code ┬} and {@code ┴}, rather than whichever edge the loop reached last erasing the
 * others. That is {@code CoreCage}'s z-buffer lesson in a different shape: draw in index order
 * without a merge rule and the picture silently loses its structure. See
 * {@link AsciiCanvas#junctionAt}.
 *
 * <h2>Weight, again, before colour</h2>
 *
 * {@code ██} here, {@code ▒▒} visited, {@code ░░} seen, {@code ··} dark, {@code ╪╪} objective
 * candidate, {@code ‡‡} known trap. The current node also gets a <b>heavy</b> frame
 * ({@code ┏━━━━┓}), which is the one place in the breach that vocabulary is used — so "where am I"
 * is answered by frame weight before any glyph is read, and stays answered in greyscale.
 *
 * <h2>Motion: one packet, and it stops</h2>
 *
 * D-6 permits motion that is itself a readout. A single {@code ·} steps along the edge run leaving
 * the <em>current</em> node, one character per {@link UiTokens#BREACH_SCAN_MS} tick. It marks which
 * node the player is standing on and which way the lattice runs from there, and it stops outright
 * when that node has no outbound edges — a dead end that keeps pulsing would be lying. ⚠ The packet
 * is only painted over a plain horizontal run: laying it over a junction erases the routing, which
 * the headless harness caught immediately and a screenshot would not have.
 */
public final class LatticeMap extends VBox {

    /** {@code ┌────┐} — six characters, four of them the cell. */
    private static final int NODE_COLS = 6;

    /** Source stub, bus, destination stub, arrowhead. */
    private static final int EDGE_COLS = 5;

    /** Box top, cell, box bottom, hostname. Every cell is exactly this tall, including empty ones. */
    private static final int NODE_LINES = 4;

    /** Which of the four lines carries the cell and the edge endpoints. */
    private static final int CELL_LINE = 1;

    private static final int BUS_COL = 2;

    private final HBox map = new HBox(0);
    private final Label legend = Ui.micro("");
    private final TextArea manifest = new TextArea();

    private TraversalBoard board;
    private java.util.function.Consumer<String> onNode = id -> {};
    private int packet;
    private AutoCloseable ticker;

    public LatticeMap() {
        super(UiTokens.SPACE_3);
        getStyleClass().add("es-lattice");
        map.setAlignment(Pos.TOP_LEFT);

        // Read-only TextArea rather than a Label: JavaFX has no selectable Label, and ManView and
        // TerminalView already established this as the client's way of shipping copyable body text.
        //
        // ⚠ It carries es-lattice-manifest ONLY. The obvious move is to reuse `es-terminal` for the
        // existing body treatment — but that class is declared in none of the five stylesheets (it
        // is used by TerminalView and ManView and styles nothing), so leaning on it would render
        // this panel at Modena defaults while looking correct in the source. es-lattice-manifest is
        // therefore a full rule, not a modifier, and it has to reach `.content` as well as the
        // control: see the integration note.
        manifest.setEditable(false);
        manifest.setWrapText(false);
        manifest.getStyleClass().add("es-lattice-manifest");
        manifest.setAccessibleText("Recovered logs and target manifest");

        getChildren().addAll(map, legend, manifest);
        ticker = Pulse.shared().animate(UiTokens.BREACH_SCAN_MS, this::advance);
    }

    public void setOnNode(java.util.function.Consumer<String> handler) {
        this.onNode = handler == null ? id -> {} : handler;
    }

    /** Rebuilds the map. Null-safe: a null board leaves the panel empty and silent. */
    public void show(TraversalBoard next) {
        this.board = next;
        render();
    }

    /**
     * One step of the packet.
     *
     * <p>Holds when there is nothing to travel — no board, or a current node with no visible exits.
     * Same rule as {@code CoreCage.advance} and {@link BreachViewport#advance}: an instrument that
     * keeps moving with nothing happening is a screensaver.
     */
    private void advance() {
        if (board == null || outboundCount() == 0) {
            return;
        }
        packet = (packet + 1) % EDGE_COLS;
        render();
    }

    /** Advances exactly one step. Test seam. */
    public void tick() {
        advance();
    }

    private int outboundCount() {
        LatticeNode current = currentNode();
        return current == null ? 0 : current.exits().size();
    }

    private LatticeNode currentNode() {
        if (board == null) {
            return null;
        }
        for (LatticeNode node : board.nodes()) {
            if (node.id().equals(board.currentNodeId())) {
                return node;
            }
        }
        return null;
    }

    private void render() {
        map.getChildren().clear();
        if (board == null || board.nodes().isEmpty()) {
            legend.setText("");
            manifest.setText("");
            return;
        }

        int ranks = Math.max(1, board.ranks());
        Map<Integer, List<LatticeNode>> byRank = new HashMap<>();
        int width = 1;
        for (LatticeNode node : board.nodes()) {
            byRank.computeIfAbsent(node.rank(), k -> new ArrayList<>()).add(node);
            width = Math.max(width, node.index() + 1);
        }

        LatticeNode current = currentNode();
        for (int rank = 0; rank < ranks; rank++) {
            map.getChildren().add(rankColumn(rank, byRank.getOrDefault(rank, List.of()), width, current));
            if (rank < ranks - 1) {
                map.getChildren().add(edgeColumn(rank, byRank, width, current));
            }
        }

        legend.setText(Ui.upper(AsciiCanvas.NODE_HERE + " here " + AsciiCanvas.BULLET + " "
                + AsciiCanvas.NODE_VISITED + " visited " + AsciiCanvas.BULLET + " "
                + AsciiCanvas.NODE_SEEN + " seen " + AsciiCanvas.BULLET + " "
                + AsciiCanvas.NODE_DARK + " dark " + AsciiCanvas.BULLET + " "
                + AsciiCanvas.NODE_OBJECTIVE + " objective " + AsciiCanvas.BULLET + " "
                + AsciiCanvas.NODE_TRAP + " trap"));
        buildManifest();
        setAccessibleText(describe());
    }

    private VBox rankColumn(int rank, List<LatticeNode> nodes, int width, LatticeNode current) {
        VBox column = new VBox(0);
        column.setAlignment(Pos.TOP_LEFT);

        // The rank header is one line, and the edge column's grid carries one blank line at the top
        // to match it. That is what keeps the two kinds of column in step without a GridPane and its
        // row-span alignment risks — every column is a plain vertical stack of equal-height lines.
        String head = "R" + rank + (rank == board.objectiveRank() ? " OBJ" : "");
        Label header = plain(pad(head));
        header.getStyleClass().add("es-lattice-rank");
        column.getChildren().add(header);

        for (int index = 0; index < width; index++) {
            LatticeNode node = null;
            for (LatticeNode candidate : nodes) {
                if (candidate.index() == index) {
                    node = candidate;
                }
            }
            column.getChildren().add(node == null ? blankCell() : nodeCell(node, current));
        }
        return column;
    }

    /** An empty grid position. Exactly {@link #NODE_LINES} lines, or every column after it shears. */
    private Label blankCell() {
        return plain((" ".repeat(NODE_COLS) + "\n").repeat(NODE_LINES - 1) + " ".repeat(NODE_COLS));
    }

    private Label nodeCell(LatticeNode node, LatticeNode current) {
        boolean here = current != null && current.id().equals(node.id());
        char tl = here ? AsciiCanvas.HEAVY_TL : AsciiCanvas.LIGHT_TL;
        char tr = here ? AsciiCanvas.HEAVY_TR : AsciiCanvas.LIGHT_TR;
        char bl = here ? AsciiCanvas.HEAVY_BL : AsciiCanvas.LIGHT_BL;
        char br = here ? AsciiCanvas.HEAVY_BR : AsciiCanvas.LIGHT_BR;
        char horizontal = here ? AsciiCanvas.HEAVY_H : AsciiCanvas.LIGHT_H;
        char vertical = here ? AsciiCanvas.HEAVY_V : AsciiCanvas.LIGHT_V;
        String run = String.valueOf(horizontal).repeat(NODE_COLS - 2);

        Label cell = plain(
                tl + run + tr + "\n"
                + vertical + " " + glyphFor(node, here) + " " + vertical + "\n"
                + bl + run + br + "\n"
                + pad(node.visible() ? node.label() : ""));
        cell.getStyleClass().addAll("es-lattice-cell", styleFor(node, here), "es-focusable");

        String words = describe(node, here);
        Tooltip tip = new Tooltip(words);
        tip.setWrapText(true);
        tip.setMaxWidth(280);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        Tooltip.install(cell, tip);
        cell.setAccessibleText(words);

        cell.setFocusTraversable(true);
        Cursors.shared().clickable(cell);
        cell.setOnMouseClicked(e -> onNode.accept(node.id()));
        cell.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                onNode.accept(node.id());
                e.consume();
            }
        });
        return cell;
    }

    /**
     * The routed gap between two ranks.
     *
     * <p>One {@link Label} for the whole gap rather than a node per edge: the routing has to be
     * computed as a grid anyway (see the class comment on merging), and once it is a grid it is one
     * string. Nothing in the gap is interactive, so there is nothing to lose by drawing it as text.
     */
    private Label edgeColumn(int rank, Map<Integer, List<LatticeNode>> byRank, int width,
            LatticeNode current) {
        int lines = 1 + width * NODE_LINES;
        char[][] grid = new char[lines][EDGE_COLS];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }

        Map<String, LatticeNode> ahead = new HashMap<>();
        for (LatticeNode node : byRank.getOrDefault(rank + 1, List.of())) {
            ahead.put(node.id(), node);
        }

        for (LatticeNode node : byRank.getOrDefault(rank, List.of())) {
            for (String exit : node.exits()) {
                LatticeNode target = ahead.get(exit);
                if (target == null) {
                    // A skip edge into a further rank. Drawn from its own gap when we reach it —
                    // rendering it here would run a line through a rank it does not touch.
                    continue;
                }
                route(grid, lineOf(node.index()), lineOf(target.index()));
            }
        }
        packet(grid, current, rank);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(grid[i]);
        }
        Label label = plain(out.toString());
        label.getStyleClass().addAll("es-lattice-cell", "es-lattice-edge");
        return label;
    }

    private static int lineOf(int index) {
        return 1 + index * NODE_LINES + CELL_LINE;
    }

    /** Source stub, bus run, destination stub, arrowhead — merged into whatever is already there. */
    private void route(char[][] grid, int from, int to) {
        merge(grid, from, 0, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        merge(grid, from, 1, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        if (from == to) {
            merge(grid, from, BUS_COL, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        } else {
            int away = to > from ? AsciiCanvas.DOWN : AsciiCanvas.UP;
            int back = to > from ? AsciiCanvas.UP : AsciiCanvas.DOWN;
            merge(grid, from, BUS_COL, AsciiCanvas.LEFT | away);
            int step = to > from ? 1 : -1;
            for (int row = from + step; row != to; row += step) {
                merge(grid, row, BUS_COL, AsciiCanvas.UP | AsciiCanvas.DOWN);
            }
            merge(grid, to, BUS_COL, back | AsciiCanvas.RIGHT);
        }
        merge(grid, to, 3, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        if (to >= 0 && to < grid.length) {
            grid[to][4] = AsciiCanvas.ARROW_RIGHT;
        }
    }

    private void merge(char[][] grid, int row, int col, int bits) {
        if (row < 0 || row >= grid.length || col < 0 || col >= EDGE_COLS) {
            return;
        }
        grid[row][col] = AsciiCanvas.junction(AsciiCanvas.bitsOf(grid[row][col]) | bits);
    }

    /**
     * ⚠ The packet only ever lands on a plain horizontal run, and it steps between those runs.
     *
     * <p>Two things the headless harness caught and a screenshot would not have. First, painting the
     * dot at an unconditional column <b>erases whatever routing was in that cell</b> — a {@code ┬}
     * became the dot, silently deleting a branch from the map the player is deducing on. Second,
     * once the junction columns are skipped the dot is absent for three frames of a five-frame
     * cycle, so it reads as a blink rather than as travel. Walking only the horizontal cells fixes
     * both: the dot marches the length of the outbound stub and never covers a junction. This is
     * decoration sitting on top of information and it does not get to win.
     */
    private void packet(char[][] grid, LatticeNode current, int rank) {
        if (current == null || current.rank() != rank || current.exits().isEmpty()) {
            return;
        }
        int row = lineOf(current.index());
        if (row < 0 || row >= grid.length) {
            return;
        }
        char run = AsciiCanvas.junction(AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        List<Integer> lane = new ArrayList<>();
        for (int col = 0; col < EDGE_COLS; col++) {
            if (grid[row][col] == run) {
                lane.add(col);
            }
        }
        if (lane.isEmpty()) {
            return;
        }
        grid[row][lane.get(packet % lane.size())] = AsciiCanvas.BULLET;
    }

    /**
     * The manifest and the recovered hints.
     *
     * <p>Hints appear only for nodes whose {@code hint} the snapshot actually carries — architect's
     * decision D-2 means an unread hint is the empty string and is not in the record at all, so
     * there is nothing here for the client to leak even if it tried.
     */
    private void buildManifest() {
        StringBuilder out = new StringBuilder();
        for (String line : board.manifest()) {
            out.append(line).append('\n');
        }
        boolean any = false;
        for (LatticeNode node : board.nodes()) {
            if (node.hint().isBlank()) {
                continue;
            }
            if (!any) {
                out.append('\n');
                any = true;
            }
            out.append(pad(node.label())).append("  ").append(node.hint()).append('\n');
        }
        if (!any) {
            out.append("\nNo logs recovered. LISTEN at a node to read one.\n");
        }
        manifest.setText(out.toString());
        int lines = (int) out.chars().filter(c -> c == '\n').count() + 1;
        manifest.setPrefRowCount(Math.max(3, Math.min(10, lines)));
    }

    private static String glyphFor(LatticeNode node, boolean here) {
        if (here) {
            return AsciiCanvas.NODE_HERE;
        }
        // Trap outranks objective: a candidate the player has established is trapped is a thing to
        // avoid before it is a thing to try, and 09 §1 makes the canary the expensive mistake.
        if (node.trapKnown()) {
            return AsciiCanvas.NODE_TRAP;
        }
        if (node.objectiveCandidate() && node.visible()) {
            return AsciiCanvas.NODE_OBJECTIVE;
        }
        if (node.visited()) {
            return AsciiCanvas.NODE_VISITED;
        }
        return node.visible() ? AsciiCanvas.NODE_SEEN : AsciiCanvas.NODE_DARK;
    }

    private static String styleFor(LatticeNode node, boolean here) {
        if (here) {
            return "es-lattice-here";
        }
        if (node.trapKnown()) {
            return "es-lattice-trap";
        }
        if (node.objectiveCandidate() && node.visible()) {
            return "es-lattice-objective";
        }
        if (node.visited()) {
            return "es-lattice-visited";
        }
        return node.visible() ? "es-lattice-seen" : "es-lattice-dark";
    }

    private static String describe(LatticeNode node, boolean here) {
        StringBuilder out = new StringBuilder();
        out.append(node.visible() && !node.label().isBlank() ? node.label() : "Unmapped node")
                .append(", rank ").append(node.rank());
        if (here) {
            out.append(". You are here");
        }
        if (node.objectiveCandidate()) {
            out.append(". Objective candidate");
        }
        if (node.trapKnown()) {
            out.append(". Trapped — extracting here fires a canary");
        }
        if (node.stepCost() > 0) {
            out.append(". Costs ").append(node.stepCost()).append(" extra attention to enter");
        }
        if (!node.hint().isBlank()) {
            out.append(". Log: ").append(node.hint());
        }
        return out.append('.').toString();
    }

    private String describe() {
        LatticeNode current = currentNode();
        return "Traversal lattice, " + board.ranks() + " ranks, objective on rank "
                + board.objectiveRank() + ". Currently at "
                + (current == null ? "an unknown node" : current.label())
                + ". " + String.join(". ", board.manifest());
    }

    private static String pad(String text) {
        String s = text == null ? "" : text;
        if (s.length() > NODE_COLS) {
            s = s.substring(0, NODE_COLS);
        }
        return s + " ".repeat(NODE_COLS - s.length());
    }

    private static Label plain(String text) {
        Label label = new Label(text);
        label.setWrapText(false);
        return label;
    }

    /** The map as text, columns joined line by line. Test seam for the edge router. */
    public String frame() {
        List<String[]> columns = new ArrayList<>();
        int height = 0;
        for (javafx.scene.Node child : map.getChildren()) {
            String[] lines = textOf(child).split("\n", -1);
            columns.add(lines);
            height = Math.max(height, lines.length);
        }
        StringBuilder out = new StringBuilder();
        for (int line = 0; line < height; line++) {
            for (String[] column : columns) {
                out.append(line < column.length ? column[line] : "");
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String textOf(javafx.scene.Node node) {
        if (node instanceof Label label) {
            return label.getText();
        }
        if (node instanceof VBox column) {
            StringBuilder out = new StringBuilder();
            for (javafx.scene.Node child : column.getChildren()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(textOf(child));
            }
            return out.toString();
        }
        return "";
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
}
