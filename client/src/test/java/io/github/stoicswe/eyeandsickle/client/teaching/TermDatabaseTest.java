package io.github.stoicswe.eyeandsickle.client.teaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the manual.
 *
 * <p>The most valuable test here is {@link Shipped#shippedManualIsClean()}: it loads every page the
 * client ships and asserts there is nothing wrong with any of them. {@code docs/client/04} §4.10
 * specifies these as CI checks, and the reason they are checks rather than review notes is that every
 * failure mode is silent — a dead cross-reference, a dropped section, a missing caveat on a simplified
 * page. Nobody finds those by reading. A player finds them at the exact moment they were curious.
 */
class TermDatabaseTest {

    @Nested
    @DisplayName("the shipped manual")
    class Shipped {

        @Test
        @DisplayName("loads with no problems at all")
        void shippedManualIsClean() {
            TermDatabase db = TermDatabase.load();

            // Every seeAlso resolves, no page claims another's reference, every file in the index
            // exists and parses. Empty is the only acceptable state at release.
            assertThat(db.problems()).isEmpty();
            assertThat(db.size()).isGreaterThanOrEqualTo(19);
        }

        @Test
        @DisplayName("every page is honestly labelled, and the split is stated")
        void everyPageHasAStatus() {
            TermDatabase db = TermDatabase.load();

            int real = db.withStatus(TermPage.Status.REAL).size();
            int simplified = db.withStatus(TermPage.Status.REAL_SIMPLIFIED).size();
            int game = db.withStatus(TermPage.Status.GAME).size();

            assertThat(real + simplified + game).isEqualTo(db.size());
            // A manual that claimed everything was real would be the failure docs/client/04 §4.7
            // exists to prevent, and one that claimed everything was invented would be useless.
            assertThat(real).isPositive();
            assertThat(game).isPositive();
        }

        @Test
        @DisplayName("no section-7 concept page carries a SYNOPSIS")
        void conceptPagesHaveNoSynopsis() {
            // §4.3.1: real section-7 pages usually have none, and the absence is what teaches the
            // section system. The parser enforces it; this proves the shipped set obeys.
            for (TermPage page : TermDatabase.load().inSection(7)) {
                assertThat(page.bodySection("SYNOPSIS"))
                        .as("%s should have no SYNOPSIS", page.reference())
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("every simplified page says what was simplified")
        void simplifiedPagesCarryCaveats() {
            for (TermPage page : TermDatabase.load().withStatus(TermPage.Status.REAL_SIMPLIFIED)) {
                assertThat(page.bodySection("CAVEATS"))
                        .as("%s is simplified and must say how", page.reference())
                        .isPresent();
            }
        }

        @Test
        @DisplayName("every gloss fits in 72 characters")
        void glossesAreShort() {
            for (TermPage page : TermDatabase.load().pages()) {
                assertThat(page.gloss().length())
                        .as("%s gloss", page.reference())
                        .isLessThanOrEqualTo(TermParser.MAX_GLOSS);
            }
        }

        @Test
        @DisplayName("the NAME line and the gloss are one string, not two")
        void nameLineIsDerived() {
            // §4.3.1 requires the NAME line and the gloss bar to be the same string — one source,
            // two surfaces. The only way to guarantee that is to have one source.
            TermPage compute = TermDatabase.load().find("compute").orElseThrow();
            assertThat(compute.nameLine()).isEqualTo(compute.name() + " — " + compute.gloss());
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        private final TermDatabase db = TermDatabase.load();

        @Test
        @DisplayName("man resolves by name, by reference, and by section-and-name")
        void resolution() {
            assertThat(db.find("compute")).isPresent();
            assertThat(db.find("compute(7)")).isPresent();
            assertThat(db.find("7 compute")).isPresent();
            assertThat(db.find("nothing-of-the-sort")).isEmpty();
        }

        @Test
        @DisplayName("aliases resolve, so a player who guesses the other word still lands")
        void aliases() {
            assertThat(db.find("cycles")).isPresent();
            assertThat(db.find("cycles").orElseThrow().id()).isEqualTo("compute");
        }

        @Test
        @DisplayName("apropos searches descriptions, which is what the real one does")
        void apropos() {
            List<TermPage> hits = db.apropos("capacity", false);
            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(p -> p.id().equals("compute"));
        }

        @Test
        @DisplayName("--all extends the search into the body")
        void aproposAll() {
            // A word that appears in prose but not in any gloss.
            assertThat(db.apropos("Ctrl-C", false)).isEmpty();
            assertThat(db.apropos("Ctrl-C", true)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("the parser refuses what it promised to refuse")
    class Parsing {

        private static final String VALID = """
                ---
                id: example
                section: 7
                name: example
                gloss: A short thing.
                status: real
                seeAlso: compute(7)
                revision: 1
                ---

                ## DESCRIPTION
                Body.

                ## REAL-WORLD COUNTERPART
                real — yes.
                """;

        @Test
        @DisplayName("a valid page parses")
        void valid() {
            TermPage page = TermParser.parse(VALID, "test");
            assertThat(page.id()).isEqualTo("example");
            assertThat(page.status()).isEqualTo(TermPage.Status.REAL);
        }

        @Test
        @DisplayName("an unknown header key fails — the key set is closed")
        void unknownKey() {
            assertThatThrownBy(() -> TermParser.parse(VALID.replace("revision: 1", "revsion: 1"), "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("unknown header key");
        }

        @Test
        @DisplayName("an unknown body section fails rather than silently dropping")
        void unknownSection() {
            // Silent-drop is how content quietly goes missing: a typo'd heading would leave a page
            // that looks finished and teaches nothing, and nobody would find it until a player did.
            assertThatThrownBy(() -> TermParser.parse(VALID.replace("## DESCRIPTION", "## DESCRPTION"), "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("unknown body section");
        }

        @Test
        @DisplayName("a missing REAL-WORLD COUNTERPART fails — the honesty rule is not optional")
        void counterpartRequired() {
            String without = VALID.substring(0, VALID.indexOf("## REAL-WORLD COUNTERPART"));
            assertThatThrownBy(() -> TermParser.parse(without, "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("REAL-WORLD COUNTERPART");
        }

        @Test
        @DisplayName("a simplified page without CAVEATS fails")
        void simplifiedNeedsCaveats() {
            assertThatThrownBy(
                            () -> TermParser.parse(VALID.replace("status: real", "status: real, simplified"), "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("CAVEATS");
        }

        @Test
        @DisplayName("a section-7 page with a SYNOPSIS fails")
        void conceptPagesRejectSynopsis() {
            String withSynopsis = VALID.replace("## DESCRIPTION", "## SYNOPSIS\nnope\n\n## DESCRIPTION");
            assertThatThrownBy(() -> TermParser.parse(withSynopsis, "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("section-7");
        }

        @Test
        @DisplayName("an over-long gloss fails")
        void glossLimit() {
            String longGloss = VALID.replace("gloss: A short thing.", "gloss: " + "x".repeat(80));
            assertThatThrownBy(() -> TermParser.parse(longGloss, "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("limit");
        }

        @Test
        @DisplayName("an invalid section number fails")
        void sectionMustBeReal() {
            assertThatThrownBy(() -> TermParser.parse(VALID.replace("section: 7", "section: 4"), "test"))
                    .isInstanceOf(TermParser.TermFormatException.class)
                    .hasMessageContaining("section must be one of");
        }
    }
}
