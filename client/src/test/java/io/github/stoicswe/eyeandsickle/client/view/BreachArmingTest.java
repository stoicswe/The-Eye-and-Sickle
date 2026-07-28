package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Arming, and the notification that makes it visible.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * The breach window subscribed to the <em>session</em> and to nothing else. Arming is not game state
 * — it is an intention the player has not acted on — so it does not travel through the session, and
 * picking a different row in the target list therefore changed the armed id and <b>repainted
 * nothing</b>: the launch panel kept naming the previous target and the row highlight never moved.
 * The list looked broken because the only thing listening for a change was the one thing that could
 * not hear about this one.
 *
 * <p>{@code BreachArming} carries no JavaFX, so the fan-out is assertable headlessly. What is not
 * assertable here is that {@code BreachView} actually subscribes; that is one line, and this class
 * exists so the line has something to be wrong against.
 */
class BreachArmingTest {

    @Test
    @DisplayName("arming a different target notifies, so a view bound to it repaints")
    void notifiesOnChange() {
        BreachArming arming = new BreachArming();
        List<String> seen = new ArrayList<>();
        arming.onChange(() -> seen.add(arming.armed()));

        arming.arm("node:10.0.0.4");
        arming.arm("node:10.0.0.9");

        assertThat(seen).containsExactly("node:10.0.0.4", "node:10.0.0.9");
    }

    @Test
    @DisplayName("re-arming the same target notifies nobody")
    void idempotent() {
        BreachArming arming = new BreachArming();
        List<String> seen = new ArrayList<>();
        arming.onChange(() -> seen.add(arming.armed()));

        arming.arm("node:10.0.0.4");
        arming.arm("node:10.0.0.4");

        // The guard that makes re-entrancy terminate: `refresh` clears a stale id by calling arm(""),
        // which notifies, which runs refresh again — and stops there only because the second arm("")
        // is a no-op.
        assertThat(seen).containsExactly("node:10.0.0.4");
    }

    @Test
    @DisplayName("clearing is a change like any other, so the launch panel can be told to go away")
    void clearing() {
        BreachArming arming = new BreachArming();
        List<String> seen = new ArrayList<>();
        arming.onChange(() -> seen.add(arming.armed()));

        arming.arm("miner:abc");
        assertThat(arming.isArmed()).isTrue();
        arming.arm("");
        assertThat(arming.isArmed()).isFalse();
        assertThat(seen).containsExactly("miner:abc", "");
    }

    @Test
    @DisplayName("⚠ rearm notifies even when the target has not changed")
    void rearmAlwaysNotifies() {
        BreachArming arming = new BreachArming();
        int[] calls = {0};
        arming.onChange(() -> calls[0]++);

        arming.arm("node:10.0.0.4");
        assertThat(calls[0]).isEqualTo(1);

        // arm() no-ops here, on purpose — it is called from inside the breach panel's own refresh.
        arming.arm("node:10.0.0.4");
        assertThat(calls[0]).as("arm on an unchanged id stays silent").isEqualTo(1);

        // rearm is the map's BREACH button: "start fresh on this machine", meant every time it is
        // pressed. Under the no-op the second press was inaudible, and a resolved outcome from the
        // previous attempt stayed on screen with no control but Dismiss.
        arming.rearm("node:10.0.0.4");
        assertThat(calls[0]).as("rearm on the same id is still heard").isEqualTo(2);
        assertThat(arming.armed()).isEqualTo("node:10.0.0.4");
    }

    @Test
    @DisplayName("rearm treats null as a clear, like arm does")
    void rearmHandlesNull() {
        BreachArming arming = new BreachArming();
        arming.arm("node:10.0.0.4");
        arming.rearm(null);
        assertThat(arming.armed()).isEmpty();
        assertThat(arming.isArmed()).isFalse();
    }

    @Test
    @DisplayName("a closed subscription stops being called")
    void unsubscribes() throws Exception {
        BreachArming arming = new BreachArming();
        List<String> seen = new ArrayList<>();
        AutoCloseable handle = arming.onChange(() -> seen.add(arming.armed()));

        arming.arm("node:1");
        handle.close();
        arming.arm("node:2");

        // ⚠ Load-bearing rather than tidy. BreachArming lives for the whole client rather than for
        // the window, so a listener left on it by a closed panel would call refresh against a
        // detached scene graph forever — and every re-open would add another.
        assertThat(seen).containsExactly("node:1");
    }

    @Test
    @DisplayName("nulls are absences, not exceptions")
    void nullsAreSafe() throws Exception {
        BreachArming arming = new BreachArming();
        assertThat(arming.armed()).isEmpty();
        arming.arm(null);
        assertThat(arming.isArmed()).isFalse();
        arming.onChange(null).close();
        // The opener is unset until the deck exists; opening before then must be a no-op rather
        // than a crash, because the map's BREACH control is live from the moment the panel is built.
        arming.open();
        arming.setOpener(null);
        arming.open();
    }
}
