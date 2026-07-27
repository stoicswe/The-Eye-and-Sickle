package io.github.stoicswe.eyeandsickle.client.ui.breach;

import java.util.Arrays;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * A character grid painted into three overlaid monospace labels. The breach's drawing surface.
 *
 * <h2>Extracted from {@code CoreCage}, for the same reason it was written there</h2>
 *
 * Colour has to vary per character and JavaFX has no styled-text-run primitive cheaper than
 * {@link javafx.scene.text.TextFlow}. Because the face is monospace, overlaid {@link Label}s align
 * exactly — each carries the characters at its own ink level and spaces everywhere else, so the
 * three of them compose into one picture. Three nodes redrawn on a slow timer, instead of a node per
 * cell. {@code CoreCage} proved the mechanism with two levels; the breach needs a third, because a
 * locked layer is genuinely hostile state and {@code -es-alarm} is the only honest way to say so.
 *
 * <h2>⚠ This file is the glyph blast radius, and that is deliberate</h2>
 *
 * The implementation spec makes {@code AsciiCanvas} the only file in the breach package permitted to
 * carry box-drawing and block literals. That rule has been taken one step further here: <b>every
 * character the six breach widgets <em>draw</em> is a named constant below.</b> The only non-ASCII
 * literals left anywhere else in the package are three em dashes in player-facing prose, which are
 * punctuation rather than vocabulary and are present in both bundled faces.
 *
 * <p>The reason is {@code GlyphCoverageTest}. It parses the bundled TTF {@code cmap} and fails the
 * build on any character absent from IBM Plex Mono — and the characters a renderer reaches for by
 * reflex are exactly the ones that are missing. The Geometric Shapes block (U+25A0–U+25FF) has
 * <b>one</b> character in Plex, the lozenge; {@code ■ □ ▲ ● ◆ ►}, which is what a status marker, a
 * bullet and an arrow all want to be, are none of them present. Concentrating the vocabulary here
 * means the audit is one file and one {@code grep}, rather than seven files and a hope. Every
 * constant below was checked against the inventory in the implementation spec §7.2.
 *
 * <h2>Depth is glyph weight, never colour</h2>
 *
 * The palette has one accent and it is reserved (§2.1, and D-7 rations it to a single element in the
 * whole feature), so a render that wanted colour depth could not have it. Every state distinction
 * the breach draws is therefore carried by a <em>different character</em> first and an ink level
 * second: sealed is {@code ▓}, cleared is {@code ░}, bypassed is {@code ▒}, locked is {@code █}. A
 * player in greyscale, or one who cannot separate the grey ramp at all, reads the same picture.
 */
public final class AsciiCanvas extends StackPane {

    /** Structure and settled state. {@code es-viewport-dim}. */
    public static final int INK_DIM = 0;

    /** The live surface — the active layer, the current node, an open port. {@code es-viewport-live}. */
    public static final int INK_LIVE = 1;

    /** Hostile state only: a locked layer, a known trap. {@code es-viewport-alarm}. */
    public static final int INK_ALARM = 2;

    private static final int INKS = 3;

    // ── The drawing vocabulary ────────────────────────────────────────────────────────────────
    //
    // Every non-ASCII character the breach draws. See the class comment for why they all live here.

    /** Frame corners and sides, double-ruled. The outer edge of a drawn surface. */
    public static final char BOX_TL = '╔';

    public static final char BOX_TR = '╗';

    public static final char BOX_BL = '╚';

    public static final char BOX_BR = '╝';

    public static final char BOX_H = '═';

    public static final char BOX_V = '║';

    /** A divider inside a double frame: light rule, double tees. */
    public static final char TEE_L = '╟';

    public static final char TEE_R = '╢';

    /** The same divider when it borders the <em>active</em> layer — heavier, so the band reads framed. */
    public static final char TEE_L_LIVE = '╠';

    public static final char TEE_R_LIVE = '╣';

    /** Light box drawing, for the port comb, the tumblers and the lattice cells. */
    public static final char LIGHT_H = '─';

    public static final char LIGHT_V = '│';

    public static final char LIGHT_TL = '┌';

