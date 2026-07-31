package io.github.stoicswe.eyeandsickle.client.i18n;

import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;

/**
 * The one place the client asks for a translated string.
 *
 * <h2>Why a holder rather than a bundle per caller</h2>
 *
 * Every surface that needs text would otherwise call {@code Messages.load} for itself, which means
 * re-reading and re-parsing the properties files on every window open — and, worse, means a language
 * change has to find and update an unknown number of independently-loaded copies. One holder, loaded
 * once and replaced on a language change, makes "what language is the client in" a single fact.
 *
 * <h2>⚠ Never cache what this returns</h2>
 *
 * The same rule {@code profile.appearance()} has, for the same reason: the answer changes when the
 * player changes the setting. Ask at the moment of building the node, not once at construction and
 * stored in a field.
 *
 * <h2>⚠ Two bundles, because English lives in two different places</h2>
 *
 * {@code commands} is {@link Messages#load} — the properties file is the only place those sentences
 * exist. {@code windows} is {@link Messages#overlay} — {@link WindowSpec} carries its own English and
 * a test asserts it against {@code docs/client/05}, so a {@code windows_en.properties} would be a
 * second English nothing keeps in step. See {@code Messages.overlay} for the distinction in full.
 */
public final class Text {

    private static volatile Text current = new Text(Language.fallback());

    private final Language language;
    private final Messages commands;
    private final Messages windows;
    private final Messages ui;

    private Text(Language language) {
        this.language = language;
        this.commands = Messages.load("commands", language.tag());
        this.windows = Messages.overlay("windows", language.tag());
        this.ui = Messages.overlay("ui", language.tag());
    }

    /** The text in force. Ask every time; never hold the result. */
    public static Text current() {
        return current;
    }

    /**
     * Switches the client to a language.
     *
     * <p>⚠ Reloads rather than mutating, so a half-applied bundle cannot be observed by a thread
     * building a window while the switch happens. Windows already on screen keep the text they were
     * built with — {@code DeskManager} calls a view's factory afresh on every open, so reopening one
     * is enough, and the title of a live frame is retitled explicitly by the caller.
     */
    public static void use(Language language) {
        current = new Text(language == null ? Language.fallback() : language);
    }

    public Language language() {
        return language;
    }

    /** The command schema's prose: option and argument descriptions, menu headings. */
    public Messages commands() {
        return commands;
    }

    /** A tool window's title, as the player should read it. Falls back to the catalogue's English. */
    public String title(WindowSpec spec) {
        return windows.get(spec.titleKey(), spec.title());
    }

    /** A tool window's one-sentence description. Falls back to the catalogue's English. */
    public String description(WindowSpec spec) {
        return windows.get(spec.descriptionKey(), spec.description());
    }

    /**
     * A general interface string — a settings category, a button, a caption.
     *
     * @param key the message key
     * @param english what the code already says, used when nothing translates it
     */
    public String ui(String key, String english) {
        return ui.get(key, english);
    }

    /** Everything wrong with the loaded bundles, for the diagnostics surface. */
    public java.util.List<String> problems() {
        java.util.List<String> out = new java.util.ArrayList<>(commands.problems());
        out.addAll(windows.problems());
        out.addAll(ui.problems());
        return out;
    }
}
