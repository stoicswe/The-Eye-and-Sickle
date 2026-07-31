package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.breach.AsciiCanvas;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the map actually draws.
 *
 * <h2>Why this reads {@link NetCanvas} rather than building a {@link NetGraph}</h2>
 *
 * ⚠ Measured on this project, JavaFX 26.0.2 / JDK 26: {@code new VBox()} succeeds with no toolkit and
 * {@code new Label("x")} does <b>not</b> — {@code ExceptionInInitializerError} caused by
 * {@code IllegalStateException: Toolkit not initialized}, thrown from {@code Control}'s static
 * initialiser before any constructor body runs. {@code Tooltip} fails identically. The client bundles
 * neither TestFX nor Monocle, and {@code UiContractTest} is explicit that "a contract test that only
 * runs on a machine with a display is a contract test that does not run in CI".
 *
 * <p>So the picture is computed by a class with no JavaFX supertype, and {@code NetGraph.frame()}
 * returns exactly {@code NetCanvas.frame(...)} for the same map and phase. Asserting on the latter
 * asserts on the former, character for character — and it runs everywhere.
 *
 * <h2>What is worth asserting on a character grid</h2>
 *
 * Every check below is a failure this kind of renderer actually produces. Edges that erase each other
 * instead of merging; a lane routed straight through a node cell, silently replacing a block glyph
 * with a stub; an animation frame that lands on a junction and deletes a branch from the map the
 * player is deducing on. All three look like a perfectly reasonable picture in a screenshot.
 */
class NetGraphTest {

    private static final int MAX_ROWS = 10;

    /**
     * Layer start to layer start, derived rather than written out.
     *
     * <p>{@code NetCanvas} computes the same number from the same three tokens and keeps it private,
     * which is right — but a test that hard-codes {@code 23} passes for the wrong reason the day one
     * of those tokens moves, reading a node cell at an offset that is no longer where node cells are.
     */
    private static final int PITCH = UiTokens.NET_LATERAL_COLS + UiTokens.NET_NODE_COLS + UiTokens.NET_GAP_COLS;

    private static List<String> lines(NetMap map, int phase) {
        return NetCanvas.paint(map, MAX_ROWS, phase).lines();
    }

    private static char at(List<String> grid, int line, int col) {
        return line < grid.size() && col < grid.get(line).length()
                ? grid.get(line).charAt(col)
                : ' ';
    }

    @Nested
    @DisplayName("edges merge; they never overwrite")
    class Routing {

        @Test
        @DisplayName("the merge rule is total: every direction set round-trips through a glyph")
        void crossingsMerge() {
            // The failure this prevents is CoreCage's z-buffer story in a different shape: draw in
            // index order with no merge rule and the last writer wins, which is whichever the loop
            // happened to reach. The picture keeps its shape and silently loses its structure.
            //
            // ⚠ This used to assert that a rendered fixture CONTAINS '┼'. It does not, and neither
            // does any other fixture — measured, by rendering all three and searching. The layout
            // routes each edge in its own lane and turns into its target row, so a full four-way
            // crossing is rare rather than typical. Asserting on one was testing a layout accident:
            // it would fail the day the router got BETTER at avoiding crossings, which is backwards.
            //
            // The rule itself is what matters and it is total, so it is asserted directly. The
            // structural evidence that merging really happens in a render lives in fanOutMerges
            // below, which passes against a real fixture.
            assertThat(AsciiCanvas.junction(AsciiCanvas.UP | AsciiCanvas.DOWN | AsciiCanvas.LEFT | AsciiCanvas.RIGHT))
                    .as("a four-way crossing has a glyph, so a crossing can never erase")
                    .isEqualTo('┼');
            for (int bits = 1; bits < 16; bits++) {
                char glyph = AsciiCanvas.junction(bits);
                assertThat(glyph).as("direction set %d has a glyph", bits).isNotEqualTo(' ');
                assertThat(AsciiCanvas.bitsOf(glyph))
                        .as("glyph for %d reports back the same directions", bits)
                        .isEqualTo(bits);
            }
        }

