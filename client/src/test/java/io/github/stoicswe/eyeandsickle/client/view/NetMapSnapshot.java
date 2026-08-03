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
 * Renders the network map in the darkest palette and the light one, side by side.
 *
 * <h2>Why this needs a picture, and why it needs BOTH</h2>
 *
 * uOS Classic is the only light palette, so it is the one where a colour chosen by eye on a dark
 * screen goes wrong: "a dim grey" reads as quiet on {@code #0C1012} and vanishes on {@code #E4E4E4}.
 * That is exactly what happened — the map drew its CONTACT and LOCKED states in the greeble token, at
 * 1.77:1 on the deck and 2.06:1 on Classic, and on Classic the locked nodes disappeared into the
 * panel. {@code ContrastTest} now holds the floor arithmetically; this is how you see the result.
 *
 * <p>⚠ Rendering one theme proves nothing about the other. The bug was invisible on four palettes.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.NetMapSnapshot \
 *     -Dexec.args="/tmp/net"
 * }</pre>
 */
public final class NetMapSnapshot {

    private NetMapSnapshot() {}

    public static void main(String[] a) throws Exception {
        Path out = Path.of(a[0]);
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
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("s.json")),
                "kyyrell",
                Clock.fixed(Instant.parse("2026-07-30T09:00:00Z"), ZoneOffset.UTC));
        // A world with a few states visible: discovered, foothold, locked.
        var hosts = game.state().topology.hosts;
        game.state().knownNodes.clear();
        for (int i = 0; i < hosts.size(); i++) {
            var h = hosts.get(i);
            h.discovered = true;
            if (i == 1 || i == 2) {
                h.foothold = true;
            }
            var n = new io.github.stoicswe.eyeandsickle.solo.state.NodeState();
            n.address = h.address;
            game.state().knownNodes.add(n);
        }
        LocalGameSession session = new LocalGameSession(game);

        for (ThemeId id : new ThemeId[] {ThemeId.DECK, ThemeId.CLASSIC}) {
            profile.appearance().themeId = id.id();
            ThemeManager themes = new ThemeManager(profile);
            themes.select(id);
            Region map = NetMapView.create(session);
            StackPane host = new StackPane(map);
            host.getStyleClass().add("es-scene-ground");
            Scene scene = new Scene(host, 1000, 720);
            themes.adopt(scene);
            scene.getRoot().applyCss();
            host.layout();
            scene.getRoot().applyCss();
            host.layout();
            WritableImage img = scene.snapshot(new WritableImage(1000, 720));
            BufferedImage png = new BufferedImage(1000, 720, BufferedImage.TYPE_INT_ARGB);
            PixelReader px = img.getPixelReader();
            for (int y = 0; y < 720; y++) for (int x = 0; x < 1000; x++) png.setRGB(x, y, px.getArgb(x, y));
            File f = new File(out.resolve("net-" + id.id() + ".png").toString());
            ImageIO.write(png, "png", f);
            System.out.println("wrote " + f);
        }
    }
}
