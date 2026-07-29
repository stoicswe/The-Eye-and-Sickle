package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.PackageView;
import io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
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
 * Renders the package installer in each of its states.
 *
 * <h2>Why this needs a picture</h2>
 *
 * Three of the panel's states are branches most sessions never take — a payment still pending, a tool
 * already owned, and a payload whose digest does not match its manifest. Nothing but a render says
 * whether the two digests fit on a line, whether the verdict is legible against the panel, or whether
 * the disabled Install reads as refused rather than broken.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.PackageSnapshot \
 *     -Dexec.args="/tmp/pkg"
 * }</pre>
 */
public final class PackageSnapshot {

    private PackageSnapshot() {}

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

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
        LocalGameSession session = new LocalGameSession(game);
        game.credit(50_000L, "TEST", "seed");
        session.purchase("canary-token");
        // Hold the chain off so the download lands while the payment is still pending — see
        // PurchaseFlowTest for why this is a fixture control rather than a mock.
        game.state().chain.networkWorkTarget = 500.0d;
        clock.advance(Duration.ofMinutes(2));
        game.tick();

        String path = game.state().files.getFirst().path();
        shoot(themes, session.packageAt(path).orElseThrow(), PackageView.Mode.INSTALL,
                out.resolve("package-locked.png"));
        shoot(themes, session.packageAt(path).orElseThrow(), PackageView.Mode.INSPECT,
                out.resolve("package-inspect.png"));

        // Tampered — the branch that cannot happen in single player and is the reason the panel
        // prints two digests rather than a tick.
        game.state().files.getFirst().payloadSalt = "substituted";
        shoot(themes, session.packageAt(path).orElseThrow(), PackageView.Mode.INSTALL,
                out.resolve("package-tampered.png"));
        game.state().files.getFirst().payloadSalt = "";

        // The port scanner, against a machine a sweep has found.
        String scanTarget = game.state().topology == null || game.state().topology.hosts.isEmpty()
                ? ""
                : game.state().topology.hosts.stream()
                        .filter(h -> !"SELF".equals(h.kind))
                        .findFirst().map(h -> h.address).orElse("");
        game.state().topology.hosts.stream()
                .filter(h -> h.address.equals(scanTarget))
                .forEach(h -> h.discovered = true);
        if (!scanTarget.isBlank()) {
            shootPanel(themes,
                    io.github.stoicswe.eyeandsickle.client.view.PortScanView.create(
                            session, scanTarget, m -> {}),
                    out.resolve("portscan.png"), 820, 700);
            // And after one has run, so the findings block has something in it.
            session.portScan(scanTarget,
                    io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.VAULT_MEDIUM);
            clock.advance(Duration.ofMinutes(4));
            game.tick();
            shootPanel(themes,
                    io.github.stoicswe.eyeandsickle.client.view.PortScanView.create(
                            session, scanTarget, m -> {}),
                    out.resolve("portscan-done.png"), 820, 700);
        }
        if (!scanTarget.isBlank()) {
            // A second, shallower scan hours later, so the report shows findings of DIFFERENT ages —
            // which is the whole reason the file is persisted rather than thrown away.
            clock.advance(Duration.ofHours(19));
            session.portScan(scanTarget,
                    io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.OS_VERSION);
            clock.advance(Duration.ofMinutes(2));
            game.tick();
            shootPanel(themes,
                    io.github.stoicswe.eyeandsickle.client.view.NodeReportView.create(
                            session, scanTarget),
                    out.resolve("node-report.png"), 700, 520);
            // Named and tagged, so the shot shows what the list is actually for.
            session.nameNode(scanTarget, "the bank");
            session.tagNode(scanTarget, java.util.List.of("rich", "defended", "revisit"));
            shootPanel(themes,
                    io.github.stoicswe.eyeandsickle.client.view.ReconView.create(session, a -> {}),
                    out.resolve("recon.png"), 900, 480);
        }
        shootPanel(themes,
                io.github.stoicswe.eyeandsickle.client.view.DefenseGameView.create(
                        session, "unregistered process  ·  6 cycles", o -> {}),
                out.resolve("defense-prototype.png"), 620, 420);

        // Confirmed and ready to install.
        game.state().chain.networkWorkTarget = 0.001d;
        clock.advance(Duration.ofHours(3));
        game.tick();
        String ready = game.state().files.getFirst().path();
        shoot(themes, session.packageAt(ready).orElseThrow(), PackageView.Mode.INSTALL,
                out.resolve("package-ready.png"));
    }

    private static void shootPanel(ThemeManager themes, Region panel, Path to, int w, int h)
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

    private static void shoot(ThemeManager themes, PackageManifest pkg, PackageView.Mode mode, Path to)
            throws Exception {
        Region panel = PackageView.create(null, pkg, mode, () -> {}, message -> {});
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, 700, 620);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();

        WritableImage image = scene.snapshot(new WritableImage(700, 620));
        BufferedImage png = new BufferedImage(700, 620, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < 620; y++) {
            for (int x = 0; x < 700; x++) {
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
