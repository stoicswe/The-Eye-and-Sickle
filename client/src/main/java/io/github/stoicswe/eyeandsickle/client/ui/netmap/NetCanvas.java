package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.breach.AsciiCanvas;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The network map as a character grid: the whole picture, computed with no scene graph.
 *
 * <h2>Why this is a separate class from {@link NetGraph}</h2>
 *
 * Every geometric claim the map makes has to be testable without a display. The client bundles no
 * TestFX and no Monocle — {@code UiContractTest} says so in as many words, "a contract test that only
 * runs on a machine with a display is a contract test that does not run in CI".
 *
 * <p>⚠ <b>Measured, on this project, JavaFX 26.0.2 / JDK 26:</b> {@code new VBox()} succeeds with no
 * toolkit, and {@code new Label("x")} does <em>not</em> — it throws {@code ExceptionInInitializerError}
 * caused by {@code IllegalStateException: Toolkit not initialized}, from {@code Control}'s static
 * initialiser, before any constructor body runs. {@code Tooltip} fails the same way. So a renderer that
 * builds its picture out of {@code Label}s cannot be asserted on at all in this build, and splitting the
 * grid out is not tidiness — it is the difference between Lane C having tests and Lane C having none.
 * The routing, the merging, the occupancy mask and the packet therefore live here, in a class with no
 * JavaFX supertype, and {@link NetGraph} is the thin part that turns this grid into focusable
 * {@code Label}s. Both read the same grid, so the picture a test asserts on is the picture the player
 * sees, character for character.
 *
 * <h2>Merging is mandatory, and the occupancy mask is the second half of it</h2>
 *
 * Edges are drawn by OR-ing direction bits into cells through {@link AsciiCanvas#junction}, so two
 * edges crossing produce {@code ┼} and a fan-out produces {@code ┬}, rather than whichever edge the
 * loop reached last erasing the others. That is {@code CoreCage}'s z-buffer lesson in a different
 * shape.
 *
 * <p>⚠ {@link AsciiCanvas#bitsOf} returns {@code 0} for anything outside the sixteen-entry light
 * table — so routing across a cell holding {@code █}, {@code ·}, {@code ╪} or a label character would
 * silently replace it with a bare stub. {@code LatticeMap} gets away without a mask only because its
 * lanes can never cross a node cell; ours can, because a bridge stub is placed into a column the
 * layout did not allocate. Hence {@link #occupied}: every cell written by a header, a node cell, a
 * stub or a destination arrow is closed to routing, and {@link #merge} refuses rather than overwrites.
 *
 * <h2>Two edge classes, told apart by shape</h2>
 *
 * A <b>forward</b> edge crosses into the next hop layer and is drawn in the seven-column gap with
 * sharp junctions and a {@code →} at the destination. A <b>lateral</b> edge stays inside a layer and
 * is drawn in the two-column strip on the left of the column, with <em>rounded</em> corners. The
 * distinction is carried by the glyph, not by the ink: the map has to survive greyscale, and the two
 * kinds of edge mean genuinely different things — one is a hop the ceiling counts, the other is not.
 *
 * <p>Three lanes rather than one bus column. {@code LatticeMap}'s single {@code BUS_COL} saturates
 * into a solid vertical run the moment a node has more than four children, and a layer here can hold
 * ten.
 */
public final class NetCanvas {

    private NetCanvas() {}

    /** Column budget for one layer: the lateral strip plus the node cell. */
    private static final int LAYER_COLS = UiTokens.NET_LATERAL_COLS + UiTokens.NET_NODE_COLS;

    /** Layer start to layer start. */
    private static final int PITCH = LAYER_COLS + UiTokens.NET_GAP_COLS;

    /** The kind field inside a node cell. Every {@code HostKind} name fits; {@code UNKNOWN} does not print. */
    private static final int KIND_COLS = 8;

    /** What an un-typed machine's kind field reads. Eight of them, so the field never changes width. */
    private static final String UNTYPED = "-".repeat(KIND_COLS);

    /**
     * One drawn box.
     *
     * @param address the machine's address, or {@code ""} for a bridge stub, which has none the
     *     player is allowed to see
     * @param peerServerName set only on a stub: the name of the server on the far side, and the one
     *     fact a bridge is licensed to publish
     * @param text exactly {@code NET_NODE_LINES} lines of {@code NET_NODE_COLS} characters
     */
    public record Piece(
            String address,
            String peerServerName,
            int layer,
            int row,
            String styleClass,
            String text,
            boolean stub,
            boolean selected) {}

    /**
     * The finished picture.
     *
     * @param lines the whole grid, one string per line, every line the same width
     * @param pieces node cells and stubs, in column-then-row order
     * @param strips one per layer: the lateral strip, body lines only (the header line is excluded,
     *     because it is drawn once across the whole map rather than per column)
     * @param gaps one per inter-layer gap, body lines only
     * @param header the layer-header line, full width
     * @param serverStrip the always-present "which network am I on" line, drawn above the graph
     * @param packetCells how many cells the travelling packet has to walk; zero means hold still
     */
    public record Painted(
            List<String> lines,
            List<Piece> pieces,
            List<String> strips,
            List<String> gaps,
            String header,
            String serverStrip,
            int layers,
            int rowsPerLayer,
            int overflow,
            int packetCells) {}

    // ── Painting ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Draws the whole map.
     *
     * @param map the player's visible network; {@code null} and empty both draw nothing at all
     * @param maxRows the tallest column, {@code UiTokens.NET_MAX_ROWS}
     * @param packetPhase the animation step; only ever moves the one {@code ·}
     */
    public static Painted paint(NetMap map, int maxRows, int packetPhase) {
        return paint(map, maxRows, packetPhase, "");
    }

    /**
     * Draws the whole map, marking one machine as the one the player has picked.
     *
     * @param selectedAddress the machine CONNECT, DOWNLOAD and the breach would act on; {@code ""}
     *     for none. An address that is not on the map marks nothing and is not an error — a
     *     selection outlives the sighting that produced it by a repaint or two, and a renderer that
     *     threw on that would crash on the frame after a machine went out of view
     */
    public static Painted paint(NetMap map, int maxRows, int packetPhase, String selectedAddress) {
        NetMap safe = map == null ? NetMap.empty() : map;
        String strip = serverStrip(safe);
        NetLayout.Result layout = NetLayout.of(safe, maxRows);
        if (layout.layers() == 0) {
            return new Painted(List.of(), List.of(), List.of(), List.of(), "", strip, 0, 0, 0, 0);
        }
        String selected = selectedAddress == null ? "" : selectedAddress;
        return new Canvas(safe, layout, Math.max(1, maxRows), packetPhase, strip, selected).paint();
    }

    /** The grid as text, one line per row. The seam every geometric test reads. */
    public static String frame(NetMap map, int maxRows, int packetPhase) {
        return frame(map, maxRows, packetPhase, "");
    }

    /** The grid as text with one machine marked — the seam the selection tests read. */
    public static String frame(NetMap map, int maxRows, int packetPhase, String selectedAddress) {
        StringBuilder out = new StringBuilder();
        for (String line : paint(map, maxRows, packetPhase, selectedAddress).lines()) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The server strip.
     *
     * <p>The brief requires the graph to name the server the player is connected to <em>always</em>,
     * and this is the redundant half of that (the layer headers are the other). It is chrome inside
     * the panel, so it has no z-order to lose and no tab to hide behind — the same structural
     * argument that put the compute readout in the top status strip.
     *
     * <p>{@code HOSTS SEEN} counts sightings, which is what the player has discovered — never what
     * exists. The only aggregate this feature is permitted to show about undetected machines is a
     * sweep's own {@code inRange}, and that belongs to the sweep report, not to the map.
     */
    private static String serverStrip(NetMap map) {
        String name = map.currentServer().name().isEmpty()
                ? (map.currentServer().serverId().isEmpty() ? UNTYPED : map.currentServer().serverId())
                : map.currentServer().name();
        int ceiling = Math.max(1, map.hopCeiling());
        return "SERVER"
                + blank(2)
                + padRight(name, 18)
                + "DEPTH " + map.currentServer().depthFromHome() + " FROM HOME"
                + blank(6)
                + "HOSTS SEEN " + map.sightings().size()
                + blank(6)
                + "CEILING " + ceiling + (ceiling == 1 ? " HOP" : " HOPS");
    }

    // ── The grid itself ──────────────────────────────────────────────────────────────────────────

    /** One paint. Short-lived and single-threaded; every field is scratch space for {@link #paint}. */
    private static final class Canvas {

        private final NetMap map;
        private final NetLayout.Result layout;
        private final int maxRows;
        private final int packetPhase;
        private final String serverStrip;
        private final String selected;

        private final int layers;
        private final int rows;
        private final int lines;
        private final int cols;

        private final char[][] grid;
        private final int[][] bits;
        private final boolean[][] occupied;

        private final List<Piece> pieces = new ArrayList<>();
        private final Map<String, int[]> slotOf = new HashMap<>();
        private final List<Stub> stubs = new ArrayList<>();
        private int packetCells;

        /** A bridge's far side: where it is drawn, and the only fact it carries. */
        private record Stub(String bridgeAddress, String peerServerName, int layer, int row) {}

        private Canvas(
                NetMap map,
                NetLayout.Result layout,
                int maxRows,
                int packetPhase,
                String serverStrip,
                String selected) {
            this.map = map;
            this.layout = layout;
            this.maxRows = maxRows;
            this.packetPhase = packetPhase;
            this.serverStrip = serverStrip;
            this.selected = selected;

            for (NetLayout.Placed placed : layout.placed()) {
                slotOf.put(placed.sighting().address(), new int[] {placed.layer(), placed.row()});
            }
            planStubs();

            int widest = layout.layers();
            int tallest = layout.rowsPerLayer();
            for (Stub stub : stubs) {
                widest = Math.max(widest, stub.layer() + 1);
                tallest = Math.max(tallest, stub.row() + 1);
            }
            this.layers = widest;
            this.rows = Math.max(1, tallest);
            this.lines = 1 + rows * UiTokens.NET_NODE_LINES;
            this.cols = layers * LAYER_COLS + Math.max(0, layers - 1) * UiTokens.NET_GAP_COLS;

            this.grid = new char[lines][cols];
            this.bits = new int[lines][cols];
            this.occupied = new boolean[lines][cols];
            for (char[] row : grid) {
                Arrays.fill(row, ' ');
            }
        }

        /**
         * Where each bridge's far side hangs.
         *
         * <p>One column further out than the bridge itself, in the first row slot no machine and no
         * other stub is using — which is what makes it read as "the network continues that way"
         * rather than as a machine the player has mapped. A bridge in the outermost layer therefore
         * grows the picture by exactly one column, and never by more, because a stub is only ever
         * placed one layer beyond a machine that <em>is</em> placed.
         *
         * <p>If the target column is already full to {@code maxRows}, the stub is dropped. The peer
         * server is still named — in the bridge cell's own accessible text and tooltip, and in the
         * list's NOTE column — so nothing the player is entitled to know is lost with it.
         */
        private void planStubs() {
            boolean[][] taken = new boolean[layout.layers() + 2][maxRows];
            for (NetLayout.Placed placed : layout.placed()) {
                if (placed.row() < maxRows) {
                    taken[placed.layer()][placed.row()] = true;
                }
            }
            for (NetLayout.Placed placed : layout.placed()) {
                Sighting sighting = placed.sighting();
                if (sighting.kind() != HostKind.BRIDGE || sighting.bridgePeerServerName().isEmpty()) {
                    continue;
                }
                int target = placed.layer() + 1;
                for (int row = 0; row < maxRows; row++) {
                    if (!taken[target][row]) {
                        taken[target][row] = true;
                        stubs.add(new Stub(sighting.address(), sighting.bridgePeerServerName(), target, row));
                        break;
                    }
                }
            }
        }

        private Painted paint() {
            drawHeader();
            drawCells();
            drawStubs();
            drawEdges();
            drawPacket();

            List<String> out = new ArrayList<>(lines);
            for (char[] row : grid) {
                out.add(new String(row));
            }
            return new Painted(
                    List.copyOf(out),
                    List.copyOf(pieces),
                    slices(0, UiTokens.NET_LATERAL_COLS, layers),
                    slices(LAYER_COLS, UiTokens.NET_GAP_COLS, Math.max(0, layers - 1)),
                    new String(grid[0]),
                    serverStrip,
                    layers,
                    rows,
                    layout.overflowInLastVisibleLayer(),
                    packetCells);
        }

        // ── The pieces ───────────────────────────────────────────────────────────────────────────

        private void drawHeader() {
            for (int layer = 0; layer < layout.layerHeaders().size() && layer < layers; layer++) {
                // A header may run into the gap beside its column — it is one line of prose above a
                // picture, and clipping "south-exchange" to sixteen columns would lose the answer to
                // "which network am I looking at" that §4.6 requires the map to keep on screen. The
                // last column has no gap to borrow, which is why fit() exists.
                int span = layer == layers - 1 ? LAYER_COLS : LAYER_COLS + UiTokens.NET_GAP_COLS - 1;
                write(0, layer * PITCH, clip(fit(layout.layerHeaders().get(layer), span), span), true);
            }
        }

        private void drawCells() {
            for (NetLayout.Placed placed : layout.placed()) {
                Sighting sighting = placed.sighting();
                boolean vantage = isVantage(sighting);
                boolean picked = !selected.isEmpty() && selected.equals(sighting.address());
                String block = cellText(sighting, vantage, picked);
                blit(placed.layer(), placed.row(), block);
                pieces.add(new Piece(
                        sighting.address(),
                        "",
                        placed.layer(),
                        placed.row(),
                        styleFor(sighting, vantage),
                        block,
                        false,
                        picked));
            }
        }

        private void drawStubs() {
            for (Stub stub : stubs) {
                String block = stubText(stub.peerServerName());
                blit(stub.layer(), stub.row(), block);
                pieces.add(new Piece(
                        "",
                        stub.peerServerName(),
                        stub.layer(),
                        stub.row(),
                        "es-netmap-dark",
                        block,
                        true,
                        // A stub is never the selection. It has no address the player has been sold,
                        // so there is nothing for CONNECT or a breach to act on and nothing to mark.
                        false));
            }
        }

        /** Writes a four-line block into a column slot and closes those cells to routing. */
        private void blit(int layer, int row, String block) {
            int top = 1 + row * UiTokens.NET_NODE_LINES;
            int left = layer * PITCH + UiTokens.NET_LATERAL_COLS;
            String[] parts = block.split("\n", -1);
            for (int i = 0; i < parts.length && i < UiTokens.NET_NODE_LINES; i++) {
                write(top + i, left, parts[i], true);
            }
            // The whole slot is closed, not only the cells that carry ink: a blank line inside a cell
            // is still inside the cell, and an edge routed through it would appear to pass behind a
            // machine it does not touch.
            for (int line = top; line < top + UiTokens.NET_NODE_LINES; line++) {
                for (int col = left; col < left + UiTokens.NET_NODE_COLS; col++) {
                    close(line, col);
                }
            }
        }

        // ── Edges ────────────────────────────────────────────────────────────────────────────────

        private void drawEdges() {
            int[] lanes = new int[Math.max(1, layers)];
            for (NetLayout.Routed routed : layout.routed()) {
                int[] from = slotOf.get(routed.fromAddress());
                int[] to = slotOf.get(routed.toAddress());
                if (from == null || to == null) {
                    continue;
                }
                if (routed.lateral()) {
                    lateral(from[0], Math.min(from[1], to[1]), Math.max(from[1], to[1]));
                } else {
                    forward(from[0], from[1], to[1], lanes[from[0]]++);
                }
            }
            // Stub edges last, so a real machine always gets the low lanes: the lane a reader follows
            // first should lead somewhere they can act on.
            for (Stub stub : stubs) {
                int[] from = slotOf.get(stub.bridgeAddress());
                if (from != null) {
                    forward(from[0], from[1], stub.row(), lanes[from[0]]++);
                }
            }
        }

        /**
         * A forward edge, drawn in the gap to the right of {@code layer}.
         *
         * <p>Source stub, a run to this edge's turn column, the turn, a run to the destination and an
         * arrowhead. The turn column is {@code 1 + lane * 2}, so the three lanes never share a
         * vertical and a fan-out stays legible where a single bus would go solid.
         */
        private void forward(int layer, int fromRow, int toRow, int index) {
            if (layer + 1 >= layers) {
                return;
            }
            int gap = layer * PITCH + LAYER_COLS;
            int last = UiTokens.NET_GAP_COLS - 1;
            int source = cellLine(fromRow);
            int destination = cellLine(toRow);

            if (source == destination) {
                for (int col = 0; col < last; col++) {
                    merge(source, gap + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
            } else {
                int turn = 1 + (index % UiTokens.NET_LANES) * 2;
                int away = destination > source ? AsciiCanvas.DOWN : AsciiCanvas.UP;
                int back = destination > source ? AsciiCanvas.UP : AsciiCanvas.DOWN;
                for (int col = 0; col < turn; col++) {
                    merge(source, gap + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
                merge(source, gap + turn, AsciiCanvas.LEFT | away);
                int step = destination > source ? 1 : -1;
                for (int line = source + step; line != destination; line += step) {
                    merge(line, gap + turn, AsciiCanvas.UP | AsciiCanvas.DOWN);
                }
                merge(destination, gap + turn, back | AsciiCanvas.RIGHT);
                for (int col = turn + 1; col < last; col++) {
                    merge(destination, gap + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
            }
            // ⚠ Written, never merged. The arrowhead is not in the junction table, so OR-ing bits
            // into it would hand back a stub and the edge would lose the one mark that says which way
            // it runs. It closes its cell afterwards so nothing else can take it back — today no
            // merge reaches this column anyway (every run stops at `last`), but the invariant the
            // reader needs is "the arrowhead survives", and that must not depend on a loop bound in
            // another method staying exclusive.
            put(destination, gap + last, AsciiCanvas.ARROW_RIGHT);
            close(destination, gap + last);
        }

        /**
         * A lateral edge, drawn in the two-column strip on the left of its own layer.
         *
         * <p>Rounded corners, so a reader can tell a same-layer edge from a hop <em>by shape</em>. The
         * arcs still merge: two lateral edges that overlap produce {@code ├}, which is the honest
         * reading — a branch — rather than one silently erasing the other.
         */
        private void lateral(int layer, int upperRow, int lowerRow) {
            if (upperRow == lowerRow) {
                return;
            }
            int strip = layer * PITCH;
            int upper = cellLine(upperRow);
            int lower = cellLine(lowerRow);
            arc(upper, strip, AsciiCanvas.DOWN | AsciiCanvas.RIGHT);
            arc(upper, strip + 1, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
            for (int line = upper + 1; line < lower; line++) {
                arc(line, strip, AsciiCanvas.UP | AsciiCanvas.DOWN);
            }
            arc(lower, strip, AsciiCanvas.UP | AsciiCanvas.RIGHT);
            arc(lower, strip + 1, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
        }

        // ── Motion ───────────────────────────────────────────────────────────────────────────────

        /**
         * One {@code ·} stepping along the vantage's outbound run.
         *
         * <p>⚠ It is painted only onto a cell that currently reads as a plain {@code ─}. {@code
         * LatticeMap} records what an unconditional write costs: the dot landed on a {@code ┬} and
         * silently deleted a branch from the map the player was deducing on. Decoration sits on top of
         * information here and it does not get to win.
         *
         * <p>{@link Painted#packetCells} is how {@link NetGraph} knows whether there is anything to
         * animate. An idle instrument holds still — a map that keeps pulsing with no vantage and no
         * exits is a screensaver.
         */
        private void drawPacket() {
            int[] slot = vantageSlot();
            if (slot == null || slot[0] + 1 >= layers) {
                return;
            }
            int line = cellLine(slot[1]);
            if (line < 0 || line >= lines) {
                return;
            }
            char run = AsciiCanvas.junction(AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
            List<Integer> lane = new ArrayList<>();
            int gap = slot[0] * PITCH + LAYER_COLS;
            for (int col = gap; col < gap + UiTokens.NET_GAP_COLS && col < cols; col++) {
                if (grid[line][col] == run) {
                    lane.add(col);
                }
            }
            packetCells = lane.size();
            if (packetCells == 0) {
                return;
            }
            grid[line][lane.get(Math.floorMod(packetPhase, packetCells))] = NetGlyphs.PACKET;
        }

        private int[] vantageSlot() {
            for (NetLayout.Placed placed : layout.placed()) {
                if (isVantage(placed.sighting())) {
                    return new int[] {placed.layer(), placed.row()};
                }
            }
            return null;
        }

        private boolean isVantage(Sighting sighting) {
            return sighting.vantage()
                    || (!map.vantageAddress().isEmpty() && map.vantageAddress().equals(sighting.address()));
        }

        // ── Grid primitives ──────────────────────────────────────────────────────────────────────

        private static int cellLine(int row) {
            return 1 + row * UiTokens.NET_NODE_LINES + 1;
        }

        private void write(int line, int col, String text, boolean close) {
            for (int i = 0; i < text.length(); i++) {
                put(line, col + i, text.charAt(i));
                if (close) {
                    close(line, col + i);
                }
            }
        }

        private void put(int line, int col, char glyph) {
            if (line < 0 || line >= lines || col < 0 || col >= cols) {
                return;
            }
            grid[line][col] = glyph;
        }

        private void close(int line, int col) {
            if (line >= 0 && line < lines && col >= 0 && col < cols) {
                occupied[line][col] = true;
            }
        }

        /** OR-s direction bits into a cell, refusing anything a piece has already claimed. */
        private void merge(int line, int col, int add) {
            if (line < 0 || line >= lines || col < 0 || col >= cols || occupied[line][col]) {
                return;
            }
            bits[line][col] |= add;
            grid[line][col] = AsciiCanvas.junction(bits[line][col]);
        }

        /** The same merge, but the two-direction cases come out as arcs. See {@link #lateral}. */
        private void arc(int line, int col, int add) {
            if (line < 0 || line >= lines || col < 0 || col >= cols || occupied[line][col]) {
                return;
            }
            bits[line][col] |= add;
            grid[line][col] = arcOf(bits[line][col]);
        }

        private static char arcOf(int corner) {
            if (corner == (AsciiCanvas.DOWN | AsciiCanvas.RIGHT)) {
                return NetGlyphs.ROUND_TL;
            }
            if (corner == (AsciiCanvas.UP | AsciiCanvas.RIGHT)) {
                return NetGlyphs.ROUND_BL;
            }
            if (corner == (AsciiCanvas.DOWN | AsciiCanvas.LEFT)) {
                return NetGlyphs.ROUND_TR;
            }
            if (corner == (AsciiCanvas.UP | AsciiCanvas.LEFT)) {
                return NetGlyphs.ROUND_BR;
            }
            // Three or four directions, or a straight run: the light table is right for all of them,
            // and there is no rounded form of a tee to reach for anyway.
            return AsciiCanvas.junction(corner);
        }

        /** A vertical strip of the grid, body lines only — the text one {@code Label} carries. */
        private List<String> slices(int offset, int width, int count) {
            List<String> out = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                StringBuilder text = new StringBuilder();
                for (int line = 1; line < lines; line++) {
                    if (line > 1) {
                        text.append('\n');
                    }
                    int from = index * PITCH + offset;
                    text.append(new String(grid[line], from, Math.min(width, cols - from)));
                }
                out.add(text.toString());
            }
            return out;
        }
    }

    // ── Cell contents ────────────────────────────────────────────────────────────────────────────

    /**
     * A node cell: frame, state glyph, kind, address.
     *
     * <pre>
     * ┌────────────┐        vantage:  ┏━━━━━━━━━━━━┓
     * │ ██ TERMINAL│                  ┃ ██ TERMINAL┃
     * └────────────┘                  ┗━━━━━━━━━━━━┛
     *  10.0.0.7                        10.0.0.1
     * </pre>
     *
     * <p><b>The vantage is the only heavy frame on the map.</b> "Where am I operating from" is
     * answered by frame weight before a single glyph is read, and stays answered in greyscale — which
     * is the acceptance test for the whole panel, because the palette reserves its one accent for
     * live/earning data and a network node is not earning.
     *
     * <h2>The selected machine gets a double frame and a pointer at its address</h2>
     *
     * <pre>
     * selected: ╔════════════╗        selected AND the vantage: ┏━━━━━━━━━━━━┓
     *           ║ ░░--------·║                                  ┃ ██ TERMINAL┃
     *           ╚════════════╝                                  ┗━━━━━━━━━━━━┛
     *          ▌10.0.0.7                                       ▌10.0.0.1
     * </pre>
     *
     * <p>Three frame weights, and their precedence is not arbitrary. <b>Vantage outranks
     * selection</b>: where the player is standing is a fact about the whole map — every hop count on
     * it is measured from there — while a selection is a transient intention, and a mark that could
     * hide the frame of reference would cost more than it bought. So the bar beside the address
     * carries selection <em>unconditionally</em> and the double frame carries it only where there is
     * a frame weight going spare. Selecting the vantage is still unmistakable; it just says so with
     * the bar rather than with the box.
     *
     * <p>Both marks are geometric and neither changes a width. That is the requirement rather than a
     * preference: the bar replaces the blank the address line already began with, and the frame
     * swaps characters one for one, so a selection cannot shear the column it is in. A selection
     * carried by colour alone would also be invisible in greyscale and silent to a screen reader,
     * which §4.4's "weight first, the grey ramp second" exists to prevent.
     */
    static String cellText(Sighting sighting, boolean vantage, boolean selected) {
        // Vantage first — see the class note on precedence. `selected && !vantage` rather than a
        // three-way pick, so that adding a fourth weight later cannot silently reorder these two.
        boolean doubled = selected && !vantage;
        char tl = vantage ? AsciiCanvas.HEAVY_TL : doubled ? AsciiCanvas.BOX_TL : AsciiCanvas.LIGHT_TL;
        char tr = vantage ? AsciiCanvas.HEAVY_TR : doubled ? AsciiCanvas.BOX_TR : AsciiCanvas.LIGHT_TR;
        char bl = vantage ? AsciiCanvas.HEAVY_BL : doubled ? AsciiCanvas.BOX_BL : AsciiCanvas.LIGHT_BL;
        char br = vantage ? AsciiCanvas.HEAVY_BR : doubled ? AsciiCanvas.BOX_BR : AsciiCanvas.LIGHT_BR;
        char horizontal = vantage ? AsciiCanvas.HEAVY_H : doubled ? AsciiCanvas.BOX_H : AsciiCanvas.LIGHT_H;
        char vertical = vantage ? AsciiCanvas.HEAVY_V : doubled ? AsciiCanvas.BOX_V : AsciiCanvas.LIGHT_V;

        String rule = String.valueOf(horizontal).repeat(UiTokens.NET_NODE_COLS - 2);
        String interior = blank(1) + glyphFor(sighting, vantage) + blank(1) + padRight(kindOf(sighting), KIND_COLS);
        // ⚠ The bar takes the address line's existing leading blank rather than being prepended.
        // Prepending would push the line one column wide and shear everything to its right — the
        // failure NET_NODE_COLS exists to make impossible, arriving through the one line nobody
        // thinks of as part of the box.
        //
        // ⚠ A BAR, NOT AN ARROWHEAD. `→` was the obvious choice and is already this map's glyph for
        // the head of an edge entering a cell (see route()), so a selection drawn with one would be
        // indistinguishable from the nine arrowheads a two-hop map already has. A gutter bar is the
        // standard idiom for "this row", is not used anywhere else on this surface, and reads at a
        // glance without being confusable with anything the routing draws.
        String lead = selected ? String.valueOf(AsciiCanvas.BAR_HALF) : blank(1);
        return tl + rule + tr
                + "\n" + vertical + clip(interior, UiTokens.NET_NODE_COLS - 2) + vertical
                + "\n" + bl + rule + br
                + "\n" + padRight(lead + sighting.address(), UiTokens.NET_NODE_COLS);
    }

    /**
     * A bridge stub: the far side, unframed.
     *
     * <p>Deliberately without a box. A frame is this map's mark for "a machine you have found", and
     * the far side of a bridge is not one — it is a direction with a name on it. The glyph column
     * lines up with a real cell's so the two read as the same kind of object seen at two different
     * distances.
     */
    static String stubText(String peerServerName) {
        // The name goes on the fourth line — where a real cell puts its address — and takes the whole
        // fourteen columns rather than sharing the glyph line. A server name is the widest string on
        // this map ("south-exchange" is exactly fourteen), and clipping the one fact a bridge exists to
        // publish down to eight characters would make the stub decorative.
        return blank(UiTokens.NET_NODE_COLS)
                + "\n" + blank(2) + NetGlyphs.NODE_DARK + blank(UiTokens.NET_NODE_COLS - 4)
                + "\n" + blank(UiTokens.NET_NODE_COLS)
                + "\n" + padRight(peerServerName, UiTokens.NET_NODE_COLS);
    }

    /**
     * The two-cell state marker.
     *
     * <p>Order is by what changes the player's next move, not by the order the states are listed in.
     * The vantage first, because it is the frame of reference for everything else. A suspected trap
     * next — {@code LatticeMap}'s rule, and the right one: a thing to avoid outranks a thing to try,
     * and {@code docs/design/09-defense-and-hardening.md} §1 makes the canary the expensive mistake.
     * Then a foothold, because it is the state that changes what the player can <em>do</em> — {@code
     * connect} and {@code download} both need one — and a bridge that the player is already inside
     * still announces itself unmistakably through the {@code ··} stub hanging off it, its tooltip and
     * the list's NOTE column. Then bridge, then identified, then contact.
     */
    static String glyphFor(Sighting sighting, boolean vantage) {
        if (vantage) {
            return NetGlyphs.NODE_VANTAGE;
        }
        if (sighting.honeypotSuspected()) {
            return NetGlyphs.NODE_TRAP;
        }
        if (sighting.foothold()) {
            return NetGlyphs.NODE_FOOTHOLD;
        }
        if (sighting.kind() == HostKind.BRIDGE) {
            return NetGlyphs.NODE_BRIDGE;
        }
        return sighting.kind() == HostKind.UNKNOWN ? NetGlyphs.NODE_CONTACT : NetGlyphs.NODE_IDENTIFIED;
    }

    /** The style class for a cell. Paired one-to-one with {@link #glyphFor}, in the same order. */
    static String styleFor(Sighting sighting, boolean vantage) {
        if (vantage) {
            return "es-netmap-vantage";
        }
        if (sighting.honeypotSuspected()) {
            return "es-netmap-trap";
        }
        if (sighting.foothold()) {
            return "es-netmap-foothold";
        }
        if (sighting.kind() == HostKind.BRIDGE) {
            return "es-netmap-bridge";
        }
        return sighting.kind() == HostKind.UNKNOWN ? "es-netmap-contact" : "es-netmap-identified";
    }

    /**
     * The kind field.
     *
     * <p>⚠ {@code UNKNOWN} prints as dashes rather than as the word. Naming it would be the sweep
     * answering the Passive Sniffer's question for free, and {@code docs/design/07-recon-tools.md} §1
     * prices that at 15 EC — but printing the literal string {@code UNKNOWN} would be worse than
     * either, because it looks like a type. Dashes look like an empty field, which is what it is.
     */
    static String kindOf(Sighting sighting) {
        return sighting.kind() == HostKind.UNKNOWN
                ? UNTYPED
                : sighting.kind().name().toUpperCase(Locale.ROOT);
    }

    // ── Text helpers ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fits a layer header into its column without losing the clamp marker.
     *
     * <p>⚠ The naive version of this is a plain {@code clip}, and it fails in precisely the case the
     * marker exists for. A server holds up to fifty machines and the deepest column is the one most
     * likely to hold them, but the deepest column is also the <b>only</b> one with no gap to borrow
     * width from — so {@code "H1 · home-relay · +40 MORE"} clipped to sixteen columns comes out as
     * {@code "H1 · home-relay "}, and a player looking at ten of fifty machines is told nothing at all.
     * A middle column is worse rather than better: it clips to {@code "… · +40 "}, which is a mangled
     * number where an exact one was meant.
     *
     * <p>So the marker is protected and the server name gives way, marked with an
     * {@link NetGlyphs#ELLIPSIS} so a shortened name reads as shortened rather than as a machine whose
     * name is wrong. The full text stays available: {@link NetLayout.Result#layerHeaders} is unclipped,
     * and {@link Painted#overflow} carries the same count to the panel note underneath the graph.
     *
     * @param header one of {@link NetLayout.Result#layerHeaders}
     * @param span how many columns this header may occupy
     */
    static String fit(String header, int span) {
        if (header.length() <= span) {
            return header;
        }
        int at = header.lastIndexOf(NetLayout.CLAMP_MARK);
        if (at < 0) {
            // No marker to protect: an ordinary long header, and clip's truncation is the whole story.
            return header;
        }
        String marker = header.substring(at);
        // Two columns is the floor for a legible remainder: one character and the ellipsis. Below it
        // there is nothing to save, and the marker alone is the more useful half of the line.
        int room = span - marker.length();
        if (room < 2) {
            return marker;
        }
        return trimSeparators(header.substring(0, room - 1)) + NetGlyphs.ELLIPSIS + marker;
    }

    /**
     * Drops trailing spaces and separator bullets, so an elision mark never follows a separator.
     *
     * <p>⚠ Found by rendering rather than by reading, and in the commonest case the game produces:
     * the outermost column is the one with no gap to borrow width from <em>and</em> the one most
     * likely to be holding fifty machines. Without this, {@code "H1 · home-relay · +40 MORE"} fitted
     * into sixteen columns came out as {@code "H1 ·… · +40 MORE"} — a dangling separator, an
     * ellipsis, then a second separator, which reads as a corrupted string rather than a shortened
     * one. Trimming gives {@code "H1… · +40 MORE"}: same information, said once.
     *
     * <p>The stem can never trim away to nothing. Its first character is the {@code H} of the hop
     * number, which is neither a space nor a bullet, and {@link #fit} has already refused a
     * {@code room} small enough to produce an empty stem.
     */
    private static String trimSeparators(String stem) {
        int end = stem.length();
        while (end > 0 && (stem.charAt(end - 1) == ' ' || stem.charAt(end - 1) == AsciiCanvas.BULLET)) {
            end--;
        }
        return stem.substring(0, end);
    }

    static String blank(int width) {
        return width <= 0 ? "" : String.valueOf(' ').repeat(width);
    }

    static String padRight(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() >= width ? value.substring(0, width) : value + blank(width - value.length());
    }

    static String clip(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() > width ? value.substring(0, width) : padRight(value, width);
    }
}
