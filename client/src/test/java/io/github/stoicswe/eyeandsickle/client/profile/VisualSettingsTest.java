package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Appearance is per character, and the three ways that can silently stop being true.
 *
 * <ol>
 *   <li><b>Migration.</b> Jackson ignores unknown properties, so the day the appearance fields moved
 *       out of {@code Settings} every existing {@code settings.json} became a file full of keys
 *       nothing binds. Without the legacy setters a player would launch into a theme they never
 *       chose, with no error anywhere.
 *   <li><b>Isolation.</b> Two characters must not share a look. A {@code copy()} that missed a field
 *       would leave that one field pointing at the same value for everybody, which reads as a bug in
 *       whichever screen happens to show it.
 *   <li><b>Scope drift.</b> A new appearance control added to {@code Settings} instead of here
 *       becomes machine-wide again, quietly, and nothing but a player noticing would catch it.
 * </ol>
 */
class VisualSettingsTest {

    @Nested
    @DisplayName("⚠ migration from a profile written before the split")
    class Migration {

        @Test
        @DisplayName("flat appearance keys land in the menu's appearance")
        void legacyKeysAreRead(@TempDir Path dir) throws IOException {
            // Exactly the shape an older build wrote: appearance fields at the top level.
            Files.writeString(dir.resolve("settings.json"), """
                    {
                      "themeId": "phosphor",
                      "cursorSkin": "reticle",
                      "wallpaper": "still",
                      "bezel": "slim",
                      "crtScanlines": true,
                      "crtAberration": true,
                      "crtGlitch": true,
                      "crtCurvature": 40,
                      "roundedWindows": true,
                      "subwindowControlOrder": "macos",
                      "teachingLevel": "terms",
                      "uiScalePercent": 125
                    }
                    """);

            ClientProfile profile = new ClientProfile(dir);
            VisualSettings look = profile.appearance();

            assertThat(look.themeId).isEqualTo("phosphor");
            assertThat(look.cursorSkin).isEqualTo("reticle");
            assertThat(look.wallpaper).isEqualTo("still");
            assertThat(look.bezel).isEqualTo("slim");
            assertThat(look.crtScanlines).isTrue();
            assertThat(look.crtAberration).isTrue();
            assertThat(look.crtGlitch).isTrue();
            assertThat(look.crtCurvature).isEqualTo(40);
            assertThat(look.roundedWindows).isTrue();
            assertThat(look.subwindowControlOrder).isEqualTo("macos");

            // And the settings that did NOT move are untouched by any of it.
            assertThat(profile.settings().teachingLevel).isEqualTo("terms");
            assertThat(profile.settings().uiScalePercent).isEqualTo(125);
        }

        @Test
        @DisplayName("a migrated profile survives a save and reload")
        void migrationIsIdempotent(@TempDir Path dir) throws IOException {
            Files.writeString(dir.resolve("settings.json"), "{\"themeId\": \"amber\"}");

            ClientProfile first = new ClientProfile(dir);
            assertThat(first.appearance().themeId).isEqualTo("amber");
            first.save();

            // ⚠ The rewritten file has NO flat themeId — the legacy hooks are setter-only, so they
            // read and never write. The nested form has to carry it on the way back in, or the
            // player's theme survives exactly one launch.
            String written = Files.readString(dir.resolve("settings.json"));
            assertThat(written).contains("\"appearance\"");

            assertThat(new ClientProfile(dir).appearance().themeId).isEqualTo("amber");
        }

        @Test
        @DisplayName("every character inherits the machine's old look, not the defaults")
        void existingCharactersKeepWhatTheyHad(@TempDir Path dir) throws IOException {
            // The migration that matters most: somebody who has been playing on Phosphor for weeks
            // must not find their character on Deck because appearance became per-character.
            Files.writeString(dir.resolve("settings.json"), "{\"themeId\": \"phosphor\"}");
            ClientProfile profile = new ClientProfile(dir);

            profile.useCharacterAppearance(2);

            assertThat(profile.appearance().themeId).isEqualTo("phosphor");
        }
    }

    @Nested
    @DisplayName("isolation")
    class Isolation {

