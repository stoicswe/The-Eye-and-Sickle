package io.github.stoicswe.eyeandsickle.client.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
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
    @DisplayName(
            "the catalogue is 05 §2.1's fifteen, less `map`, plus `man`, `log`, `breach`, `netmap`, `calc`, `files`")
    void catalogueMatchesTheDocuments() {
        // ⚠ The two documents disagree about the size of a table both call closed: docs/client/05
        // §2.1 lists fifteen and never absorbed the `man` window that docs/client/04 §4.6 adds and
        // flags as T-1. Building it and reporting the discrepancy beats silently dropping the way a
        // player reaches the teaching layer — which is client pillar C6.
        //
        // Nineteen: §2.1's fifteen MINUS `map`, plus `man` (T-1), `log`, `breach` — the core loop
        // (docs/design/05), which §2.1 could not list because the minigame had no rules when that
        // table was written — `netmap`, the network tool, and `calc`.
        //
        // `files` was ADDED on 2026-07-28 — the file manager. It earns its slot the same way
        // `calc` does, on pillar C6: the filesystem hierarchy is real, transfers to any Linux
        // machine, and until this window existed the game had a filesystem nobody could look at.
        // It is also where a machine you hold is MOUNTED, which is deliberately the same fact as an
        // open shell session rather than a second one — see FileManagerView.
        //
        // `calc` was ADDED on 2026-07-28 and earns its slot on pillar C6 rather than on a game
        // system: docs/education/01-foundations.md's whole first domain is bases, bit width, two's
        // complement, byte order and overflow, and until this window existed the client had no
        // surface that made any of them touchable. It is also the only window that takes no session,
        // which is why it can be added without checking a single invariant — there is no game state
        // in a calculator to get wrong.
        //
        // ⚠ `map` was REMOVED on 2026-07-27 and this is the assertion that says so. It was a
        // second network window holding a read-only node table, on the same Shortcut+2 `netmap`
        // now owns. It had no sweep control, so it was permanently empty for anyone who had not
        // swept elsewhere, and it carried a stale note reading "Breach targeting is not built".
        // `netmap` has had a LIST view on a chip the whole time, so nothing was lost with it.
        // ⚠ AMENDED 2026-08-04 — twenty became sixteen. FIVE TOOLS BECAME TABS: `recon` and
        // `breach` into `netmap`, `audit` and `defense` into `rig-monitor`, `mining` into `ledger`.
        // Nothing was deleted — the same views are reparented — and the count moving is the point of
        // this assertion: a window quietly disappearing is exactly what it exists to catch.
        //
        // ⚠ `botnet` JOINED THEM on 2026-08-04 — a sixth tool folded in, as a fourth tab of the
        // network tool. A bot is the residue of the three tabs to its left: you find a machine,
        // study it, get in, and this is what you left running on it.
        //
        // ⚠ `security` — the Security Center — was ADDED the same day, and it is `audit` and
        // `defense` coming back OUT of the rig monitor. They were tabs there for part of one
        // afternoon; the monitor asks whether something is wrong and these two are what you do about
        // it, and burying the answer four tabs into a window titled something else made it harder to
        // reach than the question. ⚠ Both views moved twice and neither lost anything, which is only
        // true because neither ever held its own state.
        //
        // ⚠ `switcher` was REMOVED on 2026-08-04. It listed every tool, open or not, and was "the
        // way back to a window you lost" — but the RAIL is already that: it carries one chip per
        // window in the catalogue, lit when open, and clicking one calls `show`, which UN-MINIMISES.
        // So nothing is stranded by its removal, which is the only thing that made it load-bearing.
        // Verified before deleting rather than assumed.
        //
        // `assembl` was ADDED. A schematic is a blueprint now rather than a purchase gate, so the
        // storefront no longer offers schematic-gated items at any price and the thing you do with a
        // schematic needs somewhere to happen. Compile mechanics are open as AS-1.
        //
        // ⚠ Folding `breach` in CONTRADICTS docs/client/05 §44, knowingly: a breach is meant to span
        // windows and the puzzle's anti-bot property (I10) depends on cross-referencing two
        // documents at once. Nothing breaks while the minigame is unbuilt; UI-8 records that it
        // probably has to come back out when it is.
        // ⚠ `identity` was REMOVED on 2026-08-05 and did NOT simply go away: the operator's name and
        // face were already on the top strip doing nothing when clicked, and "who am I" sitting in
        // the rail beside the terminal and the market put it on the same footing as a tool. It is a
        // panel that slides out of the OPERATOR cell now (`Views.operatorProfile`), which is where an
        // operating system puts it, and it hands the rail's slot back. ⚠ Verified before deleting:
        // everything the window carried — handle, mode, heat, balance — is on that panel, plus the
        // identifier and the standings it never showed.
        // ⚠ `notes` was ADDED 2026-08-06 — a markdown notebook, sitting above the calculator. It
        // earns its slot the way `calc` does, on pillar C6 rather than on a game system: the game
        // hands a player addresses, handles, block heights and recovered documents faster than
        // anybody holds them, and until this the only place to put them was outside the game.
        // ⚠ Nothing a player writes there is read by any rule (`rules/Notes`), so — like `calc` — it
        // was added without an invariant to check.
        assertThat(WindowSpec.values()).hasSize(15);
        assertThat(java.util.Arrays.stream(WindowSpec.values())
                        .map(WindowSpec::id)
                        .toList())
                .containsExactlyInAnyOrder(
                        "rig-monitor",
                        "terminal",
                        "storage",
                        "ledger",
                        "market",
                        "security",
                        "assembl",
                        "comms",
                        "settings",
                        "man",
                        "log",
                        "netmap",
                        "calc",
                        "notes",
                        "files");
    }

    @Test
    @DisplayName("⚠ there is exactly ONE network window, and it is the one with the sweep control")
    void onlyOneNetworkTool() {
        // The regression report that produced this: two windows both about the network, the
        // reachable one inert. A second network tool is not a feature, it is a coin flip the player
        // loses half the time.
        assertThat(WindowSpec.byId("map")).isEmpty();
        assertThat(WindowSpec.byId("netmap")).isPresent();
        assertThat(java.util.Arrays.stream(WindowSpec.values())
                        .filter(w ->
                                w.title().toLowerCase(java.util.Locale.ROOT).contains("network"))
                        .toList())
                .containsExactly(WindowSpec.NETMAP);
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

    /**
     * ⚠ The one window whose ON-SCREEN size was asked for by number, pinned as an on-screen size.
     *
     * <h2>Why this is not the same as asserting {@code defaultWidth()}</h2>
     *
     * Nothing opens a window at its declared size. {@code DeckShell} scales by
     * {@code UiTokens.WINDOW_OPEN_SCALE} and {@code DeskManager} snaps to {@code UiTokens.SNAP_GRID},
     * so the number in {@code WindowSpec} is nominal and the Security Center's row is a reverse
     * calculation from 655×550 (2026-08-06). A test that asserted 910×764 would be restating the
     * source line and would pass just as happily if either of those two constants moved — which is
     * precisely the change that would silently resize the window.
     *
     * <p>⚠ 660 rather than 655, and that is the grid rather than a rounding slip: 655 is not a
     * multiple of 22, so with snapping on (the default) it is not a reachable width at all. The
     * nearest are 638 and 660. With free-drag on nothing snaps and it opens at 655.2×550.1.
     */
    @Test
    @DisplayName("the Security Center opens at the size it was asked to open at")
    void theSecurityCentreOpensAtItsIntendedSize() {
        double width = WindowSpec.SECURITY.defaultWidth() * UiTokens.WINDOW_OPEN_SCALE;
        double height = WindowSpec.SECURITY.defaultHeight() * UiTokens.WINDOW_OPEN_SCALE;

        assertThat(width).as("unsnapped width").isCloseTo(655, within(1.0));
        assertThat(height).as("unsnapped height").isCloseTo(550, within(1.0));

        double grid = UiTokens.SNAP_GRID;
        assertThat(Math.round(width / grid) * grid).as("snapped width").isEqualTo(660);
        assertThat(Math.round(height / grid) * grid).as("snapped height").isEqualTo(550);
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
    @DisplayName("first run opens the rig monitor, and nothing else")
    void firstRunSet() {
        // ⚠ The switcher was the other one, and its removal leaves the rig monitor alone — which is
        // the right answer rather than a gap. A first run should show the machine, not a list of
        // things to open: the rail is that list, it is always on screen, and it does not have to be
        // dismissed before the desk is usable.
        assertThat(java.util.Arrays.stream(WindowSpec.values())
                        .filter(WindowSpec::openOnFirstRun)
                        .map(WindowSpec::id)
                        .toList())
                .containsExactly("rig-monitor");
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
