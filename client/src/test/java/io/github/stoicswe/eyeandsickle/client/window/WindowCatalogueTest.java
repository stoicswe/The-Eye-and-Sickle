package io.github.stoicswe.eyeandsickle.client.window;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the window catalogue against {@code docs/client/05-tool-windows-and-layout.md} §2.1.
 *
 * <p>This is a document-conformance test. The table in that section is a specification, and a
 * catalogue that drifts from it produces a client whose accelerators and sizes no longer match what
 * every other document says they are.
 */
class WindowCatalogueTest {

    @Test
    @DisplayName("the catalogue is the fifteen from 05 §2.1 plus `man` from 04 §4.6")
    void catalogueMatchesTheDocuments() {
        // ⚠ The two documents disagree about the size of a table both call closed: docs/client/05
        // §2.1 lists fifteen and never absorbed the `man` window that docs/client/04 §4.6 adds and
        // flags as T-1. Building it and reporting the discrepancy beats silently dropping the way a
        // player reaches the teaching layer — which is client pillar C6.
        assertThat(WindowSpec.values()).hasSize(16);
        assertThat(java.util.Arrays.stream(WindowSpec.values()).map(WindowSpec::id).toList())
                .containsExactlyInAnyOrder(
                        "rig-monitor", "terminal", "map", "recon", "audit", "mining", "storage",
                        "ledger", "botnet", "defense", "market", "identity", "comms", "settings",
                        "switcher", "man");
    }

    @Test
    @DisplayName("no window's minimum exceeds 720×480")
    void minimumsFitTwoAcrossALaptop() {
        // The rule from §2.1: any two tools must fit side by side on a 1366×768 screen with the rig
        // strip still visible. This is what keeps multi-window usable on the machine most players
        // actually have, rather than only on a desk with two monitors.
        for (WindowSpec spec : WindowSpec.values()) {
            assertThat(spec.minWidth())
                    .as("%s minimum width", spec.id())
                    .isLessThanOrEqualTo(WindowSpec.MAX_MINIMUM_WIDTH);
            assertThat(spec.minHeight())
                    .as("%s minimum height", spec.id())
                    .isLessThanOrEqualTo(WindowSpec.MAX_MINIMUM_HEIGHT);
        }
    }

    @Test
    @DisplayName("every default size is at least its minimum")
    void defaultsAreAtLeastMinimums() {
        for (WindowSpec spec : WindowSpec.values()) {
            assertThat(spec.defaultWidth()).as("%s", spec.id()).isGreaterThanOrEqualTo(spec.minWidth());
            assertThat(spec.defaultHeight()).as("%s", spec.id()).isGreaterThanOrEqualTo(spec.minHeight());
        }
    }

    @Test
    @DisplayName("no two windows share an accelerator")
    void acceleratorsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (WindowSpec spec : WindowSpec.values()) {
            KeyCombination combination = spec.combination();
            assertThat(seen.add(combination.getName()))
                    .as("%s duplicates the accelerator %s", spec.id(), combination.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the rig monitor is not closable — client pillar C2")
    void rigMonitorIsNotClosable() {
        // docs/design/01 §1.4 makes the compute readout mandatory and always visible. A route to
        // closing it — including the OS title-bar X — would break that through a door the UI never
        // offers.
        assertThat(WindowSpec.RIG_MONITOR.closable()).isFalse();
        for (WindowSpec spec : WindowSpec.values()) {
            if (spec != WindowSpec.RIG_MONITOR) {
                assertThat(spec.closable()).as("%s", spec.id()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("first run opens the rig monitor and the switcher, and nothing else")
    void firstRunSet() {
        assertThat(java.util.Arrays.stream(WindowSpec.values())
                        .filter(WindowSpec::openOnFirstRun)
                        .map(WindowSpec::id)
                        .toList())
                .containsExactlyInAnyOrder("rig-monitor", "switcher");
    }

    @Test
    @DisplayName("every window names the real tool it stands in for")
    void everyWindowHasAUnixAnalogue() {
        // Cheap teaching: a player who learns the audit window IS ps, netstat and df has learned
        // three real commands without being taught them.
        for (WindowSpec spec : WindowSpec.values()) {
            assertThat(spec.unixAnalogue()).as("%s", spec.id()).isNotBlank();
        }
    }

    @Test
    @DisplayName("a window remembered on a monitor that no longer exists is not restored there")
    void offScreenGeometryIsRejected() {
        // Monitors get unplugged. A window restored to a coordinate on a screen that is gone is
        // invisible AND focusable, so the player can hear it respond and never find it.
        List<Rectangle2D> oneScreen = List.of(new Rectangle2D(0, 0, 1440, 900));

        var onIt = new ClientProfile.WindowGeometry(100, 100, 800, 600, false);
        var farAway = new ClientProfile.WindowGeometry(4000, 2000, 800, 600, false);

        assertThat(WindowRegistry.isOnAScreen(onIt, oneScreen)).isTrue();
        assertThat(WindowRegistry.isOnAScreen(farAway, oneScreen)).isFalse();
    }

    @Test
    @DisplayName("a window hung half off the edge is still considered reachable")
    void partiallyOffScreenIsFine() {
        // A window deliberately pushed past the edge is a normal thing a player does; snapping it
        // back would be the annoying kind of helpful.
        List<Rectangle2D> oneScreen = List.of(new Rectangle2D(0, 0, 1440, 900));
        var halfOff = new ClientProfile.WindowGeometry(1380, 400, 800, 600, false);
        assertThat(WindowRegistry.isOnAScreen(halfOff, oneScreen)).isTrue();
    }
}
