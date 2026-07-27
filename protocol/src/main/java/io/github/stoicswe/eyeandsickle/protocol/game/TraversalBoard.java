package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;

/**
 * A Traversal layer: a ranked lattice, where the player is standing in it, and the manifest they are
 * meant to read it against.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 — "route through an internal graph to the data
 * node", skill tested: pathfinding under an attention budget. Wrong branches are paid for twice, which
 * is the class's punished failure mode.
 *
 * <h2>The manifest is Invariant I10, written down</h2>
 *
 * §3.2, verbatim: the Traversal class "hides the true objective node among decoys distinguishable only
 * by cross-referencing recovered logs". The final rank holds several objective candidates; each carries
 * a log fragment naming a service and a time, and the manifest names one service and one time. Exactly
 * one candidate matches both.
 *
 * <p>A player who reads the two side by side extracts on the first try. A fixed heuristic cannot —
 * §3.2(d) says a bot "cannot use the intuition shortcuts a human gets from reading flavor data" — so it
 * must extract at random among the candidates, paying an expected (K+1)/2 attempts at 2 attention each
 * plus the strikes a wrong extraction costs.
 *
 * <p><strong>That difference is the number P-3 asks for</strong> ("how much does manual play beat bot
 * play?", §6). §4 made it answerable by replacing seconds with a probe count; this class is built so it
 * can actually be measured, via {@link BreachLayer#probesUsed()}. The gap is to be <em>exposed</em>, not
 * tuned to a target — tuning it before it has been measured would be answering the open question with
 * the number you wanted.
 *
 * <p>Which is also why the manifest travels as text the player can select and copy. A player who cannot
 * copy a hostname cannot cross-reference, and the human-read step quietly becomes a memory test.
 *
 * <h2>What is not here</h2>
 *
 * Which candidate is the real objective. There is no field for it, on this record or on {@link
 * LatticeNode}, and the absence is the point: the answer is reconstructible only from the hints the
 * player has actually paid to read.
 *
 * @param ranks how deep the lattice runs
 * @param objectiveRank the rank holding the candidates; within {@code 0..ranks-1}
 * @param currentNodeId where the player is standing; {@code ""} before they have entered
 * @param nodes every node the board has, rank-major then index, so a map lays out identically each
 *     refresh; nodes the player cannot see yet are present but dark
 * @param manifest the cross-reference the human read depends on, oldest first
 */
public record TraversalBoard(
        int ranks, int objectiveRank, String currentNodeId, List<LatticeNode> nodes, List<String> manifest)
        implements BreachBoard {

    public TraversalBoard {
        Objects.requireNonNull(currentNodeId, "currentNodeId");

        nodes = List.copyOf(nodes);
        manifest = List.copyOf(manifest);

        if (ranks < 1) {
            throw new IllegalArgumentException("A lattice has at least one rank, was " + ranks);
        }
        // An objective rank outside the lattice is not a cosmetic error: it is a layer with no
        // reachable objective, i.e. a layer that can only ever be failed or bypassed.
        if (objectiveRank < 0 || objectiveRank >= ranks) {
            throw new IllegalArgumentException(
                    "objectiveRank " + objectiveRank + " is outside a lattice of " + ranks + " rank(s)");
        }
    }

    @Override
    public PuzzleClass puzzleClass() {
        return PuzzleClass.TRAVERSAL;
    }
}
