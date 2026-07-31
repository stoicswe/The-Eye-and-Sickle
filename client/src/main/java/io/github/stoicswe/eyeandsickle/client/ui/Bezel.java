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

    /**
     * The lamps on {@link BezelStyle#TERMINAL}, re-collected on every layout.
     *
     * <p>⚠ Held as a field and rebuilt in {@code layoutChildren}, because that method clears the
     * children — a ticker holding references from a previous pass would be restyling detached nodes
     * forever while the visible ones sat still.
     */
    private final java.util.List<Rectangle> lamps = new java.util.ArrayList<>();

    private AutoCloseable lampTicker;
    private int lampFrame;

    public Bezel() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        getStyleClass().add("es-bezel");
    }

    public void setStyle(BezelStyle wanted) {
        this.style = wanted == null ? BezelStyle.OFF : wanted;
        setVisible(this.style != BezelStyle.OFF);
        // ⚠ Pulse.animate, NOT Pulse.every. The lamps are decoration, so the decorative channel is
        // what `prefers-reduced-motion` is allowed to freeze — see §9.2's third condition. `every`
        // would keep them blinking for a player who asked the whole client to stop moving.
        stopLamps();
        if (this.style == BezelStyle.TERMINAL) {
            lampTicker = Pulse.shared().animate(700, this::advanceLamps);
        }
        requestLayout();
    }

    private void advanceLamps() {
        lampFrame++;
        for (int i = 0; i < lamps.size(); i++) {
            // Each lamp on its own prime-ish period, so the row reads as independent indicators
            // rather than as a marquee. A single shared phase looks like a chase light, which is a
            // toy; unrelated periods look like a machine with several things going on.
            boolean lit = ((lampFrame + i * 3) / (2 + i % 3)) % 2 == 0;
            lamps.get(i).getStyleClass().remove("es-bezel-lamp-off");
            if (!lit) {
                lamps.get(i).getStyleClass().add("es-bezel-lamp-off");
            }
        }
    }

    private void stopLamps() {
        if (lampTicker != null) {
            try {
                lampTicker.close();
            } catch (Exception ignored) {
                // Nothing to recover: the ticker is going away and a failed unsubscribe is not
                // something a player can act on.
            }
            lampTicker = null;
        }
    }

    /** Stops the lamp ticker. Called by {@code DeckShell.dispose}. */
    public void dispose() {
        stopLamps();
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
            case GOTHIC -> gothic(w, h, m);
            case TERMINAL -> terminal(w, h, m);
            case CHROME_31 -> chrome31(w, h, m);
            case MOTIF -> motif(w, h, m);
            case RULE -> rule(w, h, m);
            default -> {}
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
            {cut + 6, h - inset - fix}, {w - cut - 6 - fix, h - inset - fix}
        }) {
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
        for (double[] at : new double[][] {
            {m * 0.5, m * 0.5}, {w - m * 0.5, m * 0.5}, {m * 0.5, h - m * 0.5}, {w - m * 0.5, h - m * 0.5}
        }) {
            fill(at[0] - 4, at[1] - 4, 8, 8, "es-bezel-junction");
        }
        frame(m - 1, m - 1, w - 2 * (m - 1), h - 2 * (m - 1), "es-bezel-rule-inner");
    }

    /**
     * Gothic industrial: deep plate, rivet lines, buttressed corners, hazard chevrons.
     *
     * <p>⚠ Genre rather than iconography — rivets, plate and chevrons, and deliberately none of the
     * protected emblems the obvious reference is known for. Construction is free to borrow; insignia
     * are not.
     */
    private void gothic(double w, double h, double m) {
        band(0, 0, w, m);
        band(0, h - m, w, m);
        band(0, 0, m, h);
        band(w - m, 0, m, h);

        // Buttresses: each corner steps inward twice, which is what gives the plate its weight.
        double b1 = m * 1.9;
        double b2 = m * 1.15;
        for (double[] c : new double[][] {{0, 0, 1, 1}, {w, 0, -1, 1}, {0, h, 1, -1}, {w, h, -1, -1}}) {
            double x = c[0];
            double y = c[1];
            double sx = c[2];
            double sy = c[3];
            fill(Math.min(x, x + sx * b1), Math.min(y, y + sy * b2), b1, b2, "es-bezel-buttress");
            fill(Math.min(x, x + sx * b2), Math.min(y, y + sy * b1), b2, b1, "es-bezel-buttress");
        }

        // Rivet lines following each edge, set in from the outer face.
        double rivet = 3;
        double inset = m * 0.24;
        double step = 22;
        for (double x = b1 + 10; x < w - b1 - 10; x += step) {
            fill(x, inset, rivet, rivet, "es-bezel-rivet");
            fill(x, h - inset - rivet, rivet, rivet, "es-bezel-rivet");
        }
        for (double y = b1 + 10; y < h - b1 - 10; y += step) {
            fill(inset, y, rivet, rivet, "es-bezel-rivet");
            fill(w - inset - rivet, y, rivet, rivet, "es-bezel-rivet");
        }

        // Hazard chevrons down the flanks. §2.3 allows one diagonal per screen and this is it —
        // the deck's own hazard band lives inside the viewport, which is a different surface.
        double chev = 13;
        for (double y = h * 0.30; y < h * 0.70; y += chev * 2) {
            triangle("es-bezel-chevron", m * 0.55, y, m * 0.95, y + chev, m * 0.55, y + chev * 2);
            triangle("es-bezel-chevron", w - m * 0.55, y, w - m * 0.95, y + chev, w - m * 0.55, y + chev * 2);
        }
        frame(m - 2, m - 2, w - 2 * (m - 2), h - 2 * (m - 2), "es-bezel-rule");
        frame(m - 1, m - 1, w - 2 * (m - 1), h - 2 * (m - 1), "es-bezel-rule-inner");
    }

    /**
     * A hardware front panel: a grille, toggle switches, and a row of status lamps that blink.
     *
     * <p>The lamps are collected into {@link #lamps} for the ticker. Everything else is inert.
     */
    private void terminal(double w, double h, double m) {
        lamps.clear();
        band(0, 0, w, m);
        band(0, h - m, w, m);
        band(0, 0, m, h);
        band(w - m, 0, m, h);

        // Status lamps along the top left, in a recessed bay.
        double bayX = 18;
        double bayW = 190;
        double bayH = m * 0.52;
        double bayY = (m - bayH) / 2;
        fill(bayX - 6, bayY - 4, bayW + 12, bayH + 8, "es-bezel-bay");
        double lamp = Math.max(6, bayH * 0.5);
        for (int i = 0; i < 8; i++) {
            Rectangle led = new Rectangle(bayX + i * (lamp + 12), bayY + (bayH - lamp) / 2, lamp, lamp);
            led.getStyleClass().add("es-bezel-lamp");
            getChildren().add(led);
            lamps.add(led);
        }
        advanceLamps();

        // Toggle switches top right: a recessed slot with the paddle sitting up or down.
        for (int i = 0; i < 4; i++) {
            double sx = w - 40 - i * 30;
            fill(sx, bayY, 12, bayH, "es-bezel-switch-well");
            fill(sx + 2, i % 2 == 0 ? bayY + 2 : bayY + bayH * 0.5, 8, bayH * 0.45, "es-bezel-switch");
        }

        // A grille along the bottom: long thin slots, the width of the machine.
        double slotH = Math.max(3, m * 0.16);
        double gy = h - m + (m - slotH * 5) / 2;
        for (int r = 0; r < 3; r++) {
            fill(w * 0.28, gy + r * (slotH + 3), w * 0.44, slotH, "es-bezel-vent");
        }

        // Port sockets down the left flank, as on the plain casing.
        double portW = Math.max(5, m * 0.34);
        double y = h * 0.36;
        for (int i = 0; i < 5 && y < h * 0.70; i++) {
            fill(m * 0.33, y, portW, 8, "es-bezel-port");
            y += 16;
        }
        frame(m - 1, m - 1, w - 2 * (m - 1), h - 2 * (m - 1), "es-bezel-rule-inner");
    }

    /**
     * Beveled chrome in the 3.1 idiom.
     *
     * <p>⚠ The bevel is <b>brightness</b>, never shadow — §2.1's rule, and the reason this style is
     * legal at all. A light top-left edge against a dark bottom-right one is depth done the way the
     * design language already permits; a {@code DropShadow} would not be.
     */
    private void chrome31(double w, double h, double m) {
        band(0, 0, w, m);
        band(0, h - m, w, m);
        band(0, 0, m, h);
        band(w - m, 0, m, h);
        raised(0, 0, w, h);
        // The inner well the "client area" sits in: the same bevel, reversed.
        sunken(m - 4, m - 4, w - 2 * (m - 4), h - 2 * (m - 4));

        // Title bar across the top, inset within the frame.
        double barH = m * 0.55;
        double barY = (m - barH) / 2;
        fill(m - 2, barY, w - 2 * (m - 2), barH, "es-bezel-titlebar");

        // Control box left, min/max right — drawn as shapes, never as text, so no glyph the
        // bundled fonts might not carry can sneak into the casing (GlyphCoverageTest).
        double box = barH - 6;
        fill(m + 2, barY + 3, box, box, "es-bezel-button");
        raised(m + 2, barY + 3, box, box);
        fill(m + 2 + box * 0.2, barY + 3 + box * 0.42, box * 0.6, box * 0.16, "es-bezel-glyph");
        for (int i = 0; i < 2; i++) {
            double bx = w - m - 4 - (i + 1) * (box + 4);
            fill(bx, barY + 3, box, box, "es-bezel-button");
            raised(bx, barY + 3, box, box);
            if (i == 0) {
                // Maximise: an outlined square.
                frame(bx + box * 0.24, barY + 3 + box * 0.22, box * 0.52, box * 0.56, "es-bezel-glyph-line");
            } else {
                // Minimise: a bar on the baseline.
                fill(bx + box * 0.24, barY + 3 + box * 0.66, box * 0.52, box * 0.14, "es-bezel-glyph");
            }
        }
    }

    /** The Motif frame: double bevel, segmented border with corner grips, square buttons. */
    private void motif(double w, double h, double m) {
        band(0, 0, w, m);
        band(0, h - m, w, m);
        band(0, 0, m, h);
        band(w - m, 0, m, h);
        raised(0, 0, w, h);
        sunken(m - 3, m - 3, w - 2 * (m - 3), h - 2 * (m - 3));

        // The border is split into runs by short perpendicular rules — the corner segments are the
        // resize grips, which is the detail that makes an mwm frame recognisable at a glance.
        double grip = m * 2.4;
        for (double[] seg : new double[][] {
            {grip, 0, grip, m}, {w - grip, 0, w - grip, m},
            {grip, h - m, grip, h}, {w - grip, h - m, w - grip, h},
            {0, grip, m, grip}, {0, h - grip, m, h - grip},
            {w - m, grip, w, grip}, {w - m, h - grip, w, h - grip}
        }) {
            line(seg[0], seg[1], seg[2], seg[3]);
        }

        // Title bar with a menu box left and two square buttons right, each individually beveled.
        double barH = m * 0.5;
        double barY = (m - barH) / 2;
        double box = barH - 4;
        fill(grip + 4, barY + 2, box, box, "es-bezel-button");
        raised(grip + 4, barY + 2, box, box);
        fill(grip + 4 + box * 0.18, barY + 2 + box * 0.44, box * 0.64, box * 0.12, "es-bezel-glyph");
        for (int i = 0; i < 2; i++) {
            double bx = w - grip - 4 - (i + 1) * (box + 5);
            fill(bx, barY + 2, box, box, "es-bezel-button");
            raised(bx, barY + 2, box, box);
            double d = i == 0 ? box * 0.34 : box * 0.2;
            frame(bx + (box - d) / 2, barY + 2 + (box - d) / 2, d, d, "es-bezel-glyph-line");
        }
    }

    /**
     * A raised bevel: light on the top and left, dark on the bottom and right.
     *
     * <p>⚠ Two lines per edge, not a border on a Region — a Region border cannot be different
     * colours per side and still sit at exact pixel positions here, and a bevel that is a pixel out
     * on one side reads as a rendering fault rather than as depth.
     */
    private void raised(double x, double y, double w, double h) {
        bevelLine(x, y, x + w, y, "es-bezel-lit");
        bevelLine(x, y, x, y + h, "es-bezel-lit");
        bevelLine(x + w, y, x + w, y + h, "es-bezel-shade");
        bevelLine(x, y + h, x + w, y + h, "es-bezel-shade");
    }

    /** The same, inverted — what an inset well looks like. */
    private void sunken(double x, double y, double w, double h) {
        bevelLine(x, y, x + w, y, "es-bezel-shade");
        bevelLine(x, y, x, y + h, "es-bezel-shade");
        bevelLine(x + w, y, x + w, y + h, "es-bezel-lit");
        bevelLine(x, y + h, x + w, y + h, "es-bezel-lit");
    }

    private void bevelLine(double x1, double y1, double x2, double y2, String styleClass) {
        Line l = new Line(x1, y1, x2, y2);
        l.getStyleClass().add(styleClass);
        getChildren().add(l);
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
