package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;

/**
 * Renders the switch in both states, square and rounded.
 *
 * <h2>Why this needs a picture</h2>
 *
 * The whole control is two rectangles and an alignment change. Nothing about "is the knob on the
 * correct side", "is the off state legible against the panel" or "does the rounded opt-in actually
 * reach it" survives a compile — and the {@code .es-rounded} rules in particular are gated on a class
 * that has to be present on an ancestor, which is the exact shape that silently applied to nothing
 * the first time rounded corners were attempted.
 */
public final class SwitchSnapshot {

    private SwitchSnapshot() {}

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
        shoot(themes, out.resolve("switch-square.png"), false);
        shoot(themes, out.resolve("switch-rounded.png"), true);
    }

    private static void shoot(ThemeManager themes, Path to, boolean rounded) throws Exception {
        VBox rows = new VBox(10);
        rows.getStyleClass().addAll("es-panel", "es-body-pad");

        Switch off = new Switch("Rounded window corners");
        Switch on = new Switch("Outline the focused window");
        on.setSelected(true);
        Switch longLabel = new Switch("Bandwidth limits open windows  [PROPOSAL]");
        longLabel.setSelected(true);
        rows.getChildren().addAll(off, on, longLabel);

        StackPane host = new StackPane(rows);
        host.getStyleClass().add("es-scene-ground");
        // ⚠ The rounded opt-in is a class on an ANCESTOR, exactly as DeckShell applies it to the
        // deck root. Putting it on the switch itself would test a selector the app never uses.
        if (rounded) {
            host.getStyleClass().add("es-rounded");
        }
        Scene scene = new Scene(host, 420, 130);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();

        WritableImage image = scene.snapshot(new WritableImage(420, 130));
        BufferedImage png = new BufferedImage(420, 130, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < 130; y++) {
            for (int x = 0; x < 420; x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }
}
