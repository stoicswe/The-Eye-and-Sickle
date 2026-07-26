package io.github.stoicswe.eyeandsickle.client.theme;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The themes a player can choose, across both families.
 *
 * <h2>Casing is a convention, not a preference</h2>
 *
 * {@code docs/design/glossary.md} fixes it: <b>uOS</b> in prose, in UI copy and in anything a player
 * reads; {@code uos} in identifiers — theme ids, CSS classes, stylesheet filenames. Exactly the
 * macOS/{@code macos} split.
 *
 * <h2>Each variant owns a stylesheet, and that changed on 2026-07-25</h2>
 *
 * Originally every uOS variant shared one sheet and differed only in name — which meant they could
 * not actually differ. They now have their own files, which is what let the default become the
 * crimson console while the green phosphor kept existing as {@link #UOS_PHOSPHOR} rather than being
 * deleted.
 *
 * <p>The {@code -hc} suffix stays a modifier rather than a separate look: it swaps the AtlantaFX base
 * underneath for a higher-contrast one and reuses its variant's tokens.
 */
public enum ThemeId {

    /**
     * Adapts to the host OS: light/dark, accent colour and reduced-motion are read live from the
     * system, so flipping dark mode mid-session just works ({@code docs/client/02}).
     */
    NATIVE("native", ThemeFamily.NATIVE, "Native", "native.css", false),
    NATIVE_HC("native-hc", ThemeFamily.NATIVE, "Native (high contrast)", "native.css", true),

    /**
     * The default story look: near-black, crimson-lit — Blade Runner rather than VT220.
     *
     * <p>Redesigned 2026-07-25 on request. What this used to be is now {@link #UOS_PHOSPHOR}.
     */
    UOS("uos", ThemeFamily.UOS, "uOS", "uos.css", false),
    UOS_HC("uos-hc", ThemeFamily.UOS, "uOS (high contrast)", "uos.css", true),

    /** The green CRT this family started as — DEC/VT220, one phosphor at several intensities. */
    UOS_PHOSPHOR("uos-phosphor", ThemeFamily.UOS, "uOS — phosphor", "uos-phosphor.css", false),

    /** The amber tube: warmer, and the one many people read most comfortably for long stretches. */
    UOS_AMBER("uos-amber", ThemeFamily.UOS, "uOS — amber", "uos-amber.css", false),

    /**
     * System 7 meets a Unix workstation: light grey chassis, black hairlines, bevelled controls.
     *
     * <p>Also the most legible skin in the client — black on white is 21:1, nothing glows and nothing
     * moves — which makes it a genuine accessibility option in a period costume rather than a
     * novelty.
     */
    UOS_CLASSIC("uos-classic", ThemeFamily.UOS, "uOS Classic", "uos-classic.css", false);

    private final String id;
    private final ThemeFamily family;
    private final String label;
    private final String stylesheetFile;
    private final boolean highContrast;

    ThemeId(String id, ThemeFamily family, String label, String stylesheetFile, boolean highContrast) {
        this.id = id;
        this.family = family;
        this.label = label;
        this.stylesheetFile = stylesheetFile;
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

    /**
     * True for the one skin drawn on a light field.
     *
     * <p>Changes which AtlantaFX base belongs underneath: a dark base under a light token sheet
     * leaves every unstyled control dark-on-light and unreadable, which is the failure mode a
     * half-themed application always has.
     */
    public boolean light() {
        return this == UOS_CLASSIC;
    }

    /** The stylesheet carrying this theme's token values. */
    public String stylesheet() {
        return "/io/github/stoicswe/eyeandsickle/client/theme/" + stylesheetFile;
    }

    public static Optional<ThemeId> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    /** Themes offered in the picker, in the order they are offered. */
    public static List<ThemeId> selectable() {
        return List.of(NATIVE, UOS, UOS_CLASSIC, UOS_PHOSPHOR, UOS_AMBER, NATIVE_HC, UOS_HC);
    }
}
