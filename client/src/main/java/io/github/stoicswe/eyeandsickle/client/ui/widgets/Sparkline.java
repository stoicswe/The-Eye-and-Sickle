package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A rolling history of one value, drawn in cells.
 *
 * <h2>A different instrument from the noise meter, on purpose</h2>
 *
 * {@link NoiseMeter}'s columns are a <em>spectrum</em> — eighteen simultaneous readings of one
 * quantity, scattered to show agitation. This is a <em>time series</em>: the left edge is a minute
 * ago and the right edge is now. They look similar at a glance and answer opposite questions, so
 * each carries a label saying which it is, and the sparkline's newest column is marked.
 *
 * <p>The reason load and thermal recovery get history and noise does not: load and recovery are
 * quantities with a <b>shape over time</b> that the player acts on. "Recovery has been flat for a
 * minute" and "load spiked while I was reading the ledger" are both decisions. Noise is a rate that
 * decays and is only ever interesting <em>now</em>.
 *
 * <h2>Still cells, still §4</h2>
 *
 * "3px × 9px cells with 1px gaps. Never a continuous bar or gradient." A line chart would be a
 * continuous form and would also imply interpolation between samples that were never measured — one
 * column is one sample, and the gaps between columns are the sampling interval made visible.
 */
public final class Sparkline extends VBox {

    /** Columns of history. At one sample a second this is the last half-minute. */
    private static final int SAMPLES = 30;

    private static final int ROWS = 8;
    private static final double CELL_W = 2;
    private static final double CELL_H = 2;

    private final Region[][] cells = new Region[SAMPLES][ROWS];
    private final double[] history = new double[SAMPLES];
    private final javafx.scene.control.Label value = Ui.value("");

    /**
     * @param key what this measures, in the strip's own label voice
     */
    public Sparkline(String key) {
        super(2);
        setAlignment(Pos.BOTTOM_LEFT);

        javafx.scene.control.Label label = Ui.label(key);
        label.getStyleClass().add("es-kv-key");
        HBox head = Ui.row(UiTokens.SPACE_3, label, value);
        head.setAlignment(Pos.BASELINE_LEFT);

        HBox chart = new HBox(UiTokens.HAIR);
        chart.setAlignment(Pos.BOTTOM_LEFT);
        for (int s = 0; s < SAMPLES; s++) {
            VBox column = new VBox(UiTokens.HAIR);
            column.setAlignment(Pos.BOTTOM_CENTER);
            for (int r = 0; r < ROWS; r++) {
                Region cell = Ui.block(CELL_W, CELL_H, "es-spark-cell");
                cells[s][r] = cell;
                column.getChildren().add(cell);
            }
            chart.getChildren().add(column);
        }
        getChildren().addAll(head, chart);
        repaint();
    }

    /**
     * Adds a sample and scrolls the window.
     *
     * <p>Called from the shell's own one-second data tick rather than from a timer of this widget's
     * own, so every sparkline on the strip samples on the same beat. Two history charts drifting a
     * few hundred milliseconds apart would make a spike look like it happened at two different
     * times.
     *
     * @param fraction 0–1
     * @param reading what to print beside the label, with its unit
     */
    public void push(double fraction, String reading) {
        System.arraycopy(history, 1, history, 0, SAMPLES - 1);
        history[SAMPLES - 1] = Math.max(0, Math.min(1, fraction));
        value.setText(reading);
        repaint();
    }

    private void repaint() {
        for (int s = 0; s < SAMPLES; s++) {
            // Ceil, so a non-zero sample always lights at least one cell. A history that rendered a
            // small but real value as empty would be reporting "nothing happened" for something
            // that did.
            int lit = history[s] <= 0 ? 0 : (int) Math.ceil(history[s] * ROWS);
            boolean newest = s == SAMPLES - 1;
            for (int r = 0; r < ROWS; r++) {
                int fromBottom = ROWS - r;
                Region cell = cells[s][r];
                cell.getStyleClass().removeAll("es-spark-on", "es-spark-now");
                if (fromBottom <= lit) {
                    // The newest column is brighter, so "now" is findable without counting. Without
                    // it a time series and a spectrum are indistinguishable at strip size.
                    cell.getStyleClass().add(newest ? "es-spark-now" : "es-spark-on");
                }
            }
        }
    }
}
