package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
 * Renders the rig monitor's OVERVIEW tab, in the dark palette and the light one.
 *
 * <h2>Why this needs a picture</h2>
 *
 * The two instruments beside the cell field are geometry, and geometry is what a compile says nothing
 * about. This render exists because of a specific defect: the core cutaway sat with a visible gap
 * above it, because an {@code HBox} fills its resizable children to the row height by default and the
 * cutaway then centred itself inside a box far taller than it needed. {@code TOP_LEFT} alignment did
 * not fix it — alignment says where a child sits, {@code fillHeight} says whether it was handed a
 * height to sit in.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.RigOverviewSnapshot \
 *     -Dexec.args="/tmp/rig"
 * }</pre>
 */
public final class RigOverviewSnapshot {

    private RigOverviewSnapshot() {}

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
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")),
                "kyyrell",
                Clock.fixed(Instant.parse("2026-07-30T09:00:00Z"), ZoneOffset.UTC));
        game.state().rig.totalCycles = 100;
        game.allocateSelfMining(75);
        LocalGameSession session = new LocalGameSession(game);

        for (ThemeId id : new ThemeId[] {ThemeId.DECK, ThemeId.CLASSIC}) {
            profile.appearance().themeId = id.id();
            ThemeManager themes = new ThemeManager(profile);
            themes.select(id);
            Region panel = RigMonitorView.create(session);
            StackPane host = new StackPane(panel);
            host.getStyleClass().add("es-scene-ground");
            Scene scene = new Scene(host, 1000, 820);
            themes.adopt(scene);
            scene.getRoot().applyCss();
            host.layout();
            scene.getRoot().applyCss();
            host.layout();
            // Where the two halves actually begin, so "the tops align" is measured rather than eyeballed.
            // ⚠ A CELL, not the well. The two halves are bounded panels with insets, so what has to
            // line up is the first row of content — comparing the boxes hid a 2px drift.
            var well = panel.lookup(".es-cell");
            var cage = panel.lookup(".es-cage");
            if (well != null && cage != null) {
                System.out.printf(
                        "%s: well top=%.1f  cage top=%.1f  delta=%.1f%n",
                        id.id(),
                        well.localToScene(well.getBoundsInLocal()).getMinY(),
                        cage.localToScene(cage.getBoundsInLocal()).getMinY(),
                        cage.localToScene(cage.getBoundsInLocal()).getMinY()
                                - well.localToScene(well.getBoundsInLocal()).getMinY());
            }
            WritableImage img = scene.snapshot(new WritableImage(1000, 820));
            BufferedImage png = new BufferedImage(1000, 820, BufferedImage.TYPE_INT_ARGB);
            PixelReader px = img.getPixelReader();
            for (int y = 0; y < 820; y++) {
                for (int x = 0; x < 1000; x++) {
                    png.setRGB(x, y, px.getArgb(x, y));
                }
            }
            File f = new File(out.resolve("rig-" + id.id() + ".png").toString());
            ImageIO.write(png, "png", f);
            System.out.println("wrote " + f);
        }
    }
}
