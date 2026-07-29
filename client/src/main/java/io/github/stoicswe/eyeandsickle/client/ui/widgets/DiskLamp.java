package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.DiskActivity;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * The drive activity lamp, at the left end of the command strip.
 *
 * <h2>It reports real writes and nothing else</h2>
 *
 * Every flash is a file this client actually wrote on the player's machine — the autosave, a
 * settings change, a window moved, an avatar chosen. {@link DiskActivity} counts them at the two
 * methods that do the writing, so the lamp cannot drift out of step with the disk by someone adding
 * a new caller. A decorative flicker would have been three lines shorter and would have made this
 * the one indicator in the game that lies about the player's own hardware — on a deck whose whole
 * teaching posture is that its readouts are real.
 *
 * <p>It is also, quietly, a privacy readout. The client writes to exactly one directory and this
 * says when. A player who sees it stutter while they are doing nothing can go and look.
 *
 * <h2>⚠ A Circle, not a rounded box</h2>
 *
 * {@code -fx-background-radius} is on §9's rejection list and {@code UiContractTest} fails the build
 * on a non-zero one outside {@code .es-rounded}. The face rings on the login screen learned this
 * first: <b>geometry on this deck is a shape or a clip, never a corner radius.</b>
 *
 * <h2>⚠ No Timeline of its own</h2>
 *
 * §7.3 wants one shared driver rather than a timer per widget, and {@code UiContractTest} rations
 * {@code AnimationTimer} and {@code Interpolator.LINEAR} by file name. So the burst is counted in
 * ticks of the shared {@link Pulse}: a write starts it, and it stutters through {@link #FLICKER} for
 * {@link #DWELL_TICKS} ticks before settling. Nothing interpolates — the lamp is on or off on any
 * given frame — so there is no fade here to ban, and none is missing.
 *
 * <p>Subscribed with {@link Pulse#every} rather than {@link Pulse#animate}, which is the difference
 * between <b>data</b> and <b>decoration</b>. Under Reduce motion this keeps working, because
 * suppressing it would not remove an animation — it would remove the only on-screen evidence that
 * the game touched the player's disk. It is a state light, not a flourish: it changes when a fact
 * changes and holds still otherwise.
 */
public final class DiskLamp extends StackPane {

    /**
     * How many {@link Pulse} ticks a write keeps the lamp working — 20 × 100ms, so two seconds.
     *
     * <p>⚠ Not zero-and-repaint. An atomic write of a settings file takes a couple of milliseconds,
     * so a lamp that tracked the write literally would be lit for a fraction of one frame and would
     * never be seen at all. A real drive LED has the same problem and solves it the same way: it
     * keeps working for as long as the drive is still settling, not for as long as the syscall took.
     */
    private static final int DWELL_TICKS = 20;

    /**
     * The stutter, one character per tick, {@code 1} lit.
     *
     * <p>⚠ A FIXED PATTERN, NOT {@code Math.random()}. Three reasons, in order of how much they
     * matter. It is <b>testable</b> — a random lamp can only be checked by staring at it, and this
     * one is asserted tick by tick. It is <b>reproducible</b> — two players watching the same write
     * see the same thing, and a bug report about the lamp describes something someone else can see.
     * And it is <b>shapeable</b> in a way randomness is not: this pattern is dense at the head and
     * sparse at the tail, so a write reads as a burst that <em>settles</em> rather than as noise
     * that stops. Six of the first eight ticks are lit; two of the last eight.
     *
     * <p>It starts with a {@code 1} deliberately — the tick a write lands on is always lit, so the
     * lamp never appears to ignore something the player just did.
     *
     * <p>Length must equal {@link #DWELL_TICKS}; {@code DiskLampTest} holds that, because a pattern
     * one character short would silently mean the last tick of every burst read from index zero.
     */
    private static final String FLICKER = "11011101011001001001";

    private final Circle lamp = new Circle(UiTokens.DISK_LAMP / 2);
    private long lastSeen = DiskActivity.writes();
    private Flicker state = Flicker.DARK;

    public DiskLamp() {
        lamp.getStyleClass().add("es-disk-lamp");
        getChildren().add(lamp);
        setAlignment(Pos.CENTER);
        // Sized explicitly so the prompt beside it does not shift when the lamp changes class — and
        // so the strip's baseline is unaffected by a shape that has no text metrics of its own.
        setMinSize(UiTokens.DISK_LAMP, UiTokens.DISK_LAMP);
        setPrefSize(UiTokens.DISK_LAMP, UiTokens.DISK_LAMP);
        setMaxSize(UiTokens.DISK_LAMP, UiTokens.DISK_LAMP);

        setAccessibleText("Drive activity");
        Pulse.shared().every(Pulse.tickMs(), this::tick);
    }

    /** One tick: advance the state machine, then paint whatever it says. */
    private void tick() {
        long now = DiskActivity.writes();
        state = state.next(lastSeen, now);
        lastSeen = now;
        lamp.getStyleClass().remove("es-disk-lamp-on");
        if (state.lit()) {
            lamp.getStyleClass().add("es-disk-lamp-on");
        }
    }

    /**
     * The whole lamp, as a value.
     *
     * <p>⚠ Extracted from {@link #tick()} so it can be tested at all. A two-second stutter is
     * something a screenshot cannot catch and staring at the deck cannot confirm — "does the burst
     * actually thin out", "does a write mid-burst restart it", "is the tick a write lands on always
     * lit" are questions only a headless test answers. Same reasoning as {@code ui/Avatar} returning
     * pixels rather than drawing onto a Canvas.
     *
     * @param remaining ticks of activity left, zero when the drive has settled
     * @param phase how far into {@link #FLICKER} this burst is
     */
    record Flicker(int remaining, int phase) {

        static final Flicker DARK = new Flicker(0, 0);

        /**
         * ⚠ A write RESTARTS the burst rather than extending it — {@code phase} goes back to zero,
         * not just {@code remaining} back to full. That is what makes the pattern's first character
         * the guarantee it claims to be: however deep into a fading tail the next write lands, it
         * lights the lamp on that very tick and the stutter begins again dense. Extending alone
         * would have left a write arriving during a sparse tail invisible for up to 300ms.
         *
         * <p>Counts are <b>compared</b>, never consumed — see {@link DiskActivity#writes()}. Several
         * writes inside one tick are therefore one burst, not a queue of them, which is why a busy
         * moment cannot leave the lamp simply on.
         */
        Flicker next(long lastSeen, long writes) {
            if (writes != lastSeen) {
                return new Flicker(DWELL_TICKS, 0);
            }
            return remaining <= 0 ? DARK : new Flicker(remaining - 1, phase + 1);
        }

        boolean lit() {
            return remaining > 0 && FLICKER.charAt(phase % FLICKER.length()) == '1';
        }
    }
}
