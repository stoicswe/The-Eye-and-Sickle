package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

/**
 * Real backdrop blur: what is <em>behind</em> a window, blurred, painted as that window's ground.
 *
 * <h2>⚠ JavaFX HAS NO BACKDROP FILTER, and this is the way round it</h2>
 *
 * Measured, and it is the fact the whole design of {@code theme-liquid-*} was built around before
 * this existed: there is no {@code backdrop-filter}, no pixel shader, and {@code -fx-effect:
 * gaussianblur(...)} blurs the node's <b>own</b> content — i.e. a panel's text — which is worse than
 * useless. A CSS route does not exist.
 *
 * <p>What does exist is {@link Node#snapshot}. So the blur is done the only way the toolkit allows:
 * render what is beneath a window to an image, blur <em>the image</em>, and put it under the
 * window's translucent panel. The result is genuine — a window shows the wallpaper and the windows
 * below it, softened — rather than the tinted-transmission approximation this deck shipped first.
 *
 * <h2>What it costs, measured, because every decision here came out of the numbers</h2>
 *
 * Four windows at 1600×1000, per full refresh:
 *
 * <pre>
 *   one capture per window (a real compositor's semantics)   ~40ms   24fps CEILING
 *   ...each cut down to its own window's rectangle           ~37ms   27fps
 *   one shared capture for everything                         ~9ms   ~110fps
 *   what ships: shared + one per OVERLAPPING window       8–34ms   127fps tiled
 * </pre>
 *
 * <p>⚠ <b>The second row is the finding, and it is counter-intuitive.</b> {@code snapshot} renders
 * the whole node whatever the viewport says — the viewport only crops the <em>result</em>. So the
 * cost is the NUMBER of snapshots and barely at all their size, which is why shrinking them bought
 * 7% and taking fewer bought everything. It is also why {@link #SCALE} has diminishing returns: at
 * 0.22 a cascaded cycle was still 32ms.
 *
 * <h2>Shared where that is exact; per-window where it is not</h2>
 *
 * A single capture is taken with every frame hidden, so it is the desk. Handing it to a window that
 * sits on top of another shows blurred <em>desk</em> where the window beneath should be — which
 * reads as a hole punched through the stack rather than as glass, and is what shipped for one build.
 *
 * <p>⚠ The resolution is that the shared capture is not an approximation for most windows, it is
 * <b>exact</b>: if a window's rectangle does not overlap any lower window, the desk genuinely is all
 * that is under it. So only windows that really do overlap get a capture of their own — none of them
 * in the tiled layout, where windows abut and share edges. Correctness everywhere, paid for in the
 * worst case rather than in every case.
 *
 * <h2>The image is the whole desk, and each window looks at its own part</h2>
 *
 * That is what makes dragging free: a move over a static backdrop is exactly a translation of the
 * backdrop, so {@code WindowFrame.layoutChildren} re-anchors the picture and nothing is re-rendered.
 *
 * <h2>24fps is a CEILING, not a rate</h2>
 *
 * {@code UiTokens.FROST_MS}, driven by {@code DeskManager} — <b>not</b> {@code Pulse}, which ticks at
 * 100ms and quantises every subscriber to a multiple of that, so a request for 24fps would round
 * silently to 10.
 *
 * <p>⚠ And it is <b>paced against {@code UiTokens.FROST_BUDGET}</b>: {@code DeskManager} measures
 * each refresh and will not start the next until the gap is at least {@code cost / budget}. A fixed
 * 24fps would hand the thread to the blur exactly when the player has the most on screen. So the
 * frost stays correct at any window count and only its <em>frequency</em> degrades — the full 24fps
 * tiled, around 7fps with four windows cascaded.
 *
 * <p>⚠ Under reduced motion the clock stops and the event-driven path takes over, so the frost is
 * still correct after every interaction and simply never moves on its own.
 *
 * <p>⚠ It also must never run <em>during</em> a layout or CSS pass. {@code snapshot} forces both, so
 * calling it from {@code layoutChildren} recurses. {@link DeskManager} schedules it.
 */
public final class Frost {

    /**
     * How much the capture is downscaled before blurring.
     *
     * <p>⚠ Lowering it is nearly free and raising it buys nothing visible — the image is about to be
     * destroyed by a blur. It is not zero-cost though: too low and the upscale shows square
     * artefacts through the frost, because a 30px blur cannot hide an 8px block.
     */
    private static final double SCALE = 0.25d;

    /**
     * Blur radius in captured (downscaled) pixels.
     *
     * <p>⚠ In screen pixels this is {@code BLUR / SCALE}, i.e. about 30. JavaFX caps
     * {@link GaussianBlur} at 63, which is a cap on <em>this</em> number and not on the screen one —
     * another reason to blur before upscaling rather than after.
     */
    private static final double BLUR = 12.0d;

