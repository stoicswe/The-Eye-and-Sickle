package io.github.stoicswe.eyeandsickle.client.theme;

/**
 * The two theme families.
 *
 * <p>{@code docs/client/00-client-overview.md} §3.4 fixes the rule that keeps this from becoming two
 * products: <b>only the skin changes.</b> Layout, information architecture, interaction and
 * terminology are identical across families, both clear the same accessibility floor, and a theme is
 * never permitted to hide, reorder or soften information. Atmosphere is spent on chrome, never on
 * data.
 *
 * <p>That is why this enum carries no behavioural switch beyond a stylesheet name. The moment a
 * family needs a code path of its own, the rule has been broken.
 */
public enum ThemeFamily {

    /** Draws uOS using the host platform's conventions. */
    NATIVE("native.css"),

    /**
     * Draws uOS as uOS's own operator console.
     *
     * <p>uOS is not a skin name: it is the operating system every rig in the game runs
     * ({@code docs/design/glossary.md}). The player's laptop runs macOS, Windows or Linux; their rig
     * runs uOS; the client is the window onto it. Both families render the same uOS state.
     */
    UOS("uos.css");

    private final String stylesheetName;

    ThemeFamily(String stylesheetName) {
        this.stylesheetName = stylesheetName;
    }

    public String stylesheetName() {
        return stylesheetName;
    }
}
