package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
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
import java.util.LinkedHashSet;
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
 * Nodes are placed in columns by <b>hop distance from the player's own rig</b>. The implementation
 * spec derives this as a BFS layering over the discovered subgraph; it is the same number, because
 * {@code Sighting.hopsFromRig} already <em>is</em> a BFS distance — computed by the rules over the
 * full link graph, where undiscovered intermediate machines still conduct. Reading it rather than
 * re-deriving it has three consequences worth stating:
 *
 * <h2>⚠ FROM THE RIG, NOT FROM THE VANTAGE — changed 2026-08-07, and it was visible on screen</h2>
 *
 * This laid out on {@code hopsFromVantage}, so <b>moving the vantage re-rooted the entire graph</b>:
 * the machine you connected to jumped to the leftmost column and the player's own rig slid rightwards
 * among strangers. Reported from a real map — {@code 10.0.0.2} at H0 with {@code SELF} demoted to H1.
 * That reads as the world having been rearranged rather than as the player having moved through it,
 * and it undoes the one thing a map is for, which is being the same picture as last time.
 *
 * <p>⚠ <b>Repositioning still shows, and shows better.</b> The vantage keeps the only heavy frame on
 * the map ({@code NetCanvas}), and anything a sweep finds from it lands one hop further from the rig
 * than the vantage does — so a new vantage grows a <b>branch rightward</b> from the node the player
 * moved to, which is what traversal actually looks like. Nothing already drawn moves.
 *
 * <p>⚠ The other three readers of {@code hopsFromVantage} were deliberately left alone: the graph's
 * accessible text, the host list and the LIST view's HOPS column all answer "how far is this from
 * where I am operating", which is the unit the hop ceiling is measured in and is a different question
 * from where a node is drawn.
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
 * <h2>⚠ NOTHING IS HIDDEN ANY MORE — the "+N MORE" clamp is gone (2026-08-08)</h2>
 *
 * A layer wider than sixty rows used to draw the first sixty and put the remainder in a header count.
 * The machines past the cut were on the map's data and absent from its picture, which is the one thing
 * a map may not do — {@code docs/client/09-network-map-graph.md} §1.1. What replaces it is
 * {@link Stack}: a fold of machines the player <em>has</em> found, always marked, always counted
 * exactly, and always openable. There is no row cap left, so a layer nothing folds simply gets tall
 * and the panel scrolls.
 *
 * <h2>Row assignment: two barycentre passes, and never a third</h2>
 *
 * Layer 0 is the player's own rig, at row 0. A <b>forward</b> pass orders each layer by the mean row
 * of its already-placed neighbours one layer back; a <b>backward</b> pass then reorders each layer by
 * the mean row of its neighbours one layer <em>on</em>. Two passes, not "until stable": the packet
 * animation repaints on a timer and the layout has to be identical on every repaint, so an iteration
 * count that depended on the graph would make the whole map shimmer.
 *
 * <p>The backward pass is what removes a single forward pass's characteristic failure — a parent
 * sitting at the top of its column with all its children at the bottom, dragging one long edge
 * diagonally across every other edge in the corridor.
 *
 * <p>⚠ Ties break on <b>address</b>, never on anything derived. A tiebreak on tier, kind or name would
 * put a recon finding into the row order and would reshuffle the map the moment a scan landed.
 *
 * <p>The one reference to {@link AsciiCanvas} here is to its {@code BULLET} constant, and it costs
 * nothing: a {@code static final char} initialised from a literal is a compile-time constant, so
 * javac inlines it and no reference to a JavaFX-derived class survives into this class file. The
 * separator vocabulary stays shared without this class acquiring a toolkit dependency.
 */
public final class NetLayout {

    private NetLayout() {}

    /**
     * What a stack's synthetic key begins with.
     *
     * <p>⚠ A {@link Routed} carries addresses, and a collapsed group has none — so the edge into a
     * stack names the stack. The prefix is what keeps that key out of the address space: no address
     * the generator can produce contains a colon, and {@link #stackId} is the only thing that mints
     * one, so a renderer looking a key up in its address table gets a clean miss rather than the
     * wrong machine.
     */
    public static final String STACK_PREFIX = "stack:";

    /** The key a stack is expanded and collapsed by. Derived from the parent, so it is stable. */
    public static String stackId(String parentAddress) {
        return STACK_PREFIX + parentAddress;
    }

