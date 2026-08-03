package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.PackageView;
import io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest;
import io.github.stoicswe.eyeandsickle.solo.Balance;
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
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(profileDir.resolve("save.json")),
                "halflight",
                clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");
        session.purchase("canary-token");
        // Hold the chain off so the download lands while the payment is still pending — see
        // PurchaseFlowTest for why this is a fixture control rather than a mock.
        game.state().chain.networkWorkTarget = 500.0d;
        clock.advance(Duration.ofMinutes(2));
        game.tick();

        String path = game.state().files.getFirst().path();
        shoot(
                themes,
                session.packageAt(path).orElseThrow(),
                PackageView.Mode.INSTALL,
                out.resolve("package-locked.png"));
        shoot(
                themes,
                session.packageAt(path).orElseThrow(),
                PackageView.Mode.INSPECT,
                out.resolve("package-inspect.png"));

        // Tampered — the branch that cannot happen in single player and is the reason the panel
        // prints two digests rather than a tick.
        game.state().files.getFirst().payloadSalt = "substituted";
        shoot(
                themes,
                session.packageAt(path).orElseThrow(),
                PackageView.Mode.INSTALL,
                out.resolve("package-tampered.png"));
        game.state().files.getFirst().payloadSalt = "";

        // The port scanner, against a machine a sweep has found.
        String scanTarget =
                game.state().topology == null || game.state().topology.hosts.isEmpty()
                        ? ""
                        : game.state().topology.hosts.stream()
                                .filter(h -> !"SELF".equals(h.kind))
                                .findFirst()
                                .map(h -> h.address)
                                .orElse("");
        game.state().topology.hosts.stream()
                .filter(h -> h.address.equals(scanTarget))
                .forEach(h -> h.discovered = true);
        if (!scanTarget.isBlank()) {
            shootPanel(
                    themes,
                    io.github.stoicswe.eyeandsickle.client.view.PortScanView.create(session, scanTarget, m -> {}),
                    out.resolve("portscan.png"),
                    820,
                    700);
            // And after one has run, so the findings block has something in it.
            session.portScan(scanTarget, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.VAULT_MEDIUM);
            clock.advance(Duration.ofMinutes(4));
            game.tick();
            shootPanel(
                    themes,
                    io.github.stoicswe.eyeandsickle.client.view.PortScanView.create(session, scanTarget, m -> {}),
                    out.resolve("portscan-done.png"),
                    820,
                    700);
        }
        if (!scanTarget.isBlank()) {
            // A second, shallower scan hours later, so the report shows findings of DIFFERENT ages —
            // which is the whole reason the file is persisted rather than thrown away.
            clock.advance(Duration.ofHours(19));
            session.portScan(scanTarget, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.OS_VERSION);
            clock.advance(Duration.ofMinutes(2));
            game.tick();
            shootPanel(
                    themes,
                    io.github.stoicswe.eyeandsickle.client.view.NodeReportView.create(session, scanTarget),
                    out.resolve("node-report.png"),
                    700,
                    520);
            // Named and tagged, so the shot shows what the list is actually for.
            // A world label, so the shot shows the identifier doing its job rather than the
            // fallback. A machine nothing has identified genuinely has only an address.
            // ⚠ The label lives on knownNodes, which a sweep populates — marking a topology host
            // discovered does not create one. Without this the identifier correctly hides (no world
            // name to show) and the shot verifies the fallback instead of the feature.
            var known = game.state().knownNodes.stream()
                    .filter(n -> scanTarget.equals(n.address))
                    .findFirst()
                    .orElseGet(() -> {
                        var fresh = new io.github.stoicswe.eyeandsickle.solo.state.NodeState();
                        fresh.address = scanTarget;
                        game.state().knownNodes.add(fresh);
                        return fresh;
                    });
            known.label = "home-relay";
            session.nameNode(scanTarget, "the bank");
            session.tagNode(scanTarget, java.util.List.of("rich", "defended", "revisit"));
            shootPanel(
                    themes,
                    io.github.stoicswe.eyeandsickle.client.view.ReconView.create(session, a -> {}),
                    out.resolve("recon.png"),
                    900,
                    300);
        }
        shootPanel(
                themes,
                io.github.stoicswe.eyeandsickle.client.view.DefenseGameView.create(
                        session, "unregistered process  ·  6 cycles", o -> {}),
                out.resolve("defense-prototype.png"),
                620,
                420);

        // ── The LOG window, both tabs ──────────────────────────────────────────────────────────
        //
        // ⚠ A FRESH panel and a fresh Scene per tab, not one panel snapshotted twice. Scene.snapshot
        // renders what the scene last laid out, and toggling setVisible between two synchronous
        // snapshots does not re-run that — so the second shot comes out identical to the first and
        // the render "verifies" a tab it never drew. Measured here once already.
        //
        // The requirement this checks is the user's: everything the LOG window used to be is now
        // OVERVIEW and must be unchanged, with EVENTS added beside it rather than in place of it.
        shootPanel(
                themes,
                io.github.stoicswe.eyeandsickle.client.view.LogView.create(session),
                out.resolve("log-overview.png"),
                820,
                620);
        Region eventsTab = io.github.stoicswe.eyeandsickle.client.view.LogView.create(session);
        shootPanel(themes, eventsTab, out.resolve("log-events.png"), 820, 620, panel -> {
            // The chips are the tab picker; the second one is EVENTS. Fired rather than reached for
            // by a setter, because the click path is the thing a player has and therefore the thing
            // worth rendering through.
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

        // Confirmed and ready to install.
        game.state().chain.networkWorkTarget = 0.001d;
        clock.advance(Duration.ofHours(3));
        game.tick();
        String ready = game.state().files.getFirst().path();
        shoot(
                themes,
                session.packageAt(ready).orElseThrow(),
                PackageView.Mode.INSTALL,
                out.resolve("package-ready.png"));
    }

    private static void shootPanel(ThemeManager themes, Region panel, Path to, int w, int h) throws Exception {
        shootPanel(themes, panel, to, w, h, p -> {});
    }

    /**
     * The same, with a chance to drive the panel once it has been laid out.
     *
     * <p>⚠ The hook runs AFTER the first {@code applyCss}/{@code layout} pair and before the second.
     * {@code lookupAll} finds nothing on a graph that has never had CSS applied — a selector-based
     * hook placed before that pass silently matches zero nodes and the interaction never happens,
     * with the render coming out looking merely untouched.
     */
    private static void shootPanel(
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

    private static void shoot(ThemeManager themes, PackageManifest pkg, PackageView.Mode mode, Path to)
            throws Exception {
        Region panel = PackageView.create(null, pkg, mode, () -> {}, message -> {});
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, 760, 620);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();

        WritableImage image = scene.snapshot(new WritableImage(760, 620));
        BufferedImage png = new BufferedImage(760, 620, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < 620; y++) {
            for (int x = 0; x < 760; x++) {
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
