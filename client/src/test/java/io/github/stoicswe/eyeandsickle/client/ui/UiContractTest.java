package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.chrome.WindowFrame;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorRole;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Machine-checks {@code docs/design/ui-design-language.md} §10's acceptance criteria.
 *
 * <h2>Why these are tests and not review notes</h2>
 *
 * Every rule in §9 is the kind that survives review nine times and then loses to one plausible
 * exception — "just this dialog needs an ease-out", "just this one hex, it's a one-off". The design
 * language calls them build-blocking, so they block the build. Three of the four checks below scan
 * source text, which is crude and exactly right for a rule about what may appear in source text.
 *
 * <p>Nothing here needs a live toolkit, deliberately: a contract test that only runs on a machine
 * with a display is a contract test that does not run in CI.
 */
class UiContractTest {

    private static final Path CLIENT_SOURCE = Path.of("src/main/java");
    private static final Path CLIENT_RESOURCES = Path.of("src/main/resources");
    private static final String UI_RESOURCES = "io/github/stoicswe/eyeandsickle/client/ui/";

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(CLIENT_SOURCE)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    @Nested
    @DisplayName("§10 criterion 7 — no easing curve anywhere")
    class Motion {

        @Test
        @DisplayName("Interpolator.EASE_* appears nowhere in the client")
        void noEasing() throws IOException {
            // §5: "Any spring, bounce, or ease-out reads as web UI immediately and will undo the
            // whole aesthetic." This is the single easiest rule to break by accident, because
            // EASE_BOTH is what every JavaFX tutorial reaches for.
            List<String> offenders = new ArrayList<>();
            for (Path source : javaSources()) {
                String body = stripComments(read(source));
                if (body.contains("Interpolator.EASE") || body.contains("Interpolator.SPLINE")) {
                    offenders.add(source.toString());
                }
            }
            assertThat(offenders)
                    .as("easing curves are build-blocking (ui-design-language.md §9)")
                    .isEmpty();
        }

        @Test
        @DisplayName("§5.1 — continuous motion is rationed to the firmware handover")
        void animationTimerIsRationed() throws IOException {
            // ⚠ This assertion exists because the LINEAR one above cannot see the alternative. A
            // Timeline + KeyValue interpolates with Interpolator.LINEAR by DEFAULT, so a fade could
            // be added without the word appearing anywhere — passing the check by not tripping it.
            // AnimationTimer is the honest way to write a continuous ramp, and this rations it by
            // name so a second one is a decision somebody makes on purpose.
            //
            // Both permitted users are the power-on splash (§5.1): the progress bar, and the fade
            // that hands over to the login screen. Neither is a surface the player works inside.
            List<String> users = new ArrayList<>();
            for (Path source : javaSources()) {
                if (stripComments(read(source)).contains("AnimationTimer")) {
                    users.add(source.getFileName().toString());
                }
            }
            assertThat(users)
                    .as("continuous per-frame motion is permitted only on the splash (§5.1)")
                    .containsExactlyInAnyOrder("Fade.java", "PowerOn.java");
        }

        @Test
        @DisplayName("the only LINEAR interpolation is the sweep bar §5 asks for")
        void linearIsRationed() throws IOException {
            // LINEAR is permitted — §5 specifies a linear sweep loop — but it is the one continuous
            // motion in the product, so a second user of it is a decision someone should have to
            // make on purpose rather than by copying this line.
            List<String> users = new ArrayList<>();
            for (Path source : javaSources()) {
                if (stripComments(read(source)).contains("Interpolator.LINEAR")) {
                    users.add(source.getFileName().toString());
                }
            }
            assertThat(users).containsExactly("SweepPanel.java");
        }
    }

    @Nested
    @DisplayName("§10 criterion 2 — every colour is a looked-up colour")
    class Colour {

        private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");

        @Test
        @DisplayName("no hex literal appears in any ui class")
        void noHexInJava() throws IOException {
            // The split the design language mandates: colours live in theme.css and nothing else
            // does; sizes and durations live in UiTokens and nothing else does. A Color.web("#...")
            // in a widget is a colour that no palette overlay can reach, which silently breaks
            // every theme except the one it was written against.
            List<String> offenders = new ArrayList<>();
            for (Path source : javaSources()) {
                if (!source.toString().contains("/client/ui/") && !source.toString().contains("/client/view/")) {
                    continue;
                }
                String body = stripComments(read(source));
                Matcher matcher = HEX.matcher(body);
                while (matcher.find()) {
                    offenders.add(source.getFileName() + " → " + matcher.group());
                }
            }
            assertThat(offenders).as("hex literals belong in theme.css, never in Java").isEmpty();
        }

