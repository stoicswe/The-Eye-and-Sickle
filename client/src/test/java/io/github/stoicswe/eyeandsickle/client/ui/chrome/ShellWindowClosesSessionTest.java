package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Closing a shell window hands its cycles back.
 *
 * <h2>⚠ The bug this exists for, and why every existing test passed while it shipped</h2>
 *
 * A shell holds {@link Balance#SESSION_CYCLES} for as long as it is open. Two things can end one and
 * only one of them worked:
 *
 * <ul>
 *   <li>Typing {@code exit} — the shell view asks the rules to close the session, then asks the desk
 *       to close the window. Cycles released. ✓
 *   <li>Clicking the window's {@code [×]} — straight to the window manager. The frame vanished and
 *       <b>the allocation stayed reserved</b>, with nothing left on screen to release it. The rig
 *       monitor showed compute held by a shell the player could not see, and only a restart cleared
 *       it. ✗
 * </ul>
 *
 * <p>{@code DeskManager.Spec} accepted an {@code onClosed} callback and dropped it: it was declared,
 * passed in by {@code DeckShell.showShell}, and never invoked. Both halves looked right in isolation
 * — {@code SessionRulesTest} proves the rules release correctly, and the window manager closes
 * windows correctly — and the defect lived in the join, which is precisely where a unit test on
 * either side cannot look. Same failure shape as {@code NetRules.reconcileFootholds}, which was
 * written, documented, tested, and called by nothing.
 *
 * <p>⚠ This test is therefore written at the level the bug is visible: a real session, a real desk,
 * and the compute budget checked before and after a <b>window</b> close.
 */
class ShellWindowClosesSessionTest {

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");

    /**
     * Starts the JavaFX toolkit, or <b>skips this class</b> when there is no display.
     *
     * <p>⚠ Same treatment as {@code NodeMenuTest}, and for the same reason: {@code DeskManager}
     * builds real {@code WindowFrame}s, which touch {@code Label}, which needs the toolkit. GitHub
     * Actions' Linux image has no {@code DISPLAY}, and letting that fail the build would trade one
     * uncovered join for a red build on every push.
     *
     * <p>⚠ <b>Aborted, not caught and passed.</b> A regression test reporting success without
     * executing is worse than none. It runs on any developer machine — which is where this bug was
     * found and where a window-manager change is made. ⚠ It therefore guards nothing in CI until
     * {@code xvfb-run} or Monocle is wired up; that gap is recorded in {@code CLAUDE.md} and is not
     * this test's to close.
     */
    @org.junit.jupiter.api.BeforeAll
    static void toolkit() throws Exception {
        java.util.concurrent.CountDownLatch up = new java.util.concurrent.CountDownLatch(1);
        try {
            javafx.application.Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        } catch (UnsupportedOperationException | NoClassDefFoundError
                | ExceptionInInitializerError headless) {
            org.junit.jupiter.api.Assumptions.abort(
                    "no display — the JavaFX toolkit cannot start here: " + headless.getMessage());
        }
        if (!up.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            org.junit.jupiter.api.Assumptions.abort("the JavaFX toolkit did not start within 20s");
        }
    }

    /** Runs on the FX thread and rethrows whatever happened there. */
    private static void onFxThread(Runnable body) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
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
    private static final String ADDRESS = "10.0.0.5";

    /** A rig holding a foothold on one machine, so a shell may legally open on it. */
    private static LocalGameSession sessionOn(Path dir) {
        SoloGame game = SoloGame.open(
                new SaveStore(dir.resolve("s.json")), "operator", Clock.fixed(T0, ZoneOffset.UTC));
        HostState host = game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.address = ADDRESS;
        host.discovered = true;
        host.foothold = true;
        return new LocalGameSession(game);
    }

    private static long heldCycles(LocalGameSession session) {
        return session.computeBudget().allocated().cycles();
    }

    @Test
    @DisplayName("closing the window releases the shell's cycles, exactly as typing exit does")
    void closingTheWindowEndsTheSession(@TempDir Path dir) throws Exception {
      onFxThread(() -> {
        LocalGameSession session = sessionOn(dir);
        long idle = heldCycles(session);

        assertThat(session.openSession(ADDRESS).succeeded()).isTrue();
        assertThat(heldCycles(session))
                .as("an open shell holds its cycles")
                .isEqualTo(idle + Balance.SESSION_CYCLES);

        // The desk closing the window is the whole subject: no `exit`, no call to the rules.
        DeskManager desk = new DeskManager();
        desk.open(new DeskManager.Spec(
                "shell:" + ADDRESS, "Shell", ADDRESS, new javafx.scene.layout.Pane(), 760, 520, true,
                () -> session.closeSession(ADDRESS)));
        desk.close("shell:" + ADDRESS);

        assertThat(heldCycles(session))
                .as("closing the window must hand the cycles back")
                .isEqualTo(idle);
        assertThat(session.sessions()).isEmpty();
      });
    }

    /**
     * ⚠ The callback may re-enter {@code close} for the same id, and that must terminate.
     *
     * <p>A shell's callback ends the session; ending a session from inside the shell also closes the
     * window. The window is removed from the map before the callback runs, so the second call finds
     * nothing — but a regression that fired the callback first would recurse until the stack ran out,
     * on a mouse click.
     */
    @Test
    @DisplayName("a callback that closes the same window again does not recurse")
    void reentrantCloseTerminates() throws Exception {
      onFxThread(() -> {
        DeskManager desk = new DeskManager();
        AtomicInteger runs = new AtomicInteger();
        desk.open(new DeskManager.Spec(
                "shell:loop", "Shell", "loop", new javafx.scene.layout.Pane(), 760, 520, true,
                () -> {
                    runs.incrementAndGet();
                    desk.close("shell:loop");
                }));

        desk.close("shell:loop");

        assertThat(runs).hasValue(1);
        assertThat(desk.find("shell:loop")).isEmpty();
      });
    }

    /**
     * ⚠ An ordinary tool window has no callback and must not grow one.
     *
     * <p>A tool is a view onto state that exists whether or not it is on screen, so closing one has
     * to change nothing. Only a window whose existence <em>is</em> game state — a shell — releases
     * anything.
     */
    @Test
    @DisplayName("a window with no callback closes without incident")
    void ordinaryWindowsHaveNothingToRelease() throws Exception {
      onFxThread(() -> {
        DeskManager desk = new DeskManager();
        desk.open(new DeskManager.Spec(
                "ledger", "Ledger", "", new javafx.scene.layout.Pane(), 400, 300, true));
        desk.close("ledger");
        assertThat(desk.find("ledger")).isEmpty();
      });
    }
}
