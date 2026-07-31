package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode;
import java.util.List;
import java.util.Random;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * The desk's wallpaper: a sparse field of machine texture, drifting behind every window.
 *
 * <h2>What this is, in the design language's own terms</h2>
 *
 * It is <b>greeble at desk scale</b>, and deliberately nothing more.
 * {@code docs/design/ui-design-language.md} §4 fixes the greeble vocabulary as "hex quads, block
 * glyphs, dots, 4-digit serials, {@code //} marks" and this uses exactly that alphabet and no other
 * — which is also why every non-ASCII character here is already proven present in IBM Plex Mono by
 * {@link Greeble}. §4's other requirement carries over unchanged: <b>it must stay unreadable</b>.
 * The moment a fragment resolves into a word, players start reading it, and a decorative string
 * being read is a string that is lying to them.
 *
 * <h2>⚠ It stands close to two rules, and neither is violated by accident</h2>
 *
 * <ol>
 *   <li><b>§9 still cuts vignette and bezel outright</b>, and §9.1 permits scanlines only as an
 *       effect the player switches on. This is neither: it is content, not an artefact laid over
 *       content, and it is <em>sparse and two-dimensional</em> rather than a raster overlay. There
 *       is no edge darkening anywhere, and no full-width horizontal rule in the alphabet — the
 *       fragments are short and separated by long gaps. It is also why rows drift at <b>three
 *       different rates</b> ({@link #DRIFT_RATES}): a field that slid as one sheet would read as a
 *       scrolling raster, and parallax is what stops it. (Scanlines proper live in
 *       {@code ui/CrtOverlay}, off by default, which is the arrangement §9.1 requires.)
 *   <li><b>§4 budgets greeble at "roughly 10–15% of pixels".</b> Occupancy here is held near
 *       {@value #TARGET_OCCUPANCY_PERCENT}% of <em>cells</em> — see {@link #GAP_MIN}/{@link #GAP_MAX}
 *       — and {@link #BLANK_ROW_CHANCE} of rows carry nothing at all, on top of a style class whose
 *       opacity puts it barely above the void it sits on. The desk is not the whole screen and the
 *       strips already spend part of that budget, which is the other half of why this is kept low.
 * </ol>
 *
 * <h2>Motion, and why it is a setting</h2>
 *
 * §5 allows step and linear timing only, so the field moves in <b>whole character cells</b> — a row
 * jumps one column or it does not move; nothing interpolates and nothing tweens. Beyond the
 * aesthetic, ambient motion behind the entire interface is an accessibility surface: <b>WCAG 2.2.2
 * (Pause, Stop, Hide)</b> requires that moving content which starts automatically and runs for more
 * than five seconds can be paused by the user. {@link WallpaperMode} is that control, which is why
 * {@link WallpaperMode#STILL} exists as a state of its own rather than being folded into "off" — a player
 * who wants the texture but not the movement should not have to choose between them.
 *
 * <p>Reduced motion is handled for free and correctly: the ticker is registered through
 * {@link Pulse#animate}, which classifies it as <em>decoration</em>, so
 * {@code prefers-reduced-motion} paints one resting frame and then holds it — the same state
 * {@link WallpaperMode#STILL} produces. Nothing here needs to ask.
 *
 * <h2>It is mouse-transparent, and that is load-bearing</h2>
 *
 * ⚠ {@code DeskManager} drops window focus on a press whose {@code getTarget()} is the desk itself.
 * A backdrop covering the desk would become that target and silently break focus-dropping on bare
 * desk, so this node must never accept a mouse event. Set here rather than at the call site,
 * because the constraint belongs to the thing that would violate it.
 */
public final class Substrate extends Region {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * ⚠ Every non-ASCII glyph below is drawn from {@link Greeble}'s alphabet, which is verified
     * present in IBM Plex Mono, and {@code .es-substrate} is pinned to that face in
     * {@code theme.css} — asserted by {@code GlyphCoverageTest}. Do not add a character here
     * without checking it: a substituted glyph brings its own advance width, and a texture built
     * on character cells stops aligning the moment one character is wider than the rest.
     */
    private static final String BLOCKS = "▐▌▐";

    private static final String DOTS = "····";

    private static final String[] MARKS = {"//", "╞╞", "▚▚"};

    /** Roughly what fraction of cells carry a glyph. See the class comment on §4's budget. */
    private static final int TARGET_OCCUPANCY_PERCENT = 10;

    /** Blank run between fragments, in cells. The wide range is what keeps the field irregular. */
    private static final int GAP_MIN = 11;

    private static final int GAP_MAX = 42;

    /** How often a row carries nothing at all, so the field has vertical air as well as horizontal. */
    private static final double BLANK_ROW_CHANCE = 0.34;

    /**
     * The three drift rates, in ticks per one-cell step.
     *
     * <p>Parallax, and §9 compliance: a single rate would slide the whole field as one sheet, which
     * is the raster reading the rejection list forbids. Coprime-ish values so the rows do not fall
     * back into phase on a short cycle.
     */
    private static final int[] DRIFT_RATES = {1, 2, 3};

    /** How many ticks between regenerating one row outright. Rare, or the field flickers. */
    private static final int CHURN_EVERY = 23;

    /** Channel separation, in pixels, when aberration is on. One. See {@link #setAberration}. */
    private static final double ABERRATION_OFFSET = 1;

    private final Label field = new Label();

    /**
     * The chromatic-aberration layers: the same text, one pixel either side, in warm and cool.
     *
     * <p>This is where aberration is affordable in JavaFX — see {@code CrtOverlay}'s class comment
     * for why the whole scene cannot have it. The wallpaper is a text node, so separating channels
     * costs two more labels rather than a per-frame raster recomposite, and because the field is
     * dim to begin with the fringe reads as a fringe rather than as three overlapping fields.
     *
     * <p>They are painted <em>under</em> the base layer, so the base glyph shape stays crisp and the
     * colour leaks out at the edges — which is the way round a real convergence error looks.
     */
    private final Label warm = new Label();

    private final Label cool = new Label();

    private final Random random = new Random();

    private String[] rows = new String[0];
    private int[] phase = new int[0];
    private int[] rate = new int[0];
    private int cols;
    private int step;

    private double cellWidth;
    private double cellHeight;

    private WallpaperMode mode = WallpaperMode.DRIFT;
    private AutoCloseable ticker;

    public Substrate() {
        getStyleClass().add("es-substrate");
        field.getStyleClass().add("es-substrate-field");
        warm.getStyleClass().addAll("es-substrate-field", "es-substrate-warm");
        cool.getStyleClass().addAll("es-substrate-field", "es-substrate-cool");
        for (Label layer : List.of(warm, cool, field)) {
            layer.setWrapText(false);
            layer.setMouseTransparent(true);
            layer.setManaged(false);
        }
        warm.setVisible(false);
        cool.setVisible(false);
        // ⚠ See the class comment: a backdrop that takes mouse events breaks DeskManager's
        // click-bare-desk-to-drop-focus, which tests target identity rather than coordinates.
        setMouseTransparent(true);
        // Fringe first, base last: the crisp glyph sits on top and the colour leaks at its edges.
        getChildren().addAll(warm, cool, field);
        setMode(WallpaperMode.DRIFT);
    }

    /**
     * Chromatic aberration on the wallpaper.
     *
     * <p>Static — it separates channels, it does not move — so reduced motion leaves it alone. §5 is
     * about motion, and a convergence error is not motion.
     */
    public void setAberration(boolean on) {
        warm.setVisible(on);
        cool.setVisible(on);
    }

    /** Off, still, or drifting. Safe to call repeatedly with the same value. */
    public void setMode(WallpaperMode next) {
        this.mode = next == null ? WallpaperMode.DRIFT : next;
        setVisible(this.mode != WallpaperMode.OFF);
        stopTicker();
        if (this.mode == WallpaperMode.DRIFT) {
            // Decorative, so reduced motion freezes it after one frame — which is exactly STILL.
            ticker = Pulse.shared().animate(UiTokens.SUBSTRATE_DRIFT_MS, this::advance);
        } else if (this.mode == WallpaperMode.STILL) {
            // Painted once so the texture is present and simply does not move. Without this a
            // profile that starts in STILL would show a bare desk until something forced a layout.
            repaint();
        }
    }

    public WallpaperMode mode() {
        return mode;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        field.resizeRelocate(0, 0, w, h);
        // One pixel either way. The offset is the aberration; anything larger stops reading as a
        // convergence error and starts reading as three wallpapers.
        warm.resizeRelocate(-ABERRATION_OFFSET, 0, w, h);
        cool.resizeRelocate(ABERRATION_OFFSET, 0, w, h);
        if (mode == WallpaperMode.OFF || w <= 0 || h <= 0) {
            return;
        }
        measure();
        if (cellWidth <= 0 || cellHeight <= 0) {
            return;
        }
        int wantCols = (int) Math.ceil(w / cellWidth);
        int wantRows = (int) Math.ceil(h / cellHeight);
        if (wantCols != cols || wantRows != rows.length) {
            regenerate(wantCols, wantRows);
            repaint();
        }
    }

    /**
     * Reads the cell size back out of the font CSS actually applied.
     *
     * <p>Measured rather than assumed. The face and its size live in {@code theme.css} (§7.2 keeps
     * colour there, and the texture-pinning rule keeps the family there too), so hard-coding an
     * advance width in Java would be a second source of truth that drifts silently the first time
     * the stylesheet changes — and the symptom would be a field that no longer fills the desk, or
     * one that overruns it, neither of which fails a build.
     */
    private void measure() {
        if (cellWidth > 0 && cellHeight > 0) {
            return;
        }
        field.applyCss();
        Text probe = new Text("0");
        probe.setFont(field.getFont());
        cellWidth = probe.getLayoutBounds().getWidth();
        cellHeight = probe.getLayoutBounds().getHeight();
    }

    private void regenerate(int wantCols, int wantRows) {
        cols = Math.max(1, wantCols);
        rows = new String[Math.max(1, wantRows)];
        phase = new int[rows.length];
        rate = new int[rows.length];
        for (int r = 0; r < rows.length; r++) {
            rows[r] = makeRow();
            phase[r] = random.nextInt(cols);
            rate[r] = DRIFT_RATES[random.nextInt(DRIFT_RATES.length)];
        }
    }

    /** One row of texture, exactly {@link #cols} cells wide so rotation wraps cleanly. */
    private String makeRow() {
        if (random.nextDouble() < BLANK_ROW_CHANCE) {
            return " ".repeat(cols);
        }
        StringBuilder out = new StringBuilder(cols + 8);
        while (out.length() < cols) {
            out.append(" ".repeat(GAP_MIN + random.nextInt(GAP_MAX - GAP_MIN)));
            if (out.length() < cols) {
                out.append(fragment());
            }
        }
        // Truncated rather than padded: the tail of a fragment clipped at the wrap is the same
        // thing §4 asks of the greeble strip, which is clipped at the edge rather than ellipsised.
        return out.substring(0, cols);
    }

    /** One fragment from §4's greeble vocabulary. Nothing else belongs here. */
    private String fragment() {
        double roll = random.nextDouble();
        if (roll < 0.32) {
            StringBuilder quad = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                quad.append(HEX[random.nextInt(HEX.length)]);
            }
            return quad.toString();
        }
        if (roll < 0.56) {
            return DOTS.substring(0, 1 + random.nextInt(DOTS.length()));
        }
        if (roll < 0.76) {
            return BLOCKS.substring(0, 1 + random.nextInt(BLOCKS.length()));
        }
        if (roll < 0.92) {
            return Integer.toString(1000 + random.nextInt(8999));
        }
        return MARKS[random.nextInt(MARKS.length)];
    }

    /** One tick: some rows step one cell, and occasionally one row is replaced outright. */
    private void advance() {
        if (mode != WallpaperMode.DRIFT || rows.length == 0 || cols <= 0) {
            return;
        }
        step++;
        for (int r = 0; r < rows.length; r++) {
            if (step % rate[r] == 0) {
                phase[r] = (phase[r] + 1) % cols;
            }
        }
        if (step % CHURN_EVERY == 0) {
            int r = random.nextInt(rows.length);
            rows[r] = makeRow();
        }
        repaint();
    }

    private void repaint() {
        if (rows.length == 0 || cols <= 0) {
            return;
        }
        StringBuilder out = new StringBuilder((cols + 1) * rows.length);
        for (int r = 0; r < rows.length; r++) {
            if (r > 0) {
                out.append('\n');
            }
            String row = rows[r];
            int k = phase[r] % row.length();
            out.append(row, k, row.length()).append(row, 0, k);
        }
        String text = out.toString();
        field.setText(text);
        // The fringe layers carry the same text whether or not they are visible, so switching
        // aberration on never shows a frame of stale or empty texture behind the base layer.
        warm.setText(text);
        cool.setText(text);
    }

    private void stopTicker() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
    }

    public void dispose() {
        stopTicker();
    }

    /** Test seam: how many cells carry a glyph, as a percentage of the field. */
    int occupancyPercent() {
        if (rows.length == 0 || cols <= 0) {
            return 0;
        }
        long filled = 0;
        for (String row : rows) {
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) != ' ') {
                    filled++;
                }
            }
        }
        return (int) Math.round(100.0 * filled / (rows.length * (double) cols));
    }

    /** Test seam: the rendered field, rows separated by newlines. */
    String rendered() {
        return field.getText();
    }
}
