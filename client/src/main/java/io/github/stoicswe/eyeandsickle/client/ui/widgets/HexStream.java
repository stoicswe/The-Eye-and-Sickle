package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Words going through the core, one line at a time.
 *
 * <h2>What it is</h2>
 *
 * Decoration, and it says so. It sits under {@link CoreCage} in the space the capped cell field
 * leaves beside it, and it exists because a machine that is visibly working is more convincing than
 * a machine that is merely described as working. Nothing here is read off game state and nothing here
 * is a readout — every number is invented, which is exactly why the newest line is the only one drawn
 * bright: a player must never mistake this for something they can act on.
 *
 * <h2>⚠ How the motion is allowed to exist</h2>
 *
 * {@code ui-design-language.md} §5 permits no easing and rations continuous motion, and
 * {@code UiContractTest} enforces both by scanning for {@code Interpolator} and by asserting
 * {@code AnimationTimer} appears in exactly two files. So this drives off {@link Pulse#animate} —
 * <b>one</b> subscription for the whole widget, not one per line (§7.3), stepping discretely.
 *
 * <p>⚠ {@code animate}, not {@code every}, and that is the accessibility decision rather than a
 * convenience: {@code animate} is suppressed under Reduce motion, so the stream holds a single frame.
 * That is correct <em>because</em> it is decoration — the rule elsewhere in this client is that a
 * readout keeps ticking under reduced motion because stopping it would remove information. There is
 * no information here to remove.
 *
 * <h2>⚠ Only hex digits and spaces</h2>
 *
 * Every character must be in a bundled face or it is drawn by a host-OS fallback with different
 * advance widths, which breaks the character-cell alignment the whole deck is laid out on.
 * {@code GlyphCoverageTest} fails the build on an uncovered literal; {@code 0-9A-F} and the ASCII
 * separators used here are covered by both.
 */
public final class HexStream extends VBox {

    /** How many lines are on screen. Enough to read as a flow, short enough to sit under the cage. */
    private static final int LINES = 9;

    /** Fewest words on a line, however narrow the column gets. Below this it stops reading as a bus. */
    private static final int MIN_WORDS = 3;

    /** Most words on a line. A cap keeps a very wide panel from turning the stream into wallpaper. */
    private static final int MAX_WORDS = 12;

    /** Digits in the address, and in each word. */
    private static final int WIDTH = 4;

    /**
     * One step per beat.
     *
     * <p>⚠ Slower than it wants to be. A fast scroll reads as a screensaver — the same argument
     * {@link CoreCage} makes about its own 300ms — and the point is a machine working steadily rather
     * than a terminal being flooded.
     */
    private static final double FRAME_MS = 420;

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private final Deque<Label> lines = new ArrayDeque<>();
    private final Random random = new Random();
    private final AutoCloseable subscription;

    /** How many words a line currently carries. Recomputed from the column's real width. */
    private int words = MIN_WORDS;

    /** One character's advance in the face the stylesheet actually applied. Measured, never assumed. */
    private double cellWidth;

    public HexStream() {
        super(UiTokens.HAIR);
        getStyleClass().add("es-hexstream");
        for (int i = 0; i < LINES; i++) {
            push();
        }
        // ⚠ The line length follows the column. A fixed word count either leaves a wide panel
        // half-empty or overruns a narrow one — and an overrun clips mid-word, which reads as a
        // corrupted readout rather than as a stream that did not fit.
        widthProperty().addListener((obs, was, now) -> refit());
        subscription = Pulse.shared().animate(FRAME_MS, this::push);
    }

    /**
     * Recomputes the words per line from the column's width.
     *
     * <p>⚠ The character advance is <b>measured</b> off the font the stylesheet applied, exactly as
     * {@code Substrate} does and for the same reason: the face and its size live in
     * {@code theme.css}, so a hard-coded advance in Java is a second source of truth that drifts
     * silently the first time the stylesheet changes — and the symptom would be a stream that no
     * longer fills its column or one that overruns it, neither of which fails a build.
     */
    private void refit() {
        double available = getWidth();
        if (available <= 0) {
            return;
        }
        if (cellWidth <= 0) {
            Label probe = lines.peekFirst();
            if (probe == null) {
                return;
            }
            probe.applyCss();
            javafx.scene.text.Text text = new javafx.scene.text.Text("0");
            text.setFont(probe.getFont());
            cellWidth = text.getLayoutBounds().getWidth();
        }
        if (cellWidth <= 0) {
            return;
        }
        // address + two spaces, then each word plus its leading space.
        int columns = (int) Math.floor(available / cellWidth);
        int forWords = columns - (WIDTH + 2);
        int fits = forWords / (WIDTH + 1);
        int wanted = Math.max(MIN_WORDS, Math.min(MAX_WORDS, fits));
        if (wanted != words) {
            words = wanted;
            // ⚠ Every line is rewritten, not just the ones added from here on. A stream that only
            // widened as new lines arrived would show a ragged left-hand block of old short lines
            // for nine steps after every resize.
            for (Label line : lines) {
                line.setText(word());
            }
        }
    }

    /**
     * Adds a line at the bottom and drops the oldest.
     *
     * <p>⚠ The whole column is restyled on every step, because "newest" is a position rather than a
     * property: the line that was bright a moment ago has to dim when a newer one arrives, and a
     * widget that only styled the new line would end up with every line bright.
     */
    private void push() {
        Label line = Ui.micro(word());
        line.getStyleClass().add("es-hexstream-line");
        lines.addLast(line);
        getChildren().add(line);
        while (lines.size() > LINES) {
            Label oldest = lines.removeFirst();
            getChildren().remove(oldest);
        }
        int index = 0;
        for (Label existing : lines) {
            existing.getStyleClass().removeAll("es-hexstream-new", "es-hexstream-old");
            // The newest is the one at the end; the two oldest fade out at the top, so the column
            // reads as a flow with a direction rather than a block of noise.
            existing.getStyleClass()
                    .add(
                            index == lines.size() - 1
                                    ? "es-hexstream-new"
                                    : index < 2 ? "es-hexstream-old" : "es-hexstream-line");
            index++;
        }
    }

    /** One line: an address, then the words that went through it. */
    private String word() {
        StringBuilder out = new StringBuilder();
        out.append(hex(WIDTH)).append("  ");
        for (int i = 0; i < words; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(hex(WIDTH));
        }
        return out.toString();
    }

    private String hex(int digits) {
        StringBuilder out = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            out.append(DIGITS[random.nextInt(DIGITS.length)]);
        }
        return out.toString();
    }

    /** Stops this widget's share of the shared driver. Called when the panel closes. */
    public void dispose() {
        try {
            subscription.close();
        } catch (Exception ignored) {
            // AutoCloseable's checked exception; the unsubscribe itself cannot fail.
        }
    }
}
