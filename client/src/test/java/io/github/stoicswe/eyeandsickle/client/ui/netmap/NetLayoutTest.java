package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where the map puts things.
 *
 * <p>Pure and headless, because {@link NetLayout} is. The one claim worth stating up front is the
 * theorem the whole renderer rests on: in a hop layering, <b>no edge can span more than one
 * column</b>, so there are exactly two edge classes and a skip edge cannot exist. {@code LatticeMap}
 * has the opposite property — it silently drops any {@code rank → rank+2} edge, and no gap ever draws
 * it — and the difference between the two is a rule that holds by construction versus a bug waiting
 * for a graph shaped slightly differently.
 */
class NetLayoutTest {

    private static final int MAX_ROWS = 10;

    private static Map<String, NetLayout.Placed> bySlot(NetLayout.Result result) {
        Map<String, NetLayout.Placed> out = new HashMap<>();
        for (NetLayout.Placed placed : result.placed()) {
            out.put(placed.sighting().address(), placed);
        }
        return out;
    }

    @Nested
    @DisplayName("columns are hop distance, and that is what bounds every edge")
    class Layering {

        @Test
        @DisplayName("no edge spans more than one column — the theorem the router depends on")
        void noEdgeSpansTwoLayers() {
            for (NetMap map : List.of(NetFixtures.opening(), NetFixtures.twoHops(), NetFixtures.crowded(30))) {
                NetLayout.Result result = NetLayout.of(map, MAX_ROWS);
                Map<String, NetLayout.Placed> placed = bySlot(result);
                for (NetLayout.Routed routed : result.routed()) {
                    int from = placed.get(routed.fromAddress()).layer();
                    int to = placed.get(routed.toAddress()).layer();
                    assertThat(Math.abs(from - to))
                            .as("edge %s -> %s spans one column at most", routed.fromAddress(), routed.toAddress())
                            .isLessThanOrEqualTo(1);
                    assertThat(routed.lateral())
                            .as("an edge inside a column is lateral, one across is forward")
                            .isEqualTo(from == to);
                }
            }
        }

        @Test
        @DisplayName("no real edge is dropped — the theorem is asserted, not assumed")
        void everyLinkSurvives() {
            // NetLayout drops an edge it cannot route rather than drawing it wrong. That is the safe
            // failure, and it would also hide a broken hop metric behind a picture that still looks
            // plausible — so the count is checked rather than trusted.
            NetMap map = NetFixtures.twoHops();
            assertThat(NetLayout.of(map, MAX_ROWS).routed()).hasSize(map.links().size());
        }

