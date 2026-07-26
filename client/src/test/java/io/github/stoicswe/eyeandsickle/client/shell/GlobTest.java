package io.github.stoicswe.eyeandsickle.client.shell;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Glob semantics, checked against the behaviour a real shell has.
 *
 * <p>The headline test is {@link #globStarIsNotRegexStar()}. {@code docs/education/04-the-command-line.md}
 * §3.12 uses exactly this contrast to teach the difference between a glob and a regular expression,
 * and the values there were produced by running both on a real machine. If this implementation
 * disagreed with that page, the game would be teaching something false — which is the one failure the
 * whole education doc set exists to prevent.
 */
class GlobTest {

    @Test
    @DisplayName("* matches any run of characters, including none")
    void star() {
        assertThat(Glob.matches("ab*", "ab")).isTrue();
        assertThat(Glob.matches("ab*", "abb")).isTrue();
        assertThat(Glob.matches("ab*", "abcdef")).isTrue();
        assertThat(Glob.matches("ab*", "a")).isFalse();
        assertThat(Glob.matches("ab*", "ac")).isFalse();
    }

    @Test
    @DisplayName("a glob's * is NOT a regex's * — the contrast the curriculum teaches")
    void globStarIsNotRegexStar() {
        // Verified on macOS: `echo ab*` over files {a, ab, abb, ac} returns exactly "ab abb",
        // while `grep -E '^ab*$'` over the same four lines returns "a ab abb".
        String[] candidates = {"a", "ab", "abb", "ac"};

        var globMatches = java.util.Arrays.stream(candidates).filter(c -> Glob.matches("ab*", c)).toList();
        assertThat(globMatches).containsExactly("ab", "abb");

        var regexMatches = java.util.Arrays.stream(candidates)
                .filter(c -> java.util.regex.Pattern.matches("ab*", c))
                .toList();
        assertThat(regexMatches).containsExactly("a", "ab", "abb");

        // Same three characters, different answers, and no error to warn you. That is the lesson.
        assertThat(globMatches).isNotEqualTo(regexMatches);
    }

    @Test
    @DisplayName("? matches exactly one character")
    void question() {
        assertThat(Glob.matches("a?c", "abc")).isTrue();
        assertThat(Glob.matches("a?c", "ac")).isFalse();
        assertThat(Glob.matches("a?c", "abbc")).isFalse();
    }

    @Test
    @DisplayName("character classes, ranges, and ! negation")
    void classes() {
        assertThat(Glob.matches("[abc]at", "cat")).isTrue();
        assertThat(Glob.matches("[abc]at", "rat")).isFalse();
        assertThat(Glob.matches("[a-z]at", "hat")).isTrue();
        assertThat(Glob.matches("[a-z]at", "1at")).isFalse();
        // A glob negates with !, a regex negates with ^. Mixing them up silently matches the wrong
        // thing, which is why regular-expression(7)'s CAVEATS names it.
        assertThat(Glob.matches("[!abc]at", "rat")).isTrue();
        assertThat(Glob.matches("[!abc]at", "cat")).isFalse();
    }

    @Test
    @DisplayName("** is not special — it is just *, since globstar is not supported")
    void noGlobstar() {
        assertThat(Glob.matches("**", "anything")).isTrue();
        assertThat(Glob.matches("a**b", "ab")).isTrue();
        assertThat(Glob.matches("a**b", "axxxb")).isTrue();
    }

    @Test
    @DisplayName("matching is case-insensitive, per §3.3")
    void caseInsensitive() {
        assertThat(Glob.matches("Port-*", "port-sweep")).isTrue();
        assertThat(Glob.matches("port-*", "PORT-SWEEP")).isTrue();
    }

    @Test
    @DisplayName("a pattern with no wildcard is an exact match")
    void literal() {
        assertThat(Glob.matches("vault", "vault")).isTrue();
        assertThat(Glob.matches("vault", "vaults")).isFalse();
        assertThat(Glob.isGlob("vault")).isFalse();
        assertThat(Glob.isGlob("vault/*")).isTrue();
    }
}
