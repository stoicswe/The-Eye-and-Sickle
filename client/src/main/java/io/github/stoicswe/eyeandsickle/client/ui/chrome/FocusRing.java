package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The colour of the outline drawn around the focused window, when the player asks for one.
 *
 * <h2>Why this exists and why it ships OFF</h2>
 *
 * The deck already marks the focused window: its strip lightens and its title takes the accent. That
 * is deliberately quiet, because the desk can hold a dozen panels and a loud marker on one of them
 * competes with the readouts inside all of them. It is also, for some people, not enough — a
 * low-contrast strip change is exactly the cue that disappears on a dim screen or for a player who
 * does not separate those two greys easily.
 *
 * <p>So the ring is an opt-in, and the colour is theirs to pick. Default off, default
 * {@link #THEME} — which follows whichever palette is in force rather than pinning a hue.
 *
 * <h2>⚠ These hues do NOT enter the palette's vocabulary</h2>
 *
 * {@code ui-design-language.md} §2.1 bans a semantic colour system, and §9 makes it build-blocking:
 * a colour in this client means something specific and there are very few of them. This does not
 * breach that, and the distinction is worth stating precisely — <b>a ring colour means nothing</b>.
 * It is not encoding a state, a severity or a category; it says "this window, the one you chose the
 * colour for". Every value here is confined to the {@code .es-focus-ring-*} selectors, none is used
 * anywhere else, and the accessibility rule that matters (§4.4, never colour alone) is untouched
 * because the strip cue the ring supplements is still there.
 *
 * <p>⚠ The colours are declared in {@code theme.css} and nowhere else. {@code UiContractTest} fails
 * the build on a hex literal in any {@code ui} class, so this enum carries <b>ids</b>, and the
 * stylesheet carries the paint.
 */
public enum FocusRing {

    /**
     * The palette's own accent, whatever it currently is.
     *
     * <p>⚠ First in the list and the default, because it is the only choice that stays right when
     * the player changes theme. Every uOS variant redefines {@code -es-amber}, so this follows all
     * five without knowing about any of them.
     */
    THEME("theme", "Theme default"),

    CYAN("cyan", "Cyan"),
    VIOLET("violet", "Violet"),
    GREEN("green", "Green"),
    ROSE("rose", "Rose"),
    WHITE("white", "White");

    private final String id;
    private final String label;

    FocusRing(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** The persisted id, and the suffix of this ring's style class. */
    public String id() {
        return id;
    }

    /** What Settings calls it. */
    public String label() {
        return label;
    }

    /** The style class the frame carries while this ring is chosen. */
    public String styleClass() {
        return "es-focus-ring-" + id;
    }

    /** Every choice, in the order Settings offers them — {@link #THEME} first. */
    public static List<FocusRing> selectable() {
        return List.of(values());
    }

    /**
     * Reads a persisted id back.
     *
     * <p>⚠ Tolerant, like every other appearance field: this comes out of a settings file a player
     * can edit and an older build wrote without it. An unknown value is {@link #THEME}, which is the
     * default and cannot look broken.
     */
    public static FocusRing byId(String id) {
        return of(id).orElse(THEME);
    }

    private static Optional<FocusRing> of(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (FocusRing ring : values()) {
            if (ring.id.equals(wanted)) {
                return Optional.of(ring);
            }
        }
        return Optional.empty();
    }

    /** Every style class this enum can put on a frame — what a rebuild has to clear first. */
    public static List<String> allStyleClasses() {
        return java.util.Arrays.stream(values()).map(FocusRing::styleClass).toList();
    }
}
