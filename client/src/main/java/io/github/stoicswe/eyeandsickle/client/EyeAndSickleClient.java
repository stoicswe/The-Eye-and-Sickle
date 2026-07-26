package io.github.stoicswe.eyeandsickle.client;

import io.github.stoicswe.eyeandsickle.client.profile.CharacterSlots;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.ClientCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.teaching.ManCommands;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.CommandPalette;
import io.github.stoicswe.eyeandsickle.client.view.MainMenuView;
import io.github.stoicswe.eyeandsickle.client.view.ManView;
import io.github.stoicswe.eyeandsickle.client.view.MoreViews;
import io.github.stoicswe.eyeandsickle.client.view.RigMonitorView;
import io.github.stoicswe.eyeandsickle.client.view.TerminalView;
import io.github.stoicswe.eyeandsickle.client.view.Views;
import io.github.stoicswe.eyeandsickle.client.window.DockedShell;
import io.github.stoicswe.eyeandsickle.client.window.GlobalShortcuts;
import io.github.stoicswe.eyeandsickle.client.window.WindowRegistry;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.time.Clock;
import java.util.Optional;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The operator's client.
 *
 * <h2>It starts offline, and that is the default rather than a fallback</h2>
 *
 * The game opens a {@link LocalGameSession} over a {@link SoloGame}: no network, no account, no
 * database, no second process. {@code docs/design/00-vision-and-pillars.md} makes single player the
 * default mode, and the client honours that by being playable the moment it launches — the only I/O
 * it performs is reading and writing two JSON files in the profile directory.
 *
 * <h2>Multi-window is the fantasy, and the docked layout is its equal</h2>
 *
 * Each tool is its own {@link Stage}, so the screen ends up looking like an operator's desk. That is
 * a native capability of the toolkit rather than something fought for, and it is a large part of why
 * JavaFX was chosen over a game engine. But window management under time pressure is a real barrier,
 * so the single-window docked layout is a first-class alternative rather than a degraded mode
 * ({@code docs/client/07-accessibility.md}).
 *
 * <h2>What this class must never become</h2>
 *
 * A view and input layer. It renders session-owned state and sends intent; it decides nothing a
 * cheating client would want to forge (Invariant I14). In solo that distinction has no adversary, but
 * the code path is the same one online play uses — which is exactly why solo must not get its own.
 *
 * <h2>This class has no {@code main}, deliberately</h2>
 *
 * Start the client through {@link Launcher}. A {@code main} here would be a run arrow in every IDE
 * pointing at the one launch that cannot work: a main class extending {@link Application} makes the
 * JVM look for JavaFX on the module path before {@code main} runs, and a classpath launch then dies
 * with "JavaFX runtime components are missing" — an error that names the wrong problem entirely.
 * {@code Launcher}'s class comment has the full explanation.
 */
public class EyeAndSickleClient extends Application {

    private ClientProfile profile;
    private GameSession session;
    private ThemeManager themes;
    private WindowRegistry registry;
    private Shell shell;
    private TermDatabase terms;
    private CharacterSlots slots;
    private Timeline heartbeat;
    private Timeline autosave;
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        profile = ClientProfile.discover();
        themes = new ThemeManager(profile);
        registry = new WindowRegistry(profile);
        slots = new CharacterSlots(profile);
        terms = TermDatabase.load();

        themes.followSystemPreferences();

        primaryStage.setTitle("The Eye and Sickle");
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(560);
        primaryStage.setOnCloseRequest(e -> shutdown());