        @Test
        @DisplayName("two characters do not share a look")
        void slotsAreIndependent(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);

            profile.useCharacterAppearance(1);
            profile.appearance().themeId = "phosphor";
            profile.useCharacterAppearance(2);
            profile.appearance().themeId = "classic";

            assertThat(profile.settings().appearanceFor(1).themeId).isEqualTo("phosphor");
            assertThat(profile.settings().appearanceFor(2).themeId).isEqualTo("classic");
            // And neither of them moved the menu's.
            assertThat(profile.settings().appearance.themeId).isEqualTo("deck");
        }

        @Test
        @DisplayName("⚠ copy() carries every field — a missed one would be shared, not defaulted")
        void copyIsComplete() throws Exception {
            VisualSettings source = new VisualSettings();
            source.themeId = "cyberdeck";
            source.cursorSkin = "chevron";
            source.wallpaper = "off";
            source.bezel = "slim";
            source.crtScanlines = true;
            source.crtAberration = true;
            source.crtGlitch = true;
            source.crtCurvature = 77;
            source.roundedWindows = true;
            source.subwindowControlOrder = "windows";

            VisualSettings copy = source.copy();

            // Reflective, so a field added to VisualSettings and forgotten in copy() fails here
            // rather than in whichever screen happens to render it first.
            for (var field : VisualSettings.class.getFields()) {
                assertThat(field.get(copy))
                        .as("copy() does not carry %s", field.getName())
                        .isEqualTo(field.get(source));
            }
        }

        @Test
        @DisplayName("deleting a character forgets its look — a reused slot starts clean")
        void deleteForgets(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);
            profile.settings().appearanceFor(1).themeId = "phosphor";

            new CharacterSlots(profile).delete(1);

