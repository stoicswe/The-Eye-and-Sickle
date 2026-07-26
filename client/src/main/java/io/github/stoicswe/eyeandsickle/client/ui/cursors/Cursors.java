package io.github.stoicswe.eyeandsickle.client.ui.cursors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Builds, caches and applies the client's pointers.
 *
 * <h2>Three measured facts this class is built around</h2>
 *
 * All three were measured against JavaFX 26.0.2 on this project, not read from a tutorial. Two of
 * them contradict what web CSS would lead you to expect, which is the fourth and fifth time this
 * codebase has hit that.
 *
 * <ol>
 *   <li><b>{@code -fx-cursor: url(...)} does not work.</b> It parses and then fails at apply time
 *       with {@code ClassCastException: java.lang.String incompatible with javafx.scene.Cursor}. A
 *       custom cursor therefore <em>cannot</em> come from a stylesheet — it must be set from Java.
 *   <li><b>A CSS {@code -fx-cursor} on a node beats an inherited Scene cursor.</b> Measured: with
 *       {@code scene.setCursor(custom)}, a Label reads {@code getCursor() == null} (inheriting) until
 *       a stylesheet gives it {@code -fx-cursor: hand}, after which it reads {@code HAND}. So every
 *       {@code -fx-cursor} declaration left in {@code theme.css} would punch a system-cursor hole in
 *       the skin. They were removed; {@link #clickable} replaces them.
 *   <li><b>A hotspot outside the image is clamped, not rejected.</b> {@code new ImageCursor(img32,
 *       42, 42)} yields a hotspot of {@code 31,31}. Safe, but it means a wrong hotspot fails
 *       silently rather than loudly.
 * </ol>
 *
 * <h2>Colours come from the stylesheet, not from here</h2>
 *
 * See {@link Palette}. The upshot is that switching to phosphor or to high-visibility re-draws the
 * pointer in that palette's accent, with no colour constant anywhere in Java —
 * {@code UiContractTest} enforces the absence.
 *
 * <h2>HiDPI</h2>
 *
 * JavaFX offers no @2x mechanism for cursors: {@code ImageCursor.getBestSize} on macOS returns
 * whatever you pass it, and the image is used at its natural size in points. On a 2× display
 * (measured: {@code Screen.getPrimary().getOutputScaleX() == 2.0}) a 32-point cursor is therefore
 * drawn from 32 pixels and is slightly soft. Every skin is drawn on half-pixel coordinates with
 * 1px strokes to keep that as crisp as the platform allows; the alternative — a 64px image — would
 * be a physically double-sized pointer, which is worse.
 */
public final class Cursors {

    /** Cursor bitmap edge, in points. Comfortably inside every platform's maximum. */
    private static final double SIZE = 32;

    private static final Cursors INSTANCE = new Cursors();

    private final Map<CursorRole, Cursor> cache = new EnumMap<>(CursorRole.class);
    private final List<Scene> scenes = new ArrayList<>();
    private final List<Node> clickables = new ArrayList<>();
    private CursorSkin skin = CursorSkin.SYSTEM;

    private Cursors() {}

    public static Cursors shared() {
        return INSTANCE;
    }

    public CursorSkin skin() {
        return skin;
    }

    /**
     * Switches skin and repaints every registered surface.
     *
     * <p>The stylesheets are taken from a live Scene so the drawn colours match the theme that is
     * actually applied — which is why this is called again on every theme change, not only when the
     * player picks a different pointer.
     */
    public void select(CursorSkin skin, List<String> stylesheets) {
        this.skin = skin == null ? CursorSkin.SYSTEM : skin;
        cache.clear();
        rebuild(stylesheets);
        applyAll();
    }

    private void rebuild(List<String> stylesheets) {
        if (skin.isSystem()) {
            return;
        }
        Palette palette = new Palette(stylesheets);
        // The accent and the ground, by style class — so a cursor is drawn in whatever the current
        // palette means by "live" and "panel". Fallbacks are named colours, never hex (§10 crit. 2).
        Color accent = palette.colourOf("es-cell-self-mining", Color.ORANGE);
        Color edge = palette.colourOf("es-panel", Color.gray(0.05));

        for (CursorRole role : CursorRole.values()) {
            if (role == CursorRole.TEXT) {
                // Deliberately never re-drawn — see CursorSkin's class comment.
                continue;
            }
            Cursor built = build(role, accent, edge);
            if (built != null) {
                cache.put(role, built);
            }
        }
    }

    private Cursor build(CursorRole role, Color accent, Color edge) {
        try {
            Canvas canvas = new Canvas(SIZE, SIZE);
            skin.draw(canvas.getGraphicsContext2D(), role, SIZE, accent, edge);

            SnapshotParameters params = new SnapshotParameters();
            // Transparent, not white. The default snapshot fill is opaque white, which would give
            // every pointer a 32px white card behind it — the single most obvious way to get this
            // wrong, and invisible until you run it on a dark screen.
            params.setFill(Color.TRANSPARENT);
            WritableImage image = canvas.snapshot(params, null);

            double hx = Math.floor(SIZE * role.hotspotFractionX(skin));
            double hy = Math.floor(SIZE * role.hotspotFractionY(skin));
            return new ImageCursor(image, hx, hy);
        } catch (RuntimeException notBuildable) {
            // A toolkit that cannot snapshot. Falling back to the platform cursor for this role is
            // correct: cursors are cosmetic and must never stop the client from starting.
            return null;
        }
    }

    /** The cursor for a role under the current skin, falling back to the conventional one. */
    public Cursor of(CursorRole role) {
        Cursor custom = cache.get(role);
        return custom != null ? custom : role.platform();
    }

    // ── Installation ─────────────────────────────────────────────────────────────────────────

    /** Registers a Scene so it carries the pointer, now and after every future change. */
    public void adopt(Scene scene) {
        if (scene != null && !scenes.contains(scene)) {
            scenes.add(scene);
            scene.setCursor(of(CursorRole.POINTER));
        }
    }

    public void forget(Scene scene) {
        scenes.remove(scene);
    }

    /**
     * Marks a node as clickable, so it shows the hand for the current skin.
     *
     * <p>This exists because {@code -fx-cursor: hand} in a stylesheet would override the scene
     * cursor with the <em>system</em> hand and punch a hole in the skin — see fact 2 in the class
     * comment. Registered rather than set-and-forgotten, so a skin change reaches nodes that were
     * built before it.
     */
    public Node clickable(Node node) {
        if (node != null && !clickables.contains(node)) {
            clickables.add(node);
        }
        if (node != null) {
            node.setCursor(of(CursorRole.HAND));
        }
        return node;
    }

    /**
     * Marks every ordinary control under {@code root} as clickable.
     *
     * <p>The stylesheet used to do this with {@code -fx-cursor: hand} on {@code .button} and
     * friends, which cannot survive a custom skin (fact 2 in the class comment). Sweeping once when
     * a window's content is built covers the static case, which is all of it — the tool views build
     * their controls up front. A control added later simply inherits the pointer, which is a
     * missing affordance rather than a wrong one.
     */
    public void sweep(Node root) {
        if (root == null) {
            return;
        }
        if (root instanceof javafx.scene.control.ButtonBase
                || root instanceof javafx.scene.control.ChoiceBox<?>
                || root instanceof javafx.scene.control.ComboBoxBase<?>) {
            clickable(root);
            return;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                sweep(child);
            }
        }
    }

    /** Re-applies the current pointer to every registered Scene and clickable. */
    public void applyAll() {
        scenes.removeIf(scene -> scene.getRoot() == null);
        for (Scene scene : scenes) {
            scene.setCursor(of(CursorRole.POINTER));
        }
        clickables.removeIf(node -> node.getScene() == null && node.getParent() == null);
        for (Node node : clickables) {
            node.setCursor(of(CursorRole.HAND));
        }
    }

    /** The resize grip for a window edge, as {@code ui/chrome/DeskManager} computes it. */
    public Cursor resize(boolean north, boolean south, boolean west, boolean east) {
        if (north && west) {
            return of(CursorRole.RESIZE_NW);
        }
        if (north && east) {
            return of(CursorRole.RESIZE_NE);
        }
        if (south && west) {
            return of(CursorRole.RESIZE_SW);
        }
        if (south && east) {
            return of(CursorRole.RESIZE_SE);
        }
        if (north) {
            return of(CursorRole.RESIZE_N);
        }
        if (south) {
            return of(CursorRole.RESIZE_S);
        }
        if (west) {
            return of(CursorRole.RESIZE_W);
        }
        if (east) {
            return of(CursorRole.RESIZE_E);
        }
        return of(CursorRole.POINTER);
    }

    /** How many roles actually built. Zero under {@link CursorSkin#SYSTEM}; used by tests. */
    public int builtCount() {
        return cache.size();
    }
}
