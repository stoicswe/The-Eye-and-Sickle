package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The key to the map's glyphs: a marker, a word, nothing else.
 *
 * <h2>Why a legend and not a tooltip</h2>
 *
 * Every state on this map is carried by <b>glyph weight first and the grey ramp second</b>, because
 * the palette reserves its one accent for live/earning data and a network node is not earning — so
 * the picture has to be readable with the colour taken away entirely. A vocabulary that only works
 * once you have hovered each cell is not readable; it is a quiz. Ten characters and ten words,
 * permanently on screen, is what makes the ramp legible on the first glance rather than the tenth.
 *
 * <p>{@code ░░} and {@code ▒▒} are the pair worth being explicit about: they are one 15 EC purchase
 * apart, and a player who cannot see that the map is <em>telling them so</em> has no reason to make
 * it. Naming both states next to each other is the cheapest possible piece of that teaching.
 *
 * <h2>⚠ A COLUMN, not a strip, and that is a bug fix</h2>
 *
 * This was a one-line {@link javafx.scene.layout.HBox} under the graph, and ten entries do not fit
 * across a panel — the last of them ran off the right edge and the panel does not scroll
 * horizontally at that level, so they were simply unreachable. Worse, the ones that vanished were
 * the tail of the list, which is where {@code ░░} and {@code ··} sit: the two dimmest states, the
 * two a player is most likely to need named. A key whose entries can leave the window is not a key.
 *
 * <p>So it stacks, and {@code NetMapView} puts it in the same row as the graph rather than under it.
 * A column has a bounded width and an unbounded run of entries, which is the shape this data
 * actually has — adding an eleventh state now costs vertical space the panel has, not horizontal
 * space it does not. The glyph is padded to a fixed cell count so the words line up down the column;
 * that only works because the whole widget is pinned to IBM Plex, which is the same reason every
 * other glyph texture in this client is (see {@code GlyphCoverageTest}).
 *
 * <h2>It carries {@code es-netmap} itself</h2>
 *
 * The stylesheet's rules are descendant selectors — {@code .es-netmap .es-netmap-legend} — because a
 * one-class selector ties {@code .label { -fx-text-fill: -es-text; }} on specificity and loses on
 * later-rule-wins, painting silently in body grey while every other property applies. So this widget
 * puts {@code es-netmap} on itself and the legend class on its children, which makes it correct
 * wherever a view chooses to place it rather than only inside a {@link NetGraph}.
 */
public final class NetLegend extends VBox {

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

    /**
     * Character cells reserved for the marker, so every word starts in the same column.
     *
     * <p>Three, because the lock markers are {@code [#]} and the node markers are two blocks. Padded
     * with spaces rather than laid out as two Labels in a row: this is a character-cell texture like
     * everything else on this panel, and a fixed-width font already guarantees the alignment a
     * second node would only approximate.
     */
    private static final int MARKER_CELLS = 3;

    public NetLegend() {
        super(UiTokens.SPACE_1);
        getStyleClass().add("es-netmap");
        setAlignment(Pos.TOP_LEFT);
        // ⚠ Without this the longest entry renders as "PATCHED — ...". The column sits in an HBox
        // beside the graph, the graph is a character texture whose preferred width is enormous, and
        // an HBox whose children want more room than it has shrinks them towards their MINIMUM — for
        // a Label, that is however narrow it takes to fit an ellipsis. Measured, not predicted: the
        // truncated entry was in the first render of this layout.
        //
        // The graph is the child that should absorb the shortfall, because it is the one already
        // inside a horizontal scroller. A key that abbreviates the word it exists to supply is not a
        // key, which is the same argument that moved it out of the strip in the first place.
        setMinWidth(USE_PREF_SIZE);

        // Named, because a bare column of ten glyph/word pairs beside a graph made of the same
        // glyphs reads as more graph. §6's rule that a label says what a thing IS applies to the
        // key as much as to a readout.
        Label heading = Ui.label("Key");
        heading.getStyleClass().add("es-netmap-legend-head");
        getChildren().add(heading);

        StringBuilder spoken = new StringBuilder("Map key. ");
        for (Map.Entry<String, String> entry : ENTRIES.entrySet()) {
            // One Label per pair rather than one string for the whole column: the glyph and its word
            // have to stay together, and a single wrapped Label would break between them. Nothing
            // here is interactive, so a Label is the whole widget.
            Label item = new Label(marker(entry.getKey()) + Ui.upper(entry.getValue()));
            item.getStyleClass().add("es-netmap-legend");
            item.setWrapText(false);
            getChildren().add(item);
            spoken.append(entry.getValue()).append(". ");
        }
        // The glyphs themselves are meaningless to a screen reader — two block characters read as
        // nothing or as "black square" depending on the platform — so the strip announces the words.
        setAccessibleText(spoken.toString().trim());
    }

    /**
     * A marker in its fixed cell, with the single space that separates it from its word.
     *
     * <p>Package-visible so a headless test can assert the alignment without a toolkit — the column
     * only reads as a column while every word starts at the same offset, and that is a property of
     * this string rather than of the layout.
     */
    static String marker(String glyph) {
        String cell = glyph == null ? "" : glyph;
        return String.format(Locale.ROOT, "%-" + MARKER_CELLS + "s ", cell);
    }
}
