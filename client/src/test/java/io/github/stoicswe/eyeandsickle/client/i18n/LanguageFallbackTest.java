package io.github.stoicswe.eyeandsickle.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.teaching.TermPage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A partial translation must degrade one string and one page at a time.
 *
 * <p>The failure this guards against is not a crash — it is a translation that ships and quietly
 * takes content away. Both halves have the same shape: fall back per item, never per file, and say
 * so in {@code problems()} rather than only on screen.
 */
@DisplayName("falling back to English")
class LanguageFallbackTest {

    @Nested
    @DisplayName("message bundles")
    class Bundles {

        @Test
        @DisplayName("an unknown language still returns every English string")
        void unknownLanguageKeepsEnglish() {
            Messages english = Messages.load("commands");
            Messages klingon = Messages.load("commands", "tlh");

            assertThat(klingon.problems())
                    .as("a missing translation is not a fault")
                    .isEmpty();
            assertThat(klingon.get("cmd.grep.i")).isEqualTo(english.get("cmd.grep.i"));
            assertThat(klingon.language()).isEqualTo("tlh");
        }

        @Test
        @DisplayName("a key nothing defines comes back as itself, not blank and not null")
        void unknownKeyEchoes() {
            // A blank label is invisible and a null is a crash; the key on screen is ugly, obviously
            // wrong, and names the entry somebody has to add.
            assertThat(Messages.load("commands").get("cmd.nothing.defines.this"))
                    .isEqualTo("cmd.nothing.defines.this");
        }

        @Test
        @DisplayName("a missing English bundle is reported as the packaging fault it is")
        void missingEnglishIsAProblem() {
            assertThat(Messages.load("no-such-bundle").problems()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("man pages")
    class Manual {

        private static final Path TERMS = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/terms");

        @Test
        @DisplayName("an untranslated manual still has every page, in English")
        void everyPageSurvivesAnUnknownLocale() {
            TermDatabase english = TermDatabase.load();
            TermDatabase klingon = TermDatabase.load("tlh");

            // ⚠ The count is the assertion that matters. Reading a translated index instead of the
            // English one would let a partial translation shrink the manual, and a manual with
            // eleven fewer pages looks exactly like a manual with eleven fewer pages.
            assertThat(klingon.pages()).hasSameSizeAs(english.pages());

            Set<String> englishIds = new TreeSet<>();
            english.pages().forEach(p -> englishIds.add(p.id()));
            Set<String> klingonIds = new TreeSet<>();
            klingon.pages().forEach(p -> klingonIds.add(p.id()));
            assertThat(klingonIds).isEqualTo(englishIds);
        }

        @Test
        @DisplayName("each page that fell back says so")
        void fallbacksAreReported() {
            List<String> problems = TermDatabase.load("tlh").problems();
            assertThat(problems)
                    .as("an incomplete translation must be visible to whoever is finishing it")
                    .isNotEmpty();
            assertThat(problems).allMatch(p -> p.contains("shown in English"));
        }

        @Test
        @DisplayName("English itself reports nothing, which is what makes the above meaningful")
        void englishIsClean() {
            assertThat(TermDatabase.load().problems()).isEmpty();
        }

        @Test
        @DisplayName("the English index lists a real file for every line")
        void indexIsHonest() throws IOException {
            Path index = TERMS.resolve("en/index.txt");
            List<String> missing = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                if (!Files.exists(TERMS.resolve("en").resolve(entry))) {
                    missing.add(entry);
                }
            }
            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("a page keeps its identity through the fallback")
        void identityIsPreserved() {
            TermPage english = TermDatabase.load().find("grep").orElseThrow();
            TermPage fallen = TermDatabase.load("tlh").find("grep").orElseThrow();
            // The reference is how a cross-reference resolves; a fallback that changed it would break
            // every seeAlso pointing at this page in exactly the locale that fell back.
            assertThat(fallen.reference()).isEqualTo(english.reference());
        }
    }
}
