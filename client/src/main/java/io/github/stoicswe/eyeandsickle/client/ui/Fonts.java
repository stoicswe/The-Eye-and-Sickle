package io.github.stoicswe.eyeandsickle.client.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.text.Font;

/**
 * Loads the two bundled typefaces.
 *
 * <h2>Bundled, never installed</h2>
 *
 * {@code docs/design/ui-design-language.md} §2.2: "bundle the TTFs in {@code resources/fonts/}, do
 * not rely on system installs." Neither face is on a default macOS, Windows or Linux image, so a
 * client that asked the OS for them would silently fall back to the platform monospace on every
 * machine but the developer's — and §2.2's whole argument for Martian Mono is that <b>the face
 * supplies the tracking JavaFX cannot</b> (§7.2: no {@code letter-spacing}, at all). Falling back
 * does not lose a little polish; it loses the one mechanism holding the type language up.
 *
 * <h2>Registration is verified, not assumed</h2>
 *
 * {@link Font#loadFont(InputStream, double)} returns {@code null} on failure and throws nothing, so
 * an unreadable or absent resource produces a client that looks subtly wrong and reports nothing.
 * {@link #load()} therefore checks what actually registered and {@link #missing()} names anything
 * that did not, which is what {@code FontsTest} asserts against — §10 criterion 3.
 */
public final class Fonts {

    private static final String DIR = "/io/github/stoicswe/eyeandsickle/client/fonts/";

    /**
     * The faces the design language actually calls for.
     *
     * <p>Martian Mono 500 and 700 (§2.2: labels and display numerals); IBM Plex Mono 300/400/500
     * (body, data, tables). Five files rather than the two full families, because the rest are
     * weights and widths nothing in the catalogue uses and every one of them is ~170 KB in the jar.
     */
    private static final List<String> FILES = List.of(
            "MartianMono-Medium.ttf",
            "MartianMono-Bold.ttf",
            "IBMPlexMono-Light.ttf",
            "IBMPlexMono-Regular.ttf",
            "IBMPlexMono-Medium.ttf");

    private static final Set<String> LOADED = new LinkedHashSet<>();
    private static boolean attempted;

    private Fonts() {}

    /**
     * Registers every bundled face with the toolkit. Idempotent; safe to call before a Stage exists.
     *
     * @return the family names that are now available
     */
    public static synchronized Set<String> load() {
        if (attempted) {
            return Set.copyOf(LOADED);
        }
        attempted = true;
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream(DIR + file)) {
                if (in == null) {
                    continue;
                }
                // The size argument is the size the returned Font is instantiated at, not a
                // restriction — registration covers every size. -1 means "do not instantiate".
                Font font = Font.loadFont(in, -1);
                if (font != null) {
                    LOADED.add(font.getFamily());
                }
            } catch (Exception unreadable) {
                // Deliberately swallowed and reported through missing() instead. A font that fails
                // to load must not stop the client from starting — the game is still playable in a
                // fallback monospace, and a hard failure here would turn a cosmetic problem into an
                // unlaunchable build.
            }
        }
        return Set.copyOf(LOADED);
    }

    /**
     * The families the design language requires that are not actually available.
     *
     * <p>Empty is the only correct answer in a shipped build.
     */
    public static List<String> missing() {
        load();
        List<String> gaps = new ArrayList<>();
        for (String family : List.of(UiTokens.DISPLAY_FAMILY, UiTokens.BODY_FAMILY)) {
            boolean present = LOADED.stream().anyMatch(f -> f.equalsIgnoreCase(family))
                    || Font.getFamilies().stream().anyMatch(f -> f.equalsIgnoreCase(family));
            if (!present) {
                gaps.add(family);
            }
        }
        return gaps;
    }
}
