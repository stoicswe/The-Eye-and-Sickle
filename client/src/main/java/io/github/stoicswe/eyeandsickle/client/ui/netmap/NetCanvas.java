package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.breach.AsciiCanvas;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 * layout did not allocate. Hence {@link Canvas#occupied}: every cell written by a header, a node cell,
 * a stub or a destination arrow is closed to routing, and {@link Canvas#merge} refuses rather than
 * overwrites.
 *
 * <h2>⚠ THE CORRIDOR IS THIRTEEN COLUMNS, NOT THREE — fixed 2026-08-08</h2>
 *
 * The distance from one layer's node box to the next is {@link UiTokens#NET_GAP_COLS} <b>plus</b> the
 * next layer's {@link UiTokens#NET_LATERAL_COLS}. A forward edge used to stop at the end of the gap,
 * which left its arrowhead pointing into ten blank columns — reported as "there is still a space", and
 * true of every forward arrow the map has ever drawn
 * ({@code docs/client/09-network-map-graph.md} §1.3). Lateral edges had the mirror-image defect: their
 * bracket sat at the <em>start</em> of the strip and stopped eight columns short of the box it joined.
 *
 * <p>Both are fixed by moving the lateral bracket to the far end of the strip, against the box
 * ({@link UiTokens#NET_LATERAL_BUS_COLS}), and running forward edges the full corridor. A forward run
 * then crosses lateral ink at exactly <b>one column</b>, and it <b>yields</b> there rather than merging
 * — see {@link Canvas#merge}. That is what "route around those two columns" comes to in a grid where
 * everything going left to right necessarily crosses every column: not a detour, but a single cell the
 * horizontal declines to claim, so the arc underneath survives intact and §1.2's shape distinction
 * holds by construction rather than by luck.
 *
 * <h2>Two edge classes, told apart by shape</h2>
 *
 * A <b>forward</b> edge crosses into the next hop layer and is drawn along the corridor with sharp
 * junctions and a {@code →} against the destination box. A <b>lateral</b> edge stays inside a layer
 * and is drawn in the two-column bracket on the left of its own boxes, with <em>rounded</em> corners.
 * The distinction is carried by the glyph, not by the ink: the map has to survive greyscale, and the
 * two kinds of edge mean genuinely different things — one is a hop the ceiling counts, the other is
 * not.
 */
public final class NetCanvas {

    private NetCanvas() {}

    /** Column budget for one layer: the lateral strip plus the node cell. */
    private static final int LAYER_COLS = UiTokens.NET_LATERAL_COLS + UiTokens.NET_NODE_COLS;

    /** Layer start to layer start. */
    private static final int PITCH = LAYER_COLS + UiTokens.NET_GAP_COLS;

    /**
     * How far a forward edge runs: from one layer's node box to the next layer's node box.
     *
     * <p>The gap, then the whole of the next layer's lateral strip. The arrowhead lands on the last of
     * them, which is the column immediately left of the box it points at.
     */
    static final int CORRIDOR_COLS = UiTokens.NET_GAP_COLS + UiTokens.NET_LATERAL_COLS;

    /** Corridor-relative column of the arrowhead, and of a lateral edge's stub into its box. */
    static final int ARROW_COL = CORRIDOR_COLS - 1;

    /**
     * Corridor-relative column of the next layer's lateral channel — the one cell a forward run yields
     * at. Strip-relative, it is {@code NET_LATERAL_COLS - NET_LATERAL_BUS_COLS}.
     */
    static final int BUS_COL = CORRIDOR_COLS - UiTokens.NET_LATERAL_BUS_COLS;

    /**
     * Where each routing lane turns, corridor-relative.
     *
     * <h2>⚠ DERIVED, because a literal here shipped wrong once already</h2>
     *
     * The turn column was {@code 1 + lane * 2} with three lanes, written when the gap was seven columns
     * wide. The gap was later narrowed to three and this was never revisited, so lanes 1 and 2 turned
     * at columns 3 and 5 in a run that ended at 2 — outside it, on the next layer's node box, where
     * every write was refused by {@code occupied}. Two thirds of every fan-out reached the screen as a
     * source stub with no vertical, no destination run and <b>no arrowhead</b>. Nothing failed; the map
     * drew, the nodes were right, the numbers were right. It was only wrong to look at.
     *
     * <p>Odd columns so no two lanes share a vertical, strictly inside the run so the arrowhead's
     * column is never a turn, and <b>never the lateral channel</b> — a vertical there would be
     * indistinguishable from a same-layer edge, which is the one distinction this map cannot afford to
     * blur. {@code EdgeLaneFitTest} re-derives all three properties from the tokens rather than asking
     * this array for its own answer.
     */
    private static final int[] TURNS = turnColumns();

    static int[] turnColumns() {
        List<Integer> turns = new ArrayList<>();
        for (int col = 1; col < ARROW_COL; col += 2) {
            if (col != BUS_COL) {
                turns.add(col);
            }
        }
        if (turns.isEmpty()) {
            // A corridor too narrow for any lane still has to route its edges somewhere.
            turns.add(0);
        }
        int[] out = new int[turns.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = turns.get(i);
        }
        return out;
    }

    /** The kind field inside a node cell. Every {@code HostKind} name fits; {@code UNKNOWN} does not print. */
    /**
     * ⚠ Eight until 2026-07-27, when the lock marker took three columns and a separator.
     *
     * <p>The interior line is {@code blank + marker(2) + blank + kind(KIND_COLS) + blank + lock(3)}
     * and must total {@link UiTokens#NET_NODE_COLS} minus the two box rules. The kind field gave up
     * the room because it is the only piece on the line that degrades gracefully — a clipped type
     * name is still readable and the tooltip carries it in full, where a clipped lock marker is a
     * different symbol.
     */
    private static final int KIND_COLS = 4;

    /** What an un-typed machine's kind field reads. Eight of them, so the field never changes width. */
    private static final String UNTYPED = "-".repeat(KIND_COLS);

    /**
     * How many offset plates a stack draws behind its right edge.
     *
     * <p>They are drawn <b>inside</b> {@link UiTokens#NET_NODE_COLS}, not beyond it: a box that grew
     * to carry its own decoration would shear every column to its right, which is the failure that
     * constant exists to make impossible. So a stack's body is two columns narrower than a machine's
     * and the plates take the difference.
     */
    private static final int PLATE_COLS = 2;

    /**
     * One drawn box.
     *
     * @param address the machine's address, or {@code ""} for a bridge stub or a stack, neither of
     *     which has one the player is allowed to act on
     * @param peerServerName set only on a stub: the name of the server on the far side, and the one
     *     fact a bridge is licensed to publish
     * @param text exactly {@code NET_NODE_LINES} lines of {@code NET_NODE_COLS} characters
     * @param stackId set only on a stack: the key {@code NetLayout} expands and collapses it by
     * @param stackCount how many machines a stack holds; zero on anything else
     */
    public record Piece(
            String address,
            String peerServerName,
            int layer,
            int row,
            String styleClass,
            String text,
            boolean stub,
            boolean selected,
            String stackId,
            int stackCount) {

        /** A folded group rather than a machine. */
        public boolean stack() {
            return !stackId.isEmpty();
        }
    }

    /**
     * The finished picture.
     *
     * @param lines the whole grid, one string per line, every line the same width
     * @param pieces node cells, stacks and stubs, in column-then-row order
     * @param strips one per layer: the lateral strip, body lines only (the header line is excluded,
     *     because it is drawn once across the whole map rather than per column)
     * @param gaps one per inter-layer gap, body lines only
     * @param header the layer-header line, full width
     * @param serverStrip the always-present "which network am I on" line, drawn above the graph
     * @param folded how many machines are inside collapsed stacks right now — never a count of
     *     anything undiscovered
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
            int folded,
            int packetCells) {}

    // ── Painting ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Draws the whole map.
     *
     * @param map the player's visible network; {@code null} and empty both draw nothing at all
     * @param packetPhase the animation step; only ever moves the one {@code ·}
     */
    public static Painted paint(NetMap map, int packetPhase) {
        return paint(map, packetPhase, "", Set.of());
    }

    /**
     * Draws the whole map, marking one machine as the one the player has picked.
     *
     * @param selectedAddress the machine CONNECT, DOWNLOAD and the breach would act on; {@code ""}
     *     for none. An address that is not on the map marks nothing and is not an error — a
     *     selection outlives the sighting that produced it by a repaint or two, and a renderer that
     *     threw on that would crash on the frame after a machine went out of view
     * @param expanded the stacks the player has opened; see {@link NetLayout#of(NetMap, Set)}
     */
    public static Painted paint(NetMap map, int packetPhase, String selectedAddress, Set<String> expanded) {
        NetMap safe = map == null ? NetMap.empty() : map;
        String strip = serverStrip(safe);
        NetLayout.Result layout = NetLayout.of(safe, expanded);
        if (layout.layers() == 0) {
            return new Painted(List.of(), List.of(), List.of(), List.of(), "", strip, 0, 0, 0, 0);
        }
        String selected = selectedAddress == null ? "" : selectedAddress;
        return new Canvas(safe, layout, packetPhase, strip, selected).paint();
    }

    /** The grid as text, one line per row. The seam every geometric test reads. */
    public static String frame(NetMap map, int packetPhase) {
        return frame(map, packetPhase, "", Set.of());
    }

    /** The grid as text with one machine marked — the seam the selection tests read. */
    public static String frame(NetMap map, int packetPhase, String selectedAddress, Set<String> expanded) {
        StringBuilder out = new StringBuilder();
        for (String line : paint(map, packetPhase, selectedAddress, expanded).lines()) {
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
                ? (map.currentServer().serverId().isEmpty()
                        ? UNTYPED
                        : map.currentServer().serverId())
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

        private Canvas(NetMap map, NetLayout.Result layout, int packetPhase, String serverStrip, String selected) {
            this.map = map;
            this.layout = layout;
            this.packetPhase = packetPhase;
            this.serverStrip = serverStrip;
            this.selected = selected;

            for (NetLayout.Placed placed : layout.placed()) {
                slotOf.put(placed.sighting().address(), new int[] {placed.layer(), placed.row()});
            }
            for (NetLayout.Stack stack : layout.stacks()) {
                if (!stack.expanded()) {
                    slotOf.put(stack.id(), new int[] {stack.layer(), stack.row()});
                }
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
         * <p>One column further out than the bridge itself, in the first row slot no machine, stack
         * or other stub is using — which is what makes it read as "the network continues that way"
         * rather than as a machine the player has mapped. A bridge in the outermost layer therefore
         * grows the picture by exactly one column, and never by more, because a stub is only ever
         * placed one layer beyond a machine that <em>is</em> placed.
         */
        private void planStubs() {
            Set<Long> taken = new HashSet<>();
            for (NetLayout.Placed placed : layout.placed()) {
                taken.add(slot(placed.layer(), placed.row()));
            }
            for (NetLayout.Stack stack : layout.stacks()) {
                if (!stack.expanded()) {
                    taken.add(slot(stack.layer(), stack.row()));
                }
            }
            for (NetLayout.Placed placed : layout.placed()) {
                Sighting sighting = placed.sighting();
                if (sighting.kind() != HostKind.BRIDGE
                        || sighting.bridgePeerServerName().isEmpty()) {
                    continue;
                }
                int target = placed.layer() + 1;
                for (int row = 0; ; row++) {
                    if (taken.add(slot(target, row))) {
                        stubs.add(new Stub(sighting.address(), sighting.bridgePeerServerName(), target, row));
                        break;
                    }
                }
            }
        }

        private static long slot(int layer, int row) {
            return ((long) layer << 32) | (row & 0xFFFFFFFFL);
        }

        private Painted paint() {
            drawHeader();
            drawCells();
            drawStacks();
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
                    layout.foldedMachines(),
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
                        picked,
                        "",
                        0));
            }
        }

        private void drawStacks() {
            for (NetLayout.Stack stack : layout.stacks()) {
                if (stack.expanded()) {
                    // Its members are ordinary cells now. It is still reported by the layout so a
                    // keystroke can close it again; there is nothing to draw for it.
                    continue;
                }
                String block = stackText(stack.count());
                blit(stack.layer(), stack.row(), block);
                pieces.add(new Piece(
                        "",
                        "",
                        stack.layer(),
                        stack.row(),
                        "es-netmap-stack",
                        block,
                        false,
                        // A stack is never the selection: CONNECT, DOWNLOAD and a breach all act on
                        // one address and a fold has none. Opening it is the action it offers.
                        false,
                        stack.id(),
                        stack.count()));
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
                        false,
                        "",
                        0));
            }
        }

        /** Writes a block into a column slot and closes those cells to routing. */
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

        /**
         * ⚠ <b>Lateral edges are drawn first, and the order is load-bearing.</b> A forward run crosses
         * the lateral channel at one cell and {@link #merge} yields there to whatever is already in
         * it — which only works if the lateral edge got there first. Drawn the other way round, the
         * forward run would claim an empty cell and the arc would be refused instead, inverting the
         * rule and losing exactly the distinction it exists to protect.
         */
        private void drawEdges() {
            for (NetLayout.Routed routed : layout.routed()) {
                if (!routed.lateral()) {
                    continue;
                }
                int[] from = slotOf.get(routed.fromAddress());
                int[] to = slotOf.get(routed.toAddress());
                if (from != null && to != null) {
                    lateral(from[0], Math.min(from[1], to[1]), Math.max(from[1], to[1]));
                }
            }
            int[] lanes = new int[Math.max(1, layers)];
            for (NetLayout.Routed routed : layout.routed()) {
                if (routed.lateral()) {
                    continue;
                }
                int[] from = slotOf.get(routed.fromAddress());
                int[] to = slotOf.get(routed.toAddress());
                if (from != null && to != null && forward(from[0], from[1], to[1], lanes[from[0]])) {
                    lanes[from[0]]++;
                }
            }
            // Stub edges last, so a real machine always gets the low lanes: the lane a reader follows
            // first should lead somewhere they can act on.
            for (Stub stub : stubs) {
                int[] from = slotOf.get(stub.bridgeAddress());
                if (from != null && forward(from[0], from[1], stub.row(), lanes[from[0]])) {
                    lanes[from[0]]++;
                }
            }
        }

        /**
         * A forward edge, drawn along the corridor to the right of {@code layer}.
         *
         * <p>Source run, a turn, a destination run and an arrowhead against the next box. The turn
         * column comes from {@link #TURNS}, so the lanes never share a vertical and a fan-out stays
         * legible where a single bus would go solid.
         */
        private boolean forward(int layer, int fromRow, int toRow, int index) {
            if (layer + 1 >= layers) {
                return false;
            }
            int corridor = layer * PITCH + LAYER_COLS;
            int source = cellLine(fromRow);
            int destination = cellLine(toRow);

            if (source == destination) {
                for (int col = 0; col < ARROW_COL; col++) {
                    merge(source, corridor + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
                // ⚠ It took no lane, so it must not spend one. An edge between two machines on the
                // same row is a straight run with no vertical at all, and counting it pushed every
                // turning edge in the fan one lane further along — which on a five-lane corridor is
                // the difference between five distinguishable branches and four plus a collision.
                put(destination, corridor + ARROW_COL, AsciiCanvas.ARROW_RIGHT);
                close(destination, corridor + ARROW_COL);
                return false;
            } else {
                int turn = TURNS[Math.floorMod(index, TURNS.length)];
                int away = destination > source ? AsciiCanvas.DOWN : AsciiCanvas.UP;
                int back = destination > source ? AsciiCanvas.UP : AsciiCanvas.DOWN;
                for (int col = 0; col < turn; col++) {
                    merge(source, corridor + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
                merge(source, corridor + turn, AsciiCanvas.LEFT | away);
                int step = destination > source ? 1 : -1;
                for (int line = source + step; line != destination; line += step) {
                    merge(line, corridor + turn, AsciiCanvas.UP | AsciiCanvas.DOWN);
                }
                merge(destination, corridor + turn, back | AsciiCanvas.RIGHT);
                for (int col = turn + 1; col < ARROW_COL; col++) {
                    merge(destination, corridor + col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                }
            }
            // ⚠ Written, never merged. The arrowhead is not in the junction table, so OR-ing bits
            // into it would hand back a stub and the edge would lose the one mark that says which way
            // it runs. It closes its cell afterwards so nothing else can take it back.
            //
            // ⚠ It shares its column with a lateral edge's stub, and winning there is correct: both
            // mean "this joins the box on the right", and an arrowhead says it more precisely. The
            // lateral's own corner is in the column before, so nothing about that edge is lost.
            put(destination, corridor + ARROW_COL, AsciiCanvas.ARROW_RIGHT);
            close(destination, corridor + ARROW_COL);
            return true;
        }

        /**
         * A lateral edge, drawn in the bracket at the right-hand end of its own layer's strip.
         *
         * <p>Rounded corners, so a reader can tell a same-layer edge from a hop <em>by shape</em>. The
         * arcs still merge: two lateral edges that overlap produce {@code ├}, which is the honest
         * reading — a branch — rather than one silently erasing the other.
         *
         * <p>⚠ The stub reaches the box. Until 2026-08-08 the bracket sat at the far side of a
         * ten-column strip and stopped eight columns short of the machine it joined, so a same-layer
         * link visibly connected to nothing.
         */
        private void lateral(int layer, int upperRow, int lowerRow) {
            if (upperRow == lowerRow) {
                return;
            }
            int channel = layer * PITCH + UiTokens.NET_LATERAL_COLS - UiTokens.NET_LATERAL_BUS_COLS;
            int upper = cellLine(upperRow);
            int lower = cellLine(lowerRow);
            arc(upper, channel, AsciiCanvas.DOWN | AsciiCanvas.RIGHT);
            arc(lower, channel, AsciiCanvas.UP | AsciiCanvas.RIGHT);
            for (int line = upper + 1; line < lower; line++) {
                arc(line, channel, AsciiCanvas.UP | AsciiCanvas.DOWN);
            }
            for (int col = channel + 1; col < layer * PITCH + UiTokens.NET_LATERAL_COLS; col++) {
                arc(upper, col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
                arc(lower, col, AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
            }
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
         *
         * <p>⚠ It walks the whole <b>corridor</b>, which crosses a strip Label as well as a gap one.
         * {@link NetGraph#advance} repaints both for that reason, and never a node cell — a replaced
         * cell is one that has lost keyboard focus.
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
            int corridor = slot[0] * PITCH + LAYER_COLS;
            for (int col = corridor; col < corridor + CORRIDOR_COLS && col < cols; col++) {
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

        /** Whether a column is a layer's lateral channel — the one cell a forward run yields at. */
        private static boolean lateralChannel(int col) {
            return Math.floorMod(col, PITCH) == UiTokens.NET_LATERAL_COLS - UiTokens.NET_LATERAL_BUS_COLS;
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

        /**
         * OR-s direction bits into a cell, refusing anything a piece has already claimed.
         *
         * <p>⚠ And <b>yielding</b> at a lateral channel that already carries ink. A forward run
         * crossing there would turn {@code ╰} into {@code ┴} — honest, and still a loss: the arc is
         * how a same-layer edge is told from a hop in greyscale, and this map has no second signal for
         * it. Skipping the cell leaves the horizontal reading as though it passes behind the vertical,
         * which is the older and better convention anyway. This is the whole of "route around those
         * two columns" from {@code 09} §1.3 — in a grid, the only way around a column is to decline
         * one cell of it.
         */
        private void merge(int line, int col, int add) {
            if (line < 0 || line >= lines || col < 0 || col >= cols || occupied[line][col]) {
                return;
            }
            if (lateralChannel(col) && bits[line][col] != 0) {
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
        // ⚠ The widths here sum to NET_NODE_COLS - 2 exactly. Anything that does not shears every
        // column to its right, which is the failure NET_NODE_COLS exists to make impossible.
        String interior = blank(1)
                + glyphFor(sighting, vantage)
                + blank(1)
                + padRight(kindOf(sighting), KIND_COLS)
                + blank(1)
                + lockFor(sighting, vantage);
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

        // ⚠ THE OPERATOR RIDES ON THE ADDRESS LINE AND THE NAME GETS ITS OWN.
        //
        // Both are the IDENTITY rung's product and both are empty until it has been paid for, so an
        // unscanned machine reads exactly as it did before this line existed: address, then a blank.
        //
        // The widths are the reason they are split this way rather than sharing one line. The widest
        // address this scheme can produce is `10.6.0.255` — ten columns — which with the lead and a
        // separator leaves seven for an account name, and the longest name in the pool is six. So the
        // pair always fits. A machine NAME does not: `adjective-pioneer` runs to 23 columns at worst
        // (`practical-chandrasekhar`) against a box of 18, so it takes a whole line and is clipped for
        // the ~2.5% of combinations that overrun. Clipped rather than elided in the middle, because a
        // name is read from its front — and the unclipped one is on the tooltip, in the host list and
        // in the RECON file.
        String operator = sighting.operatorName();
        String addressLine = lead + sighting.address() + (operator.isEmpty() ? "" : blank(1) + operator);
        return tl + rule + tr
                + "\n" + vertical + clip(interior, UiTokens.NET_NODE_COLS - 2) + vertical
                + "\n" + bl + rule + br
                + "\n" + padRight(clip(addressLine, UiTokens.NET_NODE_COLS), UiTokens.NET_NODE_COLS)
                + "\n" + padRight(clip(blank(1) + sighting.label(), UiTokens.NET_NODE_COLS), UiTokens.NET_NODE_COLS);
    }

    /**
     * A stack: one box carrying an exact count of machines folded behind one parent.
     *
     * <pre>
     * ┌──────────────┐┐        ┌────────────────┐
     * │ ▚▚ ×7        │││       │ ▒▒ TERM    [#] │
     * └──────────────┘┘        └────────────────┘
     *  7 MACHINES               10.0.0.7
     *  [+] OPEN                 quiet-hopper
     * </pre>
     *
     * <p>⚠ <b>No heavy frame.</b> That is the vantage's, and the map has exactly one. A stack reads as
     * a stack by its <b>offset plates</b> — a shape nothing else on this surface uses — which is the
     * distinction {@code 09} §5 asks for and the one that survives greyscale.
     *
     * <p>⚠ The count is exact and it counts <b>machines the player has found</b>. It is never a hint
     * about anything undiscovered; see {@link NetLayout.Stack}.
     *
     * <p>⚠ The state is in the text as well as in the shape — {@code [+] OPEN} in the deck's existing
     * bracket idiom, the same one the lock markers use. §4.4: a state carried by a shape alone is
     * invisible to a screen reader, and {@link NetGraph} says it in words a third time.
     */
    static String stackText(int count) {
        int body = UiTokens.NET_NODE_COLS - PLATE_COLS;
        String rule = String.valueOf(AsciiCanvas.LIGHT_H).repeat(body - 2);
        String plateTop = String.valueOf(AsciiCanvas.LIGHT_TR).repeat(PLATE_COLS);
        String plateMid = String.valueOf(AsciiCanvas.LIGHT_V).repeat(PLATE_COLS);
        String plateLow = String.valueOf(AsciiCanvas.LIGHT_BR).repeat(PLATE_COLS);
        String interior = blank(1) + NetGlyphs.NODE_STACK + blank(1) + padRight("×" + count, body - 5);
        return AsciiCanvas.LIGHT_TL + rule + AsciiCanvas.LIGHT_TR + plateTop
                + "\n" + AsciiCanvas.LIGHT_V + clip(interior, body - 2) + AsciiCanvas.LIGHT_V + plateMid
                + "\n" + AsciiCanvas.LIGHT_BL + rule + AsciiCanvas.LIGHT_BR + plateLow
                + "\n" + padRight(blank(1) + count + (count == 1 ? " MACHINE" : " MACHINES"), UiTokens.NET_NODE_COLS)
                + "\n" + padRight(blank(1) + NetGlyphs.STACK_OPEN + " OPEN", UiTokens.NET_NODE_COLS);
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

    /**
     * The lock marker: whether the player is inside this machine, was, or never has been.
     *
     * <h2>⚠ It answers a different question from the state marker, which is why it is separate</h2>
     *
     * {@link #glyphFor}'s ink level says <em>how much is known</em> about a host — contact,
     * identified, foothold. The lock says <em>whether the way in is open</em>. Those came apart the
     * moment a host could be breached and then patched: such a host is fully identified, richly
     * known, and shut. One glyph cannot carry both, and overloading the ink level would have made
     * "patched" read as "less well known", which is the opposite of true.
     *
     * <p>The vantage is the machine the player is operating from, so it is open by definition and
     * says so rather than being left blank.
     */
    static String lockFor(Sighting sighting, boolean vantage) {
        if (vantage || sighting.foothold()) {
            return NetGlyphs.LOCK_OPEN;
        }
        if (sighting.patched()) {
            return NetGlyphs.LOCK_PATCHED;
        }
        return NetGlyphs.LOCK_SHUT;
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
        // ⚠ Above bridge and identified, below trap. A patched host is a warning about a route the
        // player is relying on and has lost; a suspected honeypot is still the more urgent thing,
        // because one is a closed door and the other is a trap standing open.
        if (sighting.patched()) {
            return "es-netmap-patched";
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
     * Fits a layer header into its column.
     *
     * <p>A server name too wide for its column is shortened with an {@link NetGlyphs#ELLIPSIS} rather
     * than simply cut: a header truncated without a mark reads as a machine whose name is wrong, which
     * is the kind of thing a player learns to distrust the whole instrument over. The full text stays
     * available — {@link NetLayout.Result#layerHeaders} is unclipped and the list view names every
     * server in full.
     *
     * <p>⚠ This used to have a second job: protecting a {@code "· +N MORE"} clamp marker while the
     * name gave way. That marker is gone with the clamp it described — see {@link NetLayout} — because
     * a map that draws ten of fifty machines and puts the rest in a header count is a map with
     * machines missing from it. Nothing is hidden without a box and a number now.
     *
     * @param header one of {@link NetLayout.Result#layerHeaders}
     * @param span how many columns this header may occupy
     */
    static String fit(String header, int span) {
        if (header.length() <= span || span < 2) {
            return header;
        }
        return header.substring(0, span - 1) + NetGlyphs.ELLIPSIS;
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
