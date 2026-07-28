package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rig's network name, and the prompt built from it.
 *
 * <p>Worth testing for two reasons that have nothing to do with coverage. The first is that
 * {@link Hostname#prompt} fixes the ORDER of a string a player reads several hundred times an hour;
 * it was backwards before, and a test is the only thing that stops it drifting back. The second is
 * that the validation rules are <b>DNS's</b> rather than this game's, so a change to them is a
 * change to something the client is teaching as true.
 */
class HostnameTest {

    @Nested
    @DisplayName("the prompt")
    class Prompt {

        @Test
        @DisplayName("⚠ who, then where — the order every terminal uses")
        void orderIsUserThenHost() {
            // It read `rig@operator:~$` before, which is backwards. A prompt is furniture and
            // nobody stops to parse furniture, which is exactly why getting it wrong teaches the
            // wrong thing so effectively.
            assertThat(Hostname.prompt("operator", "rig")).isEqualTo("operator@rig.local:~$");
            assertThat(Hostname.prompt("nyx", "eye-central")).isEqualTo("nyx@eye-central.local:~$");
        }

        @Test
        @DisplayName("a missing handle falls back rather than leaving a hole in the prompt")
        void blankHandle() {
            assertThat(Hostname.prompt("", "rig")).isEqualTo("operator@rig.local:~$");
            assertThat(Hostname.prompt(null, "rig")).isEqualTo("operator@rig.local:~$");
        }

        @Test
        @DisplayName("a broken saved hostname falls back to the default, never to a blank")
        void brokenSettingStillDrawsAPrompt() {
            // A profile is a plain JSON file a player can edit. A prompt with a hole in it is not a
            // state any caller should have to handle, so the fallback is here rather than at each
            // call site.
            assertThat(Hostname.prompt("op", "")).isEqualTo("op@rig.local:~$");
            assertThat(Hostname.prompt("op", "not a hostname!")).isEqualTo("op@rig.local:~$");
            assertThat(Hostname.prompt("op", null)).isEqualTo("op@rig.local:~$");
        }
    }

    @Nested
    @DisplayName("what is stored")
    class Sanitise {

        @Test
        @DisplayName("⚠ a typed `.local` is stripped, not doubled")
        void suffixIsNotDoubled() {
            // The obvious bug: the prompt appends `.local`, so a player who helpfully types the
            // suffix would be greeted by `rig.local.local` and conclude the field is broken.
            assertThat(Hostname.sanitise("rig.local")).isEqualTo("rig");
            assertThat(Hostname.sanitise("RIG.LOCAL")).isEqualTo("rig");
            assertThat(Hostname.qualified("rig.local")).isEqualTo("rig.local");
        }

        @Test
        @DisplayName("stored lowercase, because a hostname is case-insensitive")
        void lowercased() {
            assertThat(Hostname.sanitise("  Eye-Central  ")).isEqualTo("eye-central");
        }
    }

    @Nested
    @DisplayName("the rules are DNS's, not ours")
    class Validation {

        @Test
        @DisplayName("letters, digits and hyphens pass")
        void accepted() {
            for (String name : java.util.List.of("rig", "eye-central", "node7", "a", "x-1-y")) {
                assertThat(Hostname.problem(name)).as("%s is a valid host label", name).isNull();
            }
        }

        @Test
        @DisplayName("an underscore is refused, and the refusal says whose rule that is")
        void underscoreRefused() {
            // Nothing in this client would break on an underscore. It is refused because DNS
            // refuses it, and a refusal that teaches something true is worth more than a
            // permissiveness that teaches nothing.
            assertThat(Hostname.problem("my_rig")).contains("DNS");
            assertThat(Hostname.problem("my rig")).isNotNull();
            assertThat(Hostname.problem("rig!")).isNotNull();
        }

        @Test
        @DisplayName("a leading or trailing hyphen is refused; an interior one is fine")
        void hyphenPlacement() {
            assertThat(Hostname.problem("-rig")).contains("hyphen");
            assertThat(Hostname.problem("rig-")).contains("hyphen");
            assertThat(Hostname.problem("ri-g")).isNull();
        }

        @Test
        @DisplayName("63 characters is the ceiling, and the message says it is DNS's")
        void lengthCeiling() {
            assertThat(Hostname.problem("a".repeat(Hostname.MAX_LENGTH))).isNull();
            assertThat(Hostname.problem("a".repeat(Hostname.MAX_LENGTH + 1)))
                    .contains("63")
                    .contains("DNS");
        }

        @Test
        @DisplayName("blank is refused rather than silently becoming the default")
        void blankRefused() {
            // sanitise() falls back for a BROKEN saved value; the settings field must still tell a
            // player who cleared it that they cleared it, rather than appearing to accept nothing.
            assertThat(Hostname.problem("")).isNotNull();
            assertThat(Hostname.problem("   ")).isNotNull();
            assertThat(Hostname.problem(null)).isNotNull();
        }
    }
}