            assertThat(profile.settings().characterAppearance).doesNotContainKey("1");
            assertThat(profile.settings().appearanceFor(1).themeId)
                    .as("a reused slot starts from the menu's look, not the deleted character's")
                    .isEqualTo("deck");
        }
    }

    @Test
    @DisplayName("⚠ swapping which look is in force must go through reloadAppearance, not applyAll")
    void everySwapReloadsTheThemeCache() throws IOException {
        // ThemeManager caches the current ThemeId and paints from the cache. useCharacterAppearance
        // swaps the VisualSettings the profile points at behind its back, so applyAll() after a
        // swap faithfully re-applies the PREVIOUS character's palette — a character opening in
        // somebody else's colours, which reads as the per-character setting having failed to save.
        // ⚠ Comments stripped FIRST. One of the four swaps is followed by an explanatory comment,
        // and matching against the raw source silently found three — a check that misses the case
        // somebody bothered to explain is worse than no check.
        String client = Files.readString(
                        Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/EyeAndSickleClient.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\n]*", "");

        Matcher swaps = Pattern.compile(
                        "profile\\.use(?:Menu|Character|Pending)Appearance\\([^)]*\\);\\s*\n\\s*([A-Za-z.]+\\()")
                .matcher(client);
        List<String> followedBy = new ArrayList<>();
        while (swaps.find()) {
            followedBy.add(swaps.group(1));
        }

        // The count is asserted too: a swap the regex fails to see is a swap this test is not
        // protecting, and "all zero matches passed" is the classic way a source scan rots.
        assertThat(followedBy)
                .as("every appearance swap in EyeAndSickleClient must be followed by " + "themes.reloadAppearance()")
                .hasSize(client.split("profile\\.use", -1).length - 1)
                .allMatch(call -> call.equals("themes.reloadAppearance("));
    }

    @Test
    @DisplayName("⚠ no appearance field is left on Settings, where it would be machine-wide again")
    void nothingVisualLeftBehind() {
        // The failure mode: someone adds "wallpaper tint" to Settings because that is where every
        // other setting lives, and it is silently shared by every character. Names, because the
        // property is about WHERE a field is declared and no runtime behaviour reveals it.
        Set<String> onSettings = new LinkedHashSet<>();
        for (var field : ClientProfile.Settings.class.getFields()) {
            onSettings.add(field.getName());
        }

        assertThat(onSettings)
                .doesNotContain(
                        "themeId",
                        "cursorSkin",
                        "wallpaper",
                        "bezel",
                        "crtScanlines",
                        "crtAberration",
                        "crtGlitch",
                        "crtCurvature",
                        "roundedWindows",
                        "subwindowControlOrder");

        // ⚠ And the deliberate exceptions, asserted so that moving one is a decision rather than a
        // drift. uiScalePercent and reducedMotionOverride are accessibility FLOORS (docs/client/07)
        // — per-character would hand a player who needs 150% text 100% on every new character.
        // nativeWindowBorder cannot be per-character at all: Stage.initStyle is rejected on a
        // realised Stage, so it could not take effect until a restart.
        assertThat(onSettings)
                .contains("uiScalePercent", "reducedMotionOverride", "nativeWindowBorder", "windowSize", "fullScreen");
    }

    /**
     * The ten keys that lived on {@code Settings} before appearance became per-character.
     *
     * <h2>⚠ A literal list, and it must stay one</h2>
     *
     * This is a <b>historical fact</b>: it is the set of keys a pre-split {@code settings.json} can
     * actually contain, and nothing that happens from here changes it. Deriving it from
     * {@code VisualSettings.class.getFields()} — which is what this test used to do — quietly asserted
     * something else: that every appearance field ever added must also have a legacy hook. That was
     * true on the day it was written, because every field was a migrated one, and it became false the
     * first time an appearance setting was added that had never lived on {@code Settings}.
     *
     * <p>⚠ A hook for a brand-new field would be worse than none. It would read a legacy key that no
     * save has ever contained, which is exactly the dead code the second half of this test refuses.
     */
    private static final List<String> LEGACY_KEYS = List.of(
            "themeId",
            "cursorSkin",
            "wallpaper",
            "bezel",
            "crtScanlines",
            "crtAberration",
            "crtGlitch",
            "crtCurvature",
            "roundedWindows",
            "subwindowControlOrder");

    @Test
    @DisplayName("every legacy key has a migration hook, and every hook names a real field")
    void migrationHooksMatchTheFields() throws IOException {
        // Both directions. A legacy key with no hook loses its value on upgrade — the mapper has
        // FAIL_ON_UNKNOWN_PROPERTIES off, so it is dropped in silence; a hook naming a field that no
        // longer exists is dead code that reads as coverage.
        String source = Files.readString(
                Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/profile/ClientProfile.java"));

        Matcher hooks = Pattern.compile("@com\\.fasterxml\\.jackson\\.annotation\\.JsonProperty\\(\"([A-Za-z]+)\"\\)")
                .matcher(source);
        Set<String> hooked = new LinkedHashSet<>();
        while (hooks.find()) {
            hooked.add(hooks.group(1));
        }

        assertThat(hooked)
                .as("a legacy key with no hook loses its value when a player upgrades")
                .containsExactlyInAnyOrderElementsOf(LEGACY_KEYS);

        List<String> fields = new ArrayList<>();
        for (var field : VisualSettings.class.getFields()) {
            fields.add(field.getName());
        }
        assertThat(fields).as("every hook names a field that still exists").containsAll(LEGACY_KEYS);
    }

    /**
     * ⚠ A new appearance field needs no hook, but it DOES need to survive a round trip.
     *
     * <p>That is the guarantee the hook rule was standing in for, and it is the one that actually
     * matters: whatever the reason a field is on {@link VisualSettings}, saving and reloading must
     * not lose it. This checks it directly rather than by proxy, so it holds for legacy and new
     * fields alike.
     */
    @Test
    @DisplayName("a new appearance field round-trips without a migration hook")
    void newFieldsRoundTrip(@TempDir Path dir) {
        ClientProfile profile = new ClientProfile(dir);
        profile.appearance().focusRing = true;
        profile.appearance().focusRingColor = "violet";
        profile.save();

        ClientProfile reloaded = new ClientProfile(dir);
        assertThat(reloaded.appearance().focusRing).isTrue();
        assertThat(reloaded.appearance().focusRingColor).isEqualTo("violet");
    }
}