    private Frost() {}

    /**
     * Re-captures the backdrop of every frame on the desk.
     *
     * <p>One capture with every frame hidden, handed to all of them — see the class comment for the
     * measurements that ruled out the per-window version.
     *
     * @param desk the pane holding the backdrop, the snap preview and the frames
     * @param enabled false to clear every frame's backdrop and paint nothing
     */
    public static void refresh(Pane desk, boolean enabled) {
        List<WindowFrame> frames = new ArrayList<>();
        for (Node node : desk.getChildren()) {
            if (node instanceof WindowFrame frame) {
                frames.add(frame);
            }
        }
        if (!enabled) {
            frames.forEach(WindowFrame::clearFrost);
            return;
        }
        double w = desk.getWidth();
        double h = desk.getHeight();
        if (w <= 0 || h <= 0 || desk.getScene() == null) {
            // Nothing laid out yet. Called again from the desk's own size listener.
            return;
        }
        if (popupShowing()) {
            // ⚠ A CAPTURE WOULD CLOSE IT. See popupShowing().
            return;
        }

        // ⚠ ONE SHARED CAPTURE, PLUS ONE MORE FOR EACH WINDOW THAT ACTUALLY OVERLAPS ANOTHER.
        //
        // The cost is the NUMBER of snapshots, not their size — `snapshot` renders the whole node
        // whatever the viewport says, and cutting each capture down to its own window's rectangle
        // moved a four-window cycle only from 40ms to 37ms. One shared capture is ~9ms.
        //
        // But a shared capture is taken with every frame hidden, so it is the DESK — and a window
        // sitting on top of another then shows blurred desk where the window beneath it should be,
        // which reads as a hole rather than as glass.
        //
        // ⚠ The resolution is that the shared capture is not an approximation for most windows, it
        // is EXACT: if nothing of a window's own rectangle overlaps a lower window, then the desk is
        // genuinely all that is under it. So only the windows that really do overlap need a capture
        // of their own, and in the tiled layout — where windows abut and share edges — that is none
        // of them. Correctness everywhere, at the cost of the worst case rather than of every case.
        List<WindowFrame> visible = new ArrayList<>();
        for (WindowFrame frame : frames) {
            if (frame.isVisible()) {
                visible.add(frame);
            }
        }
        boolean[] stacked = overlapping(visible);
        visible.forEach(frame -> frame.setVisible(false));
        try {
            Image shared = capture(desk, w, h);
            for (int i = 0; i < visible.size(); i++) {
                WindowFrame frame = visible.get(i);
                // ⚠ Captured BEFORE this frame is revealed, so it sees frames 0..i-1 and never
                // itself. Revealing as we go is what makes each capture cost one snapshot rather
                // than one snapshot plus a round of hiding everything above.
                frame.setFrost(stacked[i] ? capture(desk, w, h) : shared, w, h);
                frame.setVisible(true);
            }
        } finally {
            // A capture that threw must not leave the desk with invisible windows, which looks
            // exactly like every tool having closed itself.
            visible.forEach(frame -> frame.setVisible(true));
        }
    }

