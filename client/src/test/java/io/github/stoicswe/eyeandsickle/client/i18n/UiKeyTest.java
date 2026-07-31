package io.github.stoicswe.eyeandsickle.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The interface's own message keys, and the one failure mode a translator cannot see.
 *
 * <h2>Why an orphaned key has to fail the build</h2>
 *
 * The {@code ui} bundle is an <b>overlay</b>: English lives in the source beside the thing it
 * describes, and a translation supplies only what it has translated. That arrangement has exactly one
 * sharp edge — a key is a string in two files that nothing links. Edit the English at the call site
 * and change its key, and the German line for it goes on sitting in {@code ui_de.properties} matching
 * nothing. Nobody finds out: the build is green, the tests pass, and the German player simply gets
 * English on that one caption forever.
 *
 * <p>So a key in a translation that no call site asks for is a <b>failure</b>, and it names itself.
 * With no translations shipped this passes over an empty set; it becomes load-bearing the day
 * somebody adds one, which is the day it is needed.
 */
@DisplayName("interface message keys")
class UiKeyTest {

    private static final Path SOURCE = Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client");

    private static final Path BUNDLES = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/i18n");

    /** {@code t("ui.calc.base", …)} and {@code Views.t("settings.cat.desk", …)}. */
    private static final Pattern ASKS_FOR = Pattern.compile("\\bt\\(\\s*\"((?:ui|settings)\\.[\\w.-]+)\"");

    /** Every key any call site resolves. */
    private static Set<String> asked() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = ASKS_FOR.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        }
        return keys;
    }

    private static List<Path> translations() throws IOException {
        if (!Files.isDirectory(BUNDLES)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(BUNDLES)) {
            return files.filter(p -> p.getFileName().toString().startsWith("ui_"))
                    .filter(p -> p.toString().endsWith(".properties"))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> keysIn(Path bundle) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String line : Files.readAllLines(bundle, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            keys.add(trimmed.substring(0, trimmed.indexOf('=')).trim());
        }
        return keys;
    }

    @Nested
    @DisplayName("the call sites")
    class CallSites {

        @Test
        @DisplayName("the interface actually asks for keys — the mechanism is wired, not just present")
        void keysAreInUse() throws IOException {
            // Guards against the whole layer being present and unreferenced, which is what "we have
            // i18n" means right up until somebody checks.
            assertThat(asked()).hasSizeGreaterThan(100);
        }

        @Test
        @DisplayName("no key is asked for twice with a different English")
        void keysAreUnique() throws IOException {
            // Two call sites sharing a key means one translation for two sentences, and whichever
            // the translator saw is the one both get. The derived-from-the-English key scheme makes
            // this nearly impossible by construction; this is the check that it stayed that way.
            java.util.Map<String, String> english = new java.util.HashMap<>();
            List<String> clashes = new ArrayList<>();
            Pattern withText = Pattern.compile(
                    "\\bt\\(\\s*\"((?:ui|settings)\\.[\\w.-]+)\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.){0,60})");
            try (Stream<Path> files = Files.walk(SOURCE)) {
                for (Path file :
                        files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = withText.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (m.find()) {
                        String was = english.putIfAbsent(m.group(1), m.group(2));
                        if (was != null && !was.equals(m.group(2))) {
                            clashes.add(m.group(1) + ": \"" + was + "\" vs \"" + m.group(2) + "\"");
                        }
                    }
                }
            }
            assertThat(clashes).as("one key, two different sentences").isEmpty();
        }
    }

    @Nested
    @DisplayName("shipped translations")
    class Translations {

        @Test
        @DisplayName("every key in a translation is one the interface asks for")
        void noOrphanedKeys() throws IOException {
            Set<String> asked = asked();
            List<String> orphans = new ArrayList<>();
            for (Path bundle : translations()) {
                for (String key : keysIn(bundle)) {
                    if (!asked.contains(key)) {
                        // Silent in production: the caption just stays English forever.
                        orphans.add(bundle.getFileName() + ": " + key);
                    }
                }
            }
            assertThat(orphans)
                    .as("translated strings that will never be shown")
                    .isEmpty();
        }

        @Test
        @DisplayName("a translation is UTF-8 and readable")
        void bundlesParse() throws IOException {
            for (Path bundle : translations()) {
                // Properties.load(InputStream) is ISO-8859-1 by definition, so a bundle that only
                // parses as Latin-1 is one whose accented characters are already mangled.
                assertThat(Files.readString(bundle, StandardCharsets.UTF_8)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("a translation actually reaches the screen")
    class EndToEnd {

        /**
         * ⚠ {@code zz} is a TEST-ONLY pseudo-language, in {@code src/test/resources} and absent from
         * {@link Language}, so no player can select it. It exists because every other check here is
         * about an empty set: without a real bundle to load, "translations work" is an assertion
         * about machinery nobody has run. This loads one.
         */
        @Test
        @DisplayName("a translated ui key wins over the English at the call site")
        void overlayWins() {
            Messages ui = Messages.overlay("ui", "zz");
            assertThat(ui.get("ui.calc.base", "Base")).isEqualTo("ZZBASIS");
            assertThat(ui.get("settings.cat.desk", "Desk")).isEqualTo("ZZTISCH");
        }

        @Test
        @DisplayName("an untranslated key keeps the English beside it")
        void untranslatedFallsBack() {
            Messages ui = Messages.overlay("ui", "zz");
            // The bundle deliberately translates two keys and no more, so this is the normal state
            // of a partial translation rather than a contrived one.
            assertThat(ui.get("ui.calc.width", "Width")).isEqualTo("Width");
        }

        @Test
        @DisplayName("a window title translates, and its untranslated neighbours do not break")
        void windowTitles() {
            Messages windows = Messages.overlay("windows", "zz");
            assertThat(windows.get(
                            io.github.stoicswe.eyeandsickle.client.window.WindowSpec.TERMINAL.titleKey(), "Terminal"))
                    .isEqualTo("ZZTERMINAL");
            assertThat(windows.get(
                            io.github.stoicswe.eyeandsickle.client.window.WindowSpec.RIG_MONITOR.titleKey(),
                            "Rig monitor"))
                    .isEqualTo("Rig monitor");
        }
    }
}
