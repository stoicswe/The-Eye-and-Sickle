package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The map's per-server tab strip, and the filter behind it.
 *
 * <h2>What is asserted here and what is asserted by construction</h2>
 *
 * The strip is a pure function of the {@link NetMap} — there is no second source of "which servers
 * are there", no set accumulated as the player travels, and nothing derived from the scene graph. So
 * the whole model is testable without a toolkit, and the wiring from it to a row of chips is short
 * enough to read.
 *
 * <p>⚠ The rules that matter here are the ones a careless filter breaks silently: a tab for a server
 * the player has never heard of, a link left pointing at a machine that is not on the grid, and the
 * rig turning up on somebody else's server.
 */
class ServerTabsTest {

    private static final ServerRef HOME = new ServerRef("s0", "wicked-freeman", 0, true);
    private static final ServerRef FAR = new ServerRef("s1", "clandestine-atreides", 1, false);
    private static final ServerRef UNSEEN = new ServerRef("s2", "sultry-cortana", 2, false);

    private static Sighting machine(String address, String serverId, int hopsFromRig, boolean self) {
        return new Sighting(
                address,
                "",
                serverId,
                self ? HostKind.SELF : HostKind.UNKNOWN,
                self ? null : DifficultyTier.of(1),
                SignalStrength.MODERATE,
                hopsFromRig,
                hopsFromRig,
                self,
                self,
                false,
                false,
                false,
                false,
                false,
                false,
                "",
                false,
                "");
    }

    /** Home with the rig and two machines; one server across a bridge with two of its own. */
    private static NetMap world() {
        return new NetMap(
                HOME,
                "10.0.0.1",
                1,
                List.of(HOME, FAR),
                List.of(
                        machine("10.0.0.1", "s0", 0, true),
                        machine("10.0.0.2", "s0", 1, false),
                        machine("10.0.0.3", "s0", 2, false),
                        machine("10.1.0.7", "s1", 3, false),
                        machine("10.1.0.8", "s1", 4, false)),
                List.of(
                        new NetLink("10.0.0.1", "10.0.0.2", false),
                        new NetLink("10.0.0.2", "10.0.0.3", false),
                        // The bridge's own edge, which crosses servers and must not survive a filter.
                        new NetLink("10.0.0.3", "10.1.0.7", true),
                        new NetLink("10.1.0.7", "10.1.0.8", false)));
    }

    @Nested
    @DisplayName("the strip")
    class Strip {

        @Test
        @DisplayName("has one tab per server the player has heard of, and none for any other")
        void oneTabPerKnownServer() {
            // ⚠ NetMap.knownServers is already "what the player has heard of" — a server reaches it by
            // being swept or by an identified bridge advertising it. Enumerating anything else would
            // publish the shape of the world for free, which is the rule NetRules states as
            // "undiscovered hosts do not exist in knownNodes, and the map draws nothing where they are".
            assertThat(ServerTabs.of(world()).stream().map(ServerTabs.Tab::serverId))
                    .containsExactly("s0", "s1")
                    .doesNotContain(UNSEEN.serverId());
        }

        @Test
        @DisplayName("puts home first and orders the rest by name")
        void homeFirst() {
            NetMap map = new NetMap(
                    FAR,
                    "10.1.0.7",
                    1,
                    // Deliberately out of order, and with home in the middle.
                    List.of(UNSEEN, HOME, FAR),
                    world().sightings(),
                    world().links());

            List<ServerTabs.Tab> tabs = ServerTabs.of(map);
            assertThat(tabs.getFirst().home()).isTrue();
            assertThat(tabs.stream().map(ServerTabs.Tab::label))
                    .containsExactly("wicked-freeman", "clandestine-atreides", "sultry-cortana");
        }

        @Test
        @DisplayName("⚠ finds home from the rig's own sighting, not from the ServerRef flag")
        void homeIsWhereTheRigIs() {
            // The flag reaches the client through several producers and the fixtures set it
            // inconsistently; the machine that says `self` is the answer that cannot be wrong.
            ServerRef lying = new ServerRef("s0", "wicked-freeman", 0, false);
            NetMap map = new NetMap(
                    lying, "10.0.0.1", 1, List.of(FAR, lying), world().sightings(), world().links());

            assertThat(ServerTabs.of(map).getFirst().serverId()).isEqualTo("s0");
        }

        @Test
        @DisplayName("marks the server the player is standing on, and opens there")
        void opensWhereTheVantageIs() {
            NetMap away = new NetMap(
                    FAR, "10.1.0.7", 1, List.of(HOME, FAR), world().sightings(), world().links());

            // ⚠ Not home. A player four servers out who opens the map and is shown a server they left
            // an hour ago has been given the wrong answer to "where am I".
            assertThat(ServerTabs.initial(away)).isEqualTo("s1");
            assertThat(ServerTabs.of(away).stream().filter(ServerTabs.Tab::current))
                    .singleElement()
                    .extracting(ServerTabs.Tab::serverId)
                    .isEqualTo("s1");
        }

