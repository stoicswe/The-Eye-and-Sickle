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
    RIG_MONITOR(
            "rig-monitor",
            "Rig monitor",
            "top",
            "Where every cycle is, one cell per cycle. Also what the rig is working on, with time remaining.",
            420,
            560,
            320,
            420,
            KeyCode.DIGIT0,
            false,
            false,
            true),

    TERMINAL(
            "terminal",
            "Terminal",
            "a shell session",
            "A real shell over the game: pipelines, globs, exit statuses. Everything the windows do, and some things they do not.",
            880,
            620,
            560,
            360,
            KeyCode.DIGIT1,
            false,
            true,
            false),
    /**
     * The network, as a graph you read and a list you sort. <b>The only network tool.</b>
     *
     * <h2>⚠ There were two of these, and that was the bug</h2>
     *
     * A second window — {@code map}, "Network map", on this same {@code Shortcut+2} — used to sit
     * here holding a read-only table of known nodes. The comment that justified it said the two
     * "answer different questions: the list is a table you sort, the graph is a shape you read".
     * That was true and it did not survive contact: this window has <b>had</b> a LIST view the whole
     * time, on a chip beside GRAPH and FOLDERS, so the split bought a second window and no second
     * capability.
     *
     * <p>What it cost was worse than duplication. The stub had <b>no sweep control</b>, so it was
     * permanently empty for anyone who had not swept elsewhere, and it carried a note reading
     * <em>"Breach targeting is not built"</em> — stale since the breach window shipped. A player
     * pressing {@code Shortcut+2} landed on an empty table that told them the feature did not exist,
     * while the working tool sat behind a letter key. Reported as networking and breaching having
     * regressed; nothing had regressed, and both tools were fine.
     *
     * <p>⚠ It inherits {@code Shortcut+2} deliberately. The habit that caused the confusion now
     * lands on the tool that works, and the digit row stays contiguous.
     */
    NETMAP(
            "netmap",
            "Network",
            "nmap / a topology view",
            "The network as a graph: your vantage, what a sweep has found, and the bridges out. Reach is a hard ceiling; a better sweep only finds quieter machines.",
            1100,
            780,
            720,
            480,
            KeyCode.DIGIT2,
            false,
            true,
            false),
    RECON(
            "recon",
            "Recon",
            "less",
            "What you have learned about a target, and what it would cost to learn more.",
            760,
            640,
            480,
            400,
            KeyCode.DIGIT3,
            false,
            true,
            false),
    AUDIT(
            "audit",
            "Audit",
            "ps / netstat / df",
            "Processes, connections and storage on your own rig. When these three disagree, something is hiding.",
            900,
            600,
            640,
            400,
            KeyCode.DIGIT4,
            false,
            true,
            false),
    MINING(
            "mining",
            "Mining",
            "a miner dashboard",
            "Self-mining allocation and deployed-miner collection. The income floor, and the only offline income.",
            820,
            600,
            560,
            400,
            KeyCode.DIGIT5,
            false,
            true,
            false),
    /**
     * Items across the three tiers.
     *
     * <p>⚠ Renamed from "Storage" to <b>VaultStore</b> on 2026-07-28, and the id stays
     * {@code storage} deliberately — the id is what saved desk layouts, accelerator bindings and
     * {@code window storage} in the shell are keyed on, and renaming it would silently discard every
     * player's remembered geometry for this window to gain nothing.
     *
     * <p>The Unix analogue changed with it: the tiers stopped being mount points at {@code /mnt} and
     * moved into {@code ~/.VaultStore}, because nobody mounted them and a {@code /mnt/vault} in the
     * sidebar of a machine an intruder is standing on is a signpost to the one place meant to be safe.
     */
    STORAGE(
            "storage",
            "VaultStore",
            "ls ~/.VaultStore",
            "Your items across the three tiers. Moving one changes how exposed it is.",
            840,
            620,
            560,
            420,
            KeyCode.DIGIT6,
            false,
            true,
            false),
    LEDGER(
            "ledger",
            "Ledger",
            "a transaction log",
            "Every ethecoin movement and what caused it. The audit trail for your own balance.",
            880,
            560,
            600,
            360,
            KeyCode.DIGIT7,
            false,
            true,
            false),
    BOTNET(
            "botnet",
            "Botnet",
            "jobs / systemctl",
            "Bot frames, their loadouts and what they are doing. Bots assist; they never solve it for you.",
            780,
            560,
            520,
            400,
            KeyCode.DIGIT8,
            false,
            true,
            false),
    DEFENSE(
            "defense",
            "Defense",
            "a firewall / IDS console",
            "What is armed and what it costs to keep armed. Defending your own rig never generates heat.",
            780,
            560,
            520,
            380,
            KeyCode.DIGIT9,
            false,
            true,
            false),
    // The core loop (docs/design/05). Given a letter rather than a digit because the digit row is
    // full, and B is the one accelerator a player will reach for without being told.
    BREACH(
            "breach",
            "Breach",
            "an exploit console",
            "The puzzle itself. Spend attention to clear a node's layers before the budget runs out; nothing here is on a clock.",
            1080,
            760,
            720,
            480,
            KeyCode.B,
            false,
            true,
            false),

    MARKET(
            "market",
            "Market",
            "a package manager",
            "What is for sale, what it costs, and which gate stands in front of it.",
            900,
            640,
            600,
            440,
            KeyCode.M,
            true,
            true,
            false),
    IDENTITY(
            "identity",
            "Identity",
            "whoami / id",
            "Your handle, faction standing and heat. Who the Eye thinks you are.",
            560,
            640,
            420,
            440,
            KeyCode.I,
            true,
            true,
            false),
    COMMS(
            "comms",
            "Comms",
            "mail / who",
            "Messages and contacts. Who is talking to you, and who can see that they did.",
            720,
            620,
            480,
            400,
            KeyCode.P,
            true,
            true,
            false),
    SETTINGS(
            "settings",
            "Settings",
            "~/.config",
            "Theme, teaching level, desk behaviour, notices, pointer and motion.",
            760,
            620,
            560,
            440,
            KeyCode.COMMA,
            false,
            true,
            false),

    /**
     * The rig log — a live stream of what the machine has been doing.
     *
     * <p>⚠ <b>A seventeenth window, and the third the catalogue documents did not anticipate.</b>
     * {@code docs/client/04} §3.10 specifies a {@code log} <em>command</em> mapped to
     * {@code journalctl -f}, and {@code docs/client/05} §5.2 reserves an alert tray — but those are
     * different surfaces. The tray is triage: rung-3 and rung-4 items with deadlines, sorted by time
     * remaining (§6.7). A log is history, unsorted and unfiltered, and it answers a question the tray
     * cannot: <em>what happened while I was not watching.</em>
     *
     * <p>{@code docs/design/04-mining.md} §3.1 needs that question answerable — reconstructing the
     * rig's recent past is how a player notices something that should not be there. Logged against
     * <b>WL-1</b> with {@code man}, since the catalogue's size is now three windows past what §2.1
     * lists.
     */
    LOG(
            "log",
            "Log",
            "journalctl -f",
            "What the rig has been doing, newest last — including everything that happened while you were away.",
            720,
            620,
            460,
            320,
            KeyCode.L,
            true,
            true,
            false),

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
    MAN(
            "man",
            "Manual",
            "man / apropos",
            "The offline manual. Every term the game uses, and what the real thing is called.",
            820,
            680,
            520,
            420,
            KeyCode.SLASH,
            false,
            true,
            false),

    /**
     * The programmer's calculator.
     *
     * <p>⚠ <b>A nineteenth window, and the fourth the catalogue documents did not anticipate.</b>
     * Logged against <b>WL-1</b> with {@code man}, {@code log} and {@code breach}.
     *
     * <p>It earns the slot on the teaching pillar rather than on a game system: <b>C6</b>, and
     * {@code docs/education/01-foundations.md}'s whole first domain — bases, bit width, two's
     * complement, byte order, overflow. Every other window in this client hands the player numbers
     * in the machine's notation and none of them can make those numbers legible; a player who cannot
     * move between hex, decimal and bits reads an address, a digest and a cycle figure as three
     * equally opaque strings.
     *
     * <p>It is also the <b>only window that takes no session</b>. It spends nothing, is gated by
     * nothing and cannot be lost — see {@code CalcView}. That is why its presence in the catalogue
     * costs the design nothing to justify: there is no invariant a calculator can touch.
     */
    CALC(
            "calc",
            "Calculator",
            "bc / printf %x / a programmer's calculator",
            "One value in hex, decimal, octal and binary at once, with its bits. Word width, two's complement, masks, shifts and byte order.",
            820,
            700,
            560,
            460,
            KeyCode.C,
            true,
            true,
            false),

    /**
     * The file manager.
     *
     * <p>⚠ A twentieth window, logged against <b>WL-1</b> with {@code man}, {@code log},
     * {@code breach} and {@code calc}. It is GNOME Files' shape — places sidebar, breadcrumb path
     * bar, detail list — because that is the arrangement an Ubuntu user already knows and this
     * window's purpose is that what a player learns in it transfers to a real machine.
     *
     * <p>⚠ The accelerator is <b>H</b>, for Home, and not F. {@code Shortcut+F} is per-window find
     * (§6.3) and {@code Shortcut+Shift+F} is already spoken for by {@code recon}'s search-all
     * (§2.6); taking either would break a documented binding to gain a better mnemonic.
     */
    FILES(
            "files",
            "Files",
            "nautilus / ls / mount",
            "Your rig's filesystem, and every machine you hold mounted onto it. Ubuntu's layout, because it is the one worth learning.",
            980,
            660,
            640,
            440,
            KeyCode.H,
            true,
            true,
            false),

    /** The answer to losing a window behind another. Opens on first run alongside the rig monitor. */
    SWITCHER(
            "switcher",
            "Windows",
            "jobs",
            "Every tool in the catalogue, open or not. The way back to a window you lost.",
            280,
            520,
            240,
            320,
            KeyCode.J,
            true,
            true,
            true);

    /** No window's minimum may exceed this. See the class comment. */
    public static final double MAX_MINIMUM_WIDTH = 720;

    public static final double MAX_MINIMUM_HEIGHT = 480;

    private final String id;
    private final String title;
    private final String unixAnalogue;
    private final String description;
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
            String description,
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
        this.description = description;
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
     * The message key for {@link #title()} — {@code window.rig-monitor.title}.
     *
     * <h2>⚠ Derived from the id, which is why it cannot drift</h2>
     *
     * The id is already the stable identifier: it keys saved desk layouts, so it is the one field
     * here that must never change. Deriving the key from it means a translation can never point at a
     * window that no longer exists, and a new window arrives with its keys already correct.
     *
     * <h2>⚠ English is NOT in the bundle</h2>
     *
     * {@link #title} above is the English, and {@code WindowSpecTest} asserts this table against
     * {@code docs/client/05} §2.1. A {@code windows_en.properties} would be a second English that
     * nothing keeps in step. Callers resolve through {@code Messages.overlay} with the enum's own
     * string as the fallback, so a locale that has not translated a window shows the English one.
     */
    public String titleKey() {
        return "window." + id + ".title";
    }

    /** The message key for {@link #description()}. See {@link #titleKey()}. */
    public String descriptionKey() {
        return "window." + id + ".description";
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

    /**
     * One sentence on what this tool is for.
     *
     * <p>Separate from {@link #unixAnalogue()}, which names the real command it stands in for. The
     * analogue teaches — a player who learns the audit window <em>is</em> {@code ps}, {@code netstat}
     * and {@code df} has learned three commands for free — but "ps / netstat / df" does not tell
     * somebody who has never used a shell what the window is <em>for</em>. The rail shows a single
     * accelerator character per tool, so without this the launcher is seventeen unlabelled keys.
     */
    public String description() {
        return description;
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
