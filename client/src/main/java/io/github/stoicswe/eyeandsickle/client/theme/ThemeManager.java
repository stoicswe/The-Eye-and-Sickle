package io.github.stoicswe.eyeandsickle.client.theme;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.ui.Fonts;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

/**
 * Applies a theme to every Scene, and owns the reduced-motion decision.
 *
 * <h2>No AtlantaFX, and no user-agent stylesheet of our own</h2>
 *
 * {@code docs/design/ui-design-language.md} §0 drops AtlantaFX. What replaces it is <em>less</em>,
 * not more: {@code theme.css} is loaded as an ordinary Scene stylesheet, and Modena stays underneath
 * as the toolkit's user-agent sheet.
 *
 * <p>That layering is deliberate. Replacing the user-agent stylesheet would mean re-specifying every
 * control JavaFX ships, including the dozens this client never instantiates — four thousand lines to
 * own so that a {@code ColorPicker} nobody opens looks right. Author stylesheets outrank the
 * user-agent sheet in the CSS cascade, so overriding the controls the client actually uses achieves
 * the same visual result with a surface small enough to read. The CONTROLS section of
 * {@code theme.css} is that override set, and an unstyled control is instantly obvious: it renders
 * as a rounded light-grey Modena box against a near-black deck.
 *
 * <h2>Live switching, in two assignments</h2>
 *
 * A theme is {@code theme.css} plus an optional palette overlay ({@link ThemeId}), so switching is
 * removing two stylesheets and adding one or two. Nothing is rebuilt and no Scene is recreated — a
 * player comparing the deck against high-visibility should be able to flip back and forth and watch
 * the same numbers redraw.
 *
 * <h2>Reduced motion, and a correction to the design document</h2>
 *
 * §10 criterion 8 of the design language says "JavaFX cannot read the OS preference; expose it
 * explicitly and default it off." <b>The first half of that is wrong.</b>
 * {@code Platform.getPreferences().isReducedMotion()} exists, is observable, and is read here — the
 * client has honoured it since before the design language was written. The criterion's practical
 * advice still stands and is implemented: there is an explicit Settings toggle, and an explicit
 * choice overrides the system one in both directions.
 *
 * <p>Getting this right matters more than the usual documentation nit. Defaulting reduced motion off
 * while ignoring the OS setting means a player who has asked their whole system to stop animating
 * gets a greeble field regenerating every four seconds until they find a toggle.
 */
public final class ThemeManager {

    private final ClientProfile profile;
    private final ObjectProperty<ThemeId> current = new SimpleObjectProperty<>(ThemeId.DECK);
    private final List<Scene> scenes = new ArrayList<>();
    private boolean reducedMotion;

    public ThemeManager(ClientProfile profile) {
        this.profile = profile;
        this.current.set(ThemeId.byId(profile.appearance().themeId).orElse(ThemeId.DECK));
        this.reducedMotion = resolveReducedMotion();
        Fonts.load();
        Pulse.shared().setReducedMotion(reducedMotion);
    }

    public ObjectProperty<ThemeId> currentProperty() {
        return current;
    }

    public ThemeId current() {
        return current.get();
    }

    /** Whether animation is suppressed. An explicit Settings choice wins over the OS preference. */
    public boolean reducedMotion() {
        return reducedMotion;
    }

    /**
     * Sets the explicit override.
     *
     * @param reduced true or false to override the OS, null to follow it again
     */
    public void setReducedMotionOverride(Boolean reduced) {
        profile.settings().reducedMotionOverride = reduced;
        reducedMotion = resolveReducedMotion();
        Pulse.shared().setReducedMotion(reducedMotion);
    }

    private boolean resolveReducedMotion() {
        Boolean override = profile.settings().reducedMotionOverride;
        if (override != null) {
            return override;
        }
        try {
            return Platform.getPreferences().isReducedMotion();
        } catch (RuntimeException notAvailable) {
            // A headless or older toolkit may not expose preferences. Defaulting to "animate" is the
            // right failure direction: someone who needs reduced motion can set it explicitly, and
            // defaulting to suppressed would silently remove feedback for everyone else.
            return false;
        }
    }

