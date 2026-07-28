package io.github.stoicswe.eyeandsickle.client.profile;

/**
 * How the deck <b>looks</b> — kept per character, not per install.
 *
 * <h2>Why appearance belongs to the character</h2>
 *
 * Three characters are three operators at three rigs, and the login screen already presents them
 * that way. A player who runs a cautious Sickle sympathiser and a reckless one gets no help at all
 * from both decks being the same green; a player who wants one character on the high-visibility
 * palette and another on Cyberdeck was, until this existed, choosing between them. The look is part
 * of the character in the same way the handle and the picture are — it is the thing that tells you
 * at a glance which one you are sitting at.
 *
 * <p>It also makes the setup assistant honest. It asks about the palette while creating a character,
 * so the answer should belong to that character; before this, pane four of the assistant quietly
 * re-themed every save on the machine.
 *
 * <h2>⚠ What is NOT here, and why</h2>
 *
 * <b>Text size ({@code uiScalePercent}) and reduce-motion stay on {@link ClientProfile.Settings}.</b>
 * They read as appearance and they are not: {@code docs/client/07} treats both as accessibility
 * <em>floors</em>. A player who needs 150% text needs it on the login screen, in the setup assistant
 * and on every character they ever make — per-character would hand them 100% every time they created
 * one, which is a regression aimed squarely at the people least able to absorb it.
 *
 * <p><b>{@code nativeWindowBorder} stays global</b> because {@code Stage.initStyle} is rejected on a
 * realised Stage: per-character it could not take effect until a restart, and a setting that
 * half-works is worse than one that does not.
 *
 * <p><b>{@code windowSize} and {@code fullScreen} stay global</b> — they are the window's geometry,
 * not the deck's look, and per-character they would resize the player's window every time they
 * switched save.
 *
 * <h2>⚠ Jackson binds these fields directly</h2>
 *
 * Public mutable fields, no getters, no constructor — the same shape as {@code Settings}, for the
 * same reason. A missing field on an older profile keeps its default, and unknown fields are ignored
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES} is off), so this type can gain and lose members without ever
 * costing a player their other preferences.
 */
public final class VisualSettings {

    /** Theme id, e.g. {@code deck} or {@code deck-hc}. Lowercase, per the glossary's convention. */
    public String themeId = "deck";

    /**
     * Which pointer to draw — {@code system}, {@code reticle}, {@code chevron} or {@code block}.
     *
     * <p>⚠ Defaults to {@code system}, and that is a floor rather than a placeholder: a pointer is
     * tuned by the player's OS for their display and their eyesight. It is per-character because it
     * is drawn in the palette's colours and reads as part of the look — but the default must stay
     * {@code system} on every new character, or moving it here would quietly turn an accessibility
     * default into something a player has to re-assert three times.
     */
    public String cursorSkin = "system";

    /** Machine texture behind every window: {@code drift}, {@code still} or {@code off}. */
    public String wallpaper = "drift";

    /** The drawn casing around the screen (§9.2). Off by default. */
    public String bezel = "off";

    /** Screen artefacts (§9.1). All three off by default; they cost real contrast on body text. */
    public boolean crtScanlines = false;

    public boolean crtAberration = false;

    public boolean crtGlitch = false;

    /** How far the rim aberration ramps towards the corners, 0–100. */
    public int crtCurvature = 0;

    /** §9.3's opt-in. Off by default — the shipped client is square-cornered. */
    public boolean roundedWindows = false;

    /** {@code system} | {@code macos} | {@code windows} — ORDER of a desk window's buttons only. */
    public String subwindowControlOrder = "system";

    /** A detached copy. Used to seed a new character from the menu's look. */
    public VisualSettings copy() {
        VisualSettings copy = new VisualSettings();
        copy.themeId = themeId;
        copy.cursorSkin = cursorSkin;
        copy.wallpaper = wallpaper;
        copy.bezel = bezel;
        copy.crtScanlines = crtScanlines;
        copy.crtAberration = crtAberration;
        copy.crtGlitch = crtGlitch;
        copy.crtCurvature = crtCurvature;
        copy.roundedWindows = roundedWindows;
        copy.subwindowControlOrder = subwindowControlOrder;
        return copy;
    }
}
