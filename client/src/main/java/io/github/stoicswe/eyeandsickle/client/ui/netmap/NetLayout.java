package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.breach.AsciiCanvas;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where every discovered machine sits on the map, and which edges may be drawn between them.
 *
 * <p>Pure: a {@link NetMap} in, placed cells out, no JavaFX and no state. That is not tidiness — a
 * graph layout whose output can only be inspected by launching the client is a graph layout nobody
 * tunes, and every geometric claim below is asserted headlessly in {@code NetLayoutTest}.
 *
 * <h2>The column is the hop count, and that is what makes the picture tractable</h2>
 *
 * Nodes are placed in columns by <b>hop distance from the vantage</b>. The implementation spec derives
 * this as a BFS layering over the discovered subgraph; it is the same number, because
 * {@code Sighting.hopsFromVantage} already <em>is</em> a BFS distance — computed by the rules over the
 * full link graph, where undiscovered intermediate machines still conduct. Reading it rather than
 * re-deriving it has three consequences worth stating:
 *
 * <ol>
 *   <li>The x axis of the picture is the same quantity the rules are written in. Hop range is the
 *       game's hard ceiling ({@code docs/design/07-recon-tools.md} §2, Invariant I2 — schematics buy
 *       reach, ethecoin buys sensitivity), so a column is literally "one purchase of reach away".
 *   <li><b>Skip edges cannot exist.</b> Two machines joined by a link are adjacent in the full graph,
 *       so their BFS distances from any one source differ by at most one. Therefore every edge joins
 *       layers {@code k} and {@code k} (lateral) or {@code k} and {@code k+1} (forward), and there are
 *       exactly two edge classes to route. {@code LatticeMap}'s known correctness gap — {@code
 *       edgeColumn} silently drops any {@code rank → rank+2} edge, so a real edge is simply absent
 *       from the picture the player is deducing on — is impossible here <em>by construction</em>
 *       rather than by a fix that a later change could undo.
 *   <li>A machine reached only through undiscovered intermediates still lands in the right column. A
 *       re-derivation over the discovered subgraph alone would push it further out than the rules say
 *       it is, or drop it into an unreachable component.
 * </ol>
 *
 * <p>The theorem is still asserted rather than assumed: {@link #of} <b>drops</b> any link whose
 * endpoints are more than one layer apart instead of drawing it wrong, and the test asserts none is
 * ever dropped. A silently mis-routed edge is worse than a missing one on a map whose entire job is to
 * tell the player what is next to what.
 *
 * <h2>Row assignment: one barycentre pass, never iterated</h2>
 *
 * Layer 0 is the vantage, at row 0. Each subsequent layer sorts its nodes by the mean row of their
 * already-placed neighbours one layer back, ties broken by address, and takes rows {@code 0, 1, 2, …}
 * in that order. One pass rather than iterating to convergence, because the layout has to be
 * <b>identical on every repaint</b>: the packet animation repaints on a timer, and a layout that
 * settles differently between two frames would make the whole map crawl.
 *
 * <p>The one reference to {@link AsciiCanvas} here is to its {@code BULLET} constant, and it costs
 * nothing: a {@code static final char} initialised from a literal is a compile-time constant, so
 * javac inlines it and no reference to a JavaFX-derived class survives into this class file. The
 * separator vocabulary stays shared without this class acquiring a toolkit dependency.
 */
public final class NetLayout {

    private NetLayout() {}

    /**
     * What a clamped layer's header puts in front of its {@code "+N MORE"} count.
     *
     * <p>Shared with {@link NetCanvas} rather than written out twice: the canvas has to fit a header
     * into a fixed column and must protect this suffix while it shortens everything else, so the two
     * classes have to agree on where the marker starts. A separator that drifted apart between them
     * would fail by <em>silently dropping the marker</em> — the exact case a player needs it, a column
     * of fifty machines showing ten.
     */
    static final String CLAMP_MARK = " " + AsciiCanvas.BULLET + " +";

    /**
     * A machine, and where it is drawn.
     *
     * @param layer hop distance from the vantage — the column
     * @param row the slot within that column, counted from the top
     */
    public record Placed(Sighting sighting, int layer, int row) {}

    /**
     * An edge that may be drawn, oriented so the renderer never has to think about direction.
     *
     * <p>{@code fromAddress} is always the shallower endpoint for a forward edge, and the upper one
     * for a lateral edge. {@code bridge} is carried through from {@link NetLink} so the renderer can
     * treat a cross-server link as the structural fact it is rather than inferring it from the two
     * endpoints' server ids.
     */
    public record Routed(String fromAddress, String toAddress, boolean lateral, boolean bridge) {}

    /**
     * The finished layout.
     *
     * @param layerHeaders one per layer, e.g. {@code "H1 · home-relay"}. A layer that had to clamp
     *     carries its own {@code "· +N MORE"} suffix here — see {@link #of} for why the marker lives in
     *     the header rather than in a node row
     * @param rowsPerLayer the tallest layer, which is what sizes the character grid
     * @param overflowInLastVisibleLayer how many machines the deepest layer could not draw
     */
    public record Result(
            List<Placed> placed,
            List<Routed> routed,
            List<String> layerHeaders,
            int layers,
            int rowsPerLayer,
            int overflowInLastVisibleLayer) {

        /** Nothing discovered. A view renders this as an instruction, never as a blank panel. */
        public static Result empty() {
            return new Result(List.of(), List.of(), List.of(), 0, 0, 0);
        }
    }

    /**
     * Lays out the discovered network.
     *
     * <h2>Clamping, and where the "+N MORE" marker went</h2>
     *
     * A server may hold up to fifty machines, and the graph is the <em>legible</em> surface while the
     * list is the exhaustive one — so a layer wider than {@code maxRows} draws the first
     * {@code maxRows} and says so. The spec puts that marker in the column's last row; it is emitted
     * here as a suffix on the layer <b>header</b> instead, for one structural reason: {@link Result} is
     * a normative signature with no per-layer overflow field, so a renderer handed only a {@code
     * Result} cannot tell <em>which</em> column clamped. Encoding it in that column's own header is the
     * only placement that keeps the information attached to the column it describes, it carries the
     * same {@code .es-netmap-layer} style class the spec asks the marker to have, and it cannot shear
     * the four-line cell rhythm the way an odd-height row would. {@code overflowInLastVisibleLayer}
     * reports the deepest layer's omission, which is the number a panel note quotes.
     *
     * @param map the player's visible network; {@code null} and empty both yield {@link Result#empty()}
     * @param maxRows the tallest column this renderer will draw, {@code UiTokens.NET_MAX_ROWS}
     */
    public static Result of(NetMap map, int maxRows) {
        if (map == null || map.sightings().isEmpty()) {
            return Result.empty();
        }
        int rowCap = Math.max(1, maxRows);

        Map<String, Sighting> byAddress = new LinkedHashMap<>();
        int layers = 0;
        for (Sighting sighting : map.sightings()) {
            byAddress.put(sighting.address(), sighting);
            layers = Math.max(layers, sighting.hopsFromVantage() + 1);
        }

        Map<Integer, List<Sighting>> byLayer = new HashMap<>();
        for (Sighting sighting : map.sightings()) {
            byLayer.computeIfAbsent(sighting.hopsFromVantage(), k -> new ArrayList<>())
                    .add(sighting);
        }

        Map<String, Set<String>> neighbours = adjacency(map, byAddress);

        List<Placed> placed = new ArrayList<>();
        Map<String, Integer> rowOf = new HashMap<>();
        Map<String, Integer> layerOf = new HashMap<>();
        int[] omitted = new int[layers];
        int rowsPerLayer = 0;

        for (int layer = 0; layer < layers; layer++) {
            List<Sighting> here = new ArrayList<>(byLayer.getOrDefault(layer, List.of()));
            here.sort(order(layer, neighbours, rowOf, layerOf));

            int drawn = Math.min(here.size(), rowCap);
            omitted[layer] = here.size() - drawn;
            rowsPerLayer = Math.max(rowsPerLayer, drawn);
            for (int row = 0; row < drawn; row++) {
                Sighting sighting = here.get(row);
                placed.add(new Placed(sighting, layer, row));
                rowOf.put(sighting.address(), row);
                layerOf.put(sighting.address(), layer);
            }
        }

        return new Result(
                List.copyOf(placed),
                routes(map, layerOf),
                headers(map, byLayer, omitted, layers),
                layers,
                rowsPerLayer,
                layers == 0 ? 0 : omitted[layers - 1]);
    }

    /**
     * Sorts one layer by barycentre.
     *
     * <p>Layer 0 sorts by address alone, which puts the vantage at row 0 in every map the rules can
     * produce — it is the only machine at hop zero. Nodes with no already-placed neighbour one layer
     * back sort last ({@link Double#POSITIVE_INFINITY}); they are reachable only laterally, and
     * hanging them off the bottom keeps them next to the lateral strip that will join them.
     */
    private static Comparator<Sighting> order(
            int layer, Map<String, Set<String>> neighbours, Map<String, Integer> rowOf, Map<String, Integer> layerOf) {
        Map<String, Double> desired = new HashMap<>();
        return Comparator.<Sighting, Double>comparing(sighting -> desired.computeIfAbsent(
                        sighting.address(), address -> barycentre(address, layer, neighbours, rowOf, layerOf)))
                .thenComparing(sighting -> sighting.address(), NetLayout::compareAddresses);
    }

    private static double barycentre(
            String address,
            int layer,
            Map<String, Set<String>> neighbours,
            Map<String, Integer> rowOf,
            Map<String, Integer> layerOf) {
        if (layer == 0) {
            return 0;
        }
        double total = 0;
        int count = 0;
        for (String peer : neighbours.getOrDefault(address, Set.of())) {
            Integer peerLayer = layerOf.get(peer);
            Integer peerRow = rowOf.get(peer);
            if (peerLayer != null && peerRow != null && peerLayer == layer - 1) {
                total += peerRow;
                count++;
            }
        }
        return count == 0 ? Double.POSITIVE_INFINITY : total / count;
    }

    /**
     * Orders two addresses the way a reader expects.
     *
     * <p>A plain string compare is deterministic, which is all the spec's tiebreak asks for, but it
     * puts {@code 10.0.0.10} above {@code 10.0.0.9} — and a column of addresses that is <em>almost</em>
     * sorted reads as a bug in the instrument rather than as a rendering choice. Comparing dotted
     * components numerically where both sides parse, and falling back to a string compare otherwise,
     * is a strict refinement: still total, still deterministic, still stable across repaints.
     */
    static int compareAddresses(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int shared = Math.min(a.length, b.length);
        for (int i = 0; i < shared; i++) {
            Integer x = parse(a[i]);
            Integer y = parse(b[i]);
            if (x == null || y == null) {
                return left.compareTo(right);
            }
            if (!x.equals(y)) {
                return Integer.compare(x, y);
            }
        }
        int lengths = Integer.compare(a.length, b.length);
        return lengths != 0 ? lengths : left.compareTo(right);
    }

    private static Integer parse(String part) {
        if (part.isEmpty() || part.length() > 9) {
            return null;
        }
        for (int i = 0; i < part.length(); i++) {
            if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                return null;
            }
        }
        return Integer.valueOf(part);
    }

    /** Undirected adjacency over links whose endpoints the player has both discovered. */
    private static Map<String, Set<String>> adjacency(NetMap map, Map<String, Sighting> byAddress) {
        Map<String, Set<String>> neighbours = new HashMap<>();
        for (NetLink link : map.links()) {
            if (!byAddress.containsKey(link.fromAddress()) || !byAddress.containsKey(link.toAddress())) {
                // NetLink's contract says both endpoints are always present. Skipping rather than
                // trusting it costs nothing and means a producer bug shows up as a missing edge
                // instead of as an exception thrown from inside a repaint on the FX thread.
                continue;
            }
            neighbours.computeIfAbsent(link.fromAddress(), k -> new HashSet<>()).add(link.toAddress());
            neighbours.computeIfAbsent(link.toAddress(), k -> new HashSet<>()).add(link.fromAddress());
        }
        return neighbours;
    }

    /**
     * Every drawable edge, de-duplicated and oriented.
     *
     * <p>Links arrive symmetrically from the rules, so an unordered-pair key is what stops every edge
     * being drawn twice — harmless for a merged junction table, but it would double-count the lane
     * assignment and push edges into lanes that hold nothing.
     */
    private static List<Routed> routes(NetMap map, Map<String, Integer> layerOf) {
        Map<Pair, Boolean> bridging = new LinkedHashMap<>();
        for (NetLink link : map.links()) {
            Integer from = layerOf.get(link.fromAddress());
            Integer to = layerOf.get(link.toAddress());
            if (from == null || to == null) {
                // One endpoint was clamped out of its column. Its edges go with it: an edge drawn to
                // a cell that is not there is a line into empty space.
                continue;
            }
            if (Math.abs(from - to) > 1) {
                // Unreachable given hop distances (see the class comment's theorem), and dropped
                // rather than drawn wrong if the rules ever hand us one.
                continue;
            }
            // A forward edge is oriented shallow-first so the renderer never has to re-decide
            // direction; a lateral one is oriented by address, because which of two same-layer
            // machines is "upper" is a row fact the renderer owns and this class has not finished
            // computing when the edge list is built.
            boolean firstIsLow =
                    from.equals(to) ? compareAddresses(link.fromAddress(), link.toAddress()) <= 0 : from < to;
            Pair pair = firstIsLow
                    ? new Pair(link.fromAddress(), link.toAddress())
                    : new Pair(link.toAddress(), link.fromAddress());
            bridging.merge(pair, link.bridge(), (a, b) -> a || b);
        }

        List<Routed> routed = new ArrayList<>();
        for (Map.Entry<Pair, Boolean> entry : bridging.entrySet()) {
            Pair pair = entry.getKey();
            boolean lateral = layerOf.get(pair.low()).equals(layerOf.get(pair.high()));
            routed.add(new Routed(pair.low(), pair.high(), lateral, entry.getValue()));
        }
        // Deterministic order: the lane a forward edge takes is its index within its gap, so an
        // unstable iteration order would shuffle the lanes between repaints.
        routed.sort(Comparator.comparing(Routed::fromAddress, NetLayout::compareAddresses)
                .thenComparing(Routed::toAddress, NetLayout::compareAddresses));
        return List.copyOf(routed);
    }

    /**
     * One header per layer: the hop distance, and the server or servers that layer sits on.
     *
     * <p>Both halves matter. The hop number is the unit the ceiling is expressed in, so a player can
     * see at a glance where their instrument stops. The server name answers "which network am I
     * looking at", which the brief requires the graph to answer <em>always</em> — and a layer really
     * can span two servers, one bridge out, so they are listed comma-separated, busiest first.
     */
    private static List<String> headers(NetMap map, Map<Integer, List<Sighting>> byLayer, int[] omitted, int layers) {
        Map<String, String> names = new HashMap<>();
        for (ServerRef server : map.knownServers()) {
            names.put(server.serverId(), server.name().isEmpty() ? server.serverId() : server.name());
        }
        if (!map.currentServer().serverId().isEmpty()) {
            names.putIfAbsent(
                    map.currentServer().serverId(),
                    map.currentServer().name().isEmpty()
                            ? map.currentServer().serverId()
                            : map.currentServer().name());
        }

        List<String> headers = new ArrayList<>();
        for (int layer = 0; layer < layers; layer++) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Sighting sighting : byLayer.getOrDefault(layer, List.of())) {
                if (!sighting.serverId().isEmpty()) {
                    counts.merge(sighting.serverId(), 1, Integer::sum);
                }
            }
            List<String> ordered = new ArrayList<>(counts.keySet());
            ordered.sort(Comparator.<String, Integer>comparing(counts::get)
                    .reversed()
                    .thenComparing(id -> names.getOrDefault(id, id)));

            StringBuilder head = new StringBuilder("H").append(layer);
            if (!ordered.isEmpty()) {
                head.append(' ').append(AsciiCanvas.BULLET).append(' ');
                for (int i = 0; i < ordered.size(); i++) {
                    if (i > 0) {
                        head.append(", ");
                    }
                    head.append(names.getOrDefault(ordered.get(i), ordered.get(i)));
                }
            }
            if (omitted[layer] > 0) {
                head.append(CLAMP_MARK).append(omitted[layer]).append(" MORE");
            }
            headers.add(head.toString());
        }
        return List.copyOf(headers);
    }

    /**
     * An undirected edge, normalised so a symmetric link pair collapses to one entry.
     *
     * <p>A record rather than a joined string key: the addresses are player-facing data and a
     * separator character is a bug waiting for an address format that contains it.
     */
    private record Pair(String low, String high) {}
}
