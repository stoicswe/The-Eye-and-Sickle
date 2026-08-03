package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Why running the same sweep twice is a waste of two cycles.
 *
 * <h2>Save-scumming is defeated by construction, not by a cooldown</h2>
 *
 * {@link HostState#detectRoll} is drawn once, at world generation, and stored. A sweep makes
 * <em>no</em> detection draw — it compares that fixed number against a threshold set by the sweep
 * tier, the host's signal and the hop distance. So the same tier from the same vantage returns a
 * bit-identical candidate set, every time, forever. Quitting without saving changes nothing, because
 * the roll predates the sweep by the whole game.
 *
 * <p>Only two things move the outcome, and both cost: a <b>higher sweep tier</b> (ethecoin, plus its
 * own compute, duration and noise) or a <b>closer vantage</b> (a breach, a foothold and a
 * {@code connect}). This file is the proof of each.
 *
 * <h2>Monotonicity is the other half</h2>
 *
 * A player who buys a better instrument must never lose a contact they already had. That makes
 * {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} a required property rather than a pleasant one —
 * an upgrade that could hide a machine you had already seen would make the purchase illegible, and
 * illegible purchases are how a player learns to stop buying things.
 */
class SweepDeterminismTest {

    private static final int SAMPLE = 400;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    /** A world where the player owns every sweep tier, so a tier can be chosen freely. */
    private static GameSave equipped(long seed) {
        GameSave save = NetTestKit.world(seed);
        NetTestKit.grant(save, SweepTier.WIDE);
        NetTestKit.grant(save, SweepTier.DEEP);
        return save;
    }

    /** What one tier finds, on a world nothing has swept yet. */
    private static Set<String> detected(long seed, SweepTier tier) {
        return new HashSet<>(
                NetTestKit.sweep(equipped(seed), tier, NetTestKit.T0).foundAddresses());
    }

    @Nested
    @DisplayName("repetition buys nothing")
    class Repetition {

        @Test
        @DisplayName("the same sweep, twice, from the same place, finds nothing new the second time")
        void resweepingIsNotAReroll() {
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                SweepReport first = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                SweepReport second = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0.plusSeconds(60));

                assertThat(first.found()).isPositive();
                assertThat(second.found())
                        .as("second sweep on seed %d", seed(i))
                        .isZero();
                // The number in range does NOT drop — that is the point of publishing it. The player
                // learns their instrument's sensitivity ("nine in range, I have four"), which is the
                // one aggregate that carries no address, type, tier or value.
                assertThat(second.inRange()).isEqualTo(first.inRange());
                // ⚠ And it says WHY, in the player's language. A mechanic that punished repetition
                // without explaining it would be indistinguishable from a bug.
                assertThat(second.note()).contains("louder instrument or a closer position");
            }
        }

        @Test
        @DisplayName("settling a sweep draws nothing, so nothing after it can be rerolled either")
        void settlementIsPure() {
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                TaskState task =
                        NetRules.beginSweep(save, SweepTier.BASE, NetTestKit.T0).orElseThrow();
                long afterBegin = save.rngSeed;

                NetRules.settleSweep(save, task, NetTestKit.T0.plusSeconds(20));

                // Everything was decided at begin and frozen into the task. A sweep that finished
                // while the game was closed therefore reports exactly what it would have reported in
                // session — the same rule scan findings and breach boards already follow.
                assertThat(save.rngSeed).as("seed unchanged by settlement").isEqualTo(afterBegin);
            }
        }

        @Test
        @DisplayName("a sweep's whole result is frozen at begin, and reading it back changes nothing")
        void theReportIsFrozen() {
            GameSave save = NetTestKit.world(seed(5));
            TaskState task =
                    NetRules.beginSweep(save, SweepTier.BASE, NetTestKit.T0).orElseThrow();

            SweepReport a = NetRules.report(task);
            SweepReport b = NetRules.report(task);
            assertThat(b).isEqualTo(a);
            assertThat(a.sweepToolId()).isEqualTo(SweepTier.BASE.itemId());
            assertThat(a.vantageAddress()).isEqualTo(save.topology.playerAddress);
            assertThat(a.found()).isEqualTo(a.foundAddresses().size());
        }

        @Test
        @DisplayName("a sweep from a build that predates the encoding reports nothing rather than guessing")
        void anUnreadableOutcomeIsHonest() {
            TaskState legacy =
                    new TaskState("sweep", "sweep", "alloc", 2L, NetTestKit.T0, NetTestKit.T0.plusSeconds(20));
            legacy.outcome = "some older shape entirely";

            SweepReport report = NetRules.report(legacy);
            // Inventing contacts would hand the player machines nobody found; inventing a
            // counter-hack would plant a parasite nobody earned. Empty is the only safe reading.
            assertThat(report.found()).isZero();
            assertThat(report.foundAddresses()).isEmpty();
            assertThat(report.counterHacked()).isFalse();
        }
    }

    @Nested
    @DisplayName("sensitivity is a purchase")
    class Sensitivity {

        @Test
        @DisplayName("a better instrument never loses a contact — T1 ⊆ T2 ⊆ T3")
        void betterTiersAreStrictlyBetter() {
            for (int i = 0; i < SAMPLE; i++) {
                Set<String> base = detected(seed(i), SweepTier.BASE);
                Set<String> wide = detected(seed(i), SweepTier.WIDE);
                Set<String> deep = detected(seed(i), SweepTier.DEEP);

                assertThat(wide)
                        .as("wide keeps everything base found, seed %d", seed(i))
                        .containsAll(base);
                assertThat(deep)
                        .as("deep keeps everything wide found, seed %d", seed(i))
                        .containsAll(wide);
            }
        }

        @Test
        @DisplayName("across many worlds, a better instrument really does find more")
        void betterTiersFindMore() {
            long base = 0;
            long wide = 0;
            long deep = 0;
            for (int i = 0; i < SAMPLE; i++) {
                base += detected(seed(i), SweepTier.BASE).size();
                wide += detected(seed(i), SweepTier.WIDE).size();
                deep += detected(seed(i), SweepTier.DEEP).size();
            }
            // The subset property alone would be satisfied by three identical tiers. This is the
            // assertion that the 25 EC and 55 EC prices buy something.
            assertThat(wide).isGreaterThan(base);
            assertThat(deep).isGreaterThan(wide);
        }

        @Test
        @DisplayName("a tier the player does not own is refused, and costs nothing")
        void unownedTiersRefuse() {
            GameSave save = NetTestKit.world(seed(9));
            long free = io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.availableCycles(save.rig);

            assertThat(NetRules.beginSweep(save, SweepTier.WIDE, NetTestKit.T0)).isEmpty();
            assertThat(NetRules.beginSweep(save, SweepTier.DEEP, NetTestKit.T0)).isEmpty();
            // Refused, not half-applied: no allocation, no task, no draw.
            assertThat(io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.availableCycles(save.rig))
                    .isEqualTo(free);
            assertThat(save.tasks).isEmpty();

            // The base tier is starting kit — the same class as Port Sweep, without which the game
            // does not start.
            assertThat(NetRules.beginSweep(save, SweepTier.BASE, NetTestKit.T0)).isPresent();
        }
    }

    @Nested
    @DisplayName("position is the other axis")
    class Position {

        @Test
        @DisplayName("moving the vantage changes what a sweep can reach, without changing the instrument")
        void positionSubstitutesForReach() {
            // ⚠ This is the whole traversal loop in one test, and the reason a one-hop ceiling is
            // survivable across a seven-server world. Reach is fixed and unbuyable; POSITION is
            // earned, and it does the job reach would have done.
            GameSave save = NetTestKit.world(seed(21));
            SweepReport first = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            assertThat(first.found()).isPositive();

            // Take a foothold the way a breach would, then stand on it.
            String next = first.foundAddresses().getFirst();
            HostState foothold = NetTestKit.host(save.topology, next);
            foothold.foothold = true;
            assertThat(NetRules.connect(save, next, NetTestKit.T0)).isTrue();
            assertThat(NetRules.vantageAddress(save)).isEqualTo(next);

            SweepReport fromThere = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0.plusSeconds(60));
            // Everything it now sees is one hop from the NEW position, and the ceiling never moved.
            var hops = NetTestKit.hops(save.topology, next);
            for (String address : fromThere.foundAddresses()) {
                assertThat(hops.get(address)).isEqualTo(1);
            }
            assertThat(NetRules.hopCeiling(save)).isEqualTo(1);
        }

        @Test
        @DisplayName("connect refuses anywhere the player has not taken a foothold")
        void connectNeedsAFoothold() {
            GameSave save = NetTestKit.world(seed(22));
            SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            String contact = report.foundAddresses().getFirst();

            // Discovered is not the same as held. Discovery is what a sweep sells; standing somewhere
            // has to be paid for with a breach, or position would be as free as reach is not.
            assertThat(NetRules.connect(save, contact, NetTestKit.T0)).isFalse();
            assertThat(NetRules.vantageAddress(save)).isEqualTo(save.topology.playerAddress);

            assertThat(NetRules.connect(save, "10.99.99.99", NetTestKit.T0)).isFalse();
            // Home is always reachable — a player can never strand themselves off their own rig.
            assertThat(NetRules.connect(save, save.topology.playerAddress, NetTestKit.T0))
                    .isTrue();
        }

        @Test
        @DisplayName("the Topology Mapper doubles reach, and two-hop vision is real but coarse")
        void theSchematicIsTheOnlyThingThatMovesReach() {
            GameSave withoutMapper = NetTestKit.world(seed(31));
            GameSave withMapper = NetTestKit.world(seed(31));
            withMapper.schematics.add(NetRules.TOPOLOGY_MAPPER);

            assertThat(NetRules.hopCeiling(withoutMapper)).isEqualTo(1);
            assertThat(NetRules.hopCeiling(withMapper)).isEqualTo(2);

            SweepReport near = NetTestKit.sweep(withoutMapper, SweepTier.BASE, NetTestKit.T0);
            SweepReport far = NetTestKit.sweep(withMapper, SweepTier.BASE, NetTestKit.T0);

            // More machines are CONSIDERED, which is what reach means — and the hop factor means the
            // second hop is a coarser look rather than an equally good one, per docs/design/07 §2's
            // reading of the Mapper as a reach purchase and not a clarity one.
            assertThat(far.inRange()).isGreaterThan(near.inRange());
            assertThat(far.foundAddresses()).containsAll(near.foundAddresses());
            assertThat(Balance.NET_HOP_FACTOR_2).isLessThan(Balance.NET_HOP_FACTOR_1);
        }
    }

    @Nested
    @DisplayName("what an undetected machine does")
    class TheUndetected {

        @Test
        @DisplayName("nothing: it is absent from knownNodes and from the map entirely")
        void undiscoveredHostsDoNotExist() {
            for (int i = 0; i < 200; i++) {
                GameSave save = NetTestKit.world(seed(i));
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

                Set<String> known = new HashSet<>();
                for (var node : save.knownNodes) {
                    known.add(node.address);
                    // ⚠ NodeState's class javadoc calls this rule load-bearing rather than tidy: the
                    // virtual namespace, tab completion and `ls /net/` are all built from this list,
                    // so a node that has not been paid for cannot leak through any of them.
                    assertThat(NetTestKit.host(save.topology, node.address).discovered)
                            .isTrue();
                }

                var map = NetRules.view(save);
                for (var sighting : map.sightings()) {
                    boolean rig = sighting.address().equals(save.topology.playerAddress);
                    assertThat(rig || known.contains(sighting.address()))
                            .as("%s is known", sighting.address())
                            .isTrue();
                }
                // Every link the map publishes has both ends in the map — no stub, no placeholder, no
                // "three contacts nearby".
                List<String> addresses =
                        map.sightings().stream().map(s -> s.address()).toList();
                for (var link : map.links()) {
                    assertThat(addresses).contains(link.fromAddress(), link.toAddress());
                }
            }
        }
    }
}
