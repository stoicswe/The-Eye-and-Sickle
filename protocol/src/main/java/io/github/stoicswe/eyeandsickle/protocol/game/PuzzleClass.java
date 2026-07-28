package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Which minigame an attempt is playing.
 *
 * <h2>⚠ This enum was five classes and is now two, and the reduction is the point</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3 originally named five — Enumeration, Credential,
 * Logic, Timing, Traversal — as <em>proposals</em>, and its own standing warning was the reason to
 * cut them: "the classes must be genuinely different kinds of thinking, not reskins. If two of them
 * reduce to the same optimal input pattern, merge them." Three shipped, and under that test three
 * did not survive. Enumeration and Logic were both "make a claim, get told how close it was, revise"
 * — the same loop with different nouns — and Traversal was a shortest-path walk whose optimal input
 * pattern never varied.
 *
 * <p>What replaced them are two games that cannot be played the same way as each other:
 *
 * <ul>
 *   <li>{@link #BREACH_PROTOCOL} is <b>spatial planning with no arithmetic</b>. Everything is on
 *       screen from the first second; the difficulty is that the path constrains itself, and a good
 *       player is thinking three picks ahead about a buffer they cannot undo.
 *   <li>{@link #OFFSET_CIPHER} is <b>arithmetic with no planning</b>. There is nothing to work out
 *       about the shape of it — both rows are visible and the answer is subtraction — and the
 *       difficulty is doing sixteen borrows correctly the first time, because the second time costs a
 *       strike.
 * </ul>
 *
 * <p>One rewards looking ahead and punishes committing early; the other rewards care and punishes
 * hurry. A player good at one is not automatically good at the other, which is the whole test §3 sets.
 */
public enum PuzzleClass {

    /**
     * A grid of hex codes, a buffer, and a path that alternates row, column, row, column.
     *
     * <p>Every pick appends to a buffer that cannot be emptied, and every pick narrows what the next
     * one may be. The target sequences are published from the start — this is not a deduction game,
     * it is a routing game, and the skill is seeing a path that lands two sequences inside one buffer.
     */
    BREACH_PROTOCOL,

    /**
     * Two rows of hex bytes and a signed offset to type under each one.
     *
     * <p>⚠ <b>Both rows are visible.</b> There is nothing hidden and nothing to deduce: the answer to
     * every cell is {@code desired − random}, and the game is whether the player can do sixteen of
     * those correctly under a budget that does not forgive a second attempt. Hex arithmetic is a real
     * skill the curriculum already teaches ({@code docs/education/01-foundations.md}), and this is the
     * one place in the game that asks for it directly.
     */
    OFFSET_CIPHER
}
