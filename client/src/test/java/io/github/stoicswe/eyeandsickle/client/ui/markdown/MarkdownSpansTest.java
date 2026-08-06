package io.github.stoicswe.eyeandsickle.client.ui.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The markdown parser behind the editor's highlight overlay.
 *
 * <h2>⚠ ONE PROPERTY MATTERS MORE THAN EVERY CLASSIFICATION HERE</h2>
 *
 * The overlay is a {@code TextFlow} laid <em>exactly over</em> the {@code TextArea} the player types
 * into, aligned by counting monospace cells. So the runs must reproduce the source
 * <b>character for character</b>: add, drop or reorder one and every glyph after it on that line
 * shifts, the caret stops landing under the pointer, and it happens only on lines containing markup.
 * {@link Roundtrip} is that property, and it is worth more than the rest of this file put together —
 * a run classified as prose merely looks plain, where a run that ate two asterisks breaks typing.
 */
class MarkdownSpansTest {

    private static String rebuild(String source) {
        StringBuilder out = new StringBuilder();
        List<MarkdownSpans.Line> lines = MarkdownSpans.parse(source);
        for (int i = 0; i < lines.size(); i++) {
            for (MarkdownSpans.Span span : lines.get(i).spans()) {
                out.append(span.text());
            }
            if (i < lines.size() - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    @Nested
    @DisplayName("⚠ the runs reproduce the source exactly")
    class Roundtrip {

        @Test
        @DisplayName("every construct survives a parse and reassemble unchanged")
        void nothingIsAddedOrLost() {
            List<String> sources = List.of(
                    "# Heading",
                    "###### Deep heading",
                    "plain prose with no markup at all",
                    "**strong** and *emphasis* and `code`",
                    "- a bullet\n- another",
                    "1. numbered\n2. again",
                    "> a quote",
                    "---",
                    "[label](https://example.invalid)",
                    "```\nfenced\n  indented inside\n```",
                    "trailing spaces   ",
                    "\ttab indented",
                    "",
                    "\n\n\n",
                    "unclosed **strong and unclosed `code",
                    "* not a bullet*because no space",
                    "mixed **bold `code` inside** tail",
                    "#hash with no space is not a heading",
                    "####### seven hashes is not a heading");

            for (String source : sources) {
                assertThat(rebuild(source))
                        .as("the overlay would slide off the text for: %s", source.replace("\n", "\\n"))
                        .isEqualTo(source);
            }
        }

        /**
         * ⚠ The awkward inputs, generated rather than hand-picked.
         *
         * <p>Every prefix of a document is a document somebody types on the way to writing it — and a
         * half-typed {@code **} or an unclosed backtick is the state the editor is in for as long as
         * it takes to reach the closing marker. If the parser only round-trips finished markup, the
         * highlight slides sideways while somebody is mid-word and snaps back when they finish.
         */
        @Test
        @DisplayName("and every PREFIX of a document does too, because that is what typing is")
        void everyPrefixSurvives() {
            String document =
                    """
                    # Kyrell

                    Recovered off **10.14.9.2** — a `systemd` unit nobody wrote.

                    - Signs off as *unsigned relay*
                    - Uses `blake2b` twice

                    > The handle is not the person.

                    ---

                    See [the notes](notes://addresses).
                    """;
            for (int i = 0; i <= document.length(); i++) {
                String prefix = document.substring(0, i);
                assertThat(rebuild(prefix))
                        .as("a partly-typed document at %d characters", i)
                        .isEqualTo(prefix);
            }
        }
    }

    @Nested
    @DisplayName("classification")
    class Kinds {

        private static MarkdownSpans.Line only(String source) {
            return MarkdownSpans.parse(source).getFirst();
        }

        @Test
        @DisplayName("headings carry their level, one to six")
        void headings() {
            assertThat(only("# One").level()).isEqualTo(1);
            assertThat(only("###### Six").level()).isEqualTo(6);
            assertThat(only("# One").block()).isEqualTo(MarkdownSpans.Kind.HEADING);
            // ⚠ Both of these are prose, and a parser that guessed would highlight text nobody meant
            // as markup — on the one window whose contents the game cannot regenerate.
            assertThat(only("#nospace").block()).isEqualTo(MarkdownSpans.Kind.TEXT);
            assertThat(only("####### seven").block()).isEqualTo(MarkdownSpans.Kind.TEXT);
        }

        @Test
        @DisplayName("a fence opens and closes, and both fence lines are code")
        void fences() {
            List<MarkdownSpans.Line> lines = MarkdownSpans.parse("```\nx = 1\n```\nafter");
            assertThat(lines.get(0).block()).isEqualTo(MarkdownSpans.Kind.CODE);
            assertThat(lines.get(1).block()).isEqualTo(MarkdownSpans.Kind.CODE);
            assertThat(lines.get(2).block()).isEqualTo(MarkdownSpans.Kind.CODE);
            assertThat(lines.get(3).block())
                    .as("the line after the closing fence is prose again")
                    .isEqualTo(MarkdownSpans.Kind.TEXT);
        }

        @Test
        @DisplayName("markdown inside a fence is left alone")
        void fencesAreLiteral() {
            List<MarkdownSpans.Line> lines = MarkdownSpans.parse("```\n# not a heading\n```");
            assertThat(lines.get(1).spans()).singleElement().satisfies(span -> {
                assertThat(span.kind()).isEqualTo(MarkdownSpans.Kind.CODE);
                assertThat(span.text()).isEqualTo("# not a heading");
            });
        }

        @Test
        @DisplayName("list markers keep their indent, so the overlay stays in register")
        void listMarkers() {
            assertThat(MarkdownSpans.listMarker("- item")).isEqualTo("- ");
            assertThat(MarkdownSpans.listMarker("    - nested")).isEqualTo("    - ");
            assertThat(MarkdownSpans.listMarker("12. numbered")).isEqualTo("12. ");
            assertThat(MarkdownSpans.listMarker("-nospace")).isEmpty();
            assertThat(MarkdownSpans.listMarker("plain")).isEmpty();
        }
    }

    @Nested
    @DisplayName("the reading form")
    class Stripped {

        @Test
        @DisplayName("strips markers for display, and only for display")
        void strips() {
            assertThat(MarkdownSpans.stripped(new MarkdownSpans.Span("**bold**", MarkdownSpans.Kind.STRONG)))
                    .isEqualTo("bold");
            assertThat(MarkdownSpans.stripped(new MarkdownSpans.Span("`code`", MarkdownSpans.Kind.CODE)))
                    .isEqualTo("code");
            assertThat(MarkdownSpans.stripped(
                            new MarkdownSpans.Span("[label](url)", MarkdownSpans.Kind.LINK)))
                    .isEqualTo("label");
            assertThat(MarkdownSpans.stripped(new MarkdownSpans.Span("plain", MarkdownSpans.Kind.TEXT)))
                    .isEqualTo("plain");
        }

        @Test
        @DisplayName("a malformed run is returned whole rather than mangled")
        void malformedIsLeftAlone() {
            // Shorter than its own markers — the substring arithmetic must not throw or produce
            // nonsense on something a player half-typed.
            assertThat(MarkdownSpans.stripped(new MarkdownSpans.Span("*", MarkdownSpans.Kind.STRONG)))
                    .isEqualTo("*");
        }
    }
}
