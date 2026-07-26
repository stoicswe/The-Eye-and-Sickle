package io.github.stoicswe.eyeandsickle.client.theme;

import java.util.Arrays;
import java.util.Optional;

/**
 * The themes a player can choose, across both families.
 *
 * <p>Casing follows {@code docs/design/glossary.md}: <b>uOS</b> in anything a player reads,
 * {@code uos} in identifiers — exactly the macOS/{@code macos} split. The {@code -hc} suffix is a
 * high-contrast modifier rather than a separate theme, which is why it composes with each variant
 * instead of multiplying the list.
 */
public enum ThemeId {

    /**
     * Adapts to the host OS: light/dark, accent colour and reduced-motion are read live from the
     * system, so flipping dark mode mid-session just works ({@code docs/client/02}).
     */
    NATIVE("native", ThemeFamily.NATIVE, "Native", false),
    NATIVE_HC("native-hc", ThemeFamily.NATIVE, "Native (high contrast)", true),

    /** The story family: uOS drawn as its own operator console ({@code docs/client/03}). */
    UOS("uos", ThemeFamily.UOS, "uOS", false),
    UOS_HC("uos-hc", ThemeFamily.UOS, "uOS (high contrast)", true),
    UOS_AMBER("uos-amber", ThemeFamily.UOS, "uOS — amber", false),
    UOS_AMBER_HC("uos-amber-hc", ThemeFamily.UOS, "uOS — amber (high contrast)", true),
    UOS_PHOSPHOR("uos-phosphor", ThemeFamily.UOS, "uOS — phosphor", false),
    UOS_PHOSPHOR_HC("uos-phosphor-hc", ThemeFamily.UOS, "uOS — phosphor (high contrast)", true);

    private final String id;
    private final ThemeFamily family;
    private final String label;
    private final boolean highContrast;

    ThemeId(String id, ThemeFamily family, String label, boolean highContrast) {
        this.id = id;
        this.family = family;
        this.label = label;
        this.highContrast = highContrast;
    }

    public String id() {
        return id;
    }

    public ThemeFamily family() {
        return family;
    }

    public String label() {
        return label;
    }

    public boolean highContrast() {
        return highContrast;
    }

    /** The stylesheet that carries this theme's token values. */
    public String stylesheet() {
        return "/io/github/stoicswe/eyeandsickle/client/theme/" + family.stylesheetName();
    }

    public static Optional<ThemeId> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }
}