        @Test
        @DisplayName("every palette token the stylesheet promises is actually declared")
        void everyTokenExists() throws IOException {
            String css = Files.readString(CLIENT_RESOURCES.resolve(UI_RESOURCES + "theme.css"));
            for (String token : List.of(
                    "-es-void", "-es-panel", "-es-panel-hi", "-es-rule", "-es-rule-hi",
                    "-es-dim-3", "-es-dim-2", "-es-dim-1", "-es-text", "-es-text-hi",
                    "-es-amber", "-es-amber-mid", "-es-amber-low", "-es-alarm", "-es-tool")) {
                assertThat(css).as("theme.css declares %s", token).contains(token + ":");
            }
        }

        @Test
        @DisplayName("nothing in the stylesheet reaches for a rounded corner or a shadow")
        void rejectionListHolds() throws IOException {
            // §9, the parts a stylesheet can violate. -fx-background-radius and -fx-border-radius
            // appear only as explicit zeroes overriding Modena, which is why the assertion is on
            // "a non-zero radius" rather than on the property name.
            String css = Files.readString(CLIENT_RESOURCES.resolve(UI_RESOURCES + "theme.css"));
            String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
            assertThat(body)
                    .as("no drop shadows, blurs or glows (§9)")
                    .doesNotContain("dropshadow(")
                    .doesNotContain("gaussian")
                    .doesNotContain("innershadow(");
            // ⚠ §9 AMENDED 2026-07-28: a non-zero radius is permitted, but ONLY under `.es-rounded`
            // — the opt-in the player turns on in Settings, off by default. So the assertion is no
            // longer "radius is always 0"; it is "radius is 0 unless the rule is gated on that
            // class". Which keeps the shipped appearance square and keeps the setting honest.
            //
            // Checked by scanning back to the start of each declaration block, because a radius
            // smuggled into an ungated rule is exactly the drift this test exists to catch.
            Matcher radius = Pattern.compile("-fx-(background|border)-radius:\\s*([^;]+);").matcher(body);
            while (radius.find()) {
                if (radius.group(2).trim().equals("0")) {
                    continue;
                }
                int blockStart = body.lastIndexOf('{', radius.start());
                int selectorStart = Math.max(0, body.lastIndexOf('}', blockStart) + 1);
                String selector = body.substring(selectorStart, blockStart);
                assertThat(selector)
                        .as("a non-zero radius may only appear under .es-rounded (§9, amended)")
                        .contains(".es-rounded");
            }
        }
    }

    @Nested
    @DisplayName("§9 amended — the rounded-window opt-in stays narrow")
    class RoundedOptIn {

        @Test
        @DisplayName("⚠ it rounds WINDOWS, and never anything a measurement is read off")
        void nothingMeasurableIsRounded() throws IOException {
            // Rounding a window is taste. Rounding a meter cell, the cycle grid or a hazard band is
            // a lie about a number — a cell with a soft corner reads as a smaller cell, and the
            // entire point of a discrete meter (§4) is that a player can count it.
            String css = Files.readString(CLIENT_RESOURCES.resolve(UI_RESOURCES + "theme.css"));
            String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
            Matcher radius = Pattern.compile("-fx-(background|border)-radius:\\s*([^;]+);").matcher(body);
            while (radius.find()) {
                if (radius.group(2).trim().equals("0")) {
                    continue;
                }
                int blockStart = body.lastIndexOf('{', radius.start());
                int selectorStart = Math.max(0, body.lastIndexOf('}', blockStart) + 1);
                String selector = body.substring(selectorStart, blockStart);
                for (String forbidden : java.util.List.of(
                        "es-cell", "es-meter", "es-cycle", "es-hazard", "es-greeble",
                        "es-substrate", "es-block", "es-tick")) {
                    assertThat(selector)
                            .as("%s must never be rounded — it is a measurement", forbidden)
                            .doesNotContain(forbidden);
                }
            }
        }

