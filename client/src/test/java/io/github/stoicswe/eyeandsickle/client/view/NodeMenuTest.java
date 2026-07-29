package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Right-clicking a machine on the map.
 *
 * <h2>⚠ The bug this exists for, and why it was invisible until it shipped</h2>
 *
 * The handler selected the machine before showing the menu — correctly, so that the entries are about
 * the thing under the pointer rather than whatever was selected before. But selecting repaints, and
 * repainting <b>rebuilds the graph</b>, so the label the player right-clicked is detached from the
 * scene by the time the popup is anchored to it. JavaFX then throws
 * {@code "The owner node needs to be associated with a window"} — from the FX thread, on every
 * right-click of a node.
 *
 * <p>Nothing in the type system or in a render catches it: the code compiles, the menu is built
 * correctly, and a snapshot never right-clicks anything. The fix anchors the popup to the
 * <b>window</b>, captured before the repaint, and screen coordinates make that identical on screen.
 *
 * <p>⚠ A scene with no {@code Window} reproduces the same condition a detached node does, which is
 * what makes this testable headlessly: the unfixed code throws here for exactly the reason it threw
 * in the running client.
 */
class NodeMenuTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        }
        assertThat(up.await(20, TimeUnit.SECONDS)).isTrue();
    }

    /** Runs on the FX thread and rethrows whatever happened there. */
    private static void onFxThread(Runnable body) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("the FX thread threw", failure.get());
        }
    }

    @Test
    @DisplayName("right-clicking a machine does not throw when the anchor is rebuilt underneath it")
    void rightClickDoesNotThrow(@TempDir Path dir) throws Exception {
        onFxThread(() -> {
            SoloGame game = SoloGame.open(
                    new SaveStore(dir.resolve("s.json")), "operator", Clock.fixed(T0, ZoneOffset.UTC));
            LocalGameSession session = new LocalGameSession(game);
            Region map = NetMapView.create(session);
            // ⚠ A Scene with NO Stage — the same condition a node detached by a repaint is in, and
            // the reason this reproduces the shipped crash without a display.
            StackPane host = new StackPane(map);
            Scene scene = new Scene(host, 1100, 780);
            scene.getRoot().applyCss();
            host.layout();

            // ⚠ `.es-netmap-cell` AND a context handler. The first version of this test fired on
            // `.es-focusable`, which matches the sweep buttons and the legend as well — none of which
            // carries the node menu, so it right-clicked six things that had nothing to open and
            // passed against the broken code. A regression test that passes both ways is worse than
            // none: it reports the bug as fixed.
            List<Node> cells = map.lookupAll(".es-netmap-cell").stream()
                    .filter(node -> node.getOnContextMenuRequested() != null)
                    .toList();
            assertThat(cells)
                    .as("the map must draw at least one machine that carries the node menu")
                    .isNotEmpty();

            for (Node cell : cells.subList(0, Math.min(6, cells.size()))) {
                assertThatCode(() -> cell.fireEvent(new ContextMenuEvent(
                        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        4, 4, 40, 40, false, null)))
                        .as("right-clicking %s", cell instanceof Label label ? label.getText() : cell)
                        .doesNotThrowAnyException();
            }
        });
    }
}
