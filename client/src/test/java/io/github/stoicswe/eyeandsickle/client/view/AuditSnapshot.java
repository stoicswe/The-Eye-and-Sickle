package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
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
 * Renders the AUDIT window's two tabs.
 *
 * <h2>Why this needs a picture</h2>
 *
 * The SCANNER tab's whole content is a progress bar and a list that grows — neither of which a
 * compile or a unit test says anything about. The bar in particular is bound to its track's live
 * width, and the identical construction on the firmware overlay rendered <b>empty</b> the first time
 * because {@code getWidth()} is 0 before the first layout pass. The only way to know is to look.
 *
 * <p>⚠ A fresh Scene per tab. {@code Scene.snapshot} renders what the scene last laid out, so
 * toggling {@code setVisible} between two synchronous snapshots yields two identical images and
 * "verifies" a tab it never drew.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.AuditSnapshot \
 *     -Dexec.args="/tmp/audit"
 * }</pre>
 */
public final class AuditSnapshot {

    private AuditSnapshot() {}

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");

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
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(profileDir.resolve("save.json")),
                "kyyrell",
                clock);
        LocalGameSession session = new LocalGameSession(game);
        Shell shell = new Shell(session, io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands.registry());

        // Two completed audits so the history has both shapes in it — a hit and a clean run.
        session.scan("quick");
        clock.advance(Duration.ofMinutes(2));
        game.tick();
        session.scan("full");
        clock.advance(Duration.ofMinutes(5));
        game.tick();

        // ⚠ And one IN FLIGHT, part-way through: a bar at 0% proves nothing about whether the fill
        // is sized from the track's live width, which is the bug this render exists to catch.
        session.scan("thorough");
        clock.advance(Duration.ofMinutes(2));

        shoot(themes, AuditView.create(session, shell), out.resolve("audit-scanner.png"), 900, 700);

        Region statusTab = AuditView.create(session, shell);
        shoot(themes, statusTab, out.resolve("audit-status.png"), 900, 700, panel -> {
            // The second chip is STATUS. Fired rather than reached for by a setter: the click path
            // is what a player has, so it is the path worth rendering through.
            var chips = new java.util.ArrayList<>(panel.lookupAll(".es-breach-chip"));
            chips.get(1)
                    .fireEvent(new javafx.scene.input.MouseEvent(
                            javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                            4,
                            4,
                            4,
                            4,
                            javafx.scene.input.MouseButton.PRIMARY,
                            1,
                            false,
                            false,
                            false,
                            false,
                            true,
                            false,
                            false,
                            true,
                            false,
                            false,
                            null));
        });
    }

    private static void shoot(ThemeManager themes, Region panel, Path to, int w, int h) throws Exception {
        shoot(themes, panel, to, w, h, p -> {});
    }

    private static void shoot(
            ThemeManager themes, Region panel, Path to, int w, int h, java.util.function.Consumer<Region> drive)
            throws Exception {
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, w, h);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        drive.accept(panel);
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

    private static final class Winding extends Clock {
        private Instant instant;

        Winding(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
