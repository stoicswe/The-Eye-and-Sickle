package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Cancelling the setup assistant has to be a real undo.
 *
 * <h2>The failure this exists to prevent</h2>
 *
 * The assistant applies its choices <b>live</b> — a palette cannot be chosen from its name — and it
 * writes them to the profile's <em>global</em> settings, shared by every character. So a player who
 * opens it, tries three palettes, and backs out must find their existing character exactly as they
 * left it. There is no other guard on that: nothing in the build notices a global that was changed
 * and never put back, and the player would experience it as the game re-theming itself for no
 * reason days later.
 *
 * <p>⚠ The dangerous version of this bug is not "restore is broken" — it is "a seventh pane was
 * added and its field was never added to the snapshot". {@link #snapshotCoversEverythingTheWizardWrites}
 * is the one that catches it, by reading the assistant's source and comparing the settings fields it
 * assigns against the record's components. It is a source scan for the same reason
 * {@code UiContractTest} uses them: the property is about the shape of the code, and no runtime test
 * can see a field nobody remembered to write down.
 */
class SettingsSnapshotTest {

    private static final Path WIZARD =
            Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/view/SetupWizardView.java");

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("every captured field comes back, the nullable one included")
        void restoresEverything() {
            ClientProfile.Settings original = new ClientProfile.Settings();
            original.rigHostname = "workbench";
            original.teachingLevel = "terms";
            original.uiScalePercent = 125;
            original.reducedMotionOverride = Boolean.TRUE;

            SettingsSnapshot before = SettingsSnapshot.of(original);

            // Everything the assistant can touch, moved to a different value.
            original.rigHostname = "somewhere-else";
            original.teachingLevel = "off";
            original.uiScalePercent = 175;
            original.reducedMotionOverride = Boolean.FALSE;

            before.restoreTo(original);

            assertThat(original.rigHostname).isEqualTo("workbench");
            assertThat(original.teachingLevel).isEqualTo("terms");
            assertThat(original.uiScalePercent).isEqualTo(125);
            assertThat(original.reducedMotionOverride).isTrue();
        }

        @Test
        @DisplayName("⚠ null survives — it is a value, not an absence")
        void nullIsAValue() {
            // reducedMotionOverride is the only nullable setting in the client, and its null MEANS
            // "follow the system". A restore that treated null as "nothing to put back" would
            // silently convert a player who follows their OS into one who has pinned an answer —
            // and the two look identical on screen until the OS preference changes.
            ClientProfile.Settings settings = new ClientProfile.Settings();
            settings.reducedMotionOverride = null;
            SettingsSnapshot before = SettingsSnapshot.of(settings);

            settings.reducedMotionOverride = Boolean.TRUE;
            before.restoreTo(settings);

            assertThat(settings.reducedMotionOverride).isNull();
        }

        @Test
        @DisplayName("a snapshot of untouched defaults restores to untouched defaults")
        void defaultsRoundTrip() {
            // The assistant's central claim: press Continue through every pane without changing
            // anything and nothing changes. Every global pane is seeded from the current value, so
            // that reduces to this.
            ClientProfile.Settings settings = new ClientProfile.Settings();
            SettingsSnapshot before = SettingsSnapshot.of(settings);
            before.restoreTo(settings);

            ClientProfile.Settings fresh = new ClientProfile.Settings();
            assertThat(settings.rigHostname).isEqualTo(fresh.rigHostname);
            assertThat(settings.teachingLevel).isEqualTo(fresh.teachingLevel);
            assertThat(settings.uiScalePercent).isEqualTo(fresh.uiScalePercent);
            assertThat(settings.reducedMotionOverride).isEqualTo(fresh.reducedMotionOverride);
        }
    }

    @Test
    @DisplayName("⚠ the snapshot covers every settings field the assistant writes")
    void snapshotCoversEverythingTheWizardWrites() throws IOException {
        String source = Files.readString(WIZARD);

        // Every `profile.settings().<field> = ` in the assistant. Assignment only: a READ is how a
        // pane seeds itself from the current value, which is the behaviour we want and not a write.
        String body = stripComments(source);
        Matcher assignment = Pattern.compile("profile\\.settings\\(\\)\\.([A-Za-z0-9_]+)\\s*=[^=]")
                .matcher(body);
        Set<String> written = new LinkedHashSet<>();
        while (assignment.find()) {
            written.add(assignment.group(1));
        }

        // ⚠ Direct assignment is not the only way in. ThemeManager owns two machine-wide settings
        // and writes them on the caller's behalf, so a pane that goes through it is invisible to the
        // scan above — which is exactly the idiom the motion pane uses. Each mediated write is
        // declared here; a THIRD one that nobody adds to this map is the gap that reopens.
        Map<String, String> mediated = Map.of("themes.setReducedMotionOverride(", "reducedMotionOverride");
        mediated.forEach((call, field) -> {
            if (body.contains(call)) {
                written.add(field);
            }
        });

        List<String> captured = new ArrayList<>();
        for (var component : SettingsSnapshot.class.getRecordComponents()) {
            captured.add(component.getName());
        }

        List<String> unprotected = new ArrayList<>(written);
        // askedFamiliarity and soloHandle are written on the LAST pane, after the player has
        // committed — the assistant is finishing, not previewing, and there is no cancel left to
        // undo. Everything else must be restorable.
        unprotected.removeAll(List.of("askedFamiliarity", "soloHandle"));
        unprotected.removeAll(captured);

        assertThat(unprotected)
                .as("SetupWizardView writes these globals and SettingsSnapshot cannot restore them "
                        + "— cancelling the assistant would leave them changed")
                .isEmpty();
    }

    @Test
    @DisplayName("the assistant reaches settings only through profile.settings()")
    void noSideDoor() throws IOException {
        // The scan above only sees `profile.settings().x = `. A pane that stashed the Settings
        // object in a local and assigned through that would be invisible to it — so the shape the
        // scan depends on is itself asserted.
        String body = stripComments(Files.readString(WIZARD));
        assertThat(body)
                .as("a local alias for Settings would hide writes from the coverage check above")
                .doesNotContain("ClientProfile.Settings ")
                .doesNotContain("Settings settings =");
    }

    /** Line and block comments removed, so prose about a field never counts as a write. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }
}
