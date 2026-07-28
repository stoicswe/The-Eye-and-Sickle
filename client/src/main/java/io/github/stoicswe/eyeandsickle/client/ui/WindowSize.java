package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The sizes the deck window can be set to, as common display resolutions.
 *
 * <h2>Why this has to exist at all</h2>
 *
 * The deck is one {@code StageStyle.UNDECORATED} Stage (§0), which means there is <b>no OS window
 * chrome</b> — and therefore no OS resize handle. Until this existed the window was created at a
 * hard-coded 1280×800 on every launch and the only sizes reachable were that one and maximised.
 * Drawing our own chrome bought the look and quietly took away a thing every other window on the
 * machine can do; this is the half of that trade that had not been paid.
 *
 * <h2>Why presets rather than a width and height field</h2>
 *
 * {@code docs/design/ui-design-language.md} §10 criterion 9 asks the layout to hold from 1280 to
 * 2560px, and §3 specifies a breakpoint at 900. Those are the numbers worth testing against, and a
 * free-text pair of boxes invites the sizes between them that nobody has ever looked at. A player
 * who wants an arbitrary size still has one — drag the window to an edge, or maximise it.
 *
 * <h2>⚠ JavaFX-free on purpose</h2>
 *
 * Same reason as {@link WallpaperMode} and {@code cursors/CursorSkin}: it can be read, persisted and
 * tested without a toolkit. {@code UiContractTest}'s own comment rules the alternative out — "a
 * contract test that only runs on a machine with a display is a contract test that does not run in
 * CI". {@link #fitsOnScreen} is the whole clamping rule and is exercised without ever opening a Stage.
 */
public enum WindowSize {

    /** The size the client shipped at, and still the default. */
    HD_1280("1280x800", 1280, 800, "The default. Fits every laptop the deck is supported on."),

    /** The most common laptop panel there is, and shorter than the default. */
    WXGA("1366x768", 1366, 768, "Common laptop panel. Short — the desk gets less height."),

    HD_PLUS("1600x900", 1600, 900, "Roomy without leaving a 1080p screen."),

    FULL_HD("1920x1080", 1920, 1080, "Fills a 1080p display."),

    QHD("2560x1440", 2560, 1440, "The widest the layout is tested at (§10 criterion 9)."),

    UHD("3840x2160", 3840, 2160, "4K. Pair it with UI scaling or the text is very small.");

    private final String id;
    private final int width;
    private final int height;
    private final String note;

    WindowSize(String id, int width, int height, String note) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.note = note;
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** One sentence of what this is for, shown in Settings. */
    public String note() {
        return note;
    }

    /** {@code 1920 × 1080}. The separator is a real multiplication sign, not a letter x. */
    public String label() {
        return width + " × " + height;
    }

    /**
     * The narrowest the deck is supported at, in <b>logical</b> pixels.
     *
     * <p>{@code ui-design-language.md} §10 criterion 9 asks the layout to hold from 1280 to 2560px.
     * The floor is lower than that because §3 specifies what happens below 900px — the rail hides
     * and the main area collapses to one column — and a minimum that forbade reaching that
     * breakpoint would make the responsive behaviour unreachable and therefore untestable.
     *
     * <p>⚠ Lives here rather than in the application class because it is half of
     * {@link #meetsMinimum}'s rule, and a second copy of the floor is how a preset gets offered that
     * the Stage minimum then refuses to honour.
     */
    public static final double MIN_DECK_WIDTH = 860;

    public static final double MIN_DECK_HEIGHT = 560;

    /**
     * Whether the window this viewport needs fits a display with this much usable room.
     *
     * <h2>⚠ The resolution is the VIEWPORT's, so the window is bigger than it (2026-07-27)</h2>
     *
     * The casing is a machine around a screen and sits <em>outside</em> the picture, so choosing
     * 1920 × 1080 gives the deck 1920 × 1080 and puts the casing beyond it. The window the desktop
     * has to find room for is therefore {@code (resolution + 2 × casing) × scale} — all three terms,
     * and leaving any one out is a size that gets silently clamped.
     *
     * <p>Usable, not total: a 1440p display with a menu bar and a dock has less than 2560 × 1440 to
     * give, and a window sized past the visual bounds of an undecorated Stage has no OS chrome to
     * drag it back into view with.
     *
     * @param scale the UI scale as a factor, where 1.0 is 100%
     * @param casingMargin {@code BezelStyle.margin()} — one side, doubled here
     */
    public boolean fitsOnScreen(
            double usableWidth, double usableHeight, double scale, int casingMargin) {
        double factor = scale <= 0 ? 1 : scale;
        double chrome = 2 * casingMargin;
        return (width + chrome) * factor <= usableWidth
                && (height + chrome) * factor <= usableHeight;
    }

    /**
     * Whether the viewport itself clears the supported layout floor.
     *
     * <h2>⚠ This no longer depends on the UI scale, and that is the point of the change</h2>
     *
     * It used to: the deck was laid out at {@code physical / scale} inside a window sized to the
     * resolution, so 1280 × 800 at 150% gave the deck 853 logical pixels and fell under the floor.
     * Now the window is sized <em>from</em> the viewport, so the deck gets exactly the chosen
     * resolution in layout units whatever the scale — the scale changes how large those pixels are
     * drawn, not how many there are. Every preset therefore clears the floor at every scale, and the
     * only remaining constraint is whether the resulting window fits the display
     * ({@link #fitsOnScreen}).
     *
     * <p>Kept as a guard rather than deleted: it is what stops a future preset being added below the
     * layout's supported minimum without anyone noticing.
     */
    public boolean meetsMinimum() {
        return width >= MIN_DECK_WIDTH && height >= MIN_DECK_HEIGHT;
    }

    public static List<WindowSize> selectable() {
        return List.of(values());
    }

    /**
     * Looks up a persisted id.
     *
     * <p>Empty rather than an exception on an unknown value, so a profile written by a client with
     * one more preset than this one still loads — the caller falls back to a default instead of the
     * player losing their settings file to an enum constant.
     */
    public static Optional<WindowSize> byId(String id) {
        return Arrays.stream(values()).filter(size -> size.id.equals(id)).findFirst();
    }
}
