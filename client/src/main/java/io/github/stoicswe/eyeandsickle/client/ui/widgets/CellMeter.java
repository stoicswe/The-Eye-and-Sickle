package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A meter drawn in discrete cells.
 *
 * <h2>Never a continuous bar</h2>
 *
 * {@code docs/design/ui-design-language.md} §4: "3px × 9px cells with 1px gaps. Never a continuous
 * bar or gradient." The reason is the same one behind {@link CycleGrid} — a smooth bar implies a
 * continuous quantity and invites a precision the model does not have. It is also §9's gradient ban
 * arriving at the component level: a filled bar with a gradient is the single most recognisable
 * piece of dashboard furniture there is.
 *
 * <h2>What it is not allowed to be used for</h2>
 *
 * <b>Not heat.</b> {@code docs/client/01-visual-language.md} §2.2.4 requires heat to render as a
 * banded chip carrying the band name, never as a meter — the player's decision is a threshold
 * decision, and a meter would also make heat visually indistinguishable from trace, which is the
 * client's one genuinely continuous quantity.
 */
public final class CellMeter extends HBox {

    private final List<Region> bars = new ArrayList<>();
    private final int cells;

    public CellMeter(int cells) {
        super(UiTokens.HAIR);
        this.cells = cells;
        setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < cells; i++) {
            Region bar = Ui.block(UiTokens.METER_BAR_WIDTH, UiTokens.METER_BAR_HEIGHT, "es-meter-bar");
            bars.add(bar);
            getChildren().add(bar);
        }
    }

    /** Lights the first {@code lit} cells. Values outside the range clamp rather than throw. */
    public void set(int lit) {
        set(lit, false);
    }

    /**
     * Lights cells, optionally in the alarm colour.
     *
     * @param hot true when what this measures has reached a state that costs the player something —
     *     not merely "high". §2.1 caps alarm at two uses per screen, and a meter that turns red on
     *     the way up spends that budget on a value that is still fine.
     */
    public void set(int lit, boolean hot) {
        int clamped = Math.max(0, Math.min(cells, lit));
        for (int i = 0; i < bars.size(); i++) {
            Region bar = bars.get(i);
            bar.getStyleClass().removeAll("es-meter-bar-on", "es-meter-bar-hot");
            if (i < clamped) {
                bar.getStyleClass().add(hot ? "es-meter-bar-hot" : "es-meter-bar-on");
            }
        }
    }

    /** Lights cells from a 0–1 fraction, rounding up so any non-zero value lights at least one. */
    public void setFraction(double fraction, boolean hot) {
        double clamped = Math.max(0, Math.min(1, fraction));
        set((int) Math.ceil(clamped * cells), hot);
    }
}
