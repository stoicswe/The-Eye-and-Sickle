package io.github.stoicswe.eyeandsickle.client.theme;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The looks a player can choose.
 *
 * <h2>What changed on 2026-07-26, and why the list is shorter</h2>
 *
 * {@code docs/design/ui-design-language.md} §0 cancels two things that were previously Established
 * in {@code docs/architecture/01-tech-stack.md}: <b>AtlantaFX for native OS theming</b> and <b>a
 * separate {@code Stage} per tool</b>. The reasoning is short and hard to argue with — native
 * theming puts real macOS traffic lights and Windows title bars around the game, and "the entire
 * aesthetic depends on the player never seeing their own operating system."
 *
 * <p>So the {@code native} family is gone. There is no OS-following light mode, because there is no
 * OS chrome for it to match.
 *
 * <h2>One component sheet, several palettes</h2>
 *
 * §0 also says "ship one hand-written stylesheet". Taken literally that would mean one look, which
 * would delete the phosphor, amber-tube and Classic skins added on 2026-07-25 at the player's
 * request. Taken as what it is arguing against — <em>a second sheet that redefines components and
 * can therefore drift</em> — it permits what is built here: {@code theme.css} owns every component
 * rule, geometry, hairline and motion, and a variant is a <b>palette overlay of about forty lines</b>
 * loaded after it.
 *
 * <p>The consequence is worth stating plainly: a widget cannot look right in one variant and broken
 * in another, because there is only one set of component rules. It also means a new variant is
 * cheap, and that adding one can never introduce a rounded corner.
 *
 * <p>⚠ <b>uOS Classic is no longer System 7 chrome.</b> Bevels, shadows and radius are on §9's
 * build-blocking rejection list, so what survives is Classic's <em>palette</em> — a light field with
 * black hairlines, which was always the part that made it the most legible skin in the client. The
 * period bevelling did not survive the design language, and pretending otherwise would leave a theme
 * that violates the contract every other theme is held to.
 */
public enum ThemeId {

    /** The deck. Cold blue-black ground, sodium amber for anything earning. The default. */
    DECK("deck", "Deck", null, false),

    /**
     * High visibility.
     *
     * <p>Not a style option — an accessibility floor ({@code docs/client/07-accessibility.md}).
     * Body text clears WCAG AAA and hairlines clear the 3:1 non-text minimum. It is the one place
     * in the client that trades §2.1's "never {@code #000}" away, and it does so deliberately.
     */
    DECK_HC("deck-hc", "Deck — high visibility", "theme-hc.css", true),

    /** The green CRT the story family started as — DEC/VT220, one phosphor at several intensities. */
    PHOSPHOR("phosphor", "Phosphor", "theme-phosphor.css", false),

    /** The amber tube: warmer, and the one many people read most comfortably for long stretches. */
    AMBER_TUBE("amber", "Amber tube", "theme-amber.css", false),

    /** uOS Classic: a light field, black hairlines. The most legible non-accessibility skin. */
    CLASSIC("classic", "uOS Classic", "theme-classic.css", false);

    /** The component sheet. Every theme loads this first; the overlay only redefines colours. */
    public static final String BASE_STYLESHEET = "/io/github/stoicswe/eyeandsickle/client/ui/theme.css";

    private static final String OVERLAY_DIR = "/io/github/stoicswe/eyeandsickle/client/ui/";

    private final String id;
    private final String label;
    private final String overlayFile;
    private final boolean highContrast;

    ThemeId(String id, String label, String overlayFile, boolean highContrast) {
        this.id = id;
        this.label = label;
        this.overlayFile = overlayFile;
        this.highContrast = highContrast;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean highContrast() {
        return highContrast;
    }

    /**
     * This theme's palette overlay, if it has one.
     *
     * <p>Empty for {@link #DECK}, which <em>is</em> the palette in {@code theme.css}. A variant that
     * needed to override a component rule rather than a colour would be a sign the component rule
     * belongs in the base sheet with a modifier class.
     */
    public Optional<String> overlayStylesheet() {
        return Optional.ofNullable(overlayFile).map(file -> OVERLAY_DIR + file);
    }

    public static Optional<ThemeId> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    /** Themes offered in the picker, in the order they are offered. */
    public static List<ThemeId> selectable() {
        return List.of(values());
    }
}