    public static final char LIGHT_TR = '┐';

    public static final char LIGHT_BL = '└';

    public static final char LIGHT_BR = '┘';

    public static final char LIGHT_T_DOWN = '┬';

    public static final char LIGHT_T_UP = '┴';

    /** Heavy box drawing. Reserved for "this one, right now": the current node, a solved tumbler. */
    public static final char HEAVY_H = '━';

    public static final char HEAVY_V = '┃';

    public static final char HEAVY_TL = '┏';

    public static final char HEAVY_TR = '┓';

    public static final char HEAVY_BL = '┗';

    public static final char HEAVY_BR = '┛';

    /** Layer fills. Four states, four weights — legible with the colour taken away. */
    public static final char FILL_SEALED = '▓';

    public static final char FILL_CLEARED = '░';

    public static final char FILL_BYPASSED = '▒';

    public static final char FILL_LOCKED = '█';

    /** Puzzle-class textures. One character each, so the class reads from across the room. */
    public static final char TEXTURE_ENUMERATION = '┬';

    public static final char TEXTURE_LOGIC = '╪';

    public static final String TEXTURE_TRAVERSAL = "╱╲";

    /** Diagonals, for the plinth under the tower and for lattice skips. */
    public static final char DIAG_DOWN = '╲';

    public static final char DIAG_UP = '╱';

    /** A strike still in hand. Spent strikes are {@link #BULLET}. */
    public static final char PIP = '▉';

    /** Response weight in the tumbler history: exact hits are solid, partials are half. */
    public static final char BAR_FULL = '█';

    public static final char BAR_HALF = '▌';

    /** The declared-port underline. Marks a slot without changing the glyph that states its state. */
    public static final char UNDERSCORE_HI = '▔';

    /** Port-slot pairs. Two cells per slot so a sixteen-slot comb is still readable. */
    public static final String PORT_UNKNOWN = "··";

    /** The alternate flicker phase for an unknown slot. Motion here means "not yet established". */
    public static final String PORT_UNKNOWN_ALT = ":·";

    public static final String PORT_CLOSED = "░░";

    public static final String PORT_OPEN = "██";

    public static final String PORT_FILTERED = "▒▒";

    /** Lattice node glyphs. Same weights as the layer fills, for the same greyscale reason. */
    public static final String NODE_HERE = "██";

    public static final String NODE_VISITED = "▒▒";

    public static final String NODE_SEEN = "░░";

    public static final String NODE_DARK = "··";

    public static final String NODE_OBJECTIVE = "╪╪";

    /** A trap the player has established. The double dagger is the client's hazard mark. */
    public static final String NODE_TRAP = "‡‡";

    public static final char DAGGER = '‡';

    /** Punctuation. {@code ·} is the separator this client uses everywhere a bullet is wanted. */
    public static final char BULLET = '·';

    /** ⚠ U+2212, the real minus. ASCII hyphen next to a digit reads as a range, not a debit. */
    public static final char MINUS = '−';

    public static final char ARROW_RIGHT = '→';

    public static final char ARROW_LEFT = '←';

    public static final char ARROW_UP = '↑';

    public static final char ARROW_DOWN = '↓';

    /**
     * The sixteen light box-drawing junctions, indexed by direction bits.
     *
     * <p>Bit 1 up, 2 down, 4 left, 8 right — so the table is indexed directly by the OR of whatever
     * arrived at a cell. {@link LatticeMap} routes several edges through one bus column, and a
     * junction table is what lets two edges cross a cell without either erasing the other. The four
     * single-direction entries are the half-length stubs {@code ╵ ╷ ╴ ╶}, which is what makes a dead
     * end look like a dead end rather than like a rule that was cut short.
     */
    private static final char[] JUNCTIONS = {
        ' ', '╵', '╷', '│',
        '╴', '┘', '┐', '┤',
        '╶', '└', '┌', '├',
        '─', '┴', '┬', '┼'
    };

    /** Direction bits for {@link #junction(int)}. */
    public static final int UP = 1;

    public static final int DOWN = 2;

    public static final int LEFT = 4;

