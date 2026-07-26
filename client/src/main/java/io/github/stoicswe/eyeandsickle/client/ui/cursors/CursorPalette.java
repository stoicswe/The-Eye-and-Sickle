package io.github.stoicswe.eyeandsickle.client.ui.cursors;

import javafx.scene.paint.Color;

/**
 * The three colours a pointer is drawn from, resolved out of the live stylesheet.
 *
 * <p>Not a set of constants — see {@link Palette}. Every one of these is read back from the theme
 * that is actually applied, which is what lets a pointer follow a palette overlay without a colour
 * literal anywhere in Java (§10 criterion 2).
 *
 * @param accent the live/earning colour. The pointer's own colour under most skins
 * @param ground the panel body. Used as the opaque backing that keeps a pointer visible over text,
 *     and as the "unlit" half of the inverting block
 * @param text the brightest foreground. This is what an <em>inverted</em> terminal cell looks like:
 *     on a dark deck it is a light block, on uOS Classic it is a dark one — in both cases the exact
 *     opposite of the ground, which is what inversion means
 */
record CursorPalette(Color accent, Color ground, Color text) {}
