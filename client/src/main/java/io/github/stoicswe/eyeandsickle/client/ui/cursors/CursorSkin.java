package io.github.stoicswe.eyeandsickle.client.ui.cursors;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * The pointer sets a player can choose between.
 *
 * <h2>Why the game draws its own pointer at all</h2>
 *
 * {@code docs/design/ui-design-language.md} §0: "the entire aesthetic depends on the player never
 * seeing their own operating system." The window chrome went first; the pointer is the last piece of
 * the host OS left on screen, and it sits on top of everything. Every skin below is drawn from the
 * same vocabulary as the rest of the interface — 1px hairlines, right angles, the single amber
 * accent, no radius and no shadow.
 *
 * <h2>{@link #SYSTEM} is a real option, not a courtesy</h2>
 *
 * A pointer is not decoration. It is the thing a player tracks continuously, it is tuned by their
 * OS for their display and their eyesight, and some people run a deliberately enlarged or
 * high-contrast one. Overriding that without a way back would be an accessibility regression dressed
 * as art direction, so the system pointer is offered first and any skin can be abandoned in one
 * click.
 *
 * <h2>Two things are deliberately NOT re-drawn</h2>
 *
 * <ul>
 *   <li><b>The text I-beam.</b> Its shape carries real precision information — it shows exactly
 *       where between two characters the caret will land — and a themed glyph would cost accuracy
 *       for atmosphere. Text fields keep the system I-beam under every skin.
 *   <li><b>Wait/busy.</b> The deck has no blocking operations; work in progress is reported by the
 *       activity list, not by taking the pointer away.
 * </ul>
 */
public enum CursorSkin {

    /** The host OS pointer, untouched. The default, and the accessibility floor. */
    SYSTEM("system", "System pointer"),

    /**
     * A surveyor's reticle: a cross with a gap at the centre and four range ticks.
     *
     * <p>The gap matters — a solid cross hides the pixel it is pointing at, which is the pixel that
     * matters. Everything on this deck is a small cell on a 1px grid.
     */
    RETICLE("reticle", "Reticle"),

    /**
     * An outlined arrow. The conventional shape, drawn as a hairline rather than a filled mass.
     *
     * <p>The one skin that reads as a pointer from muscle memory, for players who find a crosshair
     * hard to track across a dark screen.
     */
    CHEVRON("chevron", "Chevron"),

    /**
     * The terminal block, notched at the top-right like every panel on the deck.
     *
     * <p>The most diegetic and the least conventional: it is the caret of the machine you are
     * operating, escaped from the command line. Solid, so it is the easiest of the three to find.
     */
    BLOCK("block", "Block");

    private final String id;
    private final String label;

    CursorSkin(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean isSystem() {
        return this == SYSTEM;
    }

    public static Optional<CursorSkin> byId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** In picker order. System first, because it is the one that asks nothing of the player. */
    public static List<CursorSkin> selectable() {
        return List.of(values());
    }

    /**
     * Draws one role at {@code size}×{@code size}.
     *
     * <p>All coordinates land on a half-pixel so a 1px stroke covers exactly one pixel row rather
     * than smearing across two — the same reason the cycle grid floors its cell size. A blurred
     * hairline is much more obvious on a pointer than anywhere else, because the pointer is the one
     * thing on screen the eye is always tracking.
     *
     * @param accent the live/earning colour, resolved from the current palette
     * @param edge the outline colour, for contrast against a light background
     */
    void draw(GraphicsContext g, CursorRole role, double size, Color accent, Color edge) {
        double mid = Math.floor(size / 2) + 0.5;
        g.setLineWidth(1);

        if (role.isResize()) {
            drawResize(g, role, size, mid, accent, edge);
            return;
        }
        switch (this) {
            case RETICLE -> drawReticle(g, role, size, mid, accent, edge);
            case CHEVRON -> drawChevron(g, role, size, accent, edge);
            case BLOCK -> drawBlock(g, role, size, accent, edge);
            case SYSTEM -> {
                // Never drawn — Cursors short-circuits to the platform pointer.
            }
        }
    }

    private void drawReticle(
            GraphicsContext g, CursorRole role, double size, double mid, Color accent, Color edge) {
        double arm = Math.floor(size * 0.42);
        double gap = 3;

        // The dark underlay first, offset by a pixel, so the reticle stays visible on a light panel
        // as well as on the void. Two strokes is cheaper and sharper than an outline path.
        for (int pass = 0; pass < 2; pass++) {
            g.setStroke(pass == 0 ? edge : accent);
            double o = pass == 0 ? 1 : 0;
            g.strokeLine(mid - arm + o, mid + o, mid - gap + o, mid + o);
            g.strokeLine(mid + gap + o, mid + o, mid + arm + o, mid + o);
            g.strokeLine(mid + o, mid - arm + o, mid + o, mid - gap + o);
            g.strokeLine(mid + o, mid + gap + o, mid + o, mid + arm + o);
        }

        // Range ticks: pure texture, the same argument as the rail's (§4 on greeble).
        g.setStroke(edge);
        g.strokeLine(mid - arm, mid - 2.5, mid - arm, mid + 3.5);
        g.strokeLine(mid + arm, mid - 2.5, mid + arm, mid + 3.5);

        if (role == CursorRole.HAND) {
            // Clickable: the centre gap fills in. Reads instantly without changing the silhouette.
            g.setFill(accent);
            g.fillRect(mid - 1.5, mid - 1.5, 3, 3);
        }
    }

    private void drawChevron(GraphicsContext g, CursorRole role, double size, Color accent, Color edge) {
        double h = Math.floor(size * 0.66);
        double w = Math.floor(size * 0.40);
        double[] xs = {0.5, 0.5, w * 0.45, w * 0.72, w};
        double[] ys = {0.5, h, h * 0.72, h, h * 0.45};

        // Filled with the ground colour and stroked with the accent: an outline pointer that is
        // still opaque, so it never disappears against text it is sitting on.
        g.setFill(edge);
        g.fillPolygon(xs, ys, xs.length);
        g.setStroke(accent);
        g.strokePolygon(xs, ys, xs.length);

        if (role == CursorRole.HAND) {
            g.setFill(accent);
            g.fillRect(w * 0.35, h * 0.30, 3, 3);
        }
    }

    private void drawBlock(GraphicsContext g, CursorRole role, double size, Color accent, Color edge) {
        double w = Math.floor(size * 0.34);
        double h = Math.floor(size * 0.50);
        double notch = 4;

        // The same 18px-at-panel-scale corner cut §2.3 puts on every panel, shrunk to cursor size.
        double[] xs = {0.5, w - notch, w, w, 0.5};
        double[] ys = {0.5, 0.5, notch, h, h};

        g.setFill(role == CursorRole.HAND ? accent : edge);
        g.fillPolygon(xs, ys, xs.length);
        g.setStroke(accent);
        g.strokePolygon(xs, ys, xs.length);

        if (role == CursorRole.HAND) {
            // Inverted rather than decorated: a filled block over a hollow one is unmistakable even
            // at 32px, and does not rely on colour alone (docs/client/07 §5.2).
            g.setStroke(edge);
            g.strokeLine(2.5, h * 0.5, w - 2.5, h * 0.5);
        }
    }

    /**
     * The eight directional grips.
     *
     * <p>Drawn rather than left to the OS because the deck's window manager is the client's own
     * ({@code ui/chrome/DeskManager}) — the resize cursor is the one place the player is touching an
     * edge this application drew, so a system arrow there is the most conspicuous possible seam.
     */
    private void drawResize(
            GraphicsContext g, CursorRole role, double size, double mid, Color accent, Color edge) {
        double arm = Math.floor(size * 0.34);
        double head = 3.5;
        double angle = role.angleDegrees();

        g.save();
        g.translate(mid, mid);
        g.rotate(angle);
        for (int pass = 0; pass < 2; pass++) {
            g.setStroke(pass == 0 ? edge : accent);
            double o = pass == 0 ? 1 : 0;
            g.strokeLine(o, -arm + o, o, arm + o);
            // Arrowheads as open chevrons, not filled triangles — hairlines everywhere (§2.3).
            g.strokeLine(-head + o, -arm + head + o, o, -arm + o);
            g.strokeLine(head + o, -arm + head + o, o, -arm + o);
            g.strokeLine(-head + o, arm - head + o, o, arm + o);
            g.strokeLine(head + o, arm - head + o, o, arm + o);
        }
        g.restore();
    }
}
