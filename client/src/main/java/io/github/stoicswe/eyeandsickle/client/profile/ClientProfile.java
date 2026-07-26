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

    /** System property that relocates the whole profile directory. */
    public static final String PROFILE_DIR_PROPERTY = "eyeandsickle.profile";

    private static final String APP_NAME = "The Eye and Sickle";
    private static final String XDG_NAME = "the-eye-and-sickle";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final Path directory;
    private final Path settingsFile;
    private Settings settings;

    public ClientProfile(Path directory) {
        this.directory = directory;
        this.settingsFile = directory.resolve("settings.json");
        this.settings = readSettings();
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
        } catch (IOException e) {
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

        /** Theme id, e.g. {@code native} or {@code uos-amber}. Lowercase, per the glossary's convention. */
        public String themeId = "native";

        /**
         * {@code explain} | {@code terms} | {@code off} — {@code docs/client/04} §3.10's {@code teach}
         * command. Defaults to {@code explain}, which is right for the audience the education goal
         * targets and probably wrong for a player who already knows Unix; CL-4 / T-2 tracks that.
         */
        public String teachingLevel = "explain";

        /** Set from the OS where possible; an explicit choice here wins over the system preference. */
        public Boolean reducedMotionOverride = null;

        /** The single-window docked layout, which {@code docs/client/07} treats as a first-class equal. */
        public boolean dockedLayout = false;

        /** Window id → geometry. Restored on open, and sanity-checked against current screens. */
        public Map<String, WindowGeometry> windows = new LinkedHashMap<>();

        /** Which tool windows were open when the client last exited. */
        public Map<String, Boolean> openWindows = new LinkedHashMap<>();

        /** Handle used for the solo character, so a returning player is not asked twice. */
        public String soloHandle = "";
    }

    /** A remembered window position. */
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
