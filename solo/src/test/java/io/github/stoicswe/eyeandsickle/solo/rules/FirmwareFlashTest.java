package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Catalogue;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.StoredFileState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Flashing firmware: a minute and a half with the tool down.
 *
 * <h2>Why this is a task rather than an instant install</h2>
 *
 * Every other install in the game is instantaneous, because the interesting wait — somebody else's
 * uplink — already happened during the download. Firmware is the deliberate exception: the affected
 * tool is frozen for the duration, so the cost is real income foregone rather than a bar to watch.
 * Being a {@code TaskState} is what makes it survive a quit, which a device writing its own memory
 * must.
 */
class FirmwareFlashTest {

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");
    private static final String IMAGE = "firmware-implant";

    private static final class Winding extends Clock {
        private Instant now;

        Winding(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** A rig holding the schematic, mining stopped, with a flashable image in Downloads. */
    private static String ready(SoloGame game) {
        SoloSave save = game.state();
        save.schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
        save.rig.selfMiningCycles = 0L;
        save.knownNodes.forEach(node -> node.deployedMiners.clear());
        StoredFileState file = Repac.arrive(
                save,
                "/Users/operator/Downloads",
                "mining-firmware.pkg",
                "10.0.0.9",
                1_000L,
                IMAGE,
                new UpgradeVersion(4, 2),
                T0);
        Repac.repack(save, file, T0);
        return file.path();
    }

    @Nested
    @DisplayName("the file")
    class Naming {

        /**
         * ⚠ {@code .pkg → .frm}, and the first arrow is deliberately unchanged.
         *
         * <p>The {@code .pkg} rename IS the confirmation lock — a bought package stays a vendor
         * package until its payment is mined. Naming firmware {@code .frm} at both ends would leave
         * it with no rename to make, and a purchased image would become flashable before its money
         * moved.
         */
        @Test
        @DisplayName("firmware repacks to .frm, software still to .upg")
        void suffixes(@TempDir Path dir) {
            SoloGame game = game(dir);
            StoredFileState firmware = Repac.arrive(
                    game.state(),
                    "/Users/operator/Downloads",
                    "mining-firmware.pkg",
                    "10.0.0.9",
                    1_000L,
                    IMAGE,
                    new UpgradeVersion(4, 2),
                    T0);
            Repac.repack(game.state(), firmware, T0);
            assertThat(firmware.name).endsWith(".frm");

            StoredFileState software = Repac.arrive(
                    game.state(),
                    "/Users/operator/Downloads",
                    "sweep.pkg",
                    "10.0.0.9",
                    1_000L,
                    "net-sweep-wide",
                    new UpgradeVersion(2, 0),
                    T0);
            Repac.repack(game.state(), software, T0);
            assertThat(software.name).endsWith(".upg");
        }
    }

    @Nested
    @DisplayName("the flash")
    class Flashing {

        @Test
        @DisplayName("installing firmware starts a task and grants nothing yet")
        void startsATask(@TempDir Path dir) {
            SoloGame game = game(dir);
            String path = ready(game);

            Repac.Result result = Repac.install(game.state(), path, T0);
            assertThat(result.ok()).as(result.message()).isTrue();
            assertThat(Repac.flashing(game.state())).isPresent();
            // ⚠ Nothing is owned yet, and the image is STILL ON DISK. Consuming it at the start would
            // leave a player who quit mid-flash with neither the image nor the tool.
            assertThat(game.state().items).noneMatch(item -> IMAGE.equals(item.itemType));
            assertThat(game.state().files).anyMatch(file -> file.path().equals(path));
        }

        @Test
        @DisplayName("it takes about a minute and a half, then grants the item and eats the image")
        void completes(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
            String path = ready(game);
            Repac.install(game.state(), path, T0);

            clock.advance(Duration.ofSeconds(Balance.FIRMWARE_FLASH_SECONDS / 2));
            game.tick();
            assertThat(Repac.flashing(game.state())).as("still writing").isPresent();

            clock.advance(Duration.ofSeconds(Balance.FIRMWARE_FLASH_SECONDS));
            game.tick();
            assertThat(Repac.flashing(game.state())).isEmpty();
            assertThat(game.state().items).anyMatch(item -> IMAGE.equals(item.itemType));
            assertThat(game.state().files).noneMatch(file -> file.path().equals(path));
        }

        @Test
        @DisplayName("the duration is in the one-to-two minute band the design asks for")
        void duration() {
            assertThat(Balance.FIRMWARE_FLASH_SECONDS).isBetween(60L, 120L);
        }

        /**
         * ⚠ It must survive a quit, because a device writing its own memory does not stop when
         * nobody is looking — and because losing the image to a closed window would be the worst
         * possible outcome of a 90-second wait.
         */
        @Test
        @DisplayName("a flash that finishes while the game is closed settles on the way back in")
        void settlesOnResume(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            SaveStore store = new SaveStore(dir.resolve("s.json"));
            SoloGame game = SoloGame.open(store, "operator", clock);
            String path = ready(game);
            Repac.install(game.state(), path, T0);
            game.persist();

            clock.advance(Duration.ofHours(3));
            SoloGame reopened = SoloGame.open(store, "operator", clock);
            reopened.resume();

            assertThat(Repac.flashing(reopened.state())).isEmpty();
            assertThat(reopened.state().items).anyMatch(item -> IMAGE.equals(item.itemType));
        }

        @Test
        @DisplayName("two flashes at once are refused")
        void oneAtATime(@TempDir Path dir) {
            SoloGame game = game(dir);
            String first = ready(game);
            StoredFileState second = Repac.arrive(
                    game.state(),
                    "/Users/operator/Downloads",
                    "mining-firmware-2.pkg",
                    "10.0.0.8",
                    1_000L,
                    IMAGE,
                    new UpgradeVersion(3, 0),
                    T0);
            Repac.repack(game.state(), second, T0);

            assertThat(Repac.install(game.state(), first, T0).ok()).isTrue();
            // Two concurrent writes to the same device is how it is bricked.
            assertThat(Repac.install(game.state(), second.path(), T0).refusal()).isEqualTo(Repac.Refusal.TOOL_RUNNING);
        }
    }

    @Nested
    @DisplayName("⚠ the tool is frozen for the duration")
    class Frozen {

        /**
         * ⚠ The other half of "stop the tool to flash it".
         *
         * <p>Requiring mining stopped at the door and then letting the player restart it two seconds
         * later would make the rule a formality — and the flash's real cost is exactly the income
         * given up while the tool is down.
         */
        @Test
        @DisplayName("self-mining cannot be started while a flash runs")
        void miningIsFrozen(@TempDir Path dir) {
            SoloGame game = game(dir);
            String path = ready(game);
            Repac.install(game.state(), path, T0);

            assertThat(Repac.frozenTool(game.state())).isEqualTo(Catalogue.MINING_TOOL);
            assertThat(game.allocateSelfMining(20L)).isFalse();
            assertThat(game.state().rig.selfMiningCycles).isZero();
        }

        /**
         * ⚠ Stopping is always allowed, even mid-flash.
         *
         * <p>A rule that traps a player's cycles inside a tool they cannot use is a bug wearing a
         * rule's clothes. Only *raising* the allocation is refused.
         */
        @Test
        @DisplayName("setting mining to zero is never refused")
        void stoppingIsAlwaysAllowed(@TempDir Path dir) {
            SoloGame game = game(dir);
            String path = ready(game);
            game.state().rig.selfMiningCycles = 10L;
            Repac.install(game.state(), path, T0);
            assertThat(game.allocateSelfMining(0L)).isTrue();
        }

        @Test
        @DisplayName("mining works again once the flash finishes")
        void thawsOnCompletion(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
            String path = ready(game);
            Repac.install(game.state(), path, T0);

            clock.advance(Duration.ofSeconds(Balance.FIRMWARE_FLASH_SECONDS + 5));
            game.tick();
            assertThat(Repac.frozenTool(game.state())).isEmpty();
            assertThat(game.allocateSelfMining(5L)).isTrue();
        }
    }

    private static SoloGame game(Path dir) {
        return SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", Clock.fixed(T0, ZoneOffset.UTC));
    }
}
