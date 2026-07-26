package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.ui.chrome.DeskManager;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.CellMeter;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Greeble;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.HazardBand;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.view.RigStatus;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The whole screen: one undecorated Stage, four regions, and a desk in the middle.
 *
 * <h2>The four regions §3 specifies</h2>
 *
 * <pre>
 *   TOP STATUS STRIP  — operator, heat, noise, load, thermal, balance, session clock
 *   RAIL (34px)       — vertical label, launcher, minimised windows, hazard texture
 *   DESK              — the in-game window manager ({@link DeskManager})
 *   COMMAND STRIP     — prompt, live input, blinking caret, keybind hints
 * </pre>
 *
 * <p>There is no OS title bar (§0, §10 criterion 1), so the top strip is also the Stage's drag
 * handle and carries its {@code [−] [□] [×]}. That keeps §3's region count exact rather than adding
 * a fifth band for window controls — and it is the honest arrangement anyway, since on this deck the
 * game window and the top readout are the same object.
 *
 * <h2>Nothing is hidden</h2>
 *
 * §3: "No hamburgers, no modals, no collapsed drawers, no tooltips carrying information not shown
 * elsewhere." Tooltips exist in this class but only ever <em>expand</em> something already on screen
 * — the heat chip's consequence text explains the band that is already displayed, which
 * {@code docs/client/07-accessibility.md} §5.2 requires anyway.
 *
 * <h2>The rail became the launcher, which settles §11 question 3</h2>
 *
 * The design language leaves the rail as "pure texture" and notes it "may want to become the window
 * switcher / running-tool list". It does, for a reason outside that document: every tool must be
 * reachable without the terminal (client pillar <b>C1</b>). Each rail entry shows the tool's
 * accelerator key, so the launcher teaches the shortcut while being the thing that replaces needing
 * it. The hazard strip and tick marks stay, so the texture argument survives too.
 */
public final class DeckShell {

    private static final int NOISE_CELLS = 18;

    private final GameSession session;
    private final Shell shell;
    private final ClientProfile profile;
    private final DeskManager desk = new DeskManager();
    private final Map<WindowSpec, Function<WindowSpec, Region>> factories;
    private final Actions actions;

    private final javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
    private final BorderPane deckRoot = new BorderPane();
    private final PauseMenu pause;
    private final HBox topStrip = new HBox();
    private final VBox rail = new VBox(UiTokens.SPACE_6);
    private final VBox launcher = new VBox(3);
    private final Greeble commandGreeble = new Greeble(28);

    private final KeyValue operator = KeyValue.of("Operator", "—");
    private final KeyValue heat = KeyValue.of("Personal heat", "—");
    private final CellMeter noise = new CellMeter(NOISE_CELLS);
    private final KeyValue load = KeyValue.of("Load", "—");
    private final KeyValue thermal = KeyValue.of("Thermal", "—");
    private final KeyValue balance = KeyValue.of("Balance", "—");
    private final KeyValue clock = KeyValue.of("Session", "00:00:00");
    private final Label refusal = new Label("");

    private final TextField commandInput = new TextField();
    private final Instant startedAt = Instant.now();
    private AutoCloseable sessionSubscription;
    private Stage stage;
    private double dragX;
    private double dragY;

    /** What the shell needs from the application that owns it. */
    public interface Actions {
        void openPalette();

        void runCommand(String line);

        void backToMenu();

        void quit();

        /** Flushes the session and the profile to disk now, rather than waiting for the autosave. */
        void save();
    }

    public DeckShell(
            GameSession session,
            Shell shell,
            ClientProfile profile,
            Map<WindowSpec, Function<WindowSpec, Region>> factories,
            Actions actions) {
        this.session = session;
        this.shell = shell;
        this.profile = profile;
        this.factories = new EnumMap<>(factories);
        this.actions = actions;

        root.getStyleClass().add("es-deck");
        deckRoot.getStyleClass().add("es-deck");
        deckRoot.setTop(buildTop());
        deckRoot.setLeft(rail);
        deckRoot.setCenter(desk.root());
        deckRoot.setBottom(buildCommandStrip());

        // The pause menu is a sibling of the whole deck, not a child of the desk: it has to cover the
        // top strip and the command strip too, or the player can still type a command into a game
        // they believe is paused.
        pause = PauseMenu.create(new PauseMenu.Actions() {
            @Override
            public void save() {
                actions.save();
            }

            @Override
            public void openSettings() {
                show(WindowSpec.SETTINGS);
            }

            @Override
            public void quitToMenu() {
                actions.backToMenu();
            }

            @Override
            public void quitGame() {
                actions.quit();
            }
        });
        root.getChildren().addAll(deckRoot, pause);

        buildRail();
        applyPlacementSetting();
        applyWindowCapSetting();
        desk.setOnRefusal(this::showRefusal);
        desk.addListener(this::refreshRail);

        // The rail hides below 900px (§3), and the desk has to reflow when the deck resizes or a
        // maximised window keeps the size it had on the old geometry.
        deckRoot.widthProperty().addListener((obs, was, now) -> {
            boolean wide = now.doubleValue() >= UiTokens.NARROW_WIDTH;
            rail.setVisible(wide);
            rail.setManaged(wide);
            desk.reflow();
        });
        deckRoot.heightProperty().addListener((obs, was, now) -> desk.reflow());

        sessionSubscription = session.onChange(s -> refreshTop());
        Pulse.shared().every(1000, this::tickClock);
        Pulse.shared().every(UiTokens.TWITCH_MS, this::refreshTop);
        refreshTop();
    }

