package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.support.TestSaves;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.CycleGrid;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "Free" on a rig-monitor band, and what it means for each kind of consumer.
 *
 * <h2>What is being defended</h2>
 *
 * The menu is a <b>shortcut to a stop verb the rules already have</b> — stop self-mining, disarm a
 * measure, end a session — never a second way to take cycles back. A second way would need its own
 * answer to the Thermal Budget question and would eventually give a different one. So the assertions
 * here are about which rule each band reaches, and about which bands correctly offer nothing.
 *
 * <p>⚠ No toolkit. The mapping is a pure function from a consumer to a slice carrying an action, so
 * it is checkable without a scene — the seam {@code SecurityCenterView.latestOf} and
 * {@code DirectView.state} exist for, and for the same reason: the rule would otherwise live inside
 * a repaint and be reachable only by running the client and right-clicking.
 */
class RigFreeActionTest {

    private static final Instant T0 = Instant.parse("2026-08-08T09:00:00Z");

    private static LocalGameSession session(Path dir) {
        GameEngine game = TestSaves.bare(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        return new LocalGameSession(game);
    }

    /** The band for one consumer, or null when the panel offers no menu on it. */
    private static CycleGrid.Slice bandFor(
            LocalGameSession session, io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer consumer,
            List<String> unmounted) {
        return RigMonitorView.slices(
                        session.computeBudget(),
                        session.miningChain(),
                        RigMonitorView.free(session, unmounted::add))
                .stream()
                .filter(slice -> slice.freeText() != null)
                .filter(slice -> slice.label().equalsIgnoreCase(RigMonitorView.label(consumer)))
                .findFirst()
                .orElse(null);
    }

    @Nested
    @DisplayName("self-mining")
    class Mining {

        @Test
        @DisplayName("freeing it is exactly unallocating it")
        void stopsMining(@TempDir Path dir) {
            LocalGameSession session = session(dir);
            assertThat(session.allocateSelfMining(10).succeeded()).isTrue();
            long held = session.computeBudget().allocated().cycles();
            assertThat(held).isPositive();

            CycleGrid.Slice band = bandFor(
                    session, io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer.SELF_MINING,
                    new ArrayList<>());
            assertThat(band).as("a mining band offers Free").isNotNull();
            band.onFree().run();

            assertThat(session.computeBudget().allocated().cycles())
                    .as("the cycles came back")
                    .isLessThan(held);
        }
    }

    @Nested
    @DisplayName("bands with no stop verb")
    class NotOffered {

        @Test
        @DisplayName("⚠ a running task, a bot frame, a relay hop and a parasite offer no menu at all")
        void noMenu(@TempDir Path dir) {
            // ⚠ NOT OFFERED IS NOT REFUSED, and the distinction is the design. A sweep in flight has
            // no cancel in the rules, and a deployed miner's cycles are the HOST's by I6 and are not
            // the player's to hand back — a disabled "Free" on that band would invite exactly the
            // reading the grid works to prevent, that unattributed capacity is dismissable.
            LocalGameSession session = session(dir);
            for (var consumer : io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer.values()) {
                boolean freeable = switch (consumer) {
                    case SELF_MINING, DEFENSIVE_ARRAY, SHELL_SESSION -> true;
                    default -> false;
                };
                if (freeable) {
                    continue;
                }
                assertThat(bandFor(session, consumer, new ArrayList<>()))
                        .as("%s must offer no Free", consumer)
                        .isNull();
            }
        }
    }

    @Nested
    @DisplayName("the slice contract")
    class Contract {

        @Test
        @DisplayName("a band with no action carries no menu text, and the reverse")
        void textAndActionTravelTogether(@TempDir Path dir) {
            // The widget shows a menu when BOTH are present; one without the other is a menu item
            // that does nothing, or an action nothing can reach.
            LocalGameSession session = session(dir);
            session.allocateSelfMining(10);
            for (CycleGrid.Slice slice :
                    RigMonitorView.slices(
                            session.computeBudget(),
                            session.miningChain(),
                            RigMonitorView.free(session, address -> {}))) {
                assertThat(slice.freeText() == null)
                        .as("%s: text and action disagree", slice.label())
                        .isEqualTo(slice.onFree() == null);
            }
        }

        @Test
        @DisplayName("the drawing-only overload attaches nothing, so no render can free anything")
        void pureOverloadIsInert(@TempDir Path dir) {
            LocalGameSession session = session(dir);
            session.allocateSelfMining(10);
            assertThat(RigMonitorView.slices(session.computeBudget(), session.miningChain()))
                    .allSatisfy(slice -> assertThat(slice.freeText()).isNull());
        }
    }
}