        @Test
        @DisplayName("the vantage is alone in column zero, at row zero")
        void vantageIsTheOrigin() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops(), MAX_ROWS);
            List<NetLayout.Placed> first = result.placed().stream()
                    .filter(placed -> placed.layer() == 0)
                    .toList();
            assertThat(first).hasSize(1);
            assertThat(first.get(0).row()).isZero();
            assertThat(first.get(0).sighting().vantage()).isTrue();
        }

        @Test
        @DisplayName("a symmetric link pair is one edge, not two")
        void symmetricLinksCollapse() {
            // The rules write links both ways. Drawing both is invisible on a merged junction table
            // and quietly wrong in the lane assignment: every edge would consume two lanes, so a
            // three-lane gap would hold at most one and a half edges before it started stacking.
            NetMap map = NetFixtures.map(
                    List.of(
                            NetFixtures.self("10.0.0.1"),
                            NetFixtures.sighting("10.0.0.2", HostKind.UNKNOWN, 1, false, false, false, "")),
                    List.of(NetFixtures.link("10.0.0.1", "10.0.0.2"), NetFixtures.link("10.0.0.2", "10.0.0.1")),
                    1);
            assertThat(NetLayout.of(map, MAX_ROWS).routed()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("rows are assigned once, the same way every time")
    class Rows {

        @Test
        @DisplayName("the same map lays out identically twice")
        void deterministic() {
            // The packet repaints on a timer. A layout that settled differently between two frames
            // would make the whole map crawl, and it would do it only in the running client.
            NetMap map = NetFixtures.twoHops();
            assertThat(NetLayout.of(map, MAX_ROWS)).isEqualTo(NetLayout.of(map, MAX_ROWS));
        }

        @Test
        @DisplayName("rows within a column are a contiguous run from zero")
        void rowsAreContiguous() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops(), MAX_ROWS);
            Map<Integer, List<Integer>> rows = new HashMap<>();
            for (NetLayout.Placed placed : result.placed()) {
                rows.computeIfAbsent(placed.layer(), k -> new ArrayList<>()).add(placed.row());
            }
            for (Map.Entry<Integer, List<Integer>> entry : rows.entrySet()) {
                List<Integer> sorted = entry.getValue().stream().sorted().toList();
                for (int i = 0; i < sorted.size(); i++) {
                    assertThat(sorted.get(i))
                            .as("column %d has no gap at row %d", entry.getKey(), i)
                            .isEqualTo(i);
                }
            }
        }

        @Test
        @DisplayName("addresses order numerically, so 10.0.0.9 sits above 10.0.0.10")
        void addressesOrderNumerically() {
            // A plain string compare is deterministic, which is all the tiebreak needs, and it would
            // put .10 above .9. A column of addresses that is almost sorted reads as a broken
            // instrument rather than as a rendering choice.
            assertThat(NetLayout.compareAddresses("10.0.0.9", "10.0.0.10")).isNegative();
            assertThat(NetLayout.compareAddresses("10.0.0.10", "10.0.0.9")).isPositive();
            assertThat(NetLayout.compareAddresses("10.0.0.4", "10.0.0.4")).isZero();
            // Anything that is not a dotted number still orders, because a total order is what the
            // sort contract needs and "unparseable" is not an excuse to throw inside a repaint.
            assertThat(NetLayout.compareAddresses("localhost", "10.0.0.4")).isNotZero();
        }
    }

    @Nested
    @DisplayName("a column wider than the panel clamps, and says so")
    class Clamping {

        @Test
        @DisplayName("fifty machines in one column draw ten and report the other forty")
        void clampsAndCounts() {
            // A server holds up to fifty machines. The graph is the legible surface and the list is
            // the exhaustive one; what is not acceptable is drawing forty of them off the panel and
            // saying nothing.
            NetLayout.Result result = NetLayout.of(NetFixtures.crowded(50), MAX_ROWS);
            assertThat(result.rowsPerLayer()).isEqualTo(MAX_ROWS);
            assertThat(result.placed()).hasSize(MAX_ROWS + 1);
            assertThat(result.overflowInLastVisibleLayer()).isEqualTo(40);
            assertThat(result.layerHeaders().get(1)).contains("+40 MORE");
        }

        @Test
        @DisplayName("an edge to a machine that was clamped out is dropped with it")
        void clampedEdgesGo() {
            // An arrow into empty space is worse than a missing arrow: it asserts an adjacency the
            // player cannot see either end of.
            NetLayout.Result result = NetLayout.of(NetFixtures.crowded(50), MAX_ROWS);
            Map<String, NetLayout.Placed> placed = bySlot(result);
            for (NetLayout.Routed routed : result.routed()) {
                assertThat(placed).containsKeys(routed.fromAddress(), routed.toAddress());
            }
        }

        @Test
        @DisplayName("a column that fits carries no marker")
        void noMarkerWhenNothingIsHidden() {
            NetLayout.Result result = NetLayout.of(NetFixtures.opening(), MAX_ROWS);
            assertThat(result.overflowInLastVisibleLayer()).isZero();
            assertThat(result.layerHeaders()).noneMatch(header -> header.contains("MORE"));
        }
    }

    @Nested
    @DisplayName("headers answer \"which network am I looking at\"")
    class Headers {

        @Test
        @DisplayName("one per column, naming the hop and the server")
        void headersNameHopAndServer() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops(), MAX_ROWS);
            assertThat(result.layerHeaders()).hasSize(result.layers());
            assertThat(result.layerHeaders().get(0)).startsWith("H0").contains(NetFixtures.HOME.name());
            assertThat(result.layerHeaders().get(1)).startsWith("H1");
        }
    }

    @Nested
    @DisplayName("nothing discovered")
    class Empty {

        @Test
        @DisplayName("an empty map lays out to nothing at all, and does not throw")
        void emptyIsEmpty() {
            // Not a placeholder and not a count. An undiscovered machine has no Sighting, so there is
            // nothing here that could leak even by accident.
            NetLayout.Result result = NetLayout.of(NetMap.empty(), MAX_ROWS);
            assertThat(result.layers()).isZero();
            assertThat(result.placed()).isEmpty();
            assertThat(result.routed()).isEmpty();
            assertThat(NetLayout.of(null, MAX_ROWS)).isEqualTo(NetLayout.Result.empty());
        }
    }
}
