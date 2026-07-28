package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.profile.Hostname;
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
     *
     * <p>Derived from {@link #MAC}, which also decides which side the window controls sit on.
     */
    /** Whether this is macOS. Decides the modifier's name and which side the window controls sit on. */
    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");

    private static final String SHORTCUT = MAC ? "CMD " : "CTRL ";

    /** How many nodes the glitch edge-walk may visit per window. Bounds an FX-thread walk. */
    private static final int GLITCH_NODE_BUDGET = 220;

    /** Smallest node that counts as having an edge. Below this the effect degenerates into static. */
    private static final double GLITCH_MIN_EDGE = 14;

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
    /**
     * The top status strip. Wraps to a second row when the deck is too narrow for one.
     *
     * <p>An HBox until 2026-07-27, which squeezed and then clipped its cells rather than wrapping —
     * at 200% UI scale in a 1280px window the deck is 640 logical pixels wide and most of the strip
     * was simply gone. See {@code widgets/WrapStrip} for why neither HBox nor FlowPane alone does
     * what §3 asks for.
     */
    private final io.github.stoicswe.eyeandsickle.client.ui.widgets.WrapStrip topStrip =
            new io.github.stoicswe.eyeandsickle.client.ui.widgets.WrapStrip();
    private final VBox rail = new VBox(UiTokens.SPACE_6);
    private final VBox launcher = new VBox(3);
    private final Greeble commandGreeble = new Greeble(28);
    private final io.github.stoicswe.eyeandsickle.client.ui.widgets.Substrate substrate =
            new io.github.stoicswe.eyeandsickle.client.ui.widgets.Substrate();
    private final CrtOverlay crt = new CrtOverlay();

    /**
     * The optional drawn casing (§9, amended 2026-07-27). Off by default.
     *
     * <p>⚠ Below {@link #crt} in the stack, deliberately. The CRT layer is the screen the interface
     * is displayed <em>on</em>; a casing is a physical object in front of the screen, so a scanline
     * that ran across it would put the artefact on the wrong side of the glass.
     */
    private final Bezel bezel = new Bezel();

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

    /**
     * The operator's picture, beside their name on the strip.
     *
     * <p>Small — {@link UiTokens#STRIP_HEIGHT} minus a hair, so it sits inside the strip's own band
     * rather than setting the band's height. A cell that grew to fit a picture would push every
     * other readout on the strip down with it.
     */
    private final javafx.scene.image.ImageView operatorFace = new javafx.scene.image.ImageView();

    /**
     * What {@link #operatorFace} is currently showing.
     *
     * <p>⚠ The strip refreshes on every session change — about once a second, because self-mining
     * credits on every tick — and decoding a PNG that often to draw the same twenty-two pixels would
     * be pure waste. This is the guard: the image is rebuilt only when the stored picture actually
     * changes, which is when the player sets one.
     */
    private String operatorFaceKey;

    private final KeyValue heat = KeyValue.keyOnly("Personal heat");
    private final ThermoMeter thermo = new ThermoMeter();
    private final NoiseMeter noise = new NoiseMeter();
    private final Sparkline load = new Sparkline("Load");
    private final Sparkline thermal = new Sparkline("Thermal recovery");
    private final io.github.stoicswe.eyeandsickle.client.ui.widgets.BalanceReadout balance =
            new io.github.stoicswe.eyeandsickle.client.ui.widgets.BalanceReadout();
    private final KeyValue clock = KeyValue.of("Session", "00:00:00");

    /** The four times, only two of which are on the strip. Rebuilt on the one-second tick. */
    private final Tooltip clockTip = new Tooltip();

    /**
     * Local wall-clock time, 24-hour, from the engine's clock. See GameSession#now.
     *
     * <p>⚠ A KeyValue rather than a bare label since 2026-07-27, because it needed naming. Two
     * unlabelled times stacked in one cell is a readout that makes the player work out which is
     * which, and they answer completely different questions — one is how long this sitting has run,
     * the other is what time it is.
     */
    private final KeyValue localClock = KeyValue.of("Local", "--:--");

    /** What the balance is growing by, so the rate is readable without opening the mining window. */
    private final Label income = Ui.micro("+0.00 EC/HR");
    private final Label refusal = new Label("");

    private final TextField commandInput = new TextField();

    /**
     * {@code operator@rig.local:~$}.
     *
     * <p>Held as a field so {@link #applyPrompt()} can rebuild it. Both halves are settings a player
     * can change from inside the game — the handle from Settings or {@code rename}, the hostname
     * from Settings or {@code hostname(1)} — and a prompt built once at startup would keep showing
     * the old name until the client was restarted, which reads as the change not having worked.
     */
    private final Label prompt = new Label("");
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

        // The wallpaper goes inside the desk rather than behind the whole deck, because every other
        // region paints an opaque background over it — a backdrop under `root` would be invisible.
        desk.setBackdrop(substrate);

        // ⚠ The CRT layer is LAST, so it sits above the pause menu and the notices as well as the
        // deck. That is the whole point of it: it is the screen the interface is being displayed on,
        // and an artefact that stopped at the edge of a dialog would give the dialog away as not
        // being part of the same picture. It is mouse-transparent, or it would eat every click.
        root.getChildren().addAll(deckRoot, notices, pause, bezel, crt);

        buildRail();
        applyPlacementSetting();
        applyWindowCapSetting();
        applyScreenSettings();
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
     * Opens a shell window for one machine, or raises the one already open on it.
     *
     * <h2>⚠ One window per machine — and why that is not the thing WL-8 forbids</h2>
     *
     * {@code docs/client/05} §3.7 rules out a second window for the same tool, and its stated reason
     * is that the two would be <em>"a live view of the same session state"</em> with no way to tell
     * which you were reading. Two shells on two different machines are not one state twice; they are
     * two machines, exactly as two terminal windows on two servers are. The id carries the address,
     * so the desk keeps them apart and the title bar says which is which.
     *
     * <p>The window is <b>not</b> in {@link WindowSpec}. That enum is the closed catalogue of tools,
     * and a shell is not a tool — it is an instance of one, created by an act in the game and
     * destroyed by another. Putting it in the catalogue would mean a rail key and an accelerator for
     * a window that may not exist.
     */
    public void showShell(String address, String title, Region content, Runnable onClosed) {
        String id = "shell:" + address;
        if (desk.find(id).isPresent()) {
            desk.open(new DeskManager.Spec(id, title, address, content, 760, 520, true));
            return;
        }
        desk.open(new DeskManager.Spec(id, title, address, content, 760, 520, true))
                .ifPresent(window -> Motion.reveal(window.frame(), 0));
    }

    /** Takes a shell window off the desk. Called when the session ends from inside. */
    public void closeShell(String address) {
        desk.close("shell:" + address);
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

        Region spacer = new Region();
        spacer.getStyleClass().add("es-top-spacer");

        refusal.getStyleClass().addAll("es-label", "es-value-warn");

        operatorFace.setFitWidth(UiTokens.STRIP_HEIGHT - UiTokens.HAIR);
        operatorFace.setFitHeight(UiTokens.STRIP_HEIGHT - UiTokens.HAIR);
        operatorFace.setPreserveRatio(true);
        operatorFace.getStyleClass().add("es-avatar");
        HBox operatorCell = new HBox(UiTokens.SPACE_3, operatorFace, stacked(operator, operatorHex));
        operatorCell.setAlignment(Pos.CENTER_LEFT);
        topStrip.add(cell(operatorCell));
        // The thermometer and the band name together. §2.2.4 requires the name; the thermometer
        // adds "how close to the next band", which the name cannot carry.
        // ⚠ Stacked, not side by side, since the meter turned horizontal on 2026-07-27. `heat` is
        // KeyValue.keyOnly — the label with no value — so this is label over meter, which is the
        // anatomy every other cell in the strip already has (KeyValue is a key over a value). Beside
        // each other, heat was the one cell built differently from its neighbours.
        topStrip.add(cell(stacked(heat, thermo)));
        topStrip.add(cell(noise));
        topStrip.add(cell(load));
        topStrip.add(cell(thermal));
        topStrip.add(cell(refusal));
        topStrip.setSpacer(spacer);
        topStrip.add(spacer);
        topStrip.add(HazardBand.top(96));
        topStrip.add(cell(stacked(balance, income)));
        // ⚠ The two figures a player glances at, and the two they occasionally want, split by how
        // often they are wanted. Session and local time are always on; uptime and UTC live in the
        // tooltip, because a cell with four times in it is a cell nobody reads.
        Region clockCell = cell(stacked(clock, localClock));
        Tooltip.install(clockCell, clockTip);
        topStrip.add(clockCell);
        // ⚠ Pinned, never wrapped. These are the only way to minimise, maximise or close an
        // undecorated Stage, so they must not migrate to a second row as the window narrows.
        //
        // ⚠ AND ON THE LEFT ON macOS. Every other platform puts window controls on the right; macOS
        // puts them on the left, and this deck draws its own (§0), which means it also inherits the
        // obligation to put them where the player's OS would. A close button on the wrong side is
        // not merely unfamiliar — it gets mis-clicked, because the hand goes where it has gone ten
        // thousand times before.
        // ⚠ Only when the deck is drawing its own frame. With the OS's frame on (§0.1) the window
        // already HAS minimise, maximise and close a few pixels above these — two sets of window
        // controls on one window is not a redundancy, it is a question the player has to answer
        // every time they want to close the game.
        if (!profile.settings().nativeWindowBorder) {
            topStrip.setPinned(stageControls(), MAC);
        }

        // The top strip is the drag handle for the whole undecorated Stage — and only then. With a
        // native frame the OS title bar drags the window, and a second drag handle inside the
        // content fights it: press on the strip and the window jumps by the offset between the two.
        if (!profile.settings().nativeWindowBorder) {
            installStripDrag();
        }
        return topStrip;
    }

    /** The strip is the drag handle for an undecorated Stage. Not installed when the OS frames it. */
    private void installStripDrag() {
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
        // Green on hover, matching the desk windows and matching every traffic-light control the
        // player has ever used. See the note beside the rule in theme.css — it is a third use of
        // -es-gain and §2.1a rations that hue.
        maximize.getStyleClass().add("es-strip-ctl-max");
        Label close = stageControl("[×]", actions::quit);
        close.getStyleClass().add("es-strip-ctl-close");
        // ⚠ macOS orders them close, minimise, zoom — left to right — and everyone else orders them
        // minimise, maximise, close. Mirroring the group without reordering it would put close in
        // the far corner on a Mac, which is where maximise lives there.
        HBox box = MAC ? cell(close, minimize, maximize) : cell(minimize, maximize, close);
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
        // ⚠ Guarded on the stored value, not refreshed unconditionally — see operatorFaceKey. The
        // key includes the handle because the generated silhouette is seeded on it, so renaming
        // changes the face even when no picture is set.
        String faceKey = session.avatar() + "|" + handle;
        if (!faceKey.equals(operatorFaceKey)) {
            operatorFaceKey = faceKey;
            operatorFace.setImage(
                    io.github.stoicswe.eyeandsickle.client.ui.Avatar.image(session.avatar(), handle));
        }
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

        // Counts to the new figure and flashes the movement that caused it. See BalanceReadout.
        balance.setMinorUnits(session.balance().minorUnits());
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

        // Local wall-clock time from the ENGINE's clock — see GameSession#now. Local zone, because
        // it is a clock on the operator's own desk rather than a timestamp for anyone else to read.
        // 24-hour: this deck has no room for an am/pm and no reason to prefer one.
        java.time.Instant at = session.now();
        localClock.set(java.time.LocalTime.ofInstant(at, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)));

        // ⚠ UTC is labelled SERVER time deliberately. A federated home server is authoritative for
        // when things happened (I14), and every timestamp that ever crosses the wire is UTC — so the
        // clock a player checks against a server log has to be the same one the log is written in.
        // Naming it "UTC" alone would be true and would not say why anyone should care.
        long up = session.uptimeSeconds();
        clockTip.setText(
                "SESSION  " + clock.value() + "   this sitting\n"
                + "LOCAL    " + localClock.value() + "   your timezone\n"
                + "UPTIME   " + (up <= 0 ? "—" : uptime(up)) + "   this character, all sessions\n"
                + "SERVER   " + java.time.LocalTime.ofInstant(at, java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT))
                + "   UTC, which is what every timestamp on the wire uses");
    }

    /** Total play time as {@code 12d 04h 31m} — days only once there are any. */
    private static String uptime(long seconds) {
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        return (days > 0 ? days + "d " : "")
                + String.format(Locale.ROOT, "%02dh %02dm", hours, minutes);
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

        prompt.getStyleClass().add("es-prompt");
        applyPrompt();

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

    /**
     * Rebuilds the command-strip prompt from the live handle and the saved hostname.
     *
     * <p>{@code who@where}, in that order — {@link Hostname} has the argument for why, and it is not
     * a cosmetic one. The prompt is one of the most-seen strings in computing and the strip used to
     * print it backwards.
     *
     * <p>Called at build, after a rename, and after the hostname setting changes. The accessible
     * text spells the two apart, because {@code operator at rig dot local} read aloud is a string of
     * words with no structure in it.
     */
    public void applyPrompt() {
        String hostname = profile.settings().rigHostname;
        prompt.setText(Hostname.prompt(session.handle(), hostname));
        prompt.setAccessibleText("Signed in as " + session.handle()
                + " on " + Hostname.qualified(hostname) + ". Type a command here.");
    }

    /**
     * Applies the rounded-windows choice.
     *
     * <p>⚠ A class on the ROOT, so one flag reaches the outer Stage and every desk window at once —
     * "all windows in the game" was the request, and a per-window toggle would be a promise to
     * remember every future window type. §9's rejection list still describes the default; this is
     * opt-in and off unless the player asks.
     */
    public void applyRoundedSetting() {
        // ⚠ No style class any more. There is no CSS for this — see the note in theme.css: a
        // background radius under the notch's polygon clip is applied and then cut straight off,
        // which is how this feature silently did nothing the first time.
        boolean rounded = profile.settings().roundedWindows;
        // ⚠ The desk windows are shaped by a CLIP, not by CSS — see WindowFrame.clip. The class
        // above only reaches painted backgrounds; without this call the setting would appear to do
        // nothing at all, which is exactly what it did on the first attempt.
        //
        // The OUTER window is not this class's to round: it is a clip on the Scene root, which lives
        // above the deck. EyeAndSickleClient.applyRootRounding owns it, and putting it here would
        // have clipped the deck while the scale holder painted the corners back in.
        desk.setRoundedCorners(rounded);
    }

    /** Applies the desk-window control order (order only; never the side). */
    public void applyControlOrderSetting() {
        desk.setControlOrder(
                io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder.resolve(
                        profile.settings().subwindowControlOrder),
                MAC);
    }

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
     * Applies the wallpaper and the three CRT artefacts from Settings.
     *
     * <p>All four are player-chosen appearance, which is what {@code ui-design-language.md} §9's
     * 2026-07-26 amendment permits — scanlines, aberration and light glitch as <em>optional</em>
     * effects, with bezel and vignette still cut. Every one ships off or quiet, because each costs
     * either contrast or motion and neither is the client's to spend on a player's behalf.
     */
    /**
     * Every element the signal glitch may tear.
     *
     * <p>Window frames <em>and</em> the elements inside them — a headline, a table row, a meter, a
     * button — because that is what makes the artefact read as signal break-up rather than as
     * rectangles drawn over the picture. It is computed on demand rather than cached: the walk only
     * runs when a glitch actually fires, which is roughly every few seconds at most, and a cache
     * would be wrong the moment a window moved.
     *
     * <p>Bounded three ways, because this runs on the FX thread: only non-minimised windows, at most
     * {@link #GLITCH_NODE_BUDGET} nodes visited per window, and only nodes large enough to have a
     * visible edge. Without the size floor every one-pixel hairline in the client becomes a
     * candidate and the effect turns into uniform static, which is the opposite of the ask.
     *
     * <p>⚠ <b>Nodes, not bounds.</b> The overlay jogs these sideways with {@code translateX} so the
     * picture itself moves — handing it geometry instead would only let it paint marks on top of an
     * interface that never budged, which is what the first attempt did and why it did not read as a
     * tape fault.
     */
    private List<Node> glitchEdges() {
        List<Node> out = new ArrayList<>();
        // The top strip is chrome and always present, so there is something to tear even on a bare
        // desk — but only the strip, so an empty desk stays nearly quiet. That is intended: the
        // artefact should track how much interface is actually on screen.
        addEdge(out, topStrip);
        for (DeskManager.DeskWindow window : desk.windows()) {
            Region frame = window.frame();
            if (!frame.isVisible() || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
                continue;
            }
            addEdge(out, frame);
            collectInnerEdges(frame, out, new int[] {GLITCH_NODE_BUDGET});
        }
        return out;
    }

    /** Breadth-first over a window's contents, stopping once the visit budget is spent. */
    private void collectInnerEdges(Region root, List<Node> out, int[] budget) {
        java.util.Deque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && budget[0] > 0) {
            Node node = queue.poll();
            budget[0]--;
            if (node instanceof javafx.scene.Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) {
                    if (child.isVisible()) {
                        queue.add(child);
                    }
                }
            }
            if (node != root) {
                javafx.geometry.Bounds local = node.getBoundsInLocal();
                if (local.getWidth() >= GLITCH_MIN_EDGE && local.getHeight() >= GLITCH_MIN_EDGE) {
                    addEdge(out, node);
                }
            }
        }
    }

    private void addEdge(List<Node> out, Node node) {
        out.add(node);
    }

    public void applyScreenSettings() {
        // ⚠ The casing and the INSET are set together and must stay together. The frame is drawn in
        // a margin and the deck is pushed in by exactly that margin — that pairing is condition 2 of
        // the §9 amendment (a bezel may not cost legibility), and setting one without the other
        // either paints the casing over the top strip or leaves a blank band around the deck.
        BezelStyle casing = BezelStyle.byId(profile.settings().bezel).orElse(BezelStyle.OFF);
        bezel.setStyle(casing);
        javafx.scene.layout.StackPane.setMargin(
                deckRoot, new javafx.geometry.Insets(casing.margin()));
        javafx.scene.layout.StackPane.setMargin(
                notices, new javafx.geometry.Insets(casing.margin()));

        substrate.setMode(WallpaperMode.byId(profile.settings().wallpaper).orElse(WallpaperMode.DRIFT));
        substrate.setAberration(profile.settings().crtAberration);
        crt.setEdgeSource(this::glitchEdges);
        crt.setCurvature(profile.settings().crtCurvature / 100.0d);
        crt.setScanlines(profile.settings().crtScanlines);
        crt.setGlitch(profile.settings().crtGlitch);
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
            // ⚠ The breach window is never restored, because a breach in progress is never resumed.
            //
            // An attempt is abandoned when the save is opened (SoloGame.backfill), so bringing the
            // window back would raise an exploit console onto a breach that no longer exists —
            // an empty target list where the player left a live attempt, which reads as the game
            // having lost their progress rather than as the rule it is. Every other window is a
            // readout and comes back exactly as it was.
            if (WindowSpec.BREACH.id().equals(window.id())) {
                continue;
            }
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
        // Both hold a Pulse subscription, and a subscription outliving its node keeps a dead scene
        // graph alive and repainting — a shell dropped on the way back to the menu would otherwise
        // leave a wallpaper ticking behind the main menu forever.
        substrate.dispose();
        crt.dispose();
        bezel.dispose();
        balance.dispose();
        notices.detach();
        pause.dispose();
        desk.closeAll();
    }
}
