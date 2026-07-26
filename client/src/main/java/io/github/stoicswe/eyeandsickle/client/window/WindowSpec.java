package io.github.stoicswe.eyeandsickle.client.window;

import java.util.Arrays;
import java.util.Optional;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * The tool-window catalogue, transcribed from {@code docs/client/05-tool-windows-and-layout.md} §2.1.
 *
 * <h2>Why this is an enum and not a config file</h2>
 *
 * The catalogue is closed. {@code docs/client/05} §2.2 is explicit that adding a window is a
 * documented decision rather than a convenience — it added {@code comms} and {@code settings} and
 * said so, rather than quietly extending a table another document owns. An enum makes the set
 * closed in the compiler, makes the switcher exhaustive by construction, and makes a test able to
 * assert the whole table against the document.
 *
 * <h2>The rule the minimum sizes obey</h2>
 *
 * No window's minimum may exceed <b>720×480</b>, so that any two tools fit side by side on a
 * 1366×768 laptop with the rig strip still visible. That is not a style guideline — it is what keeps
 * the multi-window fantasy usable on the machine most players actually have, and {@link
 * #MAX_MINIMUM_WIDTH} exists so a test can enforce it rather than a reviewer having to notice.
 */
public enum WindowSpec {

    /**
     * The compute readout. Never closable, because {@code docs/design/01-core-resources.md} §1.4
     * makes it mandatory and always visible — client pillar C2. It collapses to a strip instead.
     */
    RIG_MONITOR("rig-monitor", "Rig monitor", "top", 420, 560, 320, 420, KeyCode.DIGIT0, false, false, true),

    TERMINAL("terminal", "Terminal", "a shell session", 880, 620, 560, 360, KeyCode.DIGIT1, false, true, false),
    MAP("map", "Network map", "traceroute", 1000, 720, 640, 480, KeyCode.DIGIT2, false, true, false),
    RECON("recon", "Recon", "less", 760, 640, 480, 400, KeyCode.DIGIT3, false, true, false),
    AUDIT("audit", "Audit", "ps / netstat / df", 900, 600, 640, 400, KeyCode.DIGIT4, false, true, false),
    MINING("mining", "Mining", "a miner dashboard", 820, 600, 560, 400, KeyCode.DIGIT5, false, true, false),
    STORAGE("storage", "Storage", "ls across three mounts", 840, 620, 560, 420, KeyCode.DIGIT6, false, true, false),
    LEDGER("ledger", "Ledger", "a transaction log", 880, 560, 600, 360, KeyCode.DIGIT7, false, true, false),
    BOTNET("botnet", "Botnet", "jobs / systemctl", 780, 560, 520, 400, KeyCode.DIGIT8, false, true, false),
    DEFENSE("defense", "Defense", "a firewall / IDS console", 780, 560, 520, 380, KeyCode.DIGIT9, false, true, false),

    MARKET("market", "Market", "a package manager", 900, 640, 600, 440, KeyCode.M, true, true, false),
    IDENTITY("identity", "Identity", "whoami / id", 560, 640, 420, 440, KeyCode.I, true, true, false),
    COMMS("comms", "Comms", "mail / who", 720, 620, 480, 400, KeyCode.P, true, true, false),
    SETTINGS("settings", "Settings", "~/.config", 760, 620, 560, 440, KeyCode.COMMA, false, true, false),

    /**
     * The manual and the term index.
     *
     * <p>⚠ <b>A sixteenth window that {@code docs/client/05} §2.1's table does not list.</b>
     * {@code docs/client/04-terminology-and-education.md} §4.6 adds it — "Window id {@code man}
     * (a fourteenth id — §2.2, <b>T-1</b>)" — and §2.2 of the catalogue document never absorbed it,
     * because that document added {@code comms} and {@code settings} without knowing about this one.
     * The two documents therefore disagree about the size of a table both call closed.
     *
     * <p>It is included here because the alternative is worse: the teaching layer is client pillar
     * <b>C6</b>, {@code man} is how a player reaches it deliberately, and a window that exists in one
     * document and not the other should be resolved by building the thing and reporting the
     * discrepancy rather than by silently dropping it. Logged against <b>T-1</b> and <b>WL-1</b>.
     */
    MAN("man", "Manual", "man / apropos", 820, 680, 520, 420, KeyCode.SLASH, false, true, false),

    /** The answer to losing a window behind another. Opens on first run alongside the rig monitor. */
    SWITCHER("switcher", "Windows", "jobs", 280, 520, 240, 320, KeyCode.J, true, true, true);

    /** No window's minimum may exceed this. See the class comment. */
    public static final double MAX_MINIMUM_WIDTH = 720;

    public static final double MAX_MINIMUM_HEIGHT = 480;

    private final String id;
    private final String title;
    private final String unixAnalogue;
    private final double defaultWidth;
    private final double defaultHeight;
    private final double minWidth;
    private final double minHeight;
    private final KeyCode accelerator;
    private final boolean acceleratorNeedsShift;
    private final boolean closable;
    private final boolean openOnFirstRun;

    WindowSpec(
            String id,
            String title,
            String unixAnalogue,
            double defaultWidth,
            double defaultHeight,
            double minWidth,
            double minHeight,
            KeyCode accelerator,
            boolean acceleratorNeedsShift,
            boolean closable,
            boolean openOnFirstRun) {
        this.id = id;
        this.title = title;
        this.unixAnalogue = unixAnalogue;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.accelerator = accelerator;
        this.acceleratorNeedsShift = acceleratorNeedsShift;
        this.closable = closable;
        this.openOnFirstRun = openOnFirstRun;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /**
     * The real tool this window is standing in for.
     *
     * <p>Shown in the window's own help and in the switcher, because it is one of the cheapest pieces
     * of teaching in the client: a player who learns that the audit window <em>is</em> {@code ps},
     * {@code netstat} and {@code df} has learned three real commands without being taught them.
     */
    public String unixAnalogue() {
        return unixAnalogue;
    }

    public double defaultWidth() {
        return defaultWidth;
    }

    public double defaultHeight() {
        return defaultHeight;
    }

    public double minWidth() {
        return minWidth;
    }

    public double minHeight() {
        return minHeight;
    }

    public boolean closable() {
        return closable;
    }

    public boolean openOnFirstRun() {
        return openOnFirstRun;
    }

    /**
     * The window's accelerator, using {@code SHORTCUT_DOWN} so it is Command on macOS and Control
     * elsewhere without a per-platform branch.
     *
     * <p>{@code docs/client/05} §3.6 warns about the accelerator-installation trap: accelerators
     * registered per-Stage fire only when that Stage has focus, which is precisely wrong for a
     * shortcut whose job is to raise a window you cannot see. {@link WindowRegistry} installs these on
     * every Stage for that reason.
     */
    public KeyCombination combination() {
        return acceleratorNeedsShift
                ? new KeyCodeCombination(accelerator, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN)
                : new KeyCodeCombination(accelerator, KeyCombination.SHORTCUT_DOWN);
    }

    /** The window's title as it appears in the OS title bar. */
    public String windowTitle() {
        return "The Eye and Sickle — " + title;
    }

    public static Optional<WindowSpec> byId(String id) {
        return Arrays.stream(values()).filter(w -> w.id.equals(id)).findFirst();
    }
}
