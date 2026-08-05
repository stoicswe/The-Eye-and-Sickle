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
    CLASSIC("classic", "uOS Classic", "theme-classic.css", false),

    /**
     * Cyberdeck: rain-lit teal glass under a sodium-vapour accent.
     *
     * <p>⚠ The accent stays <b>single</b> and keeps its meaning. The obvious cyberpunk move is hot
     * magenta for one thing and cyan for another, which is precisely the "second accent hue, or a
     * semantic color system" §9 rejects — so the sodium orange means exactly what amber means on
     * every other skin: money you have, and live work. A palette is the sanctioned place to vary
     * colour here; a second meaning for a colour is not.
     */
    CYBERDECK("cyberdeck", "Cyberdeck", "theme-cyberdeck.css", false),

    /**
     * uOS Modern Liquid Abs, dark: translucent graphite glass under a lit rim.
     *
     * <p>See {@link #LIQUID_LIGHT} and §9.4 — the pair is one decision and the argument is recorded
     * once, below.
     */
    /**
     * ⚠ The <b>id</b> stays {@code liquid-dark} while the label reads "uOS Modern Liquid Abs".
     *
     * <p>An id is not a name: it keys the saved {@code VisualSettings.themeId}, the stylesheet
     * filename and the {@code .es-swatch-liquid-dark} rules {@code SetupSwatchTest} checks. Renaming
     * it to follow a label would move three things to change what one screen says, and would drop
     * any character already on this palette back to the deck on next load.
     */
    LIQUID_DARK("liquid-dark", "uOS Modern Liquid Abs — dark", "theme-liquid-dark.css", false, true),

    /**
     * uOS Modern Liquid Abs, light: bright glass on a cool grey desk.
     *
     * <h2>⚠ These two are the client's first deliberate glassmorphism, and §9 used to forbid it</h2>
     *
     * The rejection list's rounded-corner entry says in as many words that "drop shadows, blur and
     * glassmorphism are unchanged and still cut". {@code docs/design/ui-design-language.md} §9.4
     * amends that into an opt-in on explicit direction, by the same mechanism and under the same
     * four conditions §9.1 (screen artefacts), §9.2 (casing) and §9.3 (rounded corners) already run
     * on. Two of the four are structural here rather than maintained by hand, which is what makes
     * the amendment narrow:
     *
     * <ul>
     *   <li><b>Off by default.</b> {@link #DECK} is the default and these are two entries in a
     *       picker. A theme is opt-in by construction — there is no state in which a player gets
     *       glass without having chosen it.
     *   <li><b>The blur is real, and it is not CSS.</b> ⚠ This condition read "no blur, still —
     *       JavaFX makes it impossible" until 2026-08-05, and that was true only of the stylesheet:
     *       there is no {@code backdrop-filter}, and {@code -fx-effect: gaussianblur} blurs a node's
     *       <em>own</em> text. {@code ui/chrome/Frost} does it the way the toolkit does allow —
     *       snapshot what is beneath a window, blur the image, paint it under the panel. §9's ban on
     *       blur as a <em>decorative effect on the interface itself</em> is untouched and still
     *       machine-checked across every stylesheet.
     *   <li><b>Legibility is measured.</b> {@code ContrastTest} composites these palettes'
     *       translucent tokens over what is behind them and holds the real WCAG floor against the
     *       result — see that class for why the naive reading of an eight-digit hex is worse than
     *       no check at all.
     *   <li><b>Motion is untouched.</b> Neither palette animates anything; §5 never comes up.
     * </ul>
     *
     * <p>⚠ <b>The accent stays warm amber and keeps §2.1's meaning</b>, on explicit direction, even
     * though the reference these are named after is unmistakably blue. §2.1 calls the
     * warm-accent-on-cool-ground temperature split load-bearing, and a blue accent standing beside
     * the existing gain-green, warn-orange and alarm-red is the semantic colour system §2.1 bans,
     * arriving one token at a time. The material is the reference's; the vocabulary is the game's.
     */
    LIQUID_LIGHT("liquid-light", "uOS Modern Liquid Abs — light", "theme-liquid-light.css", false, true);

    /** The component sheet. Every theme loads this first; the overlay only redefines colours. */
    public static final String BASE_STYLESHEET = "/io/github/stoicswe/eyeandsickle/client/ui/theme.css";

    private static final String OVERLAY_DIR = "/io/github/stoicswe/eyeandsickle/client/ui/";

    private final String id;
    private final String label;
    private final String overlayFile;
    private final boolean highContrast;
    private final boolean glass;

    ThemeId(String id, String label, String overlayFile, boolean highContrast) {
        this(id, label, overlayFile, highContrast, false);
    }

    /**
     * @param glass whether this palette is translucent — see {@link #roundsCorners} and
     *     {@link #frostsBackdrop}, which are the two consequences of that one fact rather than two
     *     independent settings
     */
    ThemeId(String id, String label, String overlayFile, boolean highContrast, boolean glass) {
        this.id = id;
        this.label = label;
        this.overlayFile = overlayFile;
        this.highContrast = highContrast;
        this.glass = glass;
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
     * Whether this theme rounds windows on its own, without the player having set §9.3's switch.
     *
     * <h2>⚠ Why this is not simply "turn the setting on"</h2>
     *
     * The tempting implementation is for the theme to write {@code roundedWindows = true} when it is
     * selected. That destroys the player's own choice: they picked square, tried a look for thirty
     * seconds, went back, and their deck is now permanently round with nothing to say why. A theme
     * is a costume and must be removable without leaving anything behind — the same rule the setup
     * assistant follows by previewing appearance on a detached {@code VisualSettings}.
     *
     * <p>So the setting is never written. It is <b>OR-ed with this</b> at the two places that shape
     * a window, and switching away restores exactly what the player had.
     *
     * <p>⚠ It deliberately reuses the existing {@code .es-rounded} class rather than introducing a
     * theme-scoped radius. {@code UiContractTest} permits a non-zero radius only under that
     * selector, and every widget that has opted in is already gated on it — so a glass theme gets
     * the whole rounded vocabulary for free, and §9.3's ⚠ "never round a measurement" boundary keeps
     * protecting it with no second rule to keep in step. A parallel selector would have been a
     * second place for that boundary to be forgotten.
     */
    public boolean roundsCorners() {
        return glass;
    }

    /**
     * Whether windows paint a blurred picture of what is behind them.
     *
     * <h2>⚠ This is the only real backdrop blur in the client, and it is not CSS</h2>
     *
     * JavaFX has no backdrop filter, so {@code ui/chrome/Frost} does it by snapshotting the desk
     * beneath each window and blurring the image. That is expensive enough that it must be a
     * property of the palette rather than always on — the five opaque themes would pay for a picture
     * nothing can see through.
     *
     * <p>Tied to the same two themes as {@link #roundsCorners} and deliberately not a separate
     * setting: a translucent panel with no blur behind it is the state that reads as a rendering
     * fault, so being glass and being frosted are one decision.
     */
    public boolean frostsBackdrop() {
        return glass;
    }

    /**
     * Whether windows are round right now: the player's §9.3 setting, or a theme that implies it.
     *
     * <p>⚠ <b>One place answers this, and that is the point.</b> Two call sites shape a window — the
     * Scene root's clip in {@code EyeAndSickleClient} and the desk's frames in {@code DeckShell} —
     * and CLAUDE.md records this exact family of bug three times over: a global appearance flag that
     * reached new objects and not live ones, or one half of a pair that was never told. Two call
     * sites reading a two-term condition is one call site away from a deck whose outer corner is
     * round and whose windows are square.
     */
    public static boolean cornersAreRounded(boolean playerSetting, ThemeId theme) {
        return playerSetting || (theme != null && theme.roundsCorners());
    }

    /**
     * The same question, asked of an appearance.
     *
     * <p>⚠ Reads the theme from the {@code VisualSettings} rather than from {@code ThemeManager},
     * deliberately. That class caches the current id in a property and its own Javadoc records the
     * trap: {@code useCharacterAppearance} swaps the whole {@code VisualSettings} behind its back,
     * so the cache can be describing the palette of whoever was loaded before. The appearance is
     * what the cache is populated <em>from</em>, so asking it directly cannot go stale.
     */
    public static boolean cornersAreRounded(io.github.stoicswe.eyeandsickle.client.profile.VisualSettings appearance) {
        if (appearance == null) {
            return false;
        }
        return cornersAreRounded(
                appearance.roundedWindows, byId(appearance.themeId).orElse(DECK));
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
