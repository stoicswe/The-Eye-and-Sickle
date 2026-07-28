package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * The key to the map's glyphs: a marker, a word, nothing else.
 *
 * <h2>Why a legend and not a tooltip</h2>
 *
 * Every state on this map is carried by <b>glyph weight first and the grey ramp second</b>, because
 * the palette reserves its one accent for live/earning data and a network node is not earning — so
 * the picture has to be readable with the colour taken away entirely. A vocabulary that only works
 * once you have hovered each cell is not readable; it is a quiz. Six characters and six words,
 * permanently on screen, is what makes the ramp legible on the first glance rather than the tenth.
 *
 * <p>{@code ░░} and {@code ▒▒} are the pair worth being explicit about: they are one 15 EC purchase
 * apart, and a player who cannot see that the map is <em>telling them so</em> has no reason to make
 * it. Naming both states next to each other is the cheapest possible piece of that teaching.
 *
 * <h2>It carries {@code es-netmap} itself</h2>
 *
 * The stylesheet's rules are descendant selectors — {@code .es-netmap .es-netmap-legend} — because a
 * one-class selector ties {@code .label { -fx-text-fill: -es-text; }} on specificity and loses on
 * later-rule-wins, painting silently in body grey while every other property applies. So this widget
 * puts {@code es-netmap} on itself and the legend class on its children, which makes it correct
 * wherever a view chooses to place it rather than only inside a {@link NetGraph}.
 */
public final class NetLegend extends HBox {

    /**
     * The vocabulary, in the order the map's own selection rule reads it.
     *
     * <p>Ordered by what it changes for the player, not alphabetically: where you are, the thing to
     * avoid, the thing you are already inside, the way onward, and then the two detection states that
     * a purchase separates. {@code ··} is last because it is the only entry that is not a machine.
     */
    private static final Map<String, String> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put(NetGlyphs.NODE_VANTAGE, "vantage");
        ENTRIES.put(NetGlyphs.NODE_TRAP, "trap?");
        ENTRIES.put(NetGlyphs.NODE_FOOTHOLD, "foothold");
        // The lock marks whether the way IN is open; the ink level above marks how much is known.
        // They came apart when a host could be breached and then patched — see NetCanvas.lockFor.
        ENTRIES.put(NetGlyphs.LOCK_OPEN, "breached");
        ENTRIES.put(NetGlyphs.LOCK_PATCHED, "patched — locked out");
        ENTRIES.put(NetGlyphs.LOCK_SHUT, "locked");
        ENTRIES.put(NetGlyphs.NODE_BRIDGE, "bridge");
        ENTRIES.put(NetGlyphs.NODE_IDENTIFIED, "identified");
        ENTRIES.put(NetGlyphs.NODE_CONTACT, "contact");
        ENTRIES.put(NetGlyphs.NODE_DARK, "beyond");
    }

    public NetLegend() {
        super(UiTokens.SPACE_5);
        getStyleClass().add("es-netmap");
        setAlignment(Pos.CENTER_LEFT);

        StringBuilder spoken = new StringBuilder("Map key. ");
        for (Map.Entry<String, String> entry : ENTRIES.entrySet()) {
            // One Label per pair rather than one string for the strip: the glyph and its word have to
            // stay together when the panel is narrow, and a single wrapped Label would break between
            // them. Nothing here is interactive, so a Label is the whole widget.
            Label item = new Label(entry.getKey() + String.valueOf(' ') + Ui.upper(entry.getValue()));
            item.getStyleClass().add("es-netmap-legend");
            item.setWrapText(false);
            getChildren().add(item);
            spoken.append(entry.getValue()).append(". ");
        }
        // The glyphs themselves are meaningless to a screen reader — two block characters read as
        // nothing or as "black square" depending on the platform — so the strip announces the words.
        setAccessibleText(spoken.toString().trim());
    }
}
