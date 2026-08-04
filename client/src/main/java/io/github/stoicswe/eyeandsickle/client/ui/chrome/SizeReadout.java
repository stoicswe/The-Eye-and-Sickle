package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.control.Label;

/**
 * {@code 840 × 520}, in the corner, while a window is being resized.
 *
 * <h2>Why this is worth having in a game about a computer</h2>
 *
 * Every window manager a player has used shows this, and here it is not only convention: the deck
 * lays out on real breakpoints — the rail hides below {@link UiTokens#NARROW_WIDTH}, the cycle grid
 * drops from 25 to 20 to 10 per row, the market's shelf goes from three tiles to two — so
 * <em>which</em> pixel a layout changes at is a thing the player can actually see happen. A readout
 * turns "it went funny when I made it small" into a number.
 *
 * <h2>⚠ It occupies NO layout space, and that is a rule this repo has paid for twice</h2>
 *
 * The balance delta wrapped the top strip onto two rows by being a managed child that got wider
 * while it showed; the download dock is an overlay for the same reason. This one is unmanaged and
 * positioned by its host, so a window is exactly as big with the readout on screen as without it —
 * which matters more here than anywhere, because the number it is reporting is that size.
 *
 * <h2>⚠ It STEPS away, and it is not a tween</h2>
 *
 * §5 permits no easing and {@code UiContractTest} rations {@code AnimationTimer} to two files by
 * name, so the ladder is {@link UiTokens#REVEAL_STEPS} whole steps on {@link Pulse} — the same
 * ladder {@code BalanceDelta} and {@code Motion} use. {@code Fade} exists and is deliberately not
 * used: it is the splash's continuous ramp, licensed by §5.1 for a title card the player is only
 * watching, and this is chrome on a window they are working inside.
 *
 * <h2>⚠ On {@code Pulse.every} — DATA, not decoration</h2>
 *
 * Under Reduce motion the dwell still has to expire, or the readout never leaves and every window
 * carries a permanent number in its corner. So the clock runs in both modes and only the
 * <em>ramp</em> is conditional: with motion suppressed it holds its dwell and then goes in one
 * step, which is WCAG 2.2.2 satisfied rather than the information withdrawn.
 */
public final class SizeReadout extends Label {

    /**
     * How long the size stays up after the last change.
     *
     * <p>Long enough to read after letting go of a drag, short enough not to sit over the corner of
     * a window somebody has moved on from. It restarts on every change, so a slow drag shows it
     * continuously rather than blinking.
     */
    public static final double DWELL_MS = 900;

    private AutoCloseable ticking;
    private double lastWidth = -1;
    private double lastHeight = -1;

    /**
     * ⚠ Suppresses the FIRST report, so opening a window does not flash its size.
     *
     * <p>A window opening at the size it was saved at is not a resize, and a readout on every open
     * would put a number in the corner of all twenty tool windows every time the deck restored a
     * layout. The first call only records the size to measure later ones against.
     */
    private boolean seen;

    public SizeReadout() {
        getStyleClass().add("es-size-readout");
        setVisible(false);
        // ⚠ Unmanaged, so no parent lays it out or reserves space for it. Its host positions it and
        // must therefore also autosize() it — an unmanaged node is never resized by its parent, and
        // a Label that has never been sized has a width of zero.
        setManaged(false);
        setMouseTransparent(true);
    }

    /**
     * Reports a size, restarting the dwell.
     *
     * <p>⚠ Called from a layout pass, so it must be cheap and it must ignore a pass where nothing
     * changed — {@code layoutChildren} runs whenever any child asks for layout, not only when the
     * window is resized, and reporting unconditionally would light this up on every repaint of
     * whatever is inside.
     *
     * @param width the window's width in pixels
     * @param height its height
     */
    public void report(double width, double height) {
        long w = Math.round(width);
        long h = Math.round(height);
        if (w == Math.round(lastWidth) && h == Math.round(lastHeight)) {
            return;
        }
        lastWidth = width;
        lastHeight = height;
        if (!seen) {
            seen = true;
            return;
        }
        // ⚠ `×` (U+00D7), which DeckShell's window controls already use — so GlyphCoverageTest has
        // proved it present in both bundled faces. A lowercase `x` would have been safe too and is
        // the wrong character.
        setText(w + " × " + h);
        setVisible(true);
        setOpacity(1);
        // Sized here, not by a parent: unmanaged. Without this the text is set on a zero-width node
        // and the host positions an invisible point.
        autosize();
        restart();
    }

    private void restart() {
        stop();
        int[] frame = {0};
        int hold = (int) Math.round(DWELL_MS / UiTokens.FRAME_MS);
        // ⚠ Pulse.every, not animate. `animate` is suppressed under Reduce motion, which would leave
        // the dwell永 never expiring and the readout on screen for good — the accessibility path
        // getting the worse behaviour, which is the failure the carousel already recorded.
        ticking = Pulse.shared().every(UiTokens.FRAME_MS, () -> {
            frame[0]++;
            if (frame[0] <= hold) {
                return;
            }
            if (Pulse.shared().reducedMotion()) {
                hide();
                return;
            }
            double opacity = 1 - (frame[0] - hold) / (double) UiTokens.REVEAL_STEPS;
            if (opacity <= 0) {
                hide();
                return;
            }
            setOpacity(opacity);
        });
    }

    private void hide() {
        setVisible(false);
        stop();
    }

    /**
     * Releases the ticker.
     *
     * <p>⚠ Must be called when the host goes away. A {@code Pulse} subscription outlives the node
     * that made it, and the deck opens and closes windows constantly — {@code CycleGrid.dispose} and
     * {@code CoreCage.dispose} were written, correct, and called by nobody, and every open of the
     * rig monitor leaked one.
     */
    public void stop() {
        if (ticking == null) {
            return;
        }
        try {
            ticking.close();
        } catch (Exception ignored) {
            // A Pulse subscription's close does not throw; the signature says it may.
        }
        ticking = null;
    }

    /**
     * Puts it in the bottom-right of a box of this size.
     *
     * @param width the host's width
     * @param height the host's height
     */
    public void placeIn(double width, double height) {
        if (!isVisible()) {
            return;
        }
        autosize();
        relocate(
                Math.max(0, width - getWidth() - UiTokens.SPACE_5),
                Math.max(0, height - getHeight() - UiTokens.SPACE_5));
    }
}
