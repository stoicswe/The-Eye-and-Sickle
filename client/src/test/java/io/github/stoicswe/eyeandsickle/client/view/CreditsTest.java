package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The placeholder initials on Settings → Credits.
 *
 * <p>Only the string arithmetic is tested here — the page itself is a layout, and a layout is
 * checked by looking at it. What is worth pinning is that the fallback never throws on a name that
 * is not two words, because these are real people's names and the shape of a name is not something
 * code gets to assume.
 */
class CreditsTest {

    @Test
    @DisplayName("first and last, uppercased")
    void twoWords() {
        assertThat(Credits.initials("Nathaniel Knudsen")).isEqualTo("NK");
        assertThat(Credits.initials("Sham Tomaselli")).isEqualTo("ST");
    }

    @Test
    @DisplayName("skips the middle rather than running to three letters")
    void threeWords() {
        assertThat(Credits.initials("Ada Byron Lovelace")).isEqualTo("AL");
    }

    /** People have one name. A substring(0, 1) on the second word would have thrown here. */
    @Test
    @DisplayName("one name gives one letter")
    void oneWord() {
        assertThat(Credits.initials("Prince")).isEqualTo("P");
    }

    @Test
    @DisplayName("survives padding and an empty string")
    void degenerate() {
        assertThat(Credits.initials("  Grace   Hopper  ")).isEqualTo("GH");
        assertThat(Credits.initials("")).isEmpty();
        assertThat(Credits.initials("   ")).isEmpty();
    }
}
