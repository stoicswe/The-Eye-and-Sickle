package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeKind;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
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
 * Firmware upgrades: two things to hold, one thing to stop.
 *
 * <h2>What firmware is, in this game and in the world</h2>
 *
 * Firmware sits <em>below</em> the program it upgrades, and every rule here is that fact rather than a
 * game-ism. You cannot rewrite a device's firmware while the device is using it, so the install
 * refuses. The schematic is the authorisation and the image is the payload, so neither alone does
 * anything. And it costs more, because it is a permanent capability's payload rather than a
 * consumable.
 *
 * <h2>⚠ The invariant this must not break</h2>
 *
 * The image is purchasable and the capability is not. {@code docs/design/02-unlock-gates.md} §1.1
 * sanctions exactly this split — <em>"Rainbow Table is EC + schematic (buy the table, but the
 * capability to use it is found)"</em> — under the standing condition that <b>the ceiling component is
 * on the non-EC side</b>. {@link Gating} is the guard: money must never produce the schematic, and the
 * image alone must never install.
 */
class FirmwareTest {

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");
    private static final String IMAGE = "firmware-implant";

    private static GameEngine game(Path dir) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    /** A repacked firmware image sitting in Downloads, ready to install. */
    private static String holdImage(GameEngine game) {
        StoredFileState file = Repac.arrive(
                game.state(),
                "/Users/operator/Downloads",
                "mining-firmware.pkg",
                "10.0.0.9",
                1_000L,
                IMAGE,
                new io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion(3, 1),
                T0);
        Repac.repack(game.state(), file, T0);
        return file.path();
    }

    /** Stops everything that would hold the mining tool open. */
    private static void stopMining(GameSave save) {
        save.rig.selfMiningCycles = 0L;
        for (NodeState node : save.knownNodes) {
            node.deployedMiners.clear();
        }
    }

    @Nested
    @DisplayName("⚠ the gate: money buys the image, never the capability")
    class Gating {

        @Test
        @DisplayName("the image alone does not install")
        void imageAloneIsInert(@TempDir Path dir) {
            GameEngine game = game(dir);
            stopMining(game.state());
            String path = holdImage(game);
            // Nothing about holding, paying for, or having stolen the image matters. The schematic is
            // the gate, and this is the line that keeps I2 true while the image stays purchasable.
            Repac.Result result = Repac.install(game.state(), path, T0);
            assertThat(result.ok()).isFalse();
            assertThat(result.refusal()).isEqualTo(Repac.Refusal.NO_SCHEMATIC);
            assertThat(result.message()).contains("schematic");
        }

        @Test
        @DisplayName("with the schematic and mining stopped, the flash starts")
        void withBothItFlashes(@TempDir Path dir) {
            GameEngine game = game(dir);
            stopMining(game.state());
            game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
            String path = holdImage(game);

            Repac.Result result = Repac.install(game.state(), path, T0);
            assertThat(result.ok()).as(result.message()).isTrue();
            // ⚠ Accepted, not completed. Firmware does not install — it flashes, over ~90 seconds,
            // with the mining tool frozen. What happens at the far end of that is FirmwareFlashTest's
            // subject; what this asserts is that both conditions being met lets it begin.
            assertThat(Repac.flashing(game.state())).isPresent();
            assertThat(game.state().items).noneMatch(item -> IMAGE.equals(item.itemType));
        }

