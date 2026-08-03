package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deleting files from your own rig.
 *
 * <h2>Why this had to exist</h2>
 *
 * Downloads accumulate and nothing removed them. A player who has raided a dozen machines ends up
 * with a dozen packages they will never install and — for the schematic-gated ones — cannot sell
 * either (<b>I2</b>). A filesystem you can only add to is not a filesystem, and deleting your own
 * files is the most ordinary thing a computer does.
 */
class DeleteFileTest {

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");

    private static GameEngine game(Path dir) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static StoredFileState downloaded(GameEngine game, String name, String itemType) {
        StoredFileState file = Repac.arrive(
                game.state(),
                "/Users/operator/Downloads",
                name,
                "10.0.0.9",
                1_000L,
                itemType,
                new UpgradeVersion(3, 1),
                T0);
        Repac.repack(game.state(), file, T0);
        return file;
    }

    @Nested
    @DisplayName("what can be deleted")
    class Allowed {

        @Test
        @DisplayName("a downloaded package goes, and the file is really gone")
        void deletesAPackage(@TempDir Path dir) {
            GameEngine game = game(dir);
            StoredFileState file = downloaded(game, "sweep.pkg", "net-sweep-wide");

            assertThat(Repac.delete(game.state(), file.path(), T0).ok()).isTrue();
            assertThat(game.state().files).noneMatch(entry -> entry.path().equals(file.path()));
        }

        /**
         * ⚠ The value is named in the log on the way out.
         *
         * <p>A player who deletes a package worth real ethecoin by accident should find out from
         * their own log, not from the market three days later. An item that vanishes with no trace is
         * indistinguishable from a bug.
         */
        @Test
        @DisplayName("the log says what it would have sold for")
        void logsTheValue(@TempDir Path dir) {
            GameEngine game = game(dir);
            StoredFileState file = downloaded(game, "sweep.pkg", "net-sweep-wide");
            Repac.delete(game.state(), file.path(), T0);
            assertThat(game.state().log)
                    .anyMatch(line -> line.message.contains("deleted") && line.message.contains("sold for"));
        }

        @Test
        @DisplayName("a non-package file goes too — most accumulated junk is not a package")
        void deletesAnyStoredFile(@TempDir Path dir) {
            GameEngine game = game(dir);
            StoredFileState file = Repac.arrive(
                    game.state(),
                    "/Users/operator/Downloads",
                    "notes.txt",
                    "10.0.0.9",
                    400L,
                    "",
                    UpgradeVersion.UNKNOWN,
                    T0);
            assertThat(Repac.delete(game.state(), file.path(), T0).ok()).isTrue();
        }
    }

    @Nested
    @DisplayName("what cannot")
    class Refused {

        /**
         * ⚠ Generated entries have nothing to delete.
         *
         * <p>{@code VirtualFs} derives the system tree, the app bundles and the vault views from
         * state and stores none of it — deliberately, because a stored tree is a cache of game state
         * that eventually disagrees with it. Pretending to succeed would leave the entry on screen
         * and the player concluding the file manager is broken.
         */
        @Test
        @DisplayName("a generated path is refused, and the refusal says why")
        void generatedPathsAreRefused(@TempDir Path dir) {
            GameEngine game = game(dir);
            Repac.Result result = Repac.delete(game.state(), "/System/etc/rc.conf", T0);
            assertThat(result.ok()).isFalse();
            assertThat(result.refusal()).isEqualTo(Repac.Refusal.NO_SUCH_FILE);
            assertThat(result.message()).contains("generated");
        }

        /**
         * ⚠ Deleting an image mid-flash would leave the player with nothing.
         *
         * <p>{@code completeFlash} drops the task silently when the image is missing, so without this
         * a player could delete mid-write, wait out the remaining minute, and receive nothing — with
         * the log claiming a flash had run.
         */
        @Test
        @DisplayName("an image being flashed cannot be deleted")
        void notWhileFlashing(@TempDir Path dir) {
            GameEngine game = game(dir);
            game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
            game.state().rig.selfMiningCycles = 0L;
            game.state().knownNodes.forEach(node -> node.deployedMiners.clear());
            StoredFileState image = downloaded(game, "mining-firmware.pkg", "firmware-implant");
            Repac.install(game.state(), image.path(), T0);

            Repac.Result result = Repac.delete(game.state(), image.path(), T0);
            assertThat(result.ok()).isFalse();
            assertThat(result.refusal()).isEqualTo(Repac.Refusal.TOOL_RUNNING);
            assertThat(game.state().files).anyMatch(entry -> entry.path().equals(image.path()));
        }

        /**
         * ⚠ Somebody else's machine is not yours to tidy.
         *
         * <p>{@code AccessLog} already holds the line that a remote actor <em>blanks</em> a log line
         * rather than removing it, because a deleted row turns a legible crime into a missing file.
         * A remote delete here would quietly grant the thing that rule exists to refuse.
         */
        @Test
        @DisplayName("deleting on a machine you broke into is refused")
        void notOnSomebodyElsesMachine(@TempDir Path dir) {
            GameEngine game = game(dir);
            StoredFileState file = downloaded(game, "sweep.pkg", "net-sweep-wide");
            Repac.Result result = game.delete("10.0.0.9", file.path());
            assertThat(result.ok()).isFalse();
            assertThat(game.state().files).anyMatch(entry -> entry.path().equals(file.path()));
        }

        @Test
        @DisplayName("your own rig is reachable by a blank address and by its own")
        void ownRigIsAllowedEitherWay(@TempDir Path dir) {
            GameEngine game = game(dir);
            StoredFileState first = downloaded(game, "a.pkg", "net-sweep-wide");
            assertThat(game.delete("", first.path()).ok()).isTrue();
            StoredFileState second = downloaded(game, "b.pkg", "net-sweep-wide");
            assertThat(game.delete(null, second.path()).ok()).isTrue();
        }
    }
}
