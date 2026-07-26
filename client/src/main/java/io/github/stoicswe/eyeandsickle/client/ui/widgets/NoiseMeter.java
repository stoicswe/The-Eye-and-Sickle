package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.Random;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Noise, drawn as a sound meter rather than as a level.
 *
 * <h2>Why a bar was the wrong instrument</h2>
 *
 * Noise was a single filled bar, which reads as a <em>quantity</em> — like fuel, or storage used.
 * But noise is not a quantity the player accumulates; it is how much racket the rig is making right
 * now, and it decays. A bank of columns that move says that in a way a bar cannot: a quiet rig
 * twitches along the bottom, a loud one is alive across its whole width. The player learns to read
 * the <em>motion</em>, which is exactly the peripheral signal a status strip should carry.
 *
 * <p>It also stays inside §4's rule — "3px × 9px cells with 1px gaps, never a continuous bar or
 * gradient". This is that rule in two dimensions instead of one.
 *
 * <h2>Peak hold, because the interesting number is the spike</h2>
 *
 * Every column keeps its recent maximum as a single detached cell that sinks a step at a time. That
 * is a real VU-meter mechanism and it earns its place here: a burst of noise that has already
 * decayed is still the thing that got the player noticed, and without a peak hold it would be gone
 * before they looked up.
 *
 * <h2>Motion</h2>
 *
 * Each frame is a discrete recomputation of integer cell counts — §5's step timing, not an
 * interpolation, so nothing here is an easing curve in disguise. Under reduced motion the columns
 * freeze at the true level with no jitter and no peaks, which is still an honest reading.
 */
public final class NoiseMeter extends HBox {

    /**
     * The rig's idle emission, in MHz.
     *
     * <p>Diegetic instrumentation rather than a game statistic — but <b>derived from the same noise
     * value the bars are drawn from</b>, never sampled independently. That matters for the same
     * reason everything else in this client shares one source: two readouts of the same quantity
     * that can disagree destroy the discrepancy-spotting skill {@code docs/design/04-mining.md}
     * §3.1 is built on. A quiet rig sits near this figure and barely wanders; a loud one climbs and
     * jitters.
     */
    private static final double BASE_MHZ = 2411;

    private static final double MHZ_SPAN = 780;

    private static final int COLUMNS = 18;

    /** Tall enough that the columns have somewhere to travel — a 7-row meter barely moved. */
    private static final int ROWS = 12;
    private static final double CELL_W = 2;
    private static final double CELL_H = 2;

    /** Lively enough to read as sound, slow enough not to strobe. Decorative, so reduced-motion kills it. */
    private static final double FRAME_MS = 120;

    /** Frames a peak marker holds before it sinks one step. */
    private static final int PEAK_HOLD_FRAMES = 6;

    private final javafx.scene.control.Label frequency = Ui.micro("");
    private final Region[][] cells = new Region[COLUMNS][ROWS];
    private final int[] level = new int[COLUMNS];
    private final int[] peak = new int[COLUMNS];
    private final int[] peakAge = new int[COLUMNS];
    private final Random random = new Random();

    private double noise;
    private AutoCloseable subscription;

    public NoiseMeter() {
        super(UiTokens.SPACE_4);
        setAlignment(Pos.BOTTOM_LEFT);

        // The key and its reading stack to the left of the bars, so the cell reads top-down as
        // "what is this / what does it say / what is it doing" rather than left-to-right.
        javafx.scene.control.Label key = Ui.label("Noise");
        key.getStyleClass().add("es-kv-key");
        frequency.getStyleClass().add("es-noise-frequency");
        VBox readout = new VBox(2, key, frequency);
        readout.setAlignment(Pos.BOTTOM_LEFT);
        getChildren().add(readout);

        HBox bars = new HBox(UiTokens.HAIR);
        bars.setAlignment(Pos.BOTTOM_LEFT);
        for (int c = 0; c < COLUMNS; c++) {
            VBox column = new VBox(UiTokens.HAIR);
            column.setAlignment(Pos.BOTTOM_CENTER);
            // Built top-down so index 0 is the TOP row; the level test below reads rows from the
            // bottom, which is the direction a meter fills.
            for (int r = 0; r < ROWS; r++) {
                Region cell = Ui.block(CELL_W, CELL_H, "es-noise-cell");
                cells[c][r] = cell;
                column.getChildren().add(cell);
            }
            bars.getChildren().add(column);
        }
        getChildren().add(bars);
        subscription = Pulse.shared().animate(FRAME_MS, this::step);
        retune();
        repaint();
    }

