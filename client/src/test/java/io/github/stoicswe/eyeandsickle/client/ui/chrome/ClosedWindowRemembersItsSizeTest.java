package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The one thing a close handler needs and could not have: the size the window was.
 *
 * <h2>⚠ The bug this exists for, and why the code that had it looked right</h2>
 *
 * {@code DeckShell.rememberSize} recorded a closing window's size by looking the window up in
 * {@code desk.windows()}. Its own comment said the read happened <em>"at the moment the geometry
 * still exists"</em>. It did not: {@link DeskManager#close} removes the window from its map
 * <b>before</b> invoking {@code onClosed}, deliberately and with its own comment explaining why —
 * the shell's callback ends the session, which closes the window again, and firing before the
 * removal would recurse.
 *
 * <p>So the lookup found nothing, {@code ifPresent} did nothing, and <b>a window closed by hand
 * never recorded its size</b>. Every resize a player made was thrown away by the one action most
 * likely to follow it. The only reason {@code windowSizes} was ever populated at all is
 * {@code saveLayout}, which covers windows that were still OPEN at quit — the other case entirely.
 *
 * <p>⚠ Two comments, both correct in isolation, describing incompatible orderings — which is the
 * same failure shape as {@code NetRules.reconcileFootholds} and {@code Spec.onClosed} itself, both
 * of which this repo has already recorded. A unit test on either side sees nothing wrong.
 *
 * <p>The fix is to stop making the callback ask: {@code onClosed} is handed the geometry the window
 * had, because the only party that can still see it is the one doing the removal.
 */
class ClosedWindowRemembersItsSizeTest {

    @org.junit.jupiter.api.BeforeAll
    static void toolkit() throws Exception {
        // ⚠ Same treatment as ShellWindowClosesSessionTest and NodeMenuTest: DeskManager builds real
        // WindowFrames, which need the toolkit, and CI's Linux image has no DISPLAY. Aborted rather
        // than caught-and-passed — a regression test reporting success without executing is worse
        // than none.
        java.util.concurrent.CountDownLatch up = new java.util.concurrent.CountDownLatch(1);
        try {
            javafx.application.Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        } catch (UnsupportedOperationException | NoClassDefFoundError | ExceptionInInitializerError headless) {
            org.junit.jupiter.api.Assumptions.abort(
                    "no display — the JavaFX toolkit cannot start here: " + headless.getMessage());
        }
        if (!up.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            org.junit.jupiter.api.Assumptions.abort("the JavaFX toolkit did not start within 20s");
        }
    }

