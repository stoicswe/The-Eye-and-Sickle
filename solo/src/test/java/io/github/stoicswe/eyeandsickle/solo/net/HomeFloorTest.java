package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The anti-dead-end guarantee: <b>the first sweep a new player runs always returns at least three
 * workable targets.</b>
 *
 * <h2>Why this file is a guarantee and not a tuning</h2>
 *
 * The problem being fixed is that discovery was unusable at the start. A probabilistic fix — nudging
 * the base sweep's detection rate up until a new player "usually" found something — would leave a
 * fraction of seeds producing a character with nothing to do and no way to tell that anything was
 * wrong. So the floor is applied deterministically, after every roll: the three guaranteed contacts
 * are forced to {@code detectRoll = 0.0}, which is below the base sweep's <em>worst</em> threshold
 * (0.35 — a quiet machine at one hop), so they are found on every seed, forever.
 *
 * <p>Ten thousand seeds, because "usually" is exactly the failure mode this file exists to rule out.
 */
class HomeFloorTest {

    /**
     * The headline guarantees get the full sample; the supporting ones get a smaller one.
     *
     * <p>The split is deliberate rather than lazy. Sixty thousand world generations in one test class
     * is a slow suite, and a slow suite gets run less — which costs more coverage than the extra
     * seeds buy. The two properties a new player would actually notice failing are checked at ten
     * thousand; the structural ones that support them are checked at two, where a systematic break
     * still cannot hide.
     */
    private static final int SEEDS = 10_000;

    private static final int SAMPLE = 2_000;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    @Nested
    @DisplayName("the opening position")
    class Opening {

        @Test
        @DisplayName("the rig always has at least five neighbours")
        void neighbourFloorHolds() {
            for (int i = 0; i < SAMPLE; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                HostState rig = NetTestKit.host(topology, topology.playerAddress);
                // Five rather than three so the base sweep has something to MISS as well as something
                // to find — which is what teaches that sensitivity is a purchase rather than a
                // formality, and what makes the 25 EC wide sweep legible when the player buys it.
                assertThat(rig.links.size())
                        .as("neighbours on seed %d", seed(i))
                        .isGreaterThanOrEqualTo(Balance.NET_HOME_SEED_NEIGHBOURS);
            }
        }

        @Test
        @DisplayName("at least three of them are tier-1, un-firewalled, undefended machines")
        void contactFloorHolds() {
            for (int i = 0; i < SEEDS; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                List<HostState> workable = workableNeighbours(topology);
                assertThat(workable.size())
                        .as("workable contacts on seed %d", seed(i))
                        .isGreaterThanOrEqualTo(Balance.NET_HOME_GUARANTEED_CONTACTS);
                for (HostState host : workable) {
                    assertThat(host.tier).isEqualTo(1);
                    assertThat(host.firewallTier).isZero();
                    assertThat(host.defended).isFalse();
                    assertThat(host.tarpit).isFalse();
                    assertThat(host.canaries).isFalse();
                    assertThat(host.honeypot).isFalse();
                    // Worth breaching, not merely breachable. Three of these is 9 EC against the
                    // 15 EC Passive Sniffer — the intended first purchase.
                    assertThat(host.lootWei)
                            .isGreaterThanOrEqualTo(Balance.NET_LOOT_FLOOR_WEI);
                }
            }
        }

        @Test
        @DisplayName("a way out of home is always within two links of the rig")
        void routeFloorHolds() {
            for (int i = 0; i < SAMPLE; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                Map<String, Integer> hops = NetTestKit.hops(topology, topology.playerAddress);
                boolean nearby = false;
                for (HostState host : NetTestKit.hostsOn(topology, NetTestKit.home(topology).serverId)) {
                    if (HostKind.BRIDGE.name().equals(host.kind)
                            && hops.getOrDefault(host.address, Integer.MAX_VALUE) <= 2) {
                        nearby = true;
                        break;
                    }
                }
                // Without this, a player who has cleared their neighbourhood has nowhere to go AND no
                // way to see that they have nowhere to go — the map would simply stop growing.
                assertThat(nearby).as("bridge within 2 links on seed %d", seed(i)).isTrue();
            }
        }

