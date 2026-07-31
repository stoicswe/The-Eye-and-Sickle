package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The offset cipher: two rows of hex bytes, and a signed offset to type under each one.
 *
 * <h2>Nothing is hidden, and the game is still hard</h2>
 *
 * Both {@link #observed} and {@link #target} are on screen from the first frame. The answer to every
 * cell is {@code target − observed} and there is no deduction, no probing and no information to buy.
 * What there is instead is <b>sixteen chances to make an arithmetic mistake</b> and a commit that
 * costs a strike when any cell is wrong.
 *
 * <p>That is the whole design and it is deliberately the opposite of {@link MatrixBoard}'s. One game
 * rewards seeing a path and punishes committing early; this one rewards care and punishes hurry. A
 * player good at one is not automatically good at the other, which is the test {@link PuzzleClass}
 * sets.
 *
 * <h2>⚠ There is no clock, and there must never be one</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 took the wall clock out of the breach entirely, and
 * this puzzle is the one most obviously tempting to put it back — "hex arithmetic under pressure"
 * reads like it wants a countdown. It does not. A timer would make the puzzle a test of how fast a
 * player can subtract rather than whether they can, would be unplayable for anyone who reads slowly,
 * and would collide with §4's own reason for removing it. The pressure is the strike limit and the
 * attention budget, both of which wait.
 *
 * <p>What it pays for the privilege is <b>noise</b>: sitting on somebody's wire working through
 * sixteen bytes radiates for as long as it takes, so this class is louder on average than
 * {@link MatrixBoard}. Patience costs exposure rather than time — see {@code Balance}.
 *
 * @param observed the bytes as read off the wire, {@code 0x00}–{@code 0xFF}. Always visible
 * @param target the bytes the far end is expecting. Always visible — see the class note
 * @param entered what the player has typed under each cell, or {@code null} for an untouched cell.
 *     ⚠ A signed value; {@code 0} is a legitimate answer and is not the same as untouched, which is
 *     why this is a list of boxed integers rather than an int array with a sentinel
 * @param wrong which cells the last commit rejected. Empty until a commit has been made, and cleared
 *     when the player edits. ⚠ It says <em>which</em> cells were wrong but never what they should be
 * @param cursor which cell typing goes into
 * @param typing the digits typed into the cursor cell so far, before they are accepted — so a
 *     half-typed {@code -1} does not read as {@code -1} until the player moves on
 * @param commits how many times the row has been submitted; each one that failed cost a strike
 * @param given which cells arrived already solved. ⚠ Locked, not merely pre-typed — a given cell the
 *     player could overwrite is a trap dressed as a favour, since a stray keystroke on a correct
 *     column is only discovered by the commit that costs a strike. It is also what makes the give
 *     worth more than the keystrokes it saves: a given column does not need CHECKING
 */
public record OffsetBoard(
        List<Integer> observed,
        List<Integer> target,
        List<Integer> entered,
        List<Integer> wrong,
        int cursor,
        String typing,
        int commits,
        List<Boolean> given)
        implements BreachBoard {

    /**
     * A board with nothing given — every column the player's own.
     *
     * <p>⚠ Kept so a breach persisted before pre-filling existed still renders. "Nothing was given"
     * is the true reading of a board from a save that had no such concept, not a default standing in
     * for a missing value.
     */
    public OffsetBoard(
            List<Integer> observed,
            List<Integer> target,
            List<Integer> entered,
            List<Integer> wrong,
            int cursor,
            String typing,
            int commits) {
        this(observed, target, entered, wrong, cursor, typing, commits, List.of());
    }

    /** Whether this column arrived solved. Safe on a board that predates the concept. */
    public boolean isGiven(int index) {
        return index >= 0 && index < given.size() && Boolean.TRUE.equals(given.get(index));
    }

    /** The widest a cell can be off by, in either direction — one byte's worth of distance. */
    public static final int MAX_OFFSET = 255;

    public OffsetBoard {
        given = given == null ? List.of() : List.copyOf(given);
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(target, "target");
        observed = List.copyOf(observed);
        target = List.copyOf(target);
        // ⚠ Copied with nulls intact. Collectors.toList allows them and List.copyOf does not, so this
        // is java.util.Collections.unmodifiableList over a defensive ArrayList — an untouched cell is
        // null and must stay null, because 0 is a real answer.
        entered = entered == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(entered));
        wrong = wrong == null ? List.of() : List.copyOf(wrong);
        typing = typing == null ? "" : typing;
        commits = Math.max(0, commits);

        if (observed.size() != target.size()) {
            throw new IllegalArgumentException(
                    "observed has " + observed.size() + " cells but target has " + target.size());
        }
        cursor = observed.isEmpty() ? 0 : Math.max(0, Math.min(observed.size() - 1, cursor));
    }

    public int length() {
        return observed.size();
    }

    /** How many cells the player has put something in. The only progress figure this board publishes. */
    public int filled() {
        int count = 0;
        for (Integer value : entered) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    /** Whether every cell has an answer in it. Says nothing about whether they are the right ones. */
    public boolean complete() {
        return filled() == length() && length() > 0;
    }

    /** {@code 0A} — two upper-case digits, always, so the columns line up. */
    public static String hex(int value) {
        return String.format(Locale.ROOT, "%02X", value & 0xFF);
    }

    /** {@code -9}, {@code +14}, or {@code ..} for a cell nobody has typed in yet. */
    public static String offsetText(Integer value) {
        if (value == null) {
            return "..";
        }
        return value < 0 ? String.valueOf(value) : "+" + value;
    }
}