        /**
         * ⚠ The catalogue must not be able to grow a firmware entry with no schematic.
         *
         * <p>That is the one edit that would turn firmware into a permanent capability reachable with
         * money alone — {@code docs/design/11} §4 rule 1, "no EC path, no exceptions" — and it is
         * exactly the edit somebody adding a second firmware item would make by omission.
         */
        @Test
        @DisplayName("firmware with no schematic named is rejected at construction")
        void firmwareMustNameItsSchematic() {
            assertThatThrownBy(() -> new Catalogue.Offering(
                            "rogue-firmware",
                            "Rogue",
                            "desc",
                            UnlockGate.ETHECOIN,
                            Balance.ec("1"),
                            0L,
                            "",
                            UpgradeKind.FIRMWARE,
                            "",
                            "mining"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("schematic");
        }

        @Test
        @DisplayName("no schematic is purchasable, at any price")
        void schematicsAreNotSold() {
            // The other half of the same guarantee. If a schematic ever appeared as an offering, the
            // split above would collapse into "money buys the ceiling" in one commit.
            assertThat(Catalogue.offerings())
                    .noneMatch(offering -> offering.id().equals(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC)
                            && offering.purchasable()
                            && offering.kind() != UpgradeKind.FIRMWARE);
            assertThat(Catalogue.byId(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC))
                    .as("the id names the IMAGE; the schematic itself is not a catalogue entry")
                    .isPresent()
                    .get()
                    .satisfies(offering -> assertThat(offering.firmware()).isTrue());
        }
    }

    @Nested
    @DisplayName("⚠ the affected tool must be stopped")
    class Stopped {

        @Test
        @DisplayName("self-mining blocks the flash, and the refusal says what is running")
        void selfMiningBlocks(@TempDir Path dir) {
            GameEngine game = game(dir);
            stopMining(game.state());
            game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
            game.state().rig.selfMiningCycles = 12L;
            String path = holdImage(game);

            Repac.Result result = Repac.install(game.state(), path, T0);
            assertThat(result.refusal()).isEqualTo(Repac.Refusal.TOOL_RUNNING);
            // Naming the figure is what makes it actionable. "The tool is running" sends a player to
            // guess which readout to look at.
            assertThat(result.message()).contains("12");
        }

        /**
         * ⚠ The half a player will not think of.
         *
         * <p>A deployed miner spends the <em>host's</em> compute (<b>I6</b>) — but it is still this
         * rig's mining software driving it, so flashing while one runs is the same interrupted write.
         * Nothing on the player's own rig looks busy, which is exactly why the refusal has to name it.
         */
        @Test
        @DisplayName("a deployed miner blocks it too, even with self-mining at zero")
        void deployedMinersBlock(@TempDir Path dir) {
            GameEngine game = game(dir);
            stopMining(game.state());
            game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
            NodeState node = new NodeState();
            node.address = "10.0.0.5";
            node.deployedMiners.add(new MinerState());
            game.state().knownNodes.add(node);
            String path = holdImage(game);

            Repac.Result result = Repac.install(game.state(), path, T0);
            assertThat(result.refusal()).isEqualTo(Repac.Refusal.TOOL_RUNNING);
            assertThat(result.message()).contains("deployed");
        }

        /**
         * ⚠ Ordering. A player missing both is told about the schematic, not the mining.
         *
         * <p>Telling them to stop mining first costs them their hashrate and then refuses again for a
         * schematic they were never going to have on the way back.
         */
        @Test
        @DisplayName("missing both reports the schematic, not the running tool")
        void schematicIsReportedFirst(@TempDir Path dir) {
            GameEngine game = game(dir);
            stopMining(game.state());
            game.state().rig.selfMiningCycles = 40L;
            String path = holdImage(game);
            assertThat(Repac.install(game.state(), path, T0).refusal()).isEqualTo(Repac.Refusal.NO_SCHEMATIC);
        }

        @Test
        @DisplayName("ordinary software installs while mining runs")
        void softwareIsUnaffected(@TempDir Path dir) {
            // The rule is firmware's, not a new rule for every upgrade. A sweep tool does not care
            // what the miner is doing.
            GameEngine game = game(dir);
            stopMining(game.state());
            game.state().rig.selfMiningCycles = 40L;
            StoredFileState file = Repac.arrive(
                    game.state(),
                    "/Users/operator/Downloads",
                    "sweep.pkg",
                    "10.0.0.9",
                    1_000L,
                    "net-sweep-wide",
                    new io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion(2, 0),
                    T0);
            Repac.repack(game.state(), file, T0);
            assertThat(Repac.install(game.state(), file.path(), T0).ok()).isTrue();
        }
    }

    @Nested
    @DisplayName("acquisition and price")
    class Acquisition {

        @Test
        @DisplayName("firmware costs more than any ordinary upgrade")
        void firmwareIsExpensive() {
            java.math.BigInteger dearestSoftware = Catalogue.offerings().stream()
                    .filter(offering -> !offering.firmware())
                    .map(Catalogue.Offering::priceWei)
                    .max(java.math.BigInteger::compareTo)
                    .orElse(java.math.BigInteger.ZERO);
            assertThat(Balance.FIRMWARE_IMPLANT_IMAGE_PRICE)
                    .as("firmware is a permanent capability's payload, not a consumable")
                    .isGreaterThan(dearestSoftware);
        }

        /**
         * ⚠ It has to be stealable, or the two-part requirement is pointless.
         *
         * <p>{@code docs/design/01-core-resources.md} §6 makes raiding a first-class acquisition
         * route. An image that could only be bought would make the breach route dead content and
         * reduce the whole design to "an expensive purchase".
         */
        @Test
        @DisplayName("the image is reachable by breaching, not only by buying")
        void reachableByBreaching(@TempDir Path dir) {
            GameEngine game = game(dir);
            var host = game.state().topology.hosts.stream()
                    .filter(entry -> !"SELF".equals(entry.kind))
                    .findFirst()
                    .orElseThrow();
            host.foothold = true;
            var firmwareOnHost = game.list(host.address, "/Applications").stream()
                    .filter(entry -> entry.name().equals("Mining.app"))
                    .flatMap(entry -> game.list(host.address, entry.path() + "/Contents/Upgrades").stream())
                    .toList();
            assertThat(firmwareOnHost).isNotEmpty();
            // ⚠ Named `-firmware`, so a player can see what it is in `ls` before spending anything —
            // the same argument as the .pkg/.upg rename.
            assertThat(firmwareOnHost.getFirst().name()).contains("firmware");
            assertThat(game.upgradeAt(host.address, firmwareOnHost.getFirst().path()))
                    .get()
                    .satisfies(offer -> {
                        assertThat(offer.firmware()).isTrue();
                        assertThat(offer.itemType()).isEqualTo(IMAGE);
                    });
        }

        @Test
        @DisplayName("Get info states both conditions before the transfer")
        void getInfoSaysWhatItNeeds(@TempDir Path dir) {
            // The whole point: a package that cost a download to discover you cannot flash would be
            // the defect this panel exists to remove, wearing a new face.
            GameEngine game = game(dir);
            stopMining(game.state());
            String path = holdImage(game);
            assertThat(game.info("", path))
                    .anyMatch(line -> line.contains("FIRMWARE"))
                    .anyMatch(line -> line.contains("schematic"));

            game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
            game.state().rig.selfMiningCycles = 20L;
            assertThat(game.upgradeAt("", path)).get().satisfies(offer -> {
                assertThat(offer.haveSchematic()).isTrue();
                assertThat(offer.readyToFlash()).isFalse();
                assertThat(offer.flashRequirement()).contains("Mining is running");
            });
        }
    }
}