        @Test
        @DisplayName("a fan-out produces tees, so every branch is still visible")
        void fanOutMerges() {
            // Four edges leaving one node share the source row. Without merging, three of them would
            // be invisible and the map would report the vantage as having one neighbour.
            String frame = NetCanvas.frame(NetFixtures.opening(), MAX_ROWS, 0);
            assertThat(frame)
                    .contains(String.valueOf(
                            AsciiCanvas.junction(AsciiCanvas.LEFT | AsciiCanvas.RIGHT | AsciiCanvas.DOWN)));
        }

        @Test
        @DisplayName("nothing is routed through a node cell")
        void nodeCellsAreIntact() {
            // ⚠ AsciiCanvas.bitsOf returns 0 for anything outside the sixteen-entry light table, so a
            // lane crossing a cell would replace a block glyph with a bare stub — a node that quietly
            // stops being a node. LatticeMap survives without an occupancy mask only because its lanes
            // can never reach a cell; ours can, since a bridge stub is placed into a column the layout
            // did not allocate.
            for (NetMap map : List.of(NetFixtures.opening(), NetFixtures.twoHops(), NetFixtures.crowded(30))) {
                NetCanvas.Painted painted = NetCanvas.paint(map, MAX_ROWS, 0);
                for (NetCanvas.Piece piece : painted.pieces()) {
                    String[] expected = piece.text().split("\n", -1);
                    for (int i = 0; i < expected.length; i++) {
                        int line = 1 + piece.row() * UiTokens.NET_NODE_LINES + i;
                        int left = piece.layer() * PITCH + UiTokens.NET_LATERAL_COLS;
                        String drawn = painted.lines().get(line).substring(left, left + expected[i].length());
                        assertThat(drawn)
                                .as("cell at column %d row %d, line %d", piece.layer(), piece.row(), i)
                                .isEqualTo(expected[i]);
                    }
                }
            }
        }

        @Test
        @DisplayName("a lateral edge is told from a forward one by shape, not by colour")
        void lateralEdgesUseArcs() {
            // The map has to survive greyscale — the palette reserves its one accent for live/earning
            // data and a network node is not earning — so the two edge classes cannot be distinguished
            // by ink. Arcs versus sharp junctions is the distinction that survives.
            String frame = NetCanvas.frame(NetFixtures.twoHops(), MAX_ROWS, 0);
            assertThat(frame).contains(String.valueOf(NetGlyphs.ROUND_TL));
            assertThat(frame).contains(String.valueOf(NetGlyphs.ROUND_BL));
        }
    }

    @Nested
    @DisplayName("weight before colour")
    class Weight {

        @Test
        @DisplayName("the vantage carries the only heavy frame, at column zero row zero")
        void oneHeavyFrame() {
            // "Where am I operating from" is answered by frame weight before a single glyph is read,
            // and stays answered for a player who cannot separate the grey ramp at all.
            List<String> grid = lines(NetFixtures.twoHops(), 0);
            long heavy = grid.stream()
                    .flatMapToInt(String::chars)
                    .filter(c -> c == AsciiCanvas.HEAVY_TL)
                    .count();
            assertThat(heavy).isEqualTo(1);
            // Column 10, not 2: the layout indents the first hop layer to leave routing lanes to
            // its left. Measured from the rendered grid rather than assumed from the spec.
            assertThat(at(grid, 1, 10)).isEqualTo(AsciiCanvas.HEAVY_TL);
            assertThat(at(grid, 3, 10)).isEqualTo(AsciiCanvas.HEAVY_BL);
        }

        @Test
        @DisplayName("an un-typed machine reads as dashes, never as the word UNKNOWN")
        void untypedIsAnEmptyField() {
            // Naming the type would be the sweep answering the Passive Sniffer's question for free,
            // and 07 §1 prices that at 15 EC. Printing the literal word would be worse than either: it
            // looks like a type. Dashes look like an empty field, which is what it is.
            String frame = NetCanvas.frame(NetFixtures.opening(), MAX_ROWS, 0);
            assertThat(frame).contains(NetGlyphs.NODE_CONTACT).doesNotContain("UNKNOWN");
        }

