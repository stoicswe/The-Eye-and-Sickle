package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.view.RigStatus;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Personal heat as a graduated thermometer.
 *
 * <h2>Reconciling this with §2.2.4, which forbids a heat meter</h2>
 *
 * {@code docs/client/01-visual-language.md} §2.2.4 is emphatic: <b>"Heat renders as a banded chip
 * carrying the band name, never as a continuous meter."</b> It gives two reasons, and this widget is
 * built to satisfy both rather than to argue with them.
 *
 * <ol>
 *   <li><em>"A smooth bar invites a precision the model does not have."</em> — The stem is not
 *       smooth. It is eleven discrete cells divided into <b>five zones, one per band</b>, sized in
 *       proportion to each band's real range (0–10, 10–30, 30–55, 55–80, 80–100). The gaps between
 *       zones are the thresholds. Colour <b>steps</b> at a boundary and never interpolates across
 *       one. What the player reads is still "which band am I in", with the useful addition of "how
 *       close am I to the next one" — which serves the threshold decision §2.2.4 is protecting
 *       rather than undermining it.
 *   <li><em>"Trace is the only continuous red meter in the client, so heat and trace can never be
 *       confused."</em> — Trace is a continuous horizontal fill composed of labelled contribution
 *       segments (§2.2.5). This is a vertical, cell-based, zone-gapped column with a bulb. They do
 *       not resemble each other at any size, and the collision §2.2.4 was guarding against is
 *       structural rather than chromatic.
 * </ol>
 *
 * <h2>Where the band name went (UI-8, decided 2026-07-26)</h2>
 *
 * <b>The band name is not printed on the strip.</b> §2.2.4 requires the chip to "carry the band
 * name" and the strip cell is {@code KeyValue.keyOnly("Personal heat")} — label, thermometer, no
 * name. That was a deliberate call, confirmed when UI-8 was decided: the strip stays visually quiet
 * and the name lives in this widget's tooltip.
 *
 * <p>⚠ <b>It is a knowing departure from a contract, and one thing had to be done to keep it
 * honest.</b> {@code docs/client/07-accessibility.md} §5.2 forbids meaning resting on appearance
 * alone, and a hover tooltip is not an answer for anyone who does not hover — JavaFX tooltips are
 * mouse-only. So the band name is also set as this node's <b>accessible text</b>, which is the same
 * fix {@code CL-10} used for the gloss bar: the tooltip and the accessible name carry identical
 * content down two different paths. Visually the meaning still rests on the coloured bulb plus lit
 * cell count; §5.2 is satisfied for assistive technology and knowingly stretched for a sighted
 * player who does not hover. Recorded rather than assumed — if that trade stops being acceptable,
 * the fix is one line in {@code DeckShell}: {@code KeyValue.of("Personal heat", band.label())}.
 *
 * <h2>The bulb carries the band</h2>
 *
 * A thermometer's bulb is its reservoir, and colouring it with the <em>current</em> band makes the
 * answer to "which band am I in" readable without counting cells — which is the question §2.2.4 says
 * the player is actually asking.
 */
public final class ThermoMeter extends HBox {

    /** Cell counts per band, proportional to each band's heat range. Eleven cells in total. */
    private static final int[] ZONE_CELLS = {1, 2, 3, 3, 2};

    private static final double CELL_W = 6;
    private static final double CELL_H = 2;
    private static final double TICK_W = 3;

    private final List<Region> cells = new ArrayList<>();
    private final List<Integer> cellBand = new ArrayList<>();
    private final Region bulb;

