package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.RigStatus;
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
 * The heat meter at every band, in the dark palette and the light one.
 *
 * <h2>Why both palettes</h2>
 *
 * The unlit cell used to be {@code -es-rule-hi}, which is near-black on uOS Classic — the same value
 * as that palette's two lowest band fills. Lit and unlit were indistinguishable, so the meter read as
 * a row of black dashes and the colour it exists to show was invisible on the one palette where the
 * contrast mattered most. Rendering one theme would have said the widget was fine.
 */
public final class ThermoSnapshot {

    private ThermoSnapshot() {}

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
        Path dir = out.resolve("p");
        dir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(dir);
        for (ThemeId id : new ThemeId[] {ThemeId.DECK, ThemeId.CLASSIC}) {
            profile.appearance().themeId = id.id();
            ThemeManager themes = new ThemeManager(profile);
            themes.select(id);

            VBox rows = new VBox(10);
            rows.getStyleClass().addAll("es-panel", "es-body-pad");
            // Every band, plus the cold rig — "no heat at all" has to stay a different state from
            // "the lowest band", which is I4's whole point.
            for (int heat : new int[] {0, 8, 25, 45, 70, 95}) {
                ThermoMeter meter = new ThermoMeter();
                meter.show(heat, RigStatus.HeatBand.of(heat));
                rows.getChildren().add(meter);
            }

            StackPane host = new StackPane(rows);
            host.getStyleClass().add("es-scene-ground");
            Scene scene = new Scene(host, 300, 260);
            themes.adopt(scene);
            scene.getRoot().applyCss();
            host.layout();
            scene.getRoot().applyCss();
            host.layout();
            WritableImage img = scene.snapshot(new WritableImage(300, 260));
            BufferedImage png = new BufferedImage(300, 260, BufferedImage.TYPE_INT_ARGB);
            PixelReader px = img.getPixelReader();
            for (int y = 0; y < 260; y++) {
                for (int x = 0; x < 300; x++) {
                    png.setRGB(x, y, px.getArgb(x, y));
                }
            }
            File f = new File(out.resolve("thermo-" + id.id() + ".png").toString());
            ImageIO.write(png, "png", f);
            System.out.println("wrote " + f);
        }
    }
}
