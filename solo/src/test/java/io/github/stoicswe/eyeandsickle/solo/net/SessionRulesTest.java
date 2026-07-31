package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Shell sessions: what they cost, what they require, and the one thing they must never be.
 *
 * <p>The load-bearing test here is {@link Vantage} — a session that moved the vantage would multiply
 * sweep reach by the number of windows a player had open, which is Invariant <b>I2</b>'s ceiling
 * being sold for the price of a click. Everything else in this file is about compute not leaking.
 */
class SessionRulesTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    private static SoloSave save(boolean foothold) {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 100;
        save.topology = new TopologyState();

        HostState self = new HostState();
        self.address = "10.0.0.1";
        self.kind = "SELF";
        self.discovered = true;
        self.foothold = true;

        HostState target = new HostState();
        target.address = "10.0.0.9";
        target.kind = "TERMINAL";
        target.discovered = true;
        target.foothold = foothold;

        save.topology.hosts.add(self);
        save.topology.hosts.add(target);
        save.topology.vantageAddress = "10.0.0.1";
        return save;
    }

    private static long held(SoloSave save) {
        return save.rig.allocations.stream()
                .filter(a -> ComputeConsumer.SHELL_SESSION.name().equals(a.consumer))
                .mapToLong(a -> a.cycles)
                .sum();
    }

    @Nested
    @DisplayName("⚠ a session is not the vantage")
    class Vantage {

        @Test
        @DisplayName("opening a session leaves the vantage exactly where it was")
        void doesNotMoveTheVantage() {
            // The whole reason sessions and `connect` are different verbs. If this ever fails, reach
            // has silently become a function of how many windows are open — I2's ceiling for free.
            SoloSave save = save(true);
            String before = save.topology.vantageAddress;

            assertThat(SessionRules.open(save, "10.0.0.9", NOW).succeeded()).isTrue();

            assertThat(save.topology.vantageAddress).isEqualTo(before);
        }

        @Test
        @DisplayName("many sessions can be open at once, and there is still one vantage")
        void manySessionsOneVantage() {
            SoloSave save = save(true);
            HostState second = new HostState();
            second.address = "10.0.0.12";
            second.kind = "TERMINAL";
            second.discovered = true;
            second.foothold = true;
            save.topology.hosts.add(second);

            SessionRules.open(save, "10.0.0.9", NOW);
            SessionRules.open(save, "10.0.0.12", NOW);

            assertThat(SessionRules.all(save)).hasSize(2);
            assertThat(save.topology.vantageAddress).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("what it takes")
    class Requirements {

        @Test
        @DisplayName("a machine with no foothold is refused, and the reason names the foothold")
        void needsAFoothold() {
            // You cannot run commands on a machine you have not broken into — that is what breaking
            // in is FOR (docs/design/05). The refusal is its own constant so the caller can word it.
            SoloSave save = save(false);
            var opened = SessionRules.open(save, "10.0.0.9", NOW);

            assertThat(opened.succeeded()).isFalse();
            assertThat(opened.refusal()).isEqualTo(SessionRules.Refusal.NO_FOOTHOLD);
            assertThat(held(save)).isZero();
        }

        @Test
        @DisplayName("an address that was never swept is unknown, not merely unbreached")
        void unknownHost() {
            SoloSave save = save(true);
            assertThat(SessionRules.open(save, "10.9.9.9", NOW).refusal()).isEqualTo(SessionRules.Refusal.UNKNOWN_HOST);
        }

        @Test
        @DisplayName("a rig with no spare cycles is refused, and nothing is reserved")
        void needsCompute() {
            SoloSave save = save(true);
            save.rig.totalCycles = 1;
            var opened = SessionRules.open(save, "10.0.0.9", NOW);

            assertThat(opened.refusal()).isEqualTo(SessionRules.Refusal.NOT_ENOUGH_COMPUTE);
            assertThat(held(save)).isZero();
        }
    }

    @Nested
    @DisplayName("compute does not leak")
    class Compute {

        @Test
        @DisplayName("an open session holds its cycles, and closing gives them straight back")
        void holdsAndReleases() {
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.9", NOW);
            assertThat(held(save)).isEqualTo(Balance.SESSION_CYCLES);

            assertThat(SessionRules.close(save, "10.0.0.9")).isTrue();
            assertThat(held(save)).isZero();
            assertThat(SessionRules.all(save)).isEmpty();
        }

        @Test
        @DisplayName("⚠ opening twice reserves once — a double-click must not cost twice")
        void openIsIdempotent() {
            // The control that calls this is a menu item a player may well double-click. Without
            // idempotence the second click would reserve a second allocation for one window, and the
            // first would be orphaned: two cycles held forever with nothing on screen to explain it.
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.9", NOW);
            SessionRules.open(save, "10.0.0.9", NOW);

            assertThat(SessionRules.all(save)).hasSize(1);
            assertThat(held(save)).isEqualTo(Balance.SESSION_CYCLES);
        }

        @Test
        @DisplayName("⚠ losing the foothold prunes the session and returns its cycles")
        void pruneReleases() {
            // A foothold can be lost while a window is open — a patch lands, or the player is pushed
            // off. Without the prune the session sits holding cycles against a machine that would
            // refuse every command: a leak whose cause is invisible on the rig monitor.
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.9", NOW);
            SessionRules.host(save, "10.0.0.9").foothold = false;

            assertThat(SessionRules.prune(save)).containsExactly("10.0.0.9");
            assertThat(held(save)).isZero();
            assertThat(SessionRules.all(save)).isEmpty();
        }

        @Test
        @DisplayName("the player's own rig survives a prune, because it is never a foothold")
        void pruneKeepsTheOwnRig() {
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.1", NOW);

            assertThat(SessionRules.prune(save)).isEmpty();
            assertThat(SessionRules.all(save)).hasSize(1);
        }

        @Test
        @DisplayName("closing releases by ALLOCATION ID, so two sessions cannot free each other's")
        void releasesTheRightAllocation() {
            SoloSave save = save(true);
            HostState second = new HostState();
            second.address = "10.0.0.12";
            second.kind = "TERMINAL";
            second.discovered = true;
            second.foothold = true;
            save.topology.hosts.add(second);

            SessionRules.open(save, "10.0.0.9", NOW);
            SessionRules.open(save, "10.0.0.12", NOW);
            SessionRules.close(save, "10.0.0.9");

            // One left, and it is the one that was not closed — which is only guaranteed because the
            // release matches on the allocation id rather than on the consumer or the label.
            assertThat(held(save)).isEqualTo(Balance.SESSION_CYCLES);
            assertThat(SessionRules.all(save)).extracting(s -> s.address).containsExactly("10.0.0.12");
        }
    }

    @Nested
    @DisplayName("where a session starts")
    class WorkingDirectory {

        @Test
        @DisplayName("a session lands in the machine's own home, not at the root")
        void startsInHome() {
            // Where a real login puts you, and where anything worth finding on a host actually is.
            // ⚠ /Users, not /home — uOS's root is macOS-shaped as of 2026-07-28.
            SoloSave save = save(true);
            var opened = SessionRules.open(save, "10.0.0.9", NOW);
            assertThat(opened.session().cwd).startsWith(io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.USERS + "/");
        }

        @Test
        @DisplayName("cd refuses a path that is not a directory, and does not move")
        void cdRefuses() {
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.9", NOW);
            String before = SessionRules.find(save, "10.0.0.9").orElseThrow().cwd;

            assertThat(SessionRules.changeDirectory(save, "10.0.0.9", "/nowhere", NOW))
                    .isFalse();
            assertThat(SessionRules.find(save, "10.0.0.9").orElseThrow().cwd).isEqualTo(before);
        }

        @Test
        @DisplayName("cd works, and `..` climbs")
        void cdMoves() {
            SoloSave save = save(true);
            SessionRules.open(save, "10.0.0.9", NOW);

            // /System is listable on any machine — you can see an operating system's shape without
            // holding the box. What you cannot do is read any of it.
            assertThat(SessionRules.changeDirectory(save, "10.0.0.9", "/System/etc", NOW))
                    .isTrue();
            assertThat(SessionRules.find(save, "10.0.0.9").orElseThrow().cwd).isEqualTo("/System/etc");
        }
    }
}
