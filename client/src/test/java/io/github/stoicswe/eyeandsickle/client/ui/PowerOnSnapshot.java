package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/**
 * Renders the firmware splash.
 *
 * <p>{@link DeckSnapshot}'s rationale, for a screen that is nothing but a picture. {@link PowerOnTest}
 * checks the mark's grid — widths, connectivity, the three holes — and a grid that passes every one of
 * those can still render as a beaded lattice, which is exactly what the first cut did: a VBox of
 * Labels stacks rows at the font's line height, leaving vertical gaps wider than the horizontal ones
 * the glyph itself leaves. No assertion sees that. This does.
 *
 * <p>The bar is driven to a fixed fraction before the snapshot, because a splash caught on its first
 * frame shows an empty track and proves nothing about the one element that moves.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.PowerOnSnapshot \
 *     -Dexec.args="/tmp/out"
 * }</pre>
 */
public final class PowerOnSnapshot {

    private PowerOnSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        out.toFile().mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Path dir = out.resolve("poweron-profile");
                dir.toFile().mkdirs();
                ClientProfile profile = new ClientProfile(dir);
                ThemeManager themes = new ThemeManager(profile);
                PowerOn splash = PowerOn.still();
                Scene scene = new Scene(splash, 980, 760);
                themes.adopt(scene);
                themes.select(ThemeId.DECK);
                // Part-filled, so the render shows the bar doing what it does rather than empty.
                // Reflection rather than an exposed setter: the fill width is an internal of a
                // three-second animation, and widening the class's surface for a snapshot would be
                // the tail wagging the dog.
                // The real paint path, at a point where every element is doing something:
                // `u` fully in, `S` halfway. No reflection — renderAt is what the timer calls.
                splash.renderAt(0.62);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                WritableImage image = scene.snapshot(null);
                int w = (int) image.getWidth();
                int h = (int) image.getHeight();
                BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                PixelReader reader = image.getPixelReader();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        buffered.setRGB(x, y, reader.getArgb(x, y));
                    }
                }
                File file = out.resolve("poweron.png").toFile();
                ImageIO.write(buffered, "png", file);
                System.out.println("wrote " + file);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }
}
