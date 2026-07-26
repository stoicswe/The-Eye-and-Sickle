package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A deployed miner's offline buffer: eight cells, one per half hour of the four-hour cap.
 *
 * <h2>The cap is the mechanic, so the cap is the widget</h2>
 *
 * Invariant <b>I5</b> makes deployed miners the only offline income, and
 * {@code docs/design/04-mining.md} §3 caps what they hold. Eight cells rather than a percentage
 * because the player's actual question is <em>how long can I be away</em>, and cells at half an hour
 * each answer it by counting. §4 fixes the geometry: "8 cells = 4 hours, one per half hour. Fills
 * {@code amber-mid}; goes {@code alarm} at full."
 *
 * <h2>Full is loss, which is why it is the alarm colour</h2>
 *
 * A full buffer is not a full tank — it is yield being <b>discarded</b>, every hour, silently, until
 * someone collects. That is one of the few states in the client that genuinely costs the player
 * something while they do nothing, so it earns one of §2.1's two permitted alarms and says
 * {@code DISCARDING} in words beside it. Colour alone would fail
 * {@code docs/client/07-accessibility.md} §5.2 anyway.
 */
public final class BufferBar extends VBox {

    private final List<Region> cells = new ArrayList<>();
    private final Label caption = Ui.micro("");
    private final HBox strip = new HBox(UiTokens.HAIR);

    public BufferBar() {
        super(3);
        for (int i = 0; i < UiTokens.BUFFER_CELLS; i++) {
            Region cell = Ui.block(
                    UiTokens.BUFFER_CELL_WIDTH, UiTokens.BUFFER_CELL_HEIGHT, "es-buffer-cell");
            cells.add(cell);
            strip.getChildren().add(cell);
        }
        caption.getStyleClass().add("es-buffer-text");
        getChildren().addAll(strip, caption);
    }

    /**
     * @param heldMinorUnits what is sitting on the host now
     * @param capMinorUnits the ceiling it stops at
     * @param hoursCap the real-time span the cap represents, for the caption
     */
    public void show(long heldMinorUnits, long capMinorUnits, double hoursCap) {
        double fraction = capMinorUnits <= 0 ? 0 : Math.min(1, (double) heldMinorUnits / capMinorUnits);
        boolean full = capMinorUnits > 0 && heldMinorUnits >= capMinorUnits;
        int lit = (int) Math.floor(fraction * UiTokens.BUFFER_CELLS);
        if (full) {
            lit = UiTokens.BUFFER_CELLS;
        }

        for (int i = 0; i < cells.size(); i++) {
            Region cell = cells.get(i);
            cell.getStyleClass().removeAll("es-buffer-cell-full", "es-buffer-cell-lost");
            if (i < lit) {
                cell.getStyleClass().add(full ? "es-buffer-cell-lost" : "es-buffer-cell-full");
            }
        }

        double heldHours = fraction * hoursCap;
        String text = String.format(
                Locale.ROOT, "%s / %s", clock(heldHours), clock(hoursCap));
        caption.setText(full ? text + " · DISCARDING" : text);
        caption.getStyleClass().removeAll("es-buffer-text-lost");
        if (full) {
            caption.getStyleClass().add("es-buffer-text-lost");
        }
    }

    /** {@code H:MM}, the format the reference uses — not a duration library's "4 hours 0 minutes". */
    private static String clock(double hours) {
        int whole = (int) Math.floor(hours);
        int minutes = (int) Math.round((hours - whole) * 60);
        if (minutes == 60) {
            whole += 1;
            minutes = 0;
        }
        return String.format(Locale.ROOT, "%d:%02d", whole, minutes);
    }
}