    /**
     * A machine, and where it is drawn.
     *
     * @param layer hop distance from the player's own rig — the column
     * @param row the slot within that column, counted from the top
     */
    public record Placed(Sighting sighting, int layer, int row) {}

    /**
     * A fold of machines behind one parent: one box, one edge, an exact count.
     *
     * <h2>⚠ EVERY MEMBER IS A MACHINE THE PLAYER HAS ALREADY FOUND</h2>
     *
     * {@code docs/client/09-network-map-graph.md} §3.1, and it is the invariant most easily broken
     * here. {@code NetRules} is explicit that undiscovered hosts do not exist in {@code knownNodes}
     * and that the map draws nothing where they are — "<b>no placeholder, no count</b>, no three
     * contacts nearby". A stack is therefore a folding of what is already on the map and never a hint
     * about what is not: {@code ×7} means seven discovered machines are collapsed behind this
     * machine, and it may never mean "this node has seven links, of which you have found two".
     *
     * <p>The bridge peer count ({@code PortScanTarget.PEERS}) stays the sanctioned exception and stays
     * where it was decided — a port-scan finding on a machine the player paid to scan, shown in its
     * report, not on the graph.
     *
     * @param id the key expansion is tracked by; see {@link #stackId}
     * @param parentAddress the one machine in layer {@code layer - 1} every member hangs off
     * @param row where the box is drawn, or — when {@code expanded} — where its first member is
     * @param members every folded machine, ordered by address. Never empty
     * @param expanded whether the player has opened it. An expanded stack draws no box of its own;
     *     it is still reported so a member can be collapsed again from the keyboard, which is the
     *     only route back once the box the player clicked is no longer on screen
     */
    public record Stack(
            String id, String parentAddress, int layer, int row, List<Sighting> members, boolean expanded) {

        public int count() {
            return members.size();
        }

        /** Whether this fold holds {@code address}. */
        public boolean holds(String address) {
            for (Sighting member : members) {
                if (member.address().equals(address)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * An edge that may be drawn, oriented so the renderer never has to think about direction.
     *
     * <p>{@code fromAddress} is always the shallower endpoint for a forward edge, and the upper one
     * for a lateral edge. {@code bridge} is carried through from {@link NetLink} so the renderer can
     * treat a cross-server link as the structural fact it is rather than inferring it from the two
     * endpoints' server ids.
     *
     * <p>⚠ {@code toAddress} is a {@link #stackId} when the far end is a collapsed group. That is the
     * only case either field is not an address, and {@link #STACK_PREFIX} is what makes it legible as
     * one rather than as a machine nobody can find.
     */
    public record Routed(String fromAddress, String toAddress, boolean lateral, boolean bridge) {}

    /**
     * The finished layout.
     *
     * @param layerHeaders one per layer, e.g. {@code "H1 · home-relay"}
     * @param rowsPerLayer the tallest layer, which is what sizes the character grid
     */
    public record Result(
            List<Placed> placed,
            List<Stack> stacks,
            List<Routed> routed,
            List<String> layerHeaders,
            int layers,
            int rowsPerLayer) {

        /** Nothing discovered. A view renders this as an instruction, never as a blank panel. */
        public static Result empty() {
            return new Result(List.of(), List.of(), List.of(), List.of(), 0, 0);
        }

        /** How many machines this layout has folded out of sight right now. */
        public int foldedMachines() {
            int total = 0;
            for (Stack stack : stacks) {
                if (!stack.expanded()) {
                    total += stack.count();
                }
            }
            return total;
        }

        /** The fold holding {@code address}, or {@code null} — what a collapse keystroke acts on. */
        public Stack foldHolding(String address) {
            for (Stack stack : stacks) {
                if (stack.holds(address)) {
                    return stack;
                }
            }
            return null;
        }
    }

    /** Lays out the discovered network with every stack collapsed. */
    public static Result of(NetMap map) {
        return of(map, Set.of());
    }

    /**
     * Lays out the discovered network.
     *
     * @param map the player's visible network; {@code null} and empty both yield {@link Result#empty()}
     * @param expanded the {@link #stackId}s the player has opened. Unknown ids are ignored, which is
     *     what a set held across a sweep needs — the grouping can change underneath it
     */
    public static Result of(NetMap map, Set<String> expanded) {
        if (map == null || map.sightings().isEmpty()) {
            return Result.empty();
        }
        Set<String> open = expanded == null ? Set.of() : expanded;

        // ⚠ LAYERS ARE REBASED ON THE SHALLOWEST MACHINE IN THE MAP, not on the rig, and this is
        // what lets one server be laid out on its own.
        //
        // `hopsFromRig` is measured across the whole world, so on a map filtered to a foreign server
        // (`ServerTabs.filter`) the nearest machine is four or five hops out and the first four or
        // five columns would be EMPTY — a tab whose content starts off the right-hand edge of the
        // panel, which reads as a broken view rather than as a distant server.
        //
        // ⚠ It is a NO-OP for the whole-world map, which is the reason it is safe to do here rather
        // than at the call site: the rig is always at 0, so the base is 0 and every layer keeps the
        // number it had. Nothing that was correct before changes.
        //
        // ⚠ And it does NOT rewrite the sightings. Constructing Sighting records with adjusted hop
        // counts was the obvious alternative and it is a lie in the data — `hopsFromRig` means what
        // it says, several other surfaces read it, and a projection that edits its input to suit its
        // own axis is how two screens come to disagree about how far away something is.
        int base = Integer.MAX_VALUE;
        for (Sighting sighting : map.sightings()) {
            base = Math.min(base, sighting.hopsFromRig());
        }

        Map<String, Sighting> byAddress = new LinkedHashMap<>();
        Map<String, Integer> layerOf = new HashMap<>();
        Map<Integer, List<Sighting>> byLayer = new HashMap<>();
        int layers = 0;
        for (Sighting sighting : map.sightings()) {
            int layer = sighting.hopsFromRig() - base;
            byAddress.put(sighting.address(), sighting);
            layerOf.put(sighting.address(), layer);
            byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(sighting);
            layers = Math.max(layers, layer + 1);
        }

        Map<String, Set<String>> neighbours = adjacency(map, byAddress);

        // ── The fold, decided before a single row is assigned ────────────────────────────────────
        //
        // ⚠ Order first, expand second. Everything below arranges COLLAPSED units, so opening a stack
        // cannot change what the barycentre sees and therefore cannot move a machine the player was
        // looking at — §3.4. Expansion is applied at the very end, as an insertion into the row
        // numbering of one layer.
        Map<String, Stack> folds = folds(byLayer, neighbours, layerOf, layers);
        Map<String, String> foldedInto = new HashMap<>();
        for (Stack fold : folds.values()) {
            for (Sighting member : fold.members()) {
                foldedInto.put(member.address(), fold.id());
            }
        }

        List<List<Unit>> ordered = arrange(byLayer, neighbours, layerOf, folds, foldedInto, layers);

        // ── Rows ─────────────────────────────────────────────────────────────────────────────────
        List<Placed> placed = new ArrayList<>();
        List<Stack> stacks = new ArrayList<>();
        Map<String, Integer> rowOf = new HashMap<>();
        int rowsPerLayer = 0;

        for (int layer = 0; layer < layers; layer++) {
            int row = 0;
            for (Unit unit : ordered.get(layer)) {
                if (unit.fold() == null) {
                    placed.add(new Placed(unit.sighting(), layer, row));
                    rowOf.put(unit.key(), row);
                    row++;
                    continue;
                }
                Stack fold = unit.fold();
                boolean isOpen = open.contains(fold.id());
                stacks.add(new Stack(fold.id(), fold.parentAddress(), layer, row, fold.members(), isOpen));
                if (isOpen) {
                    // §3.4: members occupy rows INSERTED at the stack's own row. Rows above keep
                    // their index; rows below shift by members - 1, which is exactly what emitting
                    // them in place produces.
                    for (Sighting member : fold.members()) {
                        placed.add(new Placed(member, layer, row));
                        rowOf.put(member.address(), row);
                        row++;
                    }
                } else {
                    rowOf.put(fold.id(), row);
                    row++;
                }
            }
            rowsPerLayer = Math.max(rowsPerLayer, row);
        }

        Map<String, Integer> keyLayer = new HashMap<>(layerOf);
        for (Stack fold : folds.values()) {
            keyLayer.put(fold.id(), fold.layer());
        }

        return new Result(
                List.copyOf(placed),
                List.copyOf(stacks),
                routes(map, keyLayer, foldedInto, open),
                headers(map, byLayer, layers),
                layers,
                rowsPerLayer);
    }

    // ── Folding ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Which parents fold their children, and which children go in.
     *
     * <h2>⚠ A MEMBER MAY HAVE NO EDGE THAT LEAVES THE GROUP</h2>
     *
     * {@code 09} §3.2's criterion for grouping by parent is that "the collapsed edge is a single
     * honest edge rather than a bundle", and that is only true if every member's drawn edges go to the
     * parent or to another member. A child with a second parent, a lateral link to a machine outside
     * the group, or a child of its own in the next layer is therefore <b>not eligible</b>: folding it
     * would leave an edge hanging off a box that cannot say which of seven machines it belongs to,
     * which is the same lie §3.2 rejects grouping-by-kind for.
     *
     * <p>⚠ That makes §3.4's "an expanded member that is itself a stack parent renders as a stack"
     * vacuous rather than unimplemented — a member with children is not a member. Stated here because
     * a later change that loosens eligibility has to answer the hanging-edge question first.
     *
     * <p>⚠ The eligible set is a <b>fixpoint</b>, not one filtering pass. Whether a child's neighbour
     * is "outside the group" depends on whether that neighbour is itself in the group, so removing one
     * child can disqualify another. Peeling until nothing changes converges on the unique maximal set,
     * which is deterministic — the layout has to be identical on every repaint and a "largest set"
     * that depended on iteration order would not be.
     */
    private static Map<String, Stack> folds(
            Map<Integer, List<Sighting>> byLayer,
            Map<String, Set<String>> neighbours,
            Map<String, Integer> layerOf,
            int layers) {
        Map<String, Stack> folds = new LinkedHashMap<>();
        for (int layer = Math.max(1, UiTokens.NET_STACK_MIN_LAYER); layer < layers; layer++) {
            Map<String, List<Sighting>> byParent = new LinkedHashMap<>();
            for (Sighting sighting : byLayer.getOrDefault(layer, List.of())) {
                String parent = soleParent(sighting.address(), layer, neighbours, layerOf);
                if (parent != null) {
                    byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(sighting);
                }
            }
            for (Map.Entry<String, List<Sighting>> entry : byParent.entrySet()) {
                String parent = entry.getKey();
                List<Sighting> eligible = peel(parent, entry.getValue(), neighbours);
                if (eligible.size() <= UiTokens.NET_STACK_THRESHOLD) {
                    continue;
                }
                eligible.sort(Comparator.comparing(Sighting::address, NetLayout::compareAddresses));
                // Row and expansion are settled later, once the layer has been arranged — this pass
                // decides membership and nothing else.
                folds.put(stackId(parent), new Stack(stackId(parent), parent, layer, 0, List.copyOf(eligible), false));
            }
        }
        return folds;
    }

    /**
     * The one machine a layer back this child hangs off, or {@code null} if it has none or several.
     *
     * <p>Several is disqualifying rather than resolvable: assigning a two-parent child to, say, its
     * lower-addressed parent would delete the other edge from the picture, and a map that quietly
     * drops an adjacency is worse than a map that draws one more box.
     */
    private static String soleParent(
            String address, int layer, Map<String, Set<String>> neighbours, Map<String, Integer> layerOf) {
        String parent = null;
        for (String peer : neighbours.getOrDefault(address, Set.of())) {
            Integer peerLayer = layerOf.get(peer);
            if (peerLayer != null && peerLayer == layer - 1) {
                if (parent != null) {
                    return null;
                }
                parent = peer;
            }
        }
        return parent;
    }

    /** Removes candidates with a neighbour outside {@code {parent} ∪ candidates}, until stable. */
    private static List<Sighting> peel(String parent, List<Sighting> candidates, Map<String, Set<String>> neighbours) {
        Set<String> inside = new LinkedHashSet<>();
        for (Sighting candidate : candidates) {
            inside.add(candidate.address());
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String address : List.copyOf(inside)) {
                for (String peer : neighbours.getOrDefault(address, Set.of())) {
                    if (!peer.equals(parent) && !inside.contains(peer)) {
                        inside.remove(address);
                        changed = true;
                        break;
                    }
                }
            }
        }
        List<Sighting> out = new ArrayList<>();
        for (Sighting candidate : candidates) {
            if (inside.contains(candidate.address())) {
                out.add(candidate);
            }
        }
        return out;
    }

    // ── Arrangement ──────────────────────────────────────────────────────────────────────────────

    /**
     * One drawable slot: a machine, or a fold standing in for several.
     *
     * <p>The barycentre passes never see the difference, which is {@code 09} §4.3's point — a stack is
     * one row and one edge, so the widths this heuristic has to arrange are bounded by the number of
     * parents rather than by the number of machines.
     */
    private record Unit(String key, Sighting sighting, Stack fold) {

        static Unit of(Sighting sighting) {
            return new Unit(sighting.address(), sighting, null);
        }

        static Unit of(Stack fold) {
            return new Unit(fold.id(), null, fold);
        }
    }

    private static List<List<Unit>> arrange(
            Map<Integer, List<Sighting>> byLayer,
            Map<String, Set<String>> neighbours,
            Map<String, Integer> layerOf,
            Map<String, Stack> folds,
            Map<String, String> foldedInto,
            int layers) {

        List<List<Unit>> ordered = new ArrayList<>(layers);
        Map<String, Set<String>> unitNeighbours = unitAdjacency(neighbours, foldedInto);
        Map<String, Integer> unitLayer = new HashMap<>(layerOf);
        for (Stack fold : folds.values()) {
            unitLayer.put(fold.id(), fold.layer());
        }

        for (int layer = 0; layer < layers; layer++) {
            List<Unit> units = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Sighting sighting : byLayer.getOrDefault(layer, List.of())) {
                String fold = foldedInto.get(sighting.address());
                if (fold == null) {
                    units.add(Unit.of(sighting));
                } else if (seen.add(fold)) {
                    units.add(Unit.of(folds.get(fold)));
                }
            }
            ordered.add(units);
        }

        Map<String, Integer> rowOf = new HashMap<>();
        // Forward: each layer takes the shape of the one before it.
        for (int layer = 0; layer < layers; layer++) {
            sortByNeighbours(ordered.get(layer), unitNeighbours, unitLayer, rowOf, layer - 1, false);
            record(ordered.get(layer), rowOf);
        }
        // Backward: and then of the one after it, which is what stops a parent hanging at the top of
        // its column while every one of its children sits at the bottom.
        for (int layer = layers - 2; layer >= 0; layer--) {
            sortByNeighbours(ordered.get(layer), unitNeighbours, unitLayer, rowOf, layer + 1, true);
            record(ordered.get(layer), rowOf);
        }
        return ordered;
    }

    private static void record(List<Unit> units, Map<String, Integer> rowOf) {
        for (int row = 0; row < units.size(); row++) {
            rowOf.put(units.get(row).key(), row);
        }
    }

    /**
     * Sorts one layer by the mean row of its neighbours in {@code against}.
     *
     * @param holdPlace what a unit with no neighbour in {@code against} does. On the forward pass it
     *     sorts <b>last</b> — it is reachable only laterally, and hanging it off the bottom keeps it
     *     next to the strip that will join it. On the backward pass it <b>keeps its current row</b>:
     *     pushing childless machines to the bottom of every column would undo the forward pass for
     *     the whole of the last layer but one, which is most of a shallow map
     */
    private static void sortByNeighbours(
            List<Unit> units,
            Map<String, Set<String>> neighbours,
            Map<String, Integer> unitLayer,
            Map<String, Integer> rowOf,
            int against,
            boolean holdPlace) {
        if (units.size() < 2 || against < 0) {
            return;
        }
        Map<String, Double> desired = new HashMap<>();
        for (int row = 0; row < units.size(); row++) {
            Unit unit = units.get(row);
            double total = 0;
            int count = 0;
            for (String peer : neighbours.getOrDefault(unit.key(), Set.of())) {
                Integer peerLayer = unitLayer.get(peer);
                Integer peerRow = rowOf.get(peer);
                if (peerLayer != null && peerRow != null && peerLayer == against) {
                    total += peerRow;
                    count++;
                }
            }
            desired.put(unit.key(), count == 0 ? (holdPlace ? row : Double.POSITIVE_INFINITY) : total / count);
        }
        // ⚠ Ties break on ADDRESS and on nothing derived. A tiebreak on tier, kind or name would put a
        // recon finding into the row order — and would reshuffle the map the moment a scan landed.
        units.sort(Comparator.<Unit, Double>comparing(unit -> desired.get(unit.key()))
                .thenComparing(Unit::key, NetLayout::compareAddresses));
    }

    /** Adjacency with every folded member replaced by the stack it went into. */
    private static Map<String, Set<String>> unitAdjacency(
            Map<String, Set<String>> neighbours, Map<String, String> foldedInto) {
        if (foldedInto.isEmpty()) {
            return neighbours;
        }
        Map<String, Set<String>> out = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : neighbours.entrySet()) {
            String from = foldedInto.getOrDefault(entry.getKey(), entry.getKey());
            for (String peer : entry.getValue()) {
                String to = foldedInto.getOrDefault(peer, peer);
                if (from.equals(to)) {
                    // An edge between two members of the same fold. It is inside the box now, and it
                    // is not lost: expanding the stack draws it.
                    continue;
                }
                out.computeIfAbsent(from, k -> new HashSet<>()).add(to);
                out.computeIfAbsent(to, k -> new HashSet<>()).add(from);
            }
        }
        return out;
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
            if (link.fromAddress().equals(link.toAddress())) {
                continue;
            }
            neighbours.computeIfAbsent(link.fromAddress(), k -> new HashSet<>()).add(link.toAddress());
            neighbours.computeIfAbsent(link.toAddress(), k -> new HashSet<>()).add(link.fromAddress());
        }
        return neighbours;
    }

    /**
     * Every drawable edge, de-duplicated, oriented, and folded where its far end is a stack.
     *
     * <p>Links arrive symmetrically from the rules, so an unordered-pair key is what stops every edge
     * being drawn twice — harmless for a merged junction table, but it would double-count the lane
     * assignment and push edges into lanes that hold nothing.
     */
    private static List<Routed> routes(
            NetMap map, Map<String, Integer> keyLayer, Map<String, String> foldedInto, Set<String> open) {
        Map<Pair, Boolean> bridging = new LinkedHashMap<>();
        for (NetLink link : map.links()) {
            Integer from = keyLayer.get(link.fromAddress());
            Integer to = keyLayer.get(link.toAddress());
            if (from == null || to == null) {
                continue;
            }
            if (Math.abs(from - to) > 1) {
                // Unreachable given hop distances (see the class comment's theorem), and dropped
                // rather than drawn wrong if the rules ever hand us one.
                continue;
            }
            // ⚠ A member of a COLLAPSED stack is not drawn, so its edges are re-pointed at the stack —
            // the parent edge becomes THE edge, and an edge between two members disappears into the
            // box. Expanded, the fold is transparent and every one of them is drawn as it always was.
            String fromKey = key(link.fromAddress(), foldedInto, open);
            String toKey = key(link.toAddress(), foldedInto, open);
            if (fromKey.equals(toKey)) {
                continue;
            }
            boolean firstIsLow = from.equals(to) ? compareAddresses(fromKey, toKey) <= 0 : from < to;
            Pair pair = firstIsLow ? new Pair(fromKey, toKey) : new Pair(toKey, fromKey);
            bridging.merge(pair, link.bridge(), (a, b) -> a || b);
        }

        List<Routed> routed = new ArrayList<>();
        for (Map.Entry<Pair, Boolean> entry : bridging.entrySet()) {
            Pair pair = entry.getKey();
            boolean lateral = keyLayer.get(pair.low()).equals(keyLayer.get(pair.high()));
            routed.add(new Routed(pair.low(), pair.high(), lateral, entry.getValue()));
        }
        // Deterministic order: the lane a forward edge takes is its index within its corridor, so an
        // unstable iteration order would shuffle the lanes between repaints.
        routed.sort(Comparator.comparing(Routed::fromAddress, NetLayout::compareAddresses)
                .thenComparing(Routed::toAddress, NetLayout::compareAddresses));
        return List.copyOf(routed);
    }

    private static String key(String address, Map<String, String> foldedInto, Set<String> open) {
        String fold = foldedInto.get(address);
        return fold == null || open.contains(fold) ? address : fold;
    }

    /**
     * One header per layer: the hop distance, and the server or servers that layer sits on.
     *
     * <p>Both halves matter. The hop number is the unit the ceiling is expressed in, so a player can
     * see at a glance where their instrument stops. The server name answers "which network am I
     * looking at", which the brief requires the graph to answer <em>always</em> — and a layer really
     * can span two servers, one bridge out, so they are listed comma-separated, busiest first.
     */
    private static List<String> headers(NetMap map, Map<Integer, List<Sighting>> byLayer, int layers) {
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
