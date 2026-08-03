package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.CalcView;
import io.github.stoicswe.eyeandsickle.client.view.LogView;
import io.github.stoicswe.eyeandsickle.client.view.MoreViews;
import io.github.stoicswe.eyeandsickle.client.view.NetMapView;
import io.github.stoicswe.eyeandsickle.client.view.RigMonitorView;
import io.github.stoicswe.eyeandsickle.client.view.TerminalView;
import io.github.stoicswe.eyeandsickle.client.view.Views;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javax.imageio.ImageIO;

/**
 * Renders the deck to a PNG, without opening a window.
 *
 * <h2>Why this exists</h2>
 *
 * A stylesheet can compile, load and apply cleanly while producing a screen nobody would ship. The
 * checks in {@code UiContractTest} catch rule violations in the source; they cannot catch a panel
 * that lays out three pixels wide, a font that silently fell back, or a palette overlay loaded in
 * the wrong order. This renders the real scene graph with the real stylesheets and writes the
 * result, so those are visible.
 *
 * <p>{@link Scene#snapshot} rather than a screen capture: it needs no display server, captures only
 * the application, and cannot accidentally photograph whatever else is on the machine. It is also
 * the only approach that works in CI.
 *
 * <p>Test scope on purpose — this is a verification tool, not a product feature, and a
 * snapshot-to-file hook wired into the shipped client would be a file-write path that exists for
 * nobody's benefit but a developer's.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.DeckSnapshot \
 *     -Dexec.args="/tmp/out 1600 1000"
 * }</pre>
 */
public final class DeckSnapshot {

