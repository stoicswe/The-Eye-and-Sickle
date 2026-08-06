package io.github.stoicswe.eyeandsickle.client.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the client keeps its settings, its window geometry and its save.
 *
 * <h2>Platform-correct locations, because the alternative is litter</h2>
 *
 * Each desktop has a convention for where an application may write, and a game that drops a dotfile
 * in {@code $HOME} on every platform is one that has not looked. The rules used here:
 *
 * <ul>
 *   <li><b>macOS</b> — {@code ~/Library/Application Support/The Eye and Sickle}
 *   <li><b>Windows</b> — {@code %APPDATA%\The Eye and Sickle}, falling back to the user home
 *   <li><b>Linux and the BSDs</b> — {@code $XDG_DATA_HOME} or {@code ~/.local/share}, per the XDG
 *       Base Directory specification, which is what a Linux player expects and what backup tools
 *       already know about
 * </ul>
 *
 * <p>{@code docs/client/00-client-overview.md} §4.5 makes this directory the <em>only</em> host
 * filesystem the client ever touches, and §7 makes that a security boundary rather than a scope
 * decision: the terminal is a game surface over a virtual namespace, and nothing a player types can
 * reach a real path. That rule is only meaningful if there is exactly one place real I/O happens, and
 * this is it.
 *
 * <h2>Overridable, for the obvious reasons</h2>
 *
 * {@code -Deyeandsickle.profile=/some/path} relocates everything. That exists for tests, for portable
 * installs on a USB stick, and for anyone who keeps their home directory on a network share and would
 * rather the game did not.
 */
