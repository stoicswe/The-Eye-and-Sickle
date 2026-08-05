package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.SecurityMark;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the Security Center's verdict says, and the one thing it must never stop doing.
 *
 * <h2>No toolkit needed, and that is the point of the split</h2>
 *
 * The derivation is the part that can be wrong; the drawing is not. Pulling it out of the repaint
 * into a pure function is what makes this testable at all — the bug below shipped precisely because
 * the rule lived inside a method that needed a live scene to reach.
 */
class SecurityVerdictTest {

    @Test
    @DisplayName("⚠ RUNNING AN AUDIT CHANGES THE VERDICT — the panel's primary action must do something")
    void aCleanAuditReachesClear() {
        // The reported bug. "Nothing armed" used to also force CHECK, so on a rig with no defences a
        // player could run a clean audit and watch the mark stay on the same warning triangle. There
        // is no way to tell that from a broken button, and a player will assume the button.
        assertThat(SecurityCenterView.markStateFor(true, true, false))
                .isEqualTo(SecurityMark.State.CLEAR);
    }

    @Test
    @DisplayName("a finding is quarantine")
    void aFindingIsQuarantine() {
        assertThat(SecurityCenterView.markStateFor(true, false, false))
                .isEqualTo(SecurityMark.State.QUARANTINE);
        assertThat(SecurityCenterView.markStateFor(true, false, true))
                .as("a stale finding is still a finding — staleness never downgrades a hit")
                .isEqualTo(SecurityMark.State.QUARANTINE);
    }

    @Test
    @DisplayName("⚠ never audited is CHECK, not quarantine — nobody has looked is not a threat")
    void neverAuditedIsUnknown() {
        // Colouring "nobody has checked" as hostile would cry wolf on every new character, and the
        // player would stop reading the one mark that matters.
        assertThat(SecurityCenterView.markStateFor(false, false, true))
                .isEqualTo(SecurityMark.State.CHECK);
        assertThat(SecurityCenterView.markStateFor(false, true, false))
                .isEqualTo(SecurityMark.State.CHECK);
    }

    @Test
    @DisplayName("⚠ a clean audit EXPIRES — a week-old all-clear is unknown, not clear")
    void staleCleanIsUnknown() {
        // Nothing stops something landing the second after an audit finishes, so "clear" has a shelf
        // life. A panel that kept saying clear on the strength of last week's scan would be lying by
        // omission in exactly the way a real one must not.
        assertThat(SecurityCenterView.markStateFor(true, true, true))
                .isEqualTo(SecurityMark.State.CHECK);
    }
}
