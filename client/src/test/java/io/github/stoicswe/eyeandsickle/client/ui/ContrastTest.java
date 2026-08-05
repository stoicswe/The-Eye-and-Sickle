package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every palette is legible, measured rather than assumed.
 *
 * <h2>What this is, and what it is deliberately NOT</h2>
 *
 * The obvious way to guarantee readable text is a runtime layer that inspects the theme and adjusts
 * colours until they pass. This is not that, and the reason is the design language rather than
 * effort: {@code ui-design-language.md} §10 criterion 2 requires every colour to be a looked-up token
 * declared in a stylesheet, and {@code CLAUDE.md} puts it plainly — <b>colours live in
 * {@code theme.css} and nowhere else</b>. A layer that computed a colour at run time would make the
 * rendered palette unpredictable, unreviewable, and impossible to state in a document; it would also
 * quietly overrule the deliberate choices each theme makes, which is the opposite of preserving them.
 *
 * <p>So the guarantee is enforced <b>at build time, against each theme's own colours</b>. Nothing here
 * invents a value. It computes the real WCAG contrast ratio for each pair the interface actually
 * draws and fails the build when a theme's own palette cannot carry its own text — which puts the fix
 * where it belongs, in the stylesheet, chosen by a person.
 *
 * <h2>⚠ What this caught</h2>
 *
 * {@code -es-dim-3} is the <b>greeble</b> colour — decorative texture — and the network map was using
 * it for the CONTACT and LOCKED node states. Measured: <b>1.77:1</b> on the deck palette and
 * <b>2.06:1</b> on uOS Classic, whose light ramp made those nodes vanish outright. A state a player
 * has to read is not "quiet" at 1.8:1, it is missing. Also found: the deck's own {@code -es-dim-2} at
 * 2.78:1, the single token in that palette under the floor, carrying server and layer labels.
 */
class ContrastTest {

    private static final Path UI = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/ui");

    /**
     * The floor.
     *
     * <p>WCAG 2.1 AA asks 4.5:1 for body text and 3:1 for large text and UI components. This client
     * sets everything in a monospace face at readout sizes and its quiet states are deliberately
     * quiet, so <b>3:1</b> is the line: it is the point below which a thing stops being subdued and
     * starts being absent. ⚠ It is a floor, not a target — {@code -es-text} sits near 10:1 on every
     * palette and should stay there.
     */
    private static final double FLOOR = 3.0;

    /** Every palette overlay, plus the base. */
    private static final List<String> THEMES = List.of(
            "theme.css",
            "theme-classic.css",
            "theme-phosphor.css",
            "theme-amber.css",
            "theme-cyberdeck.css",
            "theme-hc.css",
            "theme-liquid-dark.css",
            "theme-liquid-light.css");

    /**
     * Tokens that carry TEXT and must therefore clear the floor against the panel they sit on.
     *
     * <p>⚠ The exemptions are as important as the list. {@code -es-rule} and {@code -es-rule-hi} draw
     * <b>lines</b> — panel borders, the map's connector edges — and {@code -es-dim-3} draws greeble
     * and texture. Holding a hairline to a text threshold would force every rule in the interface to
     * become a visible stripe, which would be this test making the design worse in the name of
     * accessibility. They are excluded by name, on purpose, and anything that starts drawing text in
     * them belongs in the list above instead.
     */
    private static final List<String> TEXT_TOKENS = List.of(
            "-es-text", "-es-text-hi", "-es-dim-1", "-es-dim-2", "-es-amber", "-es-alarm", "-es-gain", "-es-warn");

    /**
     * ⚠ THE ALPHA PAIR IS OPTIONAL AND CAPTURING IT IS NOT COSMETIC.
     *
     * <p>This pattern read {@code (#[0-9A-Fa-f]{6})} until uOS Modern Liquid Abs landed with translucent
     * panels. Against an eight-digit {@code #RRGGBBAA} that regex does not fail — <b>it matches the
     * first six digits and drops the alpha</b>, so the whole suite would have gone on measuring text
     * against a panel colour that is never on screen, and reported a pass. A check that silently
     * measures the wrong thing is worse than no check, because it is believed.
     */
    private static final Pattern TOKEN = Pattern.compile("(-es-[a-z0-9-]+):\\s*(#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)");