    public static final int RIGHT = 8;

    // ── The grid ──────────────────────────────────────────────────────────────────────────────

    private final int rows;
    private final int cols;
    private final char[][] glyphs;
    private final int[][] inks;
    private final Label[] layers = new Label[INKS];

    public AsciiCanvas(int rows, int cols) {
        this.rows = Math.max(1, rows);
        this.cols = Math.max(1, cols);
        this.glyphs = new char[this.rows][this.cols];
        this.inks = new int[this.rows][this.cols];

        getStyleClass().add("es-viewport");
        String[] classes = {"es-viewport-dim", "es-viewport-live", "es-viewport-alarm"};
        for (int i = 0; i < INKS; i++) {
            layers[i] = new Label();
            layers[i].getStyleClass().add(classes[i]);
            layers[i].setWrapText(false);
            // The picture is one image assembled from three nodes; only the composite takes events.
            layers[i].setMouseTransparent(true);
            getChildren().add(layers[i]);
        }
        setAlignment(Pos.TOP_LEFT);
        clear();
        paint();
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    /** Blanks the grid. Every cell becomes a space at {@link #INK_DIM}. */
    public void clear() {
        for (char[] row : glyphs) {
            Arrays.fill(row, ' ');
        }
        for (int[] row : inks) {
            Arrays.fill(row, INK_DIM);
        }
    }

    /**
     * Writes one cell.
     *
     * <p>Silent out of range, exactly like {@code CoreCage.put}. Every caller here computes
     * coordinates from a layer count, a rank width or a band index; clamping at the edge keeps a
     * board that is one column too wide a cosmetic problem instead of an exception thrown from
     * inside a render on the FX thread.
     */
    public void put(int row, int col, char glyph, int ink) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return;
        }
        glyphs[row][col] = glyph;
        inks[row][col] = Math.max(INK_DIM, Math.min(INK_ALARM, ink));
    }

    public char at(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return ' ';
        }
        return glyphs[row][col];
    }

    public void text(int row, int col, String s, int ink) {
        if (s == null) {
            return;
        }
        for (int i = 0; i < s.length(); i++) {
            put(row, col + i, s.charAt(i), ink);
        }
    }

    /** Writes {@code s} centred in the span {@code [col, col + width)}, clipped rather than wrapped. */
    public void centre(int row, int col, int width, String s, int ink) {
        if (s == null || width <= 0) {
            return;
        }
        String clipped = s.length() > width ? s.substring(0, width) : s;
        text(row, col + (width - clipped.length()) / 2, clipped, ink);
    }

    /** Writes {@code s} so it ends at {@code col + width - 1}. Right-aligned readouts, marks, counts. */
    public void right(int row, int col, int width, String s, int ink) {
        if (s == null || width <= 0) {
            return;
        }
        String clipped = s.length() > width ? s.substring(s.length() - width) : s;
        text(row, col + width - clipped.length(), clipped, ink);
    }

    /** A double-ruled frame. The outer edge of a surface — never used for anything inside one. */
    public void box(int row, int col, int height, int width, int ink) {
        if (height < 2 || width < 2) {
            return;
        }
        put(row, col, BOX_TL, ink);
        put(row, col + width - 1, BOX_TR, ink);
        put(row + height - 1, col, BOX_BL, ink);
        put(row + height - 1, col + width - 1, BOX_BR, ink);
        for (int c = col + 1; c < col + width - 1; c++) {
            put(row, c, BOX_H, ink);
            put(row + height - 1, c, BOX_H, ink);
        }
        for (int r = row + 1; r < row + height - 1; r++) {
            put(r, col, BOX_V, ink);
            put(r, col + width - 1, BOX_V, ink);
        }
    }

    /** A divider across a boxed surface: {@code ╟────╢}. */
    public void rule(int row, int col, int width, int ink) {
        rule(row, col, width, ink, false);
    }

    /**
     * A divider, optionally the heavy form.
     *
     * @param live true for the divider that borders the active layer. §4.2 gives the active band a
     *     {@code ═} frame, which is what makes "where am I" answerable at a glance without spending
     *     the accent on it — D-7 rations amber to one element and it is not this one.
     */
    public void rule(int row, int col, int width, int ink, boolean live) {
        if (width < 2) {
            return;
        }
        put(row, col, live ? TEE_L_LIVE : TEE_L, ink);
        put(row, col + width - 1, live ? TEE_R_LIVE : TEE_R, ink);
        char run = live ? BOX_H : LIGHT_H;
        for (int c = col + 1; c < col + width - 1; c++) {
            put(row, c, run, ink);
        }
    }

    public void fill(int row, int col, int height, int width, char glyph, int ink) {
        for (int r = row; r < row + height; r++) {
            for (int c = col; c < col + width; c++) {
                put(r, c, glyph, ink);
            }
        }
    }

    /**
     * Fills a span with a repeating pattern, rotated by {@code phase} characters.
     *
     * <p>The rotation is the whole of the breach viewport's motion, and it is
     * {@code Substrate}'s trick rather than a new one: a row of texture that steps <b>one whole
     * character cell</b> per tick and never interpolates. §5 permits step timing only, and a
     * character grid has no sub-cell state to tween through even if it did.
     */
    public void pattern(int row, int col, int width, String repeating, int phase, int ink) {
        if (repeating == null || repeating.isEmpty() || width <= 0) {
            return;
        }
        int period = repeating.length();
        // floorMod, because a negative phase is a perfectly reasonable thing for a caller to have
        // computed and Java's % would hand back a negative index.
        int start = Math.floorMod(phase, period);
        for (int i = 0; i < width; i++) {
            put(row, col + i, repeating.charAt((start + i) % period), ink);
        }
    }

    /**
     * The light box-drawing character for a set of direction bits.
     *
     * <p>With {@link #bitsOf} this is a <b>merge</b> rather than an overwrite, which is what lets
     * {@link LatticeMap} route several edges through one bus column: two edges that cross produce
     * {@code ┼} instead of one erasing the other. The failure it prevents is exactly
     * {@code CoreCage}'s z-buffer story — draw in index order with no merge rule and the last writer
     * wins, which is whichever the loop happened to reach, and the picture silently loses its
     * structure while still looking like a picture.
     *
     * @param bits an OR of {@link #UP}, {@link #DOWN}, {@link #LEFT}, {@link #RIGHT}
     */
    public static char junction(int bits) {
        return JUNCTIONS[bits & 0x0F];
    }

    /** The inverse of {@link #junction}: which directions a drawn cell already carries. */
    public static int bitsOf(char glyph) {
        for (int i = 0; i < JUNCTIONS.length; i++) {
            if (JUNCTIONS[i] == glyph) {
                return i;
            }
        }
        return 0;
    }

    /** Splits the grid across the three overlaid labels. See the class comment. */
    public void paint() {
        StringBuilder[] out = new StringBuilder[INKS];
        for (int i = 0; i < INKS; i++) {
            out[i] = new StringBuilder(rows * (cols + 1));
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int ink = inks[r][c];
                for (int i = 0; i < INKS; i++) {
                    out[i].append(i == ink ? glyphs[r][c] : ' ');
                }
            }
            for (int i = 0; i < INKS; i++) {
                out[i].append('\n');
            }
        }
        for (int i = 0; i < INKS; i++) {
            layers[i].setText(out[i].toString());
        }
    }

    /**
     * The current grid as text — the test seam, and the way to iterate the artwork without a window.
     *
     * <p>Copied from {@code CoreCage.frame()} on purpose: an ASCII renderer whose output can only be
     * inspected by launching the client is an ASCII renderer nobody will tune.
     */
    public String frame() {
        StringBuilder out = new StringBuilder(rows * (cols + 1));
        for (char[] row : glyphs) {
            out.append(new String(row)).append('\n');
        }
        return out.toString();
    }

    /** The ink level of every cell, row-major. Test seam: proves alarm is where it is supposed to be. */
    public int[][] inkFrame() {
        int[][] copy = new int[rows][];
        for (int r = 0; r < rows; r++) {
            copy[r] = inks[r].clone();
        }
        return copy;
    }
}
