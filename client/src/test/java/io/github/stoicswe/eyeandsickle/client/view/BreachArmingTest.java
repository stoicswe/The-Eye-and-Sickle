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
        // Same for the tab door. The map's node menu calls it from the moment the panel is built,
        // which is before NetworkView has registered anything.
        arming.focusBreach();
        arming.setBreachFocus(null);
        arming.focusBreach();
    }

    /**
     * The one-gesture start from the map's node menu, and the ways it must not fire.
     *
     * <h2>⚠ EVERY TEST HERE IS ABOUT SPENDING SOMETHING THAT IS NOT REFUNDED</h2>
     *
     * A breach reserves compute for the whole attempt and aborting does not give it back
     * ({@code docs/design/05} §4). So the interesting assertions are not that the start <em>happens</em>
     * — that is one line — but that a request made for one machine can never be collected by another,
     * and that a request nobody could act on dies rather than waiting for the next opportunity.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("the node menu's one-gesture start")
    class StartRequest {

        @Test
        @DisplayName("arms the target and asks for it, in one call")
        void armsAndRequests() {
            BreachArming arming = new BreachArming();
            List<String> seen = new ArrayList<>();
            arming.onChange(() -> seen.add(arming.armed()));

            arming.armAndStart("node:10.0.0.4");

            assertThat(arming.armed()).isEqualTo("node:10.0.0.4");
            assertThat(seen).as("the panel has to hear it").containsExactly("node:10.0.0.4");
            assertThat(arming.takeStartRequest("node:10.0.0.4")).isTrue();
        }

        @Test
        @DisplayName("is collectable exactly once")
        void takenOnce() {
            // ⚠ The panel refreshes on every session change, about once a second. A request that
            // could be read twice would begin a second breach on the next tick — on top of the one
            // it had just started.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            assertThat(arming.takeStartRequest("node:10.0.0.4")).isTrue();
            assertThat(arming.takeStartRequest("node:10.0.0.4"))
                    .as("a second collection")
                    .isFalse();
        }

        @Test
        @DisplayName("⚠ NEVER fires on a machine it was not asked for")
        void neverFiresOnAnotherTarget() {
            // The sequence that produces this is cheap to hit: right-click Breach on something the
            // rules will not accept, so nothing starts and the request survives; then arm a different
            // machine from the list — and that one is committed to with no press at all.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            assertThat(arming.takeStartRequest("node:10.0.0.9"))
                    .as("a request for .4 must not be collected by .9")
                    .isFalse();
        }

        @Test
        @DisplayName("⚠ asking with the wrong target CLEARS it, so it cannot fire later either")
        void aMismatchAlsoClears() {
            // Returning false is not enough on its own: if the request survived the mismatch it would
            // simply wait for the machine it named to come round again, which could be minutes later
            // and long after the player had forgotten asking.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            arming.takeStartRequest("node:10.0.0.9");

            assertThat(arming.takeStartRequest("node:10.0.0.4"))
                    .as("the request should not have survived the mismatch")
                    .isFalse();
        }

        @Test
        @DisplayName("⚠ re-pointing the arming drops the request")
        void rearmingDropsIt() {
            // Selecting another row in the target list, or the panel clearing a stale id, must not
            // leave a start pending. A request belongs to the machine it was made for.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            arming.arm("node:10.0.0.9");
            assertThat(arming.takeStartRequest("node:10.0.0.9")).isFalse();

            arming.armAndStart("node:10.0.0.4");
            arming.rearm("node:10.0.0.9");
            assertThat(arming.takeStartRequest("node:10.0.0.9")).isFalse();
        }

        @Test
        @DisplayName("re-pointing at the SAME target keeps it, so open-then-focus does not lose it")
        void rearmingTheSameTargetKeepsIt() {
            // The panel calls arm() from inside its own refresh, and the menu path runs armAndStart,
            // open() and focusBreach() in sequence — a refresh in the middle of that must not eat the
            // request before the panel has settled enough to act on it.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            arming.rearm("node:10.0.0.4");

            assertThat(arming.takeStartRequest("node:10.0.0.4"))
                    .as("the request is still for the machine that is still armed")
                    .isTrue();
        }

        @Test
        @DisplayName("can be dropped outright, for when nothing could act on it")
        void clearable() {
            // What the panel calls when a breach is already running, or when the machine turns out
            // not to be a target at all.
            BreachArming arming = new BreachArming();
            arming.armAndStart("node:10.0.0.4");

            arming.clearStartRequest();

            assertThat(arming.takeStartRequest("node:10.0.0.4")).isFalse();
            assertThat(arming.armed())
                    .as("clearing the request does not disarm")
                    .isEqualTo("node:10.0.0.4");
        }

        @Test
        @DisplayName("plain arming never requests a start — the list keeps its two steps")
        void armingAloneNeverStarts() {
            // ⚠ THE FENCE. The exemption is for the map's node menu and nothing else; if arm() or
            // rearm() ever carried a request, the target list would begin an attempt the instant a
            // row was selected, which is the exact defect BreachArming was created to fix.
            BreachArming arming = new BreachArming();

            arming.arm("node:10.0.0.4");
            assertThat(arming.takeStartRequest("node:10.0.0.4")).isFalse();

            arming.rearm("node:10.0.0.4");
            assertThat(arming.takeStartRequest("node:10.0.0.4")).isFalse();
        }

        @Test
        @DisplayName("null is a clear, and asks for nothing")
        void nullRequestsNothing() {
            BreachArming arming = new BreachArming();
            arming.armAndStart(null);
            assertThat(arming.isArmed()).isFalse();
            assertThat(arming.takeStartRequest("")).isFalse();
        }
    }
}
