package io.github.stoicswe.eyeandsickle.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The language registry, and the keys the interface resolves text through. */
@DisplayName("choosing a language")
class LanguageTest {

    @AfterEach
    void resetToEnglish() {
        // Text.current is process-wide; a test that switched it and walked away would decide the
        // language for every test that ran afterwards.
        Text.use(Language.fallback());
    }

    @Nested
    @DisplayName("the registry")
    class Registry {

        @Test
        @DisplayName("English is shipped and is the fallback")
        void englishIsTheFallback() {
            assertThat(Language.shipped()).contains(Language.ENGLISH);
            assertThat(Language.fallback()).isEqualTo(Language.ENGLISH);
        }

        @Test
        @DisplayName("tags are lowercase ASCII and unique — they name files and directories")
        void tagsAreStructural() {
            Set<String> seen = new HashSet<>();
            for (Language language : Language.values()) {
                assertThat(language.tag()).matches("[a-z]{2,3}(-[a-z0-9]+)*");
                assertThat(seen.add(language.tag()))
                        .as("two languages claiming %s would load one bundle for both", language.tag())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("every language names itself, and no two share a name")
        void endonymsAreDistinct() {
            Set<String> seen = new HashSet<>();
            for (Language language : Language.values()) {
                assertThat(language.endonym()).isNotBlank();
                // The picker is the one control that looks the same in every locale, so two entries
                // reading alike would be two entries a player cannot tell apart in any of them.
                assertThat(seen.add(language.endonym())).isTrue();
            }
        }

        @Test
        @DisplayName("an unknown tag resolves to nothing rather than throwing")
        void unknownTagIsEmpty() {
            // Settings is a file the player can edit, and a save naming a language a later build
            // dropped must still open.
            assertThat(Language.ofTag("zz")).isEmpty();
            assertThat(Language.ofTag(null)).isEmpty();
            assertThat(Language.ofTag("  ")).isEmpty();
            assertThat(Language.ofTag("EN")).contains(Language.ENGLISH);
        }
    }

    @Nested
    @DisplayName("window text")
    class Windows {

        @Test
        @DisplayName("every window's keys are derived from its id, so they cannot drift")
        void keysFollowTheId() {
            for (WindowSpec spec : WindowSpec.values()) {
                assertThat(spec.titleKey()).isEqualTo("window." + spec.id() + ".title");
                assertThat(spec.descriptionKey()).isEqualTo("window." + spec.id() + ".description");
            }
        }

        @Test
        @DisplayName("with nothing translated, the catalogue's own English shows")
        void fallsBackToTheEnum() {
            Text.use(Language.ENGLISH);
            for (WindowSpec spec : WindowSpec.values()) {
                assertThat(Text.current().title(spec)).isEqualTo(spec.title());
                assertThat(Text.current().description(spec)).isEqualTo(spec.description());
            }
        }

        @Test
        @DisplayName("a window key never renders as the key itself")
        void neverShowsAKey() {
            // The overlay bundle has no English file by design, so a caller that forgot the fallback
            // would put `window.rig-monitor.title` on a title bar.
            for (WindowSpec spec : WindowSpec.values()) {
                assertThat(Text.current().title(spec)).doesNotStartWith("window.");
                assertThat(Text.current().description(spec)).doesNotStartWith("window.");
            }
        }
    }

    @Nested
    @DisplayName("the overlay")
    class Overlay {

        @Test
        @DisplayName("a missing English bundle is not a problem, because English lives in code")
        void noEnglishBundleNeeded() {
            assertThat(Messages.overlay("windows", "en").problems()).isEmpty();
            assertThat(Messages.overlay("windows", "zz").problems()).isEmpty();
        }

        @Test
        @DisplayName("an untranslated key returns the caller's own string")
        void fallbackWins() {
            Messages overlay = Messages.overlay("windows", "zz");
            assertThat(overlay.get("window.nothing.title", "Rig monitor")).isEqualTo("Rig monitor");
        }
    }
}
