package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the map window's two-view toggle and for the resources it reads.
 *
 * <h2>What is asserted here and what is asserted by construction</h2>
 *
 * The toggle is {@link NetMapView.Display} and nothing else — there is no second flag, no visibility
 * read back out of the scene graph, and exactly one place where either view's visibility is set. So
 * the state machine is testable without a toolkit, and the wiring from it to two {@code setVisible}
 * calls is short enough to be read.
 *
 * <p>The same holds for "both views bind to the same map": {@code repaint} calls
 * {@code session.net()} once and hands the <em>same instance</em> to both, so there is no second
 * read for them to disagree about. What is asserted here is the consequence a player can check —
 * that the strip, the graph's layering input and the list's rows all come out of one {@link NetMap}.
 *
 * <p>No test in this module starts the JavaFX toolkit. Making this the first one would put a display
 * dependency into the shared build to assert two {@code setVisible} calls, which is a bad trade; the
 * integrator is better placed to decide whether the module should grow that capability at all. It is
 * raised in the integration note.
 */
class NetMapViewTest {

    private static final ServerRef HOME = new ServerRef("s0", "home-relay", 0, true);

    private static NetMap map() {
        Sighting self = new Sighting("10.0.0.1", "localhost", "s0", HostKind.SELF, null,
                SignalStrength.LOW, 0, true, true, false, false, false, false, "");
        Sighting contact = new Sighting("10.0.0.4", "", "s0", HostKind.UNKNOWN,
                DifficultyTier.of(1), SignalStrength.MODERATE, 1,
                false, false, false, false, false, false, "");
        return new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), List.of(self, contact), List.of());
    }

    @Nested
    @DisplayName("the toggle")
    class Toggle {

        @Test
        @DisplayName("toggling switches between the two views and back")
        void switches() {
            assertThat(NetMapView.Display.GRAPH.toggled()).isEqualTo(NetMapView.Display.LIST);
            assertThat(NetMapView.Display.LIST.toggled()).isEqualTo(NetMapView.Display.GRAPH);
            assertThat(NetMapView.Display.GRAPH.toggled().toggled())
                    .isEqualTo(NetMapView.Display.GRAPH);
        }

        @Test
        @DisplayName("exactly one view shows at a time — there is no state where both or neither do")
        void exactlyOne() {
            for (NetMapView.Display display : NetMapView.Display.values()) {
                assertThat(display.showsGraph() ^ display.showsList())
                        .as("%s shows exactly one view", display)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("which view is showing is carried by brackets, not by a text fill")
        void bracketsCarryTheState() {
            // §4.4's rule for the graph applies to its chrome: weight and shape first, the grey ramp
            // second. A toggle whose only signal is a colour is invisible in a greyscale capture and
            // says nothing at all to a screen reader.
            assertThat(NetMapView.Display.GRAPH.control(NetMapView.Display.GRAPH))
                    .isEqualTo("[ GRAPH ]");
            assertThat(NetMapView.Display.LIST.control(NetMapView.Display.GRAPH))
                    .isEqualTo("  LIST  ");
            assertThat(NetMapView.Display.LIST.control(NetMapView.Display.LIST))
                    .isEqualTo("[ LIST ]");
        }

        @Test
        @DisplayName("both controls keep the same width in both states, so nothing shifts on a click")
        void stableWidth() {
            for (NetMapView.Display active : NetMapView.Display.values()) {
                for (NetMapView.Display control : NetMapView.Display.values()) {
                    assertThat(control.control(active))
                            .as("%s while %s is active", control, active)
                            .hasSize(control.name().length() + 4);
                }
            }
        }
    }

    @Nested
    @DisplayName("one map, two surfaces")
    class OneMap {

        @Test
        @DisplayName("the strip and the rows are derived from the same map and agree about it")
        void agree() {
            NetMap map = map();
            assertThat(NetText.serverStrip(map)).contains("home-relay").contains("HOSTS SEEN 2");
            assertThat(NetText.rows(map, false)).hasSize(2);
            // The list is exhaustive: every sighting the map carries gets a row, with no cap and no
            // paging. The graph is the one that clamps, and it says so when it does.
            assertThat(NetText.rows(map, false)).hasSize(map.sightings().size());
        }

        @Test
        @DisplayName("an empty map produces a strip and no rows, and never throws")
        void empty() {
            assertThat(NetText.rows(NetMap.empty(), false)).isEmpty();
            assertThat(NetText.serverStrip(NetMap.empty())).contains("CEILING 1 HOP");
        }
    }

    @Nested
    @DisplayName("the recovered fragments ship")
    class Documents {

        /** The twelve the generator assigns. A body that is not on the classpath is unreadable. */
        private static final List<String> IDS = List.of(
                "doc.rota", "doc.tariff", "doc.notice", "doc.transcript", "doc.manifest", "doc.memo",
                "doc.roster", "doc.letter", "doc.audit", "doc.log", "doc.spec", "doc.index");

        @Test
        @DisplayName("every id the generator can assign resolves to real text")
        void allTwelveResolve() {
            for (String id : IDS) {
                List<String> body = NetText.documentBody(id);
                assertThat(body).as("%s has a body", id).isNotEmpty();
                assertThat(String.join(" ", body))
                        .as("%s is not the unreadable placeholder", id)
                        .doesNotContain(NetText.UNREADABLE);
            }
        }

        @Test
        @DisplayName("a fragment is 60 to 150 words — long enough to be worth reading, short enough to be")
        void length() {
            for (String id : IDS) {
                int words = String.join(" ", NetText.documentBody(id)).trim().split("\\s+").length;
                assertThat(words).as("%s word count", id).isBetween(60, 150);
            }
        }

        @Test
        @DisplayName("nothing in a fragment addresses the player or instructs them")
        void noSecondPerson() {
            // docs/design/14 keeps story in recovered records rather than in a voice talking to the
            // player, and decision N-4 keeps it off the critical path. A fragment that said "you"
            // would be a companion character in a text file.
            for (String id : IDS) {
                String body = " "
                        + String.join(" ", NetText.documentBody(id)).toLowerCase(java.util.Locale.ROOT)
                        + " ";
                for (String pronoun : List.of(" you ", " your ", " yours ", " you're ", " you've ")) {
                    assertThat(body).as("%s avoids '%s'", id, pronoun.trim()).doesNotContain(pronoun);
                }
            }
        }

        @Test
        @DisplayName("a missing fragment is unreadable, not an exception — and never a path escape")
        void missingAndHostile() {
            // Unreadable is a valid in-fiction outcome: a partial recovery off a defended machine
            // reading as unreadable is what the fiction says happens. Nothing may stall on one,
            // because a document is flavour and flavour must never be able to block a run.
            assertThat(NetText.documentBody("doc.nothing")).containsExactly(NetText.UNREADABLE);
            assertThat(NetText.documentBody("")).containsExactly(NetText.UNREADABLE);
            assertThat(NetText.documentBody(null)).containsExactly(NetText.UNREADABLE);
            assertThat(NetText.documentBody("../../../../etc/passwd"))
                    .containsExactly(NetText.UNREADABLE);
            assertThat(NetText.documentBody("/absolute")).containsExactly(NetText.UNREADABLE);
        }
    }
}