    /** Registers a Scene so it receives this and every future theme. */
    public void adopt(Scene scene) {
        if (scene != null && !scenes.contains(scene)) {
            scenes.add(scene);
            applyTo(scene);
            io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().adopt(scene);
        }
    }

    public void forget(Scene scene) {
        scenes.remove(scene);
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().forget(scene);
    }

    /** Switches theme and persists the choice. */
    public void select(ThemeId id) {
        current.set(id);
        profile.appearance().themeId = id.id();
        applyAll();
    }

    /**
     * Re-reads the look after the profile has been pointed at a <b>different</b> one, then applies it.
     *
     * <h2>⚠ Why {@code applyAll()} alone is not enough</h2>
     *
     * This class caches the current {@link ThemeId} in a property, and {@code applyTo} paints from
     * that cache rather than from the profile. {@code select()} keeps the two in step because it
     * writes both. But {@code ClientProfile.useCharacterAppearance} swaps the entire
     * {@code VisualSettings} the profile points at — behind this class's back — so the cache is
     * suddenly describing the palette of whoever was loaded <em>before</em>. Calling
     * {@code applyAll()} then faithfully re-applies the wrong theme, and the symptom is a character
     * opening in the previous character's colours, which looks like the per-character setting having
     * failed to save.
     *
     * <p>Call this, not {@code applyAll()}, after any change to <em>which</em> look is in force.
     */
    public void reloadAppearance() {
        current.set(ThemeId.byId(profile.appearance().themeId).orElse(ThemeId.DECK));
        applyAll();
    }

    public void applyAll() {
        scenes.removeIf(scene -> scene.getRoot() == null);
        for (Scene scene : scenes) {
            applyTo(scene);
        }
        refreshCursors();
    }

    /**
     * Re-draws the pointer in the current palette.
     *
     * <p>Cursors are pixels, so they cannot be looked-up colours — they are drawn from colours read
     * back out of the live stylesheet ({@code ui/cursors/Palette}). That means a palette change has
     * to re-draw them, or the pointer stays sodium amber on a phosphor deck. Called from here rather
     * than from the cursor code so there is one place that knows a theme changed.
     */
    public void refreshCursors() {
        var skin = io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin
                .byId(profile.appearance().cursorSkin)
                .orElse(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.SYSTEM);
        List<String> sheets = scenes.isEmpty()
                ? List.of(resource(ThemeId.BASE_STYLESHEET))
                : List.copyOf(scenes.getFirst().getStylesheets());
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().select(skin, sheets);
    }

    private void applyTo(Scene scene) {
        scene.getStylesheets().removeIf(sheet -> sheet.contains("/client/ui/"));
        // Order is the mechanism, not an accident: the overlay redefines the same `.root` selector
        // at the same specificity, so it only wins because it is added second. Loading it first
        // would produce a client that is subtly the wrong colour and passes every test.
        scene.getStylesheets().add(resource(ThemeId.BASE_STYLESHEET));
        current().overlayStylesheet().map(ThemeManager::resource).ifPresent(scene.getStylesheets()::add);

        scene.getRoot().getStyleClass().removeIf(styleClass -> styleClass.startsWith("es-theme-"));
        scene.getRoot().getStyleClass().add("es-theme-" + current().id());
        if (!scene.getRoot().getStyleClass().contains("es-deck")) {
            scene.getRoot().getStyleClass().add("es-deck");
        }
    }

    private static String resource(String path) {
        var url = ThemeManager.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Stylesheet missing from the jar: " + path);
        }
        return url.toExternalForm();
    }

    /**
     * Subscribes to the OS reduced-motion preference.
     *
     * <p>Colour scheme is no longer followed: §0 removed the native family, so there is nothing for
     * a light system theme to switch to. Called once, after the toolkit is up; a platform that does
     * not expose preferences simply does not get live following, and the Settings toggle still works.
     */
    public void followSystemPreferences() {
        try {
            Platform.getPreferences().reducedMotionProperty().addListener((obs, was, now) -> {
                if (profile.settings().reducedMotionOverride == null) {
                    reducedMotion = now;
                    Pulse.shared().setReducedMotion(now);
                }
            });
        } catch (RuntimeException notAvailable) {
            // Nothing to follow. See resolveReducedMotion().
        }
    }
}