        @Test
        @DisplayName("no guaranteed contact is ever a gateway or a bridge")
        void theFloorNeverEatsInfrastructure() {
            for (int i = 0; i < SAMPLE; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                // The gateway is the lowest address on the server and is always one link from the
                // rig, so a naive "first three at one link" would force the server's only signpost
                // into a TERMINAL. A bridge is worse: demoting one would leave a cross-server link on
                // a machine that no longer claims to have one.
                for (HostState host : workableNeighbours(topology)) {
                    assertThat(host.kind).isEqualTo(HostKind.TERMINAL.name());
                    assertThat(host.bridgePeer).isEmpty();
                }
                assertThat(NetTestKit.host(topology, "10.0.0.2").kind).isEqualTo(HostKind.GATEWAY.name());
            }
        }
    }

    @Nested
    @DisplayName("the first sweep")
    class FirstSweep {

        @Test
        @DisplayName("the base sweep always returns at least three workable targets")
        void theFirstSweepAlwaysWorks() {
            for (int i = 0; i < SEEDS; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

                assertThat(report.found())
                        .as("contacts on seed %d", seed(i))
                        .isGreaterThanOrEqualTo(Balance.NET_HOME_GUARANTEED_CONTACTS);

                int workable = 0;
                for (String address : report.foundAddresses()) {
                    HostState host = NetTestKit.host(save.topology, address);
                    if (host.tier == 1 && host.firewallTier == 0) {
                        workable++;
                    }
                }
                // ⚠ Not "every contact is tier 1". The home gateway is HIGH-signal and found by the
                // base sweep about 85% of the time at tier 2 — which is correct and is what the
                // acceptance narrative shows. The guarantee is that at least three WORKABLE targets
                // come back, not that nothing else does.
                assertThat(workable)
                        .as("workable contacts found on seed %d", seed(i))
                        .isGreaterThanOrEqualTo(Balance.NET_HOME_GUARANTEED_CONTACTS);
            }
        }

        @Test
        @DisplayName("it costs two cycles, takes twenty seconds, and reaches exactly one hop")
        void theFirstSweepIsCheap() {
            SoloSave save = NetTestKit.world(seed(3));
            long free = io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules.availableCycles(save.rig);

            var task = NetRules.beginSweep(save, SweepTier.BASE, NetTestKit.T0).orElseThrow();
            assertThat(task.cycles).isEqualTo(Balance.NET_SWEEP_BASE_CYCLES);

            // ⚠ The published twenty seconds is a property of the TOOL; what it actually takes is a
            // property of the MACHINE. A fresh character is born with the tutorial parasite eating
            // six of a hundred cycles, so everything on that rig runs ~6% slower until it is cracked
            // — which is the cheapest hint in the game that something is wrong, and the reason this
            // asserts a bound rather than an equality. Cracking the parasite gives the time back.
            long published = NetTestKit.T0.plusSeconds(Balance.NET_SWEEP_BASE_SECONDS).getEpochSecond();
            assertThat(task.endsAt.getEpochSecond())
                    .as("never faster than the published duration, and slowed only by theft")
                    .isBetween(published, published + Balance.NET_SWEEP_BASE_SECONDS);
            // Held, not spent: the cycles are gone for the duration and only then start recovering,
            // which is the UI-6 shape a scan already takes.
            assertThat(io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules.availableCycles(save.rig))
                    .isEqualTo(free - Balance.NET_SWEEP_BASE_CYCLES);
            assertThat(NetRules.hopCeiling(save)).isEqualTo(1);
        }

        @Test
        @DisplayName("everything it finds is one hop away — the ceiling is a hard gate, not a curve")
        void nothingBeyondTheCeilingIsEverACandidate() {
            for (int i = 0; i < 2_000; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                Map<String, Integer> hops = NetTestKit.hops(save.topology, save.topology.playerAddress);
                SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                for (String address : report.foundAddresses()) {
                    // No probability is consulted beyond the ceiling. A two-hop machine is not an
                    // unlikely find, it is not a candidate at all — which is what makes the Topology
                    // Mapper a reach purchase rather than a better one.
                    assertThat(hops.get(address)).as("hops to %s", address).isEqualTo(1);
                }
            }
        }
    }

    /** The rig's immediate neighbours that the contact floor has forced into shape. */
    private static List<HostState> workableNeighbours(TopologyState topology) {
        HostState rig = NetTestKit.host(topology, topology.playerAddress);
        List<HostState> out = new ArrayList<>();
        for (String address : rig.links) {
            HostState host = NetTestKit.host(topology, address);
            if (host != null && host.detectRoll == 0.0d) {
                out.add(host);
            }
        }
        return out;
    }
}