        showMainMenu();
        primaryStage.show();
    }

    /**
     * The menu, which is where the game starts.
     *
     * <p>Also where it returns to. Nothing about a session survives going back here — the previous
     * {@link GameSession} is closed and persisted first, so "back to menu" cannot leave a half-live
     * game ticking behind a screen the player thinks is idle.
     */
    private void showMainMenu() {
        closeSession();

        MainMenuView.Actions actions = new MainMenuView.Actions() {
            @Override
            public void playSolo(int slot, String handleIfNew) {
                startSolo(slot, handleIfNew);
            }

            @Override
            public void connectOnline(String serverAddress) {
                EyeAndSickleClient.this.connectOnline(serverAddress);
            }

            @Override
            public void openSettings() {
                showMenuSettings();
            }

            @Override
            public void quit() {
                shutdown();
                javafx.application.Platform.exit();
            }
        };

        Scene scene = new Scene(MainMenuView.create(profile, themes, slots, actions), 980, 760);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
    }

    /** Settings reached from the menu, before a game exists. */
    private void showMenuSettings() {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Settings");
        dialog.getDialogPane().setContent(Views.settings(profile, themes, registry));
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        themes.adopt(dialog.getDialogPane().getScene());
        dialog.showAndWait();
        profile.save();
    }

    /**
     * Reports why online play is not available, rather than hanging on a connection that cannot
     * succeed.
     *
     * <p>The session shape exists and is tested; the transport does not (<b>CL-8</b>). Constructing
     * the real {@link io.github.stoicswe.eyeandsickle.client.session.RemoteGameSession} here means
     * the message the player sees is the same {@code EX_UNAVAILABLE} the rest of the client would
     * produce, rather than a special case written for this screen.
     */
    private void connectOnline(String serverAddress) {
        String address = serverAddress == null ? "" : serverAddress.trim();
        if (address.isBlank()) {
            alert(javafx.scene.control.Alert.AlertType.WARNING, "Enter a home server address first.");
            return;
        }
        if (!profile.settings().knownServers.contains(address)) {
            profile.settings().knownServers.addFirst(address);
            profile.save();
        }
        try {
            var remote = new io.github.stoicswe.eyeandsickle.client.session.RemoteGameSession(
                    java.net.URI.create(address), profile.settings().soloHandle);
            var outcome = remote.allocateSelfMining(0);
            remote.close();
            alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    outcome.message()
                            + "\n\nThe address is remembered. Solo play needs none of this and works now.");
        } catch (IllegalArgumentException badUri) {
            alert(javafx.scene.control.Alert.AlertType.WARNING, "That is not a valid address: " + address);
        }
    }

    private void alert(javafx.scene.control.Alert.AlertType type, String message) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(type, message);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /** Opens a solo character and switches the window to the game. */
    private void startSolo(int slot, String handleIfNew) {
        SaveStore store = new SaveStore(slots.saveFile(slot));
        String handle = handleIfNew != null && !handleIfNew.isBlank()
                ? handleIfNew.trim()
                : (profile.settings().soloHandle.isBlank() ? "operator" : profile.settings().soloHandle);

        session = new LocalGameSession(SoloGame.open(store, handle, Clock.systemUTC()));

        Shell.CommandRegistry commands = BuiltinCommands.registry();
        shell = new Shell(session, commands);
        ClientCommands.register(commands, registry, themes, profile, () -> shell.history(), this::showMainMenu);
        ManCommands.register(commands, terms);

        registerWindows();
        registry.onThemeChange(() -> themes.applyAll());

        profile.settings().lastSoloSlot = slot;
        profile.settings().soloHandle = session.handle();
        profile.save();

        if (registry.isDocked()) {
            startDocked(stage);
        } else {
            startMultiWindow(stage);
        }
        startHeartbeat();
    }

    /** The multi-window desk. Still fully built; one setting away. */
    private void startMultiWindow(Stage primaryStage) {
        primaryStage.setTitle(WindowSpec.RIG_MONITOR.windowTitle());
        primaryStage.setMinWidth(WindowSpec.RIG_MONITOR.minWidth());
        primaryStage.setMinHeight(WindowSpec.RIG_MONITOR.minHeight());
        Scene scene = new Scene(
                RigMonitorView.create(session, terms, profile),
                WindowSpec.RIG_MONITOR.defaultWidth(),
                WindowSpec.RIG_MONITOR.defaultHeight());
        primaryStage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        registry.installAllAccelerators(scene);
        GlobalShortcuts.install(scene, globalHandlers());
        openStartingWindows();
    }

    /**
     * The application-wide shortcuts, from {@code docs/client/00} §6.3.
     *
     * <p>Every one of these has a GUI equivalent as well — a menu item, a Settings control or a
     * button. Pillar <b>C1</b> is that the interface is the toolset; a keystroke that is the ONLY way
     * to reach something is a hidden feature, not a shortcut.
     */
    private GlobalShortcuts.Handlers globalHandlers() {
        return new GlobalShortcuts.Handlers() {
            @Override
            public void openPalette() {
                if (shell != null && stage != null) {
                    CommandPalette.show(stage, shell, () -> {});
                }
            }

            @Override
            public void cycleTheme() {
                var order = io.github.stoicswe.eyeandsickle.client.theme.ThemeId.selectable();
                int next = (order.indexOf(themes.current()) + 1) % order.size();
                themes.select(order.get(next));
                profile.save();
            }

            @Override
            public void cycleTeaching() {
                String level = switch (profile.settings().teachingLevel) {
                    case "explain" -> "terms";
                    case "terms" -> "off";
                    default -> "explain";
                };
                profile.settings().teachingLevel = level;
                profile.save();
            }

            @Override
            public void toggleLayout() {
                registry.setDocked(!registry.isDocked());
                profile.save();
                // Rebuilding the whole shell live would mean tearing down every open Scene mid-
                // keystroke; restarting into the other layout is the honest, simple move, and the
                // menu is where a restart is free.
                showMainMenu();
            }

            @Override
            public void cycleWindows() {
                var open = registry.openWindows();
                if (open.isEmpty()) {
                    return;
                }
                registry.open(open.get(0));
            }

            @Override
            public void abort() {
                if (shell == null) {
                    return;
                }
                // Always confirms: `aborted` is a persisted outcome with real consequences, so a
                // mis-key must not be able to spend one (docs/design/05 §4).
                javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.CONFIRMATION,
                        "Abort the current operation? This is recorded as an outcome.",
                        javafx.scene.control.ButtonType.CANCEL,
                        javafx.scene.control.ButtonType.OK);
                confirm.setHeaderText("Abort");
                confirm.showAndWait()
                        .filter(b -> b == javafx.scene.control.ButtonType.OK)
                        .ifPresent(b -> shell.run("abort"));
            }
        };
    }

    /** Closes and persists any live session. Called before the menu and on exit. */
    private void closeSession() {
        if (heartbeat != null) {
            heartbeat.stop();
            heartbeat = null;
        }
        if (autosave != null) {
            autosave.stop();
            autosave = null;
        }
        registry.closeAll();
        if (session != null) {
            registry.rememberAll();
            profile.save();
            session.close();
            session = null;
        }
    }

    /**
     * The single-window layout.
     *
     * <p>A mode, not a fallback: {@code docs/client/07-accessibility.md} §2.3 requires that no
     * functionality or information is lost in it. The rig strip is chrome rather than a pane, which
     * is a stronger guarantee for pillar C2 than always-on-top ever was — there is no z-order for the
     * compute readout to lose.
     */
    private void startDocked(Stage stage) {
        java.util.Map<WindowSpec, java.util.function.Function<WindowSpec, javafx.scene.layout.Region>>
                factories = new java.util.EnumMap<>(WindowSpec.class);
        for (WindowSpec spec : WindowSpec.values()) {
            factories.put(spec, s -> (javafx.scene.layout.Region) contentFor(s));
        }
        DockedShell docked = new DockedShell(session, factories, () -> globalHandlers().openPalette());

        stage.setTitle("The Eye and Sickle");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        Scene scene = new Scene(docked.root(), 1280, 800);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        // Shortcut+N focuses a TAB here, never opens a second OS window. A shortcut that quietly
        // broke the single-window model would defeat the reason the player chose this mode.
        registry.installAllAccelerators(scene, docked::show);
        GlobalShortcuts.install(scene, globalHandlers());
    }

    /**
     * Opens what should be on screen at launch.
     *
     * <p>First run gets the set {@code docs/client/05} §2.18 specifies. Afterwards the player's own
     * arrangement wins, because a desk you rebuilt once should not need rebuilding every session.
     */
    private void openStartingWindows() {
        if (profile.settings().openWindows.isEmpty()) {
            for (WindowSpec spec : WindowSpec.values()) {
                if (spec != WindowSpec.RIG_MONITOR && spec.openOnFirstRun()) {
                    registry.open(spec);
                }
            }
            return;
        }
        profile.settings().openWindows.keySet().stream()
                .map(WindowSpec::byId)
                .flatMap(Optional::stream)
                .filter(spec -> spec != WindowSpec.RIG_MONITOR)
                .forEach(registry::open);
    }

    /**
     * Tells the registry how to build every window.
     *
     * <p>Registered up front rather than lazily, so a missing view is a startup failure rather than a
     * click that silently does nothing. The switcher lists every window in the catalogue and every one
     * of them must open.
     */
    /**
     * One place that knows how to build each window's content.
     *
     * <p>Shared by both layouts on purpose. If the docked shell built its own views, "no
     * functionality is lost" would be a promise maintained by hand, and the two would drift the first
     * time somebody improved one of them.
     */
    private javafx.scene.Node contentFor(WindowSpec spec) {
        return switch (spec) {
            case RIG_MONITOR -> RigMonitorView.create(session, terms, profile);
            case TERMINAL -> TerminalView.create(shell);
            case AUDIT -> Views.audit(session, shell);
            case MINING -> Views.mining(session);
            case STORAGE -> Views.storage(session);
            case LEDGER -> Views.ledger(session);
            case DEFENSE -> Views.defense(session);
            case IDENTITY -> Views.identity(session);
            case SWITCHER -> Views.switcher(registry);
            case SETTINGS -> Views.settings(profile, themes, registry);
            case MAN -> ManView.create(terms);
            case MARKET -> MoreViews.market(session);
            case MAP -> MoreViews.map(session);
            case RECON -> MoreViews.recon(session);
            case BOTNET -> MoreViews.botnet(session);
            case COMMS -> MoreViews.comms(session);
        };
    }

    private void registerWindows() {
        for (WindowSpec spec : WindowSpec.values()) {
            registry.register(spec, s -> (Parent) contentFor(s));
        }
    }

    /**
     * Advances the game and autosaves.
     *
     * <p>One second is fast enough that self-mining income looks continuous and slow enough to cost
     * nothing. Autosave is deliberately much rarer: writing every second would enter the atomic-write
     * window sixty times a minute for no benefit, and the engine's catch-up on load makes a lost
     * minute recoverable anyway.
     */
    private void startHeartbeat() {
        heartbeat = new Timeline(new KeyFrame(Duration.seconds(1), e -> session.tick()));
        heartbeat.setCycleCount(Animation.INDEFINITE);
        heartbeat.play();

        autosave = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            registry.rememberAll();
            profile.save();
            session.persist();
        }));
        autosave.setCycleCount(Animation.INDEFINITE);
        autosave.play();
    }

    private void shutdown() {
        closeSession();
        profile.save();
    }

    @Override
    public void stop() {
        shutdown();
    }
}
