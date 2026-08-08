package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Who is watching a bridge — {@code docs/design/17} §4.4.
 *
 * <p>All of it is a pure function of the address and the server's depth, so all of it is checkable
 * without generating a world.
 */
@DisplayName("MonJobs")
class MonJobsTest {

    private static HostState bridge(String address) {
        HostState host = new HostState();
        host.address = address;
        host.kind = HostKind.BRIDGE.name();
        return host;
    }

    private static HostState of(String address, HostKind kind) {
        HostState host = new HostState();
        host.address = address;
        host.kind = kind.name();
        return host;
    }

    /** A spread of addresses in the shape the generator produces. */
    private static java.util.List<HostState> sample() {
        java.util.List<HostState> hosts = new java.util.ArrayList<>();
        for (int server = 0; server < 8; server++) {
            for (int host = 0; host < 50; host++) {
                hosts.add(bridge("10." + server + ".0." + host));
            }
        }
        return hosts;
    }

    private static double watchedFraction(int depth) {
        long watched =
                sample().stream().filter(host -> MonJobs.watched(host, depth)).count();
        return watched / (double) sample().size();
    }

    @Nested
    @DisplayName("the home floor")
    class Home {

        @Test
        @DisplayName("no bridge on the home server is ever watched")
        void homeIsClean() {
            // ⚠ NOT a small number — zero. `NET_COUNTER_HACK_HOME` is its own named constant for
            // exactly this reason, and its stated argument transfers word for word: "the home server
            // is where the game teaches, and a teaching space that occasionally plants a parasite on
            // the student is a teaching space they learn to avoid". The first bridge a player ever
            // crosses is clean, always.
            for (HostState host : sample()) {
                assertThat(MonJobs.watched(host, 0))
                        .as("%s at home", host.address)
                        .isFalse();
            }
            assertThat(Balance.MONJOB_DENSITY_HOME).isZero();
        }
    }

    @Nested
    @DisplayName("density")
    class Density {

        @Test
        @DisplayName("rises with depth, and lands near the published curve")
        void ramps() {
            // Sampled rather than asserted per host: the point of a hash is that WHICH bridges are
            // watched is arbitrary, and the only thing worth pinning is how many.
            assertThat(watchedFraction(1)).as("depth 1").isBetween(0.05, 0.16);
            assertThat(watchedFraction(2)).as("depth 2").isBetween(0.22, 0.35);
            assertThat(watchedFraction(3)).as("depth 3").isBetween(0.43, 0.57);
            assertThat(watchedFraction(4)).as("depth 4").isBetween(0.63, 0.77);
        }

