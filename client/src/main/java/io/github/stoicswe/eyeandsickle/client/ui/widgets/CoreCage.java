package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.Locale;
import java.util.Random;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * A slowly turning cutaway of the rig's own compute cage, drawn in characters.
 *
 * <h2>Instrumentation, not a screensaver</h2>
 *
 * Two hexagonal plates joined by six posts. <b>Each post is one bank of the rig's cores, and a post
 * is amber for exactly as long as that bank is self-mining</b> — which makes this a second view of
 * the number the cycle grid beside it is already showing, rather than decoration that happens to
 * share a panel. It is also the only amber in the render, which is the single use §2.1 permits:
 * "live/earning data only".
 *
 * <p>Everything else is the cold grey ramp, and depth is carried entirely by <em>glyph choice</em>
 * — near edges are box-drawing, far edges decay to dots. That is forced rather than chosen: the
 * palette has one accent and no second hue, so a render that wanted colour depth could not have it.
 * It turns out to be the better constraint. A wireframe shaded by character density reads as a
 * service manual, which is what the fiction wants.
 *
 * <h2>The horror is on a timer the player controls</h2>
 *
 * As personal heat rises the render degrades: edges drop out, the caption's drift figure stops
 * reading zero, and past the highest band <b>something appears inside the cage that is not part of
 * it</b>. That is the one piece grafted from the design this beat in review — an iris, in the middle
 * of the player's own hardware. It is not random and it is not atmosphere: it appears when and only
 * when the Eye is actually paying attention, so a player who keeps their heat down never sees it,
 * and a player who does not will not be able to un-see it.
 *
 * <h2>Two labels, not 500 nodes</h2>
 *
 * Colour has to vary per character, and JavaFX has no styled-text-run primitive cheaper than
 * {@link javafx.scene.text.TextFlow}. Because the face is monospace, two overlaid {@link Label}s
 * align exactly — one carrying the grey glyphs with spaces where the amber ones go, one carrying the
 * inverse. Two nodes redrawn on a slow timer, instead of a node per cell.
 *
 * <h2>Motion</h2>
 *
 * §5, step timing only. Each frame is a discrete recomputation at {@value #FRAME_MS}ms — deliberately
 * slow, both because a fast ASCII tumble reads as a screensaver and because 48 steps of visible
 * stepping is what makes it look like a machine redrawing rather than an animation playing. Under
 * reduced motion it holds one frame.
 */
public final class CoreCage extends StackPane {

    private static final int COLS = 36;
    /**
     * ⚠ Sized to the cage, with no blank rows above it (2026-07-30).
     *
     * <p>It was 14, and the projection put the top plate at {@code ROWS/2 - halfHeight*0.5} = row 2
     * — so the widget reserved two empty rows above the drawing and one below. In the rig monitor's
     * split that read as the instrument starting lower than the cell field beside it, even though
     * the two nodes were laid out at exactly the same y (measured: delta 0.0). The gap was inside
     * the art, not in the layout, and no alignment change could have fixed it.
     *
     * <p>⚠ Trimming is only safe because the blank rows are CONSTANT. {@code yaw} enters the
     * projection through {@code x} and {@code z} alone — the plates are horizontal and viewed at a
     * fixed elevation — so the topmost and bottommost drawn rows do not move as the cage turns. A
     * render whose extent varied with rotation would bob against the grid instead.
     */
    private static final int ROWS = 10;

    /** 48 steps to a revolution at this period is a ~14s turn. Slow on purpose — see the class doc. */
    private static final double FRAME_MS = 300;

    private static final int STEPS = 48;

    /** The six core banks. Also the six posts, and the six vertices of each plate. */
    private static final int BANKS = 6;

    private final Label structure = new Label();
    private final Label live = new Label();
    private final Label caption = Ui.micro("");
    private final Random random = new Random();

    private final char[][] glyphs = new char[ROWS][COLS];
    private final boolean[][] hot = new boolean[ROWS][COLS];

    /**
     * Depth per cell, so nearer geometry wins.
     *
     * <p>⚠ Without this the render is drawn in index order and the LAST edge to touch a cell wins,
     * which is whichever the loop happened to reach — so the far side of the cage painted over the
     * near side. Two posts 180° apart project to the same column, so the visible symptom was the
     * near posts disappearing entirely and the cage reading as an empty frame. A painter's
     * algorithm would also work; a z-buffer is two lines and does not need the edges sorted.
     */
    private final double[][] depth = new double[ROWS][COLS];

    private int step;
    private int earningBanks;
    private double heat;
    private AutoCloseable ticker;

    public CoreCage() {
        structure.getStyleClass().add("es-cage");
        live.getStyleClass().add("es-cage-live");
        caption.getStyleClass().add("es-cage-caption");

        javafx.scene.layout.VBox column = new javafx.scene.layout.VBox(UiTokens.SPACE_2);
        StackPane art = new StackPane(structure, live);
        art.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        column.getChildren().addAll(art, caption);
        getChildren().add(column);
        setAlignment(javafx.geometry.Pos.TOP_LEFT);

        ticker = Pulse.shared().animate(FRAME_MS, this::advance);
    }

    /**
     * @param selfMiningCycles cycles committed to self-mining
     * @param totalCycles the rig's capacity
     * @param personalHeat 0–100. Drives the decay, and eventually what appears inside the cage
     */
    public void show(long selfMiningCycles, long totalCycles, int personalHeat) {
        this.earningBanks =
                totalCycles <= 0 ? 0 : (int) Math.round(BANKS * Math.min(1.0, selfMiningCycles / (double) totalCycles));
        this.heat = Math.max(0, Math.min(1, personalHeat / 100.0d));
        render();
    }

    /**
     * Turns the cage — but only while the rig is doing something.
     *
     * <p>An idle rig holds its frame. That is the difference between a gauge and a screensaver, and
     * it is the sharpest note the design review produced: a thing that keeps moving when nothing is
     * happening is decoration, and a thing that stops is an instrument. A player glancing at a still
     * cage has learned something true — the rig is earning nothing and nobody is looking at it —
     * without reading a single figure.
     */
    private void advance() {
        if (earningBanks == 0 && heat <= 0) {
            return;
        }
        step = (step + 1) % STEPS;
        render();
    }

    private void render() {
        for (char[] row : glyphs) {
            java.util.Arrays.fill(row, ' ');
        }
        for (boolean[] row : hot) {
            java.util.Arrays.fill(row, false);
        }
        for (double[] row : depth) {
            java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
        }

        double yaw = step * (2 * Math.PI / STEPS);
        double radius = 12;
        // Tall enough that the two plates read as two plates. At 4.4 the cage projected to about
        // four rows total and looked like a flat grille rather than a cage with a volume in it.
        double halfHeight = 9.0;

        double[][] top = new double[BANKS][];
        double[][] bottom = new double[BANKS][];
        for (int i = 0; i < BANKS; i++) {
            double a = yaw + i * (2 * Math.PI / BANKS);
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            top[i] = project(x, halfHeight, z);
            bottom[i] = project(x, -halfHeight, z);
        }

        for (int i = 0; i < BANKS; i++) {
            int j = (i + 1) % BANKS;
            // The plates. Never amber: a plate is structure, and structure does not earn.
            line(top[i], top[j], false);
            line(bottom[i], bottom[j], false);
            // The posts. Amber for exactly the banks that are self-mining, and nothing else in this
            // render is ever amber (§2.1).
            line(top[i], bottom[i], i < earningBanks);
        }
        for (int i = 0; i < BANKS; i++) {
            node(top[i]);
            node(bottom[i]);
        }

        if (heat > 0.8) {
            iris();
        }
        decay();
        paint();
    }

    /**
     * Orthographic projection with the monospace aspect corrected.
     *
     * <p>⚠ The {@code 0.5} is the whole reason this looks like a hexagonal cage rather than an egg.
     * A character cell is roughly twice as tall as it is wide, so a render that treats the grid as
     * square squashes every vertical dimension by half. Forgetting it is the single most common way
     * an ASCII 3D render goes wrong, and it is invisible until you compare against a real circle.
     */
    private double[] project(double x, double y, double z) {
        double cx = COLS / 2.0;
        // ⚠ Derived from the cage's own half-height rather than from ROWS, so the top plate lands on
        // row 0. Deriving it from ROWS/2 is what left the blank band above the drawing, and it would
        // silently come back the moment ROWS changed again.
        return new double[] {cx + x, CENTRE_ROW - y * 0.5, z};
    }

    /** The row the cage's waist projects to. Puts the top plate on row 0 — see {@link #ROWS}. */
    private static final double CENTRE_ROW = 4.5;

    /** A depth-shaded line between two projected points. */
    private void line(double[] a, double[] b, boolean amber) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0) {
            return;
        }
        char glyph = glyphFor(dx, dy);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double z = a[2] + (b[2] - a[2]) * t;
            put((int) Math.round(a[1] + dy * t), (int) Math.round(a[0] + dx * t), shade(glyph, z), amber, z);
        }
    }

    /** Box-drawing for the direction, so the shape reads as drawn rather than as sampled. */
    private static char glyphFor(double dx, double dy) {
        if (Math.abs(dx) < 0.6) {
            return '│';
        }
        if (Math.abs(dy) < 0.6) {
            return '─';
        }
        return (dx > 0) == (dy > 0) ? '╲' : '╱';
    }

    /**
     * Depth as glyph weight.
     *
     * <p>The far half of the cage decays to dots — which is not only a depth cue but the reason the
     * render stays legible at 36 columns. Drawing both halves at full weight produces the
     * characteristic ASCII-3D mush where the back of the object is indistinguishable from the front.
     */
    private static char shade(char glyph, double z) {
        if (z > 5) {
            return glyph;
        }
        if (z > -6) {
            return switch (glyph) {
                case '│' -> ':';
                case '─' -> '-';
                case '╲' -> '\\';
                default -> '/';
            };
        }
        return '·';
    }

    /**
     * A cage vertex, weighted by depth.
     *
     * <p>⚠ Was {@code ▮ ▪ ▫}, none of which is in either bundled TTF — the nodes were drawn by a
     * host-OS fallback. These three are verified present in IBM Plex, which is the face this widget
     * is pinned to. See {@code GlyphCoverageTest}.
     */
    private void node(double[] p) {
        char glyph = p[2] > 4 ? '█' : p[2] > -4 ? '▄' : '·';
        // Biased slightly forward so a vertex always beats the edges meeting at it — otherwise a
        // post drawn after its own endpoint erases the joint and the cage loses its corners.
        put((int) Math.round(p[1]), (int) Math.round(p[0]), glyph, false, p[2] + 0.5);
    }

    /**
     * What is in the cage at high heat.
     *
     * <p>Deliberately drawn <em>after</em> the structure and over it, so it reads as something
     * occupying the cage rather than as part of it. It only exists above the Named-hacker threshold
     * — a player who manages their heat never sees this, which is what makes it mean something when
     * it appears.
     */
    private void iris() {
        int cx = COLS / 2;
        // The cage's waist, the same one the projection uses — the iris sits inside the cage, so a
        // separately-derived centre would drift away from it the moment either constant moved.
        int cy = (int) Math.round(CENTRE_ROW);
        double open = (heat - 0.8) / 0.2;
        int r = (int) Math.round(1 + open * 2);
        for (int y = -r; y <= r; y++) {
            for (int x = -r * 2; x <= r * 2; x++) {
                double d = Math.hypot(x / 2.0, y);
                if (d <= r) {
                    // Nearer than any cage geometry: it is inside the cage and in front of the far
                    // half, and it must not be occluded by the structure it is sitting in.
                    put(cy + y, cx + x, d < r * 0.45 ? '█' : d < r * 0.75 ? '▓' : '▒', false, Double.MAX_VALUE);
                }
            }
        }
    }

    /**
     * Heat eats the render.
     *
     * <p>Dropouts rather than added noise: a rig under attention loses signal, it does not gain
     * static. Cells go blank, so the cage looks like it is being observed through something
     * intermittent — and the effect scales continuously from "nothing" at zero heat, which is the
     * only setting at which this instrument should look healthy.
     */
    private void decay() {
        if (heat <= 0) {
            return;
        }
        int dropouts = (int) Math.round(heat * heat * 70);
        for (int i = 0; i < dropouts; i++) {
            int r = random.nextInt(ROWS);
            int c = random.nextInt(COLS);
            if (glyphs[r][c] != ' ') {
                glyphs[r][c] = ' ';
                hot[r][c] = false;
            }
        }
    }

    private void put(int row, int col, char glyph, boolean amber, double z) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return;
        }
        if (z < depth[row][col]) {
            return;
        }
        depth[row][col] = z;
        glyphs[row][col] = glyph;
        hot[row][col] = amber;
    }

    /** Splits the grid into the two overlaid layers. See the class comment. */
    private void paint() {
        StringBuilder grey = new StringBuilder(ROWS * (COLS + 1));
        StringBuilder amber = new StringBuilder(ROWS * (COLS + 1));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean isHot = hot[r][c];
                grey.append(isHot ? ' ' : glyphs[r][c]);
                amber.append(isHot ? glyphs[r][c] : ' ');
            }
            grey.append('\n');
            amber.append('\n');
        }
        structure.setText(grey.toString());
        live.setText(amber.toString());

        // The caption is the readout, and its last field is the one that stops being reassuring.
        caption.setText(Ui.upper(String.format(
                Locale.ROOT,
                "az %03d° · bank %d/%d · drift %.2fmm",
                Math.round(step * (360.0 / STEPS)),
                earningBanks,
                BANKS,
                heat * 0.31)));
    }

    /** The current frame as text, for tests and for iterating the artwork without a window. */
    public String frame() {
        StringBuilder out = new StringBuilder();
        for (char[] row : glyphs) {
            out.append(new String(row)).append('\n');
        }
        return out.toString();
    }

    /** Advances one step. Exposed so a harness can walk a full revolution deterministically. */
    public void tick() {
        advance();
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
