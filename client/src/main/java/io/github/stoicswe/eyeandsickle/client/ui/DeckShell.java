package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.ui.chrome.DeskManager;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.NoiseMeter;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Sparkline;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.ThermoMeter;
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

    /**
     * How the Shortcut modifier is spelled in a key hint.
     *
     * <p>⚠ Not {@code ⌘}. U+2318 is in neither bundled face, so it was being drawn by a host-OS
     * fallback — which on a Linux box without an Apple font is a tofu box, in the one place the
     * interface is telling the player how to use it. Spelling the modifier is also more honest on
     * Windows and Linux, where Shortcut is Control and the Apple glyph would have been wrong even
     * if it had rendered.
     */
    private static final String SHORTCUT =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                    ? "CMD "
                    : "CTRL ";

    private final GameSession session;
    private final Shell shell;
    private final ClientProfile profile;
    private final DeskManager desk = new DeskManager();
    private final Map<WindowSpec, Function<WindowSpec, Region>> factories;
    private final Actions actions;

    private final javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
    private final BorderPane deckRoot = new BorderPane();
    private final PauseMenu pause;
    private final Notifications notices;
    private final HBox topStrip = new HBox();
    private final VBox rail = new VBox(UiTokens.SPACE_6);
    private final VBox launcher = new VBox(3);
    private final Greeble commandGreeble = new Greeble(28);

    private final KeyValue operator = KeyValue.of("Operator", "—");

    /**
     * The operator name as bytes.
     *
     * <p>The same characters shown above it, in hex — so the mapping between a glyph and its pair is
     * readable straight off the strip. That is the cheapest possible demonstration of the single
     * idea {@code docs/education/01-foundations.md} exists to teach: <b>text is bytes</b>. It costs
     * one line of chrome and it is true, which is the bar {@code CLAUDE.md} sets for anything the
     * game states as fact.
     */
    private final Label operatorHex = Ui.micro("");

    private final KeyValue heat = KeyValue.keyOnly("Personal heat");
    private final ThermoMeter thermo = new ThermoMeter();
    private final NoiseMeter noise = new NoiseMeter();
    private final Sparkline load = new Sparkline("Load");
    private final Sparkline thermal = new Sparkline("Thermal recovery");
    private final KeyValue balance = KeyValue.of("Balance", "—");
    private final KeyValue clock = KeyValue.of("Session", "00:00:00");

    /** In-world time, from the engine's clock. See GameSession#now. */
    private final Label gameClock = Ui.micro("--:--:--");

    /** What the balance is growing by, so the rate is readable without opening the mining window. */
    private final Label income = Ui.micro("+0.00 EC/HR");
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
        // ⚠ A StackPane CENTRES a child larger than itself, clipping it equally top and bottom. If
        // the deck's minimum height ever exceeds the window's, the top status strip slides off the
        // top of the screen — taking the compute readout with it, which is pillar C2's one
        // structural guarantee. Letting the deck be squeezed instead means the panels get smaller,
        // which is recoverable; a readout above y=0 is not reachable at all.
        deckRoot.setMinSize(0, 0);
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
        // Over the desk, under the pause menu. Anchored top-right of the deck rather than of the
        // desk so a notice is not covered by a window tiled into that corner.
        notices = new Notifications(profile);
        javafx.scene.layout.StackPane.setAlignment(notices, Pos.TOP_RIGHT);
        notices.setPadding(new javafx.geometry.Insets(
                UiTokens.STRIP_HEIGHT + UiTokens.SPACE_6, UiTokens.SPACE_6, 0, 0));
        notices.watch(session);

        root.getChildren().addAll(deckRoot, notices, pause);

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

        Region spacer = new Region();
        spacer.getStyleClass().add("es-top-spacer");
        HBox.setHgrow(spacer, Priority.ALWAYS);

        refusal.getStyleClass().addAll("es-label", "es-value-warn");

        topStrip.getChildren().addAll(
                cell(stacked(operator, operatorHex)),
                // The thermometer and the band name together. §2.2.4 requires the name; the
                // thermometer adds "how close to the next band", which the name cannot carry.
                cell(thermo, heat),
                cell(noise),
                cell(load),
                cell(thermal),
                cell(refusal),
                spacer,
                HazardBand.top(96),
                cell(stacked(balance, income)),
                cell(stacked(clock, gameClock)),
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

    /**
     * A string as space-separated uppercase hex, truncated with an ASCII ellipsis.
     *
     * <p>Hexes the <em>displayed</em> (uppercased) form rather than the stored one, so each pair
     * lines up with the glyph above it. A player who notices that {@code H} is {@code 48} and that
     * lowercase would have been {@code 68} has learned the bit that separates them — which is the
     * whole of ASCII case in one observation.
     *
     * <p>Bytes rather than chars: {@code String.getBytes(UTF_8)} is what a name actually is on the
     * wire and in the save file, and a multi-byte character correctly produces more than one pair.
     * Encoding per {@code char} would print UTF-16 code units and quietly teach something false.
     */
    private static String hexOf(String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        int shown = Math.min(bytes.length, HEX_BYTES);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(String.format(Locale.ROOT, "%02X", bytes[i]));
        }
        if (bytes.length > shown) {
            out.append("...");
        }
        return out.toString();
    }

    /** How many bytes of the operator name the strip has room for. The rest is elided. */
    private static final int HEX_BYTES = 10;

    /**
     * A strip cell with a second, quieter line under the first.
     *
     * <p>The sub-line is always a <em>derived</em> reading of the value above it — the balance's
     * rate of change, the session timer's wall-clock equivalent. Keeping that relationship strict is
     * what stops the strip becoming a list of unrelated numbers competing for the same glance.
     */
    private static VBox stacked(Node primary, Node secondary) {
        VBox box = new VBox(2, primary, secondary);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
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
        Label maximize = stageControl("[+]", () -> {
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

        String handle = Ui.upper(session.handle());
        operator.set(handle);
        operatorHex.setText(hexOf(handle));
        if (!operatorHex.getStyleClass().contains("es-operator-hex")) {
            operatorHex.getStyleClass().add("es-operator-hex");
            operatorHex.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        }
        // ⚠ The band NAME is no longer printed. docs/client/01 §2.2.4 asks the chip to carry it and
        // docs/client/07 §5.2 forbids meaning resting on appearance — both are still satisfied, but
        // by different channels: the band is readable by WHICH ZONE the fill reaches against visible
        // graduations, which is position rather than colour, and the name itself is on the tooltip
        // and on the node's accessible text for a screen reader. §5.2's concern is colour-only
        // encoding; a graduated scale is a shape channel. Logged as UI-8.
        thermo.show(status.personalHeat(), status.heat());

        // Outward-facing work only. A rig running flat out on self-mining, defences and local
        // scans is SILENT — I4, I9 and docs/design/04 §3.1 — and a meter that showed total load
        // would teach the player the opposite of the risk model. See RigStatus#outwardCycles.
        noise.setNoise(status.noise());

        // Load and recovery are sampled on the shell's own one-second tick (see tickClock) so every
        // history on the strip shares a beat. refreshTop also runs on the 1.9s twitch, which would
        // double-sample and compress the window — so the push lives in the clock tick, not here.

        balance.set(String.format(Locale.ROOT, "%.2f EC", session.balance().minorUnits() / 100.0d));
        balance.valueNode().getStyleClass().removeAll("es-value-live");
        income.getStyleClass().removeAll("es-income-live");
        if (status.incomeMinorUnitsPerHour() > 0) {
            balance.valueNode().getStyleClass().add("es-value-live");
            income.getStyleClass().add("es-income-live");
        }
        // The projected rate, not a measurement — self-mining is online-only (I5), so this is what
        // the balance grows by WHILE THE CLIENT IS OPEN and nothing at all while it is not. Labelled
        // per hour rather than per second because the hourly figure is the one a player reasons with.
        income.setText("+" + status.incomePerHour() + " EC/HR");

        // The greeble gets busier as the Eye gets closer. Nothing becomes legible; the machine just
        // gets noisier around the player. See Greeble#setAgitation.
        commandGreeble.setAgitation(status.personalHeat() / 100.0d);
    }

    private void tickClock() {
        // One sample a second, for every history chart on the strip. Sampling here rather than in
        // refreshTop keeps the two charts on the same beat — two time series drifting apart would
        // put the same spike at two different moments.
        RigStatus status = RigStatus.of(session);
        long total = status.budget().total().cycles();
        long free = status.budget().available().cycles();
        long recovering = status.budget().recovering().cycles();
        load.push(status.load(), (total - free) + "/" + total + "C");
        thermal.push(total == 0 ? 0 : recovering / (double) total, recovering + "C");

        Duration elapsed = Duration.between(startedAt, Instant.now());
        clock.set(String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                elapsed.toHours(),
                elapsed.toMinutesPart(),
                elapsed.toSecondsPart()));

        // In-world time, from the ENGINE's clock — see GameSession#now. Local zone, because it is a
        // wall clock on the operator's own desk rather than a timestamp for anyone else to read.
        gameClock.setText(java.time.LocalTime.ofInstant(session.now(), java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)));
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

        Tooltip.install(chip, railTooltip(spec, minimized));
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

    /**
     * What a rail entry says when you hover it.
     *
     * <p>The rail can only show one character per tool — it is 34px wide (§3) — so the tooltip is
     * doing the whole job of naming seventeen otherwise-unlabelled keys. Four lines, in the order a
     * player needs them: what it is called, what it is <em>for</em>, what the real Unix tool is, and
     * how to open it from the keyboard.
     *
     * <p>§3 bans "tooltips carrying information not shown elsewhere", and this obeys that: every
     * line here is also on the tool's own header strip, in the switcher, or in the manual. It is a
     * shortcut to information, not a hiding place for it.
     */
    private static Tooltip railTooltip(WindowSpec spec, boolean minimized) {
        Tooltip tip = new Tooltip(
                Ui.upper(spec.title())
                        + "\n" + spec.description()
                        + "\n\nStands in for: " + spec.unixAnalogue()
                        + "\nOpens with: " + spec.combination().getDisplayText()
                        + (minimized ? "\nMinimised — click to restore." : ""));
        tip.setWrapText(true);
        tip.setMaxWidth(320);
        // JavaFX defaults to a one-second delay, which on a launcher rail is long enough that a
        // player scanning the strip never sees one. This is the surface those seventeen keys are
        // explained on; it has to keep up with the pointer.
        tip.setShowDelay(javafx.util.Duration.millis(220));
        tip.setShowDuration(javafx.util.Duration.seconds(30));
        return tip;
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
                keyHint(SHORTCUT + "K", "palette", actions::openPalette),
                keyHint(SHORTCUT + "0", "rig", () -> show(WindowSpec.RIG_MONITOR)),
                keyHint(SHORTCUT + "1", "term", () -> show(WindowSpec.TERMINAL)),
                keyHint(SHORTCUT + "/", "man", () -> show(WindowSpec.MAN)),
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

    /** The toast stack, so the shell can report things the rig itself did not log. */
    public Notifications notices() {
        return notices;
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
        if (notices != null) {
            notices.say("desk", message, true);
        }
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

    /**
     * Restores the desk exactly as the player left it, or tiles a starting set on a fresh profile.
     *
     * <p>A desk you arranged once should not need arranging every session — and until this existed
     * it did, because the persistence that survived from the {@code Stage}-per-tool era wrote
     * nothing the deck could read.
     *
     * <p>Windows are replayed <b>in saved order</b>, which restores z-order for free: the desk
     * raises each window as it opens, so the last one replayed ends up on top exactly as it was.
     */
    public void restoreLayout() {
        var saved = profile.settings().deskLayout;
        if (saved.isEmpty()) {
            openStartingWindows(java.util.Arrays.stream(WindowSpec.values())
                    .filter(WindowSpec::openOnFirstRun)
                    .toList());
            return;
        }

        List<DeskManager.DeskWindow> opened = new ArrayList<>();
        List<ClientProfile.DeskWindowState> states = new ArrayList<>();
        for (var entry : saved.entrySet()) {
            WindowSpec spec = WindowSpec.byId(entry.getKey()).orElse(null);
            Function<WindowSpec, Region> factory = spec == null ? null : factories.get(spec);
            if (factory == null) {
                // A window id from a build that had one this build does not. Skipping it is the
                // right failure: the rest of the layout still comes back.
                continue;
            }
            WindowSpec target = spec;
            desk.open(new DeskManager.Spec(
                            spec.id(),
                            spec.title(),
                            designator(spec),
                            content(target, factory),
                            spec.defaultWidth() * 0.72,
                            spec.defaultHeight() * 0.72,
                            spec.closable()))
                    .ifPresent(window -> {
                        opened.add(window);
                        states.add(entry.getValue());
                    });
        }

        javafx.application.Platform.runLater(() -> {
            for (int i = 0; i < opened.size(); i++) {
                ClientProfile.DeskWindowState state = states.get(i);
                opened.get(i).restoreState(
                        clampToDesk(state),
                        state.minimized,
                        state.expanded,
                        state.restoreWidth > 0
                                ? new DeskManager.Geometry(
                                        state.restoreX, state.restoreY,
                                        state.restoreWidth, state.restoreHeight)
                                : null);
            }
            for (int i = 0; i < opened.size(); i++) {
                Motion.reveal(opened.get(i).frame(), i * UiTokens.REVEAL_STAGGER_MS);
            }
        });
    }

    /**
     * Keeps a restored window somewhere the player can reach it.
     *
     * <p>The saved layout was written against whatever the desk measured last time, and the game
     * window may since have been made smaller, or moved from a large monitor to a laptop screen. A
     * window restored to x=2400 on a 1280-wide desk is invisible AND focusable, so the player can
     * hear it respond and never find it — the same failure {@code WindowCatalogueTest} guards for
     * the {@code Stage} era.
     */
    private DeskManager.Geometry clampToDesk(ClientProfile.DeskWindowState state) {
        double deskWidth = desk.root().getWidth();
        double deskHeight = desk.root().getHeight();
        if (deskWidth <= 0 || deskHeight <= 0) {
            return new DeskManager.Geometry(state.x, state.y, state.width, state.height);
        }
        double width = Math.min(state.width, deskWidth);
        double height = Math.min(state.height, deskHeight);
        double x = Math.max(0, Math.min(state.x, deskWidth - Math.min(width, UiTokens.SNAP_GRID * 4)));
        double y = Math.max(0, Math.min(state.y, deskHeight - UiTokens.STRIP_HEIGHT));
        return new DeskManager.Geometry(x, y, width, height);
    }

    /**
     * Writes the desk into the profile.
     *
     * <p>Called on autosave, on the pause menu's Save, on returning to the menu and on quit — the
     * same four moments the session itself is persisted, because "my game" includes where the
     * player put their windows.
     */
    public void saveLayout() {
        var saved = profile.settings().deskLayout;
        saved.clear();
        for (DeskManager.DeskWindow window : desk.windows()) {
            var state = new ClientProfile.DeskWindowState();
            DeskManager.Geometry geometry = window.geometry();
            state.x = geometry.x();
            state.y = geometry.y();
            state.width = geometry.width();
            state.height = geometry.height();
            state.minimized = window.isMinimized();
            state.expanded = window.isExpanded();
            DeskManager.Geometry restore = window.restorePoint();
            if (restore != null) {
                state.restoreX = restore.x();
                state.restoreY = restore.y();
                state.restoreWidth = restore.width();
                state.restoreHeight = restore.height();
            }
            saved.put(window.id(), state);
        }
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
        noise.dispose();
        notices.detach();
        pause.dispose();
        desk.closeAll();
    }
}
