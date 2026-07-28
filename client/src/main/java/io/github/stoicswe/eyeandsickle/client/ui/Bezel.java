package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Draws {@link BezelStyle}'s casing in the margin around the deck.
 *
 * <h2>⚠ It draws in a margin and never over content</h2>
 *
 * This is condition 2 of the §9 amendment ({@link BezelStyle}), and it is structural rather than
 * maintained by hand: {@code DeckShell} insets the deck by {@link BezelStyle#margin()} and this node
 * paints only inside that inset. Nothing the player has to read is ever underneath it — most
 * importantly the top strip's compute readout, which is client pillar C2.
 *
 * <p>⚠ {@code setMouseTransparent(true)}, unconditionally. The casing sits above the deck in the
 * root StackPane so it is not clipped by it, which means without this it would swallow every click
 * in the outer band — including the window-drag handle and the resize grips, on an undecorated Stage
 * where those are the only way to move or size the window at all.
 *
 * <h2>Colours come from the stylesheet, never from here</h2>
 *
 * Every shape gets a style class and {@code theme.css} supplies the fill, so the casing re-colours
 * with the palette like everything else and §10 criterion 2 (no colour literal in any ui class)
 * holds. {@code UiContractTest} enforces that mechanically.
 */
public final class Bezel extends Region {

    private BezelStyle style = BezelStyle.OFF;

    public Bezel() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        getStyleClass().add("es-bezel");
    }

    public void setStyle(BezelStyle wanted) {
        this.style = wanted == null ? BezelStyle.OFF : wanted;
        setVisible(this.style != BezelStyle.OFF);
        requestLayout();
    }

    public BezelStyle style() {
        return style;
    }

    @Override
    protected void layoutChildren() {
        getChildren().clear();
        double w = getWidth();
        double h = getHeight();
        if (style == BezelStyle.OFF || w <= 0 || h <= 0) {
            return;
        }
        double m = style.margin();
        switch (style) {
            case HAIRLINE -> hairline(w, h, m);
            case BRACKETS -> brackets(w, h, m);
            case CASING -> casing(w, h, m);
            case LOOM -> loom(w, h, m);
            case RULE -> rule(w, h, m);
            default -> { }
        }
    }

    /** Two rules and a gap — what an instrument face does at its edge. */
    private void hairline(double w, double h, double m) {
        frame(1.5, 1.5, w - 3, h - 3, "es-bezel-rule");
        frame(m - 2, m - 2, w - 2 * (m - 2), h - 2 * (m - 2), "es-bezel-rule-inner");
    }

    /**
     * Corner brackets plus a tick at the middle of each run.
     *
     * <p>Open in the middle deliberately: a frame that closes on all four sides puts the interface
     * inside a picture, which is exactly what §9 objected to about a bezel in the first place.
     */
    private void brackets(double w, double h, double m) {
        double arm = Math.min(64, Math.min(w, h) / 5);
        double in = 2;
        // Four corners, two arms each.
        line(in, in, in + arm, in);
        line(in, in, in, in + arm);
        line(w - in - arm, in, w - in, in);
        line(w - in, in, w - in, in + arm);
        line(in, h - in, in + arm, h - in);
        line(in, h - in - arm, in, h - in);
        line(w - in - arm, h - in, w - in, h - in);
        line(w - in, h - in - arm, w - in, h - in);
        // A short centre tick on each run, so the open sides still read as edges.
        double tick = 10;
        line(w / 2 - tick, in, w / 2 + tick, in);
        line(w / 2 - tick, h - in, w / 2 + tick, h - in);
        line(in, h / 2 - tick, in, h / 2 + tick);
        line(w - in, h / 2 - tick, w - in, h / 2 + tick);
    }

    /**
     * The machine: band, notched corners, vents, fixings, a port block and a designator plate.
     *
     * <h2>⚠ Drawn as four edge rectangles plus four corner triangles, never as one shape with a hole</h2>
     *
     * A single rectangle with an inner cut-out needs an even-odd fill rule or a {@code Shape.subtract},
     * and both produce a node that has to be rebuilt on every resize anyway. This way each piece is a
     * plain rectangle and the corner geometry is explicit.
     *
     * <p>⚠ The detailing is <b>asymmetric</b>: vents along the top, ports down the left, a designator
     * bottom-right. Real equipment has a front, and a border with the same trim on all four sides
     * reads as a picture frame — which is exactly what §9 objected to about bezels. The asymmetry is
     * what makes it read as a fabricated object instead.
     */
    private void casing(double w, double h, double m) {
        band(0, 0, w, m);
        band(0, h - m, w, m);
        band(0, 0, m, h);
        band(w - m, 0, m, h);
        // The notch: a triangle cut back at 45° from each corner, matching §2.3's panel geometry.
        double cut = m * 1.6;
        triangle("es-bezel-notch", 0, 0, cut, 0, 0, cut);
        triangle("es-bezel-notch", w, 0, w - cut, 0, w, cut);
        triangle("es-bezel-notch", 0, h, cut, h, 0, h - cut);
        triangle("es-bezel-notch", w, h, w - cut, h, w, h - cut);

        // Vent slots along the top, in two banks with a gap. Cut short of the corner notches so a
        // slot never lands in the void triangle and reads as a stray mark.
        double slotW = 3;
        double gap = 4;
        double ventY = m * 0.32;
        double ventH = Math.max(3, m * 0.36);
        for (double x = cut + 18; x < w * 0.42; x += slotW + gap) {
            fill(x, ventY, slotW, ventH, "es-bezel-vent");
        }
        for (double x = w * 0.58; x < w - cut - 18; x += slotW + gap) {
            fill(x, ventY, slotW, ventH, "es-bezel-vent");
        }

        // Fixings: one small square inboard of each corner. Four, because that is how a panel is
        // actually held on, and their inset is what gives the band an apparent thickness.
        double fix = 4;
        double inset = m * 0.5 - fix / 2;
        for (double[] at : new double[][] {
                {cut + 6, inset}, {w - cut - 6 - fix, inset},
                {cut + 6, h - inset - fix}, {w - cut - 6 - fix, h - inset - fix}}) {
            fill(at[0], at[1], fix, fix, "es-bezel-fixing");
        }

        // A port block down the left flank: alternating wide and narrow sockets.
        double portX = m * 0.28;
        double portW = Math.max(4, m * 0.44);
        double y = h * 0.34;
        for (int i = 0; i < 6 && y < h * 0.72; i++) {
            double portH = i % 2 == 0 ? 9 : 5;
            fill(portX, y, portW, portH, "es-bezel-port");
            y += portH + 6;
        }

        // The designator plate, bottom right. A machine has a part number on it.
        double plateW = Math.min(72, w * 0.2);
        double plateH = Math.max(3, m * 0.3);
        fill(w - cut - 12 - plateW, h - m * 0.5 - plateH / 2, plateW, plateH, "es-bezel-plate");

        frame(m - 1, m - 1, w - 2 * (m - 1), h - 2 * (m - 1), "es-bezel-rule-inner");
    }

    /**
     * The loom: orthogonal cable runs with junctions and terminators.
     *
     * <p>Wiring dressed the way a harness is inside a real case — runs parallel to the edge, turns
     * at right angles, a junction pad where two meet and a terminator block at each end. No curves:
     * §9 has no vocabulary for one, and a dressed loom does not have any either.
     *
     * <p>⚠ Every run is inset a different amount so they read as separate cables rather than as a
     * thick line. Three at the same offset is a border; three at different offsets is a bundle.
     */
    private void loom(double w, double h, double m) {
        frame(0.5, 0.5, w - 1, h - 1, "es-bezel-rule");
        // Three cables, each on its own lane, each turning the corner it reaches.
        double[] lanes = {m * 0.30, m * 0.50, m * 0.70};
        for (int i = 0; i < lanes.length; i++) {
            double d = lanes[i];
            // Top run, turning down the right flank.
            cable(d, d, w - d, d);
            cable(w - d, d, w - d, h * (0.30 + 0.12 * i));
            terminator(w - d, h * (0.30 + 0.12 * i));
            // Bottom run, turning up the left flank.
            cable(d, h - d, w - d, h - d);
            cable(d, h - d, d, h * (0.70 - 0.12 * i));
            terminator(d, h * (0.70 - 0.12 * i));
        }
        // Junction pads where the bundle turns each corner — a harness is clamped at every bend.
        for (double[] at : new double[][] {{m * 0.5, m * 0.5}, {w - m * 0.5, m * 0.5},
                {m * 0.5, h - m * 0.5}, {w - m * 0.5, h - m * 0.5}}) {
            fill(at[0] - 4, at[1] - 4, 8, 8, "es-bezel-junction");
        }
        frame(m - 1, m - 1, w - 2 * (m - 1), h - 2 * (m - 1), "es-bezel-rule-inner");
    }

    /** A tick scale along all four edges, heavier every fifth mark. */
    private void rule(double w, double h, double m) {
        frame(0.5, 0.5, w - 1, h - 1, "es-bezel-rule");
        double step = 12;
        int i = 0;
        for (double x = 0; x <= w; x += step, i++) {
            double len = i % 5 == 0 ? m : m / 2;
            line(x, 0, x, len);
            line(x, h, x, h - len);
        }
        i = 0;
        for (double y = 0; y <= h; y += step, i++) {
            double len = i % 5 == 0 ? m : m / 2;
            line(0, y, len, y);
            line(w, y, w - len, y);
        }
    }

    // ------------------------------------------------------------------ primitives

    private void line(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.getStyleClass().add("es-bezel-rule");
        getChildren().add(l);
    }

    private void frame(double x, double y, double w, double h, String styleClass) {
        Rectangle r = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
        r.setFill(null);
        r.getStyleClass().add(styleClass);
        getChildren().add(r);
    }

    private void band(double x, double y, double w, double h) {
        Rectangle r = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
        r.getStyleClass().add("es-bezel-band");
        getChildren().add(r);
    }

    /** A filled rectangle in a named class — vents, fixings, ports, plates. */
    private void fill(double x, double y, double w, double h, String styleClass) {
        Rectangle r = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
        r.getStyleClass().add(styleClass);
        getChildren().add(r);
    }

    /** One run of cable. Thicker than a rule, so a loom does not read as a second border. */
    private void cable(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.getStyleClass().add("es-bezel-cable");
        getChildren().add(l);
    }

    /** The block a cable run ends in. A wire that simply stopped would read as a rendering fault. */
    private void terminator(double x, double y) {
        fill(x - 3, y - 2, 6, 4, "es-bezel-junction");
    }

    private void triangle(String styleClass, double... points) {
        Polygon p = new Polygon(points);
        p.getStyleClass().add(styleClass);
        getChildren().add(p);
    }
}