    public ThermoMeter() {
        super(UiTokens.HAIR);
        setAlignment(Pos.BOTTOM_LEFT);

        VBox ticks = new VBox(UiTokens.HAIR);
        ticks.setAlignment(Pos.BOTTOM_CENTER);
        VBox stem = new VBox();
        stem.setAlignment(Pos.BOTTOM_CENTER);

        // Built top-down: the hottest band is at the top of the stem, as on a real thermometer.
        for (int band = ZONE_CELLS.length - 1; band >= 0; band--) {
            for (int i = 0; i < ZONE_CELLS[band]; i++) {
                Region cell = Ui.block(CELL_W, CELL_H, "es-thermo-cell");
                cells.add(cell);
                cellBand.add(band);
                stem.getChildren().add(cell);
                ticks.getChildren().add(Ui.block(TICK_W, CELL_H, "es-thermo-gap"));
            }
            if (band > 0) {
                // The threshold. A 1px gap in the stem and a visible graduation beside it — this is
                // what makes the meter banded rather than continuous, and it is load-bearing.
                stem.getChildren().add(Ui.block(CELL_W, UiTokens.HAIR, "es-thermo-gap"));
                ticks.getChildren().add(Ui.block(TICK_W, UiTokens.HAIR, "es-thermo-tick"));
            }
        }

        // cells was filled hottest-first; reverse so index 0 is the bottom of the stem.
        java.util.Collections.reverse(cells);
        java.util.Collections.reverse(cellBand);

        bulb = Ui.block(CELL_W + 2, CELL_W, "es-thermo-bulb");
        VBox column = new VBox(UiTokens.HAIR, stem, bulb);
        column.setAlignment(Pos.BOTTOM_CENTER);

        getChildren().addAll(ticks, column);
    }

    /**
     * @param personalHeat 0–100
     * @param band the band that heat falls in, so the widget and the label beside it cannot disagree
     */
    public void show(int personalHeat, RigStatus.HeatBand band) {
        int clamped = Math.max(0, Math.min(100, personalHeat));
        // Ceil, so any heat at all lights the first cell. A player who has just become non-zero
        // should see it — that transition is the one the whole heat economy turns on.
        int lit = clamped == 0 ? 0 : (int) Math.ceil(clamped / 100.0d * cells.size());

        for (int i = 0; i < cells.size(); i++) {
            Region cell = cells.get(i);
            cell.getStyleClass().removeIf(c -> c.startsWith("es-thermo-fill-"));
            if (i < lit) {
                // Coloured by the band THIS CELL belongs to, not by the current band — so the stem
                // shows the ramp it has climbed through. Colour changes at zone boundaries and
                // nowhere else, which is the "never a gradient" half of §2.2.4.
                cell.getStyleClass().add("es-thermo-fill-" + cellBand.get(i));
            }
        }
        bulb.getStyleClass().removeIf(c -> c.startsWith("es-thermo-fill-"));
        if (clamped > 0) {
            // Only tinted once there is heat. A cold rig's bulb stays an outline, so "no heat at
            // all" is visibly a different state from "the lowest band", which it is: I4's whole
            // point is that there is a real bottom to the scale.
            bulb.getStyleClass().add("es-thermo-fill-" + band.index());
        }

        // The band name down the OTHER path. The strip prints no name (UI-8), so without this the
        // only textual statement of which band the player is in would be mouse-only — and
        // docs/client/07 §5.2 does not let meaning rest on appearance. Same two-path fix as CL-10's
        // gloss bar. The number goes with it: a screen reader has no bulb to read.
        setFocusTraversable(false);
        setAccessibleText(Ui.upper("personal heat " + personalHeat + " of 100, " + band.label())
                + ". " + band.consequence());

        // The tooltip is the consequence, not the number: a band name alone is trivia, and
        // docs/client/01 §2.2.4's whole point is that the player's decision is about sweep odds.
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(
                Ui.upper("personal heat · " + band.label()) + "\n" + band.consequence()
                        + "\n\nHeat comes from acting outward. Self-mining, defending your own rig "
                        + "and scanning it are all free.");
        tip.setWrapText(true);
        tip.setMaxWidth(300);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        javafx.scene.control.Tooltip.install(this, tip);
    }
}
