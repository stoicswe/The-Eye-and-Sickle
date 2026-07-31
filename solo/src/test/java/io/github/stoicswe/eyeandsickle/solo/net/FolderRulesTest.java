package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
import io.github.stoicswe.eyeandsickle.solo.state.FolderState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the player's filing of what they have found.
 *
 * <p>Filing is mechanically inert, so there is no balance number here to defend and no invariant in
 * reach. What is worth asserting is the other thing: that a feature the player can point at any part
 * of cannot be used to <b>learn</b> anything they have not paid for, and that a tree they can
 * hand-edit into nonsense still opens.
 */
class FolderRulesTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    private static SoloSave save(String... discovered) {
        SoloSave save = new SoloSave();
        for (String address : discovered) {
            NodeState node = new NodeState();
            node.address = address;
            save.knownNodes.add(node);
        }
        return save;
    }

    private static String mkdir(SoloSave save, String parentId, String name) {
        FolderRules.Result result = FolderRules.create(save, parentId, name, T0);
        assertThat(result.refused()).as("create '%s': %s", name, result.why()).isFalse();
        return result.folderId();
    }

    @Nested
    @DisplayName("making folders")
    class Making {

        @Test
        @DisplayName("a folder nests under another and the tree reports its depth and path")
        void nests() {
            SoloSave save = save();
            String eye = mkdir(save, "", "eye");
            mkdir(save, eye, "relays");

            List<NetFolder> tree = FolderRules.tree(save);
            assertThat(tree).hasSize(2);
            assertThat(tree.get(0).name()).isEqualTo("eye");
            assertThat(tree.get(0).depth()).isZero();
            assertThat(tree.get(0).path()).isEqualTo("/eye");
            assertThat(tree.get(1).depth()).isEqualTo(1);
            assertThat(tree.get(1).path()).isEqualTo("/eye/relays");
        }

        @Test
        @DisplayName("parents come before children, so a renderer can walk the list once")
        void preOrder() {
            SoloSave save = save();
            String a = mkdir(save, "", "alpha");
            mkdir(save, a, "inner");
            mkdir(save, "", "beta");

            // The order IS the contract — both the window and the terminal indent by depth without
            // traversing. If either did its own walk they would eventually order siblings
            // differently, which is the C1 parity failure hardest to notice.
            assertThat(FolderRules.tree(save).stream().map(NetFolder::path))
                    .containsExactly("/alpha", "/alpha/inner", "/beta");
        }

        @Test
        @DisplayName("two folders in the same place cannot share a name; in different places they can")
        void siblingNames() {
            SoloSave save = save();
            String eye = mkdir(save, "", "eye");
            assertThat(FolderRules.create(save, "", "EYE", T0).refused()).isTrue();
            // Different parent, same name: fine. A path disambiguates them and nothing indexes by
            // name, so there is nothing here to collide.
            assertThat(FolderRules.create(save, eye, "eye", T0).refused()).isFalse();
        }

        @Test
        @DisplayName("a name cannot be empty, over-long, or contain the path separator")
        void names() {
            SoloSave save = save();
            assertThat(FolderRules.create(save, "", "  ", T0).refused()).isTrue();
            assertThat(FolderRules.create(save, "", "x".repeat(FolderRules.MAX_NAME + 1), T0)
                            .refused())
                    .isTrue();
            // '/' separates path segments everywhere this tree is printed, so a name containing one
            // would render a path the player could not type back.
            assertThat(FolderRules.create(save, "", "eye/relays", T0).refused()).isTrue();
            assertThat(FolderRules.create(save, "", "two words", T0).refused()).isFalse();
        }

        @Test
        @DisplayName("nesting stops at the published depth")
        void depthCap() {
            SoloSave save = save();
            String at = "";
            for (int level = 0; level <= FolderRules.MAX_DEPTH; level++) {
                at = mkdir(save, at, "level" + level);
            }
            assertThat(FolderRules.create(save, at, "toofar", T0).refused()).isTrue();
        }
    }

    @Nested
    @DisplayName("moving and removing")
    class Moving {

        @Test
        @DisplayName("a folder cannot be moved inside itself or inside its own descendant")
        void noCycles() {
            SoloSave save = save();
            String outer = mkdir(save, "", "outer");
            String inner = mkdir(save, outer, "inner");

            assertThat(FolderRules.move(save, outer, outer).refused()).isTrue();
            // The cycle this prevents is not cosmetic: it would be unreachable from the root, so
            // every folder in it and everything filed under them would vanish from the tree at once
            // while still sitting in the save on disk.
            assertThat(FolderRules.move(save, outer, inner).refused()).isTrue();
            assertThat(FolderRules.tree(save)).hasSize(2);
        }

        @Test
        @DisplayName("removing a folder lifts what was inside it up a level rather than deleting it")
        void removeIsNotRecursive() {
            SoloSave save = save("10.0.0.4");
            String outer = mkdir(save, "", "outer");
            String inner = mkdir(save, outer, "inner");
            assertThat(FolderRules.file(save, "10.0.0.4", inner).refused()).isFalse();

            assertThat(FolderRules.remove(save, inner).refused()).isFalse();

            // The machine did not become unfiled and the sub-folder did not disappear: both
            // re-parented to where the removed folder was. Filing carries no risk lesson, so a
            // mis-click must cost a flattened level and nothing more.
            assertThat(FolderRules.tree(save)).hasSize(1);
            assertThat(FolderRules.tree(save).getFirst().addresses()).containsExactly("10.0.0.4");
            assertThat(FolderRules.tree(save).getFirst().folderId()).isEqualTo(outer);
        }

        @Test
        @DisplayName("a moved subtree still cannot exceed the depth cap")
        void moveRespectsDepth() {
            SoloSave save = save();
            String deep = "";
            for (int level = 0; level < FolderRules.MAX_DEPTH; level++) {
                deep = mkdir(save, deep, "level" + level);
            }
            String tall = mkdir(save, "", "tall");
            mkdir(save, tall, "child");

            // `tall` is only one level deep itself, but it is two levels TALL — and it is the height
            // that would land past the cap. Measuring the folder alone is the bug this covers.
            assertThat(FolderRules.move(save, tall, deep).refused()).isTrue();
        }
    }

    @Nested
    @DisplayName("filing machines")
    class Filing {

        @Test
        @DisplayName("a machine is in one folder at a time; filing it again moves it")
        void oneFolder() {
            SoloSave save = save("10.0.0.4");
            String a = mkdir(save, "", "a");
            String b = mkdir(save, "", "b");

            assertThat(FolderRules.file(save, "10.0.0.4", a).refused()).isFalse();
            assertThat(FolderRules.file(save, "10.0.0.4", b).refused()).isFalse();

            List<NetFolder> tree = FolderRules.tree(save);
            assertThat(tree.stream()
                            .filter(f -> f.folderId().equals(a))
                            .findFirst()
                            .orElseThrow()
                            .addresses())
                    .isEmpty();
            assertThat(tree.stream()
                            .filter(f -> f.folderId().equals(b))
                            .findFirst()
                            .orElseThrow()
                            .addresses())
                    .containsExactly("10.0.0.4");
        }

        @Test
        @DisplayName("filing to a blank folder is how a machine is taken back out")
        void unfile() {
            SoloSave save = save("10.0.0.4");
            String a = mkdir(save, "", "a");
            FolderRules.file(save, "10.0.0.4", a);
            assertThat(FolderRules.unfiled(save)).isEmpty();

            assertThat(FolderRules.file(save, "10.0.0.4", "").refused()).isFalse();
            assertThat(FolderRules.unfiled(save)).containsExactly("10.0.0.4");
        }

        @Test
        @DisplayName("an undiscovered address and a fictional one are refused in the SAME WORDS")
        void noOracle() {
            SoloSave save = save("10.0.0.4");
            String a = mkdir(save, "", "a");

            String undiscovered = FolderRules.file(save, "10.0.0.9", a).why();
            String fictional = FolderRules.file(save, "not-an-address", a).why();

            // ⚠ The point of the test, and it is not tidiness. Two distinguishable refusals would let
            // a player enumerate the whole world one guess at a time — for free, with no sweep, no
            // cycles and no noise — which is the entire product the sweep ladder is sold on
            // (docs/design/07-recon-tools.md §1).
            assertThat(undiscovered).isNotEmpty().isEqualTo(fictional);
        }

        @Test
        @DisplayName("the subtree count includes sub-folders; the address list does not")
        void counts() {
            SoloSave save = save("10.0.0.4", "10.0.0.5");
            String outer = mkdir(save, "", "outer");
            String inner = mkdir(save, outer, "inner");
            FolderRules.file(save, "10.0.0.4", outer);
            FolderRules.file(save, "10.0.0.5", inner);

            NetFolder top = FolderRules.tree(save).getFirst();
            assertThat(top.addresses()).containsExactly("10.0.0.4");
            // The number a collapsed row shows: what a player uses to decide whether opening it is
            // worth it, which is useless if it stops at the first level.
            assertThat(top.subtreeCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("a save the player has edited by hand still opens")
    class Repair {

        @Test
        @DisplayName("a folder whose parent is gone re-roots instead of vanishing with its contents")
        void orphanReRoots() {
            SoloSave save = save("10.0.0.4");
            String orphan = mkdir(save, "", "orphan");
            FolderRules.file(save, "10.0.0.4", orphan);
            FolderRules.find(save, orphan).parentId = "a-folder-that-was-deleted-in-a-text-editor";

            List<NetFolder> tree = FolderRules.tree(save);
            assertThat(tree).hasSize(1);
            assertThat(tree.getFirst().depth()).isZero();
            // Dropping the orphan would have silently deleted the machine filed under it, which is
            // the one outcome a repair pass must never produce.
            assertThat(tree.getFirst().addresses()).containsExactly("10.0.0.4");
        }

        @Test
        @DisplayName("two folders pointing at each other are broken apart rather than disappearing")
        void cycleIsBroken() {
            SoloSave save = save();
            String a = mkdir(save, "", "a");
            String b = mkdir(save, "", "b");
            FolderRules.find(save, a).parentId = b;
            FolderRules.find(save, b).parentId = a;

            assertThat(FolderRules.tree(save)).hasSize(2);
        }

        @Test
        @DisplayName("a machine filed under a folder that no longer exists becomes unfiled")
        void danglingFiling() {
            SoloSave save = save("10.0.0.4");
            save.knownNodes.getFirst().folderId = "gone";
            assertThat(FolderRules.unfiled(save)).containsExactly("10.0.0.4");
        }

        @Test
        @DisplayName("repair is idempotent — a healthy tree passes through untouched")
        void idempotent() {
            SoloSave save = save("10.0.0.4");
            String outer = mkdir(save, "", "outer");
            mkdir(save, outer, "inner");
            FolderRules.file(save, "10.0.0.4", outer);

            List<NetFolder> once = FolderRules.tree(save);
            FolderRules.repair(save);
            FolderRules.repair(save);
            assertThat(FolderRules.tree(save)).isEqualTo(once);
        }

        @Test
        @DisplayName("an empty or absent filing is an empty tree, never a throw")
        void emptyIsSafe() {
            assertThat(FolderRules.tree(null)).isEmpty();
            assertThat(FolderRules.unfiled(null)).isEmpty();
            SoloSave save = new SoloSave();
            save.netFolders = null;
            assertThat(FolderRules.tree(save)).isEmpty();
        }
    }

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        @DisplayName("a path resolves case-insensitively, with or without leading and trailing slashes")
        void byPath() {
            SoloSave save = save();
            String eye = mkdir(save, "", "eye");
            String relays = mkdir(save, eye, "relays");

            assertThat(FolderRules.byPath(save, "/eye/relays"))
                    .isNotNull()
                    .extracting(f -> f.folderId)
                    .isEqualTo(relays);
            assertThat(FolderRules.byPath(save, "EYE/Relays/"))
                    .isNotNull()
                    .extracting(f -> f.folderId)
                    .isEqualTo(relays);
            assertThat(FolderRules.byPath(save, "eye/missing")).isNull();
            assertThat(FolderRules.byPath(save, "/")).isNull();
        }

        @Test
        @DisplayName("a renamed folder keeps its id, so an intent that named the id still lands")
        void renameKeepsIdentity() {
            SoloSave save = save();
            String eye = mkdir(save, "", "eye");
            assertThat(FolderRules.rename(save, eye, "the eye").refused()).isFalse();

            FolderState found = FolderRules.find(save, eye);
            assertThat(found).isNotNull();
            assertThat(found.name).isEqualTo("the eye");
            assertThat(FolderRules.tree(save).getFirst().path()).isEqualTo("/the eye");
        }
    }
}
