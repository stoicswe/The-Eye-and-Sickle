package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The setup assistant's palette swatches must actually be the palettes.
 *
 * <h2>Why these colours are duplicated at all</h2>
 *
 * A swatch is a small picture of the deck a palette produces, and it is rendered <b>under whichever
 * palette is currently live</b>. A looked-up {@code -es-} token would therefore paint all six tiles
 * identically in whatever theme happens to be on — which is precisely the thing the tiles exist to
 * distinguish. So {@code theme.css} carries one literal block per theme, and those literals restate
 * values that also live in the palette overlays.
 *
 * <p>⚠ <b>That duplication is the whole reason this test exists.</b> Re-tune {@code -es-amber} in
 * {@code theme-phosphor.css} and the Phosphor swatch keeps showing the old green — a screen whose
 * entire job is "this is what that one looks like" quietly starts lying, and nothing else in the
 * build would notice. Six palettes × five values is thirty numbers nobody is going to diff by hand.
 *
 * <p>No JavaFX here: both sides are stylesheets, so this is text against text.
 */
class SetupSwatchTest {

    private static final Path UI = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/ui");

    /** Which palette token each part of the swatch is standing in for. */
    private static final Map<String, String> PARTS = new LinkedHashMap<>(Map.of(
            "es-swatch-strip", "-es-panel-hi",
            "es-swatch-pane", "-es-panel",
            "es-swatch-accent", "-es-amber"));

    @Test
    @DisplayName("every swatch is drawn in its own palette's actual colours")
    void swatchesMatchTheirPalettes() throws IOException {
        String base = Files.readString(UI.resolve("theme.css"));

        for (ThemeId id : ThemeId.selectable()) {
            // The base sheet IS the deck palette; every other theme layers an overlay over it.
            // ⚠ overlayStylesheet() returns a CLASSPATH resource path, not a file name — resolving
            // it against a directory yields an absolute path that does not exist. Only the last
            // segment names the file on disk.
            String palette = id.overlayStylesheet()
                    .map(resource -> read(UI.resolve(resource.substring(resource.lastIndexOf('/') + 1))))
                    .orElse(base);

            for (Map.Entry<String, String> part : PARTS.entrySet()) {
                String want = token(palette, part.getValue());
                String got = background(base, ".es-swatch-" + id.id() + " ." + part.getKey());
                assertThat(got)
                        .as("%s's %s swatch part should be %s (%s)",
                                id.id(), part.getKey(), part.getValue(), want)
                        .isEqualToIgnoringCase(want);
            }

            // The screen carries two: the ground it is filled with and the edge it is drawn in.
            String screen = ".es-swatch-" + id.id() + " .es-swatch-screen";
            assertThat(background(base, screen))
                    .as("%s's swatch ground should be -es-void", id.id())
                    .isEqualToIgnoringCase(token(palette, "-es-void"));
            assertThat(property(base, screen, "-fx-border-color"))
                    .as("%s's swatch edge should be -es-rule-hi", id.id())
                    .isEqualToIgnoringCase(token(palette, "-es-rule-hi"));
        }
    }

    @Test
    @DisplayName("a swatch exists for every selectable palette, and for no others")
    void oneSwatchPerPalette() throws IOException {
        String base = Files.readString(UI.resolve("theme.css"));
        // ⚠ Both directions. A palette with no swatch is a hole in the picker; a swatch with no
        // palette is a tile the assistant will never draw, which rots without ever being seen.
        Matcher declared = Pattern.compile("\\.es-swatch-([a-z0-9-]+) \\.es-swatch-screen")
                .matcher(base);
        java.util.Set<String> inCss = new java.util.LinkedHashSet<>();
        while (declared.find()) {
            inCss.add(declared.group(1));
        }
        assertThat(inCss)
                .containsExactlyInAnyOrderElementsOf(
                        ThemeId.selectable().stream().map(ThemeId::id).toList());
    }

    /** Reads a palette token's hex out of a stylesheet's {@code .root} block. */
    private static String token(String css, String name) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + ":\\s*([^;]+);").matcher(css);
        assertThat(matcher.find()).as("stylesheet declares %s", name).isTrue();
        return matcher.group(1).trim();
    }

    private static String background(String css, String selector) {
        return property(css, selector, "-fx-background-color");
    }

    /** Reads one declaration out of one rule. */
    private static String property(String css, String selector, String name) {
        Matcher matcher = Pattern
                .compile(Pattern.quote(selector) + "\\s*\\{([^}]*)\\}")
                .matcher(css);
        assertThat(matcher.find()).as("theme.css declares a rule for %s", selector).isTrue();
        Matcher declaration = Pattern
                .compile(Pattern.quote(name) + ":\\s*([^;]+);")
                .matcher(matcher.group(1));
        assertThat(declaration.find()).as("%s sets %s", selector, name).isTrue();
        return declaration.group(1).trim();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