public final class ClientProfile {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ClientProfile.class.getName());

    /** System property that relocates the whole profile directory. */
    public static final String PROFILE_DIR_PROPERTY = "eyeandsickle.profile";

    private static final String APP_NAME = "The Eye and Sickle";
    private static final String XDG_NAME = "the-eye-and-sickle";

    // Unknown properties are ignored on purpose. Settings files outlive the code that wrote them: a
    // player who downgrades, or who kept a profile across a release that removed a setting, would
    // otherwise hit the catch below and silently lose every preference they had — theme, teaching
    // level, window positions — because of one field the build no longer knows about.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path directory;
    private final Path settingsFile;
    private Settings settings;

    /**
     * The look currently in force.
     *
     * <p>⚠ A POINTER into {@link Settings}, not a copy. It is the menu's appearance before a
     * character is loaded and that character's afterwards, and everything that draws — the theme
     * manager, the deck, the settings panel — reads it through {@link #appearance()} rather than
     * knowing which of the two it has. That is what stops "which look is this" being a question
     * asked in twelve places and answered differently in one of them.
     */
    private VisualSettings active;

    public ClientProfile(Path directory) {
        this.directory = directory;
        this.settingsFile = directory.resolve("settings.json");
        this.settings = readSettings();
        this.active = settings.appearance;
    }

    /** The conventional profile directory for this platform, unless overridden. */
    public static ClientProfile discover() {
        return new ClientProfile(defaultDirectory());
    }

    static Path defaultDirectory() {
        String override = System.getProperty(PROFILE_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", APP_NAME);
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, APP_NAME);
            }
            return Path.of(home, APP_NAME);
        }
        // Linux, the BSDs, and anything else that follows the XDG spec.
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, XDG_NAME);
        }
        return Path.of(home, ".local", "share", XDG_NAME);
    }

    public Path directory() {
        return directory;
    }

    /** Where the single-player save lives. */
    public Path saveFile() {
        return directory.resolve("solo-save.json");
    }

    public Settings settings() {
        return settings;
    }

    /**
     * The look currently in force — the menu's, or the loaded character's.
     *
     * <p>Never cached by a caller. A character load swaps what this returns, and a widget holding
     * the previous one would keep painting the previous character's palette.
     */
    public VisualSettings appearance() {
        return active;
    }

    /** Uses the menu's look: the splash, the login screen, and Settings with no game loaded. */
    public void useMenuAppearance() {
        this.active = settings.appearance;
    }

    /**
     * Uses a solo slot's look, creating it from the menu's on first use.
     *
     * <p>That first-use copy is the migration: every character that existed before appearance
     * became per-character simply keeps the look the machine already had.
     */
    public void useCharacterAppearance(int slot) {
        this.active = settings.appearanceFor(slot);
    }

    /**
     * Uses a detached look that belongs to nothing yet.
     *
     * <p>⚠ For the setup assistant, and only for it. It previews a palette on a character that does
     * not exist, so its edits must not land in {@link Settings} at all until the character is
     * created — which is what makes cancelling free rather than something that has to be undone.
     */
    public void usePendingAppearance(VisualSettings pending) {
        this.active = pending;
    }

    // ------------------------------------------------------------------ persistence

    private Settings readSettings() {
        if (!Files.isRegularFile(settingsFile)) {
            return new Settings();
        }
        try {
            Settings loaded = MAPPER.readValue(Files.readString(settingsFile), Settings.class);
            return loaded == null ? new Settings() : loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + settingsFile, e);
        } catch (RuntimeException e) {
            // Settings are not precious: a corrupt file means the player loses their window
            // positions, not their character. Starting from defaults is friendlier than refusing to
            // launch, which is the opposite of the call SaveStore makes and for the opposite reason.
            return new Settings();
        }
    }

    /** Writes settings atomically. Same reasoning as the save file: a torn write loses real work. */
    public void save() {
        try {
            Files.createDirectories(directory);
            Path tmp = settingsFile.resolveSibling("settings.json.tmp");
            Files.writeString(tmp, MAPPER.writeValueAsString(settings));
            try {
                Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
            // ⚠ AFTER the move, not before the write. The command strip's drive lamp reports work
            // that actually reached the disk; lighting it on entry would make it a statement of
            // intent, and it would flash on exactly the writes that then threw below.
            io.github.stoicswe.eyeandsickle.client.DiskActivity.wrote();
            // ⚠ FINE. Settings are written on every window move and every 30-second autosave; at
            // INFO this one line would be most of the log.
            LOG.log(java.util.logging.Level.FINE, "settings written to {0}", settingsFile);
        } catch (IOException e) {
            // ⚠ Logged AND rethrown. The throw is the caller's problem to handle; the log is so that
            // a player whose settings silently stop persisting has something to send in. Losing
            // either half loses a different question's answer.
            LOG.log(java.util.logging.Level.SEVERE, "could not write " + settingsFile, e);
            throw new UncheckedIOException("Could not write " + settingsFile, e);
        }
    }

    /**
     * Everything that persists across sessions.
     *
     * <p>{@code docs/client/00-client-overview.md} §4.5 fixes this list: the chosen theme, the
     * teaching level, and window geometry. Nothing here is game state — a settings file that could
     * change what a player owns would be a save file wearing a disguise.
     */
    public static final class Settings {

        /**
         * How the deck looks <b>right now, with no character loaded</b> — and the seed for the next
         * one created.
         *
         * <p>The firmware splash, the login screen and the setup assistant all render before any
         * character exists, so something has to own their appearance. This does. A new character's
         * look starts as a copy of it, which means a player who sets up their machine once and then
         * makes a second character does not have to set it up again.
         */
        public VisualSettings appearance = new VisualSettings();

        /**
         * One look per solo slot.
         *
         * <p>⚠ Keyed by slot number <b>as a string</b>, because JSON object keys are strings and an
         * integer key would round-trip through Jackson as one anyway. Use
         * {@link #appearanceFor(int)} rather than touching the map: it creates a slot's entry from
         * {@link #appearance} on first use, which is what silently and correctly gives every
         * character that existed before this split the look the machine already had.
         *
         * <p>⚠ {@code CharacterSlots.delete} removes the entry. A slot is reused, and inheriting a
         * deleted character's palette is the kind of ghost nobody can explain.
         */
        public java.util.Map<String, VisualSettings> characterAppearance = new java.util.LinkedHashMap<>();

        /** The look belonging to a solo slot, created from the menu's on first use. */
        public VisualSettings appearanceFor(int slot) {
            return characterAppearance.computeIfAbsent(String.valueOf(slot), key -> appearance.copy());
        }

        /** Forgets a slot's look. Called when the character in it is deleted. */
        public void forgetAppearance(int slot) {
            characterAppearance.remove(String.valueOf(slot));
        }

        /**
         * {@code explain} | {@code terms} | {@code off} — {@code docs/client/04} §3.10's {@code teach}
         * command. Defaults to {@code explain}, which is right for the audience the education goal
         * targets and probably wrong for a player who already knows Unix; CL-4 / T-2 tracks that.
         */
        public String teachingLevel = "explain";

        /** Set from the OS where possible; an explicit choice here wins over the system preference. */
        public Boolean reducedMotionOverride = null;

        /**
         * Which pointer to draw — {@code system}, {@code reticle}, {@code chevron} or {@code block}.
         *
         * <p>Defaults to {@code system}, and that is a deliberate floor rather than a placeholder. A
         * pointer is tuned by the player's OS for their display and their eyesight, and some people
         * run a deliberately enlarged or high-contrast one; replacing it by default would be an
         * accessibility regression dressed as art direction. The custom skins are opt-in.
         */
        /** Master switch for the slide-in notices. Everything they say stays in the log either way. */
        public boolean notificationsEnabled = true;

        /**
         * RFC 5424 severity floor for notices — {@code journalctl -p} semantics.
         *
         * <p>⚠ <b>The numbering runs backwards</b>: 0 is Emergency and 7 is Debug, so a LOWER number
         * is a stricter filter. 5 (notice) is the default because that is where the rig's
         * "something finished" and "something is armed" lines sit; 6 would add per-allocation info
         * chatter, and 4 would show only warnings and worse.
         *
         * <p>This is the same number the {@code log} command takes, deliberately — a player who sets
         * 4 here and types {@code log -p 4} sees the same set, and the habit transfers to any Linux
         * box they ever touch.
         */
        public int notifyMinSeverity = 5;

        /**
         * Facilities the player has muted — {@code mining}, {@code defense}, {@code scan},
         * {@code compute}, {@code storage}, {@code rig}.
         *
         * <p>Facility rather than an invented "category" vocabulary, so what is muted here is
         * nameable in the terminal too.
         */
        public java.util.List<String> mutedFacilities = new java.util.ArrayList<>();

        /**
         * The single-window deck — now the only layout, and no longer a choice.
         *
         * <p>Kept as a field so that settings files written before 2026-07-26 still deserialise;
         * nothing reads it. {@code docs/design/ui-design-language.md} §0 cancelled the
         * separate-{@code Stage}-per-tool model outright, and what replaced it is not the old docked
         * layout either — it is one undecorated Stage containing a real in-game window manager
         * ({@code ui/chrome/DeskManager}), which is strictly more capable than both. Multi-window
         * survives in §0 only as an opt-in multi-monitor feature, which is not built.
         *
         * @deprecated superseded by the deck; retained only for backward-compatible deserialisation
         */
        @Deprecated
        public boolean dockedLayout = true;

        /**
         * Free-drag windows instead of snapping them to a grid.
         *
         * <p>{@code docs/design/ui-design-language.md} §11 question 1 asks which of these the desk
         * should do and answers "prototype both". Both are built; this is the switch. Snapping is
         * the default because it reinforces the character-cell language the rest of the interface is
         * drawn on, and because it is what makes edge-tiling — dragging a window against a side of
         * the desk to fill that half — available at all.
         */
        public boolean freeDragWindows = false;

        /**
         * Whether Bandwidth caps how many tool windows can be open at once.
         *
         * <p><b>[PROPOSAL]</b> — {@code docs/design/ui-design-language.md} §8 wants the desk to be a
         * mechanic rather than a skin, and this is that mechanic. Defaulted <b>off</b> because the
         * cap is not calibrated: a starting rig has {@code bandwidth = 1}
         * ({@code docs/design/11-rig-infrastructure.md} §2), and the arithmetic that turns that into
         * a usable window budget is invented rather than derived. Logged as <b>UI-2</b>.
         */
        public boolean bandwidthCapsWindows = false;

        /**
         * How big the deck window is, as a {@code ui/WindowSize} id.
         *
         * <p>⚠ There is no OS chrome on the deck (§0), so there is no OS resize handle either. Until
         * this existed the window was created at a hard-coded 1280×800 on every launch and the only
         * other size reachable was maximised. Stored by id rather than as a width/height pair so a
         * preset can be re-tuned without every profile carrying the old number.
         */
        public String windowSize = "1280x800";

        /**
         * How large the interface is drawn, as a percentage. 100 is the shipped look.
         *
         * <p>An {@code int} rather than a double for the same reason {@code VisualSettings.crtCurvature} is: it is
         * a percentage a player picks off a list, and a float in a settings file invites a value
         * like {@code 1.2500000000000002} that no control can ever show as selected.
         *
         * <p>⚠ Not independent of {@link #windowSize} — the deck is laid out at
         * {@code physical / scale}, so a large scale in a small window falls under the supported
         * minimum. {@code WindowSize.usableAt} is the rule and Settings disables what fails it.
         */
        /** The fastest AnonShare may poll — see {@code stockRefreshSeconds}. */
        public static final int STOCK_REFRESH_MIN = 15;

        /** The slowest. Beyond a few minutes the panel stops feeling connected to anything. */
        public static final int STOCK_REFRESH_MAX = 600;

        public int uiScalePercent = 100;

        /**
         * Which language the interface is in — an IETF tag, {@code en} by default.
         *
         * <h2>⚠ Machine-wide, not per character, and for the same reason as {@link #uiScalePercent}</h2>
         *
         * Appearance is per character ({@code Settings.characterAppearance}) because a palette is a
         * costume. A language is not: it is whether the player can read the game. Per-character would
         * hand somebody who needs Deutsch an English client on every new character they start, which
         * is the accessibility floor {@code docs/client/07} draws for text size and motion, and this
         * sits on the same side of that line.
         *
         * <p>⚠ Stored as the <b>tag</b> rather than the enum, because this file outlives the build
         * that wrote it. A tag naming a language we later removed reads back as unknown and falls to
         * English — {@code Language.ofTag} returns empty rather than throwing for exactly that.
         *
         * <p>⚠ Blank means "never chosen", which is <em>not</em> the same as "chose English". The
         * first is free to follow the host's language; the second must be obeyed even on a German
         * machine. {@code Language.hostDefault()} resolves the first case, once.
         */
        public String language = "";

        /**
         * Which quote service AnonShare uses, by {@code StockProvider} name.
         *
         * <p>⚠ MACHINE-WIDE, not per character — it is a credential and a preference about this
         * installation, the same line {@link #language} and {@link #uiScalePercent} sit on. A
         * per-character key would ask a player to paste it again for every new character.
         */
        public String stockProvider = "";

        /**
         * The player's own API key. Blank means the offline feed.
         *
         * <h2>⚠ THE PLAYER'S KEY, IN THE PLAYER'S SETTINGS FILE</h2>
         *
         * It is not encrypted and is not pretending to be: it sits in a file on their own machine
         * alongside a save they can edit freely, and anything else here would be theatre against
         * somebody who already owns the disk. What matters is that <b>it is never logged and never
         * leaves the machine except to the provider it belongs to</b> — the URL that carries it is
         * kept out of every log line for that reason.
         */
        public String stockApiKey = "";

        /**
         * How often AnonShare asks for a fresh price, in seconds.
         *
         * <h2>⚠ SLOW BY DEFAULT, and that is the point</h2>
         *
         * A share price is not the Shadow Market: it moves on a scale of minutes and the panel is
         * something a player glances at rather than watches. Every refresh also spends one of a
         * budget the player is paying for out of their own free-tier allowance — at
         * {@link #STOCK_REFRESH_MIN} seconds a single symbol would use several hundred calls a day,
         * which is more than one provider's entire daily quota.
         */
        public int stockRefreshSeconds = 60;

        /**
         * Ticker symbols the search has learned, so the universe grows across sessions.
         *
         * <h2>⚠ MACHINE-WIDE reference data, not per character</h2>
         *
         * A symbol is the same symbol for everybody. Storing it per character would make each new
         * one start from the bundled fifty and re-spend the player's API allowance discovering
         * exactly what the last character already found.
         *
         * <p>⚠ Bounded. Persisted in the same settings file as everything else, and a player who
         * searched for years would otherwise turn it into a symbol table.
         */
        public java.util.Map<String, String> discoveredSymbols = new java.util.LinkedHashMap<>();

        /** The most discovered symbols kept. Beyond this the oldest are dropped. */
        public static final int DISCOVERED_LIMIT = 500;

        /**
         * Whether the client tells Discord roughly what the player is doing.
         *
         * <h2>⚠ OFF, and it is the default that makes the feature defensible</h2>
         *
         * Rich presence is broadcast to everyone on the player's friends list through a third
         * party's servers. {@code docs/client/00-client-overview.md} §7 amended its
         * <b>"not a telemetry client"</b> non-goal to admit exactly this, on exactly these terms —
         * opt-in, off by default, to a program the player already runs, on their own account, with
         * nothing reaching this project. Defaulting it on would break every clause of that at once.
         *
         * <p>⚠ What may be said is a closed set of constants in
         * {@code client/presence/PresenceState} — never the handle, the balance, a machine or an
         * item — and that is enforced by construction rather than by care at the call sites.
         *
         * <h2>⚠ MACHINE-WIDE, not per character</h2>
         *
         * The same line {@link #language}, {@link #stockProvider} and {@link #uiScalePercent} sit
         * on. Appearance is per character because a palette is a costume; this is a decision about
         * what this installation is allowed to tell other people, and asking a player to re-consent
         * on every new character would be asking until they said yes.
         */
        public boolean discordPresenceEnabled = false;

        /**
         * Whether the deck takes the whole screen.
         *
         * <p><b>Off by default, and deliberately.</b> Full screen on macOS moves the window to its
         * own Space and hides the menu bar; a client that did that uninvited on first launch would
         * have taken over the display before the player had seen it once. It is also the one window
         * state that cannot be undone with the mouse alone here, because the deck draws its own
         * chrome and a hidden menu bar leaves nothing else to click.
         */
        public boolean fullScreen = false;

        /**
         * Window id → OS-window geometry, from the {@code Stage}-per-tool era.
         *
         * @deprecated superseded by {@link #deskLayout}; retained so older profiles still
         *     deserialise. Nothing reads it.
         */
        @Deprecated
        public Map<String, WindowGeometry> windows = new LinkedHashMap<>();

        /**
         * Which tool windows were open when the client last exited.
         *
         * @deprecated superseded by {@link #deskLayout}, which records position and size too
         */
        @Deprecated
        public Map<String, Boolean> openWindows = new LinkedHashMap<>();

        /**
         * The desk exactly as the player left it — which windows, where, how big, and in what state.
         *
         * <p>Insertion-ordered, and the order is load-bearing: it is the order windows were opened
         * in, which the desk replays so z-order and the tiling arrangement come back the same way.
         *
         * <p>Empty means a genuinely fresh profile, and only then does the desk tile a starting set.
         * Distinguishing "no saved layout" from "a saved layout that happens to be one window" is
         * the whole reason this is a separate map rather than a repurposing of the two above.
         */
        public Map<String, DeskWindowState> deskLayout = new LinkedHashMap<>();

        /** Handle used for the solo character, so a returning player is not asked twice. */
        public String soloHandle = "";

        /**
         * What the rig calls itself on the network — the {@code host} half of the shell prompt.
         *
         * <p>A client setting rather than game state, and it belongs here for the same reason the
         * theme does: it changes what the player sees and nothing a cheater would want. There is no
         * rule anywhere that reads it, no gate that depends on it and no ledger entry that records
         * it, which is why editing this file to say {@code eye-central} costs a player exactly the
         * novelty of having done so.
         *
         * <p>Stored bare, without the {@code .local} suffix the prompt appends — see
         * {@link Hostname#sanitise}.
         */
        public String rigHostname = Hostname.DEFAULT;

        /**
         * Whether every window in the game is drawn with rounded corners.
         *
         * <p>Permitted by {@code docs/design/ui-design-language.md} §9.3, amended 2026-07-28 to allow more
         * user controllability — the same direction §9.1 already took for screen artefacts.
         *
         * <p>⚠ <b>Off by default</b>, because the failure §1 names is <em>a competent dark-mode
         * developer tool</em> and hard edges are most of what keeps this deck from being one. A player
         * who prefers soft corners on their own screen is not a design problem; a shipped default that
         * drifts toward the generic is. What it must never round is anything a <em>measurement</em> is
         * read off — see §9.3.
         */
        /**
         * Whether the game window wears the operating system's own frame instead of drawing its own.
         *
         * <p>⚠ <b>Off by default, and this is the one setting that contradicts §0 outright.</b>
         * {@code docs/design/ui-design-language.md} §0 cancelled the {@code Stage}-per-tool model on the
         * grounds that "the entire aesthetic depends on the player never seeing their own operating
         * system", and §10 criterion 1 makes no visible OS chrome an acceptance criterion. Turning this
         * on gives the player back a real title bar, real traffic lights, and a window that looks like
         * every other window on their desk.
         *
         * <p>It is offered under §0.1 (amended 2026-07-28) for the same reason §9.1 permits screen
         * artefacts and §9.3 permits rounded corners: it is the player's machine. What matters is that
         * the <b>default</b> still describes the game — a player who never opens Settings sees the deck
         * the design language specifies.
         *
         * <p>⚠ Needs a restart, unavoidably. {@code initStyle} is rejected on a realised Stage, and
         * {@code DECORATED} and {@code TRANSPARENT} cannot both be true of one window.
         */
        public boolean nativeWindowBorder = false;

        /**
         * The order of {@code [×] [−] [+]} on windows inside the game — {@code system}, {@code macos}
         * or {@code windows}.
         *
         * <p>⚠ <b>Order only, and desk windows only.</b> It never changes which side the controls are
         * on — that is a platform convention the outer window follows unconditionally — and it never
         * touches the outer window, which sits beside the player's real windows and is judged against
         * them. See {@code ui/chrome/ControlOrder}.
         *
         * <p>Defaults to {@code system}: whatever this computer does, because that is the arrangement
         * the player's hand already knows.
         */
        /** Which solo slot was last played, so the menu can pre-select it. 1-based; 0 means none. */
        public int lastSoloSlot = 0;

        /**
         * Home servers the player has named, most recent first.
         *
         * <p>Addresses only. No credentials and no tokens are ever written here — the profile is a
         * plain unencrypted JSON file in a conventional location, which is the correct place for a
         * window position and the wrong place for anything that grants access to an account.
         */
        public java.util.List<String> knownServers = new java.util.ArrayList<>();

        /**
         * Whether the first-run familiarity question has been answered.
         *
         * <p>{@code CL-4 / T-2}: the teaching layer defaults to {@code explain}, which is right for
         * the audience the education goal targets and probably wrong for a player who already knows
         * Unix. The obvious answer is to ask once — and the main menu is where that costs nothing,
         * because a player is already stopped there deciding something.
         */
        public boolean askedFamiliarity = false;
    }

    /** A remembered window position. */
    /**
     * One tool window's place on the desk.
     *
     * <p>Public mutable fields with a no-arg constructor, like every other persisted type here —
     * Jackson binds them directly and a missing field on an older profile simply keeps its default
     * rather than failing the load.
     */
    public static final class DeskWindowState {

        public double x;
        public double y;
        public double width;
        public double height;

        /** Collapsed to the rail. Restored collapsed, because that is where the player put it. */
        public boolean minimized;

        /**
         * Maximised or edge-tiled — i.e. sitting somewhere the desk computed rather than somewhere
         * the player placed it.
         */
        public boolean expanded;

        /**
         * Where an expanded window returns to on double-click.
         *
         * <p>Persisted so the restore still works after a reload. Without it, a player who quits
         * with a window maximised comes back to a maximised window that has forgotten what it was,
         * and double-clicking hands them an arbitrary default instead of their layout.
         */
        public double restoreX;

        public double restoreY;
        public double restoreWidth;
        public double restoreHeight;

        public DeskWindowState() {}
    }

    public static final class WindowGeometry {
        public double x;
        public double y;
        public double width;
        public double height;
        public boolean maximized;

        public WindowGeometry() {}

        public WindowGeometry(double x, double y, double width, double height, boolean maximized) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maximized = maximized;
        }
    }
}
