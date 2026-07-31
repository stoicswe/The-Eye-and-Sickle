package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;

/**
 * Renders the upgrade compare block in each of its four standings.
 *
 * <h2>Why this needs a picture</h2>
 *
 * All four states are the same widget with different text, which is exactly the shape that looks
 * correct in review and lays out wrong on screen: {@code none} is shorter than {@code v4.0}, the
 * {@code OLDER} verdict is the longest sentence of the four, and the two version cells have to stay
 * aligned across all of it. Nothing but a render says whether they do.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.UpgradeCompareSnapshot \
 *     -Dexec.args="/tmp/upg"
 * }</pre>
 */
public final class UpgradeCompareSnapshot {

    private UpgradeCompareSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        out.toFile().mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static UpgradeOffer offer(
            UpgradeVersion theirs,
            UpgradeVersion yours,
            UpgradeOffer.Standing standing,
            java.math.BigInteger resale,
            boolean sellable) {
        return new UpgradeOffer(
                "net-sweep-wide",
                "Net Sweep (Wide)",
                "A wider sweep of the same distance. Finds quieter machines inside the reach you "
                        + "already have. It does not reach further — reach is not for sale.",
                theirs,
                yours,
                standing,
                UnlockGate.ETHECOIN,
                187_000_000L,
                resale,
                sellable,
                0L);
    }

    /** The Firmware Implant image, in a given readiness. */
    private static UpgradeOffer fw(UpgradeOffer.Standing standing, boolean schematic, String blocked) {
        return new UpgradeOffer(
                "firmware-implant",
                "Firmware Implant (image)",
                "The flashable image for the Firmware Implant: deployed miners survive a host wipe.",
                new UpgradeVersion(4, 2),
                UpgradeVersion.UNKNOWN,
                standing,
                UnlockGate.ETHECOIN,
                142_000_000L,
                io.github.stoicswe.eyeandsickle.solo.Balance.ec("120.96"),
                true,
                0L,
                io.github.stoicswe.eyeandsickle.protocol.game.UpgradeKind.FIRMWARE,
                "firmware-implant",
                schematic,
                blocked);
    }

    private static void render(Path out) throws Exception {
        Path profileDir = out.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        VBox all = new VBox(UiTokens.SPACE_4);
        all.getStyleClass().addAll("es-files", "es-body-pad");
        all.getChildren()
                .addAll(
                        FileManagerView.compare(offer(
                                new UpgradeVersion(3, 1),
                                UpgradeVersion.UNKNOWN,
                                UpgradeOffer.Standing.NEW,
                                io.github.stoicswe.eyeandsickle.solo.Balance.ec("4.8"),
                                true)),
                        FileManagerView.compare(offer(
                                new UpgradeVersion(5, 2),
                                new UpgradeVersion(1, 8),
                                UpgradeOffer.Standing.UPGRADE,
                                io.github.stoicswe.eyeandsickle.solo.Balance.ec("6.4"),
                                true)),
                        FileManagerView.compare(offer(
                                new UpgradeVersion(3, 1),
                                new UpgradeVersion(3, 1),
                                UpgradeOffer.Standing.SAME,
                                io.github.stoicswe.eyeandsickle.solo.Balance.ec("4.8"),
                                true)),
                        // ⚠ The longest verdict of the four, and the one whose whole job is to still make a
                        // case. If it wraps badly or the dimmed cell reads as broken, it is visible here.
                        FileManagerView.compare(offer(
                                new UpgradeVersion(2, 0),
                                new UpgradeVersion(4, 0),
                                UpgradeOffer.Standing.OLDER,
                                io.github.stoicswe.eyeandsickle.solo.Balance.ec("4.0"),
                                true)));
        shoot(themes, all, out.resolve("upgrade-compare.png"), 700, 760);

        // ⚠ Firmware's three states. The longest lines in the whole block live here, and the one
        // that matters most is "no schematic" — a player seeing it has to understand that the image
        // is still worth taking, which is a lot of words in a narrow panel.
        VBox firmware = new VBox(UiTokens.SPACE_4);
        firmware.getStyleClass().addAll("es-files", "es-body-pad");
        firmware.getChildren()
                .addAll(
                        FileManagerView.compare(fw(UpgradeOffer.Standing.NEW, false, "")),
                        FileManagerView.compare(fw(
                                UpgradeOffer.Standing.NEW,
                                true,
                                "Mining is running: 40 cycles self-mining and 3 deployed miner(s).")),
                        FileManagerView.compare(fw(UpgradeOffer.Standing.NEW, true, "")));
        shoot(themes, firmware, out.resolve("upgrade-firmware.png"), 700, 640);
    }

    private static void shoot(ThemeManager themes, Region panel, Path to, int w, int h) throws Exception {
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, w, h);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();
        WritableImage image = scene.snapshot(new WritableImage(w, h));
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }
}
