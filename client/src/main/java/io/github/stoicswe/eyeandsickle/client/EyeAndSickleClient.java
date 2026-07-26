package io.github.stoicswe.eyeandsickle.client;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.ClientCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.teaching.ManCommands;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.ManView;
import io.github.stoicswe.eyeandsickle.client.view.MoreViews;
import io.github.stoicswe.eyeandsickle.client.view.RigMonitorView;
import io.github.stoicswe.eyeandsickle.client.view.TerminalView;
import io.github.stoicswe.eyeandsickle.client.view.Views;
import io.github.stoicswe.eyeandsickle.client.window.DockedShell;
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
    private Timeline heartbeat;

    @Override
    public void start(Stage primaryStage) {
        profile = ClientProfile.discover();
        themes = new ThemeManager(profile);
        registry = new WindowRegistry(profile);

        SaveStore store = new SaveStore(profile.saveFile());
        String handle = profile.settings().soloHandle.isBlank() ? "operator" : profile.settings().soloHandle;
        session = new LocalGameSession(SoloGame.open(store, handle, Clock.systemUTC()));

        terms = TermDatabase.load();
        Shell.CommandRegistry commands = BuiltinCommands.registry();
        shell = new Shell(session, commands);
        // Registered after the Shell exists so `history` can be handed a supplier rather than
        // reaching back into its own executor.
        ClientCommands.register(commands, registry, themes, profile, () -> shell.history());
        ManCommands.register(commands, terms);

        registerWindows();
        registry.onThemeChange(() -> themes.applyAll());
        themes.followSystemPreferences();

        if (registry.isDocked()) {
            startDocked(primaryStage);
            return;
        }

        // The rig monitor is the primary Stage: it is the one window that must always exist (client
        // pillar C2), so it takes the Stage the toolkit hands us rather than one we open later.
        primaryStage.setTitle(WindowSpec.RIG_MONITOR.windowTitle());
        primaryStage.setMinWidth(WindowSpec.RIG_MONITOR.minWidth());
        primaryStage.setMinHeight(WindowSpec.RIG_MONITOR.minHeight());
        Scene scene = new Scene(
                RigMonitorView.create(session),
                WindowSpec.RIG_MONITOR.defaultWidth(),
                WindowSpec.RIG_MONITOR.defaultHeight());
        primaryStage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        registry.installAllAccelerators(scene);
        primaryStage.show();

        openStartingWindows();
        startHeartbeat();

        primaryStage.setOnCloseRequest(e -> shutdown());
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
        DockedShell docked = new DockedShell(session, factories);

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
        stage.show();

        startHeartbeat();
        stage.setOnCloseRequest(e -> shutdown());
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
            case RIG_MONITOR -> RigMonitorView.create(session);
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

        Timeline autosave = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            registry.rememberAll();
            profile.save();
            session.persist();
        }));
        autosave.setCycleCount(Animation.INDEFINITE);
        autosave.play();
    }

    private void shutdown() {
        if (heartbeat != null) {
            heartbeat.stop();
        }
        registry.rememberAll();
        profile.settings().soloHandle = session.handle();
        profile.save();
        session.close();
    }

    @Override
    public void stop() {
        shutdown();
    }
}
