package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Why moving is worth a breach: what a sweep hears depends on where it is standing.
 *
 * <h2>The change this file exists for</h2>
 *
 * Detection used to compare a threshold against {@link HostState#detectRoll} alone — a property of
 * the <b>machine</b>. So a contact a base sweep missed from the rig was missed from <em>every</em>
 * position at that tier, and repositioning bought nothing but a different set of neighbours inside
 * the hop ceiling. It now compares against {@code detectRoll × (FLOOR + (1 − FLOOR) × hash(machine,
 * vantage))} — a property of the <b>pair</b> — so two positions are two chances at the same machine,
 * and a player working outward keeps growing their graph.
 *
 * <h2>⚠ WHAT MUST NOT HAVE MOVED, and why each is here rather than assumed</h2>
 *
 * <ul>
 *   <li><b>Re-sweeping is still not a reroll.</b> The vantage term is <b>hashed, not drawn</b>, so
 *       the same spot answers the same way forever. {@code SweepDeterminismTest} owns the sweep-level
 *       proof; what is asserted here is the one line underneath it.
 *   <li><b>The home floor survives.</b> Three home neighbours are forced to {@code detectRoll = 0},
 *       and the perturbation is a multiply, so they stay audible from everywhere. An additive spread
 *       would have lifted them off the floor — this is the assertion that would have caught it.
 *   <li><b>The sensitivity ladder survives.</b> If a new position could find anything, nobody would
 *       buy a louder instrument. Measured here rather than argued.
 * </ul>
 */
class VantageDiscoveryTest {

    private static final int SAMPLE = 300;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    /**
     * The audibility rule as the sweep applies it.
     *
     * <p>⚠ Restated here rather than reached for, because {@code NetRules.audibility} is private and
     * making it visible would widen the class's surface for a test. The two must agree — and they do
     * where it counts, because {@link Nested} {@code InTheSweep} exercises the real path end to end
     * and would fail if this drifted.
     */
    private static double audibility(HostState host, String vantage) {
        return Balance.netSweepAudibility(
                host.detectRoll, AddressHash.unitOf(host.address + " from " + vantage, "sweep-audibility"));
    }

    @Nested
    @DisplayName("standing somewhere else")
    class Elsewhere {

        @Test
        @DisplayName("changes what is audible — a machine missed from one spot can be heard from another")
        void positionDecidesAudibility() {
            // ⚠ THE HEADLINE PROPERTY, and it is stated as a rate rather than as "at least one",
            // because "at least one somewhere" is true of almost any perturbation and says nothing
            // about whether moving is worth a breach.
            //
            // Measured against the BASE tier's threshold for a quiet machine (0.35), which is the
            // hardest case and the one a new player is actually in.
            double threshold = Balance.netSweepBase(1, "LOW");
            int rescued = 0;
            int missedSomewhere = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                String rig = save.topology.playerAddress;
                for (HostState host : save.topology.hosts) {
                    if (host.address.equals(rig)) {
                        continue;
                    }
                    if (audibility(host, rig) < threshold) {
                        continue; // audible from home already — not what this measures
                    }
                    missedSomewhere++;
                    // Every other machine in the world is a position the player could conceivably
                    // reach by breaching outward. If ANY of them hears this one, moving pays.
                    for (HostState from : save.topology.hosts) {
                        if (!from.address.equals(host.address) && audibility(host, from.address) < threshold) {
                            rescued++;
                            break;
                        }
                    }
                }
            }
            assertThat(missedSomewhere).isPositive();
            double rate = rescued / (double) missedSomewhere;
            // ⚠ A BAND, not a floor. Too low and repositioning is theatre; too high and the sweep
            // upgrades are pointless, because everything would eventually be findable at base tier
            // from somewhere. Measured at ~0.36 with NET_SWEEP_VANTAGE_FLOOR at 0.55.
            assertThat(rate).as("share of home-inaudible machines audible from somewhere").isBetween(0.15, 0.65);
        }

        @Test
        @DisplayName("does not make a quiet machine audible at any position — that is what the instrument is for")
        void quietStaysQuiet() {
            // ⚠ THE OTHER HALF, and the one that keeps ethecoin's purchase meaningful. Past the point
            // where even the most favourable position cannot reach the threshold, only a louder
            // instrument helps. FLOOR is what puts that point at a finite place: at 0 the multiply
            // would make every machine findable from somewhere and the ladder would be decoration.
            double threshold = Balance.netSweepBase(1, "LOW");
            double quietest = threshold / Balance.NET_SWEEP_VANTAGE_FLOOR;
            assertThat(quietest).isLessThan(1.0d).as("some machines must be beyond every position");

            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (HostState host : save.topology.hosts) {
                    if (host.detectRoll <= quietest) {
                        continue;
                    }
                    for (HostState from : save.topology.hosts) {
                        assertThat(audibility(host, from.address))
                                .as("a machine at detectRoll %.3f heard from %s", host.detectRoll, from.address)
                                .isGreaterThanOrEqualTo(threshold);
                    }
                }
            }
        }

        @Test
        @DisplayName("asks the same question twice and gets the same answer")
        void isHashedNotDrawn() {
            GameSave save = NetTestKit.world(seed(1));
            for (HostState host : save.topology.hosts) {
                for (HostState from : save.topology.hosts) {
                    assertThat(audibility(host, from.address)).isEqualTo(audibility(host, from.address));
                }
            }
        }
    }

    @Nested
    @DisplayName("what did not move")
    class Unmoved {

        @Test
        @DisplayName("⚠ a guaranteed first contact is audible from every position in the world")
        void theHomeFloorSurvives() {
            // TopologyGenerator forces the first three home neighbours to detectRoll 0.0, and the
            // whole first-sweep guarantee rests on that being below the base sweep's WORST threshold.
            // The perturbation is a multiply, so zero stays zero — an additive spread would have
            // lifted these three off the floor for some positions and broken the guarantee for a new
            // player on some seeds only, which is the worst way for it to break.
            double worstThreshold = Balance.netSweepBase(1, "LOW") * Balance.NET_HOP_FACTOR_2;
            int floored = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (HostState host : save.topology.hosts) {
                    if (host.detectRoll != 0.0d) {
                        continue;
                    }
                    floored++;
                    for (HostState from : save.topology.hosts) {
                        assertThat(audibility(host, from.address))
                                .as("guaranteed contact %s heard from %s", host.address, from.address)
                                .isLessThan(worstThreshold);
                    }
                }
            }
            assertThat(floored)
                    .as("the fixture must actually contain floored hosts, or this asserts nothing")
                    .isGreaterThanOrEqualTo(SAMPLE * Balance.NET_HOME_GUARANTEED_CONTACTS);
        }

        @Test
        @DisplayName("⚠ a machine is never harder to hear than its own roll — no contact is lost")
        void neverWorseThanBefore() {
            // The factor is at most 1, so audibility ≤ detectRoll everywhere. That is what makes this
            // change purely additive against the old rule: anything a player could find before, from
            // wherever they were standing, they can still find.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                for (HostState host : save.topology.hosts) {
                    for (HostState from : save.topology.hosts) {
                        assertThat(audibility(host, from.address)).isLessThanOrEqualTo(host.detectRoll);
                    }
                }
            }
        }

        @Test
        @DisplayName("⚠ the sensitivity ladder is intact — a deep sweep still hears far more than a base one")
        void theLadderSurvives() {
            // ⚠ MEASURED, because this is the property a careless FLOOR destroys silently. At FLOOR 0
            // the multiply roughly doubles detection and the T1/T3 gap collapses; the sweep upgrades
            // would still be purchasable, still cost ethecoin, and buy almost nothing.
            double base = detectionRate(1);
            double deep = detectionRate(3);
            assertThat(deep / base)
                    .as("T3/T1 detection ratio for a quiet machine")
                    .isGreaterThan(1.6d);
            // And the absolute rates are worth pinning: a base sweep must stay clearly fallible.
            assertThat(base).as("base tier, quiet machine").isBetween(0.35d, 0.60d);
            assertThat(deep).as("deep tier, quiet machine").isGreaterThan(0.80d);
        }

        /** How often a uniformly-rolled quiet machine is audible from a given position, at one tier. */
        private double detectionRate(int tier) {
            double threshold = Balance.netSweepBase(tier, "LOW");
            int heard = 0;
            int total = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                String rig = save.topology.playerAddress;
                for (HostState host : save.topology.hosts) {
                    if (host.address.equals(rig) || host.detectRoll == 0.0d) {
                        continue; // the floored contacts are guaranteed and would skew the rate
                    }
                    total++;
                    if (audibility(host, rig) < threshold) {
                        heard++;
                    }
                }
            }
            assertThat(total).isPositive();
            return heard / (double) total;
        }
    }

    @Nested
    @DisplayName("in the sweep")
    class InTheSweep {

        /** How many rungs of the traversal loop a walking player is simulated as taking. */
        private static final int STEPS = 12;

        @Test
        @DisplayName("⚠ walking the traversal loop builds a graph the rig alone can never reach")
        void theWholeLoop() {
            // ⚠ END TO END, THROUGH THE REAL RULES — begin, settle, connect, begin again. The unit
            // assertions above are about the function; this is about the player's actual loop, and it
            // is the one that would catch the function being right and unwired.
            //
            // ⚠ THE COMPARISON IS AGAINST SWEEPING FROM HOME FOREVER, and it has to be. "A second
            // position finds something new" is the obvious assertion and it is a weak one: measured,
            // it holds on only 60% of worlds, because a first hop often lands on a machine whose
            // whole neighbourhood is already discovered — a fact about hub-shaped servers rather than
            // a defect. What the feature promises is that the graph keeps GROWING as you work
            // outward, so that is what is measured.
            //
            // Measured over 300 worlds and 12 positions:
            //
            //     BASE   staying home 5.02   walking  7.66   (1.5x)
            //     WIDE   staying home 5.91   walking 18.82   (3.2x)
            //
            // ⚠ THE BASE ROW FELL FROM 2.0x WHEN design/18's SERVER SHAPE LANDED, and the reason is a
            // real design consequence rather than a regression: a server is a deep tree now, so most
            // machines have one parent and one child, and a one-hop ceiling therefore reveals fewer
            // machines per position than the old bushy random tree did. Crossing a server costs more
            // POSITIONS than it used to. That is the shape working — but it is the number to watch if
            // NET_SPINE_BUDGET_SHARE is ever raised, because a narrower server makes each move worth
            // less.
            //
            // ⚠ THE GAP BETWEEN THOSE TWO ROWS IS THE BRIDGE TIER GATE, not this change. A base sweep
            // cannot see a BRIDGE at any distance (NET_SWEEP_BRIDGE_MIN_TIER = 2), so a base-only
            // player can walk their home server and no further, and their graph plateaus at about ten
            // machines however long they keep moving. A WIDE sweep crosses servers and keeps growing
            // — measured at 35.2 by 25 positions. Anybody re-tuning that gate should re-read these
            // numbers first.
            assertGrowth(SweepTier.BASE, 1.4d);
            assertGrowth(SweepTier.WIDE, 3.0d);
        }

        private void assertGrowth(SweepTier tier, double factor) {
            double walkedTotal = 0;
            double stayedTotal = 0;
            for (int i = 0; i < SAMPLE; i++) {
                stayedTotal += discovered(sweepUntilDry(equippedWorld(seed(i)), tier));
                walkedTotal += discovered(walkOutward(equippedWorld(seed(i)), tier));
            }
            double walked = walkedTotal / SAMPLE;
            double stayed = stayedTotal / SAMPLE;
            assertThat(stayed).isPositive();
            assertThat(walked)
                    .as("%s: machines found over %d positions vs staying home (%.2f)", tier, STEPS, stayed)
                    .isGreaterThan(stayed * factor);
        }

        /** Sweeps from the rig until it stops paying — the whole of what standing still can ever buy. */
        private GameSave equippedWorld(long seed) {
            GameSave save = NetTestKit.world(seed);
            NetTestKit.grant(save, SweepTier.WIDE);
            NetTestKit.grant(save, SweepTier.DEEP);
            return save;
        }

        /** Sweeps from the rig until it stops paying — the whole of what standing still can ever buy. */
        private GameSave sweepUntilDry(GameSave save, SweepTier tier) {
            for (int step = 0; step <= STEPS; step++) {
                freeCompute(save);
                NetTestKit.sweep(save, tier, NetTestKit.T0.plusSeconds(step * 120L));
            }
            return save;
        }

        /** Sweep, take a foothold on something found, stand on it, sweep again. */
        private GameSave walkOutward(GameSave save, SweepTier tier) {
            Set<String> visited = new HashSet<>(Set.of(save.topology.vantageAddress));
            for (int step = 0; step <= STEPS; step++) {
                freeCompute(save);
                NetTestKit.sweep(save, tier, NetTestKit.T0.plusSeconds(step * 120L));
                String next = nextVantage(save, visited);
                if (next == null) {
                    continue;
                }
                // The breach itself is short-circuited: this test is about discovery, not combat.
                NetRules.host(save, next).foothold = true;
                NetRules.connect(save, next, NetTestKit.T0.plusSeconds(step * 120L + 1));
                visited.add(next);
            }
            return save;
        }

        private GameSave sweepUntilDry(GameSave save) {
            for (int step = 0; step <= STEPS; step++) {
                freeCompute(save);
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0.plusSeconds(step * 120L));
            }
            return save;
        }

        /**
         * Somewhere new to stand — a discovered machine this walk has not swept from yet.
         *
         * <h2>⚠ "NOT YET USED AS A VANTAGE" IS THE WHOLE OF IT, and the first version had it wrong</h2>
         *
         * Taking whatever was found most recently reads as a walk and is not one: it bounces between
         * two adjacent machines, re-sweeping positions it has already exhausted, and the graph
         * plateaus at about twelve machines however many steps it is given. That is a defect in the
         * simulated player, not in the rules — and it would have been reported as the feature not
         * working. A frontier, preferring bridges, is what an actual player does.
         */
        private String nextVantage(GameSave save, Set<String> visited) {
            return save.topology.hosts.stream()
                    .filter(host -> host.discovered)
                    .filter(host -> !visited.contains(host.address))
                    .min(java.util.Comparator.comparingInt(
                            host -> HostKind.BRIDGE.name().equals(host.kind) ? 0 : 1))
                    .map(host -> host.address)
                    .orElse(null);
        }

        /**
         * Hands the sweep's held cycles back.
         *
         * <p>⚠ {@code NetTestKit.sweep} commissions and settles but never releases the compute hold,
         * which is fine for a single sweep and is not for seven: a walking player would otherwise run
         * out of rig before they ran out of network, and the WIDE walk failed with "no compute" for
         * exactly that reason rather than for anything about discovery.
         */
        private void freeCompute(GameSave save) {
            save.rig.allocations.clear();
        }

        private long discovered(GameSave save) {
            return save.topology.hosts.stream().filter(host -> host.discovered).count();
        }

        @Test
        @DisplayName("⚠ and the same position, twice, still finds nothing new")
        void repetitionStillBuysNothing() {
            // The feature is "a different position is a second chance", NOT "another go is a second
            // chance". If this ever fails, the vantage term has become a draw and save-scumming is
            // back.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = NetTestKit.world(seed(i));
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                assertThat(NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0.plusSeconds(60))
                                .found())
                        .as("second sweep from the same spot, seed %d", seed(i))
                        .isZero();
            }
        }
    }
}
