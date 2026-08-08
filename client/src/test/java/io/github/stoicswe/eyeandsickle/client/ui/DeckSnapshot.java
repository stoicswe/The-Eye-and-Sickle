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
import io.github.stoicswe.eyeandsickle.client.view.SecurityCenterView;
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

    /**
     * A clock the harness can wind forward.
     *
     * <h2>⚠ The alternative was reaching into the rules, and it would have proved less</h2>
     *
     * A sweep's whole design is that its outcome is decided at commission and applied at completion —
     * {@code NetRules.beginSweep} freezes it precisely so quitting cannot re-roll it. Calling
     * {@code settleSweep} by hand would skip the task, the compute hold and the recovery, so the
     * render would be of a state the game cannot actually reach. Winding the clock exercises the real
     * path and costs one class.
     */
    private static final class Advancing extends Clock {

        private java.time.Instant at;

        private Advancing(java.time.Instant at) {
            this.at = at;
        }

        void advance(java.time.Duration by) {
            at = at.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return at;
        }
    }

    /**
     * Gives the harness a network to draw.
     *
     * <h2>⚠ WITHOUT THIS, NO RENDER THIS PROJECT CAN PRODUCE CONTAINS A SINGLE EDGE</h2>
     *
     * {@code docs/client/09-network-map-graph.md} §1.3 records it as the prerequisite for every visual
     * decision in that document, and it is the sharper of the two defects there: a fresh character
     * knows one machine — their own — so the map photographed as one box on an empty field. Routing,
     * lane assignment, merging, arcs, arrowheads, bridge stubs and stacks were all invisible to the
     * only tool this project has for looking at itself, which is how two of three routing lanes came
     * to render as stubs silently for as long as the lane token was wrong.
     *
     * <p>The topology mapper is granted so the ceiling is two hops rather than one: at one hop every
     * machine is a child of the rig and the picture has no second column, so nothing about depth,
     * forward edges or stacking would be on screen. A deep sweep because bridges need tier 2 or better
     * to be found at all ({@code Balance.NET_SWEEP_BRIDGE_MIN_TIER}) and the bridge stub is a piece of
     * the picture in its own right.
     *
     * <p>⚠ Nothing here fakes a discovery. The sweep is commissioned through the session, held for its
     * real duration, and settled by the engine's own tick — so what the map draws is a world the rules
     * produced, and a change that broke discovery would show up here as an empty map rather than as a
     * render that still looked right.
     */
    private static void sweepTheNeighbourhood(GameEngine game, LocalGameSession session, Advancing clock) {
        game.state().schematics.add(io.github.stoicswe.eyeandsickle.engine.net.NetRules.TOPOLOGY_MAPPER);
        for (String owned : List.of("net-sweep-wide", "net-sweep-deep")) {
            io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(owned).ifPresent(offering -> {
                var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
                item.itemType = offering.id();
                item.displayName = offering.name();
                item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
                game.state().items.add(item);
            });
        }
        session.sweep("--deep");
        settleASweep(session, clock);

        // ⚠ `-Ddeck.reposition=N` walks the traversal loop N times: take a foothold on the deepest
        // machine found, `connect` to it, sweep again. That is the ONLY way a map grows past two
        // columns — the hop ceiling is two and reach is never bought (I2) — so without it the harness
        // can never photograph a deep map, and the pressure docs/client/09 §2 describes is fan-out
        // TIMES depth. It is opt-in because a repositioned vantage is a different picture from the one
        // the other fourteen windows are set up for.
        //
        // ⚠ It is also how NM-2 gets an answer. Measured over four generated worlds at N=1: layers run
        // 1–5 machines wide, so a threshold of 4 never fires at that depth. Any calibration of it has
        // to be done out here, several repositions from home, which is the only place the fan-out the
        // design is written against actually exists.
        int hops = Integer.getInteger("deck.reposition", 0);
        for (int step = 0; step < hops; step++) {
            var hopsFromRig = io.github.stoicswe.eyeandsickle.engine.net.NetRules.hopsFrom(
                    game.state(), game.state().topology.playerAddress);
            String deepest = "";
            int furthest = -1;
            for (var host : game.state().topology.hosts) {
                // ⚠ Never the machine already stood on. Without that the loop re-connects to the
                // current vantage and sweeps from the same place, and a sweep's outcome is frozen at
                // world generation — so every step after the first found nothing and the map stopped
                // growing at three columns while the flag said six.
                if (!host.discovered
                        || host.address.equals(game.state().topology.playerAddress)
                        || host.address.equals(game.state().topology.vantageAddress)) {
                    continue;
                }
                int distance = hopsFromRig.getOrDefault(host.address, -1);
                if (distance > furthest) {
                    furthest = distance;
                    deepest = host.address;
                }
            }
            if (deepest.isEmpty()) {
                break;
            }
            // ⚠ The foothold is planted rather than breached, and that is the one shortcut here. It
            // stands in for the puzzle, not for the rule: `connect` still refuses or accepts on the
            // rules' own terms, and the sweep that follows is a real sweep from the new position.
            for (var host : game.state().topology.hosts) {
                if (host.address.equals(deepest)) {
                    host.foothold = true;
                }
            }
            io.github.stoicswe.eyeandsickle.engine.net.NetRules.connect(game.state(), deepest, game.now());
            session.sweep("--deep");
            settleASweep(session, clock);
        }

        // ⚠ `-Ddeck.netdump=1` prints the map as text. The grid IS the rendering, so this is the
        // cheapest and most exact way to look at one — and it is what NM-2 needs: the stack threshold
        // is proposed rather than measured, and measuring it means reading real layer widths off real
        // generated worlds rather than off a hand-built fixture.
        if (System.getProperty("deck.netdump") != null) {
            System.out.println(io.github.stoicswe.eyeandsickle.client.ui.netmap.NetCanvas.frame(session.net(), 0));
        }
    }

    /** Lets a commissioned sweep run to completion, and its compute finish recovering afterwards. */
    private static void settleASweep(LocalGameSession session, Advancing clock) {
        // Past the sweep, and past the compute recovery that follows it, so the cycles the rest of
        // this fixture allocates are actually free. A sweep still running would also render the rig
        // monitor with a second task in it, which is a different picture from the one the other
        // windows are set up for.
        clock.advance(java.time.Duration.ofSeconds(
                io.github.stoicswe.eyeandsickle.engine.Balance.NET_SWEEP_DEEP_SECONDS + 1));
        session.tick();
        clock.advance(java.time.Duration.ofMinutes(10));
        session.tick();
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

        // ⚠ ADVANCEABLE, because a sweep is a TASK and the harness has to be able to let one finish.
        // See sweepTheNeighbourhood: the engine's own clock is the only way to settle work whose whole
        // design is that its outcome was frozen at commission and applied at completion.
        Advancing clock = new Advancing(Clock.systemUTC().instant());
        // ⚠ `TestSaves.bare`, NOT `GameEngine.open` — the rig has to be at the top of the compute
        // ladder or most of this fixture is silently refused. A starting rig is 24 cycles as of
        // 2026-08-06 and this harness was written against 100: measured on the render before it was
        // changed, `allocateSelfMining(30)` and `scan("thorough")` (35) were both refusals, so the
        // deck photographed with an idle grid and a SECURITY CENTER reading "Unaudited — no audit has
        // ever run on this rig". Both are states indistinguishable from those features being broken,
        // which is the exact failure a render harness exists to prevent.
        var game = io.github.stoicswe.eyeandsickle.client.support.TestSaves.bare(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(profileDir.resolve("save.json")),
                "halflight",
                clock);
        LocalGameSession session = new LocalGameSession(game);
        sweepTheNeighbourhood(game, session, clock);
        // A rig doing something. An empty rig renders an empty grid, which would prove nothing about
        // the component the whole design language calls its signature.
        //
        // ⚠ TEN, NOT THIRTY, and the budget is the reason. A 64-cycle rig has to carry self-mining,
        // an armed firewall, a canary, a shell and — the expensive one — a THOROUGH scan at 35, which
        // is what gives the activity panel a long-running task with a real countdown. At thirty this
        // fixture spent its way past the ceiling and the scan was refused, so the render showed a rig
        // that had never been audited. Written against a 100-cycle rig, and nothing re-checked it when
        // the ladder landed.
        session.allocateSelfMining(10);
        // ⚠ ARMING NOW REQUIRES OWNING (2026-08-06), so the harness has to grant what it arms. It
        // did not, and both calls below silently became refusals — the FIREWALL panel photographed
        // with nothing armed, which is the state indistinguishable from the switches not working.
        //
        // ⚠ Deliberately grants only SOME of the ladder: firewall T2 and a canary. What that buys is
        // a render showing all three row states at once — armed, owned-but-off, and gated — where a
        // fully-stocked rig would prove nothing about the gate and an empty one nothing about the
        // switches. Firewall T1 is already there; GameEngine.newCharacter issues it.
        for (String owned : List.of("firewall-t2", "canary-token", "tarpit")) {
            io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(owned).ifPresent(offering -> {
                var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
                item.itemType = offering.id();
                item.displayName = offering.name();
                item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
                game.state().items.add(item);
            });
        }
        session.arm("firewall", 2);
        session.arm("canary", 1);
        // A thorough scan so the activity panel has a long-running task with a real countdown.
        session.scan("thorough");
        // Enough heat to light the thermometer past two band boundaries, so the render shows the
        // banded ramp rather than an empty stem.
        game.state().personalHeat = 62;
        // ⚠ `-Ddeck.notes=1` seeds the notebook. An empty NOTES window renders an empty tree and an
        // instruction to make a note, which proves nothing about the tree, the editor or — the one
        // thing only a render can settle — whether the highlight overlay actually lines up with the
        // text underneath it.
        if (System.getProperty("deck.notes") != null) {
            var notes = io.github.stoicswe.eyeandsickle.engine.rules.Notes.class;
            var lore = io.github.stoicswe.eyeandsickle.engine.rules.Notes.create(
                    game.state(), "", "lore", true, game.now());
            var addresses = io.github.stoicswe.eyeandsickle.engine.rules.Notes.create(
                    game.state(), "", "addresses", false, game.now());
            String folderId = lore.map(n -> n.noteId).orElse("");
            io.github.stoicswe.eyeandsickle.engine.rules.Notes.create(
                    game.state(), folderId, "the eye", false, game.now());
            io.github.stoicswe.eyeandsickle.engine.rules.Notes.create(
                    game.state(), folderId, "kyrell", false, game.now());
            // ⚠ The markdown goes in a ROOT note, because the window opens on the first note in tree
            // order — seeding it inside a collapsed folder photographs an empty editor, which proves
            // nothing about the highlight overlay that is the only reason to render this at all.
            addresses.ifPresent(n -> io.github.stoicswe.eyeandsickle.engine.rules.Notes.write(
                    game.state(),
                    n.noteId,
                    """
                    # Kyrell

                    Recovered off **10.14.9.2** — a `systemd` unit nobody wrote.

                    ## What is known

                    - Signs off as *unsigned relay*
                    - Uses the same `blake2b` digest twice
                    - Paid in EC, never in favours

                    > The handle is not the person. The handle is what the person
                    > wanted logged.

                    ---

                    See [the sweep notes](notes://addresses) for the rest.
                    """,
                    game.now()));
        }
        // ⚠ `-Ddeck.noticed=1` makes the black market contact this character, so COMS and the TOR
        // Marknet tab are renderable at all. The notice is sent on a TICK when standing and heat
        // cross, and a synchronous render never ticks — so without this the inbox photographs empty,
        // which is the state indistinguishable from the feature being absent.
        if (System.getProperty("deck.noticed") != null) {
            game.state().factionReputationSickle =
                    io.github.stoicswe.eyeandsickle.engine.Balance.BLACK_MARKET_MIN_REPUTATION + 5;
            io.github.stoicswe.eyeandsickle.engine.rules.BlackMarket.contactIfDue(game.state(), game.now());
            // ⚠ `-Ddeck.torInstalled=1` goes one step further and puts the MODULE on the rig, which
            // is what the Marknet tab keys on. The two flags are separate because the interesting
            // states are different screens: the notice sitting unclaimed in COMS, and the board it
            // eventually opens.
            if (System.getProperty("deck.torInstalled") != null) {
                io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(
                                io.github.stoicswe.eyeandsickle.engine.Catalogue.TOR_MODULE)
                        .ifPresent(offering -> {
                            var mod = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
                            mod.itemType = offering.id();
                            mod.displayName = offering.name();
                            mod.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
                            game.state().items.add(mod);
                        });
            }
        }

        Shell.CommandRegistry commands = BuiltinCommands.registry();
        Shell shell = new Shell(session, commands);

        Map<WindowSpec, Function<WindowSpec, Region>> factories = new EnumMap<>(WindowSpec.class);
        for (WindowSpec spec : WindowSpec.values()) {
            factories.put(spec, s -> switch (s) {
                case RIG_MONITOR -> (Region) RigMonitorView.create(session);
                case TERMINAL -> (Region) TerminalView.create(shell);
                case LOG -> (Region) LogView.create(session);
                case SETTINGS -> (Region) Views.settings(profile, themes, () -> {}, null, null, session);
                case LEDGER -> (Region) Views.ledger(session);
                // Real views rather than the recon stand-in, because both are visual checks that no
                // text assertion can make: the map's legend is a column whose alignment depends on a
                // fixed-width font resolving, and the calculator is a grid of sixty-four cells.
                case NETMAP -> NetMapView.create(session);
                case CALC -> CalcView.create();
                // ⚠ The real Security Center, for the same reason as the map: its verdict, its rail
                // and its cards are all visual claims no text assertion can check.
                case SECURITY -> (Region) SecurityCenterView.create(session, shell);
                // ⚠ -Dsec.state=clear|check|quarantine drives the mark directly. The three
                // states depend on scan history, defences and elapsed time, so a plain render
                // only ever photographs whichever one this fixture happens to be in — and a
                // stepped animation shows nothing at all in a synchronous frame.
                // ⚠ The real inbox. COMMS fell to the default below and therefore photographed the
                // RECON stub — under a title bar reading COMPORT, which is exactly how the mistake
                // survived: the frame said the right thing and the content was a different window.
                // ⚠ `-Ddeck.direct=1` renders COMS with the DIRECT tab present. It is built from a
                // signed-OUT chat client on purpose: what the render checks is that the tab APPEARS
                // and that its not-connected state reads correctly. Rendering a signed-in one would
                // need somebody's real Bluesky account, which no test may reach for.
                case COMMS -> (Region) io.github.stoicswe.eyeandsickle.client.view.CommsView.create(
                        session,
                        System.getProperty("deck.direct") == null
                                ? null
                                : io.github.stoicswe.eyeandsickle.client.view.DirectView.create(
                                        new io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat(null),
                                        "you.bsky.social",
                                        60, io.github.stoicswe.eyeandsickle.client.view.DirectView.Alerts.NONE));
                // ⚠ The real market, because the TOR Marknet tab appears and disappears with an
                // ITEM and the whole point of rendering it is to see whether it did.
                case MARKET -> (Region)
                        io.github.stoicswe.eyeandsickle.client.view.MarketView.create(session, 60);
                // ⚠ The real notebook: its highlight is a TextFlow laid over a TextArea, and
                // whether those two actually line up is a question only a render can answer.
                case NOTES -> (Region) io.github.stoicswe.eyeandsickle.client.view.NotesView.create(session);
                // ⚠ THIS DEFAULT PHOTOGRAPHS THE WRONG WINDOW AND REPORTS SUCCESS. Anything not
                // named above renders the RECON stub inside its own frame, which looks like a real
                // capture of a real window — the same failure shape as a snapshot of a feature in
                // the one state indistinguishable from it being absent. Add a case before rendering
                // a window for the first time; do not trust a frame's title bar.
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
            // ⚠ A THEME CAN IMPLY GEOMETRY NOW (ThemeId.roundsCorners, §9.4), and selecting one does
            // not apply it — rounding is a clip plus a style class, neither of which a stylesheet
            // swap touches. The running client re-applies it from a listener on the theme property
            // (EyeAndSickleClient); this is that listener's stand-in, and without it every liquid
            // frame here photographs SQUARE. That is the trap this harness exists to catch: a render
            // that captures the one state indistinguishable from the feature being absent, and
            // reports it as a pass.
            deck.applyRoundedSetting();
            deck.desk().closeAll();
            // ⚠ `-Ddeck.windows=TERMINAL,NETWORK,CALC,STORAGE` opens a different set. The default four
            // are a good cross-section of PANELS, and they are therefore a poor test of anything
            // whose subject is an inset WELL — the terminal's scrollback, the map canvas, the file
            // list, the calculator keypad. Those are the surfaces a theme change most easily leaves
            // behind, and until this flag existed rendering them meant editing this line.
            String chosen = System.getProperty("deck.windows");
            deck.openStartingWindows(
                    chosen == null
                            ? List.of(WindowSpec.RIG_MONITOR, WindowSpec.SETTINGS, WindowSpec.LOG, WindowSpec.SECURITY)
                            : java.util.Arrays.stream(chosen.split(","))
                                    .map(String::trim)
                                    .map(name -> WindowSpec.valueOf(name.toUpperCase(java.util.Locale.ROOT)))
                                    .toList());

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
            // ⚠ `-Ddeck.cascade` leaves them CASCADED — overlapping — instead. The tiled layout is
            // the one case where no window sits over another, so it is exactly the wrong layout for
            // checking anything about what a window shows THROUGH itself. The frost's stacking was
            // wrong for a whole build because every render was tiled.
            if (System.getProperty("deck.cascade") == null) {
                deck.desk().tileAll();
            }

            // ⚠ The size readout only appears AFTER a size change — a window opening at its saved
            // size is not a resize, and the first report is deliberately swallowed. So a single-pass
            // render photographs the one state indistinguishable from the feature being absent, and
            // proving it works needs a second layout at a different size.
            // ⚠ It also never fades here: the step-down runs on Pulse, and no Pulse frame fires in a
            // synchronous render — which is exactly what makes it photographable.
            if (System.getProperty("deck.resize") != null) {
                double to = Double.parseDouble(System.getProperty("deck.resize"));
                deck.root().resize(to, height - 40);
                deck.root().layout();
                deck.desk().tileAll();
                deck.root().layout();
            }
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

            // ⚠ `-Ddeck.settingsPage=Discord` selects a Settings category by its sidebar label. The
            // panel opens on the FIRST category and there is no other way in, so every other page is
            // unrenderable without this — which is how a page could ship having never been looked at
            // while the harness reported the Settings window as covered.
            //
            // ⚠ It clicks the real row rather than reaching into settingsBody, so what is
            // photographed is the state a player reaches by clicking, not one only a test can build.
            String settingsPage = System.getProperty("deck.settingsPage");
            if (settingsPage != null) {
                boolean found = false;
                for (var node : deck.root().lookupAll(".es-settings-row")) {
                    if (node instanceof javafx.scene.control.Label row
                            && row.getText().equalsIgnoreCase(settingsPage)) {
                        javafx.event.Event.fireEvent(
                                row,
                                new javafx.scene.input.MouseEvent(
                                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                                        0,
                                        0,
                                        0,
                                        0,
                                        javafx.scene.input.MouseButton.PRIMARY,
                                        1,
                                        false,
                                        false,
                                        false,
                                        false,
                                        true,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        null));
                        found = true;
                        break;
                    }
                }
                // ⚠ Says so rather than rendering the default page. A silent miss photographs
                // whichever category happened to be first and reports it as the one asked for.
                System.out.println(found
                        ? "settings page: " + settingsPage
                        : "settings page NOT FOUND: " + settingsPage);
                scene.getRoot().applyCss();
                deck.root().layout();
            }

            // ⚠ `-Ddeck.securitySection=FIREWALL` selects a Security Center section by its rail
            // label. Exactly the settingsPage problem one window along: the panel opens on HOME and
            // there is no other way in, so AUDIT, FIREWALL and SCHEDULE were all unrenderable and
            // the harness reported the SECURITY window as covered while having only ever
            // photographed a quarter of it.
            // ⚠ `-Ddeck.commsTab=ALO` selects a COMS sub-tab by its label. Same gap
            // `deck.securitySection` closed one window along: the pane opens on its first tab and
            // there is no other way in, so the messenger was unrenderable and a render of COMS
            // reported the window as covered while only ever photographing INBOX.
            // ⚠ Matched as a PREFIX, not for equality, and that is what survived the DIRECT →
            // ALO MESSENGER rename. An exact match against a label is a selector that breaks
            // silently the day anybody edits the word — and it breaks by printing NOT FOUND and
            // then photographing the wrong tab, which is the failure this whole flag exists to
            // stop. A prefix also spares the caller quoting a system property with a space in it.
            String commsTab = System.getProperty("deck.commsTab");
            if (commsTab != null) {
                boolean found = false;
                for (var node : deck.root().lookupAll(".tab-pane")) {
                    if (!(node instanceof javafx.scene.control.TabPane pane)) {
                        continue;
                    }
                    for (var tab : pane.getTabs()) {
                        String label = tab.getText() == null ? "" : tab.getText();
                        if (label.toLowerCase(java.util.Locale.ROOT)
                                .startsWith(commsTab.toLowerCase(java.util.Locale.ROOT))) {
                            pane.getSelectionModel().select(tab);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                System.out.println(found ? "comms tab: " + commsTab : "comms tab NOT FOUND: " + commsTab);
                scene.getRoot().applyCss();
                deck.root().layout();
            }

            String securitySection = System.getProperty("deck.securitySection");
            if (securitySection != null) {
                boolean found = false;
                for (var node : deck.root().lookupAll(".es-sec-nav")) {
                    if (node instanceof javafx.scene.control.Label chip
                            && chip.getText().equalsIgnoreCase(securitySection)) {
                        javafx.event.Event.fireEvent(
                                chip,
                                new javafx.scene.input.MouseEvent(
                                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                                        0,
                                        0,
                                        0,
                                        0,
                                        javafx.scene.input.MouseButton.PRIMARY,
                                        1,
                                        false,
                                        false,
                                        false,
                                        false,
                                        true,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        null));
                        found = true;
                        break;
                    }
                }
                System.out.println(found
                        ? "security section: " + securitySection
                        : "security section NOT FOUND: " + securitySection);
                scene.getRoot().applyCss();
                deck.root().layout();
            }

            // ⚠ Prints what each window actually MEASURES, because "measure node bounds before
            // hunting a gap in the layout" has ended more than one search here in a single line —
            // the rig monitor's cutaway gap took four rounds of staring at a render and one
            // `well top=127.0, cage top=127.0` to settle. A size read off a screenshot is a guess.
            for (var node : deck.root().lookupAll(".es-window")) {
                if (node instanceof Region frame) {
                    System.out.printf(
                            "window %.1f x %.1f  (min %.1f x %.1f, max %.1f x %.1f)%n",
                            frame.getWidth(),
                            frame.getHeight(),
                            frame.minWidth(-1),
                            frame.minHeight(-1),
                            frame.maxWidth(-1),
                            frame.maxHeight(-1));
                }
            }

            scene.getRoot().applyCss();
            deck.root().layout();
            // ⚠ The blurred backdrop is captured through Platform.runLater in the running client,
            // and NO QUEUED RUNNABLE EXECUTES during a synchronous Scene.snapshot. Without this the
            // glass palettes photograph with nothing behind them — the one state indistinguishable
            // from the feature being absent. Same stand-in as tileAll() above.
            // ⚠ `-Ddeck.operator=1` slides the operator profile out of the strip. It opens on a
            // click, and a synchronous render never delivers one — so without this flag the panel
            // photographs as absent, which is the state indistinguishable from it being broken.
            if (System.getProperty("deck.operator") != null) {
                deck.openOperatorPanel();
            }
            deck.desk().frostNow();
            deck.root().layout();
            // ⚠ `-Ddeck.frostBench=N` times N full re-frosts and prints the cost. The frost is the
            // one thing in this client whose viability is a number rather than a look: refreshing it
            // on a clock is only defensible if a whole cycle fits inside a frame, and that is
            // measured here rather than assumed.
            if (System.getProperty("deck.frostBench") != null) {
                int rounds = Integer.parseInt(System.getProperty("deck.frostBench"));
                deck.desk().frostNow(); // warm: first call pays for image allocation and pipeline setup
                long start = System.nanoTime();
                for (int i = 0; i < rounds; i++) {
                    deck.desk().frostNow();
                }
                double perRefresh = (System.nanoTime() - start) / 1_000_000.0d / rounds;
                System.out.printf(
                        "frost %s: %.2f ms per refresh, %d windows -> %.1f fps ceiling%n",
                        id.id(), perRefresh, deck.desk().windowCount(), 1000.0d / perRefresh);
            }

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