    public Region root() {
        return root;
    }

    /** The deck itself, without the pause layer — for the shell's own layout listeners. */
    Region deckRegion() {
        return deckRoot;
    }

    public DeskManager desk() {
        return desk;
    }

    /** Opens a tool window, or raises it if it is already on the desk. */
    public void show(WindowSpec spec) {
        Function<WindowSpec, Region> factory = factories.get(spec);
        if (factory == null) {
            return;
        }
        desk.open(new DeskManager.Spec(
                        spec.id(),
                        spec.title(),
                        designator(spec),
                        content(spec, factory),
                        spec.defaultWidth() * 0.72,
                        spec.defaultHeight() * 0.72,
                        spec.closable()))
                .ifPresent(window -> Motion.reveal(window.frame(), 0));
    }

    /**
     * Builds a tool's content and marks its controls as clickable.
     *
     * <p>The sweep is here rather than in each view because {@code -fx-cursor: hand} had to leave
     * the stylesheet — a CSS cursor beats the scene cursor and would show the SYSTEM hand over every
     * button, punching holes in whichever pointer skin the player chose. See {@code ui/cursors}.
     */
    private Region content(WindowSpec spec, Function<WindowSpec, Region> factory) {
        Region region = factory.apply(spec);
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().sweep(region);
        return region;
    }

    /**
     * The dim right-hand identifier on a panel's header strip (§3).
     *
     * <p>Built from the tool's real Unix analogue rather than invented, so the designator is also
     * the cheapest piece of teaching in the client: {@code AUDIT} reads {@code SYS/PS · NETSTAT · DF}
     * and the player learns three commands without being taught them.
     */
    private static String designator(WindowSpec spec) {
        return spec.unixAnalogue().replace(" / ", " · ").toUpperCase(Locale.ROOT);
    }

    // ── Top status strip ─────────────────────────────────────────────────────────────────────

