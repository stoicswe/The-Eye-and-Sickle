package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

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
            "theme-hc.css");

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

    private static Map<String, String> tokensOf(String file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m =
                Pattern.compile("(-es-[a-z0-9-]+):\\s*(#[0-9A-Fa-f]{6})").matcher(Files.readString(UI.resolve(file)));
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

    private static double relativeLuminance(String hex) {
        String h = hex.substring(1);
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16) / 255.0d;
            c[i] = v <= 0.03928d ? v / 12.92d : Math.pow((v + 0.055d) / 1.055d, 2.4d);
        }
        return 0.2126d * c[0] + 0.7152d * c[1] + 0.0722d * c[2];
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
            String panel = palette.get("-es-panel");
            assertThat(panel).as("%s declares a panel colour", theme).isNotNull();

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
            String raised = palette.get("-es-panel-hi");
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
            String panel = palette.get("-es-panel");
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
}
