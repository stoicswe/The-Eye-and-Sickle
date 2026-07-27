package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;

/**
 * One node of a Traversal lattice, as the player currently sees it.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 — "route through an internal graph to the data
 * node", skill tested: pathfinding under an attention budget. Every field here is the player's
 * knowledge of a node, never the node.
 *
 * <h2>There is no {@code trapped} field, and there never will be</h2>
 *
 * The record can say {@code trapKnown} — the player has established that this node is trapped — and it
 * cannot say whether one is. That asymmetry is the whole D-2 discipline in miniature, and this is the
 * type where getting it wrong would be most expensive: a trap the client knows about is a trap the
 * client can route around for free, which deletes the reason the {@code traceroute} action exists and
 * with it the loud-versus-patient trade §4 is built on.
 *
 * <p>{@code hint} works the same way. It is {@code ""} until the player has listened, and it is the
 * only channel through which the objective can be identified — see {@link TraversalBoard} for why that
 * matters to Invariant I10.
 *
 * <p>{@code objectiveCandidate} says the node sits on the objective rank, which the player can see for
 * themselves once the rank is visible. It does <strong>not</strong> say the node is the objective;
 * nothing on the wire says that, ever.
 *
 * @param id stable identity, and what an action takes as its argument
 * @param rank distance from the entry point, 0-based
 * @param index position within the rank, top to bottom, so a map lays out the same way every refresh
 * @param label the hostname, once the node is visible; {@code ""} before that — and the cross-reference
 *     the human read depends on is a hostname, which is why it is not synthesised client-side
 * @param exits ids of outbound nodes visible from here; empty when the player has not looked
 * @param visited whether the player has stood here
 * @param visible whether the player can see the node at all
 * @param objectiveCandidate whether the node sits on the objective rank — a decoy and the real thing are
 *     indistinguishable by this flag, deliberately
 * @param hint the recovered log fragment, once listened to; {@code ""} until then
 * @param trapKnown whether the player has established there is a trap here; silence is not safety
 * @param stepCost extra attention this node charges on entry, above the base cost of a step
 */
public record LatticeNode(
        String id,
        int rank,
        int index,
        String label,
        List<String> exits,
        boolean visited,
        boolean visible,
        boolean objectiveCandidate,
        String hint,
        boolean trapKnown,
        int stepCost) {

    public LatticeNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(hint, "hint");

        exits = List.copyOf(exits);

        if (id.isBlank()) {
            throw new IllegalArgumentException("A lattice node needs an id: it is what step, listen and extract "
                    + "take as their argument, and an unnamed node is unreachable");
        }
        if (rank < 0) {
            throw new IllegalArgumentException("rank must not be negative, was " + rank);
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative, was " + index);
        }
        if (stepCost < 0) {
            throw new IllegalArgumentException("stepCost is a surcharge, never a rebate, was " + stepCost);
        }
    }
}