    private DeckSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 1600;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 1000;
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
        Path profileDir = outputDir.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);

        // ⚠ Set on the PROFILE, before ThemeManager exists — not on Pulse directly. ThemeManager
        // owns the reduced-motion decision and pushes it to Pulse in its constructor, so a Pulse
        // call made first is silently overwritten by the OS preference. That cost a debugging round:
        // every window laid out correctly and the snapshot was empty, because Motion.reveal had
        // clipped each panel to zero width and no pulse ever ran to open it.
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        // The screen artefacts ship off, so a snapshot with the defaults would prove only that they
        // are off. Turned on here because the render IS the check for them — none of the three has a
        // failure mode a text assertion could catch.
        profile.appearance().crtScanlines = true;
        profile.appearance().crtAberration = true;
        profile.appearance().crtGlitch = true;
        profile.appearance().crtCurvature = 100;
        // ⚠ Opt-in so the default frames keep showing the character wallpaper. `-Ddeck.wallpaper=ring`
        // or `ring-glitch` renders the emblem instead — the only way to see it, since a wallpaper is
        // the change a green build most readily reports as done while drawing nothing.
        if (System.getProperty("deck.chromatic") != null) {
            profile.appearance().wallpaperChromatic = true;
        }
        String wallpaper = System.getProperty("deck.wallpaper");
        if (wallpaper != null) {
            profile.appearance().wallpaper = wallpaper;
        }
        ThemeManager themes = new ThemeManager(profile);

        var game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(profileDir.resolve("save.json")),
                "halflight",
                Clock.systemUTC());
        LocalGameSession session = new LocalGameSession(game);
        // A rig doing something. An empty rig renders an empty grid, which would prove nothing about
        // the component the whole design language calls its signature.
        session.allocateSelfMining(30);
        session.arm("firewall", 2);
        session.arm("canary", 1);
        // A thorough scan so the activity panel has a long-running task with a real countdown.
        session.scan("thorough");
        // Enough heat to light the thermometer past two band boundaries, so the render shows the
        // banded ramp rather than an empty stem.
        game.state().personalHeat = 62;

        Shell.CommandRegistry commands = BuiltinCommands.registry();
        Shell shell = new Shell(session, commands);

        Map<WindowSpec, Function<WindowSpec, Region>> factories = new EnumMap<>(WindowSpec.class);
        for (WindowSpec spec : WindowSpec.values()) {
            factories.put(spec, s -> switch (s) {
                case RIG_MONITOR -> (Region) RigMonitorView.create(session);
                case TERMINAL -> (Region) TerminalView.create(shell);
                case LOG -> (Region) LogView.create(session);
                case MINING -> (Region) Views.mining(session);
                case SETTINGS -> (Region) Views.settings(profile, themes, () -> {}, null, null, session);
                case DEFENSE -> (Region) Views.defense(session);
                case LEDGER -> (Region) Views.ledger(session);
                // Real views rather than the recon stand-in, because both are visual checks that no
                // text assertion can make: the map's legend is a column whose alignment depends on a
                // fixed-width font resolving, and the calculator is a grid of sixty-four cells.
                case NETMAP -> NetMapView.create(session);
                case CALC -> CalcView.create();
                default -> (Region) MoreViews.recon(session);
            });
        }

        DeckShell deck = new DeckShell(session, shell, profile, factories, new DeckShell.Actions() {
            @Override
            public void openPalette() {}

            @Override
            public void runCommand(String line) {}

            @Override
            public void backToMenu() {}

            @Override
            public void quit() {}

            @Override
            public void save() {}
        });

        Scene scene = new Scene(deck.root(), width, height);
        themes.adopt(scene);

        for (ThemeId id : ThemeId.selectable()) {
            themes.select(id);
            deck.desk().closeAll();
            deck.openStartingWindows(
                    List.of(WindowSpec.RIG_MONITOR, WindowSpec.SETTINGS, WindowSpec.LOG, WindowSpec.DEFENSE));

            // Two passes. The first resolves CSS and sizes the panels; the desk then places windows
            // against a desk whose width is finally known, and the second pass lays those out. One
            // pass produces a snapshot with every window at its cascade origin and zero size.
            // Drive the shell's one-second data tick by hand so the sparklines have history: in a
            // synchronous render no Pulse frame ever fires, and an empty history draws blank.
            try {
                var tick = DeckShell.class.getDeclaredMethod("tickClock");
                tick.setAccessible(true);
                for (int i = 0; i < 30; i++) {
                    session.allocateSelfMining(20 + (i * 7) % 60);
                    tick.invoke(deck);
                }
            } catch (Exception ignored) {
                // Best effort — the snapshot is still useful without history.
            }

            scene.getRoot().applyCss();
            deck.root().layout();
            // openStartingWindows defers tiling to runLater, which never fires in a synchronous
            // render. Tiling directly here is the same call it would have made.
            deck.desk().tileAll();
            // ⚠ Opt-in: the chain-sync report only exists after a real absence, so a deck built from
            // a fresh save has nothing to show. `-Ddeck.sync=1` feeds it a literal one through
            // DeckShell's seam, which is the only way to render the banner without doctoring a save's
            // timestamps and hoping the rules read them the way this meant.
            if (System.getProperty("deck.sync") != null) {
                deck.showChainSync(new io.github.stoicswe.eyeandsickle.protocol.game.ChainSync(
                        java.time.Instant.parse("2026-07-29T12:00:00Z"),
                        java.time.Instant.parse("2026-08-02T12:00:00Z"),
                        4 * 24 * 3600,
                        4 * 3600,
                        4412,
                        4823,
                        411,
                        102,
                        2,
                        3,
                        new java.math.BigInteger("324000000000000000000"),
                        1,
                        344.18,
                        352.90,
                        1,
                        false));
            }

            // ⚠ The glitch cycle starts at rest and no Pulse tick runs in a synchronous render, so
            // without this every ring-glitch frame is a clean ring — the harness reporting the effect
            // as working by photographing the one state that looks identical to it being broken.
            String phase = System.getProperty("deck.glitchPhase");
            if (phase != null) {
                for (var node : deck.root().lookupAll(".es-ringfield")) {
                    if (node instanceof io.github.stoicswe.eyeandsickle.client.ui.widgets.RingField field) {
                        field.seekForRender(Double.parseDouble(phase));
                    }
                }
            }

            // ⚠ Reproduces the switch, not the start-up state. A deck built straight into `drift`
            // renders it correctly; the defect only appears when this layer has been OFF for the
            // whole of its life so far and is then asked to draw — which is what selecting a ring
            // wallpaper and then going back does.
            if (System.getProperty("deck.wallpaperSwitch") != null) {
                profile.appearance().wallpaper = System.getProperty("deck.wallpaperSwitch");
                deck.applyScreenSettings();
                scene.getRoot().applyCss();
                deck.root().layout();
            }

            scene.getRoot().applyCss();
            deck.root().layout();

            // Scene.snapshot takes only a target image — SnapshotParameters is Node's overload.
            WritableImage image = scene.snapshot(null);
            write(image, outputDir.resolve("deck-" + id.id() + ".png").toFile());
            System.out.println(
                    "wrote deck-" + id.id() + ".png  " + (int) image.getWidth() + "x" + (int) image.getHeight());

            // And the pause menu over the same deck, for the default palette only — it is the same
            // panel language, so rendering it five times would prove nothing new.
            if (id == ThemeId.DECK) {
                deck.togglePause();
                scene.getRoot().applyCss();
                deck.root().layout();
                write(scene.snapshot(null), outputDir.resolve("deck-paused.png").toFile());
                deck.togglePause();
                System.out.println("wrote deck-paused.png");

                // ⚠ A bare desk, and it is the ONLY frame in which the wallpaper is visible at all:
                // every other snapshot tiles four windows edge to edge, so the backdrop is covered
                // by definition. Without this the whole Substrate layer could be drawing nothing and
                // every image here would look correct — which is precisely the failure mode a
                // snapshot harness exists to catch.
                // The signal glitch, forced. It fires on a random Pulse tick and no Pulse frame runs
                // in a synchronous render, so left alone this frame would show nothing and prove
                // nothing. Forcing one spawn is the same call the ticker makes.
                try {
                    var crtField = DeckShell.class.getDeclaredField("crt");
                    crtField.setAccessible(true);
                    Object overlay = crtField.get(deck);
                    var spawn = overlay.getClass().getDeclaredMethod("spawnBandForTest");
                    spawn.setAccessible(true);
                    spawn.invoke(overlay);
                    // And drive the tube animation far enough for the refresh bar to be on screen —
                    // it starts above the top edge, so frame zero shows nothing of it.
                    var scan = overlay.getClass().getDeclaredMethod("advanceScanForTest");
                    scan.setAccessible(true);
                    for (int i = 0; i < 34; i++) {
                        scan.invoke(overlay);
                    }
                    scene.getRoot().applyCss();
                    deck.root().layout();
                    write(
                            scene.snapshot(null),
                            outputDir.resolve("deck-glitch.png").toFile());
                    System.out.println("wrote deck-glitch.png");
                } catch (Exception e) {
                    System.out.println("glitch frame skipped: " + e);
                }

                deck.desk().closeAll();
                scene.getRoot().applyCss();
                deck.root().layout();
                write(
                        scene.snapshot(null),
                        outputDir.resolve("deck-wallpaper.png").toFile());
                System.out.println("wrote deck-wallpaper.png");
            }
        }
    }

    /**
     * Writes a {@link WritableImage} as PNG without {@code javafx.swing}.
     *
     * <p>{@code SwingFXUtils} lives in the {@code javafx-swing} artifact, which this module does not
     * depend on and should not gain a dependency on for a test utility. Reading the pixels directly
     * is six lines and adds nothing to the build.
     */
    private static void write(WritableImage image, File file) throws Exception {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(out, "png", file);
    }
}
