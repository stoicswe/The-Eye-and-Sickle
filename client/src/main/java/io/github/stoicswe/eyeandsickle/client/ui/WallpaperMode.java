package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the desk wallpaper is doing: off, still, or drifting.
 *
 * <h2>Why this is a three-state enum and not a boolean</h2>
 *
 * <b>WCAG 2.2.2 (Pause, Stop, Hide)</b> requires that moving content which starts automatically and
 * runs for more than five seconds can be paused by the user. A wallpaper behind the entire interface
 * is exactly that, so a pause has to exist. Folding it into "off" would satisfy the letter and lose
 * the point: a player who wants the texture but not the movement would have to give up both, and the
 * texture is what {@code docs/design/ui-design-language.md} §4 protects as "the single largest
 * difference between this look and a dashboard".
 *
 * <h2>Why it is a top-level enum rather than nested in the widget</h2>
 *
 * So it can be read, persisted and tested <em>without a JavaFX toolkit</em>. {@code Substrate}
 * extends {@code Region}, and touching it from a unit test would drag in a live toolkit — which
 * {@code UiContractTest}'s own comment rules out: "a contract test that only runs on a machine with
 * a display is a contract test that does not run in CI." Same shape and same reason as
 * {@code ui/cursors/CursorSkin}.
 */
public enum WallpaperMode {
    OFF("off", "Off", "Bare desk. No texture behind the windows at all."),
    STILL("still", "Still", "The texture is drawn once and never moves."),
    DRIFT("drift", "Drifting", "The texture drifts a character at a time, slowly.");

    private final String id;
    private final String label;
    private final String note;

    WallpaperMode(String id, String label, String note) {
        this.id = id;
        this.label = label;
        this.note = note;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** One sentence of what this does, shown in Settings and returned by the `wallpaper` command. */
    public String note() {
        return note;
    }

    /**
     * Looks up a persisted id.
     *
     * <p>Returns empty rather than throwing on an unknown value, so a profile written by a client
     * that has one more mode than this one still loads — the caller falls back to a default instead
     * of the player losing their settings file to an enum constant.
     */
    public static Optional<WallpaperMode> byId(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }

    public static List<WallpaperMode> selectable() {
        return List.of(values());
    }
}