    private Region buildTop() {
        topStrip.getStyleClass().add("es-top");
        topStrip.setAlignment(Pos.CENTER_LEFT);

        HBox noiseCell = cell(KeyValue.keyOnly("Noise"), noise);

        Region spacer = new Region();
        spacer.getStyleClass().add("es-top-spacer");
        HBox.setHgrow(spacer, Priority.ALWAYS);

        refusal.getStyleClass().addAll("es-label", "es-value-warn");

        topStrip.getChildren().addAll(
                cell(operator),
                cell(heat),
                noiseCell,
                cell(load),
                cell(thermal),
                cell(refusal),
                spacer,
                HazardBand.top(96),
                cell(balance),
                cell(clock),
                stageControls());

        // The top strip is the drag handle for the whole undecorated Stage.
        topStrip.setOnMousePressed(e -> {
            dragX = e.getScreenX() - stageOrZero(true);
            dragY = e.getScreenY() - stageOrZero(false);
        });
        topStrip.setOnMouseDragged(e -> {
            if (stage != null && !stage.isMaximized()) {
                stage.setX(e.getScreenX() - dragX);
                stage.setY(e.getScreenY() - dragY);
            }
        });
        topStrip.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && stage != null) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
        return topStrip;
    }

    private double stageOrZero(boolean x) {
        if (stage == null) {
            return 0;
        }
        return x ? stage.getX() : stage.getY();
    }

    private static HBox cell(Node... content) {
        HBox box = new HBox(UiTokens.SPACE_4, content);
        box.getStyleClass().add("es-top-cell");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** The Stage's own controls, drawn rather than inherited from the OS (§0). */
    private HBox stageControls() {
        Label minimize = stageControl("[−]", () -> {
            if (stage != null) {
                stage.setIconified(true);
            }
        });
        Label maximize = stageControl("[□]", () -> {
            if (stage != null) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
        Label close = stageControl("[×]", actions::quit);
        close.getStyleClass().add("es-strip-ctl-close");
        HBox box = cell(minimize, maximize, close);
        box.setSpacing(UiTokens.SPACE_3);
        return box;
    }

    private Label stageControl(String glyph, Runnable action) {
        Label label = new Label(glyph);
        label.getStyleClass().addAll("es-strip-ctl", "es-focusable");
        label.setFocusTraversable(true);
        label.setOnMouseClicked(e -> {
            e.consume();
            action.run();
        });
        label.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                e.consume();
                action.run();
            }
        });
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(label);
        return label;
    }

    /** Binds the Stage this shell is drawing, so the drag handle and controls have a target. */
    public void attach(Stage stage) {
        this.stage = stage;
    }

    private void refreshTop() {
        RigStatus status = RigStatus.of(session);

        operator.set(Ui.upper(session.handle()));
        heat.set(Ui.upper(status.heat().label()));
        heat.valueNode().getStyleClass().removeIf(c -> c.startsWith("es-heat-"));
        heat.valueNode().getStyleClass().add(status.heat().styleClass());
        Tooltip.install(heat, new Tooltip(status.heat().consequence()));

        // Noise is a real quantity but the client is not authoritative over it (pillar C4), so what
        // the meter shows is the rig's own committed load — an honest local reading — rather than a
        // guess at how loud the Eye thinks you are.
        noise.setFraction(status.load(), status.load() > 0.9);

        long total = status.budget().total().cycles();
        long free = status.budget().available().cycles();
        load.set(total - free + "/" + total + "C");
        load.valueNode().getStyleClass().removeAll("es-value-live");
        if (status.selfMiningCycles() > 0) {
            load.valueNode().getStyleClass().add("es-value-live");
        }

        long recovering = status.budget().recovering().cycles();
        thermal.set(recovering + "C RECOV");

        balance.set(String.format(Locale.ROOT, "%.2f EC", session.balance().minorUnits() / 100.0d));
        balance.valueNode().getStyleClass().removeAll("es-value-live");
        if (status.incomeMinorUnitsPerHour() > 0) {
            balance.valueNode().getStyleClass().add("es-value-live");
        }

        // The greeble gets busier as the Eye gets closer. Nothing becomes legible; the machine just
        // gets noisier around the player. See Greeble#setAgitation.
        commandGreeble.setAgitation(status.personalHeat() / 100.0d);
    }

    private void tickClock() {
        Duration elapsed = Duration.between(startedAt, Instant.now());
        clock.set(String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                elapsed.toHours(),
                elapsed.toMinutesPart(),
                elapsed.toSecondsPart()));
    }

    // ── Rail ─────────────────────────────────────────────────────────────────────────────────

    private void buildRail() {
        rail.getStyleClass().add("es-rail");
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setMinWidth(UiTokens.RAIL_WIDTH);
        rail.setPrefWidth(UiTokens.RAIL_WIDTH);
        rail.setMaxWidth(UiTokens.RAIL_WIDTH);
        refreshRail();
    }

    private void refreshRail() {
        rail.getChildren().clear();

        Label title = Ui.label("Rig");
        title.getStyleClass().add("es-rail-label");
        title.setRotate(90);
        // Rotation does not affect layout bounds in JavaFX, so a rotated label still reserves its
        // horizontal width and would blow the 34px rail open. A Group re-measures to the rendered
        // bounds, which is the standard fix and the only reason this wrapper exists.
        rail.getChildren().add(new javafx.scene.Group(title));

        rail.getChildren().add(ticks(5));
        launcher.getChildren().clear();
        launcher.setAlignment(Pos.TOP_CENTER);
        for (WindowSpec spec : WindowSpec.values()) {
            launcher.getChildren().add(railEntry(spec));
        }
        rail.getChildren().add(launcher);
        rail.getChildren().add(ticks(3));
        rail.getChildren().add(HazardBand.rail(8));

        Label mode = Ui.label(desk.placement() == DeskManager.Placement.SNAP ? "SNP" : "FRE");
        mode.getStyleClass().add("es-rail-label");
        Tooltip.install(mode, new Tooltip(desk.placement() == DeskManager.Placement.SNAP
                ? "Windows snap to a grid, and tile when dragged to an edge. Settings → Layout."
                : "Windows drag freely. Settings → Layout."));
        rail.getChildren().add(mode);
    }

    /**
     * One launcher entry: the tool's accelerator key, lit when the tool is open.
     *
     * <p>A single character because the rail is 34px (§3). The character is the tool's real
     * accelerator, so the rail is a legend for the keyboard rather than a second, unrelated
     * vocabulary — and it satisfies pillar C1 without the terminal.
     */
    private Node railEntry(WindowSpec spec) {
        String key = acceleratorGlyph(spec);
        Label chip = Ui.label(key);
        chip.getStyleClass().add("es-chip");
        chip.setPrefWidth(22);
        chip.setAlignment(Pos.CENTER);

        boolean open = desk.find(spec.id()).filter(w -> !w.isMinimized()).isPresent();
        boolean minimized = desk.find(spec.id()).filter(DeskManager.DeskWindow::isMinimized).isPresent();
        if (open) {
            chip.getStyleClass().add("es-chip-open");
        } else if (minimized) {
            chip.getStyleClass().add("es-chip-minimized");
        }

        Tooltip.install(chip, new Tooltip(spec.title()
                + " — " + spec.unixAnalogue()
                + "\n" + spec.combination().getDisplayText()
                + (minimized ? "\nMinimised. Click to restore." : "")));
        chip.setOnMouseClicked(e -> show(spec));
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(chip);
        chip.setFocusTraversable(true);
        chip.getStyleClass().add("es-focusable");
        chip.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                show(spec);
            }
        });
        return chip;
    }

    private static String acceleratorGlyph(WindowSpec spec) {
        String name = spec.combination().getName();
        String last = name.substring(name.lastIndexOf('+') + 1).trim();
        return switch (last) {
            case "Comma" -> ",";
            case "Slash" -> "/";
            default -> last.length() > 1 ? last.substring(0, 1) : last;
        };
    }

    private Region ticks(int count) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        for (int i = 0; i < count * 4 + 1; i++) {
            boolean major = i % 4 == 0;
            box.getChildren().add(Ui.block(
                    major ? 20 : 12, UiTokens.HAIR, major ? "es-rail-tick-long" : "es-rail-tick"));
        }
        return box;
    }

    // ── Command strip ────────────────────────────────────────────────────────────────────────

    private Region buildCommandStrip() {
        HBox strip = new HBox(UiTokens.SPACE_5);
        strip.getStyleClass().add("es-cmd");
        strip.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label("rig@" + session.handle() + ":~$");
        prompt.getStyleClass().add("es-prompt");

        commandInput.getStyleClass().add("es-command-input");
        commandInput.setPromptText("alloc --release mine 12");
        HBox.setHgrow(commandInput, Priority.ALWAYS);
        commandInput.setOnAction(e -> {
            String line = commandInput.getText();
            if (!line.isBlank()) {
                actions.runCommand(line.trim());
                commandInput.clear();
            }
        });
        commandInput.setOnKeyPressed(e -> {
            // Up-arrow history, the one thing a player will try immediately and be annoyed to lose.
            if (e.getCode() == KeyCode.UP) {
                List<String> history = shell.history();
                if (!history.isEmpty()) {
                    commandInput.setText(history.getLast());
                    commandInput.positionCaret(commandInput.getText().length());
                }
                e.consume();
            }
        });

        HBox keys = new HBox(UiTokens.SPACE_6);
        keys.setAlignment(Pos.CENTER_LEFT);
        keys.getChildren().addAll(
                keyHint("⌘K", "palette", actions::openPalette),
                keyHint("⌘0", "rig", () -> show(WindowSpec.RIG_MONITOR)),
                keyHint("⌘1", "term", () -> show(WindowSpec.TERMINAL)),
                keyHint("⌘/", "man", () -> show(WindowSpec.MAN)),
                keyHint("ESC", "pause", this::togglePause));

        strip.getChildren().addAll(prompt, commandInput, Motion.caret(), commandGreeble, keys);
        return strip;
    }

    /**
     * A keybind hint that is also a button.
     *
     * <p>Pillar <b>C1</b>: a keystroke that is the only way to reach something is a hidden feature,
     * not a shortcut. Making the hint clickable costs one line and removes that whole category.
     */
    private Node keyHint(String key, String what, Runnable action) {
        Label cap = new Label(key);
        cap.getStyleClass().add("es-key-cap");
        Label text = Ui.label(what);
        HBox hint = Ui.row(UiTokens.SPACE_2, cap, text);
        hint.getStyleClass().addAll("es-key-hint", "es-focusable");
        hint.setFocusTraversable(true);
        hint.setOnMouseClicked(e -> action.run());
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(hint);
        hint.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                action.run();
            }
        });
        return hint;
    }

    /**
     * Opens or closes the pause menu.
     *
     * <p>Escape used to go straight back to the main menu — closing and persisting the session in one
     * keystroke, bound to the key whose entire cultural meaning is "wait, stop", not "leave". It now
     * opens the menu, and Escape again closes it.
     */
    public void togglePause() {
        pause.toggle();
    }

    /**
     * What Escape should do right now.
     *
     * <p>Escape means "get me out of the thing I am in", and the innermost thing wins: a half-typed
     * command first, then the pause menu. Without the first step a player who mistypes a command and
     * reaches for Escape gets a menu instead of an empty prompt, which is the wrong answer to a
     * reflex almost every terminal user has.
     */
    public void handleEscape() {
        if (pause.isVisible()) {
            pause.close();
            return;
        }
        if (commandInput.isFocused() && !commandInput.getText().isEmpty()) {
            commandInput.clear();
            return;
        }
        pause.open();
    }

    public PauseMenu pauseMenu() {
        return pause;
    }

    public void focusCommandLine() {
        commandInput.requestFocus();
    }

    // ── Settings-driven behaviour ────────────────────────────────────────────────────────────

    /** Applies the free-drag / snap-to-grid choice from Settings (§11 question 1). */
    public void applyPlacementSetting() {
        desk.setPlacement(profile.settings().freeDragWindows
                ? DeskManager.Placement.FREE
                : DeskManager.Placement.SNAP);
    }

    /**
     * Applies the Bandwidth window cap (§8).
     *
     * <p><b>[PROPOSAL]</b>, defaulted off — see {@code GameSession.RigCapacity#proposedWindowCap}
     * and open question <b>UI-2</b>.
     */
    public void applyWindowCapSetting() {
        if (profile.settings().bandwidthCapsWindows) {
            desk.setWindowCap(session.capacity().proposedWindowCap(), "Bandwidth");
        } else {
            desk.setWindowCap(Integer.MAX_VALUE, "Bandwidth");
        }
    }

    /**
     * Shows a refusal in the top strip.
     *
     * <p>Not a modal: §3 bans them, and §6 requires the message to name the constraint rather than
     * apologise. It clears itself, because a refusal that stays on screen after the player has dealt
     * with it becomes furniture.
     */
    private void showRefusal(String message) {
        refusal.setText(message);
        Pulse.shared().every(3000, new Runnable() {
            private boolean fired;

            @Override
            public void run() {
                if (!fired) {
                    fired = true;
                    return;
                }
                refusal.setText("");
            }
        });
    }

    /**
     * Opens the set that should be on screen at launch, tiled.
     *
     * <p>Tiled rather than cascaded because §3 asks for it — "panels abut and share edges, filling
     * the screen" — and because a cascade would open the rig monitor at a width where the cycle grid
     * falls back to ten cells a row. The player's first sight of the signature component should not
     * be its degraded form.
     */
    public void openStartingWindows(List<WindowSpec> specs) {
        List<WindowSpec> ordered = new ArrayList<>(specs);
        List<DeskManager.DeskWindow> opened = new ArrayList<>();
        for (WindowSpec spec : ordered) {
            Function<WindowSpec, Region> factory = factories.get(spec);
            if (factory == null) {
                continue;
            }
            desk.open(new DeskManager.Spec(
                            spec.id(),
                            spec.title(),
                            designator(spec),
                            content(spec, factory),
                            spec.defaultWidth() * 0.72,
                            spec.defaultHeight() * 0.72,
                            spec.closable()))
                    .ifPresent(opened::add);
        }

        // Deferred: the desk has no width until the Scene has laid out at least once, and tiling
        // against a zero-width desk silently does nothing. runLater puts this after that first pass.
        javafx.application.Platform.runLater(() -> {
            desk.tileAll();
            for (int i = 0; i < opened.size(); i++) {
                // Staggered, so the deck wakes up in sequence rather than all at once (§5).
                Motion.reveal(opened.get(i).frame(), i * UiTokens.REVEAL_STAGGER_MS);
            }
        });
    }

    /** Re-tiles the desk. Bound to the rail's layout control and the {@code tile} command. */
    public void tile() {
        desk.tileAll();
    }

    public void dispose() {
        if (sessionSubscription != null) {
            try {
                sessionSubscription.close();
            } catch (Exception ignored) {
                // Unsubscribing cannot fail; the checked exception is AutoCloseable's.
            }
            sessionSubscription = null;
        }
        commandGreeble.dispose();
        pause.dispose();
        desk.closeAll();
    }
}
