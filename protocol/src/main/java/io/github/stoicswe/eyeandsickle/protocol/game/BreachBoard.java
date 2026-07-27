package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The playable surface of one breach layer — everything the player has established about it, and
 * nothing else.
 *
 * <h2>Why the board is a wire type at all</h2>
 *
 * The tempting alternative is to keep it inside the single-player engine and let the client reach in.
 * That is the shape that makes single player quietly become a different game. The precedent already in
 * this module is {@link ComputeBudget}: the server reports the rig's ledger and the client renders it,
 * and that seam is what lets the same views bind to an in-process rules engine or to a home server over
 * REST without knowing which. A breach is the same shape of thing and a real home server will send one,
 * so it is described here, in the module both sides already depend on.
 *
 * <h2>Only revealed information travels</h2>
 *
 * Every implementation is written so the secret is not merely omitted but <em>absent</em>: there is no
 * field on any of them that could carry the true port states, the Logic code, or which candidate is the
 * real objective. Unknown is encoded as {@link PortState#UNKNOWN}, as the empty string, or as absence
 * from a list.
 *
 * <p>This is not paranoia about a solo save the player can already edit. It is what keeps the puzzle
 * honest when the same records cross a wire, and it means a renderer <em>cannot</em> accidentally draw
 * a cheat — the information to do it never arrives. A leak in this direction is the kind of bug that
 * ships silently and is discovered by a player.
 *
 * <h2>Sealed to exactly three</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3.1 decided (2026-07-26) that there are three puzzle
 * classes, down from a proposed five, by applying the merge rule the section always carried: if two
 * classes reduce to the same optimal input pattern, merge them. Timing closed because its skill was an
 * action skill with nothing to express in an attention budget; Credential folded into Logic because
 * "pattern deduction" and "reconstruct a rule from probe responses" are the same verb.
 *
 * <p>§3.1 then makes three a <em>floor</em> as well as a target: "each class must stay a genuinely
 * different kind of thinking. A fourth needs to earn its place against that test, not fill a table."
 * Sealing the interface is that sentence made mechanical — a fourth board is a compile error in every
 * renderer that switches over these, which is exactly the review conversation it should trigger.
 */
public sealed interface BreachBoard permits EnumerationBoard, LogicBoard, TraversalBoard {

    /**
     * Which class this board belongs to.
     *
     * <p>Redundant with the type for a renderer that pattern-matches, and not redundant at all for one
     * that keys a lookup, logs, or reports telemetry — and it keeps a board self-describing if it is
     * ever read without its enclosing {@link BreachLayer}.
     *
     * @return the puzzle class, fixed per implementation
     */
    PuzzleClass puzzleClass();
}
