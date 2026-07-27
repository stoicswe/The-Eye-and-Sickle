package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The desk wallpaper and the CRT artefact layer, checked without a toolkit.
 *
 * <p>Same constraint {@code UiContractTest} states: nothing here may need a display, so these check
 * the parts that are text — the persisted vocabulary, the stylesheet, and the design document's own
 * rules — and the <em>look</em> is checked by rendering with {@code DeckSnapshot}. A wallpaper is
 * exactly the kind of change a green build reports as done while it draws nothing at all.
 */
class ScreenArtefactTest {

    private static final Path THEME =
            Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/ui/theme.css");

    private static final Path DESIGN_LANGUAGE = Path.of("../docs/design/ui-design-language.md");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("the wallpaper's persisted vocabulary")
    class Modes {

        @Test
        @DisplayName("every mode round-trips through its id")
        void idsRoundTrip() {
            for (WallpaperMode mode : WallpaperMode.values()) {
                assertThat(WallpaperMode.byId(mode.id())).contains(mode);
            }
        }

        @Test
        @DisplayName("an unknown id falls back instead of throwing")
        void unknownIdIsEmpty() {
            // A profile written by a client with one more mode than this one must still load. The
            // caller substitutes a default; the player does not lose their settings file to an enum.
            assertThat(WallpaperMode.byId("kaleidoscope")).isEmpty();
            assertThat(WallpaperMode.byId("")).isEmpty();
        }

        @Test
        @DisplayName("STILL exists as its own state — WCAG 2.2.2 needs a pause, not just an off")
        void pauseIsDistinctFromOff() {
            // Folding pause into "off" would satisfy the letter of Pause, Stop, Hide and lose the
            // point: the player who wants texture without movement would have to give up both.
            assertThat(WallpaperMode.values()).hasSize(3);
            assertThat(WallpaperMode.STILL).isNotEqualTo(WallpaperMode.OFF);
        }
    }

    @Nested
    @DisplayName("§9.1 — artefacts are opt-in, and the defaults prove it")
    class Defaults {

        @Test
        @DisplayName("all three artefacts ship off, and the wallpaper ships on")
        void shippedDefaults() {
            ClientProfile.Settings fresh = new ClientProfile.Settings();

            // §9.1 condition 1: an effect the player switches on is a costume; one welded to the
            // interface is a claim about fidelity the interface then has to keep making.
            assertThat(fresh.crtScanlines).as("scanlines cost contrast on body text").isFalse();
            assertThat(fresh.crtAberration).isFalse();
            assertThat(fresh.crtGlitch).as("the only artefact that moves").isFalse();

            // The wallpaper is not an artefact — it is greeble, which §9 makes build-blocking to
            // remove. It ships on, and drifting, with off and still one setting away.
            assertThat(WallpaperMode.byId(fresh.wallpaper)).contains(WallpaperMode.DRIFT);
        }
    }

    @Nested
    @DisplayName("the stylesheet says what it means to say")
    class Stylesheet {

        @Test
        @DisplayName("scanlines use JavaFX's repeat spelling, not the web one that fails silently")
        void scanlinesUseTheJavaFxGradientSpelling() throws IOException {
            String css = read(THEME);
            // ⚠ This is the trap, and it is why the test exists rather than a code comment alone:
            // `repeating-linear-gradient` parses as an unknown function and the whole declaration is
            // dropped at RUNTIME with no error. Green build, no scanlines, nothing to grep for.
            //
            // Comments are stripped first, and that is not a loophole — theme.css *documents* the
            // trap by name right above the rule, and a check that could not tell a warning from a
            // violation would make writing the warning down the thing that failed the build.
            String declarations = css.replaceAll("(?s)/\\*.*?\\*/", "");
            assertThat(declarations)
                    .as("repeating-linear-gradient does not exist in JavaFX")
                    .doesNotContain("repeating-linear-gradient");
            String rule = ruleFor(css, ".es-crt-scanlines");
            assertThat(rule).contains("linear-gradient(").contains("repeat");
        }

