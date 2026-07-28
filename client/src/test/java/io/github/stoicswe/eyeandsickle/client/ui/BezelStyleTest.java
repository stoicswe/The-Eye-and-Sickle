package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bezel amendment's conditions, as tests rather than as prose.
 *
 * <p>{@code ui-design-language.md} §9 cut bezel twice and §9.1 pointedly kept it cut when four other
 * screen artefacts were permitted. It is allowed since 2026-07-27 on explicit direction, under
 * §9.1's same four conditions — and the two that can be checked mechanically are checked here,
 * because an amendment defended only by a paragraph erodes.
 */
class BezelStyleTest {

    @Test
    @DisplayName("⚠ condition 1 — OFF is the default, in the enum and in a fresh profile")
    void offByDefault(@TempDir Path dir) {
        // The condition the whole rejection list was protecting: an effect the player switches on is
        // a costume, one welded to the interface is a claim about fidelity the interface then has to
        // keep making while they are trying to read a number.
        assertThat(BezelStyle.values()[0]).isEqualTo(BezelStyle.OFF);
        assertThat(BezelStyle.OFF.margin()).isZero();
        assertThat(new ClientProfile(dir).settings().bezel).isEqualTo(BezelStyle.OFF.id());
    }

    @Test
    @DisplayName("⚠ condition 2 — every style has a margin to draw in, so none can overlay content")
    void everyStyleOwnsAMargin() {
        // The deck is inset by exactly margin(), and the casing paints only inside that inset. A
        // style with no margin would have nowhere to draw but on top of the interface — and the top
        // strip carries the compute readout, which is pillar C2.
        for (BezelStyle style : BezelStyle.selectable()) {
            if (style == BezelStyle.OFF) {
                continue;
            }
            assertThat(style.margin()).as("%s", style).isPositive();
            // ⚠ Bounded, and the reason CHANGED on 2026-07-27. It used to be that the casing was
            // subtracted from the chosen resolution, so a fat margin ate the deck's own room and
            // had to stay small against the 860px floor. Now the resolution is the viewport's and
            // the casing is added OUTSIDE it, so it costs window room instead — the bound is about
            // not demanding a window far larger than the screen the player picked a size to fit.
            // 40px a side is 80 on each axis, under 7% of the smallest preset's width.
            assertThat(style.margin()).as("%s", style).isLessThanOrEqualTo(40);
        }
    }

    @Test
    @DisplayName("ids round-trip, and an unknown one falls back to OFF rather than throwing")
    void idsRoundTrip() {
        for (BezelStyle style : BezelStyle.selectable()) {
            assertThat(BezelStyle.byId(style.id())).contains(style);
        }
        assertThat(BezelStyle.byId("chrome-trim")).isEmpty();
        assertThat(BezelStyle.byId(null)).isEmpty();
    }

    @Test
    @DisplayName("every style says what it looks like, since Settings shows the note")
    void everyStyleIsDescribed() {
        for (BezelStyle style : BezelStyle.selectable()) {
            assertThat(style.label()).as("%s label", style).isNotBlank();
            assertThat(style.note()).as("%s note", style).isNotBlank();
        }
    }
}
