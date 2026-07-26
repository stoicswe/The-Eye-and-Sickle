package io.github.stoicswe.eyeandsickle.client.ui.cursors;

import javafx.scene.Cursor;

/**
 * What a pointer is currently over.
 *
 * <h2>Roles, not shapes</h2>
 *
 * A view asks for {@code HAND} because the thing under the pointer is clickable, never for "the
 * little pointing hand". That indirection is what lets a skin answer the question differently — the
 * reticle fills its centre gap, the block inverts — instead of every skin being obliged to draw a
 * hand.
 *
 * <p>Each role also carries the platform cursor it falls back to, so {@link CursorSkin#SYSTEM} and
 * any failure to build an image both degrade to exactly the conventional pointer rather than to
 * nothing.
 */
public enum CursorRole {

    /** The default. Everything that is not one of the below. */
    POINTER(Cursor.DEFAULT, 0),

    /** Over something that responds to a click. */
    HAND(Cursor.HAND, 0),

    /**
     * Over editable text.
     *
     * <p>Always the platform I-beam — see {@link CursorSkin}'s class comment. Present as a role so
     * views can ask for it explicitly rather than relying on Modena's {@code .text-input} rule,
     * which a scene-level cursor would otherwise have to fight.
     */
    TEXT(Cursor.TEXT, 0),

    // The eight window-manager grips. The angle is the rotation applied to one drawn double-headed
    // arrow, measured clockwise from vertical — so a skin draws the arrow once and gets all eight.
    RESIZE_N(Cursor.N_RESIZE, 0),
    RESIZE_NE(Cursor.NE_RESIZE, 45),
    RESIZE_E(Cursor.E_RESIZE, 90),
    RESIZE_SE(Cursor.SE_RESIZE, 135),
    RESIZE_S(Cursor.S_RESIZE, 0),
    RESIZE_SW(Cursor.SW_RESIZE, 45),
    RESIZE_W(Cursor.W_RESIZE, 90),
    RESIZE_NW(Cursor.NW_RESIZE, 135);

    private final Cursor platform;
    private final double angleDegrees;

    CursorRole(Cursor platform, double angleDegrees) {
        this.platform = platform;
        this.angleDegrees = angleDegrees;
    }

    /** The conventional cursor this role degrades to. */
    public Cursor platform() {
        return platform;
    }

    double angleDegrees() {
        return angleDegrees;
    }

    boolean isResize() {
        return ordinal() >= RESIZE_N.ordinal();
    }

    /**
     * The hotspot, as a fraction of the image.
     *
     * <p>Wrong here is worse than ugly: a hotspot a few pixels off means every click in the game
     * lands somewhere the player did not aim. The reticle and the resize arrows point from their
     * centre; an arrow points from its tip.
     */
    double hotspotFractionX(CursorSkin skin) {
        return switch (this) {
            case POINTER, HAND -> skin == CursorSkin.RETICLE ? 0.5 : 0.03;
            case TEXT -> 0.5;
            default -> 0.5;
        };
    }

    double hotspotFractionY(CursorSkin skin) {
        return switch (this) {
            case POINTER, HAND -> skin == CursorSkin.RETICLE ? 0.5 : 0.03;
            case TEXT -> 0.5;
            default -> 0.5;
        };
    }
}
