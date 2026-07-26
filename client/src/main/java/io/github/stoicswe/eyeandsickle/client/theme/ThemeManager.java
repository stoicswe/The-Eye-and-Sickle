package io.github.stoicswe.eyeandsickle.client.theme;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

/**
 * Applies a theme to every window, and follows the host OS when asked to.
 *
 * <h2>Live switching, no restart ({@code docs/client/00} §4.3)</h2>
 *
 * Themes swap in place. AtlantaFX's stylesheet is set application-wide, and each Scene carries the
 * token sheet for its family — so changing family is two assignments and a re-apply across open
 * Scenes, not a relaunch. A player evaluating whether they prefer uOS should be able to flip back and
 * forth mid-session and watch the same numbers redraw.
 *
 * <h2>Following the system ({@code docs/client/02} §2)</h2>
 *
 * {@code Platform.Preferences} exposes the OS colour scheme and reduced-motion preference as
 * observable properties, so flipping dark mode while the game is running just works. This is a real
 * JavaFX capability rather than something polled — the native family subscribes to it and the uOS
 * family deliberately does not, because uOS has one look and following the system would undercut it.
 *
 * <p>Reduced motion is honoured by <em>both</em> families: it is an accessibility floor
 * ({@code docs/client/00} §3.5), not a style preference, and a story theme does not get an exemption
 * from it.
 */
public final class ThemeManager {

    private final ClientProfile profile;
    private final ObjectProperty<ThemeId> current = new SimpleObjectProperty<>(ThemeId.NATIVE);
    private final List<Scene> scenes = new ArrayList<>();
    private boolean reducedMotion;

    public ThemeManager(ClientProfile profile) {
        this.profile = profile;
        ThemeId saved = ThemeId.byId(profile.settings().themeId).orElse(ThemeId.NATIVE);
        this.current.set(saved);
        this.reducedMotion = resolveReducedMotion();
    }

    public ObjectProperty<ThemeId> currentProperty() {
        return current;
    }

    public ThemeId current() {
        return current.get();
    }

    /**
     * Whether animation should be suppressed.
     *
     * <p>An explicit choice in Settings wins over the system preference — a player who has turned
     * reduced motion on globally for one troublesome app should still be able to turn it off here,
     * and vice versa. Absent an explicit choice, the OS is the answer.
     */
    public boolean reducedMotion() {
        return reducedMotion;
    }

    private boolean resolveReducedMotion() {
        Boolean override = profile.settings().reducedMotionOverride;
        if (override != null) {
            return override;
        }
        try {
            return Platform.getPreferences().isReducedMotion();
        } catch (RuntimeException notAvailable) {
            // Older or headless toolkits may not expose preferences. Defaulting to "animate" is the
            // right failure direction: a player who needs reduced motion can set it explicitly, and
            // defaulting to suppressed would silently remove feedback for everyone else.
            return false;
        }
    }

    /** Registers a Scene so it receives this and every future theme. */
    public void adopt(Scene scene) {
        if (scene != null && !scenes.contains(scene)) {
            scenes.add(scene);
            applyTo(scene);
        }
    }

    public void forget(Scene scene) {
        scenes.remove(scene);
    }

    /** Switches theme and persists the choice. */
    public void select(ThemeId id) {
        current.set(id);
        profile.settings().themeId = id.id();
        applyAll();
    }

    /** Re-applies the current theme to every adopted Scene. */
    public void applyAll() {
        Application.setUserAgentStylesheet(baseTheme().getUserAgentStylesheet());
        scenes.removeIf(s -> s.getWindow() == null && s.getRoot() == null);
        for (Scene scene : scenes) {
            applyTo(scene);
        }
    }

    private void applyTo(Scene scene) {
        String sheet = getClass().getResource(current().stylesheet()).toExternalForm();
        scene.getStylesheets().removeIf(s -> s.contains("/client/theme/"));
        scene.getStylesheets().add(sheet);
        scene.getRoot().getStyleClass().removeIf(c -> c.startsWith("es-theme-"));
        scene.getRoot().getStyleClass().add("es-theme-" + current().id());
    }

    /**
     * The AtlantaFX base under the token layer.
     *
     * <p>The native family follows the OS colour scheme; the uOS family is always dark, because it is
     * a phosphor terminal and a light one would not be the same thing. Dark is also the right default
     * for the game as a whole — this is a story about being watched.
     */
    private Theme baseTheme() {
        // uOS Classic is drawn on a light field and needs a light base underneath. Getting this
        // wrong is the classic half-themed-application failure: every control the token sheet does
        // not explicitly restyle stays dark, and the result is unreadable rather than merely ugly.
        if (current().light()) {
            return current().highContrast() ? new NordLight() : new PrimerLight();
        }
        if (current().family() == ThemeFamily.UOS) {
            return current().highContrast() ? new NordDark() : new PrimerDark();
        }
        boolean light = systemPrefersLight();
        if (current().highContrast()) {
            return light ? new NordLight() : new NordDark();
        }
        return light ? new PrimerLight() : new PrimerDark();
    }

    private static boolean systemPrefersLight() {
        try {
            return Platform.getPreferences().getColorScheme() == ColorScheme.LIGHT;
        } catch (RuntimeException notAvailable) {
            return false;
        }
    }

    /**
     * Subscribes to the OS colour-scheme and reduced-motion preferences.
     *
     * <p>Called once, after the toolkit is up. Failing here is not fatal: a platform that does not
     * expose preferences simply does not get live following, and the explicit Settings controls
     * still work.
     */
    public void followSystemPreferences() {
        try {
            Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> {
                if (current().family() == ThemeFamily.NATIVE) {
                    applyAll();
                }
            });
            Platform.getPreferences().reducedMotionProperty().addListener((obs, was, now) -> {
                if (profile.settings().reducedMotionOverride == null) {
                    reducedMotion = now;
                }
            });
        } catch (RuntimeException notAvailable) {
            // Nothing to follow. See resolveReducedMotion().
        }
    }
}
