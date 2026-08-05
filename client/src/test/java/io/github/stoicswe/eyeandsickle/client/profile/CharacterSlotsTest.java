package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the save slots the main menu chooses between, and for the theme catalogue. */
class CharacterSlotsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    @Nested
    @DisplayName("save slots")
    class Slots {

        @Test
        @DisplayName("there are three, matching the online cap")
        void threeSlots(@TempDir Path dir) {
            // docs/architecture/09 fixes three characters per account for online play. Mirroring it
            // offline means a player who later goes online does not have to learn a second mental
            // model of what a character is.
            assertThat(new CharacterSlots(new ClientProfile(dir)).soloSlots()).hasSize(3);
            assertThat(CharacterSlots.SLOT_COUNT).isEqualTo(3);
        }

        @Test
        @DisplayName("an empty slot reads as empty, not as broken")
        void emptySlots(@TempDir Path dir) {
            List<CharacterSlots.Slot> slots = new CharacterSlots(new ClientProfile(dir)).soloSlots();
            assertThat(slots).allMatch(s -> !s.occupied());
            assertThat(slots).allMatch(s -> !s.unreadable());
            assertThat(slots.getFirst().summary()).isEqualTo("empty");
        }

        @Test
        @DisplayName("an occupied slot summarises the character")
        void occupiedSlot(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);
            CharacterSlots slots = new CharacterSlots(profile);

            // ⚠ Through slots.store(2), the production path — NOT a fixture store keyed on the
            // legacy path. A slot lives in the profile's own database now, so writing anywhere else
            // and then asking soloSlots() what it sees is a test of two unrelated stores.
            GameEngine game = GameEngine.open(slots.store(2), "ghost", CLOCK);
            game.credit(Balance.ec("42"), "TEST", "seed");
            game.persist();

            CharacterSlots.Slot slot = slots.soloSlots().get(1);
            // ⚠ The problem string is in the assertion deliberately. `occupied` is false both when a
            // slot is genuinely empty and when reading it threw, so a bare isTrue() reports "expected
            // true, got false" for two completely different faults.
            assertThat(slot.occupied())
                    .as("slot 2 should hold the character just written; problem=%s", slot.problem())
                    .isTrue();
            assertThat(slot.handle()).isEqualTo("ghost");
            assertThat(slot.summary()).contains("ghost").contains("42 EC").contains("100 cycles");
        }

        @Test
        @DisplayName("a corrupt slot is reported, never silently shown as empty")
        void corruptSlotIsVisible(@TempDir Path dir) throws IOException {
            // A slot that reads as empty invites the player to overwrite the character they were
            // trying to recover. Showing the problem costs one line and prevents a real loss.
            ClientProfile profile = new ClientProfile(dir);
            CharacterSlots slots = new CharacterSlots(profile);
            // ⚠ Written straight into the state column, which is the only way to produce a document
            // the store itself would never write. A corrupt row is not hypothetical — a hand-edited
            // database or a half-flushed page both land here, and the slot must say so rather than
            // read as empty.
            slots.database()
                    .jdbcClient()
                    .sql("""
                            INSERT INTO character_game_state (character_id, state, format, updated_at)
                            VALUES (:id, '{ not json at all', 1, CURRENT_TIMESTAMP)
                            """)
                    .param("id", slots.characterId(3))
                    .update();

            CharacterSlots.Slot slot = slots.soloSlots().get(2);
            assertThat(slot.unreadable()).isTrue();
            assertThat(slot.occupied()).isFalse();
            assertThat(slot.summary()).startsWith("unreadable");
        }

        @Test
        @DisplayName("deleting a slot removes only that slot")
        void deleteIsScoped(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);
            CharacterSlots slots = new CharacterSlots(profile);
            GameEngine.open(slots.store(1), "a", CLOCK).persist();
            GameEngine.open(slots.store(2), "b", CLOCK).persist();

            assertThat(slots.delete(1)).isTrue();
            assertThat(slots.soloSlots().get(0).occupied()).isFalse();
            assertThat(slots.soloSlots().get(1).occupied()).isTrue();
        }
    }

    @Nested
    @DisplayName("the first-run familiarity question — CL-4 / T-2")
    class Familiarity {

        @Test
        @DisplayName("a fresh profile has not been asked, and defaults to explain")
        void freshProfileIsUnasked(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);
            assertThat(profile.settings().askedFamiliarity).isFalse();
            // The default is right for the audience the education goal targets; the question exists
            // because it is probably wrong for someone who already knows Unix.
            assertThat(profile.settings().teachingLevel).isEqualTo("explain");
        }

        @Test
        @DisplayName("the answer persists, so it is asked exactly once")
        void answerPersists(@TempDir Path dir) {
            ClientProfile profile = new ClientProfile(dir);
            profile.settings().askedFamiliarity = true;
            profile.settings().teachingLevel = "terms";
            profile.save();

            ClientProfile reloaded = new ClientProfile(dir);
            assertThat(reloaded.settings().askedFamiliarity).isTrue();
            assertThat(reloaded.settings().teachingLevel).isEqualTo("terms");
        }
    }

    @Nested
    @DisplayName("themes")
    class Themes {

        @Test
        @DisplayName("the component sheet and every palette overlay actually exist")
        void everyStylesheetResolves() {
            // A missing stylesheet is a NullPointerException at theme-switch time — a crash in
            // front of a player rather than a build failure in front of a developer.
            assertThat(ThemeId.class.getResource(ThemeId.BASE_STYLESHEET))
                    .as("the component sheet")
                    .isNotNull();
            for (ThemeId id : ThemeId.selectable()) {
                id.overlayStylesheet()
                        .ifPresent(sheet -> assertThat(ThemeId.class.getResource(sheet))
                                .as("overlay for %s", id.id())
                                .isNotNull());
            }
        }

        @Test
        @DisplayName("the deck IS the base sheet, so it has no overlay of its own")
        void deckHasNoOverlay() {
            // If DECK ever gained an overlay it would mean the base sheet had stopped being the
            // default palette — which is the first step back towards two sheets that can disagree.
            assertThat(ThemeId.DECK.overlayStylesheet()).isEmpty();
            for (ThemeId id : ThemeId.values()) {
                if (id != ThemeId.DECK) {
                    assertThat(id.overlayStylesheet()).as("%s", id.id()).isPresent();
                }
            }
        }

        @Test
        @DisplayName("every palette overlay redefines colours and NOTHING else")
        void overlaysAreColoursOnly() throws Exception {
            // The whole argument for collapsing seven stylesheets into one component sheet plus
            // palettes (ui-design-language.md §0) is that a widget cannot look right in one variant
            // and broken in another. That only holds while overlays stay palettes. A geometry or
            // font property here is the drift starting, so it fails the build rather than review.
            for (ThemeId id : ThemeId.selectable()) {
                var sheet = id.overlayStylesheet().orElse(null);
                if (sheet == null) {
                    continue;
                }
                String css;
                try (var in = ThemeId.class.getResourceAsStream(sheet)) {
                    css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
                for (String banned : java.util.List.of(
                        "-fx-padding",
                        "-fx-font-size",
                        "-fx-font-family",
                        "-fx-border-width",
                        "-fx-background-radius",
                        "-fx-border-radius",
                        "-fx-effect")) {
                    assertThat(body)
                            .as("%s must not set %s — overlays are palettes", id.id(), banned)
                            .doesNotContain(banned);
                }
            }
        }

        @Test
        @DisplayName("high visibility is offered, because it is an accessibility floor")
        void highContrastIsSelectable() {
            assertThat(ThemeId.selectable()).contains(ThemeId.DECK_HC);
            assertThat(ThemeId.DECK_HC.highContrast()).isTrue();
        }

        @Test
        @DisplayName("the palette variants each have their own overlay, so they can actually differ")
        void variantsAreDistinct() {
            // ⚠ Walks the ENUM rather than naming four themes, which is what it used to do. A
            // hand-kept list here does not fail when a theme is added — it silently stops covering
            // it, so two palettes sharing one overlay file (the copy-paste that makes a "new" theme
            // identical to the one it was cloned from) would ship green. The two liquid variants are
            // exactly that risk: they are a pair, written together, and differ only in their values.
            var overlays = java.util.Arrays.stream(ThemeId.values())
                    .filter(id -> id != ThemeId.DECK)
                    .map(id -> id.overlayStylesheet().orElseThrow())
                    .toList();
            assertThat(overlays)
                    .as("every non-default theme has an overlay of its own")
                    .doesNotHaveDuplicates()
                    .hasSize(ThemeId.values().length - 1);
        }

        @Test
        @DisplayName("⚠ the shipped default is square-cornered — a theme that rounds is opt-in (§9.3, §9.4)")
        void roundingThemesAreOptIn() {
            // §9.4 permits uOS Modern Liquid Abs to round windows without the player's §9.3 switch, on
            // the condition every other amendment runs on: the shipped client is unchanged. DECK is
            // what a new character gets, and it must never be a theme that implies geometry.
            assertThat(ThemeId.DECK.roundsCorners()).isFalse();
            assertThat(new VisualSettings().roundedWindows).isFalse();
            assertThat(ThemeId.cornersAreRounded(new VisualSettings()))
                    .as("a fresh character has square corners")
                    .isFalse();

            // ⚠ And the theme must not WRITE the setting — a costume has to come off cleanly.
            VisualSettings appearance = new VisualSettings();
            appearance.themeId = ThemeId.LIQUID_DARK.id();
            assertThat(ThemeId.cornersAreRounded(appearance))
                    .as("a liquid palette rounds on its own")
                    .isTrue();
            assertThat(appearance.roundedWindows)
                    .as("...without touching what the player chose")
                    .isFalse();
        }
    }
}
