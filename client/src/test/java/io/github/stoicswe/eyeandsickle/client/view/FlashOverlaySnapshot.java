package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import io.github.stoicswe.eyeandsickle.solo.Catalogue;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.rules.Repac;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.StoredFileState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javax.imageio.ImageIO;

/**
 * Renders the firmware flashing overlay part-way through a flash.
 *
 * <h2>Why this needs a picture more than most</h2>
 *
 * The warning mark is <b>drawn</b> — a Polygon with two Regions composing its counter-shape — because
 * {@code U+26A0} is in neither bundled font and {@code GlyphCoverageTest} has already rejected it once.
 * A drawn mark has no fallback to look wrong, it simply looks wrong: the bar and dot have to sit
 * inside the triangle at the right size, and nothing but a render says whether they do.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.FlashOverlaySnapshot \
 *     -Dexec.args="/tmp/flash"
 * }</pre>
 */
public final class FlashOverlaySnapshot {

    private FlashOverlaySnapshot() {}

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");

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

    private static void render(Path out) throws Exception {
        Path profileDir = out.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(profileDir.resolve("save.json")), "halflight", clock);
        game.state().schematics.add(Catalogue.FIRMWARE_IMPLANT_SCHEMATIC);
        game.state().rig.selfMiningCycles = 0L;
        StoredFileState image = Repac.arrive(game.state(), "/Users/halflight/Downloads",
                "mining-firmware.pkg", "10.0.0.9", 142_000_000L, "firmware-implant",
                new UpgradeVersion(4, 2), T0);
        Repac.repack(game.state(), image, T0);
        LocalGameSession session = new LocalGameSession(game);
        Repac.install(game.state(), image.path(), T0);

        // ⚠ Part-way through, not at the start. A bar rendered at 0% proves nothing about whether the
        // fill is sized from the track's live width — which is the bug a fixed width would produce.
        clock.advance(Duration.ofSeconds(38));

        Region panel = PackageView.create(
                session, Repac.manifest(game.state(), image.path()).orElseThrow(),
                PackageView.Mode.INSTALL, () -> {}, message -> {});
        shoot(themes, panel, out.resolve("firmware-flashing.png"), 700, 720);
    }

    private static void shoot(ThemeManager themes, Region panel, Path to, int w, int h)
            throws Exception {
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