    /**
     * Sets how loud the rig currently is.
     *
     * @param noise 0–1. Drives the columns' mean height, how far they scatter around it, and how
     *     often they jump — all three, because a meter that only got taller would read as a bar
     *     again.
     */
    public void setNoise(double noise) {
        this.noise = Math.max(0, Math.min(1, noise));
        if (Pulse.shared().reducedMotion()) {
            // No jitter, no peaks: the true level, held still.
            int flat = (int) Math.round(this.noise * ROWS);
            for (int c = 0; c < COLUMNS; c++) {
                level[c] = flat;
                peak[c] = 0;
            }
            retune();
            repaint();
        }
    }

    private void step() {
        int base = (int) Math.floor(noise * ROWS);
        // Scatter widens with noise: a near-silent rig barely moves, a loud one is ragged. At zero
        // the whole meter is still, which is the reading a player should be able to trust at a
        // glance — a floor of permanent fidgeting would make "quiet" indistinguishable from "low".
        int scatter = (int) Math.ceil(noise * (ROWS * 0.55));

        for (int c = 0; c < COLUMNS; c++) {
            int jitter = scatter == 0 ? 0 : random.nextInt(scatter + 1) - (scatter / 2);
            int next = Math.max(0, Math.min(ROWS, base + jitter));
            // A little asymmetry: levels jump up immediately and fall one step at a time, the way a
            // real meter's needle behaves and the reason a transient is visible at all.
            level[c] = next > level[c] ? next : Math.max(next, level[c] - 1);

            if (level[c] >= peak[c]) {
                peak[c] = level[c];
                peakAge[c] = 0;
            } else if (++peakAge[c] >= PEAK_HOLD_FRAMES) {
                peakAge[c] = 0;
                peak[c] = Math.max(0, peak[c] - 1);
            }
        }
        retune();
        repaint();
    }

    /**
     * The frequency reading, from the same noise value as the bars.
     *
     * <p>Recomputed per frame with a small deterministic-per-frame wobble so it twitches like the
     * meter does. §5: values jump to a new figure, never tween towards one.
     */
    private void retune() {
        double wobble = (random.nextDouble() - 0.5) * (6 + noise * 40);
        frequency.setText(String.format(
                java.util.Locale.ROOT, "%.0f MHZ", BASE_MHZ + noise * MHZ_SPAN + wobble));
    }

    private void repaint() {
        // The top two rows are the alarm band. Noise is not damage, so this is not "you are hurt" —
        // it is the rig being loud enough that the peaks are the part worth looking at. §2.1 rations
        // alarm to two uses per screen; this is one, and only when the meter actually reaches it.
        int hot = ROWS - 2;
        for (int c = 0; c < COLUMNS; c++) {
            for (int r = 0; r < ROWS; r++) {
                int fromBottom = ROWS - r;
                Region cell = cells[c][r];
                cell.getStyleClass().removeAll("es-noise-on", "es-noise-hot", "es-noise-peak");
                if (fromBottom <= level[c]) {
                    cell.getStyleClass().add(fromBottom > hot ? "es-noise-hot" : "es-noise-on");
                } else if (peak[c] > 0 && fromBottom == peak[c]) {
                    cell.getStyleClass().add("es-noise-peak");
                }
            }
        }
    }

    public void dispose() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            subscription = null;
        }
    }
}
