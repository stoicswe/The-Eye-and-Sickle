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
import io.github.stoicswe.eyeandsickle.client.view.BreachView;
import io.github.stoicswe.eyeandsickle.client.view.CalcView;
import io.github.stoicswe.eyeandsickle.client.view.CommandPalette;
import io.github.stoicswe.eyeandsickle.client.view.FileManagerView;
import io.github.stoicswe.eyeandsickle.client.view.LogView;
import io.github.stoicswe.eyeandsickle.client.view.MainMenuView;
import io.github.stoicswe.eyeandsickle.client.view.ManView;
import io.github.stoicswe.eyeandsickle.client.view.MoreViews;
import io.github.stoicswe.eyeandsickle.client.view.NetMapView;
import io.github.stoicswe.eyeandsickle.client.view.NodeShellView;
import io.github.stoicswe.eyeandsickle.client.view.PortScanView;
import io.github.stoicswe.eyeandsickle.client.view.RigMonitorView;
import io.github.stoicswe.eyeandsickle.client.view.SetupWizardView;
import io.github.stoicswe.eyeandsickle.client.view.TerminalView;
import io.github.stoicswe.eyeandsickle.client.view.Views;
import io.github.stoicswe.eyeandsickle.client.window.GlobalShortcuts;
import io.github.stoicswe.eyeandsickle.client.window.WindowRegistry;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.time.Clock;
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
 * <h2>One undecorated Stage, and a window manager inside it</h2>
 *
 * Tools used to be separate {@link Stage}s. {@code docs/design/ui-design-language.md} §0 cancelled
 * that on 2026-07-26: native window chrome puts real macOS traffic lights and Windows title bars
 * around the game, and "the entire aesthetic depends on the player never seeing their own operating
 * system." What replaced it is {@link io.github.stoicswe.eyeandsickle.client.ui.DeckShell} — one
 * {@link javafx.stage.StageStyle#UNDECORATED} Stage containing a desk the client draws itself, with
 * drag, focus, z-order, snap-to-grid and edge tiling.
 *
 * <p>That is strictly more capable than either of the layouts it replaces, which is why the setting
 * that chose between them is gone rather than repointed. Window management under time pressure is
 * still a real barrier ({@code docs/client/07-accessibility.md}), and the answer is now the rail
 * launcher and the switcher rather than a second layout to maintain.
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
    private io.github.stoicswe.eyeandsickle.client.ui.DeckShell deck;

    /**
     * Where the breach window is pointed, shared by the two windows that can point it.
     *
     * <p>One instance for the life of the client rather than one per window open: the network map
     * arms it and the breach window reads it, and a per-window instance would mean the map aimed at
     * a copy nobody was looking at. It holds no game state — see {@link BreachArming}.
     */
    private final io.github.stoicswe.eyeandsickle.client.view.BreachArming arming =
            new io.github.stoicswe.eyeandsickle.client.view.BreachArming();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        profile = ClientProfile.discover();
        themes = new ThemeManager(profile);
        registry = new WindowRegistry(profile);
        slots = new CharacterSlots(profile);

        // ⚠ Resolved BEFORE anything reads text, and used for both the interface and the manual —
        // one language, decided once. The manual in particular is loaded here and never reloaded, so
        // deciding after this line would leave `man` permanently English however the picker was set.
        //
        // ⚠ A BLANK setting means "never chosen", which is not the same as "chose English": the
        // first is free to follow the host's language, the second must be obeyed on a German
        // machine. An unknown tag — a language some later build removed — falls to English rather
        // than throwing, because this is a file the player can edit.
        io.github.stoicswe.eyeandsickle.client.i18n.Language language =
                io.github.stoicswe.eyeandsickle.client.i18n.Language.ofTag(profile.settings().language)
                        .orElseGet(io.github.stoicswe.eyeandsickle.client.i18n.Language::hostDefault);
        io.github.stoicswe.eyeandsickle.client.i18n.Text.use(language);
        terms = TermDatabase.load(language.tag());

        themes.followSystemPreferences();

        // §0 and §10 criterion 1: no OS chrome visible on macOS, Windows or Linux. This has to be
        // set before the Stage is shown — JavaFX rejects a style change on a Stage that has already
        // been realised, and the failure is an IllegalStateException at the worst possible moment.
        // ⚠ ALWAYS TRANSPARENT, not conditionally.
        //
        // Both styles are chrome-free, so §0 holds either way. The difference is that an UNDECORATED
        // window still paints its own corner pixels — so a rounded clip cuts the deck away and the OS
        // fills the gap, which looks like a rendering fault rather than a radius. TRANSPARENT is the
        // only style in which a corner can actually be ABSENT.
        //
        // ⚠ It used to be conditional on the setting, and that was the wrong trade. `initStyle` is
        // rejected on a realised Stage, so choosing at startup meant the main window could only
        // change on a restart — while the desk windows changed instantly. A toggle that half works
        // is worse than one that does not, because the player cannot tell which half is broken.
        //
        // The residual risk is Linux without a compositor, where a transparent Stage can render
        // black. It is not exercised unless the player opts in: the scene's ground holder covers the
        // window edge to edge, so with rounding OFF nothing is ever actually see-through and the
        // window behaves exactly as it always has.
        // ⚠ DECORATED when the player asked for their OS's own frame (§0.1). It is the one setting
        // that contradicts §0 outright, and it is off by default so the shipped game still looks
        // like the game.
        //
        // ⚠ Restart-only, unavoidably: initStyle is rejected on a realised Stage, and DECORATED and
        // TRANSPARENT cannot both be true of one window. The rounded-corners setting could dodge
        // this by always being TRANSPARENT; this one has no such escape, and the Settings text says
        // so rather than leaving the player to discover it.
        primaryStage.initStyle(
                profile.settings().nativeWindowBorder
                        ? javafx.stage.StageStyle.DECORATED
                        : javafx.stage.StageStyle.TRANSPARENT);
        // ⚠ The APPLICATION name, not the game's — and deliberately so. This deck is undecorated
        // (§0), so the title is invisible inside the game and the only thing that ever reads it is
        // the OS window list. On Windows it is the ONLY lever there is: the taskbar labels a window
        // by its title and groups by the executable, and no system property changes either. A title
        // nobody can see is worth more as the one label all three platforms agree to read.
        // ⚠ Which name depends on whether anyone can SEE it. Undecorated, the title is invisible
        // in-game and its only reader is the OS window list, so the application name is worth more
        // there (it is the only lever Windows gives). With a native frame the title bar is on
        // screen and is the game's own furniture, so it says the game's name.
        primaryStage.setTitle(profile.settings().nativeWindowBorder ? "The Eye and Sickle" : Launcher.APP_NAME);
        primaryStage.setOnCloseRequest(e -> shutdown());

        // ⚠ Both of these must be set before the Stage is ever shown full screen, and neither can be
        // set from the Settings panel later without a frame where the default applies.
        //
        // (1) JavaFX's built-in full-screen exit key is ESCAPE, and it CONSUMES the event. Escape is
        // this client's pause menu (`deck.handleEscape`), so leaving the default in place means a
        // player in full screen presses Escape expecting to pause and instead drops out of full
        // screen with no menu — and the deck's own scene filter never sees the key at all.
        // (2) The "Press ESC to exit full screen" toast is OS-drawn chrome laid over a deck whose
        // entire premise (§0) is that there is none. An empty hint suppresses it.
        primaryStage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        primaryStage.setFullScreenExitHint("");

        // The firmware splash, then the login screen. Once per process — see PowerOn.
        showPowerOn(() -> showMainMenu(true));
        applyWindowSettings();
        primaryStage.show();
    }

    /** The narrowest the deck is supported at. See {@code ui/WindowSize.MIN_DECK_WIDTH}. */
    private static final double UI_MIN_WIDTH = io.github.stoicswe.eyeandsickle.client.ui.WindowSize.MIN_DECK_WIDTH;

    private static final double UI_MIN_HEIGHT = io.github.stoicswe.eyeandsickle.client.ui.WindowSize.MIN_DECK_HEIGHT;

    /** The live scaler for whichever Scene is showing, so a settings change reaches it. */
    private io.github.stoicswe.eyeandsickle.client.ui.UiScale uiScale;

    /**
     * Puts the window size, the UI scale and full screen from the profile onto the Stage.
     *
     * <h2>Order matters, twice</h2>
     *
     * The scale is applied <b>before</b> the size, because the Stage minimum is derived from it —
     * setting a 1280px width while the minimum is still 1720 (860 × 200%) silently clamps the width
     * and the player's chosen preset never takes. And full screen is applied <b>last</b>, because
     * setting a size on a full-screen Stage is either ignored or takes effect on exit, depending on
     * the platform; applying it last means the size is what the window returns to.
     *
     * <p>⚠ The size is clamped to the screen's <em>visual</em> bounds rather than its total bounds.
     * An undecorated Stage sized past the usable area has no OS chrome to drag it back with, so a
     * 4K preset picked on a 1080p display would put the deck's own controls off-screen and there
     * would be no way to reach them.
     */
    private void applyWindowSettings() {
        if (stage == null) {
            return;
        }
        int percent = io.github.stoicswe.eyeandsickle.client.ui.UiScale.sanitise(profile.settings().uiScalePercent);
        if (uiScale != null) {
            uiScale.setPercent(percent);
        }
        double factor = percent / 100.0d;

        // ⚠ The chosen resolution is the VIEWPORT's, not the window's (2026-07-27). The casing is a
        // machine around a screen, so it sits OUTSIDE the picture: choosing 1920 × 1080 has to give
        // the deck 1920 × 1080 and put the casing beyond it. Before this the casing was subtracted
        // from the resolution, so a 20px casing turned a 1920-wide choice into an 1880-wide deck and
        // the number in Settings described something the player never got.
        io.github.stoicswe.eyeandsickle.client.ui.BezelStyle casing =
                io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.byId(profile.appearance().bezel)
                        .orElse(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.OFF);
        // Both sides, and scaled with everything else — the casing is drawn inside the scaled deck,
        // so a bezel that ignored the factor would shrink as the interface grew.
        double chrome = 2 * casing.margin() * factor;

        stage.setMinWidth(UI_MIN_WIDTH * factor + chrome);
        stage.setMinHeight(UI_MIN_HEIGHT * factor + chrome);

        javafx.geometry.Rectangle2D usable = javafx.stage.Screen.getPrimary().getVisualBounds();
        io.github.stoicswe.eyeandsickle.client.ui.WindowSize size =
                io.github.stoicswe.eyeandsickle.client.ui.WindowSize.byId(profile.settings().windowSize)
                        .orElse(io.github.stoicswe.eyeandsickle.client.ui.WindowSize.HD_1280);

        if (!stage.isFullScreen() && !stage.isMaximized()) {
            double width = Math.max(stage.getMinWidth(), Math.min(size.width() + chrome, usable.getWidth()));
            double height = Math.max(stage.getMinHeight(), Math.min(size.height() + chrome, usable.getHeight()));
            stage.setWidth(width);
            stage.setHeight(height);
            // Re-centred, because a window that grew from its top-left corner can end up mostly off
            // the bottom-right of the screen — and there is no title bar to drag it back by until
            // the top strip is on screen.
            stage.setX(usable.getMinX() + (usable.getWidth() - width) / 2);
            stage.setY(usable.getMinY() + (usable.getHeight() - height) / 2);
        }

        stage.setFullScreen(profile.settings().fullScreen);
    }

    /**
     * Builds a Scene whose content is drawn through the UI scaler.
     *
     * <h2>⚠ Every Scene the client ever sets, without exception</h2>
     *
     * The menu, the boot sequence and the deck are three separate Scenes on one Stage, and the
     * scaler lives on the Scene rather than on the Stage — so one that skipped this would snap back
     * to 100% when the player walked through it. The boot sequence is the one that makes this
     * visible: it sits between the menu and the deck for a few seconds, and at 150% it was the only
     * screen that was not.
     */
    private Scene scaled(javafx.scene.Parent content, double width, double height) {
        // A Parent that is not a Region cannot be given a size, and everything the client roots a
        // Scene at is a Region. Guarding rather than casting blind, because the failure mode is a
        // ClassCastException at a screen transition rather than at startup.
        if (!(content instanceof javafx.scene.layout.Region region)) {
            uiScale = null;
            return new Scene(content, width, height);
        }
        uiScale = new io.github.stoicswe.eyeandsickle.client.ui.UiScale(region);
        // ⚠ The holder paints the deck's own ground. Without it the Scene's default fill — WHITE —
        // shows for the frame between the Stage taking a new Scene and CSS resolving on it, which is
        // the flash a player sees when the boot log hands over to the deck. Painting it here rather
        // than calling scene.setFill keeps the colour in the stylesheet, where §10 criterion 2
        // requires every colour in this client to live.
        uiScale.root().getStyleClass().add("es-scene-ground");
        uiScale.setPercent(
                io.github.stoicswe.eyeandsickle.client.ui.UiScale.sanitise(profile.settings().uiScalePercent));
        Scene scene = new Scene(uiScale.root(), width, height);
        // ⚠ A transparent FILL with an opaque ground holder on top of it. The holder covers the
        // window edge to edge, so nothing is see-through until the clip takes a corner away — which
        // is what makes always-TRANSPARENT safe for players who never touch the setting.
        //
        // Color.TRANSPARENT rather than a colour: §10 criterion 2 keeps every COLOUR in the
        // stylesheet, and the absence of one is not a colour.
        if (!profile.settings().nativeWindowBorder) {
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        }
        applyRootRounding(scene);
        return scene;
    }

    /** True once the firmware splash has played. It plays on power-on, not on every logout. */
    private boolean poweredOn;

    /**
     * The rig's firmware coming up, before anyone has said who they are.
     *
     * <p>Its own Scene, like the uOS boot log — and for the same reason the menu is not built behind
     * it: the login screen's first paint is a row of pictures being decoded, and a splash sitting on
     * top of that would hand the player a half-drawn screen the moment it ended.
     *
     * <p>⚠ Guarded, and guarded here rather than inside {@link PowerOn}. {@code showMainMenu} is
     * reached from four places — startup, "quit to menu", the pause menu and a failed connection —
     * and only the first of those is a machine being switched on.
     */
    private void showPowerOn(Runnable then) {
        if (poweredOn) {
            then.run();
            return;
        }
        poweredOn = true;

        io.github.stoicswe.eyeandsickle.client.ui.PowerOn splash =
                io.github.stoicswe.eyeandsickle.client.ui.PowerOn.play(then);
        Scene scene = scaled(splash, 980, 760);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        // Focused so a keypress skips it without the player having to click the window first.
        splash.requestFocus();
    }

    /**
     * The menu, which is where the game starts.
     *
     * <p>Also where it returns to. Nothing about a session survives going back here — the previous
     * {@link GameSession} is closed and persisted first, so "back to menu" cannot leave a half-live
     * game ticking behind a screen the player thinks is idle.
     */
    private void showMainMenu() {
        showMainMenu(false);
    }

    /**
     * @param fadeIn true only when arriving from the firmware splash. Every other route here — quit
     *     to menu, the pause menu, a failed connection — is a screen change the player asked for,
     *     and fading those in would put a delay between their click and the thing they clicked for.
     */
    private void showMainMenu(boolean fadeIn) {
        closeSession();
        // Leaving a character puts the machine back into its own clothes. The login screen belongs
        // to the machine, not to whichever operator was last sitting at it.
        profile.useMenuAppearance();
        themes.reloadAppearance();

        MainMenuView.Actions actions = new MainMenuView.Actions() {
            @Override
            public void playSolo(int slot, String handleIfNew) {
                startSolo(slot, handleIfNew);
            }

            @Override
            public void setUpNewCharacter(int slot, String suggestedHandle) {
                showSetupWizard(slot, suggestedHandle);
            }

            @Override
            public void connectOnline(String serverAddress) {
                EyeAndSickleClient.this.connectOnline(serverAddress);
            }

            @Override
            public void addOnlineAccount() {
                EyeAndSickleClient.this.showOnlineAccount();
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

        // ⚠ The CONTENT fades, not the Scene root. The root holds the ground colour, and fading
        // that would show the Scene's TRANSPARENT fill through it (§0) — a see-through window for a
        // fifth of a second. Fading the content over a black that never moves is also what the
        // handover should look like.
        javafx.scene.layout.Region content = MainMenuView.create(profile, themes, slots, actions);
        Scene scene = scaled(content, 980, 760);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        if (fadeIn) {
            io.github.stoicswe.eyeandsickle.client.ui.Fade.in(content);
        }
    }

    /**
     * The setup assistant, between "New character" and the character existing.
     *
     * <p>⚠ It writes the profile's GLOBAL settings live, so the player can see a palette rather than
     * read its name — which means backing out has to put them back. The snapshot is taken here
     * rather than inside the view because the restore has to outlive the view: by the time cancel
     * runs, that node is being discarded.
     *
     * <p>The picture is the one value that cannot be applied as it is chosen. There is no save to
     * hold it until {@link #startSolo} has run, so it rides out of the wizard and is applied to the
     * session immediately afterwards.
     */
    private void showSetupWizard(int slot, String suggestedHandle) {
        ClientProfile.Settings settings = profile.settings();
        io.github.stoicswe.eyeandsickle.client.profile.SettingsSnapshot before =
                io.github.stoicswe.eyeandsickle.client.profile.SettingsSnapshot.of(settings);

        // ⚠ The palette is previewed on a look that belongs to NOBODY yet. The assistant is choosing
        // the appearance of a character that does not exist, so its edits must not reach the menu's
        // — that is what makes Cancel free rather than something that has to be unwound, and it is
        // why cancelling out of pane four cannot re-theme the character the player was playing.
        io.github.stoicswe.eyeandsickle.client.profile.VisualSettings pending = settings.appearance.copy();
        profile.usePendingAppearance(pending);
        themes.reloadAppearance();

        SetupWizardView.Actions actions = new SetupWizardView.Actions() {
            @Override
            public void applyPreview() {
                applyWindowSettings();
            }

            @Override
            public void begin(int chosenSlot, String handle, String avatarPng) {
                // The look becomes the character's here and nowhere earlier. startSolo re-points the
                // profile at the slot, so the pending set has to be stored against it FIRST or the
                // character opens wearing the menu's palette instead of the one just chosen.
                settings.characterAppearance.put(String.valueOf(chosenSlot), pending);
                profile.save();
                startSolo(chosenSlot, handle);
                if (session != null && avatarPng != null && !avatarPng.isEmpty()) {
                    session.setAvatar(avatarPng);
                }
            }

            @Override
            public void cancel() {
                // The pending look is simply dropped — nothing wrote it anywhere. Only the
                // machine-wide settings the assistant touched need putting back.
                before.restoreTo(settings);
                profile.save();
                profile.useMenuAppearance();
                themes.reloadAppearance();
                // ⚠ Restoring the VALUES is only half of it: two of them need a runtime call before
                // anything on screen changes. applyWindowSettings is what actually moves the UI
                // scaler — UiScale.setPercent alone leaves the Stage's minimum size stale.
                themes.setReducedMotionOverride(before.reducedMotionOverride());
                applyWindowSettings();
                showMainMenu();
            }
        };

        Scene scene = scaled(SetupWizardView.create(profile, themes, slot, suggestedHandle, actions), 980, 760);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
    }

    /** Settings reached from the menu, before a game exists. */
    private void showMenuSettings() {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        // Undecorated, like the main Stage. A Dialog defaults to a real OS-decorated window, which
        // would put macOS traffic lights on screen in a client whose §0 premise is that the player
        // never sees their own operating system.
        dialog.initStyle(javafx.stage.StageStyle.UNDECORATED);
        dialog.setTitle("Settings");
        dialog.setHeaderText("Settings");
        dialog.getDialogPane()
                .setContent(Views.settings(profile, themes, this::applyDeskSettings, null, this::applyWindowSettings));
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        themes.adopt(dialog.getDialogPane().getScene());
        dialog.showAndWait();
        profile.save();
    }

    /**
     * Writes everything that persists: the desk arrangement, the profile, and the save.
     *
     * <p>One method so the four callers — autosave, the pause menu's Save, returning to the menu and
     * quitting — cannot drift into saving different subsets of the same thing.
     */
    private void saveEverything() {
        if (deck != null) {
            deck.saveLayout();
        }
        profile.save();
        if (session != null) {
            session.persist();
        }
    }

    /**
     * Renames the solo operator.
     *
     * <p>⚠ Solo only, and the check is here rather than in the view: online, a handle comes from an
     * AT Proto DID and the server owns it (Invariant I14). {@code SoloGame.rename} is deliberately
     * not on the {@code GameSession} port for the same reason — a capability that must never work
     * online is best made absent rather than guarded.
     */
    private void renameOperator(String handle) {
        if (session instanceof LocalGameSession local) {
            local.game().rename(handle);
            session.persist();
            profile.save();
            // The handle is the left half of the command-strip prompt, which is built once. Without
            // this the strip keeps the old name until the client is restarted.
            if (deck != null) {
                deck.applyPrompt();
            }
        }
    }

    /**
     * Rounds — or unrounds — the whole application window.
     *
     * <h2>⚠ The SCENE ROOT is clipped, not the deck</h2>
     *
     * The scene root is the UI-scale holder, and the deck is inside it. Clipping the deck leaves the
     * holder's own ground painting square corners over the top, which is indistinguishable from the
     * setting doing nothing — the exact failure this feature has now had twice, once from CSS being
     * clipped off and once from clipping the wrong node. Clipping the outermost thing means nothing
     * downstream can paint the corner back in.
     *
     * <p>Sized from the root's own properties rather than from a listener: a size listener on the
     * deck fires before the {@code BorderPane} has laid out its centre, which is the seventh JavaFX
     * trap in {@code CLAUDE.md}.
     */
    private void applyRootRounding(Scene scene) {
        if (scene == null || !(scene.getRoot() instanceof javafx.scene.layout.Region root)) {
            return;
        }
        // ⚠ With a native frame the OS owns the outer corners, so clipping the scene root would cut
        // the game away INSIDE a square window — a visible gap between the content and the frame.
        // Desk windows still round; that is the deck's own furniture and stays the deck's business.
        if (!profile.appearance().roundedWindows || profile.settings().nativeWindowBorder) {
            root.setClip(null);
            return;
        }
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        // ⚠ arcWidth is the full arc, so it is twice the radius. Getting that backwards halves the
        // curve and looks like the constant being wrong rather than the arithmetic.
        clip.setArcWidth(io.github.stoicswe.eyeandsickle.client.ui.UiTokens.WINDOW_RADIUS * 2);
        clip.setArcHeight(io.github.stoicswe.eyeandsickle.client.ui.UiTokens.WINDOW_RADIUS * 2);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);
    }

    /** Pushes the desk options to the live shell. A no-op from the menu, where there is no desk. */
    private void applyDeskSettings() {
        if (deck != null) {
            deck.applyPlacementSetting();
            deck.applyWindowCapSetting();
            deck.applyScreenSettings();
            deck.applyRoundedSetting();
            // The command strip's prompt is built from a setting too, and a prompt still showing the
            // old hostname after the field said it had saved reads as the setting not having worked.
            deck.applyPrompt();
        }
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
            // No handle is passed: nothing has signed in, so the session reports NOT_SIGNED_IN.
            // This used to hand over profile.settings().soloHandle — the OFFLINE character's name —
            // which then showed as the online identity forever (architecture/10 §2).
            var remote =
                    new io.github.stoicswe.eyeandsickle.client.session.RemoteGameSession(java.net.URI.create(address));
            var outcome = remote.allocateSelfMining(0);
            remote.close();
            alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION,
                    outcome.message() + "\n\nThe address is remembered. Solo play needs none of this and works now.");
        } catch (IllegalArgumentException badUri) {
            alert(javafx.scene.control.Alert.AlertType.WARNING, "That is not a valid address: " + address);
        }
    }

    /**
     * The signed-in AT Protocol identity, or null. Session state, never persisted here.
     *
     * <p>⚠ The credentials live in the {@code TokenStore}; this is only what is on screen. Putting
     * any part of a token in {@code ClientProfile} would break the promise its own comment makes.
     */
    private io.github.stoicswe.eyeandsickle.client.oauth.SignInFlow.Identity onlineIdentity;

    /**
     * Opens the "Add an online account" panel.
     *
     * <h2>⚠ The browser is opened through JavaFX's own {@code HostServices}</h2>
     *
     * Not {@code Desktop.browse} and not a subprocess. {@code HostServices.showDocument} is the
     * toolkit's supported route, works from a jpackage image, and needs no {@code java.desktop}
     * grant. ⚠ This is the first time the client opens a browser at all — {@code CLAUDE.md} records
     * that Credits prints handles rather than linking them, because "opening a browser would throw
     * the player out of a full-screen game". That reasoning holds for a gratuitous link; here the
     * redirect <em>is</em> the protocol, and the panel warns before it happens.
     */
    private void showOnlineAccount() {
        var store = io.github.stoicswe.eyeandsickle.client.oauth.TokenStores.forProfile(profile.directory());
        var http = new io.github.stoicswe.eyeandsickle.protocol.identity.HardenedHttpClient();
        var dids = new io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver();
        var handles = new io.github.stoicswe.eyeandsickle.protocol.identity.HandleResolver(
                http, dids, io.github.stoicswe.eyeandsickle.protocol.identity.TxtLookup.system());
        var discovery = new io.github.stoicswe.eyeandsickle.client.oauth.OauthDiscovery(http, handles, dids);

        var flow = new io.github.stoicswe.eyeandsickle.client.oauth.SignInFlow(
                discovery,
                store,
                uri -> getHostServices().showDocument(uri.toString()),
                // ⚠ http://localhost is the atproto DEVELOPMENT client_id: the authorization server
                // synthesises native-client metadata with loopback redirects for it, so the whole
                // flow works with no domain and no certificate. A release build points this at the
                // project's hosted client-metadata document and nothing else changes.
                redirectUri -> new io.github.stoicswe.eyeandsickle.client.oauth.OauthClient(
                        http, "http://localhost", redirectUri, java.time.Instant::now));

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        var panel = io.github.stoicswe.eyeandsickle.client.view.OnlineAccountPanel.build(
                new io.github.stoicswe.eyeandsickle.client.view.OnlineAccountPanel.Host() {
                    @Override
                    public void signIn(
                            String handle,
                            String server,
                            java.util.function.Consumer<
                                            io.github.stoicswe.eyeandsickle.client.oauth.SignInFlow.Identity>
                                    onDone,
                            java.util.function.Consumer<Exception> onError) {
                        io.github.stoicswe.eyeandsickle.client.view.OnlineAccountPanel.offThread(
                                () -> flow.signIn(handle, server), onDone, onError);
                    }

                    @Override
                    public io.github.stoicswe.eyeandsickle.client.oauth.TokenStore store() {
                        return store;
                    }
                },
                identity -> onlineIdentity = identity);
        popup.getContent().add(panel);
        if (stage != null) {
            popup.show(stage);
        }
    }

    private void alert(javafx.scene.control.Alert.AlertType type, String message) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(type, message);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /** Opens a solo character and switches the window to the game. */
    private void startSolo(int slot, String handleIfNew) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(slots.saveFile(slot));
        String handle = handleIfNew != null && !handleIfNew.isBlank()
                ? handleIfNew.trim()
                : (profile.settings().soloHandle.isBlank() ? "operator" : profile.settings().soloHandle);

        // ⚠ BEFORE the session, the shell, the windows and the deck. Everything below this line
        // reads the appearance to build itself, and a deck constructed against the menu's palette
        // and re-themed afterwards flashes the wrong look for one frame — on the screen the player
        // is watching most closely, immediately after choosing it.
        profile.useCharacterAppearance(slot);
        themes.reloadAppearance();

        session = new LocalGameSession(SoloGame.open(store, handle, Clock.systemUTC()));

        Shell.CommandRegistry commands = BuiltinCommands.registry();
        shell = new Shell(session, commands);
        ClientCommands.register(
                commands,
                registry,
                themes,
                profile,
                () -> shell.history(),
                this::showMainMenu,
                this::applyDeskSettings);
        ManCommands.register(commands, terms);
        // Pillar C1: everything the breach window can do, the terminal can do. Both go through the
        // same GameSession port, so the two cannot disagree about what a move costs or whether it
        // was allowed.
        io.github.stoicswe.eyeandsickle.client.shell.BreachCommands.register(commands);
        io.github.stoicswe.eyeandsickle.client.shell.NetCommands.register(commands);

        registerWindows();
        registry.onThemeChange(() -> themes.applyAll());

        profile.settings().lastSoloSlot = slot;
        profile.settings().soloHandle = session.handle();
        profile.save();

        // The boot log first. It reads the session that was just opened, so every figure it prints
        // is this rig's — see BootSequence. The deck is built behind it and swapped in when it ends.
        showBootSequence(() -> {
            startDeck(stage);
            startHeartbeat();
            // ⚠ AFTER the deck exists, and only here. The report hangs off the balance cell's bounds,
            // which are zero until the strip has laid out — and this is the one moment a load has to
            // announce itself. Consuming it also means the LEDGER window will not repeat it.
            deck.showChainSync();
        });
    }

    /**
     * Plays the uOS boot log, then hands over to the deck.
     *
     * <p>On its own Scene rather than layered over the deck: the deck's first paint includes a
     * staggered panel reveal (§5), and having that happen underneath a boot log would mean the
     * player's first sight of the desk was the tail end of an animation they never saw start.
     */
    private void showBootSequence(Runnable then) {
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        io.github.stoicswe.eyeandsickle.client.ui.BootSequence boot =
                io.github.stoicswe.eyeandsickle.client.ui.BootSequence.play(session, then);
        root.getChildren().addAll(boot, boot.hint());
        javafx.scene.layout.StackPane.setAlignment(boot.hint(), javafx.geometry.Pos.BOTTOM_CENTER);

        Scene scene = scaled(
                root, stage.getWidth() > 0 ? stage.getWidth() : 1280, stage.getHeight() > 0 ? stage.getHeight() : 800);
        stage.setScene(scene);
        themes.adopt(scene);
        themes.applyAll();
        // Focused so a keypress skips it without the player having to click the window first.
        boot.requestFocus();
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
                String level =
                        switch (profile.settings().teachingLevel) {
                            case "explain" -> "terms";
                            case "terms" -> "off";
                            default -> "explain";
                        };
                profile.settings().teachingLevel = level;
                profile.save();
            }

            @Override
            public void toggleLayout() {
                // Was: switch between the docked layout and the multi-window desk. Both were
                // replaced by the deck (§0), so the shortcut now toggles the thing the design
                // language actually left open — §11 question 1, free-drag versus snap-to-grid.
                profile.settings().freeDragWindows = !profile.settings().freeDragWindows;
                profile.save();
                applyDeskSettings();
            }

            @Override
            public void cycleWindows() {
                if (deck != null) {
                    deck.desk().focusNext();
                }
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
        // ⚠ Order matters: the layout is captured BEFORE the deck is disposed, because disposing
        // closes every window and there would be nothing left to record. Getting this backwards
        // saves an empty desk over the player's arrangement, every time they quit.
        if (session != null) {
            saveEverything();
        }
        if (deck != null) {
            deck.dispose();
            deck = null;
        }
        registry.closeAll();
        if (session != null) {
            session.close();
            session = null;
        }
    }

    /**
     * The deck: one undecorated Stage, the four regions §3 specifies, and a drawn window manager.
     *
     * <p>{@code docs/client/07-accessibility.md} §2.3's requirement that no functionality or
     * information is lost carries over unchanged and is stronger here than it was under the docked
     * layout: the compute readout is a cell in the top strip, which is chrome. There is no z-order
     * for it to lose and no tab it can hide behind, which is client pillar <b>C2</b> made
     * structural rather than maintained by hand.
     */
    private void startDeck(Stage stage) {
        java.util.Map<WindowSpec, java.util.function.Function<WindowSpec, javafx.scene.layout.Region>> factories =
                new java.util.EnumMap<>(WindowSpec.class);
        for (WindowSpec spec : WindowSpec.values()) {
            factories.put(spec, s -> (javafx.scene.layout.Region) contentFor(s));
        }

        deck = new io.github.stoicswe.eyeandsickle.client.ui.DeckShell(
                session, shell, profile, factories, deckActions());
        deck.attach(stage);

        Scene scene = scaled(deck.root(), 1280, 800);
        stage.setScene(scene);
        // The deck comes up out of the dark rather than appearing whole. §5 allows step timing only,
        // so this is the same nine-step DISCRETE ladder the panel reveal uses — which is also the
        // truer effect, since a tube coming up to brightness rises through visible levels.
        io.github.stoicswe.eyeandsickle.client.ui.Motion.fadeIn(
                deck.root(), io.github.stoicswe.eyeandsickle.client.ui.UiTokens.WAKE_MS);
        themes.adopt(scene);
        themes.applyAll();
        // Accelerators open a window ON THE DESK. Routing them through the registry's Stage path
        // would open a second OS window and break §0's whole premise from a keystroke.
        registry.installAllAccelerators(scene, deck::show);
        // The map's BREACH control raises the breach window without knowing a deck exists. Wired
        // here because this is the first moment there is a deck to raise it on.
        arming.setOpener(() -> deck.show(WindowSpec.BREACH));
        GlobalShortcuts.install(scene, globalHandlers());

        // Escape opens the pause menu. A filter rather than a handler, so it fires even while a text
        // field has focus — a player who has just typed a command and wants out should not have to
        // click elsewhere first. What it actually does is DeckShell's decision, because the innermost
        // thing wins: a half-typed command clears before the menu opens.
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                e.consume();
                deck.handleEscape();
            }
        });
        deck.applyRoundedSetting();
        deck.applyControlOrderSetting();
        deck.restoreLayout();
    }

    /**
     * What the map's right-click menu does.
     *
     * <p>⚠ The shell is opened by asking the <b>rules</b> first and only then making a window. A
     * window created before the session exists would be a shell attached to nothing — and the refusal
     * (no foothold, not enough cycles) is exactly the message the player needs, which they would
     * never see behind a window that had already opened.
     */
    private NetMapView.NodeActions nodeActions() {
        return new NetMapView.NodeActions() {
            @Override
            public void openShell(String address) {
                boolean already =
                        session.sessions().stream().anyMatch(s -> s.address().equals(address));
                if (!already) {
                    GameSession.Outcome outcome = session.openSession(address);
                    if (!outcome.succeeded()) {
                        // The refusal is already in the log — announce() put it there — so there is
                        // nothing to print here. Not opening the window IS the response.
                        return;
                    }
                }
                if (deck == null) {
                    return;
                }
                deck.showShell(
                        address,
                        "Shell — " + address,
                        NodeShellView.create(session, address, () -> deck.closeShell(address)),
                        // ⚠ Closes the SESSION, not the window. The window is already going — this
                        // is what hands its two cycles back. Pointing it at closeShell (as it was)
                        // asked the desk to close a window it was in the middle of closing, which
                        // did nothing and left the allocation held forever.
                        () -> session.closeSession(address));
            }

            @Override
            public void breach(String address) {
                arming.rearm("node:" + address);
                arming.open();
            }

            /**
             * ⚠ Opened as a SHELL-style window, not a catalogue one.
             *
             * <p>The desk's {@code showShell} keys a window on a string and reuses it, which is
             * exactly the shape a per-machine tool needs: two scanners open on two machines, one per
             * machine, and reopening raises the one that is already there. Adding it to
             * {@code WindowSpec} instead would put it in the rail, where it would open with no
             * target and have nothing to be about.
             */
            @Override
            public void portScan(String address) {
                if (deck == null) {
                    return;
                }
                String key = "portscan:" + address;
                deck.showShell(
                        key,
                        "Port scan - " + address,
                        // ⚠ Swallowed here, not printed twice. Every Outcome this panel produces has already
                        // reached the rig log through the session, and a second copy in a window
                        // would be the same sentence in two places — which is how a player comes to
                        // believe two things happened.
                        PortScanView.create(session, address, message -> {}),
                        // ⚠ Nothing to release. A scanner or a report is a VIEW onto state that exists
                        // whether or not it is on screen — unlike a shell, which is an
                        // instance holding cycles for as long as it lives.
                        null);
            }

            /** Same per-subject window shape as the scanner — one file open per machine. */
            @Override
            public void info(String address) {
                if (deck == null) {
                    return;
                }
                String key = "report:" + address;
                deck.showShell(
                        key,
                        "Report - " + address,
                        io.github.stoicswe.eyeandsickle.client.view.NodeReportView.create(session, address),
                        // ⚠ Nothing to release. A scanner or a report is a VIEW onto state that exists
                        // whether or not it is on screen — unlike a shell, which is an
                        // instance holding cycles for as long as it lives.
                        null);
            }
        };
    }

    private io.github.stoicswe.eyeandsickle.client.ui.DeckShell.Actions deckActions() {
        return new io.github.stoicswe.eyeandsickle.client.ui.DeckShell.Actions() {
            @Override
            public void openPalette() {
                globalHandlers().openPalette();
            }

            @Override
            public void runCommand(String line) {
                shell.run(line);
            }

            @Override
            public void backToMenu() {
                showMainMenu();
            }

            @Override
            public void quit() {
                shutdown();
                javafx.application.Platform.exit();
            }

            @Override
            public void save() {
                // Everything shutdown() would write, without ending anything. The desk layout is
                // included because the arrangement is part of what a player means by "save my
                // game", even though it lives in the profile rather than the save file.
                saveEverything();
            }
        };
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
            case BREACH -> BreachView.create(session, terms, profile, arming);
            case NETMAP -> NetMapView.create(session, arming, nodeActions());
            case AUDIT -> io.github.stoicswe.eyeandsickle.client.view.AuditView.create(session, shell);
            case MINING -> Views.mining(session);
            case STORAGE -> Views.storage(session);
            case LEDGER -> Views.ledger(session);
            case DEFENSE -> Views.defense(session);
            case IDENTITY -> Views.identity(session);
            case SWITCHER -> Views.switcher(registry);
            case SETTINGS ->
                Views.settings(
                        profile,
                        themes,
                        this::applyDeskSettings,
                        this::renameOperator,
                        this::applyWindowSettings,
                        session);
            case CALC -> CalcView.create();
            case FILES -> FileManagerView.create(session);
            case MAN -> ManView.create(terms);
            case LOG -> LogView.create(session);
            case MARKET -> MoreViews.market(session);
            // ⚠ RECON is the reports now, not the page about them. The cost model and what a scan
            // is a model of moved to `man port-scan` — reference a player reads once, in the place
            // they can find it deliberately, rather than above the data every single time.
            case RECON ->
                io.github.stoicswe.eyeandsickle.client.view.ReconView.create(
                        session, address -> nodeActions().info(address));
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

        autosave = new Timeline(new KeyFrame(Duration.seconds(30), e -> saveEverything()));
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