    private static Map<String, String> tokensOf(String file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(Files.readString(UI.resolve(file)));
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    /** A palette is the base overlaid with the theme's own declarations. */
    private static Map<String, String> palette(String theme) throws IOException {
        Map<String, String> merged = new LinkedHashMap<>(tokensOf("theme.css"));
        merged.putAll(tokensOf(theme));
        return merged;
    }

    private static int channel(String hex, int index) {
        return Integer.parseInt(hex.substring(1 + index * 2, 3 + index * 2), 16);
    }

    /** 1.0 for the six-digit tokens every opaque palette uses. */
    private static double alphaOf(String hex) {
        return hex.length() == 9 ? channel(hex, 3) / 255.0d : 1.0d;
    }

    /**
     * Source-over compositing — what the player actually sees.
     *
     * <p>⚠ This is the whole reason the checks below are trustworthy for a glass palette. A
     * translucent panel's contrast is not a property of its own hex; it is a property of the hex
     * <em>and of what is behind it</em>. Measuring the token alone would have declared uOS Modern
     * Liquid legible without ever computing the surface its text sits on.
     *
     * @param background must be opaque — the desk is the bottom of the stack
     */
    private static String over(String foreground, String background) {
        double a = alphaOf(foreground);
        StringBuilder out = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            long v = Math.round(channel(foreground, i) * a + channel(background, i) * (1 - a));
            out.append(String.format("%02X", v));
        }
        return out.toString();
    }

