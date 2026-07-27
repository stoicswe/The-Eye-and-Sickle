package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * One edge of the network the player can see. Both endpoints are always present in the same
 * {@link NetMap}'s sightings.
 *
 * <p>That last sentence is a producer contract rather than something this record can check — an edge
 * cannot see the map it belongs to — but it is the property every consumer relies on. A link naming a
 * machine that has no {@link Sighting} would either draw an edge into empty space or leak the existence
 * of an undiscovered machine through the back door, which is exactly what {@link Sighting}'s
 * absence-means-undiscovered encoding exists to prevent.
 *
 * <h2>An edge has two ends and no direction</h2>
 *
 * {@code fromAddress} and {@code toAddress} are two ends, not a source and a destination. The map decides
 * how to draw an edge from the <em>layering</em>, not from the record: nodes are placed in columns by
 * breadth-first distance from the vantage, and in a BFS layering every edge joins layers whose indices
 * differ by at most one. So there are exactly two ways to draw one — forward to the next column, or
 * lateral within a column — and an edge that skips a column cannot exist. That is a theorem about the
 * layout, not a case the renderer has to handle, which is worth writing down here because the previous
 * ASCII graph in this codebase had a real correctness gap precisely where a skip edge would have gone.
 *
 * <p>Producers are therefore free to emit an edge once or in both orientations; consumers must not read
 * meaning into which end came first, and must not assume the other orientation is absent.
 *
 * @param fromAddress one end; never blank
 * @param toAddress the other end; never blank
 * @param bridge whether this edge crosses between two servers. In single player both ends are locally
 *     generated, so nothing federates; the flag exists because a cross-server hop is the move that
 *     changes how dangerous everything around the player is, and the map must be able to draw it
 *     differently
 */
public record NetLink(String fromAddress, String toAddress, boolean bridge) {

    public NetLink {
        // Blank is rejected rather than normalised, unlike everywhere else in this vocabulary. An
        // address is this graph's identity: a blank end is an edge to nothing, which draws a stub that
        // reads as a link to somewhere the player cannot reach yet — the single most misleading thing
        // this map could show, since "there is more out there" is precisely what the player is trying
        // to work out.
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("from");
        }
        if (toAddress == null || toAddress.isBlank()) {
            throw new IllegalArgumentException("to");
        }
        // A self-loop is never something the generator meant; it is an off-by-one that would draw a
        // machine as its own neighbour and inflate every "how many are next to me" reading by one.
        if (fromAddress.equals(toAddress)) {
            throw new IllegalArgumentException("self-loop");
        }
    }
}