        @Test
        @DisplayName("a bridge's far side is a named stub, and it is the only thing drawn beyond the map")
        void bridgeStubNamesItsPeer() {
            // The one fact a bridge exists to publish. Never a peer address, never a host count,
            // never anything about what is over there.
            String frame = NetCanvas.frame(NetFixtures.twoHops(), MAX_ROWS, 0);
            assertThat(frame).contains(NetGlyphs.NODE_BRIDGE);
            assertThat(frame).contains(NetGlyphs.NODE_DARK);
            assertThat(frame).contains(NetFixtures.SOUTH.name());
        }
    }

    @Nested
    @DisplayName("motion is decoration and it does not get to win")
    class Packet {

        @Test
        @DisplayName("a step only ever changes a plain horizontal run")
        void packetNeverErasesRouting() {
            // ⚠ LatticeMap's own warning, copied verbatim in behaviour: an unconditional write put the
            // dot on a ┬ and silently deleted a branch from the map the player was deducing on. The
            // headless harness caught it; a screenshot would not have.
            NetMap map = NetFixtures.twoHops();
            int cells = NetCanvas.paint(map, MAX_ROWS, 0).packetCells();
            assertThat(cells).isPositive();

            List<String> base = lines(map, 0);
            char run = AsciiCanvas.junction(AsciiCanvas.LEFT | AsciiCanvas.RIGHT);
            for (int phase = 1; phase < cells + 3; phase++) {
                List<String> next = lines(map, phase);
                assertThat(next).hasSameSizeAs(base);
                for (int line = 0; line < base.size(); line++) {
                    for (int col = 0; col < base.get(line).length(); col++) {
                        char was = base.get(line).charAt(col);
                        char now = next.get(line).charAt(col);
                        if (was == now) {
                            continue;
                        }
                        Set<Character> allowed = new HashSet<>(List.of(run, NetGlyphs.PACKET));
                        assertThat(allowed)
                                .as("phase %d changed (%d,%d) from '%c' to '%c'", phase, line, col, was, now)
                                .contains(was, now);
                    }
                }
            }
        }