        @Test
        @DisplayName("is monotonic in depth")
        void monotonic() {
            // The whole player-facing claim is "further out is worse". A dip anywhere would make that
            // false somewhere, and it would be invisible until somebody mapped it.
            double previous = -1;
            for (int depth = 0; depth <= 6; depth++) {
                double now = watchedFraction(depth);
                assertThat(now).as("depth %d", depth).isGreaterThanOrEqualTo(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("only bridges are ever watched")
        void bridgesOnly() {
            // A MonJob is a route the bridge itself keeps, which is why bridges and only bridges can
            // carry one — and why it cannot be deleted like a file.
            for (HostKind kind : HostKind.values()) {
                if (kind == HostKind.BRIDGE) {
                    continue;
                }
                for (int i = 0; i < 50; i++) {
                    assertThat(MonJobs.watched(of("10.4.0." + i, kind), 4))
                            .as("%s at depth 4", kind)
                            .isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("the same bridge answers the same way, every time")
        void stable() {
            // ⚠ NetRules' rule, verbatim: "Detection is a roll made once, at world generation, and
            // stored. Nothing here draws for detection, ever." Scouting a bridge twice must not give
            // two answers, and quitting without saving must change nothing.
            HostState host = bridge("10.3.0.7");
            boolean first = MonJobs.watched(host, 3);
            int tier = MonJobs.tier(host, 3);
            for (int i = 0; i < 100; i++) {
                assertThat(MonJobs.watched(host, 3)).isEqualTo(first);
                assertThat(MonJobs.tier(host, 3)).isEqualTo(tier);
            }
        }

        @Test
        @DisplayName("⚠ neighbouring addresses do not march in lockstep")
        void noLockstep() {
            // ⚠ THE TRAP THIS CODEBASE HAS ALREADY HIT TWICE, and this file made it a third time.
            //
            // `String.hashCode` is 31·h + c, so addresses differing by one in their last character
            // land one apart and a threshold over them walks the space in order — `VirtualFs.hostUser`
            // shipped that, and the "random" name was the host index in disguise. FNV-1a fixes the
            // low bits, but this method's first version took the HIGH ones, where a one-character
            // change reaches only through carry propagation: measured, 10.3.0.10 through 10.3.0.19
            // every one returned **0.980**. Fixed by finalising the hash properly.
            //
            // ⚠ ASSERTED ON THE SPREAD OF THE VALUES, NOT ON A COUNT OF BOOLEAN FLIPS. The first
            // version of this test counted how often `watched` changed answer across a decade — which
            // at a density of 0.10 is expected to happen under twice, so zero flips is an ordinary
            // outcome and the test could not tell "well distributed" from "all identical". It passed
            // and failed for reasons unrelated to the bug. The unit values are the real subject.
            //
            // ⚠ One DECADE, because a run crossing …9 → …10 changes the string's length and would
            // scatter for a reason that has nothing to do with the mixing.
            double lowest = 1.0;
            double highest = 0.0;
            for (int i = 10; i <= 19; i++) {
                double value = AddressHash.unitOf("10.3.0." + i, "monjob");
                lowest = Math.min(lowest, value);
                highest = Math.max(highest, value);
            }
            assertThat(highest - lowest)
                    .as("ten consecutive addresses collapsed into a narrow band — the hash is walking in order")
                    .isGreaterThan(0.5);
        }

        @Test
        @DisplayName("the unit values fill the range rather than clustering")
        void wellDistributed() {
            // The other half of the same property, over the whole sample: a mean far from 0.5 means
            // every density constant would silently mean something other than what it says.
            double sum = 0;
            for (HostState host : sample()) {
                sum += AddressHash.unitOf(host.address, "monjob");
            }
            assertThat(sum / sample().size()).as("mean of a uniform 0..1").isBetween(0.45, 0.55);
        }

        @Test
        @DisplayName("presence and tier are drawn from different salts, so they do not correlate")
        void saltsAreIndependent() {
            // Sharing one salt would tie the two answers together: tier 2 would land on the same
            // bridges that were most likely to be watched at all, and a player would eventually read
            // the correlation without being able to name it.
            long announcing = sample().stream()
                    .filter(host -> MonJobs.watched(host, 4))
                    .filter(host -> MonJobs.announces(host, 4))
                    .count();
            long watched =
                    sample().stream().filter(host -> MonJobs.watched(host, 4)).count();
            assertThat(announcing / (double) watched)
                    .as("tier-2 share at depth 4")
                    .isBetween(0.50, 0.70);
        }
    }

    @Nested
    @DisplayName("tiers")
    class Tiers {

        @Test
        @DisplayName("an unwatched bridge has no tier at all")
        void noTierWithoutAJob() {
            // A tier for an absent MonJob is a number that reads as real everywhere it is shown. The
            // two questions come from different salts, so nothing about the tier implies the job.
            for (HostState host : sample()) {
                if (!MonJobs.watched(host, 4)) {
                    assertThat(MonJobs.tier(host, 4)).as("%s", host.address).isZero();
                    assertThat(MonJobs.announces(host, 4)).isFalse();
                }
            }
        }

        @Test
        @DisplayName("the tier-2 share rises with depth — density alone teaches nothing")
        void mixShifts() {
            // ⚠ THE POINT OF MJ-3. A tier-1 MonJob is invisible to the intruder by design, so a
            // player crossing ten watched bridges learns nothing and is then counter-hacked with no
            // visible cause — the failure NetRules names as "a mechanic that punishes without
            // explaining is indistinguishable from a bug". Tier 2 is the only thing that says so.
            assertThat(Balance.monJobTierTwoShare(1)).isLessThan(Balance.monJobTierTwoShare(4));
            double previous = -1;
            for (int depth = 0; depth <= 6; depth++) {
                double now = Balance.monJobTierTwoShare(depth);
                assertThat(now).as("depth %d", depth).isGreaterThanOrEqualTo(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("being told stays rarer than being watched, at every depth")
        void tellingIsRarerThanWatching() {
            // A warning that fires on every crossing stops being read — §2.1's rationing argument one
            // system along. The combined chance of watched AND told must stay well under the chance
            // of being watched at all.
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.monJobTierTwoShare(depth))
                        .as("depth %d tier-2 share", depth)
                        .isLessThan(1.0);
            }
        }
    }

    @Nested
    @DisplayName("the scan finding — MJ-4")
    class Finding {

        @Test
        @DisplayName("MONITORED and PEERS exist only on bridges")
        void bridgeOnlyFindings() {
            for (HostKind kind : HostKind.values()) {
                boolean bridge = kind == HostKind.BRIDGE;
                assertThat(PortScanTarget.PEERS.appliesTo(kind))
                        .as("PEERS on %s", kind)
                        .isEqualTo(bridge);
                assertThat(PortScanTarget.MONITORED.appliesTo(kind))
                        .as("MONITORED on %s", kind)
                        .isEqualTo(bridge);
            }
        }

        @Test
        @DisplayName("⚠ every other machine keeps EXACTLY the calibrated eight")
        void nonBridgesAreUntouched() {
            // ⚠ THE ASSERTION THAT STOPS THIS CHANGE BREAKING THE GAME QUIETLY. `NodeReports.known`
            // divides by the applicable rungs and feeds `Balance.breachProtocolShare` — which puzzle a
            // breach draws. If a desktop's count ever moves off 8, breach-protocol odds shift for
            // every target a player has ever scanned, silently, with every screen still rendering.
            for (HostKind kind : HostKind.values()) {
                if (kind != HostKind.BRIDGE) {
                    assertThat(PortScanTarget.countFor(kind)).as("%s", kind).isEqualTo(8);
                }
            }
        }

        @Test
        @DisplayName("a bridge has five findings, and no vault or downloads among them")
        void bridgeSet() {
            // The wart this fixes: before per-kind applicability a scan of a router recorded a
            // downloads folder and two vault tiers. Nothing failed — the numbers were just about
            // things a bridge does not have.
            assertThat(PortScanTarget.countFor(HostKind.BRIDGE)).isEqualTo(5);
            assertThat(PortScanTarget.DOWNLOADS.appliesTo(HostKind.BRIDGE)).isFalse();
            assertThat(PortScanTarget.VAULT_HIGH.appliesTo(HostKind.BRIDGE)).isFalse();
            assertThat(PortScanTarget.VAULT_MEDIUM.appliesTo(HostKind.BRIDGE)).isFalse();
            assertThat(PortScanTarget.CYCLE_CAPABILITY.appliesTo(HostKind.BRIDGE))
                    .as("a bridge accepts no deployed work, so its capacity is not a usable number")
                    .isFalse();
        }

        @Test
        @DisplayName("⚠ within any one kind, no two findings share a depth")
        void depthsAreUniqueWithinAKind() {
            // ⚠ THE INVARIANT THAT MAKES SHARING A DEPTH ACROSS KINDS SAFE, and the reason PEERS can
            // sit at 4 beside CYCLE_CAPABILITY. Depth drives cost, duration, noise and detection
            // risk, and `reachedBy` says a scan to depth N answers everything at or above N — so two
            // rungs at one depth on ONE machine would be two findings at one price with no way to ask
            // for only one. It is fine only because they never co-exist: a bridge has PEERS and no
            // CYCLE_CAPABILITY, everything else the reverse.
            //
            // ⚠ `PortScanTest.depthCostsMoreOnEveryAxis` walks a kind's rungs in depth order and
            // depends on this holding.
            for (HostKind kind : HostKind.values()) {
                java.util.Set<Integer> depths = new java.util.HashSet<>();
                for (PortScanTarget target : PortScanTarget.values()) {
                    if (target.appliesTo(kind)) {
                        assertThat(depths.add(target.depth()))
                                .as(
                                        "%s: %s reuses depth %d on a machine that already has one",
                                        kind, target, target.depth())
                                .isTrue();
                    }
                }
            }
        }

        @Test
        @DisplayName("the peer count is mid-ladder, not the dearest finding in the game")
        void peersAreCheap() {
            // Depth 4, sharing with CYCLE_CAPABILITY. Appending at 9 would have made the count the
            // most expensive thing a scan can buy, which contradicts what it is: a fact about a router
            // that is loud by nature.
            assertThat(PortScanTarget.PEERS.depth()).isEqualTo(4);
            assertThat(PortScanTarget.PEERS.depth()).isLessThan(PortScanTarget.VAULT_MEDIUM.depth());
        }
    }
}