    private static void onFxThread(Runnable body) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        javafx.application.Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("the FX thread threw", failure.get());
        }
    }

    private static DeskManager.Spec spec(String id, java.util.function.Consumer<DeskManager.Geometry> onClosed) {
        return new DeskManager.Spec(id, id, "", new javafx.scene.layout.Pane(), 400, 300, true, onClosed);
    }

    @Nested
    @DisplayName("the close handler")
    class Handler {

        @Test
        @DisplayName("⚠ is told the size the window was, which it could not otherwise find out")
        void receivesTheGeometry() throws Exception {
            onFxThread(() -> {
                DeskManager desk = new DeskManager();
                AtomicReference<DeskManager.Geometry> seen = new AtomicReference<>();
                desk.open(spec("ledger", seen::set)).orElseThrow();

                // A resize, which is the whole point: the player made this window their size, and it
                // is that size the close has to survive with.
                desk.windows().getFirst().setGeometry(new DeskManager.Geometry(40, 60, 880, 540));
                desk.close("ledger");

                assertThat(seen.get()).as("the handler was handed a geometry").isNotNull();
                assertThat(seen.get().width()).isEqualTo(880);
                assertThat(seen.get().height()).isEqualTo(540);
            });
        }

        @Test
        @DisplayName("⚠ and the window is already off the desk by then — the reason it has to be told")
        void theWindowIsGoneByThen() throws Exception {
            // ⚠ THIS IS THE ASSERTION THAT NAMES THE BUG. `close` removes from the map before firing
            // the callback, on purpose: the shell's handler ends the session, which closes the window
            // again, and firing first would recurse. So any handler that tries to LOOK UP the closing
            // window — which is exactly what rememberSize did — finds nothing.
            onFxThread(() -> {
                DeskManager desk = new DeskManager();
                AtomicReference<Boolean> stillListed = new AtomicReference<>();
                desk.open(spec("ledger", geometry -> stillListed.set(
                                desk.windows().stream().anyMatch(w -> w.id().equals("ledger")))))
                        .orElseThrow();

                desk.close("ledger");

                assertThat(stillListed.get())
                        .as("a handler cannot find its own window; it must be handed the geometry")
                        .isFalse();
            });
        }

        @Test
        @DisplayName("fires for the [×] control, not only for a programmatic close")
        void firesForTheControl() throws Exception {
            // The [×] goes straight to the window manager — it is the path that skipped the rules
            // entirely when Spec.onClosed was accepted and dropped.
            onFxThread(() -> {
                DeskManager desk = new DeskManager();
                AtomicReference<DeskManager.Geometry> seen = new AtomicReference<>();
                DeskManager.DeskWindow window =
                        desk.open(spec("ledger", seen::set)).orElseThrow();
                window.setGeometry(new DeskManager.Geometry(0, 0, 700, 420));

                // The real control's own action, which is what the [x] label runs.
                window.frame().closeAction().run();

                assertThat(seen.get()).isNotNull();
                assertThat(seen.get().width()).isEqualTo(700);
            });
        }

        @Test
        @DisplayName("⚠ a window closed while MAXIMISED reports the size the player chose, not the desk's")
        void expandedReportsTheRestorePoint() {
            // ⚠ THE DEFECT THIS PREVENTS IS PERMANENT AND SILENT. A maximised window's geometry is
            // the DESK's, so remembering it would reopen that tool full-desk forever after — every
            // session, with nothing on screen to say why or any obvious way to undo it. `saveLayout`
            // has always kept `geometry` and `restorePoint` apart for this reason; the close path
            // has to make the same distinction.
            try {
                onFxThread(() -> {
                    DeskManager desk = new DeskManager();
                    AtomicReference<DeskManager.Geometry> seen = new AtomicReference<>();
                    DeskManager.DeskWindow window =
                            desk.open(spec("ledger", seen::set)).orElseThrow();
                    window.setGeometry(new DeskManager.Geometry(30, 30, 640, 400));

                    window.toggleMaximized();
                    assertThat(window.isExpanded()).as("the fixture must actually expand it").isTrue();
                    desk.close("ledger");

                    assertThat(seen.get().width())
                            .as("the player's width, not the desk's")
                            .isEqualTo(640);
                    assertThat(seen.get().height()).isEqualTo(400);
                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Test
        @DisplayName("a window with no handler closes without complaint")
        void handlerIsOptional() throws Exception {
            onFxThread(() -> {
                DeskManager desk = new DeskManager();
                desk.open(new DeskManager.Spec(
                        "calc", "Calc", "", new javafx.scene.layout.Pane(), 300, 200, true));
                desk.close("calc");
                assertThat(desk.windows()).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("the rule")
    class Rule {

        @Test
        @DisplayName("a remembered size wins over the catalogue default")
        void rememberedWins() {
            var remembered = new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.DeskWindowState();
            remembered.width = 880;
            remembered.height = 540;

            var size = io.github.stoicswe.eyeandsickle.client.ui.DeckShell.openSizeFor(
                    io.github.stoicswe.eyeandsickle.client.window.WindowSpec.LEDGER, remembered);

            assertThat(size.width()).isEqualTo(880);
            assertThat(size.height()).isEqualTo(540);
        }

        @Test
        @DisplayName("and nothing remembered means the catalogue default, scaled")
        void defaultOtherwise() {
            var spec = io.github.stoicswe.eyeandsickle.client.window.WindowSpec.LEDGER;
            var size = io.github.stoicswe.eyeandsickle.client.ui.DeckShell.openSizeFor(spec, null);

            assertThat(size.width())
                    .isEqualTo(spec.defaultWidth() * io.github.stoicswe.eyeandsickle.client.ui.UiTokens.WINDOW_OPEN_SCALE);
        }

        @Test
        @DisplayName("⚠ a degenerate entry falls back rather than opening a window that is not there")
        void degenerateFallsBack() {
            // ⚠ Zero is what an entry written before the window had ever been laid out carries, and a
            // window opened at 0 x 0 is invisible with no way for a player to tell what happened.
            // Defaulting is the recoverable failure; honouring the entry is not.
            var spec = io.github.stoicswe.eyeandsickle.client.window.WindowSpec.LEDGER;
            for (double[] bad : new double[][] {{0, 0}, {0, 400}, {400, 0}, {-10, -10}}) {
                var state = new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.DeskWindowState();
                state.width = bad[0];
                state.height = bad[1];
                assertThat(io.github.stoicswe.eyeandsickle.client.ui.DeckShell.openSizeFor(spec, state).width())
                        .as("%s x %s", bad[0], bad[1])
                        .isEqualTo(spec.defaultWidth() * io.github.stoicswe.eyeandsickle.client.ui.UiTokens.WINDOW_OPEN_SCALE);
            }
        }
    }

    @Nested
    @DisplayName("across a restart")
    class Restart {

        @Test
        @DisplayName("⚠ the size is on disk, so a new process opens the window the player's size")
        void survivesAReload(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            // ⚠ THE HALF THAT MAKES THIS FEATURE WORTH HAVING, and the half a unit test on the map
            // cannot see. windowSizes is a field on Settings; whether it round-trips through Jackson
            // is a fact about the serialiser and the class shape, not about the desk.
            var profile = new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile(dir);
            var state = new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.DeskWindowState();
            state.width = 902;
            state.height = 638;
            profile.settings().windowSizes.put("ledger", state);
            profile.save();

            // A different ClientProfile over the same directory is what a restart is.
            var reloaded = new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile(dir);
            var back = reloaded.settings().windowSizes.get("ledger");

            assertThat(back).as("the entry survived the process").isNotNull();
            assertThat(back.width).isEqualTo(902);
            assertThat(back.height).isEqualTo(638);
            assertThat(io.github.stoicswe.eyeandsickle.client.ui.DeckShell.openSizeFor(
                            io.github.stoicswe.eyeandsickle.client.window.WindowSpec.LEDGER, back)
                    .width())
                    .isEqualTo(902);
        }
    }
}
