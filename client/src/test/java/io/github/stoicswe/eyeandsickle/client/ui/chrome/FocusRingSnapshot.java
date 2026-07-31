package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javax.imageio.ImageIO;

/**
 * Renders the focused-window outline off, on, and in each colour.
 *
 * <h2>Why this needs a picture</h2>
 *
 * The ring paints the frame's {@code edge} region, which is clipped to the same polygon as the rest
 * of the frame. A border applied to the frame itself would be <b>cut away by that clip and appear to
 * do nothing</b> — which is exactly how the first rounded-corners attempt failed, silently, with the
 * CSS applying correctly and nothing on screen changing. A stylesheet that parses is not a stylesheet
 * that paints.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.chrome.FocusRingSnapshot \
 *     -Dexec.args="/tmp/ring"
 * }</pre>
 */
public final class FocusRingSnapshot {

    private FocusRingSnapshot() {}

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
        ThemeManager themes = new ThemeManager(new ClientProfile(profileDir));

        // Off — the shipped default: focus is the strip cue alone.
        WindowFrame.setFocusRing(false, "theme");
        shoot(themes, out.resolve("ring-off.png"));

        for (FocusRing ring : FocusRing.selectable()) {
            WindowFrame.setFocusRing(true, ring.id());
            shoot(themes, out.resolve("ring-" + ring.id() + ".png"));
        }
    }

    /** Two frames side by side: one focused, one not. The contrast IS the subject. */
    private static void shoot(ThemeManager themes, Path to) throws Exception {
        WindowFrame focused = frame("Focused", "THIS ONE");
        focused.focusedFlag().set(true);
        focused.applyFocusRing();
        WindowFrame idle = frame("Not focused", "THE OTHER");
        idle.applyFocusRing();

        Pane desk = new Pane(focused, idle);
        desk.getStyleClass().add("es-desk");
        focused.setManaged(false);
        idle.setManaged(false);
        focused.resizeRelocate(20, 20, 300, 150);
        idle.resizeRelocate(340, 20, 300, 150);

        StackPane host = new StackPane(desk);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, 660, 190);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();

        WritableImage image = scene.snapshot(new WritableImage(660, 190));
        BufferedImage png = new BufferedImage(660, 190, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < 190; y++) {
            for (int x = 0; x < 660; x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }

    private static WindowFrame frame(String title, String id) {
        WindowFrame frame = new WindowFrame(title, id);
        frame.setContent(new Label("  panel content"));
        return frame;
    }
}