        @Test
        @DisplayName("the scanline period in CSS matches the one Java rolls by")
        void scanPeriodAgrees() throws IOException {
            // ⚠ The period genuinely lives in two places: the gradient's `to 0px Npx` in CSS, and
            // SCAN_PERIOD in Java, which the drift wraps at. JavaFX cannot look a SIZE up from CSS
            // (§7.2 — looked-up values are colours only), so Java cannot read the pattern it is
            // rolling. If they drift apart the lines jump once per cycle instead of wrapping, which
            // looks like a rendering stutter rather than like a mistake in a constant.
            String rule = ruleFor(read(THEME), ".es-crt-scanlines");
            Matcher period = Pattern.compile("to\\s+0px\\s+(\\d+)px").matcher(rule);
            assertThat(period.find()).as("the gradient states its period").isTrue();
            assertThat(Integer.parseInt(period.group(1)))
                    .as("CSS gradient period must equal CrtOverlay.SCAN_PERIOD")
                    .isEqualTo(CrtOverlay.scanPeriod());
        }

        @Test
        @DisplayName("the refresh bar is near-transparent, which is what §9 permits a gradient to be")
        void theRollBarIsNearlyInvisible() throws IOException {
            // §9 allows gradients only where they are "hard-edged or near-transparent". This is the
            // one soft-edged thing in the client, so the alpha is the whole justification: every
            // stop has to stay far below anything that would read as a shape.
            String rule = ruleFor(read(THEME), ".es-crt-roll");
            assertThat(rule).contains("linear-gradient(");
            Matcher alpha = Pattern.compile("rgba\\([^)]*?,\\s*([0-9.]+)\\s*\\)").matcher(rule);
            boolean sawOne = false;
            while (alpha.find()) {
                sawOne = true;
                assertThat(Double.parseDouble(alpha.group(1)))
                        .as("a refresh-bar stop must stay near-transparent")
                        .isLessThanOrEqualTo(0.08);
            }
            assertThat(sawOne).as("the bar states its stops as rgba").isTrue();
        }

        @Test
        @DisplayName("the wallpaper never spends the accent — §2.1 reserves amber for live data")
        void theWallpaperIsNeverAmber() throws IOException {
            String css = read(THEME);
            // The largest surface in the client. An accent here would out-shout every cycle cell
            // and every income figure that actually earned it.
            for (String selector : java.util.List.of(
                    ".es-substrate", ".es-substrate-field", ".es-substrate-warm", ".es-substrate-cool")) {
                assertThat(ruleFor(css, selector))
                        .as("%s must not reach for the accent", selector)
                        .doesNotContain("-es-amber");
            }
        }

        @Test
        @DisplayName("no artefact reaches for a blur or a glow — §9 still cuts those")
        void artefactsAreHardEdged() throws IOException {
            String css = read(THEME);
            for (String selector : java.util.List.of(
                    ".es-crt-scanlines", ".es-crt-band", ".es-crt-fringe-warm", ".es-crt-fringe-cool")) {
                assertThat(ruleFor(css, selector))
                        .as("%s is a hard-edged band, like the real artefact", selector)
                        .doesNotContain("dropshadow(")
                        .doesNotContain("gaussian")
                        .doesNotContain("innershadow(");
            }
        }
    }

    @Nested
    @DisplayName("the amendment did not quietly become a licence")
    class Amendment {

        @Test
        @DisplayName("bezel and vignette are still cut, in the document's own words")
        void bezelAndVignetteRemainBanned() throws IOException {
            // The 2026-07-26 amendment permitted scanlines, aberration and light glitch. It named
            // two survivors, and this is what stops a later reading from taking the whole entry as
            // repealed — which is exactly how a rejection list dies.
            String doc = read(DESIGN_LANGUAGE);
            assertThat(doc).contains("**Bezel**").contains("**Vignette**");
            assertThat(doc)
                    .as("§9.1 must state the switchability condition, which is the whole trade")
                    .contains("off by default and switchable off permanently");
        }

        @Test
        @DisplayName("there is no vignette rule anywhere in the stylesheet")
        void noVignetteSnuckIn() throws IOException {
            String css = read(THEME);
            // A vignette in this codebase would look like a radial gradient — there is no other way
            // to draw one — so its absence is checkable rather than a matter of trust.
            assertThat(css).as("edge darkening is still cut (§9)").doesNotContain("radial-gradient");
        }
    }

    /** The declaration block for a selector, comments stripped. */
    private static String ruleFor(String css, String selector) {
        String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher matcher = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(body);
        StringBuilder found = new StringBuilder();
        while (matcher.find()) {
            found.append(matcher.group(1)).append('\n');
        }
        assertThat(found.length()).as("theme.css declares %s", selector).isGreaterThan(0);
        return found.toString();
    }
}
