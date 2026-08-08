package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the port scanner offers to scan for, and on which machines.
 *
 * <h2>The defect this pins</h2>
 *
 * The panel walked {@code PortScanTarget.values()}, so every ordinary machine in the game offered
 * <b>Peers</b> and <b>Monitoring</b> — two rungs {@code PortScanTarget.appliesTo} says exist on a
 * bridge and nowhere else. Both quoted a real price, a real duration and a real detection risk, and
 * both answer {@code -1} on anything that is not a bridge. Nothing failed; the panel was selling an
 * answer that does not exist.
 *
 * <p>No toolkit here, and that is the point: {@link PortScanView#rungsFor} takes a {@link NetMap}
 * rather than a session precisely so the rule can be checked without building a node. The bug shipped
 * because the rule lived inside a repaint.
 */
class PortScanViewTest {

    private static final ServerRef HOME = new ServerRef("s0", "home-relay", 0, true);

    /** Every rung an ordinary machine has — the eight that were calibrated before bridges existed. */
    private static final List<PortScanTarget> ORDINARY = List.of(
            PortScanTarget.IDENTITY,
            PortScanTarget.FIREWALL,
            PortScanTarget.OS_VERSION,
            PortScanTarget.CYCLE_CAPABILITY,
            PortScanTarget.CYCLE_LOAD,
            PortScanTarget.DOWNLOADS,
            PortScanTarget.VAULT_HIGH,
            PortScanTarget.VAULT_MEDIUM);

    private static NetMap mapOf(Sighting... sightings) {
        return new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), List.of(sightings), List.of());
    }

    private static Sighting machine(String address, HostKind kind, String peerServerName) {
        return new Sighting(
                address,
                "",
                "s0",
                kind,
                DifficultyTier.of(1),
                SignalStrength.MODERATE,
                1,
                false,
                false,
                false,
                false,
                false,
                false,
                peerServerName);
    }

    @Nested
    @DisplayName("an ordinary machine")
    class Ordinary {

        @Test
        @DisplayName("is offered the calibrated eight and neither bridge rung")
        void eight() {
            NetMap net = mapOf(machine("10.0.0.3", HostKind.TERMINAL, ""));

            assertThat(PortScanView.rungsFor(net, "10.0.0.3")).containsExactlyElementsOf(ORDINARY);
        }

        @Test
        @DisplayName("is offered no peer count and no monitoring, whatever it is")
        void neverTheBridgeRungs() {
            for (HostKind kind : HostKind.values()) {
                if (kind == HostKind.BRIDGE) {
                    continue;
                }
                assertThat(PortScanView.rungsFor(mapOf(machine("10.0.0.3", kind, "")), "10.0.0.3"))
                        .as("rungs offered on a %s", kind)
                        .doesNotContain(PortScanTarget.PEERS, PortScanTarget.MONITORED);
            }
        }

        @Test
        @DisplayName("with no sighting reads as untyped rather than as an error")
        void absent() {
            // Map visibility keys on knownNodes and port scanning keys on host.discovered — two
            // notions of "found" that agree only because a sweep sets both. The honest reading of an
            // absent sighting is "nobody has typed this machine", not "this machine has no findings".
            assertThat(PortScanView.rungsFor(mapOf(), "10.0.0.3")).containsExactlyElementsOf(ORDINARY);
            assertThat(PortScanView.rungsFor(null, "10.0.0.3")).containsExactlyElementsOf(ORDINARY);
        }
    }

    @Nested
    @DisplayName("a bridge")
    class Bridge {

        @Test
        @DisplayName("is offered its own five, and none of the rungs a router does not have")
        void five() {
            NetMap net = mapOf(machine("10.0.0.9", HostKind.BRIDGE, "vega-exchange"));

            assertThat(PortScanView.rungsFor(net, "10.0.0.9"))
                    .containsExactly(
                            PortScanTarget.IDENTITY,
                            PortScanTarget.FIREWALL,
                            PortScanTarget.OS_VERSION,
                            PortScanTarget.PEERS,
                            PortScanTarget.MONITORED);
        }

        @Test
        @DisplayName("does not advertise itself before something has typed it")
        void unidentified() {
            // ⚠ THE LEAK THIS EXISTS TO STOP. Sighting.kind is UNKNOWN until a type-revealing tool has
            // run — a sweep sells existence and adjacency, the 15 EC Passive Sniffer sells identity
            // (docs/design/07 §1). Filtering on the topology's own kind instead would put "Peers" and
            // "Monitoring" on unidentified bridges and nowhere else, which hands the sniffer's whole
            // product to anyone who right-clicks a machine.
            NetMap net = mapOf(machine("10.0.0.9", HostKind.UNKNOWN, ""));

            assertThat(PortScanView.rungsFor(net, "10.0.0.9")).containsExactlyElementsOf(ORDINARY);
        }
    }

    @Nested
    @DisplayName("the ladder")
    class Ladder {

        @Test
        @DisplayName("is in ascending depth order on every kind of machine")
        void ordered() {
            // The panel is a price list read top to bottom, and PEERS/MONITORED share depths 4 and 5
            // with rungs a bridge does not have — so a filtered list is only legible if what survives
            // is still ordered. Depths are also unique within one kind, which is what makes sharing
            // them safe at all (MonJobsTest.depthsAreUniqueWithinAKind).
            for (HostKind kind : HostKind.values()) {
                List<PortScanTarget> rungs = PortScanView.rungsFor(mapOf(machine("10.0.0.3", kind, "")), "10.0.0.3");
                assertThat(rungs).as("rungs on a %s", kind).isNotEmpty();
                for (int i = 1; i < rungs.size(); i++) {
                    assertThat(rungs.get(i).depth())
                            .as("%s after %s on a %s", rungs.get(i), rungs.get(i - 1), kind)
                            .isGreaterThan(rungs.get(i - 1).depth());
                }
            }
        }
    }
}
