package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import javafx.scene.layout.Region;

/**
 * The 45° stripe band.
 *
 * <h2>One per screen. Not two.</h2>
 *
 * {@code docs/design/ui-design-language.md} §2.3: "One diagonal per screen, no more — a hazard-stripe
 * band at 45°. It is what stops the layout reading as 'terminal'." Everything else in the interface
 * is orthogonal — hairlines, cells, strips — so a single diagonal is a strong, cheap signal that
 * this is industrial equipment rather than a text console. Two diagonals is a pattern, and a pattern
 * is wallpaper.
 *
 * <p>The rail's texture strip is the deliberate exception and is drawn in {@code rule-hi} rather
 * than {@code amber-low} for that reason: it reads as machining on the chassis, not as a second
 * hazard marking.
 *
 * <h2>The gradient JavaFX does not have</h2>
 *
 * There is no {@code repeating-linear-gradient} — measured on this project, the function does not
 * resolve and the declaration is dropped. The stylesheet uses a two-stop {@code linear-gradient}
 * with an explicit start and end point and the {@code repeat} cycle method, which is the native
 * spelling of the same thing. The 8.4853px axis is 6px at 45° (6 × √2), matching the reference.
 */
public final class HazardBand extends Region {

    private HazardBand(String styleClass) {
        getStyleClass().add(styleClass);
        setMouseTransparent(true);
    }

    /** The top strip's band: fixed width, amber-low, the screen's one diagonal. */
    public static HazardBand top(double width) {
        HazardBand band = new HazardBand("es-hazard");
        band.setMinWidth(width);
        band.setPrefWidth(width);
        band.setMaxWidth(width);
        return band;
    }

    /** The rail's vertical texture strip. Chassis machining, not a hazard marking. */
    public static HazardBand rail(double width) {
        HazardBand band = new HazardBand("es-hazard-rail");
        band.setMinWidth(width);
        band.setPrefWidth(width);
        band.setMaxWidth(width);
        band.setMinHeight(60);
        javafx.scene.layout.VBox.setVgrow(band, javafx.scene.layout.Priority.ALWAYS);
        return band;
    }
}
