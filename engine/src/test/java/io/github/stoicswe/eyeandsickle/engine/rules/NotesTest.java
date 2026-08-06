package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.NoteState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The notebook's tree operations.
 *
 * <p>The interesting cases here are all about a tree becoming something that is not a tree: a folder
 * inside itself, a delete that orphans its contents, a nest deep enough to be unusable. Every one of
 * them fails <em>silently</em> if unguarded — the notes stay in the save, count against the limit,
 * and are unreachable from the window.
 */
class NotesTest {

    private static final Instant T0 = Instant.parse("2026-08-06T12:00:00Z");

    private static String make(GameSave s, String parent, String name, boolean folder) {
        return Notes.create(s, parent, name, folder, T0).orElseThrow().noteId;
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        @DisplayName("a note goes where it was made, and a folder can hold one")
        void createsIntoFolders() {
            GameSave s = new GameSave();
            String folder = make(s, "", "lore", true);
            String note = make(s, folder, "kyrell", false);

            assertThat(Notes.childrenOf(s, "")).extracting(n -> n.noteId).containsExactly(folder);
            assertThat(Notes.childrenOf(s, folder)).extracting(n -> n.noteId).containsExactly(note);
        }

        /**
         * ⚠ Refused rather than silently re-parented to the root.
         *
         * <p>A note that quietly appeared somewhere other than where it was made is worse than one
         * that was not made: the player looks for it where they created it and it is not there.
         */
        @Test
        @DisplayName("creating inside a NOTE is refused, not redirected")
        void notesAreNotFolders() {
            GameSave s = new GameSave();
            String note = make(s, "", "flat", false);
            assertThat(Notes.create(s, note, "child", false, T0)).isEmpty();
            assertThat(s.notes).hasSize(1);
        }

        @Test
        @DisplayName("nesting stops at the depth limit")
        void depthIsBounded() {
            GameSave s = new GameSave();
            String at = "";
            for (int i = 0; i < Notes.DEPTH_LIMIT; i++) {
                at = make(s, at, "f" + i, true);
            }
            assertThat(Notes.create(s, at, "one too deep", true, T0)).isEmpty();
        }

        @Test
        @DisplayName("the notebook is bounded, because the player fills it by hand")
        void sizeIsBounded() {
            GameSave s = new GameSave();
            for (int i = 0; i < Notes.LIMIT; i++) {
                make(s, "", "n" + i, false);
            }
            assertThat(Notes.create(s, "", "one more", false, T0)).isEmpty();
        }

        /** ⚠ A blank name is a row nobody can point at, describe, or hear read out. */
        @Test
        @DisplayName("a blank name becomes a placeholder rather than a blank row")
        void blankNamesArePlaceheld() {
            GameSave s = new GameSave();
            assertThat(Notes.create(s, "", "   ", false, T0).orElseThrow().name).isEqualTo(Notes.UNTITLED);
            assertThat(Notes.create(s, "", null, false, T0).orElseThrow().name).isEqualTo(Notes.UNTITLED);
        }

        @Test
        @DisplayName("folders sort before notes, then A–Z")
        void ordering() {
            GameSave s = new GameSave();
            make(s, "", "zeta note", false);
            make(s, "", "alpha note", false);
            make(s, "", "zeta folder", true);
            make(s, "", "alpha folder", true);

            assertThat(Notes.childrenOf(s, ""))
                    .extracting(n -> n.name)
                    .containsExactly("alpha folder", "zeta folder", "alpha note", "zeta note");
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("an unchanged body reports no change, so nothing is persisted for a keystroke")
        void unchangedIsNoChange() {
            GameSave s = new GameSave();
            String note = make(s, "", "n", false);

            assertThat(Notes.write(s, note, "hello", T0)).isTrue();
            assertThat(Notes.write(s, note, "hello", T0.plusSeconds(1)))
                    .as("the editor calls this on a timer; an unchanged write must be free")
                    .isFalse();
        }

        @Test
        @DisplayName("a folder refuses text rather than holding a body nothing reads")
        void foldersHoldNoText() {
            GameSave s = new GameSave();
            String folder = make(s, "", "lore", true);
            assertThat(Notes.write(s, folder, "words", T0)).isFalse();
        }

        @Test
        @DisplayName("a body past the limit is truncated rather than refused")
        void bodyIsBounded() {
            GameSave s = new GameSave();
            String note = make(s, "", "n", false);
            Notes.write(s, note, "x".repeat(Notes.BODY_LIMIT + 500), T0);
            assertThat(Notes.byId(s, note).orElseThrow().body).hasSize(Notes.BODY_LIMIT);
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        /**
         * ⚠ RECURSIVE, unlike {@code Repac.delete}, and the difference is what is being deleted.
         *
         * <p>{@code Repac} never walks a tree because its filesystem is generated from game state —
         * there is nothing to remove. This tree is stored, and a folder delete that orphaned its
         * contents would leave notes alive with a parent that no longer exists: invisible in the
         * window, still in the save, still counting against the limit.
         */
        @Test
        @DisplayName("a folder takes everything inside it, at every depth")
        void foldersTakeTheirContents() {
            GameSave s = new GameSave();
            String outer = make(s, "", "outer", true);
            String inner = make(s, outer, "inner", true);
            make(s, inner, "deep", false);
            make(s, outer, "shallow", false);
            String survivor = make(s, "", "elsewhere", false);

            assertThat(Notes.delete(s, outer)).isEqualTo(4);
            assertThat(s.notes).extracting(n -> n.noteId).containsExactly(survivor);
        }

        @Test
        @DisplayName("a note takes only itself")
        void notesTakeOnlyThemselves() {
            GameSave s = new GameSave();
            String folder = make(s, "", "lore", true);
            String note = make(s, folder, "n", false);

            assertThat(Notes.delete(s, note)).isEqualTo(1);
            assertThat(Notes.byId(s, folder)).isPresent();
        }
    }

    @Nested
    @DisplayName("moving")
    class Moving {

        @Test
        @DisplayName("into a folder, and back to the root")
        void movesBothWays() {
            GameSave s = new GameSave();
            String folder = make(s, "", "lore", true);
            String note = make(s, "", "n", false);

            assertThat(Notes.move(s, note, folder, T0)).isTrue();
            assertThat(Notes.byId(s, note).orElseThrow().parentId).isEqualTo(folder);

            assertThat(Notes.move(s, note, "", T0)).isTrue();
            assertThat(Notes.byId(s, note).orElseThrow().parentId).isEmpty();
        }

        /**
         * ⚠ THE ONE THAT DETACHES A SUBTREE FROM THE ROOT.
         *
         * <p>Moving a folder inside its own descendant leaves the whole branch alive in the save,
         * counting against the limit, and unreachable from the tree — a leak with no error message
         * and nothing on screen to notice.
         */
        @Test
        @DisplayName("a folder cannot be moved into its own descendant")
        void noCycles() {
            GameSave s = new GameSave();
            String outer = make(s, "", "outer", true);
            String inner = make(s, outer, "inner", true);
            String deeper = make(s, inner, "deeper", true);

            assertThat(Notes.move(s, outer, inner, T0)).isFalse();
            assertThat(Notes.move(s, outer, deeper, T0)).isFalse();
            assertThat(Notes.move(s, outer, outer, T0)).isFalse();
            assertThat(Notes.byId(s, outer).orElseThrow().parentId)
                    .as("and it stayed where it was")
                    .isEmpty();
        }

        /**
         * ⚠ A hand-edited save can already contain a cycle, and the walks must survive one.
         *
         * <p>{@code depthOf} and the delete walk both follow parent pointers; on a cycle an unbounded
         * walk never returns and the client hangs on load, before any screen is drawn.
         */
        @Test
        @DisplayName("a save that already contains a cycle does not hang the walks")
        void aPlantedCycleTerminates() {
            GameSave s = new GameSave();
            String a = make(s, "", "a", true);
            String b = make(s, a, "b", true);
            // Planted directly, which is what a hand-edited save looks like.
            Notes.byId(s, a).orElseThrow().parentId = b;

            assertThat(Notes.depthOf(s, a)).isPositive();
            assertThat(Notes.delete(s, a)).isPositive();
        }
    }

    @Nested
    @DisplayName("the constraint that is not code")
    class NotARule {

        /**
         * ⚠ <b>Nothing in the notebook may ever be read by a rule.</b>
         *
         * <p>This cannot be asserted mechanically — it is a statement about code nobody has written
         * yet — so it is stated here where somebody adding a rule that consults a note will read it.
         * The moment a gate, price, threshold or outcome depends on note text, every note becomes a
         * save-editable input to the rules and the notebook becomes a cheat menu.
         *
         * <p>What IS checkable is the shape that keeps it honest: notes carry no ids that anything
         * else keys on, and nothing outside this class and the session port touches {@code notes}.
         */
        @Test
        @DisplayName("a note holds text and timestamps, and nothing a rule could branch on")
        void notesAreInert() {
            GameSave s = new GameSave();
            NoteState note = Notes.byId(s, make(s, "", "n", false)).orElseThrow();

            assertThat(note.body).isEmpty();
            assertThat(note.folder).isFalse();
            // If this list ever grows a numeric or enum field, ask what would read it.
            assertThat(NoteState.class.getFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder(
                            "noteId", "parentId", "name", "body", "folder", "createdAt", "updatedAt");
        }
    }
}