        @Test
        @DisplayName("a step changes nothing outside a gap column")
        void packetStaysInsideItsGap() {
            // What NetGraph's partial repaint rests on. It advances the animation by setting the text
            // of the gap Labels alone, because rebuilding the columns three times a second would
            // replace every node cell — and a replaced Label has lost keyboard focus, so a player
            // tabbing across the map would be thrown back to the start of the traversal order before
            // they could press SPACE. That optimisation is only correct while this holds, and it is
            // the kind of coupling that rots silently: nothing in NetCanvas would fail if a future
            // change let a phase touch a node cell, and nothing on screen would look wrong either.
            for (NetMap map : List.of(NetFixtures.opening(), NetFixtures.twoHops())) {
                NetCanvas.Painted painted = NetCanvas.paint(map, MAX_ROWS, 0);
                List<String> base = painted.lines();
                for (int phase = 1; phase < painted.packetCells() + 3; phase++) {
                    List<String> next = lines(map, phase);
                    for (int line = 0; line < base.size(); line++) {
                        for (int col = 0; col < base.get(line).length(); col++) {
                            if (base.get(line).charAt(col) == next.get(line).charAt(col)) {
                                continue;
                            }
                            int within = col % PITCH;
                            assertThat(within)
                                    .as("phase %d changed (%d,%d), which is not in a gap", phase, line, col)
                                    .isGreaterThanOrEqualTo(UiTokens.NET_LATERAL_COLS + UiTokens.NET_NODE_COLS);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("exactly one packet is on the map at a time")
        void oneDot() {
            // Body lines only. The middle dot is this client's universal separator — AsciiCanvas.BULLET
            // — so the layer-header line carries one per column, and a bridge stub is two of them. That
            // overlap is deliberate and matches LatticeMap, which uses the same character for its own
            // packet and for its dark nodes simultaneously; the three never share a line, so context
            // separates them. What must stay true is that the moving one is singular.
            for (int phase = 0; phase < 6; phase++) {
                List<String> grid = lines(NetFixtures.opening(), phase);
                long dots = grid.subList(1, grid.size()).stream()
                        .flatMapToInt(String::chars)
                        .filter(c -> c == NetGlyphs.PACKET)
                        .count();
                assertThat(dots).as("phase %d", phase).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("a vantage with nothing leaving it reports no lane, so the widget holds still")
        void idleInstrumentHoldsStill() {
            // An instrument that keeps moving with nothing happening is a screensaver. NetGraph.advance
            // reads exactly this number to decide whether to repaint at all.
            NetMap alone = NetFixtures.map(List.of(NetFixtures.self("10.0.0.1")), List.of(), 1);
            assertThat(NetCanvas.paint(alone, MAX_ROWS, 0).packetCells()).isZero();
        }
    }

    @Nested
    @DisplayName("the grid holds its shape")
    class Shape {

        @Test
        @DisplayName("every line is the same width, so no column can shear")
        void rectangular() {
            // A slot short by a character shears every column to its right, and the map stops being a
            // grid at exactly the point a player starts trusting it. LatticeMap carries the same note.
            for (NetMap map : List.of(NetFixtures.opening(), NetFixtures.twoHops(), NetFixtures.crowded(50))) {
                List<String> grid = lines(map, 0);
                assertThat(grid).isNotEmpty();
                assertThat(grid).allMatch(line -> line.length() == grid.get(0).length());
            }
        }

        @Test
        @DisplayName("the same map paints the same frame twice")
        void stable() {
            NetMap map = NetFixtures.twoHops();
            assertThat(NetCanvas.frame(map, MAX_ROWS, 0)).isEqualTo(NetCanvas.frame(map, MAX_ROWS, 0));
            assertThat(NetCanvas.frame(NetFixtures.twoHops(), MAX_ROWS, 0))
                    .as("two equal maps paint the same picture")
                    .isEqualTo(NetCanvas.frame(map, MAX_ROWS, 0));
        }

        @Test
        @DisplayName("nothing discovered draws no cells and throws nothing")
        void emptyDrawsNothing() {
            assertThat(NetCanvas.frame(NetMap.empty(), MAX_ROWS, 0)).isEmpty();
            assertThat(NetCanvas.frame(null, MAX_ROWS, 0)).isEmpty();
            assertThat(NetCanvas.paint(NetMap.empty(), MAX_ROWS, 0).pieces()).isEmpty();
        }

        @Test
        @DisplayName("the server strip is always there, even with nothing on the map")
        void serverStripIsAlwaysPresent() {
            // The brief requires the graph to name the server the player is connected to at all times.
            // It is chrome inside the panel, so it has no z-order to lose and no tab to hide behind.
            assertThat(NetCanvas.paint(NetFixtures.opening(), MAX_ROWS, 0).serverStrip())
                    .contains(NetFixtures.HOME.name())
                    .contains("HOSTS SEEN 5")
                    .contains("CEILING 1 HOP");
            assertThat(NetCanvas.paint(NetMap.empty(), MAX_ROWS, 0).serverStrip())
                    .contains("HOSTS SEEN 0");
        }

        @Test
        @DisplayName("a clamped column is reported to the panel, not silently truncated")
        void overflowReaches() {
            assertThat(NetCanvas.paint(NetFixtures.crowded(50), MAX_ROWS, 0).overflow())
                    .isEqualTo(40);
        }

        @Test
        @DisplayName("a clamped column's drawn header keeps its exact count")
        void clampedHeaderSurvivesTheColumnWidth() {
            // ⚠ Found by rendering. The outermost column is the only one with no gap to borrow width
            // from and the one most likely to be holding fifty machines, so a plain truncation loses
            // the marker in precisely the case it exists for — the header came out as `H1 ·… · +40
            // MORE`, a dangling separator followed by an ellipsis followed by a second separator.
            // A middle column failed worse: it clipped mid-number, showing a count that was wrong
            // rather than absent.
            String header =
                    NetCanvas.paint(NetFixtures.crowded(50), MAX_ROWS, 0).header();
            assertThat(header).contains("+40 MORE");
            assertThat(header)
                    .as("an elision mark never follows a separator")
                    .doesNotContain(AsciiCanvas.BULLET + String.valueOf(NetGlyphs.ELLIPSIS));
        }
    }
}
