package io.github.stoicswe.eyeandsickle.client.ui.cursors;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Reads a colour back out of the stylesheet.
 *
 * <h2>Why a cursor cannot just name its own colour</h2>
 *
 * Cursors are pixels, so drawing one needs a {@link Color} in Java. But
 * {@code docs/design/ui-design-language.md} §10 criterion 2 says every colour is a looked-up colour
 * declared once in {@code theme.css}, and {@code UiContractTest} fails the build on any hex literal
 * under {@code client/ui/}. Both rules are right and they collide exactly here.
 *
 * <p>The way out is to stop treating the stylesheet as write-only. This applies the real stylesheet
 * to a throwaway {@link Region} carrying a real style class, then reads the resolved fill back off
 * its background. The cursor is therefore drawn in whatever {@code -es-amber} currently means — which
 * makes it follow every palette overlay for free, including the high-visibility one, without a single
 * colour constant in Java.
 *
 * <p>That is not a workaround for the rule; it is the rule working. A cursor that had its own
 * hard-coded amber would be the one element of the interface that did not change when the player
 * switched to phosphor.
 *
 * <h2>Cost</h2>
 *
 * One off-scene {@link Scene} and a CSS pass per resolution. Called a handful of times when the
 * theme changes, never per frame.
 */
final class Palette {

    private final java.util.List<String> stylesheets;

    Palette(java.util.List<String> stylesheets) {
        this.stylesheets = java.util.List.copyOf(stylesheets);
    }

    /**
     * The background colour a style class resolves to.
     *
     * @param styleClass e.g. {@code es-cell-self-mining} for the accent, {@code es-panel} for ground
     * @param fallback used when the class has no background, or CSS could not be applied at all —
     *     never null, because a cursor with no colour is an invisible cursor
     */
    Color colourOf(String styleClass, Color fallback) {
        try {
            Region probe = new Region();
            probe.getStyleClass().add(styleClass);
            probe.resize(8, 8);
            // A Scene is required: JavaFX resolves looked-up colours through the Scene's stylesheet
            // list, and applyCss() on an unparented node silently resolves nothing.
            Scene scene = new Scene(new Group(probe));
            scene.getStylesheets().addAll(stylesheets);
            probe.applyCss();

            var background = probe.getBackground();
            if (background == null || background.getFills().isEmpty()) {
                return fallback;
            }
            // The LAST fill, not the first. Several cell classes paint two layers — an outline
            // colour and then the ground inset by a pixel — and the first fill of those is the
            // hairline, not the body. For the classes used here the two agree, but taking the last
            // is what stays correct if one of them gains a second layer.
            var paint = background.getFills().getLast().getFill();
            return paint instanceof Color colour ? colour : fallback;
        } catch (RuntimeException notResolvable) {
            // A headless or partially-initialised toolkit. Cursors are cosmetic; failing to resolve
            // a colour must never stop the client from starting.
            return fallback;
        }
    }
}