        @Test
        @DisplayName("counts what has been found on each server, and zero is a real answer")
        void countsMachines() {
            // ⚠ A tab may legitimately be EMPTY: an identified bridge advertises the server on its far
            // side by name, and until the player crosses it that name is all they have. The tab exists
            // and says so — which is the whole point of the bridge finding.
            NetMap justHeardOf = new NetMap(
                    HOME,
                    "10.0.0.1",
                    1,
                    List.of(HOME, FAR),
                    List.of(machine("10.0.0.1", "s0", 0, true)),
                    List.of());

            List<ServerTabs.Tab> tabs = ServerTabs.of(justHeardOf);
            assertThat(tabs).hasSize(2);
            assertThat(tabs.getFirst().machines()).isEqualTo(1);
            assertThat(tabs.get(1).machines()).isZero();
            assertThat(tabs.get(1).explored()).isFalse();
        }
    }

    @Nested
    @DisplayName("the filter")
    class Filter {

        @Test
        @DisplayName("keeps one server's machines and nobody else's")
        void keepsOneServer() {
            assertThat(ServerTabs.filter(world(), "s1").sightings())
                    .extracting(Sighting::address)
                    .containsExactly("10.1.0.7", "10.1.0.8");
        }

        @Test
        @DisplayName("⚠ drops the bridge's own edge, because one of its ends is not on the grid")
        void dropsCrossingLinks() {
            // ⚠ THE FAILURE THIS PREVENTS IS NOT A CRASH. NetLayout's adjacency pass would build a
            // neighbour set containing a machine that has no sighting, and the barycentre arrangement
            // would order the layer around something invisible — a grid that is subtly wrong with
            // nothing to point at.
            assertThat(ServerTabs.filter(world(), "s0").links())
                    .extracting(NetLink::toAddress)
                    .doesNotContain("10.1.0.7");
            assertThat(ServerTabs.filter(world(), "s1").links())
                    .extracting(NetLink::fromAddress)
                    .doesNotContain("10.0.0.3");
            // And what survives is exactly the intra-server edges.
            assertThat(ServerTabs.filter(world(), "s1").links()).hasSize(1);
        }

        @Test
        @DisplayName("⚠ leaves the rig on its own server and plants it on no other")
        void theRigStaysHome() {
            assertThat(ServerTabs.filter(world(), "s0").sightings()).anyMatch(Sighting::self);
            assertThat(ServerTabs.filter(world(), "s1").sightings()).noneMatch(Sighting::self);
        }

        @Test
        @DisplayName("an unknown or blank server yields an empty map, never the world")
        void unknownIsEmpty() {
            // The dangerous default is the other one: a filter that fell through to "everything" would
            // put the whole world on a tab named after one server.
            assertThat(ServerTabs.filter(world(), "s9").sightings()).isEmpty();
            assertThat(ServerTabs.filter(world(), "").sightings()).isEmpty();
            assertThat(ServerTabs.filter(null, "s0").sightings()).isEmpty();
        }

        @Test
        @DisplayName("keeps the strip intact, so the tabs survive being on a filtered map")
        void keepsTheServerList() {
            assertThat(ServerTabs.of(ServerTabs.filter(world(), "s1")))
                    .extracting(ServerTabs.Tab::serverId)
                    .containsExactly("s0", "s1");
        }
    }

    @Nested
    @DisplayName("laid out")
    class LaidOut {

        @Test
        @DisplayName("⚠ a foreign server starts at layer 0, not at its distance from the rig")
        void layersAreRebased() {
            // ⚠ WITHOUT THE REBASE THE TAB IS BLANK-LOOKING. s1's machines are 3 and 4 hops from the
            // rig, so the grid would open with three empty columns and the content off the right-hand
            // edge — which reads as a broken view rather than as a distant server.
            NetLayout.Result far = NetLayout.of(ServerTabs.filter(world(), "s1"));

            assertThat(far.layers()).isEqualTo(2);
            assertThat(far.placed()).extracting(NetLayout.Placed::layer).containsExactlyInAnyOrder(0, 1);
        }

        @Test
        @DisplayName("⚠ and the whole-world map is completely unchanged by it")
        void homeIsUntouched() {
            // The rig is always at hop 0, so the base is 0 and every layer keeps the number it had.
            // This is what makes the rebase safe to do inside NetLayout rather than at the call site.
            NetLayout.Result whole = NetLayout.of(world());

            assertThat(whole.layers()).isEqualTo(5);
            assertThat(whole.placed())
                    .filteredOn(placed -> placed.sighting().self())
                    .singleElement()
                    .extracting(NetLayout.Placed::layer)
                    .isEqualTo(0);
        }
    }
}
