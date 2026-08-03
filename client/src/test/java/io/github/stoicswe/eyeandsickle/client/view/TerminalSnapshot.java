package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.LocalCatalogue;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
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
 * Renders the local terminal beside a machine shell.
 *
 * <h2>Why both, in one place</h2>
 *
 * The point of the change was that the two should look and behave the same, so the only render worth
 * having is the one that puts them next to each other. It also prints the generated command
 * catalogue, which is the half a picture cannot check: the menu is derived from the registry, so what
 * matters is that it lists every registered verb and no invented flag.
 */
public final class TerminalSnapshot {

    private TerminalSnapshot() {}

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
        ThemeManager themes = new ThemeManager(profile);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                "kyyrell",
                Clock.fixed(Instant.parse("2026-07-30T09:00:00Z"), ZoneOffset.UTC));
        LocalGameSession session = new LocalGameSession(game);
        // ⚠ The FULL registry, the way EyeAndSickleClient builds it. Using BuiltinCommands alone
        // would have "verified" a menu missing breach, net and the manual — the catalogue derives
        // from whatever the shell was given, so the fixture has to give it the same thing.
        var registry = BuiltinCommands.registry();
        io.github.stoicswe.eyeandsickle.client.shell.BreachCommands.register(registry);
        io.github.stoicswe.eyeandsickle.client.shell.NetCommands.register(registry);
        Shell shell = new Shell(session, registry);

        // What the right-click menu will offer, group by group. Derived from the registry, so this
        // is also the check that "all locally available commands" is literally true.
        var catalogue = LocalCatalogue.byGroup(shell.registry());
        int total = catalogue.values().stream().mapToInt(java.util.List::size).sum();
        System.out.println("menu groups: " + catalogue.keySet());
        System.out.println("commands in menu: " + total + "   registered: "
                + shell.registry().commands().size());
        catalogue.forEach((group, commands) -> System.out.println(
                "  " + group + ": " + commands.stream().map(c -> c.name()).toList()));

        shell.run("ps");
        shell.run("nonsense");
        Region panel = TerminalView.create(shell);
        shoot(themes, panel, out.resolve("terminal.png"), 820, 560);
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
        WritableImage img = scene.snapshot(new WritableImage(w, h));
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader px = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                png.setRGB(x, y, px.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }
}