    private static double relativeLuminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = channel(hex, i) / 255.0d;
            c[i] = v <= 0.03928d ? v / 12.92d : Math.pow((v + 0.055d) / 1.055d, 2.4d);
        }
        return 0.2126d * c[0] + 0.7152d * c[1] + 0.0722d * c[2];
    }

    /** Whether the theme owning this stylesheet paints a blurred backdrop ({@code ui/chrome/Frost}). */
    private static boolean frosts(String sheet) {
        return java.util.Arrays.stream(ThemeId.values())
                .filter(id -> id.overlayStylesheet()
                        .map(path -> path.endsWith("/" + sheet))
                        .orElse(false))
                .anyMatch(ThemeId::frostsBackdrop);
    }

    /** The window body as rendered: the panel token composited over the desk. */
    private static String panelOf(Map<String, String> palette) {
        return over(palette.get("-es-panel"), palette.get("-es-void"));
    }

    /**
     * The raised surface as rendered.
     *
     * <p>⚠ Composited over the <b>panel</b>, not over the desk. A header strip sits inside a window,
     * so with a translucent palette two glass layers stack and the result is darker than the token
     * suggests. Compositing it over the desk would measure a surface that exists nowhere.
     */
    private static String raisedOf(Map<String, String> palette) {
        return over(palette.get("-es-panel-hi"), panelOf(palette));
    }

    /** WCAG 2.1's contrast ratio. */
    private static double contrast(String a, String b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05d) / (Math.min(la, lb) + 0.05d);
    }

    @Test
    @DisplayName("every text colour clears 3:1 against the panel it is drawn on, in every theme")
    void textIsLegibleInEveryTheme() throws IOException {
        for (String theme : THEMES) {
            Map<String, String> palette = palette(theme);
            assertThat(palette.get("-es-panel"))
                    .as("%s declares a panel colour", theme)
                    .isNotNull();
            String panel = panelOf(palette);

            for (String token : TEXT_TOKENS) {
                String colour = palette.get(token);
                assertThat(colour).as("%s declares %s", theme, token).isNotNull();
                assertThat(contrast(colour, panel))
                        .as("%s: %s (%s) on the panel (%s) — a state a player must read", theme, token, colour, panel)
                        .isGreaterThanOrEqualTo(FLOOR);
            }
        }
    }

    /**
     * ⚠ The elevated panel is a different background, and the same text sits on it.
     *
     * <p>{@code -es-panel-hi} is the focused window's strip and several insets. A palette that was
     * legible on the panel and not on the raised one would fail exactly where the player is looking.
     */
    @Test
    @DisplayName("the same colours clear the floor on the raised panel too")
    void textIsLegibleOnTheRaisedPanel() throws IOException {
        for (String theme : THEMES) {
            Map<String, String> palette = palette(theme);
            String raised = raisedOf(palette);
            for (String token : TEXT_TOKENS) {
                assertThat(contrast(palette.get(token), raised))
                        .as("%s: %s on the raised panel", theme, token)
                        .isGreaterThanOrEqualTo(FLOOR);
            }
        }
    }

    /**
     * ⚠ The quiet states must stay quiet, or this test would have "fixed" the design.
     *
     * <p>Raising a floor is only correct if the hierarchy above it survives. A contact is meant to
     * read as less than an identified host — {@code docs/design/07} sells knowing <em>what</em> a
     * machine is as the Passive Sniffer's whole job — so the quiet token has to remain visibly below
     * body text. If a future change flattened them, the map would stop teaching what it charges for.
     */
    @Test
    @DisplayName("quiet is still quieter than loud — the floor did not flatten the hierarchy")
    void hierarchySurvivesTheFloor() throws IOException {
        for (String theme : THEMES) {
            Map<String, String> palette = palette(theme);
            String panel = panelOf(palette);
            double quiet = contrast(palette.get("-es-dim-1"), panel);
            double body = contrast(palette.get("-es-text"), panel);
            assertThat(quiet)
                    .as("%s: the quiet state must stay below body text", theme)
                    .isLessThan(body);
        }
    }

    /**
     * ⚠ uOS Classic inverts the ramp, and that is the case everything else gets wrong.
     *
     * <p>It is the only light palette, so a token chosen by eye on a dark screen — "a dim grey" —
     * becomes a light grey on a light panel and disappears. This asserts the inversion is real rather
     * than a numeric flip, which is the mistake its own header warns about.
     */
    @Test
    @DisplayName("Classic is a light palette and its text is dark, not merely inverted")
    void classicInvertsProperly() throws IOException {
        Map<String, String> classic = palette("theme-classic.css");
        assertThat(relativeLuminance(classic.get("-es-panel")))
                .as("Classic's panel is light")
                .isGreaterThan(0.5d);
        assertThat(relativeLuminance(classic.get("-es-text")))
                .as("and its body text is dark")
                .isLessThan(0.2d);
    }

    /**
     * ⚠ §9.4 condition 2, and the one bound a glass palette can actually breach.
     *
     * <h2>What goes wrong, and why nothing else here would catch it</h2>
     *
     * JavaFX has no backdrop filter, so a translucent panel does not blur what is behind it — it
     * shows it, sharp. Desk windows overlap. So a panel whose alpha is tuned by eye on a bare desk
     * (where the only thing behind is the wallpaper, and it looks wonderful) turns into two columns
     * of interleaved monospace the moment the player drags a second window under the first. Every
     * other assertion in this class passes throughout: the panel's own text is still perfectly
     * legible against the panel. The damage is done by content that is not the panel's at all.
     *
     * <h2>⚠ The bound was WEAKENED on 2026-08-05, deliberately, and this records what was given up</h2>
     *
     * It was <b>"no more legible than greeble"</b> — {@code -es-dim-3}, the design language's own name
     * for texture deliberately below the threshold of being read. That is the stronger claim and it is
     * the one to restore if the direction ever changes; it caps transmission at around 5%.
     *
     * <p>uOS Modern Liquid Abs was then directed to be <em>very</em> transparent glass, which is
     * incompatible with it: the palettes now transmit 40–60%, and at that level a window behind is
     * unmistakably present. So the guarantee is now the weaker but still meaningful one — <b>what
     * shows through must stay below {@link #FLOOR}</b>, the ratio at which this client considers a
     * thing legible at all. Above it a second screen is readable under every overlapping window;
     * below it there is visibly something there and it cannot be read as text.
     *
     * <p><b>What that costs, stated plainly:</b> the backdrop is no longer merely a material. A player
     * who overlaps two windows will see shapes moving under the top one. That is the trade the
     * transparency buys, it was made on explicit direction, and it is the reason the light film in
     * {@code theme-liquid-dark.css} matters so much — a light film compresses a dark backdrop's range
     * hard, which is what keeps the ghost under the floor at all, where a mid-toned film at the same
     * alpha measures 4.95:1 and is plainly readable.
     *
     * <h2>⚠ THIS BOUND IS NECESSARY AND NOT SUFFICIENT — the palettes are tuned BELOW it</h2>
     *
     * A build that measured <b>2.78:1</b> here, comfortably passing, rendered the notification stack
     * over the LOG window as two columns of text in the same pixels. Each was individually below the
     * legibility floor and the pair was unreadable, because <b>this is a per-pair luminance ratio and
     * cannot see two texts competing for the same glyph cells.</b> The shipped transmission is set by
     * rendering, not by this number, and it sits well under the line.
     *
     * <p>The other half of that fix is {@code -es-float}: a window may be glass because what is behind
     * it is usually the desk, but a toast, dialog, menu or tooltip is <em>always</em> over content and
     * is therefore opaque in these palettes. <b>Passing this test does not mean a palette is
     * legible — render it.</b>
     *
     * <p>The worst case is measured, not a sample: the brightest text this palette draws, on the
     * raised surface, seen through the panel.
     */
    @Test
    @DisplayName("⚠ transmission and blur are COUPLED — a see-through palette must frost (§9.4)")
    void transmissionRequiresFrost() throws IOException {
        // ⚠ THE ONE RULE THAT REPLACED THE GHOST BOUND, and the reason it had to.
        //
        // The bound below models a translucent panel over a SHARP backdrop, and asks whether what
        // shows through can be read. Once `ui/chrome/Frost` landed, that model stopped describing
        // the liquid palettes: what shows through them is a Gaussian blur of the desk, so it is
        // unreadable at any alpha and the arithmetic answers a question nobody is asking.
        //
        // What is still true, and is now the thing worth guarding, is that the two are a PAIR. A
        // panel at 86% transmission is safe only because there is a blur behind it. Lowering some
        // future palette's alpha without declaring `frostsBackdrop()` puts a sharp, fully legible
        // second screen under every window — the exact failure the ghost bound existed to stop,
        // reached from the other direction.
        double sharpLimit = 0.35d; // transmission a palette may have with no blur behind it
        for (ThemeId theme : ThemeId.values()) {
            String sheet = theme.overlayStylesheet()
                    .map(path -> path.substring(path.lastIndexOf('/') + 1))
                    .orElse("theme.css");
            double transmission = 1.0d - alphaOf(palette(sheet).get("-es-panel"));
            if (transmission <= sharpLimit || theme.frostsBackdrop()) {
                continue;
            }
            org.assertj.core.api.Assertions.fail(
                    "%s transmits %.0f%% and does not frost its backdrop. Without a blur behind it "
                            + "that is a sharp, readable second screen under every overlapping "
                            + "window. Either declare the theme glass (ThemeId, which turns on "
                            + "ui/chrome/Frost) or keep transmission at or under %.0f%%.",
                    sheet, transmission * 100, sharpLimit * 100);
        }
    }

    @Test
    @DisplayName("⚠ a window behind UNFROSTED glass is never READABLE through it (§9.4 condition 2)")
    void whatShowsThroughIsNotReadable() throws IOException {
        for (String theme : THEMES) {
            Map<String, String> palette = palette(theme);
            if (alphaOf(palette.get("-es-panel")) >= 1.0d) {
                continue; // an opaque palette transmits nothing; there is no backdrop to measure
            }
            if (frosts(theme)) {
                // Blurred by ui/chrome/Frost, so "can it be read" is answered by the blur rather
                // than by the alpha. transmissionRequiresFrost above is what holds the pairing.
                continue;
            }
            String panelToken = palette.get("-es-panel");
            String behindSurface = raisedOf(palette);
            String behindText = palette.get("-es-text-hi");

            // The same panel drawn over each: over the window behind it, and over that window's text.
            String seenSurface = over(panelToken, behindSurface);
            String seenText = over(panelToken, behindText);
            double ghost = contrast(seenText, seenSurface);

            assertThat(ghost)
                    .as(
                            "%s: text on a window BEHIND the glass reads at %.2f:1 through it, at or "
                                    + "above the %.1f:1 floor this client treats as legible. JavaFX "
                                    + "cannot blur a backdrop, so that is a readable second screen "
                                    + "under every overlapping window (§9.4 condition 2). Either "
                                    + "lower the transmission or lighten the film — a light film "
                                    + "compresses a dark backdrop far harder than a mid-toned one at "
                                    + "the same alpha.",
                            theme, ghost, FLOOR)
                    .isLessThan(FLOOR);
        }
    }
}
