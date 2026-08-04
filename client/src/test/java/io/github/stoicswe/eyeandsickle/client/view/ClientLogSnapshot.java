package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.log.ClientLog;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Renders the CLIENT LOGS tab, because a panel that compiles can still lay out wrong.
 *
 * <p>A <b>main class run by hand</b>, which is this repository's convention for anything that starts
 * the JavaFX toolkit — {@code NodeMenuTest} is the one JUnit test that does, and it broke CI's Linux
 * job for want of a display.
 *
 * <pre>
 *   mvn -q -pl client exec:java -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.ClientLogSnapshot \
 *       -Dexec.classpathScope=test
 * </pre>
 *
 * <p>⚠ The fixture logs at <b>every</b> level, including one line with a stack trace. The five
 * severities are the whole visual contract of this panel — if the colour ramp is wrong, or a recycled
 * {@code ListCell} keeps a previous row's class, that is a defect nothing but a render will show.
 */
public final class ClientLogSnapshot {

    private ClientLogSnapshot() {}

    private static final Logger LOG = Logger.getLogger("io.github.stoicswe.eyeandsickle.client.demo");

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 1180;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 900;
        outputDir.toFile().mkdirs();

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(outputDir, width, height);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path outputDir, double width, double height) throws Exception {
        Path profileDir = outputDir.resolve("clientlog-profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        // ⚠ On the PROFILE, before ThemeManager exists — see DeckSnapshot. Otherwise the OS
        // preference overwrites it and every panel stays clipped to zero width by Motion.reveal.
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        ClientLog.install();
        ClientLog.shared().clear();
        // One line per band, so the colour ramp is visible in a single frame.
        LOG.log(Level.INFO, "EAS uOS Client starting — Java 25 on Mac OS X aarch64");
        LOG.log(Level.INFO, "opening character database at /Users/player/…/characters");
        LOG.log(Level.FINE, "settings written to /Users/player/…/settings.json");
        LOG.log(Level.FINE, "opening window rig-monitor");
        LOG.log(Level.FINER, "$ ls -la /Users/halflight/Documents");
        LOG.log(Level.FINE, "intent allocateSelfMining -> ok (0)");
        LOG.log(Level.INFO, "refused [mining] status 1: Your rig does not have those cycles free.");
        LOG.log(Level.WARNING, "the file dialog would not open");
        LOG.log(Level.SEVERE, "slot 2 could not be read", new IllegalStateException("save format 9999"));

        Region panel = ClientLogView.create();
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-panel-body");
        Scene scene = new Scene(host, width, height);
        themes.adopt(scene);
        // ⚠ CSS before layout. A scene whose CSS has never been applied reports a preferred width of
        // zero for anything styled, and every measurement taken from it is a measurement of nothing.
        scene.getRoot().applyCss();
        host.layout();

        shoot(scene, outputDir.resolve("client-logs.png"), width, height);

        // ⚠ A SECOND frame with TRACE switched on, because the filter is the feature. The first shot
        // proves the ramp renders; only this one proves the toggle does anything — and "the filter
        // is wired to nothing" is a defect that looks identical to "there were no trace lines".
        for (javafx.scene.Node node : panel.lookupAll(".es-switch")) {
            if (node instanceof io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch toggle
                    && "Show TRACE lines".equals(toggle.getAccessibleText())) {
                toggle.setSelected(true);
            }
        }
        host.layout();
        shoot(scene, outputDir.resolve("client-logs-trace.png"), width, height);
    }

    private static void shoot(Scene scene, Path to, double width, double height) throws Exception {
        WritableImage image = scene.snapshot(new WritableImage((int) width, (int) height));
        BufferedImage out =
                new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                out.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(out, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }
}
