package io.github.stoicswe.eyeandsickle.client.window;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Command;
import io.github.stoicswe.eyeandsickle.client.shell.ExitStatus;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the keyboard surface and for the GUI/terminal parity rule.
 *
 * <p>The important one is {@link Parity} — it pins client pillar <b>C1</b> mechanically, so a future
 * action added to the session cannot quietly become terminal-only.
 */
class ShortcutsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private static Shell shell(Path dir) {
        var session = new LocalGameSession(io.github.stoicswe.eyeandsickle.client.support.TestSaves.bare(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")), "op", CLOCK));
        return new Shell(session, BuiltinCommands.registry());
    }

    @Nested
    @DisplayName("shortcuts")
    class Bindings {

        @Test
        @DisplayName("no global shortcut collides with a window accelerator")
        void noCollisions() {
            // Both sets are installed on every Scene, so a collision means one of them silently
            // stops working — and the one that loses is whichever was registered second, which is
            // not a property anybody should have to reason about.
            Set<String> windows = new HashSet<>();
            for (WindowSpec spec : WindowSpec.values()) {
                windows.add(spec.combination().getName());
            }
            for (KeyCombination global : GlobalShortcuts.bindings()) {
                assertThat(windows)
                        .as("global shortcut %s collides with a window accelerator", global.getName())
                        .doesNotContain(global.getName());
            }
        }

        @Test
        @DisplayName("the six documented global shortcuts all exist")
        void allDocumentedShortcutsBound() {
            // docs/client/00 §6.3's table, minus the ones that belong to a window (Shortcut+0..9)
            // and Shortcut+F, which is per-window find and not global.
            assertThat(GlobalShortcuts.bindings()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("GUI and terminal parity — pillar C1")
    class Parity {

        @Test
        @DisplayName("every command the palette lists can actually be run from it")
        void paletteCoversTheCatalogue(@TempDir Path dir) {
            // The palette runs through the same Shell the terminal does, so "listed" and "runnable"
            // cannot drift apart. This checks the registry has no entry that would 127.
            Shell s = shell(dir);
            for (Command command : s.registry().commands()) {
                assertThat(s.registry().find(command.name()))
                        .as("palette lists %s but the registry cannot resolve it", command.name())
                        .isPresent();
            }
        }

        @Test
        @DisplayName("every command carries a synopsis, because the palette searches on it")
        void everyCommandIsSearchable(@TempDir Path dir) {
            // A command with no synopsis is findable only by somebody who already knows its name,
            // which defeats the palette's entire purpose — discovering what the game can do.
            for (Command command : shell(dir).registry().commands()) {
                assertThat(command.synopsis())
                        .as("%s has no synopsis", command.name())
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("abort exists, so Shortcut+. is not a no-op")
        void abortIsBound(@TempDir Path dir) {
            Shell s = shell(dir);
            assertThat(s.registry().find("abort")).isPresent();
            // Reports honestly that there is nothing to abort rather than claiming to have aborted
            // nothing — the minigame is still [PROPOSAL].
            assertThat(s.run("abort").status()).isEqualTo(ExitStatus.OK);
            assertThat(String.join(" ", s.run("abort").lines())).contains("Nothing to abort");
        }

        @Test
        @DisplayName("the actions a tool window offers are the same ones its command offers")
        void actionsAgree(@TempDir Path dir) {
            // Both paths funnel through GameSession, so a GUI button and its command cannot produce
            // different outcomes. Checked by running the command form and asserting the session
            // changed the same way a button press would.
            Shell s = shell(dir);

            assertThat(s.run("mine --allocate=40").status()).isEqualTo(ExitStatus.OK);
            assertThat(s.session().computeBudget().allocated().cycles()).isEqualTo(40);

            // UI-6: a running scan HOLDS its 5 cycles, so they land in `allocated` beside the 40
            // committed to mining above. They only move to `recovering` when the scan ends.
            assertThat(s.run("scan --quick").status()).isEqualTo(ExitStatus.OK);
            assertThat(s.session().computeBudget().allocated().cycles()).isEqualTo(45);
            assertThat(s.session().computeBudget().recovering().cycles()).isZero();
        }

        @Test
        @DisplayName("a gated purchase reports 77 with the requirement, from either path")
        void gatesExplainThemselves(@TempDir Path dir) {
            // docs/client/04 §3.5: 77 means "a gate blocks this, and the requirement is printed".
            // The GUI button and the session call are the same call, so the message is the same.
            //
            // ⚠ T3, not T1. This named `detection-array-t1` until 2026-08-06, when T1 and T2 moved to
            // the ethecoin gate and only the ladder's top rung stayed behind a schematic — so the T1
            // purchase became merely UNAFFORDABLE (status 1) rather than GATED (77), and the test
            // started measuring an empty wallet instead of a gate. Pointing it at T3 keeps its
            // subject and now also pins the rule that replaced the old one: money reaches the highest
            // rung of a ladder and never the rung above it, which is Invariant I2.
            var session = shell(dir).session();
            var outcome = session.purchase("detection-array-t3");

            assertThat(outcome.status())
                    .isEqualTo(io.github.stoicswe.eyeandsickle.client.session.GameSession.Outcome.NOPERM);
            assertThat(outcome.message()).contains("schematic").contains("never");
        }

        @Test
        @DisplayName("an affordable purchase works and lands in the vault")
        void purchaseWorks(@TempDir Path dir) {
            var session = shell(dir).session();
            session.getClass();
            ((LocalGameSession) session).game().credit(Balance.ec("50"), "TEST", "seed");

            var outcome = session.purchase("canary-token");
            assertThat(outcome.succeeded()).isTrue();
            // ⚠ NOT in the vault. Changed 2026-07-29: buying takes the money and starts a download;
            // the package lands in Downloads and installs when the payment is mined. This test is
            // about the command being reachable, so it asserts the money moved and the download
            // started — PurchaseFlowTest owns the rest of the journey.
            assertThat(session.items(io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT))
                    .noneMatch(i -> i.displayName().equals("Canary Token"));
            assertThat(session.transfers()).isNotEmpty();
            assertThat(outcome.message()).contains("downloading");
        }

        /**
         * ⚠ <b>AMENDED 2026-08-06 — I1 now has exactly one hole, and this is where it is measured.</b>
         *
         * <p>This used to read "nothing sells capacity", which was I1 and I12 made structural. On
         * explicit direction the compute ladder's <b>first rung</b> (24 → 32) is purchasable, and the
         * test that was protecting the invariant is the right place to record that — weakening it to
         * a blanket allowance would have been the wrong edit, because the safety argument is
         * precisely that the exception is <em>one item wide</em>.
         *
         * <p>So it now asserts the narrower, stronger thing: exactly one ethecoin offering may raise
         * the compute ceiling, it must be the ladder's bottom rung, and <b>vault capacity is still
         * unpurchasable at any price</b> (I12 is untouched — it never had an amendment and this test
         * still holds the whole of it).
         */
        @Test
        @DisplayName("exactly ONE offering sells compute, and nothing sells vault capacity — I1 amended, I12 intact")
        void nothingSellsCapacity() {
            var catalogue = io.github.stoicswe.eyeandsickle.engine.Catalogue.offerings();

            // ── I1, amended: one rung, and it is the bottom one ──────────────────────────────
            var sellingCompute = catalogue.stream()
                    .filter(o -> o.purchasable())
                    .filter(o -> io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungFor(o.id())
                            .isPresent())
                    .toList();
            assertThat(sellingCompute)
                    .as("compute is the master scarcity; a SECOND purchasable rung closes the "
                            + "mine-buys-mining loop that I1 exists to prevent")
                    .hasSize(1);
            assertThat(sellingCompute.getFirst().id())
                    .isEqualTo(io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()
                            .getFirst()
                            .itemType());

            // ── and nothing ELSE may raise a ceiling by another name ─────────────────────────
            for (var o : catalogue) {
                if (io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungFor(o.id())
                        .isPresent()) {
                    continue;
                }
                String text = (o.name() + " " + o.description()).toLowerCase();
                assertThat(o.purchasable() && text.contains("capacity"))
                        .as("%s appears to sell capacity for ethecoin", o.id())
                        .isFalse();
            }

            // ── I12: vault capacity is unpurchasable, unamended, at any price ────────────────
            assertThat(catalogue)
                    .as("I12 has no exception and this test still holds all of it")
                    .noneMatch(o -> o.id().contains("vault"));
        }
    }

    @Nested
    @DisplayName("the catalogue is honest about gates")
    class Gates {

        @Test
        @DisplayName("a non-ethecoin offering has no price, and an ethecoin one does")
        void pricesMatchGates() {
            // A price on a schematic-gated item would be exactly the I2 violation the gate exists to
            // prevent: money buying a permanent ceiling.
            for (var o : io.github.stoicswe.eyeandsickle.engine.Catalogue.offerings()) {
                if (o.purchasable()) {
                    assertThat(o.priceWei()).as("%s", o.id()).isPositive();
                } else {
                    assertThat(o.priceWei()).as("%s", o.id()).isZero();
                    assertThat(o.gateRequirement())
                            .as("%s must say why", o.id())
                            .isNotBlank();
                }
            }
        }

        @Test
        @DisplayName("ethecoin prices sit inside the published bands")
        void pricesRespectTheBands() {
            // docs/design/03 §2 publishes the bands and CLAUDE.md warns the economy numbers are
            // calibrated as a set. An offering priced outside them is a balance change wearing a
            // content change's clothes.
            for (var o : io.github.stoicswe.eyeandsickle.engine.Catalogue.offerings()) {
                // ⚠ THE COMPUTE RUNG IS OUTSIDE THE BANDS ON PURPOSE, and exempting it here is the
                // honest way to say so rather than widening PRICE_TOP_PURCHASABLE — which would let
                // every other item creep up behind it.
                //
                // `design/03` §2's bands price TOOLS. This is not a tool: it is the one thing in the
                // game that raises the master scarcity's ceiling, and Balance.COMPUTE_32_PRICE is
                // deliberately about six times the top band because the I1 amendment is only safe
                // while the purchase hurts. `ComputeLadderTest` is what holds it to one rung.
                if (io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungFor(o.id())
                        .isPresent()) {
                    // ⚠ Only the PURCHASABLE rung has a price to check — the two above it are
                    // schematic-gated and priced at zero, which `ComputeLadderTest` holds. Asserting
                    // "far above the bands" on those failed on the zero, which is the guard working.
                    if (o.purchasable()) {
                        assertThat(o.priceWei())
                                .as("the compute rung must stay far ABOVE the tool bands, not drift into them")
                                .isGreaterThan(io.github.stoicswe.eyeandsickle.engine.Balance.PRICE_TOP_PURCHASABLE);
                    }
                    continue;
                }
                if (o.purchasable()) {
                    assertThat(o.priceWei())
                            .as("%s is outside every published band", o.id())
                            .isBetween(
                                    io.github.stoicswe.eyeandsickle.engine.Balance.PRICE_CONSUMABLE_MIN,
                                    io.github.stoicswe.eyeandsickle.engine.Balance.PRICE_TOP_PURCHASABLE);
                }
            }
        }
    }

    /** Kept so the unused-import check stays honest about what this file exercises. */
    @Test
    @DisplayName("the window catalogue and the global set together cover §6.3")
    void documentedSurface() {
        List<KeyCombination> globals = GlobalShortcuts.bindings();
        assertThat(globals).isNotEmpty();
        // ⚠ This was `values().length >= 16` — an arbitrary FLOOR on the number of windows, which
        // fails every time tools are consolidated and passes for every reason except the one it
        // meant. It also duplicated WindowCatalogueTest, which pins the exact set by id. What §6.3
        // actually requires is that the catalogue is reachable from the keyboard, so that is what is
        // asserted: every accelerator a window declares is distinct, and none collides with a global.
        List<KeyCombination> perWindow = java.util.Arrays.stream(WindowSpec.values())
                .map(WindowSpec::combination)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertThat(perWindow).doesNotHaveDuplicates();
        assertThat(perWindow).doesNotContainAnyElementsOf(globals);
    }
}
