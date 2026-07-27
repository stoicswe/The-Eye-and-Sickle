package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;

/**
 * One machine in a Traversal lattice.
 *
 * <h2>Three fields are hidden, and each is hidden behind its own flag</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3.2 makes the Traversal class's human-read step "the
 * true objective node hidden among decoys distinguishable only by cross-referencing recovered
 * logs". That only works if the logs stay unrecovered until the player recovers them:
 *
 * <ul>
 *   <li>{@link #hint} is snapshotted only when {@link #hintRead} — the log fragment is what the
 *       cross-reference is <em>made of</em>, so handing it over free removes the step.
 *   <li>{@link #trapped} is snapshotted only when {@link #trapKnown} — a trap the player can see
 *       without paying for a {@code traceroute} is not a trap.
 *   <li>Everything about a node with {@link #visible} false is absent from the snapshot entirely.
 * </ul>
 *
 * <p>The pairing of a truth field with its own "has the player established this" flag is deliberate
 * repetition: one combined field would have to encode both, and every place that read it would have
 * to remember which meaning it wanted.
 */
public final class LatticeNodeState {

    public String id = "";

    /** Distance from the entry, in ranks. Edges only ever run forward. */
    public int rank = 0;

    /** Position within the rank, top to bottom, so a renderer can lay the lattice out stably. */
    public int index = 0;

    /** Hostname. Empty until the node becomes visible. */
    public String label = "";

    /** Ids of nodes reachable from here. Forward only — a lattice, not a maze. */
    public List<String> exits = new ArrayList<>();

    public boolean visited = false;

    public boolean visible = false;

    /** Sits on the objective rank. Being a candidate is public; being <em>the</em> objective is not. */
    public boolean objectiveCandidate = false;

    /** The recovered log fragment. ⚠ Authored at generation, snapshotted only when {@link #hintRead}. */
    public String hint = "";

    public boolean hintRead = false;

    /** ⚠ Snapshotted only when {@link #trapKnown}. A canary the player can see is not a canary. */
    public boolean trapped = false;

    public boolean trapKnown = false;

    /**
     * Extra attention this node charges on entry, on top of the ordinary step cost.
     *
     * <p>The Tarpit's fiction made local: {@code docs/design/09-defense-and-hardening.md} §1 says it
     * "slows every intruder action", and under {@code 05} §4 there is no clock left for "slow" to
     * mean anything except a higher price per move.
     */
    public int stepCost = 0;

    public LatticeNodeState() {}
}