    /**
     * Whether any popup — a dropdown, a context menu, a tooltip — is on screen.
     *
     * <h2>⚠ CAPTURING WHILE ONE IS OPEN CLOSES IT, and this cost a real bug</h2>
     *
     * A capture hides every window frame for the duration of one snapshot. A JavaFX
     * {@link javafx.stage.PopupWindow} watches its owner node and <b>dismisses itself when that node
     * becomes invisible</b> — so the frost clock was closing every dropdown within 42ms of it
     * opening. The reported symptom was exactly that: "it appears, then disappears."
     *
     * <p>⚠ The failure is invisible in every render harness, because a popup is a separate window and
     * never appears in a {@code Scene.snapshot} of the deck. It also could not happen before the
     * frost went on a clock: an event-driven refresh never fired while a menu was open, because
     * opening one is not a desk event.
     *
     * <p>Skipping the refresh leaves the frost a few frames stale behind an open menu, which is
     * invisible at this radius and is obviously the right trade against the menu not working.
     */
    private static boolean popupShowing() {
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window instanceof javafx.stage.PopupWindow && window.isShowing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which of these frames, in bottom-to-top order, sit over another frame rather than over bare desk.
     *
     * <p>⚠ <b>Strictly positive overlap.</b> Tiled windows abut — one ends exactly where the next
     * begins — and a plain {@code intersects} counts a zero-width touch as an overlap, which would
     * put every window in the default layout on the expensive path and quietly cost four snapshots a
     * frame to produce a picture identical to the shared one.
     */
    private static boolean[] overlapping(List<WindowFrame> ordered) {
        boolean[] stacked = new boolean[ordered.size()];
        for (int i = 1; i < ordered.size(); i++) {
            WindowFrame above = ordered.get(i);
            for (int j = 0; j < i && !stacked[i]; j++) {
                WindowFrame below = ordered.get(j);
                double overlapX =
                        Math.min(right(above), right(below)) - Math.max(above.getLayoutX(), below.getLayoutX());
                double overlapY =
                        Math.min(bottom(above), bottom(below)) - Math.max(above.getLayoutY(), below.getLayoutY());
                stacked[i] = overlapX > 1 && overlapY > 1;
            }
        }
        return stacked;
    }

    private static double right(WindowFrame frame) {
        return frame.getLayoutX() + frame.getWidth();
    }

    private static double bottom(WindowFrame frame) {
        return frame.getLayoutY() + frame.getHeight();
    }

    /**
     * Captures the desk with every window hidden, and blurs it.
     *
     * <p>⚠ <b>The blur radius pads nothing here, and does not need to.</b> The capture is the whole
     * desk, so the Gaussian always has real pixels to sample except at the desk's own outer edge —
     * which is behind the deck's chrome and never visible through a window.
     */
    /**
     * Frosts a floating overlay — a notice, a dropdown — against whatever it is floating over.
     *
     * <h2>⚠ Why this is not the same call the windows use</h2>
     *
     * A desk window's backdrop is the <em>desk</em>: the wallpaper and any windows below it, captured
     * with the frames hidden. An overlay sits above <b>everything</b>, so its backdrop is the deck
     * itself — the windows very much included. Handing it the desk capture would show the wallpaper
     * through a notice lying on top of a window, which is the same hole-in-the-stack failure the
     * per-window captures exist to avoid.
     *
     * <p>⚠ <b>The overlay layer must be hidden for its own capture</b>, or the notice photographs
     * itself and each refresh compounds the last into an ever-brighter smear.
     *
     * @param deck what the overlay floats over — captured, blurred, and handed back
     * @param overlays the overlay layers to hide while capturing, so they cannot see themselves
     * @return the blurred deck, or null if it cannot be captured yet
     */
    public static Image overlayBackdrop(javafx.scene.Node deck, List<? extends Node> overlays) {
        if (deck.getScene() == null) {
            return null;
        }
        javafx.geometry.Bounds bounds = deck.getLayoutBounds();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return null;
        }
        List<Node> hidden = new ArrayList<>();
        for (Node overlay : overlays) {
            if (overlay.isVisible()) {
                overlay.setVisible(false);
                hidden.add(overlay);
            }
        }
        try {
            return capture((Pane) deck, bounds.getWidth(), bounds.getHeight());
        } finally {
            hidden.forEach(node -> node.setVisible(true));
        }
    }

    /**
     * Points a floating region at its part of an overlay backdrop.
     *
     * <p>⚠ The image is the whole deck, so it is placed by the region's position <em>within</em> the
     * deck, negated — the same anchoring a window frame does, and the same reason: a picture of
     * everywhere costs nothing to re-aim and is correct wherever the overlay ends up.
     */
    public static void placeOverlay(javafx.scene.image.ImageView view, Node region, Node deck, Image image) {
        if (image == null) {
            view.setImage(null);
            return;
        }
        javafx.geometry.Bounds inDeck = deck.sceneToLocal(region.localToScene(region.getLayoutBounds()));
        view.setImage(image);
        view.setFitWidth(deck.getLayoutBounds().getWidth());
        view.setFitHeight(deck.getLayoutBounds().getHeight());
        view.setSmooth(true);
        view.relocate(-inDeck.getMinX(), -inDeck.getMinY());
    }

    private static Image capture(Pane desk, double w, double h) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(new Scale(SCALE, SCALE));
        // ⚠ The viewport is in the TRANSFORMED space, so the figures are scaled. Passing desk
        // coordinates here captures the wrong rectangle — and plausibly, since it is only wrong by
        // the scale factor and still produces a picture of the desk.
        parameters.setViewport(new Rectangle2D(0, 0, w * SCALE, h * SCALE));
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = desk.snapshot(parameters, null);
        return blur(image);
    }

    /**
     * Blurs an image by rendering it through a {@link GaussianBlur} into a second image.
     *
     * <p>⚠ The effect could be left on the {@code ImageView} instead, and that is worse: it would be
     * re-applied on every frame the view is painted, for an image that never changes. Baking it once
     * means the per-frame cost of a blurred backdrop is the cost of drawing a bitmap.
     */
    private static Image blur(Image source) {
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(source);
        view.setEffect(new GaussianBlur(BLUR));
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        // ⚠ A blurred node is BIGGER than its image — the effect spreads beyond the source bounds —
        // so the viewport has to be pinned to the original rectangle or the result comes back padded
        // and every backdrop is offset by the blur radius.
        parameters.setViewport(new Rectangle2D(0, 0, source.getWidth(), source.getHeight()));
        return view.snapshot(parameters, null);
    }
}