        @Test
        @DisplayName("the shipped default is square")
        void defaultIsSquare() {
            // The setting exists; it is not the default. §9's rejection list still describes what
            // this client looks like out of the box.
            assertThat(new io.github.stoicswe.eyeandsickle.client.profile.VisualSettings()
                            .roundedWindows)
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("§10 criterion 3 — both bundled fonts ship")
    class Typefaces {

        @Test
        @DisplayName("every TTF the design language names is in the jar")
        void fontsArePresent() {
            // Not loaded — loading needs a toolkit. Presence on the classpath is what actually
            // fails in a packaging mistake, which is the failure this guards: §2.2 says do not rely
            // on system installs, and neither face is on a default macOS, Windows or Linux image.
            for (String file : List.of(
                    "MartianMono-Medium.ttf",
                    "MartianMono-Bold.ttf",
                    "IBMPlexMono-Light.ttf",
                    "IBMPlexMono-Regular.ttf",
                    "IBMPlexMono-Medium.ttf")) {
                assertThat(getClass().getResource(
                                "/io/github/stoicswe/eyeandsickle/client/fonts/" + file))
                        .as("%s is bundled", file)
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("both families are named in the stylesheet, and no proportional face is")
        void noProportionalType() throws IOException {
            // §9: "Proportional (non-mono) type anywhere, including body copy" is build-blocking.
            String css = Files.readString(CLIENT_RESOURCES.resolve(UI_RESOURCES + "theme.css"));
            assertThat(css).contains(UiTokens.DISPLAY_FAMILY).contains(UiTokens.BODY_FAMILY);
            for (String proportional : List.of(
                    "Helvetica", "Arial", "Segoe UI", "Roboto", "system-ui", "sans-serif", "serif")) {
                assertThat(css).as("no proportional face (§9)").doesNotContain(proportional);
            }
        }
    }

    @Nested
    @DisplayName("§10 criterion 6 — the notch does not distort")
    class Notch {

        @Test
        @DisplayName("the cut stays 18px at every window width")
        void cutIsConstant() {
            // The reason -fx-shape is unusable (§7.2): it scales the shape to the region. These
            // three widths are the criterion's "three window widths", and the assertion is that the
            // horizontal and vertical legs of the diagonal are equal and constant — which is what
            // "45°, 18px" means and what a proportional shape would break.
            for (double width : List.of(320.0, 1280.0, 2560.0)) {
                double[] points = WindowFrame.notchPoints(width, 600);
                double horizontalLeg = width - points[2];
                double verticalLeg = points[5];
                assertThat(horizontalLeg).as("horizontal leg at %s", width).isEqualTo(UiTokens.NOTCH);
                assertThat(verticalLeg).as("vertical leg at %s", width).isEqualTo(UiTokens.NOTCH);
            }
        }

        @Test
        @DisplayName("a panel smaller than the notch degrades to a rectangle instead of inverting")
        void tinyPanelsDoNotInvert() {
            // Reachable by dragging a window small. A self-intersecting polygon renders as a
            // triangle pointing the wrong way, which looks like a rendering fault rather than a
            // small window.
            double[] tiny = WindowFrame.notchPoints(10, 10);
            assertThat(tiny[2]).as("the cut never exceeds the panel").isGreaterThanOrEqualTo(0);
            double[] zero = WindowFrame.notchPoints(0, 0);
            assertThat(zero).hasSize(8);
        }
    }

    @Nested
    @DisplayName("§2.3 — the spacing scale is closed")
    class Spacing {

        @Test
        @DisplayName("the scale is exactly 1, 5, 7, 9, 12, 14")
        void scaleMatchesTheDocument() {
            // Density is the aesthetic. A 16px gutter appearing because a panel "felt cramped" is
            // the first step towards §1's named failure mode — a competent dark-mode dev tool.
            assertThat(List.of(
                            UiTokens.SPACE_1, UiTokens.SPACE_2, UiTokens.SPACE_3,
                            UiTokens.SPACE_4, UiTokens.SPACE_5, UiTokens.SPACE_6))
                    .containsExactly(1.0, 5.0, 7.0, 9.0, 12.0, 14.0);
        }

        @Test
        @DisplayName("the reveal is nine discrete steps, as §5 specifies")
        void revealSteps() {
            assertThat(UiTokens.REVEAL_STEPS).isEqualTo(9);
            assertThat(UiTokens.REVEAL_MS).isEqualTo(340);
            assertThat(UiTokens.NOTCH).isEqualTo(18);
            assertThat(UiTokens.CELL).isEqualTo(11);
            assertThat(UiTokens.CYCLE_PER_ROW).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("cursors — §0's last piece of the host OS")
    class Cursors {

        @Test
        @DisplayName("the stylesheet declares no -fx-cursor at all")
        void noCursorInCss() throws IOException {
            // MEASURED, not preference. Two JavaFX 26 facts make this necessary:
            //   1. a CSS -fx-cursor on a node BEATS an inherited Scene cursor, so any declaration
            //      here punches a system-cursor hole in whichever skin the player chose;
            //   2. -fx-cursor: url(...) does not work at all — it fails at apply time with
            //      ClassCastException: String incompatible with Cursor.
            // Together they mean cursors can only come from Java. This test is what stops a
            // well-meaning `-fx-cursor: hand` from reappearing on .button.
            for (String sheet : List.of("theme.css", "theme-hc.css", "theme-phosphor.css",
                    "theme-amber.css", "theme-classic.css")) {
                String css = Files.readString(CLIENT_RESOURCES.resolve(UI_RESOURCES + sheet));
                String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
                assertThat(body).as("%s declares no cursor", sheet).doesNotContain("-fx-cursor");
            }
        }

        @Test
        @DisplayName("the system pointer is offered, and offered first")
        void systemIsTheFloor() {
            // A pointer is tuned by the player's OS for their display and their eyesight, and some
            // people run a deliberately enlarged or high-contrast one. Overriding that with no way
            // back would be an accessibility regression dressed as art direction.
            assertThat(CursorSkin.selectable().getFirst()).isEqualTo(CursorSkin.SYSTEM);
            assertThat(CursorSkin.SYSTEM.isSystem()).isTrue();
            assertThat(CursorSkin.selectable()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("every skin has a distinct id and a human label")
        void skinsAreWellFormed() {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (CursorSkin skin : CursorSkin.values()) {
                assertThat(skin.id()).as("%s id", skin).isNotBlank();
                assertThat(skin.label()).as("%s label", skin).isNotBlank();
                assertThat(ids.add(skin.id())).as("%s duplicates an id", skin).isTrue();
                assertThat(CursorSkin.byId(skin.id())).contains(skin);
            }
        }

        @Test
        @DisplayName("every role falls back to the conventional platform cursor")
        void rolesDegrade() {
            // The whole design has to survive a toolkit that cannot snapshot, a skin that fails to
            // build, and the system skin. In all three cases the player must get the ORDINARY
            // cursor for what they are over — not nothing, and not the wrong one.
            for (CursorRole role : CursorRole.values()) {
                assertThat(role.platform()).as("%s", role).isNotNull();
            }
            assertThat(CursorRole.POINTER.platform()).isEqualTo(javafx.scene.Cursor.DEFAULT);
            assertThat(CursorRole.HAND.platform()).isEqualTo(javafx.scene.Cursor.HAND);
            assertThat(CursorRole.TEXT.platform()).isEqualTo(javafx.scene.Cursor.TEXT);
            assertThat(CursorRole.RESIZE_NW.platform()).isEqualTo(javafx.scene.Cursor.NW_RESIZE);
        }

        @Test
        @DisplayName("the text I-beam is never re-drawn, under any skin")
        void textKeepsThePlatformIBeam() {
            // Its shape carries real precision information — it shows which two characters the
            // caret will land between. A themed glyph would trade accuracy for atmosphere.
            assertThat(CursorRole.TEXT.platform()).isEqualTo(javafx.scene.Cursor.TEXT);
        }

        @Test
        @DisplayName("no cursor class names a colour of its own")
        void cursorsHaveNoPaletteOfTheirOwn() throws IOException {
            // Cursors are pixels, so drawing one needs a Color — which collides with §10 criterion 2
            // until you notice the stylesheet can be read as well as written. ui/cursors/Palette
            // resolves a style class against the live sheet, so a pointer follows every palette
            // overlay for free. The general no-hex check covers /client/ui/; this pins the reason.
            for (Path source : javaSources()) {
                if (!source.toString().contains("/client/ui/cursors/")) {
                    continue;
                }
                assertThat(stripComments(read(source)))
                        .as("%s must not name a colour", source.getFileName())
                        .doesNotContain("Color.web")
                        .doesNotContain("Color.rgb");
            }
        }
    }

    /**
     * Strips comments before scanning.
     *
     * <p>Without this the checks fail on their own documentation: every one of these rules is
     * explained in a Javadoc that necessarily quotes the thing it forbids.
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}